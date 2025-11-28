package com.kanvas.fx.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Kanvas DSL Demo: Gravity",
    ) {
        MaterialTheme {
            GravityDemoApp()
        }
    }
}
