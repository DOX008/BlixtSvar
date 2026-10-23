package com.lillans.image.blixtsvar.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RulePresetsTest {

    @Test
    fun nightDefaultMatchesOrderedShiftButNotReverseOrder() {
        val nightRule = RulePresets.defaultRules.first { it.id == "Natt" }

        assertTrue(
            nightRule.pattern.containsMatchIn(
                "Förfrågan: 14 nov 21:00 - 15 nov 07:00"
            )
        )

        assertFalse(
            nightRule.pattern.containsMatchIn(
                "Förfrågan: 14 nov 07:00 - 14 nov 21:00"
            )
        )
    }

    @Test
    fun orderedTimeRuleSupportsTillAndSingleDigitEndHour() {
        val nightRule =
            RulePresets.buildTimeRule("Natt", "21:00", "07:00")

        assertTrue(
            nightRule.pattern.containsMatchIn(
                "Kan du jobba 21:00 till 7:00 i natt?"
            )
        )

        assertFalse(
            nightRule.pattern.containsMatchIn(
                "Kan du jobba 7:00 till 21:00 i natt?"
            )
        )
    }

    @Test
    fun orderedTimeRuleSupportsMultilineShiftSmsWithDetailsBetweenTimes() {
        val nightRule =
            RulePresets.buildTimeRule("Natt", "21:00", "07:00")

        assertTrue(
            nightRule.pattern.containsMatchIn(
                """
                Förfrågan:
                14 nov 21:00
                Kirurgen sal 3
                15 nov 07:00
                """.trimIndent()
            )
        )
    }

    @Test
    fun orderedTimeRuleSupportsInlineShiftDetailsBetweenTimes() {
        val nightRule =
            RulePresets.buildTimeRule("Natt", "21:00", "07:00")

        assertTrue(
            nightRule.pattern.containsMatchIn(
                "14 nov 21:00 - Kirurgen sal 3 - 15 nov 07:00"
            )
        )
    }

    @Test
    fun orderedTimeRuleSupportsDatesWithYears() {
        val nightRule =
            RulePresets.buildTimeRule("Natt", "21:00", "07:00")

        assertTrue(
            nightRule.pattern.containsMatchIn(
                "14 nov 2026 21:00 - 15 nov 2026 07:00"
            )
        )
    }

    @Test
    fun orderedTimeRuleStopsAtFirstMatchingShiftEntry() {
        val nightRule =
            RulePresets.buildTimeRule("Natt", "21:00", "07:00")

        val message =
            "14 nov 21:00 - 15 nov 07:00 | 15 nov 21:00 - 16 nov 07:00"

        val match = nightRule.pattern.find(message)

        assertNotNull(match)
        assertEquals(
            "14 nov 21:00 - 15 nov 07:00",
            match?.value
        )
    }

    @Test
    fun normalizeTimeInputCanonicalizesSingleDigitHours() {
        assertEquals(
            "07:00",
            RulePresets.normalizeTimeInput("7:00")
        )

        assertEquals(
            "21:00",
            RulePresets.normalizeTimeInput("21:00")
        )

        assertEquals(
            null,
            RulePresets.normalizeTimeInput("24:00")
        )
    }

    @Test
    fun generatedTimeRuleIdsAreStableButShownWithFriendlyName() {
        val rule = RulePresets.buildTimeRule(
            id = RulePresets.suggestedRuleId("22:00", "06:00"),
            startTime = "22:00",
            endTime = "06:00"
        )

        assertEquals(
            "time_22_00_06_00",
            rule.id
        )

        assertEquals(
            "Tid 22:00-06:00",
            RulePresets.displayNameFor(rule)
        )
    }

    @Test
    fun presetTimeRulesKeepNamedLabels() {
        val rule =
            RulePresets.defaultRules.first { it.id == "Kväll" }

        assertEquals(
            "Kväll (14:00-21:00)",
            RulePresets.displayNameFor(rule)
        )
    }

    @Test
    fun upsertTimeRuleReusesExistingGeneratedRule() {
        val existing =
            RulePresets.buildTimeRule(
                "time_22_00_06_00",
                "22:00",
                "06:00",
                reply = "NEJ"
            ).copy(enabled = false)

        val updated =
            RulePresets.buildTimeRule(
                "time_22_00_06_00",
                "22:00",
                "06:00"
            )

        val rules =
            RulePresets.upsertTimeRule(
                listOf(existing),
                updated
            )

        assertEquals(1, rules.size)
        assertEquals("NEJ", rules.single().reply)
        assertFalse(rules.single().enabled)
        assertEquals(existing.uid, rules.single().uid)
    }

    @Test
    fun migrateStoredTimeRuleUpdatesGeneratedLegacyPatternWithoutLosingReplyOrEnabled() {
        val legacyRule =
            TriggerRule(
                id = "time_22_00_06_00",
                pattern = Regex(
                    "(?s)(?=.*22:00)(?=.*0?6:00)",
                    setOf(
                        RegexOption.IGNORE_CASE,
                        RegexOption.DOT_MATCHES_ALL
                    )
                ),
                reply = "Kanske",
                enabled = false
            )

        val migrated =
            RulePresets.migrateStoredTimeRule(legacyRule)

        assertEquals("Kanske", migrated.reply)
        assertFalse(migrated.enabled)

        assertEquals(
            RulePresets.buildTimeRule(
                "time_22_00_06_00",
                "22:00",
                "06:00"
            ).pattern.pattern,
            migrated.pattern.pattern
        )
    }

    @Test
    fun migrateStoredTimeRuleCanonicalizesLegacyPresetId() {
        val legacyRule =
            TriggerRule(
                id = "Kvall",
                pattern = Regex(
                    "(?s)(?=.*14:00)(?=.*21:00)",
                    setOf(
                        RegexOption.IGNORE_CASE,
                        RegexOption.DOT_MATCHES_ALL
                    )
                ),
                reply = "JA"
            )

        val migrated =
            RulePresets.migrateStoredTimeRule(legacyRule)

        assertEquals("Kväll", migrated.id)

        assertEquals(
            "Kväll (14:00-21:00)",
            RulePresets.displayNameFor(migrated)
        )
    }
}