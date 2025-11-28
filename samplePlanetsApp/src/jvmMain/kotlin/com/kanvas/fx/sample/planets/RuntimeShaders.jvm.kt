package com.kanvas.fx.sample.planets

import com.kanvas.fx.render.AssetRegistry
import com.kanvas.fx.render.enableDesktopRuntimeShaders

actual fun AssetRegistry.enableSampleRuntimeShaders() {
    enableDesktopRuntimeShaders()
}
