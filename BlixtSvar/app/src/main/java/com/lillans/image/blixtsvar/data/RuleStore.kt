package com.lillans.image.blixtsvar.data

import android.content.Context
import com.lillans.image.blixtsvar.core.RulePresets
import com.lillans.image.blixtsvar.core.TriggerRule
import org.json.JSONArray
import org.json.JSONObject

class RuleStore(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("rules", Context.MODE_PRIVATE)

    fun load(): List<TriggerRule> {
        val raw = prefs.getString("rules_json", null) ?: return defaultRules()
        return runCatching {
            val arr = JSONArray(raw)
            var needsNormalization = false
            val rules = (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val patternText = o.getString("pattern")
                val options = mutableSetOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
                val uid = o.optString("uid").ifBlank {
                    needsNormalization = true
                    java.util.UUID.randomUUID().toString()
                }
                TriggerRule(
                    id = o.getString("id"),
                    pattern = Regex(patternText, options),
                    reply = o.getString("reply"),
                    enabled = o.optBoolean("enabled", true),
                    uid = uid
                )
            }
            if (needsNormalization) {
                persistRules(rules)
            }
            rules
        }.getOrElse { defaultRules() }
    }

    fun save(rules: List<TriggerRule>) {
        persistRules(rules)
    }

    fun loadIgnoredText(): String = prefs.getString("ignored_text", "") ?: ""

    fun saveIgnoredText(text: String) {
        prefs.edit().putString("ignored_text", text).apply()
    }

    private fun persistRules(rules: List<TriggerRule>) {
        val arr = JSONArray()
        rules.forEach { r ->
            val o = JSONObject().apply {
                put("id", r.id)
                put("pattern", r.pattern.pattern)
                put("reply", r.reply)
                put("enabled", r.enabled)
                put("uid", r.uid)
            }
            arr.put(o)
        }
        prefs.edit().putString("rules_json", arr.toString()).apply()
    }

    private fun defaultRules(): List<TriggerRule> = RulePresets.defaultRules
}
