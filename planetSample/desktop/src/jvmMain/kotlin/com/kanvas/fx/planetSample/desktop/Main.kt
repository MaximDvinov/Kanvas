package com.kanvas.fx.planetSample.desktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.kanvas.fx.planetSample.planets.GravityDemoApp

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "Kanvas Planets") {
        GravityDemoApp()
    }
}
