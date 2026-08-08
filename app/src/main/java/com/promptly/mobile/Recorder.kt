package com.promptly.mobile

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

class Recorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    var file: File? = null
        private set

    fun start() {
        val output = File(context.cacheDir, "promptly_${System.currentTimeMillis()}.m4a")
        val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        mediaRecorder.setOutputFile(output.absolutePath)
        mediaRecorder.prepare()
        mediaRecorder.start()
        recorder = mediaRecorder
        file = output
    }

    fun stop(): File {
        val mediaRecorder = recorder ?: throw IllegalStateException("Recorder not started")
        try {
            mediaRecorder.stop()
        } finally {
            mediaRecorder.release()
            recorder = null
        }
        return file ?: throw IllegalStateException("No output file")
    }

    fun release() {
        try {
            recorder?.release()
        } catch (_: Exception) {
        }
        recorder = null
    }
}