package com.ss.voice

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
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
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : Activity() {
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
    private lateinit var voiceLibraryStatus: TextView
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
    private lateinit var sceneContainer: LinearLayout
    private lateinit var sceneStatus: TextView
    private lateinit var saveSceneButton: Button
    private lateinit var cacheStatus: TextView
    private lateinit var clearCacheButton: Button

    private var player: MediaPlayer? = null
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val audioQueue = mutableListOf<AudioItem>()
    private val installedTts = mutableMapOf<String, OfflineTts>()
    private var queueIndex = 0
    private var speed = 1.0f
    private var pitch = 1.0f
    private var volume = 1.0f
    private var selectedProfile = "Narrator"
    private var selectedVoiceKey = ""
    private var audiobookMode = true
    private var sentencePauseMs = 300L
    private var speakerPauseMs = 500L
    private var paragraphPauseMs = 1000L
    private var dramaticPauseMs = 1500L

    data class VoiceDefinition(val key: String, val name: String, val language: String, val modelSize: String, val type: String, val style: String, val archiveUrl: String, val archiveName: String)
    data class DialogueLine(val speaker: String, val text: String, val paragraphBreakAfter: Boolean = false)
    data class VoiceSettings(val speed: Float, val pitch: Float, val volume: Float)
    data class AudioItem(val file: File, val settings: VoiceSettings, val speaker: String, val text: String, val durationMs: Long, val pauseAfterMs: Long)

    private val voiceCatalog = listOf(
        VoiceDefinition("ryan", "Ryan", "English (en-US)", "Medium", "männlich", "klar, ruhig, erwachsen", "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-en_US-ryan-medium.tar.bz2", "vits-piper-en_US-ryan-medium.tar.bz2"),
        VoiceDefinition("lessac", "Lessac", "English (en-US)", "Medium", "weiblich", "warm, weich, erwachsen", "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-en_US-lessac-medium.tar.bz2", "vits-piper-en_US-lessac-medium.tar.bz2"),
        VoiceDefinition("amy", "Amy", "English (en-US)", "Medium", "weiblich", "weich, natürlich, erwachsen", "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-en_US-amy-medium.tar.bz2", "vits-piper-en_US-amy-medium.tar.bz2"),
        VoiceDefinition("sweetbbak_amy", "SweetBBak Amy", "English (en-GB)", "Medium", "weiblich", "britisch, weich, natürlich", "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-en_GB-sweetbbak-amy.tar.bz2", "vits-piper-en_GB-sweetbbak-amy.tar.bz2")
    )

    private val profileNames = listOf("Narrator", "Alex", "Ciro")
    private val preferences by lazy { getSharedPreferences("voice_profiles", MODE_PRIVATE) }
    private val scenePreferences by lazy { getSharedPreferences("scenes", MODE_PRIVATE) }
    private val voicePreferences by lazy { getSharedPreferences("voice_library", MODE_PRIVATE) }
    private val cacheDirectory by lazy { File(filesDir, "audio_cache") }
    private val sceneAudioDirectory by lazy { File(filesDir, "scene_audio") }
    private val voiceDirectory by lazy { File(filesDir, "voices") }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        bindViews()
        cacheDirectory.mkdirs(); sceneAudioDirectory.mkdirs(); voiceDirectory.mkdirs()
        setupControls(); setupProfiles(); setupVoiceLibrary(); setupAudiobookMode(); refreshSceneList(); updateCacheStatus(); clearTimeline()
        loadInstalledVoices()
        findViewById<Button>(R.id.speakButton).setOnClickListener { speak() }
        findViewById<Button>(R.id.stopButton).setOnClickListener { stop() }
        saveProfileButton.setOnClickListener { saveCurrentProfile() }
        saveSceneButton.setOnClickListener { showSaveSceneDialog() }
        clearCacheButton.setOnClickListener { confirmClearCache() }
        status.text = "Voice Library bereit • keine Stimmen fest eingebaut"
    }

    private fun bindViews() {
        text = findViewById(R.id.textInput); status = findViewById(R.id.status)
        speedBar = findViewById(R.id.speedBar); pitchBar = findViewById(R.id.pitchBar); volumeBar = findViewById(R.id.volumeBar)
        speedValue = findViewById(R.id.speedValue); pitchValue = findViewById(R.id.pitchValue); volumeValue = findViewById(R.id.volumeValue)
        profileSpinner = findViewById(R.id.profileSpinner); voiceLibraryContainer = findViewById(R.id.voiceLibraryContainer); voiceLibraryStatus = findViewById(R.id.voiceLibraryStatus)
        timelineContainer = findViewById(R.id.timelineContainer); timelineStatus = findViewById(R.id.timelineStatus); saveProfileButton = findViewById(R.id.saveProfileButton)
        audiobookModeSwitch = findViewById(R.id.audiobookModeSwitch); sentencePauseBar = findViewById(R.id.sentencePauseBar); speakerPauseBar = findViewById(R.id.speakerPauseBar); paragraphPauseBar = findViewById(R.id.paragraphPauseBar); dramaticPauseBar = findViewById(R.id.dramaticPauseBar)
        sentencePauseValue = findViewById(R.id.sentencePauseValue); speakerPauseValue = findViewById(R.id.speakerPauseValue); paragraphPauseValue = findViewById(R.id.paragraphPauseValue); dramaticPauseValue = findViewById(R.id.dramaticPauseValue)
        sceneContainer = findViewById(R.id.sceneContainer); sceneStatus = findViewById(R.id.sceneStatus); saveSceneButton = findViewById(R.id.saveSceneButton)
        cacheStatus = findViewById(R.id.cacheStatus); clearCacheButton = findViewById(R.id.clearCacheButton)
    }

    private fun setupControls() {
        speedBar.progress = 50; pitchBar.progress = 50; volumeBar.progress = 100; updateControlLabels()
        speedBar.setOnSeekBarChangeListener(simpleListener { speed = 0.75f + it / 100f; updateControlLabels() })
        pitchBar.setOnSeekBarChangeListener(simpleListener { pitch = 0.75f + it / 100f; updateControlLabels() })
        volumeBar.setOnSeekBarChangeListener(simpleListener { volume = it / 100f; updateControlLabels(); player?.setVolume(volume, volume) })
    }

    private fun setupProfiles() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, profileNames); adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); profileSpinner.adapter = adapter
        profileSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { selectedProfile = profileNames[position]; loadProfile(selectedProfile) }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun setupVoiceLibrary() { refreshVoiceLibrary() }

    private fun refreshVoiceLibrary() {
        voiceLibraryContainer.removeAllViews()
        var installed = 0
        voiceCatalog.forEach { def ->
            val isInstalled = isVoiceInstalled(def.key); if (isInstalled) installed++
            val card = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(20, 16, 20, 16); background = GradientDrawable().apply { cornerRadius = 18f; setStroke(1, 0xFFCCCCCC.toInt()) } }
            val p = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); p.setMargins(0, 0, 0, 12); card.layoutParams = p
            card.addView(TextView(this).apply { text = def.name; textSize = 18f; setTypeface(typeface, Typeface.BOLD) })
            card.addView(TextView(this).apply { text = "${def.language} • ${def.modelSize}\n${def.type} • ${def.style}\nPiper • Lizenz/Metadaten: Modellpaket"; setPadding(0, 4, 0, 8) })
            val state = TextView(this).apply { text = if (isInstalled) "● Installiert • Offline • ${formatBytes(voiceDirectoryFor(def.key).walkTopDown().filter { it.isFile }.sumOf { it.length() })}" else "○ Nicht installiert • Download erforderlich" }
            card.addView(state)
            if (isInstalled) {
                val test = Button(this).apply { text = "▶ Test"; setOnClickListener { testVoice(def.key) } }
                val use = Button(this).apply { text = if (selectedVoiceKey == def.key) "✓ Für $selectedProfile ausgewählt" else "Als $selectedProfile-Stimme verwenden"; setOnClickListener { assignVoiceToCurrentProfile(def.key) } }
                val remove = Button(this).apply { text = "Stimme entfernen"; setOnClickListener { confirmRemoveVoice(def.key) } }
                card.addView(test); card.addView(use); card.addView(remove)
            } else {
                val download = Button(this).apply { text = "↓ Stimme herunterladen"; setOnClickListener { downloadVoice(def) } }
                card.addView(download)
            }
            voiceLibraryContainer.addView(card)
        }
        voiceLibraryStatus.text = "$installed/${voiceCatalog.size} Stimmen installiert • nur installierte Stimmen benötigen Speicher • Offline-Wiedergabe"
    }

    private fun isVoiceInstalled(key: String): Boolean = voiceDirectoryFor(key).walkTopDown().any { it.isFile && it.extension.equals("onnx", true) }
    private fun voiceDirectoryFor(key: String) = File(voiceDirectory, key)

    private fun downloadVoice(def: VoiceDefinition) {
        if (isVoiceInstalled(def.key)) return
        status.text = "Download startet • ${def.name}"
        executor.execute {
            val target = voiceDirectoryFor(def.key); target.mkdirs(); val archive = File(voiceDirectory, "${def.key}.tar.bz2")
            try {
                val connection = URL(def.archiveUrl).openConnection() as HttpURLConnection
                connection.connectTimeout = 20000; connection.readTimeout = 120000; connection.instanceFollowRedirects = true; connection.connect()
                if (connection.responseCode !in 200..299) throw IllegalStateException("HTTP ${connection.responseCode}")
                connection.inputStream.use { input -> FileOutputStream(archive).use { output -> input.copyTo(output) } }; connection.disconnect()
                runOnUiThread { status.text = "Entpacke • ${def.name}" }
                extractTarBz2(archive, target); archive.delete()
                val model = target.walkTopDown().firstOrNull { it.isFile && it.extension.equals("onnx", true) } ?: throw IllegalStateException("Kein ONNX-Modell im Paket gefunden")
                createAndStoreTts(def.key, model)
                runOnUiThread { refreshVoiceLibrary(); loadProfile(selectedProfile); status.text = "Installiert • ${def.name} • vollständig offline verfügbar" }
            } catch (e: Exception) {
                archive.delete(); target.deleteRecursively()
                runOnUiThread { refreshVoiceLibrary(); status.text = "Download fehlgeschlagen • ${def.name}: ${e.message ?: "unknown error"}" }
            }
        }
    }

    private fun extractTarBz2(archive: File, destination: File) {
        BZip2CompressorInputStream(archive.inputStream().buffered()).use { bz ->
            TarArchiveInputStream(bz).use { tar ->
                while (true) {
                    val entry = tar.nextTarEntry ?: break
                    val output = File(destination, entry.name).canonicalFile
                    if (!output.path.startsWith(destination.canonicalPath + File.separator)) continue
                    if (entry.isDirectory) output.mkdirs() else { output.parentFile?.mkdirs(); FileOutputStream(output).use { tar.copyTo(it) } }
                }
            }
        }
    }

    private fun loadInstalledVoices() {
        executor.execute {
            voiceCatalog.filter { isVoiceInstalled(it.key) }.forEach { def ->
                try { val model = voiceDirectoryFor(def.key).walkTopDown().first { it.isFile && it.extension.equals("onnx", true) }; createAndStoreTts(def.key, model) } catch (_: Exception) { }
            }
            runOnUiThread { refreshVoiceLibrary(); loadProfile(selectedProfile); status.text = if (installedTts.isEmpty()) "Voice Library bereit • Stimme zum Start herunterladen" else "Ready • ${installedTts.size} lokale Stimme(n) • Offline" }
        }
    }

    private fun createAndStoreTts(key: String, model: File) {
        installedTts[key]?.release()
        val root = model.parentFile ?: throw IllegalStateException("Ungültiges Modell")
        val dataDir = model.parentFile?.let { root.walkTopDown().firstOrNull { it.isDirectory && it.name == "espeak-ng-data" } } ?: findDirectory(voiceDirectoryFor(key), "espeak-ng-data")
        if (dataDir == null) throw IllegalStateException("espeak-ng-data fehlt")
        val config = getOfflineTtsConfig(modelDir = root.absolutePath, modelName = model.name, acousticModelName = "", vocoder = "", voices = "", lexicon = "", dataDir = dataDir.absolutePath, dictDir = "", ruleFsts = "", ruleFars = "", numThreads = 2)
        installedTts[key] = OfflineTts(config = config)
    }

    private fun findDirectory(root: File, name: String): File? = root.walkTopDown().firstOrNull { it.isDirectory && it.name == name }

    private fun confirmRemoveVoice(key: String) {
        val def = voiceCatalog.firstOrNull { it.key == key } ?: return
        val affected = getScenesUsingVoice(key)
        val message = if (affected.isEmpty()) "${def.name} wird vom Gerät gelöscht und gibt den Speicher frei." else "${def.name} wird vom Gerät gelöscht. ${affected.size} Szene(n) verwenden diese Stimme. Bereits erzeugtes Szenen-Audio bleibt erhalten. Neu generieren benötigt die Stimme wieder."
        AlertDialog.Builder(this).setTitle("${def.name} entfernen?").setMessage(message).setNegativeButton("Abbrechen", null).setPositiveButton("Entfernen") { _, _ -> removeVoice(key) }.show()
    }

    private fun removeVoice(key: String) {
        installedTts.remove(key)?.release(); voiceDirectoryFor(key).deleteRecursively(); refreshVoiceLibrary()
        status.text = "Stimme entfernt • vorhandenes Szenen-Audio bleibt erhalten"
    }

    private fun assignVoiceToCurrentProfile(key: String) {
        if (!isVoiceInstalled(key)) { status.text = "Stimme zuerst herunterladen"; return }
        selectedVoiceKey = key; preferences.edit().putString("${profileKey(selectedProfile)}_voice", key).apply(); refreshVoiceLibrary(); status.text = "Voice assigned • $selectedProfile • ${voiceName(key)}"
    }

    private fun loadProfile(profile: String) {
        val key = profileKey(profile); speed = preferences.getFloat("${key}_speed", 1.0f); pitch = preferences.getFloat("${key}_pitch", 1.0f); volume = preferences.getFloat("${key}_volume", 1.0f)
        selectedVoiceKey = preferences.getString("${key}_voice", "") ?: ""
        speedBar.progress = ((speed - 0.75f) * 100f).toInt().coerceIn(0, 100); pitchBar.progress = ((pitch - 0.75f) * 100f).toInt().coerceIn(0, 100); volumeBar.progress = (volume * 100f).toInt().coerceIn(0, 100); updateControlLabels(); refreshVoiceLibrary()
    }

    private fun saveCurrentProfile() {
        val key = profileKey(selectedProfile); preferences.edit().putFloat("${key}_speed", speed).putFloat("${key}_pitch", pitch).putFloat("${key}_volume", volume).putString("${key}_voice", selectedVoiceKey).apply(); status.text = "Profil gespeichert • $selectedProfile"
    }

    private fun profileKey(profile: String) = profile.lowercase().replace(" ", "_")
    private fun profileForSpeaker(speaker: String) = when (speaker.trim().lowercase()) { "alex", "ethyalexia", "ethyalexia eztliquies" -> "Alex"; "ciro", "cyrus", "cyrus d'amantino", "cyrus d’amantino" -> "Ciro"; else -> "Narrator" }
    private fun voiceKeyForSpeaker(speaker: String): String = preferences.getString("${profileKey(profileForSpeaker(speaker))}_voice", "") ?: ""
    private fun settingsForSpeaker(speaker: String): VoiceSettings { val k = profileKey(profileForSpeaker(speaker)); return VoiceSettings(preferences.getFloat("${k}_speed", 1f), preferences.getFloat("${k}_pitch", 1f), preferences.getFloat("${k}_volume", 1f)) }
    private fun voiceName(key: String) = voiceCatalog.firstOrNull { it.key == key }?.name ?: key

    private fun setupAudiobookMode() {
        audiobookMode = preferences.getBoolean("audiobook_mode", true); sentencePauseMs = preferences.getLong("sentence_pause_ms", 300L); speakerPauseMs = preferences.getLong("speaker_pause_ms", 500L); paragraphPauseMs = preferences.getLong("paragraph_pause_ms", 1000L); dramaticPauseMs = preferences.getLong("dramatic_pause_ms", 1500L); applyAudiobookUi()
        audiobookModeSwitch.setOnCheckedChangeListener { _, checked -> audiobookMode = checked; saveAudiobookSettings() }
        sentencePauseBar.setOnSeekBarChangeListener(pauseListener { sentencePauseMs = it * 10L }); speakerPauseBar.setOnSeekBarChangeListener(pauseListener { speakerPauseMs = it * 10L }); paragraphPauseBar.setOnSeekBarChangeListener(pauseListener { paragraphPauseMs = it.toLong() }); dramaticPauseBar.setOnSeekBarChangeListener(pauseListener { dramaticPauseMs = it.toLong() })
    }

    private fun applyAudiobookUi() { audiobookModeSwitch.isChecked = audiobookMode; sentencePauseBar.progress = (sentencePauseMs / 10).toInt().coerceIn(0, 100); speakerPauseBar.progress = (speakerPauseMs / 10).toInt().coerceIn(0, 100); paragraphPauseBar.progress = paragraphPauseMs.toInt().coerceIn(0, 2000); dramaticPauseBar.progress = dramaticPauseMs.toInt().coerceIn(0, 3000); updatePauseLabels() }
    private fun pauseListener(update: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener { override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) { update(p); updatePauseLabels(); if (fromUser) saveAudiobookSettings() }; override fun onStartTrackingTouch(s: SeekBar?) = Unit; override fun onStopTrackingTouch(s: SeekBar?) = Unit }
    private fun saveAudiobookSettings() { preferences.edit().putBoolean("audiobook_mode", audiobookMode).putLong("sentence_pause_ms", sentencePauseMs).putLong("speaker_pause_ms", speakerPauseMs).putLong("paragraph_pause_ms", paragraphPauseMs).putLong("dramatic_pause_ms", dramaticPauseMs).apply() }
    private fun updatePauseLabels() { sentencePauseValue.text = String.format(Locale.US, "%.2fs", sentencePauseMs / 1000f); speakerPauseValue.text = String.format(Locale.US, "%.2fs", speakerPauseMs / 1000f); paragraphPauseValue.text = String.format(Locale.US, "%.2fs", paragraphPauseMs / 1000f); dramaticPauseValue.text = String.format(Locale.US, "%.2fs", dramaticPauseMs / 1000f) }
    private fun simpleListener(action: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener { override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) { action(p) }; override fun onStartTrackingTouch(s: SeekBar?) = Unit; override fun onStopTrackingTouch(s: SeekBar?) = Unit }
    private fun updateControlLabels() { speedValue.text = String.format(Locale.US, "%.2fx", speed); pitchValue.text = String.format(Locale.US, "%.2fx", pitch); volumeValue.text = "${(volume * 100).toInt()}%" }

    private fun parseDialogue(input: String): List<DialogueLine> {
        val result = mutableListOf<DialogueLine>(); var currentSpeaker: String? = null; val currentText = StringBuilder()
        fun flush(paragraph: Boolean = false) { val v = currentText.toString().trim(); if (v.isNotEmpty()) result += DialogueLine(currentSpeaker ?: "Narrator", cleanText(v), paragraph); currentText.clear() }
        for (raw in input.lines()) { val line = raw.trim(); if (line.isEmpty()) { if (currentText.isNotEmpty()) { flush(true); currentSpeaker = null }; continue }; val m = Regex("^([A-Za-z0-9•._ -]{1,40}):\\s*(.*)$").matchEntire(line); if (m != null) { flush(); currentSpeaker = m.groupValues[1].trim(); currentText.append(m.groupValues[2]) } else { if (currentText.isNotEmpty()) currentText.append(' '); currentText.append(line) } }
        flush(); return result
    }
    private fun cleanText(v: String) = v.trim().removeSurrounding("\"").removeSurrounding("“", "”")

    private fun calculatePauseAfter(index: Int, line: DialogueLine, dialogue: List<DialogueLine>): Long {
        if (!audiobookMode || index == dialogue.lastIndex) return 0L; val next = dialogue[index + 1]; val changed = line.speaker.trim() != next.speaker.trim(); val v = line.text.trim()
        if (line.paragraphBreakAfter) return paragraphPauseMs
        if (v.contains("...") || v.contains("…")) return maxOf(dramaticPauseMs, if (changed) speakerPauseMs else 0L)
        if (changed) return speakerPauseMs
        if (v.endsWith(".") || v.endsWith("!") || v.endsWith("?") || v.endsWith(":") || v.endsWith(";")) return sentencePauseMs
        return 0L
    }

    private fun speak() {
        val input = text.text.toString().trim(); if (input.isEmpty()) return; stop(); audioQueue.clear(); clearTimeline(); queueIndex = 0
        val dialogue = parseDialogue(input); if (dialogue.isEmpty()) return
        executor.execute {
            try {
                val missing = dialogue.map { voiceKeyForSpeaker(it.speaker) }.distinct().filter { it.isBlank() || !installedTts.containsKey(it) }
                if (missing.isNotEmpty()) throw IllegalStateException("Stimme nicht installiert: ${missing.map { if (it.isBlank()) profileForSpeaker(dialogue.first().speaker) else voiceName(it) }.joinToString(", ")}")
                var hits = 0
                dialogue.forEachIndexed { index, line ->
                    runOnUiThread { status.text = "Generating ${index + 1}/${dialogue.size} • ${line.speaker}" }
                    val vk = voiceKeyForSpeaker(line.speaker); val settings = settingsForSpeaker(line.speaker); val file = getCacheFile(line.text, vk, settings)
                    if (file.exists()) hits++ else { installedTts[vk]?.generate(line.text, sid = 0, speed = 1f)?.save(file.absolutePath) ?: throw IllegalStateException("Stimme nicht geladen: ${voiceName(vk)}") }
                    val item = AudioItem(file, settings, line.speaker, line.text, getAudioDuration(file), calculatePauseAfter(index, line, dialogue)); synchronized(audioQueue) { audioQueue += item }
                }
                runOnUiThread { updateCacheStatus(); status.text = "Ready • ${dialogue.size} Zeilen • Cache $hits/${dialogue.size} wiederverwendet"; buildTimeline(); playNext() }
            } catch (e: Exception) { runOnUiThread { status.text = "Wiedergabe nicht möglich • ${e.message ?: "unknown error"}" } }
        }
    }

    private fun getCacheFile(textValue: String, voiceKey: String, settings: VoiceSettings): File { val source = "s3|$voiceKey|${settings.speed}|${settings.pitch}|${settings.volume}|$textValue"; val hash = MessageDigest.getInstance("SHA-256").digest(source.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }; return File(cacheDirectory, "$hash.wav") }
    private fun updateCacheStatus() { val files = cacheDirectory.listFiles()?.filter { it.isFile && it.extension.equals("wav", true) } ?: emptyList(); cacheStatus.text = "${files.size} Audio-Datei(en) • ${formatBytes(files.sumOf { it.length() })} • Szenen-Cache bleibt separat erhalten" }
    private fun formatBytes(bytes: Long): String { if (bytes < 1024) return "$bytes B"; if (bytes < 1024 * 1024) return String.format(Locale.US, "%.1f KB", bytes / 1024f); return String.format(Locale.US, "%.1f MB", bytes / (1024f * 1024f)) }

    private fun confirmClearCache() { val count = cacheDirectory.listFiles()?.count { it.isFile && it.extension.equals("wav", true) } ?: 0; if (count == 0) { status.text = "Audio Cache ist bereits leer"; return }; AlertDialog.Builder(this).setTitle("Audio Cache löschen?").setMessage("$count allgemeine Audio-Datei(en) werden gelöscht. Szenen-Audio und Stimmen bleiben erhalten.").setNegativeButton("Abbrechen", null).setPositiveButton("Löschen") { _, _ -> clearCache() }.show() }
    private fun clearCache() { stop(); cacheDirectory.listFiles()?.forEach { if (it.isFile) it.delete() }; audioQueue.clear(); clearTimeline(); updateCacheStatus(); status.text = "Allgemeiner Audio Cache gelöscht" }

    private fun getAudioDuration(file: File): Long { val r = MediaMetadataRetriever(); return try { r.setDataSource(file.absolutePath); r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L } finally { r.release() } }
    private fun buildTimeline() { timelineContainer.removeAllViews(); val items = synchronized(audioQueue) { audioQueue.toList() }; if (items.isEmpty()) { timelineStatus.text = "Noch keine Szene analysiert."; return }; var elapsed = 0L; items.forEachIndexed { i, item -> val start = elapsed; elapsed += item.durationMs; val end = elapsed; elapsed += item.pauseAfterMs; val card = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(18, 14, 18, 14); background = GradientDrawable().apply { cornerRadius = 18f; setStroke(1, 0xFFCCCCCC.toInt()) }; isClickable = true; setOnClickListener { replayTimelineItem(i) } }; card.addView(TextView(this).apply { text = "${formatTime(start)}  ${item.speaker}"; textSize = 17f; setTypeface(typeface, Typeface.BOLD) }); card.addView(TextView(this).apply { text = if (item.text.length > 110) item.text.take(110).trimEnd() + "…" else item.text; setPadding(0, 8, 0, 4) }); card.addView(TextView(this).apply { text = "${formatTime(start)} – ${formatTime(end)}${if (item.pauseAfterMs > 0) " • Pause ${formatSeconds(item.pauseAfterMs)}" else ""} • Tippen zum Wiederholen"; textSize = 12f }); timelineContainer.addView(card) }; timelineStatus.text = "${items.size} Abschnitte • ${items.map { it.speaker }.distinct().size} Sprecher • ${formatTime(elapsed)}" }
    private fun replayTimelineItem(index: Int) { val item = synchronized(audioQueue) { audioQueue.getOrNull(index) } ?: return; mainHandler.removeCallbacksAndMessages(null); player?.release(); player = null; playSingleItem(item, index, true) }
    private fun playSingleItem(item: AudioItem, index: Int, replay: Boolean = false) { player?.release(); player = MediaPlayer().apply { setDataSource(item.file.absolutePath); prepare(); setVolume(item.settings.volume, item.settings.volume); setPlaybackParams(PlaybackParams().setSpeed(item.settings.speed).setPitch(item.settings.pitch)); setOnCompletionListener { it.release(); player = null; if (replay) status.text = "Ready • Timeline-Abschnitt wiedergegeben" else { queueIndex = index + 1; if (item.pauseAfterMs > 0) mainHandler.postDelayed({ playNext() }, item.pauseAfterMs) else playNext() } }; start() }; status.text = if (replay) "Replay • ${item.speaker}" else "Playing • ${item.speaker}" }
    private fun playNext() { val item = synchronized(audioQueue) { audioQueue.getOrNull(queueIndex) }; if (item == null) { status.text = "Ready • Offline"; return }; playSingleItem(item, queueIndex) }

    private fun testVoice(key: String) { val tts = installedTts[key] ?: return; stop(); executor.execute { try { val audio = tts.generate("This is the ${voiceName(key)} S•S Voice.", sid = 0, speed = 1f); val file = File(cacheDirectory, "test_${key}_${System.nanoTime()}.wav"); audio.save(file.absolutePath); runOnUiThread { player = MediaPlayer().apply { setDataSource(file.absolutePath); prepare(); setVolume(volume, volume); setPlaybackParams(PlaybackParams().setSpeed(speed).setPitch(pitch)); setOnCompletionListener { it.release(); player = null; file.delete(); updateCacheStatus(); status.text = "Ready • Offline" }; start() }; status.text = "Testing • ${voiceName(key)}" } } catch (e: Exception) { runOnUiThread { status.text = "Voice test failed • ${e.message ?: "unknown error"}" } } } }
    private fun stop() { mainHandler.removeCallbacksAndMessages(null); player?.stop(); player?.release(); player = null; queueIndex = 0 }

    private fun showSaveSceneDialog() { val inputText = text.text.toString().trim(); if (inputText.isEmpty()) { status.text = "Scene not saved • Text ist leer"; return }; val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 8, 48, 0) }; val title = EditText(this).apply { hint = "Szenenname"; setSingleLine(true) }; val desc = EditText(this).apply { hint = "Beschreibung (optional)"; minLines = 2; gravity = Gravity.TOP }; layout.addView(title); layout.addView(desc); AlertDialog.Builder(this).setTitle("Szene speichern").setView(layout).setNegativeButton("Abbrechen", null).setPositiveButton("Speichern") { _, _ -> saveScene(title.text.toString().trim().ifEmpty { "Unbenannte Szene" }, desc.text.toString().trim(), inputText) }.show() }

    private fun saveScene(title: String, description: String, sceneText: String) {
        val scenes = getScenes(); val id = System.currentTimeMillis().toString() + "_" + System.nanoTime(); val scene = JSONObject().apply { put("id", id); put("title", title); put("description", description); put("text", sceneText); put("selected_profile", selectedProfile); put("audiobook_mode", audiobookMode); put("sentence_pause_ms", sentencePauseMs); put("speaker_pause_ms", speakerPauseMs); put("paragraph_pause_ms", paragraphPauseMs); put("dramatic_pause_ms", dramaticPauseMs); put("saved_at", System.currentTimeMillis()); val profiles = JSONArray(); profileNames.forEach { p -> val k = profileKey(p); profiles.put(JSONObject().apply { put("name", p); put("speed", preferences.getFloat("${k}_speed", 1f).toDouble()); put("pitch", preferences.getFloat("${k}_pitch", 1f).toDouble()); put("volume", preferences.getFloat("${k}_volume", 1f).toDouble()); put("voice", preferences.getString("${k}_voice", "")) }) }; put("profiles", profiles) }
        copyCurrentAudioToScene(id); scenes.put(scene); saveScenes(scenes); refreshSceneList(); status.text = "Scene gespeichert • $title • Szenen-Audio gesichert"
    }

    private fun copyCurrentAudioToScene(sceneId: String) { val items = synchronized(audioQueue) { audioQueue.toList() }; if (items.isEmpty()) return; val dir = File(sceneAudioDirectory, sceneId); dir.mkdirs(); items.forEachIndexed { i, item -> if (item.file.exists()) item.file.copyTo(File(dir, "%03d.wav".format(i)), overwrite = true) } }
    private fun sceneAudioFiles(sceneId: String): List<File> = File(sceneAudioDirectory, sceneId).listFiles()?.filter { it.isFile && it.extension.equals("wav", true) }?.sortedBy { it.name } ?: emptyList()
    private fun getScenes(): JSONArray = try { JSONArray(scenePreferences.getString("scene_data", "[]") ?: "[]") } catch (_: Exception) { JSONArray() }
    private fun saveScenes(scenes: JSONArray) { scenePreferences.edit().putString("scene_data", scenes.toString()).apply() }
    private fun getScenesUsingVoice(key: String): List<String> { val result = mutableListOf<String>(); val scenes = getScenes(); for (i in 0 until scenes.length()) { val s = scenes.optJSONObject(i) ?: continue; val profiles = s.optJSONArray("profiles") ?: continue; for (j in 0 until profiles.length()) if (profiles.optJSONObject(j)?.optString("voice", "") == key) { result += s.optString("title", "Unbenannte Szene"); break } }; return result }


    private fun refreshSceneList() { sceneContainer.removeAllViews(); val scenes = getScenes(); sceneStatus.text = "${scenes.length()} gespeicherte Szene(n)"; if (scenes.length() == 0) { sceneContainer.addView(TextView(this).apply { text = "Noch keine Szenen gespeichert."; setPadding(0, 8, 0, 8) }); return }; for (i in 0 until scenes.length()) { val s = scenes.optJSONObject(i) ?: continue; val id = s.optString("id", ""); val cached = sceneAudioFiles(id).size; val card = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(18, 14, 18, 14); background = GradientDrawable().apply { cornerRadius = 18f; setStroke(1, 0xFFCCCCCC.toInt()) } }; card.addView(TextView(this).apply { text = s.optString("title", "Unbenannte Szene"); textSize = 18f; setTypeface(typeface, Typeface.BOLD) }); card.addView(TextView(this).apply { text = if (s.optString("description").isBlank()) "Keine Beschreibung" else s.optString("description") }); card.addView(TextView(this).apply { text = "${s.optString("selected_profile", "Narrator")} • $cached gecachte Audio-Datei(en)"; textSize = 12f; setPadding(0, 5, 0, 5) }); val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }; val load = Button(this).apply { text = "Laden"; setOnClickListener { loadScene(i) } }; val dup = Button(this).apply { text = "Duplizieren"; setOnClickListener { duplicateScene(i) } }; val del = Button(this).apply { text = "Löschen"; setOnClickListener { confirmDeleteScene(i) } }; row.addView(load, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)); row.addView(dup, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)); row.addView(del, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)); card.addView(row); sceneContainer.addView(card) } }

    private fun loadScene(index: Int) { val scenes = getScenes(); val s = scenes.optJSONObject(index) ?: return; stop(); text.setText(s.optString("text", "")); s.optJSONArray("profiles")?.let { arr -> for (i in 0 until arr.length()) { val p = arr.optJSONObject(i) ?: continue; val n = p.optString("name", ""); if (!profileNames.contains(n)) continue; val k = profileKey(n); preferences.edit().putFloat("${k}_speed", p.optDouble("speed", 1.0).toFloat()).putFloat("${k}_pitch", p.optDouble("pitch", 1.0).toFloat()).putFloat("${k}_volume", p.optDouble("volume", 1.0).toFloat()).putString("${k}_voice", p.optString("voice", "")).apply() } }; audiobookMode = s.optBoolean("audiobook_mode", true); sentencePauseMs = s.optLong("sentence_pause_ms", 300L); speakerPauseMs = s.optLong("speaker_pause_ms", 500L); paragraphPauseMs = s.optLong("paragraph_pause_ms", 1000L); dramaticPauseMs = s.optLong("dramatic_pause_ms", 1500L); saveAudiobookSettings(); applyAudiobookUi(); val p = s.optString("selected_profile", "Narrator"); profileSpinner.setSelection(profileNames.indexOf(p).coerceAtLeast(0)); loadProfile(profileNames[profileNames.indexOf(p).coerceAtLeast(0)]); audioQueue.clear(); clearTimeline(); val files = sceneAudioFiles(s.optString("id", "")); if (files.isNotEmpty()) { val dialogue = parseDialogue(s.optString("text", "")); files.forEachIndexed { i, file -> val line = dialogue.getOrNull(i) ?: DialogueLine("Narrator", ""); val settings = settingsForSpeaker(line.speaker); audioQueue += AudioItem(file, settings, line.speaker, line.text, getAudioDuration(file), calculatePauseAfter(i, line, dialogue)) }; buildTimeline(); status.text = "Scene geladen • gespeichertes Szenen-Audio verfügbar" } else status.text = "Scene geladen • neues Audio benötigt installierte Stimmen" }
    private fun duplicateScene(index: Int) { val scenes = getScenes(); val original = scenes.optJSONObject(index) ?: return; val copy = JSONObject(original.toString()).apply { put("id", System.currentTimeMillis().toString() + "_" + System.nanoTime()); put("title", original.optString("title") + " (Kopie)"); put("saved_at", System.currentTimeMillis()) }; val newId = copy.optString("id"); val oldId = original.optString("id"); sceneAudioFiles(oldId).forEachIndexed { i, f -> val dir = File(sceneAudioDirectory, newId); dir.mkdirs(); f.copyTo(File(dir, "%03d.wav".format(i)), overwrite = true) }; scenes.put(copy); saveScenes(scenes); refreshSceneList(); status.text = "Scene dupliziert" }
    private fun confirmDeleteScene(index: Int) { val s = getScenes().optJSONObject(index) ?: return; AlertDialog.Builder(this).setTitle("Szene löschen?").setMessage("${s.optString("title")} und ihr Szenen-Audio werden gelöscht.").setNegativeButton("Abbrechen", null).setPositiveButton("Löschen") { _, _ -> deleteScene(index) }.show() }
    private fun deleteScene(index: Int) { val scenes = getScenes(); if (index !in 0 until scenes.length()) return; val id = scenes.optJSONObject(index)?.optString("id", "") ?: ""; File(sceneAudioDirectory, id).deleteRecursively(); val out = JSONArray(); for (i in 0 until scenes.length()) if (i != index) out.put(scenes.get(i)); saveScenes(out); refreshSceneList(); status.text = "Scene gelöscht" }

    private fun clearTimeline() { if (::timelineContainer.isInitialized) timelineContainer.removeAllViews(); if (::timelineStatus.isInitialized) timelineStatus.text = "Noch keine Szene analysiert." }
    private fun formatTime(ms: Long): String { val s = (ms / 1000).coerceAtLeast(0); return String.format(Locale.US, "%02d:%02d", s / 60, s % 60) }
    private fun formatSeconds(ms: Long) = String.format(Locale.US, "%.2fs", ms / 1000f)

    override fun onDestroy() { super.onDestroy(); mainHandler.removeCallbacksAndMessages(null); player?.release(); installedTts.values.forEach { it.release() }; installedTts.clear(); executor.shutdownNow() }
}
