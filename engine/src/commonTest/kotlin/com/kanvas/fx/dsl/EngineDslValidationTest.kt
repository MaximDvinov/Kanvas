package com.kanvas.fx.dsl

import com.kanvas.fx.core.SceneSystem
import kotlin.test.Test
import kotlin.test.assertFailsWith

class EngineDslValidationTest {
    @Test
    fun rejectsBlankSceneName() {
        assertFailsWith<IllegalArgumentException> {
            engine {
                scene(" ") {}
            }
        }
    }

    @Test
    fun rejectsInvalidTimeScaleAndZoomRange() {
        assertFailsWith<IllegalArgumentException> {
            engine {
                scene("ok") {
                    timeScale(-1f)
                }
            }
        }
        assertFailsWith<IllegalArgumentException> {
            engine {
                scene("ok") {
                    camera {
                        zoomRange(0f, 1f)
                    }
                }
            }
        }
    }

    @Test
    fun rejectsDuplicateSystemIdsAndNegativeBoundsRadius() {
        assertFailsWith<IllegalArgumentException> {
            engine {
                scene("ok") {
                    systems {
                        add(id = "dup", system = SceneSystem { })
                        add(id = "dup", system = SceneSystem { })
                    }
                }
            }
        }
        assertFailsWith<IllegalArgumentException> {
            engine {
                scene("ok") {
                    entities {
                        entity("e") {
                            autoBoundsCircle(0f, 0f, -1f)
                        }
                    }
                }
            }
        }
    }
}
