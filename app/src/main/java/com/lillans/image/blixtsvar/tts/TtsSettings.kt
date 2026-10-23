package com.lillans.image.blixtsvar.tts

import android.content.Context

/** Egna inställningar för TTS – helt separata från regel-/SMS-svarssystemet. */
class TtsSettings(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(v) = prefs.edit().putBoolean(KEY_ENABLED, v).apply()

    /** Paketnamn för talsyntesmotor, tom sträng = systemets standardmotor. */
    var engine: String
        get() = prefs.getString(KEY_ENGINE, "") ?: ""
        set(v) = prefs.edit().putString(KEY_ENGINE, v).apply()

    /** Röstnamn, tom sträng = automatiskt (bästa lokala svenska rösten). */
    var voice: String
        get() = prefs.getString(KEY_VOICE, "") ?: ""
        set(v) = prefs.edit().putString(KEY_VOICE, v).apply()

    var rate: Float
        get() = (Math.round(prefs.getFloat(KEY_RATE, DEFAULT_RATE) * 100) / 100f)
            .coerceIn(MIN_RATE, MAX_RATE)
        set(v) = prefs.edit().putFloat(KEY_RATE, (Math.round(v * 100) / 100f).coerceIn(MIN_RATE, MAX_RATE)).apply()

    companion object {
        private const val PREFS = "tts_settings"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_ENGINE = "engine"
        private const val KEY_VOICE = "voice"
        private const val KEY_RATE = "rate"

        const val MIN_RATE = 0.5f
        const val MAX_RATE = 2.0f
        const val DEFAULT_RATE = 1.0f
    }
}
