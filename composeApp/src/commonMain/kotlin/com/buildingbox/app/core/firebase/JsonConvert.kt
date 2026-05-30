package com.buildingbox.app.core.firebase

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.longOrNull

/** Convert a kotlinx JsonElement into plain Kotlin values for GitLive's updateChildren(Map). */
fun JsonElement.toAny(): Any? = when (this) {
    is JsonNull -> null
    is JsonPrimitive -> when {
        isString -> content
        booleanOrNull != null -> booleanOrNull
        longOrNull != null -> longOrNull
        else -> content.toDoubleOrNull() ?: content
    }
    is JsonObject -> mapValues { it.value.toAny() }
    is JsonArray -> map { it.toAny() }
}
