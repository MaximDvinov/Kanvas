package com.kanvas.fx.render

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Brush

/**
 * Registry for render assets referenced by DSL and game objects.
 */
class AssetRegistry {
    private val textures = mutableMapOf<String, TextureAsset>()
    private val shaders = mutableMapOf<String, ShaderAsset>()
    private val textureResolvers = mutableListOf<TextureResolver>()
    private val resourceResolvers = mutableListOf<ResourceResolver>()
    private val imageDecoders = mutableListOf<ResourceImageDecoder>()
    private var runtimeShaderBrushFactory: RuntimeShaderBrushFactory? = null

    init {
        resourceResolvers += defaultResourceResolver()
        imageDecoders += defaultResourceImageDecoder()
    }

    /**
     * Registers texture asset.
     *
     * @param asset texture asset descriptor.
     */
    fun registerTexture(asset: TextureAsset) {
        textures[asset.id] = asset
    }

    /**
     * Registers texture by id and path.
     *
     * @param id texture id used in renderer calls.
     * @param path filesystem or logical path.
     */
    fun registerTexture(
        id: String,
        path: String,
    ) {
        textures[id] = TextureAsset(id = id, source = TextureSource.Path(path))
    }

    /**
     * Adds lazy path-to-bitmap resolver.
     *
     * @param resolver texture resolver invoked for path-based textures.
     */
    fun addTextureResolver(resolver: TextureResolver) {
        textureResolvers += resolver
    }

    /**
     * Adds a binary resource resolver used for multiplatform resource loading.
     */
    fun addResourceResolver(resolver: ResourceResolver) {
        resourceResolvers += resolver
    }

    /**
     * Replaces all registered resource resolvers with [resolvers].
     */
    fun setResourceResolvers(resolvers: List<ResourceResolver>) {
        resourceResolvers.clear()
        resourceResolvers += resolvers
    }

    /**
     * Restores default multiplatform resource resolver chain.
     */
    fun restoreDefaultResourceResolvers() {
        resourceResolvers.clear()
        resourceResolvers += defaultResourceResolver()
    }

    /**
     * Adds image decoder that converts raw bytes into [ImageBitmap].
     */
    fun addImageDecoder(decoder: ResourceImageDecoder) {
        imageDecoders += decoder
    }

    /**
     * Replaces all image decoders with [decoders].
     */
    fun setImageDecoders(decoders: List<ResourceImageDecoder>) {
        imageDecoders.clear()
        imageDecoders += decoders
    }

    /**
     * Restores default image decoder for current target.
     */
    fun restoreDefaultImageDecoders() {
        imageDecoders.clear()
        imageDecoders += defaultResourceImageDecoder()
    }

    /**
     * Registers shader asset.
     *
     * @param asset shader asset descriptor.
     */
    fun registerShader(asset: ShaderAsset) {
        shaders[asset.id] = asset
    }

    /**
     * Registers platform runtime shader brush factory.
     *
     * Platform backends can use it to execute shader code stored in [ShaderSource.Text]
     * or [ShaderSource.SkiaRuntime]. If no factory is registered, shader text is ignored.
     *
     * @param factory runtime shader brush factory.
     */
    fun registerRuntimeShaderBrushFactory(factory: RuntimeShaderBrushFactory) {
        runtimeShaderBrushFactory = factory
    }

    /**
     * Returns texture by id.
     *
     * For [TextureSource.Path] this method attempts lazy resolution through registered
     * [TextureResolver] instances and caches resolved bitmap source.
     *
     * @param id texture id.
     */
    fun texture(id: String): TextureAsset? {
        val asset = textures[id] ?: return null
        val path = (asset.source as? TextureSource.Path)?.value ?: return asset
        for (resolver in textureResolvers) {
            val resolved = resolver.resolve(path) ?: continue
            val bitmapAsset = TextureAsset(id = id, source = TextureSource.Bitmap(resolved))
            textures[id] = bitmapAsset
            return bitmapAsset
        }
        val bytes = resolveResourceBytes(path)
        if (bytes != null) {
            for (decoder in imageDecoders) {
                val bitmap = decoder.decode(bytes) ?: continue
                val bitmapAsset = TextureAsset(id = id, source = TextureSource.Bitmap(bitmap))
                textures[id] = bitmapAsset
                return bitmapAsset
            }
        }
        return asset
    }

    fun resolveResourceBytes(path: String): ByteArray? {
        for (resolver in resourceResolvers) {
            val bytes = resolver.resolve(path) ?: continue
            return bytes
        }
        return null
    }

    /**
     * Returns shader by id.
     *
     * @param id shader id.
     */
    fun shader(id: String): ShaderAsset? = shaders[id]

    /**
     * Creates runtime shader brush for [shader], if current platform supports it.
     */
    fun runtimeShaderBrush(
        shader: ShaderAsset,
        geometry: PrimitiveGeometry,
        uniforms: Map<String, ShaderUniform>,
        context: RenderContext? = null,
    ): Brush? = runtimeShaderBrushFactory?.create(shader, geometry, uniforms, context)
}

/**
 * Texture descriptor.
 *
 * @property id unique texture id.
 * @property source source variant used for loading/rendering.
 */
data class TextureAsset(
    val id: String,
    val source: TextureSource,
)

/**
 * Texture source variants.
 */
sealed interface TextureSource {
    /**
     * Path-based texture source.
     *
     * @property value path string.
     */
    data class Path(val value: String) : TextureSource
    /**
     * In-memory bitmap texture source.
     *
     * @property value image bitmap.
     */
    data class Bitmap(val value: ImageBitmap) : TextureSource
}

/**
 * Texture path resolver used by [AssetRegistry].
 */
fun interface TextureResolver {
    /**
     * Resolves path to image bitmap.
     *
     * @param path path from [TextureSource.Path].
     */
    fun resolve(path: String): ImageBitmap?
}

/**
 * Multiplatform binary resource resolver.
 */
fun interface ResourceResolver {
    /**
     * Resolves resource path to raw bytes.
     */
    fun resolve(path: String): ByteArray?
}

/**
 * Decodes resource bytes into [ImageBitmap].
 */
fun interface ResourceImageDecoder {
    fun decode(bytes: ByteArray): ImageBitmap?
}

/**
 * Shader descriptor.
 *
 * @property id unique shader id.
 * @property source shader source variant.
 */
data class ShaderAsset(
    val id: String,
    val source: ShaderSource,
)

/**
 * Shader source variants.
 */
sealed interface ShaderSource {
    /**
     * Generic shader text source.
     */
    data class Text(val value: String) : ShaderSource

    /**
     * Skia RuntimeEffect shader code.
     *
     * @property code SKSL/RuntimeEffect shader code.
     * @property uniformOrder explicit float packing order for [ShaderUniform] values.
     */
    data class SkiaRuntime(
        val code: String,
        val uniformOrder: List<String> = emptyList(),
    ) : ShaderSource

    /**
     * Bridge source for external shader DSL objects (for example redbytefx definitions).
     */
    data class ExternalDsl(val value: Any) : ShaderSource
}

/**
 * Runtime shader uniform values supported by the engine.
 */
sealed interface ShaderUniform {
    /** Single float uniform. */
    data class Float1(val value: Float) : ShaderUniform
    /** Two-float vector uniform. */
    data class Float2(val x: Float, val y: Float) : ShaderUniform
    /** Four-float vector uniform. */
    data class Float4(
        val x: Float,
        val y: Float,
        val z: Float,
        val w: Float,
    ) : ShaderUniform
    /** Color uniform packed as RGBA floats. */
    data class Color4(val value: androidx.compose.ui.graphics.Color) : ShaderUniform
}

/**
 * Platform hook that converts shader code into a Compose [Brush].
 */
fun interface RuntimeShaderBrushFactory {
    fun create(
        shader: ShaderAsset,
        geometry: PrimitiveGeometry,
        uniforms: Map<String, ShaderUniform>,
        context: RenderContext?,
    ): Brush?
}

/**
 * Default resolver using standard Compose Multiplatform resource conventions per target.
 */
expect fun defaultResourceResolver(): ResourceResolver

/**
 * Default image decoder for current target.
 */
expect fun defaultResourceImageDecoder(): ResourceImageDecoder
