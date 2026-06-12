package com.kanvas.fx.planetSample.ios

import androidx.compose.ui.window.ComposeUIViewController
import com.kanvas.fx.planetSample.planets.GravityDemoApp
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController = ComposeUIViewController {
    GravityDemoApp()
}
