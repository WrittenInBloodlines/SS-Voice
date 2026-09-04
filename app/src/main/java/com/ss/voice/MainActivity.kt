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
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private lateinit var tts: OfflineTts
    private lateinit var text: EditText
    private lateinit var status: TextView
    private var player: MediaPlayer? = null
    private val executor = Executors.newSingleThreadExecutor()
    private val audioQueue = mutableListOf<File>()
    private var queueIndex = 0

    data class DialogueLine(val speaker: String, val text: String)

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

    private fun parseDialogue(input: String): List<DialogueLine> {
        val result = mutableListOf<DialogueLine>()
        var currentSpeaker: String? = null
        val currentText = StringBuilder()

        fun flush() {
            val value = currentText.toString().trim()
            if (value.isNotEmpty()) {
                result += DialogueLine(currentSpeaker ?: "Narrator", cleanText(value))
            }
            currentText.clear()
        }

        for (rawLine in input.lines()) {
            val line = rawLine.trim()
            if (line.isEmpty()) {
                flush()
                currentSpeaker = null
                continue
            }

            val match = Regex("^([A-Za-z0-9•._ -]{1,40}):\\s*(.*)$").matchEntire(line)
            if (match != null) {
                flush()
                currentSpeaker = match.groupValues[1].trim()
                currentText.append(match.groupValues[2])
            } else {
                if (currentText.isNotEmpty()) currentText.append(' ')
                currentText.append(line)
            }
        }
        flush()
        return result
    }

    private fun cleanText(value: String): String {
        return value.trim().removeSurrounding("\"").removeSurrounding("“", "”")
    }

    private fun speak() {
        if (!::tts.isInitialized) return
        val input = text.text.toString().trim()
        if (input.isEmpty()) return

        stop()
        audioQueue.clear()
        queueIndex = 0

        val dialogue = parseDialogue(input)
        if (dialogue.isEmpty()) return

        val speakers = dialogue.map { it.speaker }.distinct()
        status.text = "Parsing • ${speakers.size} speaker(s) • ${speakers.joinToString(", ")}" 

        executor.execute {
            try {
                dialogue.forEachIndexed { index, line ->
                    runOnUiThread {
                        status.text = "Generating ${index + 1}/${dialogue.size} • ${line.speaker}"
                    }

                    // Phase 2: every detected speaker is routed independently.
                    // For now all speakers use the bundled Ryan voice. Later, this
                    // map will connect each character to its own Voice Profile.
                    val audio = tts.generate(line.text, sid = 0, speed = 1.0f)
                    val file = File(filesDir, "dialogue_${index}_${System.nanoTime()}.wav")
                    audio.save(file.absolutePath)
                    synchronized(audioQueue) { audioQueue += file }
                }

                runOnUiThread {
                    status.text = "Ready • ${dialogue.size} lines • ${speakers.size} speaker(s) detected"
                    playNext()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "Generation failed: ${e.message ?: "unknown error"}"
                }
            }
        }
    }

    private fun playNext() {
        val file = synchronized(audioQueue) {
            if (queueIndex < audioQueue.size) audioQueue[queueIndex] else null
        }

        if (file == null) {
            status.text = "Ready • Piper • Ryan Medium • English • Offline"
            return
        }

        player?.release()
        player = MediaPlayer().apply {
            setDataSource(file.absolutePath)
            prepare()
            setOnCompletionListener {
                queueIndex++
                it.release()
                player = null
                playNext()
            }
            start()
        }
    }

    private fun stop() {
        player?.stop()
        player?.release()
        player = null
        queueIndex = 0
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
        executor.shutdownNow()
        if (::tts.isInitialized) tts.release()
        super.onDestroy()
    }
}
