package com.lillans.image.blixtsvar.tts

import android.speech.tts.TextToSpeech
import android.speech.tts.Voice

object TtsVoices {

    /** Installerade svenska röster som fungerar offline (lokala). */
    fun localSwedish(tts: TextToSpeech): List<Voice> {
        val voices = try {
            tts.voices
        } catch (_: Exception) {
            null
        }
        if (voices == null) return emptyList()
        return voices
            .filter { it.locale.language == "sv" }
            .filter { !it.isNetworkConnectionRequired }
            .filter { !it.features.orEmpty().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) }
            .sortedWith(
                compareByDescending<Voice> { it.quality }
                    .thenBy { it.latency }
                    .thenBy { it.name }
            )
    }

    /** Bästa lokala svenska rösten, eller null om ingen finns. */
    fun best(tts: TextToSpeech): Voice? = localSwedish(tts).firstOrNull()

    /** Läsbart namn för röstmenyn. */
    fun label(voice: Voice): String {
        val country = voice.locale.country
        val loc = if (country.isNullOrEmpty()) voice.locale.language else "${voice.locale.language}-$country"
        return "${voice.name} ($loc)"
    }
}
