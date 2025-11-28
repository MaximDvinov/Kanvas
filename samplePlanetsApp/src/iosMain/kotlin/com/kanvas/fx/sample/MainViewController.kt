package com.kanvas.fx.sample

import androidx.compose.ui.window.ComposeUIViewController
import com.kanvas.fx.sample.planets.GravityDemoApp
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController = ComposeUIViewController {
    GravityDemoApp()
}
