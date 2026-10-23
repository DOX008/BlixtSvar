package com.lillans.image.blixtsvar.tts

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale
import java.util.UUID

/**
 * Läser upp en text med lokal talsyntes enligt [TtsSettings].
 * Engångsobjekt: skapar en TextToSpeech-instans, läser upp och stänger den sedan.
 */
class TtsSpeaker(
    context: Context,
    private val settings: TtsSettings = TtsSettings(context),
) {
    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    private var finished = false

    fun speak(text: String, onFinished: () -> Unit = {}) {
        if (text.isBlank()) {
            onFinished()
            return
        }
        val engineName = settings.engine.ifEmpty { null }
        var created: TextToSpeech? = null
        created = TextToSpeech(appContext, { status ->
            val instance = created
            if (status != TextToSpeech.SUCCESS || instance == null) {
                Log.w(TAG, "TTS init misslyckades: $status")
                finish(instance, onFinished)
            } else {
                start(instance, text, onFinished)
            }
        }, engineName)
        tts = created
    }

    private fun start(engine: TextToSpeech, text: String, onFinished: () -> Unit) {
        val chosen = settings.voice.takeIf { it.isNotEmpty() }
            ?.let { name -> TtsVoices.localSwedish(engine).firstOrNull { it.name == name } }
            ?: TtsVoices.best(engine)

        if (chosen != null) {
            engine.setVoice(chosen)
        } else {
            engine.setLanguage(Locale("sv", "SE"))
        }
        engine.setSpeechRate(settings.rate)

        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) = finish(engine, onFinished)

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = finish(engine, onFinished)
            override fun onError(utteranceId: String?, errorCode: Int) = finish(engine, onFinished)
        })

        // Tvinga lokal syntes – aldrig nätverk.
        val params = Bundle().apply { putBoolean(EMBEDDED_TTS, true) }
        val result = engine.speak(text, TextToSpeech.QUEUE_ADD, params, UUID.randomUUID().toString())
        if (result != TextToSpeech.SUCCESS) {
            Log.w(TAG, "TTS speak misslyckades: $result")
            finish(engine, onFinished)
        }
    }

    @Synchronized
    private fun finish(engine: TextToSpeech?, onFinished: () -> Unit) {
        if (finished) return
        finished = true
        try {
            engine?.stop()
            engine?.shutdown()
        } catch (e: Exception) {
            Log.w(TAG, "TTS shutdown: ${e.message}")
        }
        tts = null
        onFinished()
    }

    /** Avbryt och frigör (t.ex. vid timeout). */
    fun cancel() = finish(tts) {}

    companion object {
        private const val TAG = "BlixtSvarTTS"
        private const val EMBEDDED_TTS = "embeddedTts"
    }
}
