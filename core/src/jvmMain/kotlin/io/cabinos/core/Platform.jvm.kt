package io.cabinos.core

internal actual fun platformNowMillis(): Long = System.currentTimeMillis()
internal actual fun randomEventId(): String = java.util.UUID.randomUUID().toString()
