package com.lillans.image.blixtsvar.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TriggerEngineTest {

    private val rules = listOf(
        TriggerRule(
            id = "Natt",
            pattern = Regex("(?=.*21:00)(?=.*07:00)", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)),
            reply = "JA"
        )
    )
    private val request = "Förfrågan: 14 nov 21:00 - 15 nov 07:00"
    private val booked = "Hej, du är inbokad 14 nov 21:00 - 15 nov 07:00. Svara ej på detta meddelande"

    @Test
    fun ignoredTextBlocksReply() {
        assertNull(TriggerEngine(rules, "du är inbokad").match(booked))
    }

    @Test
    fun normalRequestStillReplies() {
        assertEquals("JA", TriggerEngine(rules, "du är inbokad").match(request))
    }

    @Test
    fun ignoreIsCaseInsensitive() {
        assertNull(TriggerEngine(rules, "DU ÄR INBOKAD").match(booked))
    }

    @Test
    fun ignoreNormalizesNewlinesAndNbsp() {
        assertNull(TriggerEngine(rules, "du är inbokad").match("Hej, du\när\u00A0inbokad 21:00 07:00"))
        assertNull(TriggerEngine(rules, "du  är\u00A0inbokad").match(booked))
    }

    @Test
    fun emptyOrBlankIgnoreDisablesFeature() {
        assertEquals("JA", TriggerEngine(rules, "").match(booked))
        assertEquals("JA", TriggerEngine(rules, "   ").match(booked))
        assertEquals("JA", TriggerEngine(rules).match(booked))
    }
}
