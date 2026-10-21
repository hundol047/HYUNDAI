package io.cabinos.core

internal actual fun platformNowMillis(): Long = kotlin.js.Date.now().toLong()
internal actual fun randomEventId(): String = js("globalThis.crypto.randomUUID()").unsafeCast<String>()
