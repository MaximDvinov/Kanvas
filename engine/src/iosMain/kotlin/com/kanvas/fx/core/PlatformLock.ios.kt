package com.kanvas.fx.core

actual class PlatformLock actual constructor() {
    actual fun <T> withLock(block: () -> T): T = block()
}

