package com.promptly.mobile

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import java.io.File
import java.io.IOException

class OverlayService : Service() {

    companion object {
        private const val CHANNEL_ID = "promptly_overlay"
        private const val NOTIFICATION_ID = 1
        private const val TAG = "Promptly"
        private const val PREFS = "promptly"

        const val ACTION_START_RECORDING = "com.promptly.mobile.action.START_RECORDING"
        const val ACTION_STOP_RECORDING = "com.promptly.mobile.action.STOP_RECORDING"
        const val ACTION_TOGGLE_RECORDING = "com.promptly.mobile.action.TOGGLE_RECORDING"
        const val ACTION_TOGGLE_OVERLAY = "com.promptly.mobile.action.TOGGLE_OVERLAY"

        fun isRunning(context: Context): Boolean {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            return manager.getRunningServices(Int.MAX_VALUE)
                .any { it.service.className == OverlayService::class.java.name }
        }

        fun stopMe(context: Context) {
            context.stopService(Intent(context, OverlayService::class.java))
        }
    }

    private enum class BubbleState { IDLE, RECORDING, TRANSCRIBING }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var windowManager: WindowManager
    private lateinit var bubbleRoot: FrameLayout
    private lateinit var coreView: View
    private lateinit var overlayParams: WindowManager.LayoutParams
    private val prefs: SharedPreferences by lazy {
        getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }
    private val recorder = Recorder(this)
    private var state = BubbleState.IDLE
    private var overlayVisible = true
    private var startedAt = 0L
    private var currentCall: Call? = null
    private var cancelRequested = false
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var bubbleStartX = 0
    private var bubbleStartY = 0
    private var moved = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Ready — tap the button to record"))
        buildBubble()
        overlayVisible = prefs.getBoolean("overlay_visible", true)
        if (overlayVisible) {
            windowManager.addView(bubbleRoot, overlayParams)
        }
        updateBubble()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_RECORDING -> if (state == BubbleState.IDLE) startRecording()
            ACTION_STOP_RECORDING -> if (state == BubbleState.RECORDING) stopAndTranscribe()
            ACTION_TOGGLE_RECORDING -> onBubbleTap()
            ACTION_TOGGLE_OVERLAY -> toggleOverlay()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        recorder.release()
        try {
            windowManager.removeView(bubbleRoot)
        } catch (_: Exception) {
        }
        super.onDestroy()
    }

    private fun buildBubble() {
        val size = dp(38)
        bubbleRoot = FrameLayout(this)
        bubbleRoot.isClickable = true
        bubbleRoot.setPadding(0, 0, 0, 0)

        val circle = View(this)
        circle.layoutParams = FrameLayout.LayoutParams(dp(36), dp(36), Gravity.CENTER)
        circle.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.WHITE)
        }

        coreView = View(this)
        coreView.layoutParams = FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER)
        coreView.background = roundedRect(Color.BLACK, dp(5))

        bubbleRoot.addView(circle)
        bubbleRoot.addView(coreView)

        overlayParams = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(16)
            y = dp(160)
        }

        bubbleRoot.setOnTouchListener { _, event ->
            handleTouch(event)
            true
        }
    }

    private fun toggleOverlay() {
        if (overlayVisible) hideOverlay() else showOverlay()
    }

    private fun showOverlay() {
        if (overlayVisible) return
        try {
            windowManager.addView(bubbleRoot, overlayParams)
            overlayVisible = true
            prefs.edit().putBoolean("overlay_visible", true).apply()
            Log.d(TAG, "Overlay shown")
        } catch (e: Exception) {
            Log.e(TAG, "Show overlay failed", e)
        }
    }

    private fun hideOverlay() {
        if (!overlayVisible) return
        try {
            windowManager.removeView(bubbleRoot)
            overlayVisible = false
            prefs.edit().putBoolean("overlay_visible", false).apply()
            Log.d(TAG, "Overlay hidden — going to sleep")
        } catch (e: Exception) {
            Log.e(TAG, "Hide overlay failed", e)
        }
        if (state == BubbleState.IDLE) {
            stopSelf()
        }
    }

    private fun handleTouch(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchStartX = event.rawX
                touchStartY = event.rawY
                bubbleStartX = overlayParams.x
                bubbleStartY = overlayParams.y
                moved = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - touchStartX
                val dy = event.rawY - touchStartY
                if (dx * dx + dy * dy > 64) moved = true
                if (moved) {
                    overlayParams.x = bubbleStartX + dx.toInt()
                    overlayParams.y = bubbleStartY + dy.toInt()
                    windowManager.updateViewLayout(bubbleRoot, overlayParams)
                }
            }
            MotionEvent.ACTION_UP -> {
                if (!moved) onBubbleTap()
            }
        }
    }

    private fun onBubbleTap() {
        when (state) {
            BubbleState.IDLE -> startRecording()
            BubbleState.RECORDING -> stopAndTranscribe()
            BubbleState.TRANSCRIBING -> cancelTranscription()
        }
    }

    private fun cancelTranscription() {
        cancelRequested = true
        currentCall?.cancel()
        toast("Stopping…")
    }

    private fun startRecording() {
        if (state != BubbleState.IDLE) return
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            toast("Microphone permission missing — open the Promptly app")
            openApp()
            return
        }
        val apiKey = getSharedPreferences("promptly", MODE_PRIVATE)
            .getString(MainActivity.KEY_API, "").orEmpty().trim()
        if (apiKey.isBlank()) {
            toast("Add your Groq API key in the Promptly app first")
            openApp()
            return
        }
        try {
            recorder.start()
            state = BubbleState.RECORDING
            startedAt = System.currentTimeMillis()
            prefs.edit().putBoolean("recording", true).apply()
            Log.d(TAG, "Recording started")
            updateBubble()
        } catch (e: Exception) {
            Log.e(TAG, "Recording start failed", e)
            toast("Could not start recording")
        }
    }

    private fun stopAndTranscribe() {
        if (System.currentTimeMillis() - startedAt < 1200) {
            toast("Too quick — recording just started")
            return
        }
        val file: File
        try {
            file = recorder.stop()
            Log.d(TAG, "Recorded ${file.length()} bytes -> transcribing")
        } catch (e: Exception) {
            recorder.release()
            recorder.file?.delete()
            state = BubbleState.IDLE
            prefs.edit().putBoolean("recording", false).apply()
            updateBubble()
            toast("Recording failed — try again")
            if (!overlayVisible) stopSelf()
            return
        }
        state = BubbleState.TRANSCRIBING
        prefs.edit().putBoolean("recording", false).apply()
        updateBubble()
        cancelRequested = false
        val apiKey = getSharedPreferences("promptly", MODE_PRIVATE)
            .getString(MainActivity.KEY_API, "").orEmpty().trim()

        val language = prefs.getString("language", "en").orEmpty().trim().ifEmpty { "en" }
        val translateTo = prefs.getString("translate_to", "").orEmpty().trim()

        scope.launch {
            scope.launch {
                delay(15_000)
                if (state == BubbleState.TRANSCRIBING) toast("Still working…")
            }
            val result = withContext(Dispatchers.IO) {
                try {
                    if (cancelRequested) throw IOException("Cancelled")
                    val accurate = prefs.getBoolean("accurate_model", true)
                    val model = if (accurate) "whisper-large-v3" else "whisper-large-v3-turbo"
                    val call = GroqApi.transcribe(file, apiKey, model, language)
                    currentCall = call
                    call.execute().use { response ->
                        val raw = response.body?.string().orEmpty()
                        if (!response.isSuccessful) {
                            throw IOException("HTTP ${response.code}: $raw")
                        }
                        Log.d(TAG, "Transcribed ${raw.trim().length} characters ($model, $language)")
                        Result.success(raw.trim())
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Transcription failed", e)
                    Result.failure(e)
                } finally {
                    currentCall = null
                    file.delete()
                }
            }
            val message = result.exceptionOrNull()?.message.orEmpty()
            if (result.isFailure) {
                if (cancelRequested || message.contains("Cancel", ignoreCase = true)) {
                    toast("Transcription cancelled")
                } else {
                    toast("Transcription failed: $message")
                }
            } else {
                val text = result.getOrNull().orEmpty()
                if (text.isBlank()) {
                    toast("No speech detected")
                } else {
                    val shouldTranslate = translateTo.isNotBlank() && translateTo != language
                    var finalText = text
                    var translated: String? = null
                    if (shouldTranslate) {
                        updateNotification("Translating…")
                        translated = withContext(Dispatchers.IO) {
                            try {
                                val call = GroqApi.translate(text, apiKey, language, translateTo)
                                currentCall = call
                                call.execute().use { response ->
                                    val raw = response.body?.string().orEmpty()
                                    if (!response.isSuccessful) {
                                        throw IOException("HTTP ${response.code}: $raw")
                                    }
                                    val content = org.json.JSONObject(raw)
                                        .getJSONArray("choices")
                                        .getJSONObject(0)
                                        .getJSONObject("message")
                                        .getString("content")
                                        .trim()
                                    if (content.isBlank()) null else content
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Translation failed", e)
                                null
                            } finally {
                                currentCall = null
                            }
                        }
                        if (translated != null) finalText = translated
                    }
                    if (cancelRequested) {
                        toast("Transcription cancelled")
                    } else {
                        // Polish the final text — after translation, so the cleanup
                        // also covers any translation roughness. A polish failure
                        // or a model refusal never blocks the text from being copied.
                        if (prefs.getBoolean("polish_text", true)) {
                            updateNotification("Polishing…")
                            val polished = withContext(Dispatchers.IO) {
                                try {
                                    val call = GroqApi.polish(finalText, apiKey)
                                    currentCall = call
                                    call.execute().use { response ->
                                        val raw = response.body?.string().orEmpty()
                                        if (!response.isSuccessful) {
                                            throw IOException("HTTP ${response.code}: $raw")
                                        }
                                        val content = org.json.JSONObject(raw)
                                            .getJSONArray("choices")
                                            .getJSONObject(0)
                                            .getJSONObject("message")
                                            .getString("content")
                                            .trim()
                                        if (content.isBlank() || GroqApi.looksLikeRefusal(content, finalText)) {
                                            null
                                        } else {
                                            content
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Polish failed", e)
                                    null
                                } finally {
                                    currentCall = null
                                }
                            }
                            if (polished != null) finalText = polished
                        }
                        copyToClipboard(finalText)
                        Log.d(TAG, "Text copied to clipboard")
                        if (translated != null) {
                            Log.d(TAG, "Translated to $translateTo & copied to clipboard")
                            toast("Translated & copied — paste it anywhere")
                        } else if (shouldTranslate) {
                            toast("Translation failed — copied original")
                        } else {
                            toast("Copied — paste it anywhere")
                        }
                    }
                }
            }
            state = BubbleState.IDLE
            updateBubble()
            if (!overlayVisible) {
                Log.d(TAG, "Work done, bubble hidden — going to sleep")
                stopSelf()
            }
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Promptly", text))
    }

    private fun updateBubble() {
        when (state) {
            BubbleState.IDLE -> {
                coreView.background = roundedRect(Color.BLACK, dp(5))
                updateNotification("Ready — tap the button to record")
            }
            BubbleState.RECORDING -> {
                coreView.background = roundedRect(Color.rgb(217, 32, 32), dp(5))
                updateNotification("Recording… tap the button to stop")
            }
            BubbleState.TRANSCRIBING -> {
                coreView.background = roundedRect(Color.rgb(47, 111, 224), dp(5))
                updateNotification("Transcribing…")
            }
        }
    }

    private fun roundedRect(color: Int, radius: Int) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadius = radius.toFloat()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(intent)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Promptly overlay",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Promptly")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(openAppIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }
}