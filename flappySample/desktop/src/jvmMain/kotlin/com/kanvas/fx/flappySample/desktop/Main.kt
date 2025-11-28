package com.kanvas.fx.flappySample.desktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.kanvas.fx.flappySample.ui.FlappyGameApp

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "Kanvas Flappy Sample") {
        FlappyGameApp()
    }
}
