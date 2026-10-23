package com.lillans.image.blixtsvar.core

import android.util.Log

class TriggerEngine(private val rules: List<TriggerRule>) {

    companion object {
        private const val TAG = "BlixtSvar"
        // Vanliga “kolon-lika” och “mellanrum-lika” tecken i operatörs-SMS
        private val COLON_VARIANTS = "[：﹕꞉]"       // U+FF1A, U+FE55, U+A789 m.fl.
        private val NBSP_VARIANTS  = "[\\u00A0\\u2007\\u202F]" // NBSP, Figure Space, Narrow NBSP
        private val DASH_VARIANTS  = "[\\u2012-\\u2015\\u2212]" // en/em/figure/minus dash
    }

    fun match(message: String): String? {
        // 1) Normalisera radbrytningar → mellanslag
        // 2) Normalisera NBSP/”smala” mellanrum → vanligt mellanslag
        // 3) Normalisera fullbredds-kolon m.fl. → ':'
        // 4) Normalisera typografiska streck → '-'
        // 5) Krympa multipla blanktecken
        val normalized = message
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .replace('\n', ' ')
            .replace(Regex(NBSP_VARIANTS), " ")
            .replace(Regex(COLON_VARIANTS), ":")
            .replace(Regex(DASH_VARIANTS), "-")
            .replace(Regex("\\s+"), " ")
            .trim()

        Log.d(TAG, "Engine.match(): normalized=\"$normalized\"")

        for (rule in rules) {
            if (!rule.enabled) continue
            if (rule.pattern.containsMatchIn(normalized)) {
                Log.d(TAG, "Engine.match(): MATCH on id=${rule.id}, pattern=/${rule.pattern.pattern}/ reply=\"${rule.reply}\"")
                return rule.reply
            } else {
                Log.d(TAG, "Engine.match(): NO-HIT on id=${rule.id}, pattern=/${rule.pattern.pattern}/")
            }
        }
        Log.d(TAG, "Engine.match(): NO MATCH")
        return null
    }
}
