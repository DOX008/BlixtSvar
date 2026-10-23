package com.lillans.image.blixtsvar.data

import android.content.Context
import com.lillans.image.blixtsvar.core.TriggerRule
import org.json.JSONArray
import org.json.JSONObject
import android.os.Build

class RuleStore(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("rules", Context.MODE_PRIVATE)

    fun load(): List<TriggerRule> {
        val raw = prefs.getString("rules_json", null) ?: return defaultRules()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val patternText = o.getString("pattern")
                val options = mutableSetOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
                TriggerRule(
                    id = o.getString("id"),
                    pattern = Regex(patternText, options),
                    reply = o.getString("reply"),
                    enabled = o.optBoolean("enabled", true)
                )
            }
        }.getOrElse { defaultRules() }
    }

    fun save(rules: List<TriggerRule>) {
        val arr = JSONArray()
        rules.forEach { r ->
            val o = JSONObject().apply {
                put("id", r.id)
                put("pattern", r.pattern.pattern)
                put("reply", r.reply)
                put("enabled", r.enabled)
            }
            arr.put(o)
        }
        prefs.edit().putString("rules_json", arr.toString()).apply()
    }

    private fun defaultRules() = listOf(
        TriggerRule("hej",   Regex("\\b(hej|hello|tja|tjena)\\b", RegexOption.IGNORE_CASE), "Hej! [BlixtSvar]", true),
        TriggerRule("saldo", Regex("\\bsaldo\\b", RegexOption.IGNORE_CASE),                  "Ditt saldo är 123 kr. [BlixtSvar]", true),
        TriggerRule("order", Regex("\\border\\b", RegexOption.IGNORE_CASE),                  "Din order är mottagen. [BlixtSvar]", true)
    )
}
