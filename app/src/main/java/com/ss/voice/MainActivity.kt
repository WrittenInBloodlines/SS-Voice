package com.ss.voice

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {
    private lateinit var tts: TextToSpeech
    private lateinit var text: EditText
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        text = findViewById(R.id.textInput)
        status = findViewById(R.id.status)
        tts = TextToSpeech(this, this)

        findViewById<Button>(R.id.speakButton).setOnClickListener {
            if (::tts.isInitialized) {
                tts.speak(text.text.toString(), TextToSpeech.QUEUE_FLUSH, null, "ss_voice_preview")
            }
        }

        findViewById<Button>(R.id.stopButton).setOnClickListener {
            if (::tts.isInitialized) tts.stop()
        }
    }

    override fun onInit(result: Int) {
        if (result == TextToSpeech.SUCCESS) {
            tts.language = Locale.GERMAN
            status.text = "Bereit • Lokaler TTS-Test"
        } else {
            status.text = "TTS konnte nicht gestartet werden"
        }
    }

    override fun onDestroy() {
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        super.onDestroy()
    }
}
