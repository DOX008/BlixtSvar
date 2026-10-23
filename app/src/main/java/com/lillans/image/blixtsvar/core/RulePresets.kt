package com.lillans.image.blixtsvar.core

object RulePresets {

    private data class TimePreset(
        val id: String,
        val startTime: String,
        val endTime: String
    )

    private val TIME_INPUT = Regex("""^\s*(\d{1,2}):([0-5]\d)\s*$""")
    private const val OPTIONAL_DATE = """(?:\d{1,2}\s+\p{L}{3,}\s+(?:\d{4}\s+)?)?"""
    private val timePresets = listOf(
        TimePreset("Dag", "07:00", "16:00"),
        TimePreset("Kväll", "14:00", "21:00"),
        TimePreset("Natt", "21:00", "07:00")
    )
    private val timePresetsById = timePresets.associateBy { it.id }
    private val presetIdAliases = mapOf(
        "Dag" to setOf("Dag"),
        "Kväll" to setOf("Kväll", "Kvall"),
        "Natt" to setOf("Natt")
    )
    val timePresetIds: Set<String> = timePresets.map { it.id }.toSet()

    val legacyTimePatternsById = mapOf(
        "Dag" to setOf("(?s)(?=.*0?7:00)(?=.*16:00)"),
        "Kväll" to setOf("(?s)(?=.*14:00)(?=.*21:00)"),
        "Natt" to setOf(
            "(?s)(?=.*20:55)(?=.*0?6:55)",
            "(?s)(?=.*21:00)(?=.*07:00)",
            "(?s)(?=.*21:00)(?=.*07:00)(?=.*21:00.*07:00)"
        )
    )

    val defaultRules: List<TriggerRule> = listOf(
        TriggerRule(
            "hej",
            Regex("\\b(hej|hello|tja|tjena)\\b", RegexOption.IGNORE_CASE),
            "Hej! [BlixtSvar]"
        ),
        TriggerRule(
            "saldo",
            Regex("\\bsaldo\\b", RegexOption.IGNORE_CASE),
            "Ditt saldo är 123 kr. [BlixtSvar]"
        ),
        TriggerRule(
            "order",
            Regex("\\border\\b", RegexOption.IGNORE_CASE),
            "Din order är mottagen. [BlixtSvar]"
        ),
    ) + timePresets.map { preset ->
        buildTimeRule(preset.id, preset.startTime, preset.endTime)
    }

    fun normalizeTimeInput(input: String): String? {
        val match = TIME_INPUT.matchEntire(input) ?: return null
        val hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2]
        if (hour !in 0..23) return null
        return "%02d:%s".format(hour, minute)
    }

    fun buildTimeRule(id: String, startTime: String, endTime: String, reply: String = "JA"): TriggerRule =
        TriggerRule(
            id = id,
            pattern = Regex(buildOrderedTimeRangePattern(startTime, endTime), setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)),
            reply = reply
        )

    fun buildOrderedTimeRangePattern(startTime: String, endTime: String): String {
        val normalizedStart = normalizeTimeInput(startTime)
            ?: error("Ogiltig starttid: $startTime")
        val normalizedEnd = normalizeTimeInput(endTime)
            ?: error("Ogiltig sluttid: $endTime")
        return """(?s)$OPTIONAL_DATE\b${timeToken(normalizedStart)}\b${textWithinShiftEntryPattern()}${timeToken(normalizedEnd)}\b"""
    }

    fun upsertTimeRule(existingRules: List<TriggerRule>, newRule: TriggerRule): List<TriggerRule> {
        val rules = existingRules.toMutableList()
        val existingIndex = findExistingTimeRuleIndex(rules, newRule)
        if (existingIndex >= 0) {
            rules[existingIndex] = newRule.copy(
                reply = rules[existingIndex].reply,
                enabled = rules[existingIndex].enabled,
                uid = rules[existingIndex].uid
            )
        } else {
            rules += newRule
        }
        return rules
    }

    fun suggestedRuleId(startTime: String, endTime: String): String =
        timePresets.firstOrNull { it.startTime == startTime && it.endTime == endTime }?.id
            ?: generatedTimeRuleId(startTime, endTime)

    fun displayNameFor(rule: TriggerRule): String {
        val canonicalPresetId = canonicalPresetId(rule.id)
        val times = timeRangeForRuleId(rule.id) ?: return rule.id
        return if (canonicalPresetId != null) {
            "$canonicalPresetId (${times.first}-${times.second})"
        } else {
            "Tid ${times.first}-${times.second}"
        }
    }

    fun isGeneratedTimeRuleId(id: String): Boolean = parseGeneratedTimeRuleId(id) != null

    fun migrateStoredTimeRule(rule: TriggerRule): TriggerRule {
        val canonicalId = canonicalPresetId(rule.id) ?: rule.id
        val times = timeRangeForRuleId(rule.id) ?: return rule
        val normalizedUid = rule.uid.ifBlank { java.util.UUID.randomUUID().toString() }
        val migratedPattern = buildTimeRule(canonicalId, times.first, times.second, rule.reply).pattern
        return if (rule.id == canonicalId &&
            rule.pattern.pattern == migratedPattern.pattern &&
            rule.uid == normalizedUid
        ) {
            rule
        } else {
            rule.copy(id = canonicalId, pattern = migratedPattern, uid = normalizedUid)
        }
    }

    private fun findExistingTimeRuleIndex(rules: List<TriggerRule>, newRule: TriggerRule): Int {
        val canonicalNewPresetId = canonicalPresetId(newRule.id)
        val exactPatternMatch = rules.indexOfFirst {
            it.pattern.pattern == newRule.pattern.pattern &&
                    ((canonicalPresetId(it.id) != null && canonicalNewPresetId != null) || isGeneratedTimeRuleId(it.id))
        }
        if (exactPatternMatch >= 0) {
            return exactPatternMatch
        }

        if (canonicalNewPresetId == null) {
            return if (isGeneratedTimeRuleId(newRule.id)) {
                rules.indexOfFirst { it.id == newRule.id }
            } else {
                -1
            }
        }

        val legacyPatterns = legacyTimePatternsById[canonicalNewPresetId].orEmpty()
        return rules.indexOfFirst {
            canonicalPresetId(it.id) == canonicalNewPresetId &&
                    (it.pattern.pattern == newRule.pattern.pattern || it.pattern.pattern in legacyPatterns)
        }
    }

    private fun timeToken(normalizedTime: String): String {
        val (hourText, minute) = normalizedTime.split(":")
        val hour = hourText.toInt()
        return if (hour < 10) {
            "0?$hour:$minute"
        } else {
            "$hour:$minute"
        }
    }

    private fun generatedTimeRuleId(startTime: String, endTime: String): String =
        "time_${startTime.replace(":", "_")}_${endTime.replace(":", "_")}"

    private fun timeRangeForRuleId(id: String): Pair<String, String>? =
        canonicalPresetId(id)?.let { canonicalId ->
            timePresetsById[canonicalId]?.let { it.startTime to it.endTime }
        } ?: parseGeneratedTimeRuleId(id)

    private fun canonicalPresetId(id: String): String? =
        presetIdAliases.entries.firstOrNull { id in it.value }?.key

    private fun parseGeneratedTimeRuleId(id: String): Pair<String, String>? {
        val match = Regex("""^time_(\d{2})_(\d{2})_(\d{2})_(\d{2})$""").matchEntire(id) ?: return null
        return "${match.groupValues[1]}:${match.groupValues[2]}" to
                "${match.groupValues[3]}:${match.groupValues[4]}"
    }

    private fun textWithinShiftEntryPattern(): String =
        """(?:(?!(?:\b\d{1,2}:\d{2}\b|\r?\n\s*\r?\n|\||\r?\n(?=\s*$OPTIONAL_DATE\d{1,2}:\d{2}[^\n\r]*\d{1,2}:\d{2}))).)*?"""
}
