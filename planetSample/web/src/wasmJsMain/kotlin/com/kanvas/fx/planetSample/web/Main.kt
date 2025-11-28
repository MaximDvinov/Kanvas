package com.kanvas.fx.planetSample.web

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.kanvas.fx.planetSample.planets.GravityDemoApp
import kotlinx.browser.document
import org.w3c.dom.HTMLElement

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val body = document.body ?: return
    body.setAttribute("style", "margin:0;padding:0;overflow:hidden;background:#050914;")
    val root = (document.getElementById("kanvas-root") as? HTMLElement)
        ?: (document.createElement("div") as HTMLElement).also {
            it.id = "kanvas-root"
            it.setAttribute("style", "position:fixed;inset:0;width:100vw;height:100vh;")
            body.appendChild(it)
        }
    ComposeViewport(root) {
        GravityDemoApp()
    }
}
