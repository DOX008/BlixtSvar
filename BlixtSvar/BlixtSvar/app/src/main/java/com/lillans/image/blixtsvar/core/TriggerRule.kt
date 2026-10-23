package com.lillans.image.blixtsvar.core

data class TriggerRule(
    val id: String,
    val pattern: Regex,
    val reply: String,
    val enabled: Boolean = true
)