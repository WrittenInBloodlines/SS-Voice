package com.ss.voice

import android.app.Activity
import android.media.MediaPlayer
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.getOfflineTtsConfig
import java.io.File

class MainActivity : Activity() {
    private lateinit var tts: OfflineTts
    private lateinit var text: EditText
    private lateinit var status: TextView
    private var player: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        text = findViewById(R.id.textInput)
        status = findViewById(R.id.status)

        try {
            val modelDir = copyAssetFolder("vits-piper-en_US-ryan-medium")
            val config = getOfflineTtsConfig(
                modelDir = modelDir,
                modelName = "en_US-ryan-medium.onnx",
                acousticModelName = "",
                vocoder = "",
                voices = "",
                lexicon = "",
                dataDir = "$modelDir/espeak-ng-data",
                dictDir = "",
                ruleFsts = "",
                ruleFars = "",
                numThreads = 2
            )
            tts = OfflineTts(config = config)
            status.text = "Ready • Piper • Ryan Medium • English • Offline"
        } catch (e: Exception) {
            status.text = "Piper failed: ${e.message ?: "unknown error"}"
        }

        findViewById<Button>(R.id.speakButton).setOnClickListener { speak() }
        findViewById<Button>(R.id.stopButton).setOnClickListener { stop() }
    }

    private fun speak() {
        if (!::tts.isInitialized) return
        val input = text.text.toString().trim()
        if (input.isEmpty()) return

        status.text = "Generating • Piper • English • Offline"
        Thread {
            try {
                val audio = tts.generate(input, sid = 0, speed = 1.0f)
                val file = File(filesDir, "preview.wav")
                audio.save(file.absolutePath)
                runOnUiThread {
                    player?.release()
                    player = MediaPlayer().apply {
                        setDataSource(file.absolutePath)
                        prepare()
                        start()
                        setOnCompletionListener { status.text = "Ready • Piper • Ryan Medium • English • Offline" }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { status.text = "Generation failed: ${e.message ?: "unknown error"}" }
            }
        }.start()
    }

    private fun stop() {
        player?.stop()
        player?.release()
        player = null
        if (::tts.isInitialized) status.text = "Ready • Piper • Ryan Medium • English • Offline"
    }

    private fun copyAssetFolder(path: String): String {
        val destination = File(filesDir, path)
        if (!destination.exists()) destination.mkdirs()
        copyAssetContents(path, destination)
        return destination.absolutePath
    }

    private fun copyAssetContents(path: String, destination: File) {
        val entries = assets.list(path) ?: return
        for (entry in entries) {
            val childPath = "$path/$entry"
            val child = File(destination, entry)
            val children = assets.list(childPath)
            if (children != null && children.isNotEmpty()) {
                child.mkdirs()
                copyAssetContents(childPath, child)
            } else {
                assets.open(childPath).use { input ->
                    child.outputStream().use { output -> input.copyTo(output) }
                }
            }
        }
    }

    override fun onDestroy() {
        player?.release()
        if (::tts.isInitialized) tts.release()
        super.onDestroy()
    }
}
