package com.kanvas.fx.core

actual class PlatformLock actual constructor() {
    private val delegate = Any()

    actual fun <T> withLock(block: () -> T): T = synchronized(delegate) { block() }
}

