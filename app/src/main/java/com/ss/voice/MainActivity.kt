package com.ss.voice

import android.app.Activity
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.getOfflineTtsConfig
import java.io.File
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private lateinit var narratorTts: OfflineTts
    private lateinit var alexTts: OfflineTts
    private lateinit var ciroTts: OfflineTts
    private lateinit var text: EditText
    private lateinit var status: TextView
    private lateinit var speedValue: TextView
    private lateinit var pitchValue: TextView
    private lateinit var volumeValue: TextView
    private lateinit var speedBar: SeekBar
    private lateinit var pitchBar: SeekBar
    private lateinit var volumeBar: SeekBar
    private lateinit var profileSpinner: Spinner
    private lateinit var voiceSpinner: Spinner
    private lateinit var saveProfileButton: Button
    private lateinit var testVoiceButton: Button
    private var player: MediaPlayer? = null
    private val executor = Executors.newSingleThreadExecutor()
    private val audioQueue = mutableListOf<AudioItem>()
    private var queueIndex = 0
    private var speed = 1.0f
    private var pitch = 1.0f
    private var volume = 1.0f
    private var selectedProfile = "Narrator"
    private var selectedVoiceKey = "ryan"

    private val profileNames = listOf("Narrator", "Alex", "Ciro")
    private val voiceNames = listOf(
        "Ryan • English • Medium",
        "Lessac • English • Medium",
        "Amy • English • Medium"
    )
    private val voiceKeys = listOf("ryan", "lessac", "amy")
    private val voiceLabels = mapOf(
        "ryan" to "Ryan",
        "lessac" to "Lessac",
        "amy" to "Amy"
    )
    private val preferences by lazy { getSharedPreferences("voice_profiles", MODE_PRIVATE) }

    data class DialogueLine(val speaker: String, val text: String)
    data class VoiceSettings(val speed: Float, val pitch: Float, val volume: Float)
    data class AudioItem(val file: File, val settings: VoiceSettings, val speaker: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        text = findViewById(R.id.textInput)
        status = findViewById(R.id.status)
        speedBar = findViewById(R.id.speedBar)
        pitchBar = findViewById(R.id.pitchBar)
        volumeBar = findViewById(R.id.volumeBar)
        speedValue = findViewById(R.id.speedValue)
        pitchValue = findViewById(R.id.pitchValue)
        volumeValue = findViewById(R.id.volumeValue)
        profileSpinner = findViewById(R.id.profileSpinner)
        voiceSpinner = findViewById(R.id.voiceSpinner)
        saveProfileButton = findViewById(R.id.saveProfileButton)
        testVoiceButton = findViewById(R.id.testVoiceButton)

        setupControls()
        setupProfiles()
        setupVoiceLibrary()

        try {
            val narratorDir = copyAssetFolder("vits-piper-en_US-ryan-medium")
            val alexDir = copyAssetFolder("vits-piper-en_US-lessac-medium")
            val ciroDir = copyAssetFolder("vits-piper-en_US-amy-medium")

            narratorTts = createTts(narratorDir, "en_US-ryan-medium.onnx")
            alexTts = createTts(alexDir, "en_US-lessac-medium.onnx")
            ciroTts = createTts(ciroDir, "en_US-amy-medium.onnx")

            status.text = "Ready • 3 local voices • English • Offline"
        } catch (e: Exception) {
            status.text = "Piper failed: ${e.message ?: "unknown error"}"
        }

        findViewById<Button>(R.id.speakButton).setOnClickListener { speak() }
        findViewById<Button>(R.id.stopButton).setOnClickListener { stop() }
        saveProfileButton.setOnClickListener { saveCurrentProfile() }
        testVoiceButton.setOnClickListener { testSelectedVoice() }
    }

    private fun setupProfiles() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, profileNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        profileSpinner.adapter = adapter
        profileSpinner.setSelection(0)
        profileSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                selectedProfile = profileNames[position]
                loadProfile(selectedProfile)
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }
    }

    private fun setupVoiceLibrary() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, voiceNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        voiceSpinner.adapter = adapter
        voiceSpinner.setSelection(0)
        voiceSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                selectedVoiceKey = voiceKeys[position]
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }
    }

    private fun setupControls() {
        speedBar.progress = 50
        pitchBar.progress = 50
        volumeBar.progress = 100
        updateControlLabels()

        speedBar.setOnSeekBarChangeListener(simpleListener { progress ->
            speed = 0.75f + progress / 100f
            updateControlLabels()
        })
        pitchBar.setOnSeekBarChangeListener(simpleListener { progress ->
            pitch = 0.75f + progress / 100f
            updateControlLabels()
        })
        volumeBar.setOnSeekBarChangeListener(simpleListener { progress ->
            volume = progress / 100f
            updateControlLabels()
            player?.setVolume(volume, volume)
        })
    }

    private fun simpleListener(onProgress: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            onProgress(progress)
        }
        override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
        override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
    }

    private fun updateControlLabels() {
        speedValue.text = String.format("%.2fx", speed)
        pitchValue.text = String.format("%.2fx", pitch)
        volumeValue.text = "${(volume * 100).toInt()}%"
    }

    private fun profileKey(profile: String) = profile.lowercase().replace(" ", "_")

    private fun loadProfile(profile: String) {
        val key = profileKey(profile)
        speed = preferences.getFloat("${key}_speed", 1.0f)
        pitch = preferences.getFloat("${key}_pitch", 1.0f)
        volume = preferences.getFloat("${key}_volume", 1.0f)
        selectedVoiceKey = preferences.getString("${key}_voice", defaultVoiceForProfile(profile)) ?: defaultVoiceForProfile(profile)

        speedBar.progress = ((speed - 0.75f) * 100f).toInt().coerceIn(0, 100)
        pitchBar.progress = ((pitch - 0.75f) * 100f).toInt().coerceIn(0, 100)
        volumeBar.progress = (volume * 100f).toInt().coerceIn(0, 100)
        voiceSpinner.setSelection(voiceKeys.indexOf(selectedVoiceKey).coerceAtLeast(0))
        updateControlLabels()
    }

    private fun defaultVoiceForProfile(profile: String): String {
        return when (profile) {
            "Alex" -> "lessac"
            "Ciro" -> "amy"
            else -> "ryan"
        }
    }

    private fun saveCurrentProfile() {
        val key = profileKey(selectedProfile)
        preferences.edit()
            .putFloat("${key}_speed", speed)
            .putFloat("${key}_pitch", pitch)
            .putFloat("${key}_volume", volume)
            .putString("${key}_voice", selectedVoiceKey)
            .apply()
        status.text = "Saved • $selectedProfile • ${voiceLabels[selectedVoiceKey] ?: selectedVoiceKey}"
    }

    private fun voiceKeyForSpeaker(speaker: String): String {
        val profile = when (speaker.trim().lowercase()) {
            "alex", "ethyalexia", "ethyalexia eztliquies" -> "Alex"
            "ciro", "cyrus", "cyrus d'amantino", "cyrus d’amantino" -> "Ciro"
            else -> "Narrator"
        }
        val key = profileKey(profile)
        return preferences.getString("${key}_voice", defaultVoiceForProfile(profile)) ?: defaultVoiceForProfile(profile)
    }

    private fun settingsForSpeaker(speaker: String): VoiceSettings {
        val profile = when (speaker.trim().lowercase()) {
            "alex", "ethyalexia", "ethyalexia eztliquies" -> "Alex"
            "ciro", "cyrus", "cyrus d'amantino", "cyrus d’amantino" -> "Ciro"
            else -> "Narrator"
        }
        val key = profileKey(profile)
        return VoiceSettings(
            speed = preferences.getFloat("${key}_speed", 1.0f),
            pitch = preferences.getFloat("${key}_pitch", 1.0f),
            volume = preferences.getFloat("${key}_volume", 1.0f)
        )
    }

    private fun voiceForKey(key: String): OfflineTts {
        return when (key) {
            "lessac" -> alexTts
            "amy" -> ciroTts
            else -> narratorTts
        }
    }

    private fun createTts(modelDir: String, modelName: String): OfflineTts {
        val config = getOfflineTtsConfig(
            modelDir = modelDir,
            modelName = modelName,
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
        return OfflineTts(config = config)
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
        if (!::narratorTts.isInitialized || !::alexTts.isInitialized || !::ciroTts.isInitialized) return
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

                    val selectedVoice = voiceForKey(voiceKeyForSpeaker(line.speaker))
                    val audio = selectedVoice.generate(line.text, sid = 0, speed = 1.0f)
                    val file = File(filesDir, "dialogue_${index}_${System.nanoTime()}.wav")
                    audio.save(file.absolutePath)
                    val settings = settingsForSpeaker(line.speaker)
                    synchronized(audioQueue) { audioQueue += AudioItem(file, settings, line.speaker) }
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

    private fun testSelectedVoice() {
        if (!::narratorTts.isInitialized || !::alexTts.isInitialized || !::ciroTts.isInitialized) return
        stop()
        executor.execute {
            try {
                val tts = voiceForKey(selectedVoiceKey)
                val audio = tts.generate("This is the selected S•S Voice.", sid = 0, speed = 1.0f)
                val file = File(filesDir, "voice_test_${System.nanoTime()}.wav")
                audio.save(file.absolutePath)
                runOnUiThread {
                    player?.release()
                    player = MediaPlayer().apply {
                        setDataSource(file.absolutePath)
                        prepare()
                        setVolume(volume, volume)
                        setPlaybackParams(PlaybackParams().setSpeed(speed).setPitch(pitch))
                        setOnCompletionListener {
                            it.release()
                            player = null
                            status.text = "Ready • 3 local voices • English • Offline"
                        }
                        start()
                    }
                    status.text = "Testing • ${voiceLabels[selectedVoiceKey] ?: selectedVoiceKey}"
                }
            } catch (e: Exception) {
                runOnUiThread { status.text = "Voice test failed: ${e.message ?: "unknown error"}" }
            }
        }
    }

    private fun playNext() {
        val item = synchronized(audioQueue) {
            if (queueIndex < audioQueue.size) audioQueue[queueIndex] else null
        }

        if (item == null) {
            status.text = "Ready • 3 local voices • English • Offline"
            return
        }

        player?.release()
        player = MediaPlayer().apply {
            setDataSource(item.file.absolutePath)
            prepare()
            setVolume(item.settings.volume, item.settings.volume)
            setPlaybackParams(
                PlaybackParams()
                    .setSpeed(item.settings.speed)
                    .setPitch(item.settings.pitch)
            )
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
        if (::narratorTts.isInitialized) status.text = "Ready • 3 local voices • English • Offline"
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
        if (::narratorTts.isInitialized) narratorTts.release()
        if (::alexTts.isInitialized) alexTts.release()
        if (::ciroTts.isInitialized) ciroTts.release()
        super.onDestroy()
    }
}
