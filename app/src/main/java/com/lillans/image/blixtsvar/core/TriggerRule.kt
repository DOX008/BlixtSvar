package com.lillans.image.blixtsvar.core

import java.util.UUID

data class TriggerRule(
    val id: String,
    val pattern: Regex,
    val reply: String,
    val enabled: Boolean = true,
    val uid: String = UUID.randomUUID().toString()
)