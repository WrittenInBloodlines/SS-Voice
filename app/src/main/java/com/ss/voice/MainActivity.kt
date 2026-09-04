package com.ss.voice

import android.app.Activity
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import java.util.Locale

class MainActivity : Activity(), TextToSpeech.OnInitListener {
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
            tts.speak(text.text.toString(), TextToSpeech.QUEUE_FLUSH, null, "preview")
        }
        findViewById<Button>(R.id.stopButton).setOnClickListener { tts.stop() }
    }

    override fun onInit(result: Int) {
        if (result == TextToSpeech.SUCCESS) {
            tts.language = Locale.US
            status.text = "Ready • Android TTS • English"
        } else {
            status.text = "TTS could not be started"
        }
    }

    override fun onDestroy() {
        tts.stop()
        tts.shutdown()
        super.onDestroy()
    }
}
