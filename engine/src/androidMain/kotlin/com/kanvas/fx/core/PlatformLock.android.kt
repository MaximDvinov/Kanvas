package com.kanvas.fx.core

actual class PlatformLock {
    actual inline fun <T> withLock(block: () -> T): T = synchronized(this, block)
}
