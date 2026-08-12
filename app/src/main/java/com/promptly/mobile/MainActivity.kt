package com.promptly.mobile

import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import com.google.android.material.snackbar.Snackbar

class MainActivity : AppCompatActivity() {

    private lateinit var apiInput: EditText
    private lateinit var toggleButton: Button
    private lateinit var addTilesButton: Button
    private lateinit var statusText: TextView
    private lateinit var accurateSwitch: android.widget.Switch
    private lateinit var languageSpinner: Spinner
    private lateinit var translateSpinner: Spinner

    private val prefs: SharedPreferences by lazy {
        getSharedPreferences("promptly", MODE_PRIVATE)
    }

    private val micRequest =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startOverlay()
            else statusText.setText(R.string.status_mic_denied)
        }

    private val notificationRequest =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            micRequest.launch(android.Manifest.permission.RECORD_AUDIO)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        apiInput = findViewById(R.id.apiKeyInput)
        toggleButton = findViewById(R.id.overlayToggle)
        addTilesButton = findViewById(R.id.addTilesButton)
        statusText = findViewById(R.id.statusText)
        accurateSwitch = findViewById(R.id.accurateSwitch)
        languageSpinner = findViewById(R.id.languageSpinner)
        translateSpinner = findViewById(R.id.translateSpinner)

        apiInput.setText(prefs.getString(KEY_API, ""))
        apiInput.addTextChangedListener {
            prefs.edit().putString(KEY_API, it?.toString()?.trim().orEmpty()).apply()
        }

        toggleButton.setOnClickListener {
            if (OverlayService.isRunning(this)) {
                OverlayService.stopMe(this)
                updateStatus()
            } else {
                tryStartOverlay()
            }
        }

        accurateSwitch.isChecked = prefs.getBoolean("accurate_model", true)
        accurateSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("accurate_model", checked).apply()
        }

        val languages = resources.getStringArray(R.array.languages)
        val codes = resources.getStringArray(R.array.language_codes)
        languageSpinner.adapter = ArrayAdapter(
            this,
            R.layout.spinner_item,
            languages
        ).apply {
            setDropDownViewResource(R.layout.spinner_dropdown_item)
        }
        val savedLanguage = prefs.getString("language", "en").orEmpty()
        val savedIndex = codes.indexOfFirst { it == savedLanguage }
        languageSpinner.setSelection(if (savedIndex >= 0) savedIndex else 0)
        languageSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: android.widget.AdapterView<*>?,
                view: android.view.View?,
                position: Int,
                id: Long
            ) {
                prefs.edit().putString("language", codes[position]).apply()
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        val translateNames = listOf(getString(R.string.no_translation)) + languages.toList()
        val translateCodes = listOf("") + codes.toList()
        translateSpinner.adapter = ArrayAdapter(
            this,
            R.layout.spinner_item,
            translateNames
        ).apply {
            setDropDownViewResource(R.layout.spinner_dropdown_item)
        }
        val savedTranslate = prefs.getString("translate_to", "").orEmpty()
        val savedTranslateIndex = translateCodes.indexOfFirst { it == savedTranslate }
        translateSpinner.setSelection(if (savedTranslateIndex >= 0) savedTranslateIndex else 0)
        translateSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: android.widget.AdapterView<*>?,
                view: android.view.View?,
                position: Int,
                id: Long
            ) {
                prefs.edit().putString("translate_to", translateCodes[position]).apply()
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        addTilesButton.setOnClickListener { addQuickSettingsTiles() }
    }

    private fun addQuickSettingsTiles() {
        Snackbar.make(
            findViewById(android.R.id.content),
            "Swipe down twice from the top of the screen, press the pencil icon, and drag the Promptly buttons into the panel",
            Snackbar.LENGTH_LONG
        ).show()
    }

    private fun tryStartOverlay() {
        if (prefs.getString(KEY_API, "").orEmpty().isBlank()) {
            Snackbar.make(findViewById(android.R.id.content), "Add your Groq API key first", Snackbar.LENGTH_LONG).show()
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                android.net.Uri.parse("package:$packageName")
            )
            startActivity(intent)
            Snackbar.make(
                findViewById(android.R.id.content),
                "Allow \"display over other apps\" for Promptly, then press Start again",
                Snackbar.LENGTH_LONG
            ).show()
            return
        }
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationRequest.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        micRequest.launch(android.Manifest.permission.RECORD_AUDIO)
    }

    private fun startOverlay() {
        prefs.edit().putBoolean("overlay_visible", true).apply()
        val intent = Intent(this, OverlayService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        updateStatus()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun updateStatus() {
        statusText.text = getString(R.string.status_text)
        toggleButton.text = getString(
            if (OverlayService.isRunning(this)) R.string.stop_overlay else R.string.start_overlay
        )
    }

    companion object {
        const val KEY_API = "api_key"
    }
}