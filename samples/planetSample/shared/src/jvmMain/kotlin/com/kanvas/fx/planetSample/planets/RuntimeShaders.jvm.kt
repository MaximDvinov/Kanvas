package com.kanvas.fx.planetSample.planets

import com.kanvas.fx.render.AssetRegistry
import com.kanvas.fx.render.enableDesktopRuntimeShaders

actual fun AssetRegistry.enableSampleRuntimeShaders() {
    enableDesktopRuntimeShaders()
}
