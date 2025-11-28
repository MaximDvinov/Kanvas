package com.kanvas.fx.core

expect class PlatformLock() {
    fun <T> withLock(block: () -> T): T
}

