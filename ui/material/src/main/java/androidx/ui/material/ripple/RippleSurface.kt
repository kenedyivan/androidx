/*
 * Copyright 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.ui.material.ripple

import androidx.annotation.CheckResult
import androidx.ui.core.LayoutCoordinates
import androidx.ui.core.OnPositioned
import androidx.ui.core.adapter.Draw
import androidx.ui.core.toRect
import androidx.ui.painting.Color
import com.google.r4a.Ambient
import com.google.r4a.Children
import com.google.r4a.ambient
import com.google.r4a.composer
import com.google.r4a.effectOf
import com.google.r4a.invalidate
import com.google.r4a.memo
import com.google.r4a.unaryPlus

/**
 * An interface for creating [RippleEffect]s on a [RippleSurface].
 */
interface RippleSurfaceOwner {

    /**
     * The surface background color.
     */
    val backgroundColor: Color?

    /**
     * The surface layout coordinates.
     */
    val layoutCoordinates: LayoutCoordinates

    /**
     * Add an [RippleEffect].
     *
     * The effect will be drawns as part of this [RippleSurface].
     */
    fun addEffect(feature: RippleEffect)

    /**
     * Removes added previously effects. Called by [RippleEffect] in onDispose()
     */
    fun removeEffect(feature: RippleEffect)

    /** Notifies the [RippleSurface] that one of its effects needs to redraw. */
    fun markNeedsRedraw()
}

/**
 * An ambient to provide the current [RippleSurface].
 *
 * Use [ambientRippleSurface] to receive the value.
 */
internal val CurrentRippleSurface = Ambient.of<RippleSurfaceOwner> {
    throw IllegalStateException("No RippleSurface ancestor found.")
}

/**
 * [ambient] to get a current [RippleSurfaceOwner] object from the [RippleSurface] ancestor.
 */
@CheckResult(suggest = "+")
fun ambientRippleSurface() = effectOf<RippleSurfaceOwner> { +ambient(CurrentRippleSurface) }

/**
 * A surface used to draw [RippleEffect]s on top of it.
 */
fun RippleSurface(
    /**
     * The surface background backgroundColor.
     */
    color: Color?,
    @Children children: () -> Unit
) {
    val owner = +memo { RippleSurfaceOwnerImpl() }
    owner.backgroundColor = color
    owner.recompose = +invalidate

    <OnPositioned onPositioned={ owner._layoutCoordinates = it } />
    <Draw> canvas, size ->
        if (owner.effects.isNotEmpty()) {
            canvas.save()
            canvas.clipRect(size.toRect())
            owner.effects.forEach { it.draw(canvas) }
            canvas.restore()
        }
    </Draw>
    <CurrentRippleSurface.Provider value=owner>
        <children />
    </CurrentRippleSurface.Provider>
}

private class RippleSurfaceOwnerImpl : RippleSurfaceOwner {

    override var backgroundColor: Color? = null
    override val layoutCoordinates
        get() = _layoutCoordinates
            ?: throw IllegalStateException("The surface wasn't yet positioned!")
    internal var recompose: () -> Unit = {}

    internal var _layoutCoordinates: LayoutCoordinates? = null
    internal var effects = mutableListOf<RippleEffect>()

    override fun markNeedsRedraw() = recompose()

    override fun addEffect(feature: RippleEffect) {
        assert(!feature.debugDisposed)
        assert(feature.rippleSurface == this)
        assert(!effects.contains(feature))
        effects.add(feature)
        markNeedsRedraw()
    }

    override fun removeEffect(feature: RippleEffect) {
        effects.remove(feature)
        markNeedsRedraw()
    }
}
