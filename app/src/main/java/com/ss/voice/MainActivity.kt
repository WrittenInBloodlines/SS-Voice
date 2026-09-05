package com.ss.voice

import android.app.Activity
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.getOfflineTtsConfig
import java.io.File
import java.util.Locale
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
    private lateinit var voiceLibraryContainer: LinearLayout
    private lateinit var timelineContainer: LinearLayout
    private lateinit var timelineStatus: TextView
    private lateinit var saveProfileButton: Button
    private lateinit var audiobookModeSwitch: Switch
    private lateinit var sentencePauseBar: SeekBar
    private lateinit var speakerPauseBar: SeekBar
    private lateinit var paragraphPauseBar: SeekBar
    private lateinit var dramaticPauseBar: SeekBar
    private lateinit var sentencePauseValue: TextView
    private lateinit var speakerPauseValue: TextView
    private lateinit var paragraphPauseValue: TextView
    private lateinit var dramaticPauseValue: TextView

    private var player: MediaPlayer? = null
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val audioQueue = mutableListOf<AudioItem>()
    private var queueIndex = 0
    private var speed = 1.0f
    private var pitch = 1.0f
    private var volume = 1.0f
    private var selectedProfile = "Narrator"
    private var selectedVoiceKey = "ryan"

    private var audiobookMode = true
    private var sentencePauseMs = 300L
    private var speakerPauseMs = 500L
    private var paragraphPauseMs = 1000L
    private var dramaticPauseMs = 1500L

    private val profileNames = listOf("Narrator", "Alex", "Ciro")
    private val voiceKeys = listOf("ryan", "lessac", "amy")
    private val voiceLabels = mapOf("ryan" to "Ryan", "lessac" to "Lessac", "amy" to "Amy")
    private val voiceDescriptions = mapOf(
        "ryan" to "English • Medium • Clear adult voice",
        "lessac" to "English • Medium • Warm adult voice",
        "amy" to "English • Medium • Soft adult voice"
    )
    private val preferences by lazy { getSharedPreferences("voice_profiles", MODE_PRIVATE) }

    data class DialogueLine(val speaker: String, val text: String, val paragraphBreakAfter: Boolean = false)
    data class VoiceSettings(val speed: Float, val pitch: Float, val volume: Float)
    data class AudioItem(val file: File, val settings: VoiceSettings, val speaker: String, val text: String, val durationMs: Long, val pauseAfterMs: Long)

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
        voiceLibraryContainer = findViewById(R.id.voiceLibraryContainer)
        timelineContainer = findViewById(R.id.timelineContainer)
        timelineStatus = findViewById(R.id.timelineStatus)
        saveProfileButton = findViewById(R.id.saveProfileButton)
        audiobookModeSwitch = findViewById(R.id.audiobookModeSwitch)
        sentencePauseBar = findViewById(R.id.sentencePauseBar)
        speakerPauseBar = findViewById(R.id.speakerPauseBar)
        paragraphPauseBar = findViewById(R.id.paragraphPauseBar)
        dramaticPauseBar = findViewById(R.id.dramaticPauseBar)
        sentencePauseValue = findViewById(R.id.sentencePauseValue)
        speakerPauseValue = findViewById(R.id.speakerPauseValue)
        paragraphPauseValue = findViewById(R.id.paragraphPauseValue)
        dramaticPauseValue = findViewById(R.id.dramaticPauseValue)

        setupControls()
        setupProfiles()
        setupVoiceLibrary()
        setupAudiobookMode()
        clearTimeline()

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
    }

    private fun setupProfiles() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, profileNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        profileSpinner.adapter = adapter
        profileSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedProfile = profileNames[position]
                loadProfile(selectedProfile)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun setupVoiceLibrary() {
        voiceLibraryContainer.removeAllViews()
        voiceKeys.forEach { key ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(20, 16, 20, 16)
                background = GradientDrawable().apply {
                    cornerRadius = 18f
                    setStroke(1, 0xFFCCCCCC.toInt())
                }
            }
            val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            params.setMargins(0, 0, 0, 12)
            card.layoutParams = params
            val title = TextView(this).apply { text = voiceLabels[key] ?: key; textSize = 18f; setTypeface(typeface, Typeface.BOLD) }
            val info = TextView(this).apply { text = voiceDescriptions[key] ?: "English • Local voice"; setPadding(0, 4, 0, 8) }
            val offline = TextView(this).apply { text = "● Installed • Offline" }
            val test = Button(this).apply { text = "▶ Test"; setOnClickListener { testVoice(key) } }
            val use = Button(this).apply { text = "Als ${selectedProfile}-Stimme verwenden"; setOnClickListener { assignVoiceToCurrentProfile(key) } }
            card.addView(title); card.addView(info); card.addView(offline); card.addView(test); card.addView(use)
            voiceLibraryContainer.addView(card)
        }
    }

    private fun setupControls() {
        speedBar.progress = 50
        pitchBar.progress = 50
        volumeBar.progress = 100
        updateControlLabels()
        speedBar.setOnSeekBarChangeListener(simpleListener { progress -> speed = 0.75f + progress / 100f; updateControlLabels() })
        pitchBar.setOnSeekBarChangeListener(simpleListener { progress -> pitch = 0.75f + progress / 100f; updateControlLabels() })
        volumeBar.setOnSeekBarChangeListener(simpleListener { progress -> volume = progress / 100f; updateControlLabels(); player?.setVolume(volume, volume) })
    }

    private fun setupAudiobookMode() {
        audiobookMode = preferences.getBoolean("audiobook_mode", true)
        sentencePauseMs = preferences.getLong("sentence_pause_ms", 300L)
        speakerPauseMs = preferences.getLong("speaker_pause_ms", 500L)
        paragraphPauseMs = preferences.getLong("paragraph_pause_ms", 1000L)
        dramaticPauseMs = preferences.getLong("dramatic_pause_ms", 1500L)
        audiobookModeSwitch.isChecked = audiobookMode
        sentencePauseBar.progress = (sentencePauseMs.toInt().coerceIn(0, 1000) / 10)
        speakerPauseBar.progress = (speakerPauseMs.toInt().coerceIn(0, 1000) / 10)
        paragraphPauseBar.progress = paragraphPauseMs.toInt().coerceIn(0, 2000)
        dramaticPauseBar.progress = dramaticPauseMs.toInt().coerceIn(0, 3000)
        updatePauseLabels()

        audiobookModeSwitch.setOnCheckedChangeListener { _, checked ->
            audiobookMode = checked
            saveAudiobookSettings()
            status.text = if (checked) "Hörspiel-Modus • automatische Pausen aktiv" else "Hörspiel-Modus • automatische Pausen aus"
        }
        sentencePauseBar.setOnSeekBarChangeListener(pauseListener { sentencePauseMs = it * 10L })
        speakerPauseBar.setOnSeekBarChangeListener(pauseListener { speakerPauseMs = it * 10L })
        paragraphPauseBar.setOnSeekBarChangeListener(pauseListener { paragraphPauseMs = it.toLong() })
        dramaticPauseBar.setOnSeekBarChangeListener(pauseListener { dramaticPauseMs = it.toLong() })
    }

    private fun pauseListener(update: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            update(progress)
            updatePauseLabels()
            if (fromUser) saveAudiobookSettings()
        }
        override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
        override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
    }

    private fun updatePauseLabels() {
        sentencePauseValue.text = String.format(Locale.US, "%.2fs", sentencePauseMs / 1000f)
        speakerPauseValue.text = String.format(Locale.US, "%.2fs", speakerPauseMs / 1000f)
        paragraphPauseValue.text = String.format(Locale.US, "%.2fs", paragraphPauseMs / 1000f)
        dramaticPauseValue.text = String.format(Locale.US, "%.2fs", dramaticPauseMs / 1000f)
    }

    private fun saveAudiobookSettings() {
        preferences.edit()
            .putBoolean("audiobook_mode", audiobookMode)
            .putLong("sentence_pause_ms", sentencePauseMs)
            .putLong("speaker_pause_ms", speakerPauseMs)
            .putLong("paragraph_pause_ms", paragraphPauseMs)
            .putLong("dramatic_pause_ms", dramaticPauseMs)
            .apply()
    }

    private fun simpleListener(onProgress: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { onProgress(progress) }
        override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
        override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
    }

    private fun updateControlLabels() {
        speedValue.text = String.format(Locale.US, "%.2fx", speed)
        pitchValue.text = String.format(Locale.US, "%.2fx", pitch)
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
        updateControlLabels()
        if (::voiceLibraryContainer.isInitialized) refreshVoiceCardButtons()
    }

    private fun refreshVoiceCardButtons() {
        for (i in 0 until voiceLibraryContainer.childCount) {
            val card = voiceLibraryContainer.getChildAt(i) as? LinearLayout ?: continue
            val useButton = card.getChildAt(4) as? Button ?: continue
            val key = voiceKeys[i]
            useButton.text = if (key == selectedVoiceKey) "✓ Für $selectedProfile ausgewählt" else "Als $selectedProfile-Stimme verwenden"
        }
    }

    private fun defaultVoiceForProfile(profile: String): String = when (profile) {
        "Alex" -> "lessac"
        "Ciro" -> "amy"
        else -> "ryan"
    }

    private fun assignVoiceToCurrentProfile(key: String) {
        selectedVoiceKey = key
        val profileKey = profileKey(selectedProfile)
        preferences.edit().putString("${profileKey}_voice", key).apply()
        refreshVoiceCardButtons()
        status.text = "Voice assigned • $selectedProfile • ${voiceLabels[key] ?: key}"
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

    private fun profileForSpeaker(speaker: String): String = when (speaker.trim().lowercase()) {
        "alex", "ethyalexia", "ethyalexia eztliquies" -> "Alex"
        "ciro", "cyrus", "cyrus d'amantino", "cyrus d’amantino" -> "Ciro"
        else -> "Narrator"
    }

    private fun voiceKeyForSpeaker(speaker: String): String {
        val profile = profileForSpeaker(speaker)
        val key = profileKey(profile)
        return preferences.getString("${key}_voice", defaultVoiceForProfile(profile)) ?: defaultVoiceForProfile(profile)
    }

    private fun settingsForSpeaker(speaker: String): VoiceSettings {
        val key = profileKey(profileForSpeaker(speaker))
        return VoiceSettings(
            preferences.getFloat("${key}_speed", 1.0f),
            preferences.getFloat("${key}_pitch", 1.0f),
            preferences.getFloat("${key}_volume", 1.0f)
        )
    }

    private fun voiceForKey(key: String): OfflineTts = when (key) {
        "lessac" -> alexTts
        "amy" -> ciroTts
        else -> narratorTts
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
        var paragraphBreakPending = false

        fun flush(paragraphBreakAfter: Boolean = false) {
            val value = currentText.toString().trim()
            if (value.isNotEmpty()) result += DialogueLine(currentSpeaker ?: "Narrator", cleanText(value), paragraphBreakAfter)
            currentText.clear()
        }

        for (rawLine in input.lines()) {
            val line = rawLine.trim()
            if (line.isEmpty()) {
                if (currentText.isNotEmpty()) {
                    flush(paragraphBreakAfter = true)
                    currentSpeaker = null
                    paragraphBreakPending = true
                }
                continue
            }
            val match = Regex("^([A-Za-z0-9•._ -]{1,40}):\\s*(.*)$").matchEntire(line)
            if (match != null) {
                flush()
                currentSpeaker = match.groupValues[1].trim()
                paragraphBreakPending = false
                currentText.append(match.groupValues[2])
            } else {
                if (currentText.isNotEmpty()) currentText.append(' ')
                currentText.append(line)
            }
        }
        flush()
        return result
    }

    private fun cleanText(value: String): String = value.trim().removeSurrounding("\"").removeSurrounding("“", "”")

    private fun calculatePauseAfter(index: Int, line: DialogueLine, dialogue: List<DialogueLine>): Long {
        if (!audiobookMode || index == dialogue.lastIndex) return 0L
        val next = dialogue[index + 1]
        val text = line.text.trim()
        val speakerChanged = line.speaker.trim() != next.speaker.trim()
        if (line.paragraphBreakAfter) return paragraphPauseMs
        if (text.contains("...") || text.contains("…")) return maxOf(dramaticPauseMs, if (speakerChanged) speakerPauseMs else 0L)
        if (speakerChanged) return speakerPauseMs
        if (text.endsWith(".") || text.endsWith("!") || text.endsWith("?") || text.endsWith(":") || text.endsWith(";")) return sentencePauseMs
        return 0L
    }

    private fun speak() {
        if (!::narratorTts.isInitialized || !::alexTts.isInitialized || !::ciroTts.isInitialized) return
        val input = text.text.toString().trim()
        if (input.isEmpty()) return
        stop()
        audioQueue.clear()
        queueIndex = 0
        clearTimeline()
        saveAudiobookSettings()
        val dialogue = parseDialogue(input)
        if (dialogue.isEmpty()) return
        val speakers = dialogue.map { it.speaker }.distinct()
        status.text = "Parsing • ${speakers.size} speaker(s) • ${speakers.joinToString(", ")}"
        timelineStatus.text = "Generiere Sprecher-Timeline..."
        executor.execute {
            try {
                dialogue.forEachIndexed { index, line ->
                    runOnUiThread { status.text = "Generating ${index + 1}/${dialogue.size} • ${line.speaker}" }
                    val audio = voiceForKey(voiceKeyForSpeaker(line.speaker)).generate(line.text, sid = 0, speed = 1.0f)
                    val file = File(filesDir, "dialogue_${index}_${System.nanoTime()}.wav")
                    audio.save(file.absolutePath)
                    val duration = getAudioDuration(file)
                    val pauseAfter = calculatePauseAfter(index, line, dialogue)
                    synchronized(audioQueue) {
                        audioQueue += AudioItem(file, settingsForSpeaker(line.speaker), line.speaker, line.text, duration, pauseAfter)
                    }
                }
                runOnUiThread {
                    status.text = "Ready • ${dialogue.size} lines • ${speakers.size} speaker(s) detected"
                    buildTimeline()
                    playNext()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    timelineStatus.text = "Timeline konnte nicht erstellt werden."
                    status.text = "Generation failed: ${e.message ?: "unknown error"}"
                }
            }
        }
    }

    private fun getAudioDuration(file: File): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } finally {
            retriever.release()
        }
    }

    private fun buildTimeline() {
        timelineContainer.removeAllViews()
        val items = synchronized(audioQueue) { audioQueue.toList() }
        if (items.isEmpty()) {
            timelineStatus.text = "Noch keine Szene analysiert."
            return
        }
        var elapsed = 0L
        items.forEachIndexed { index, item ->
            val start = elapsed
            elapsed += item.durationMs
            val audioEnd = elapsed
            elapsed += item.pauseAfterMs
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(18, 14, 18, 14)
                background = GradientDrawable().apply { cornerRadius = 18f; setStroke(1, 0xFFCCCCCC.toInt()) }
                isClickable = true
                isFocusable = true
                setOnClickListener { replayTimelineItem(index) }
            }
            val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            params.setMargins(0, 0, 0, 10)
            card.layoutParams = params
            val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
            val time = TextView(this).apply { text = formatTime(start); textSize = 13f }
            val speaker = TextView(this).apply { text = "  ${item.speaker}"; textSize = 17f; setTypeface(typeface, Typeface.BOLD) }
            header.addView(time); header.addView(speaker)
            val preview = TextView(this).apply {
                text = if (item.text.length > 110) item.text.take(110).trimEnd() + "…" else item.text
                textSize = 15f
                setPadding(0, 8, 0, 4)
            }
            val pauseLabel = if (item.pauseAfterMs > 0L) " • Pause ${formatSeconds(item.pauseAfterMs)}" else ""
            val meta = TextView(this).apply { text = "${formatTime(start)} – ${formatTime(audioEnd)}$pauseLabel • Tippen zum Wiederholen"; textSize = 12f }
            card.addView(header); card.addView(preview); card.addView(meta)
            timelineContainer.addView(card)
        }
        timelineStatus.text = "${items.size} Abschnitte • ${items.map { it.speaker }.distinct().size} Sprecher • ${formatTime(elapsed)} • Pausen ${if (audiobookMode) "aktiv" else "aus"}"
    }

    private fun replayTimelineItem(index: Int) {
        val item = synchronized(audioQueue) { audioQueue.getOrNull(index) } ?: return
        mainHandler.removeCallbacksAndMessages(null)
        player?.stop()
        player?.release()
        player = null
        playSingleItem(item, index, timelineReplay = true)
    }

    private fun playSingleItem(item: AudioItem, index: Int, timelineReplay: Boolean = false) {
        player?.release()
        player = MediaPlayer().apply {
            setDataSource(item.file.absolutePath)
            prepare()
            setVolume(item.settings.volume, item.settings.volume)
            setPlaybackParams(PlaybackParams().setSpeed(item.settings.speed).setPitch(item.settings.pitch))
            setOnCompletionListener {
                it.release()
                player = null
                if (timelineReplay) {
                    status.text = "Ready • Timeline-Abschnitt wiedergegeben"
                } else {
                    queueIndex = index + 1
                    if (item.pauseAfterMs > 0L) {
                        status.text = "Pause • ${item.speaker}"
                        mainHandler.postDelayed({ playNext() }, item.pauseAfterMs)
                    } else playNext()
                }
            }
            start()
        }
        status.text = if (timelineReplay) "Replay • ${item.speaker}" else "Playing • ${item.speaker}"
    }

    private fun testVoice(key: String) {
        if (!::narratorTts.isInitialized || !::alexTts.isInitialized || !::ciroTts.isInitialized) return
        stop()
        executor.execute {
            try {
                val audio = voiceForKey(key).generate("This is the ${voiceLabels[key] ?: key} S•S Voice.", sid = 0, speed = 1.0f)
                val file = File(filesDir, "voice_test_${key}_${System.nanoTime()}.wav")
                audio.save(file.absolutePath)
                runOnUiThread {
                    player?.release()
                    player = MediaPlayer().apply {
                        setDataSource(file.absolutePath); prepare(); setVolume(volume, volume)
                        setPlaybackParams(PlaybackParams().setSpeed(speed).setPitch(pitch))
                        setOnCompletionListener { it.release(); player = null; status.text = "Ready • 3 local voices • English • Offline" }
                        start()
                    }
                    status.text = "Testing • ${voiceLabels[key] ?: key}"
                }
            } catch (e: Exception) {
                runOnUiThread { status.text = "Voice test failed: ${e.message ?: "unknown error"}" }
            }
        }
    }

    private fun playNext() {
        val item = synchronized(audioQueue) { if (queueIndex < audioQueue.size) audioQueue[queueIndex] else null }
        if (item == null) {
            status.text = "Ready • 3 local voices • English • Offline"
            return
        }
        playSingleItem(item, queueIndex)
    }

    private fun stop() {
        mainHandler.removeCallbacksAndMessages(null)
        player?.stop()
        player?.release()
        player = null
        queueIndex = 0
        if (::narratorTts.isInitialized) status.text = "Ready • 3 local voices • English • Offline"
    }

    private fun clearTimeline() {
        if (::timelineContainer.isInitialized) timelineContainer.removeAllViews()
        if (::timelineStatus.isInitialized) timelineStatus.text = "Noch keine Szene analysiert."
    }

    private fun formatTime(ms: Long): String {
        val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
        return String.format(Locale.US, "%02d:%02d", totalSeconds / 60L, totalSeconds % 60L)
    }

    private fun formatSeconds(ms: Long): String = String.format(Locale.US, "%.2fs", ms / 1000f)

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
            val childDestination = File(destination, entry)
            if (assets.list(childPath)?.isNotEmpty() == true) {
                if (!childDestination.exists()) childDestination.mkdirs()
                copyAssetContents(childPath, childDestination)
            } else if (!childDestination.exists()) {
                assets.open(childPath).use { input -> childDestination.outputStream().use { output -> input.copyTo(output) } }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacksAndMessages(null)
        player?.release()
        player = null
        if (::narratorTts.isInitialized) narratorTts.release()
        if (::alexTts.isInitialized) alexTts.release()
        if (::ciroTts.isInitialized) ciroTts.release()
        executor.shutdownNow()
    }
}
