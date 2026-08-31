package com.example.notestaker.utils

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File
import java.io.FileOutputStream

class AudioRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null

    fun startRecording(outputFile: File) {
        recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            MediaRecorder()
        }.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(FileOutputStream(outputFile).fd)

            try {
                prepare()
                start()
            } catch (e: Exception) {
                Log.e("AudioRecorder", "prepare() failed: ${e.message}")
            }
        }
    }

    fun stopRecording() {
        recorder?.apply {
            try {
                stop()
                release()
            } catch (e: Exception) {
                Log.e("AudioRecorder", "stop() failed: ${e.message}")
            }
        }
        recorder = null
    }
}
