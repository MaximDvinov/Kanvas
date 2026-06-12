package com.kanvas.fx.sample

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.kanvas.fx.sample.planets.GravityDemoApp

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Kanvas Planets",
    ) {
        GravityDemoApp()
    }
}
