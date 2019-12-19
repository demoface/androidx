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

package androidx.ui.test

import androidx.annotation.FloatRange
import androidx.ui.core.Duration
import androidx.ui.core.Px
import androidx.ui.core.PxPosition
import androidx.ui.core.SemanticsTreeNode
import androidx.ui.core.inMilliseconds
import androidx.ui.core.milliseconds
import androidx.ui.core.px
import androidx.ui.engine.geometry.Rect
import androidx.ui.lerp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sign
import kotlin.math.sin

/**
 * An object that has an associated component in which one can inject gestures. The gestures can
 * be injected by calling methods defined on [GestureScope], such as [sendSwipeUp]. The associated
 * component is the [SemanticsTreeNode] found by one of the finder methods such as [findByTag].
 *
 * Example usage:
 * findByTag("myWidget")
 *    .doGesture {
 *        sendSwipeUp()
 *    }
 */
class GestureScope internal constructor(
    internal val semanticsNodeInteraction: SemanticsNodeInteraction
) {
    internal inline val semanticsTreeNode
        get() = semanticsNodeInteraction.semanticsTreeNode
    internal inline val semanticsTreeInteraction
        get() = semanticsNodeInteraction.semanticsTreeInteraction
}

/**
 * The distance of a swipe's start position from the node's edge, in terms of the node's length.
 * We do not start the swipe exactly on the node's edge, but somewhat more inward, since swiping
 * from the exact edge may behave in an unexpected way (e.g. may open a navigation drawer).
 */
private const val edgeFuzzFactor = 0.083f

private fun GestureScope.getGlobalBounds(): Rect {
    return requireNotNull(semanticsTreeNode.globalRect) {
        "Semantic Node has no child layout to resolve coordinates on"
    }
}

private fun GestureScope.toGlobalPosition(position: PxPosition): PxPosition {
    val bounds = getGlobalBounds()
    return position + PxPosition(bounds.left.px, bounds.top.px)
}

/**
 * Performs a click gesture on the given [position] on the associated component. The [position]
 * is in the component's local coordinate system.
 *
 * Throws [AssertionError] when the component doesn't have a bounding rectangle set
 */
fun GestureScope.sendClick(position: PxPosition) {
    semanticsTreeInteraction.sendInput {
        it.sendClick(toGlobalPosition(position))
    }
}

/**
 * Performs a click gesture on the associated component. The click is done in the middle of the
 * component's bounds.
 *
 * Throws [AssertionError] when the component doesn't have a bounding rectangle set
 */
fun GestureScope.sendClick() {
    val bounds = getGlobalBounds()
    sendClick(PxPosition(Px(bounds.width / 2), Px(bounds.height / 2)))
}

/**
 * Performs the swipe gesture on the associated component. The motion events are linearly
 * interpolated between [start] and [end]. The coordinates are in the component's local
 * coordinate system, i.e. (0, 0) is the top left corner of the component. The default duration
 * is 200 milliseconds.
 *
 * Throws [AssertionError] when the component doesn't have a bounding rectangle set
 */
fun GestureScope.sendSwipe(
    start: PxPosition,
    end: PxPosition,
    duration: Duration = 200.milliseconds
) {
    val globalStart = toGlobalPosition(start)
    val globalEnd = toGlobalPosition(end)
    semanticsTreeInteraction.sendInput {
        it.sendSwipe(globalStart, globalEnd, duration)
    }
}

/**
 * Performs the swipe gesture on the associated component, such that the velocity when the
 * gesture is finished is roughly equal to [endVelocity]. The MotionEvents are linearly
 * interpolated between [start] and [end]. The coordinates are in the component's
 * local coordinate system, i.e. (0, 0) is the top left corner of the component. The default
 * duration is 200 milliseconds.
 *
 * Note that due to imprecisions, no guarantees can be made on the precision of the actual
 * velocity at the end of the gesture, but generally it is within 0.1% of the desired velocity.
 *
 * Throws [AssertionError] when the component doesn't have a bounding rectangle set
 */
fun GestureScope.sendSwipeWithVelocity(
    start: PxPosition,
    end: PxPosition,
    @FloatRange(from = 0.0) endVelocity: Float,
    duration: Duration = 200.milliseconds
) {
    require(endVelocity >= 0f) {
        "Velocity cannot be $endVelocity, it must be positive"
    }
    // TODO(146551983): require that duration >= 2.5 * eventPeriod
    // TODO(146551983): check that eventPeriod < 40 milliseconds
    require(duration >= 25.milliseconds) {
        "Duration must be at least 25ms because velocity requires at least 3 input events"
    }
    val globalStart = toGlobalPosition(start)
    val globalEnd = toGlobalPosition(end)

    // Decompose v into it's x and y components
    val delta = end - start
    val theta = atan2(delta.y.value, delta.x.value)
    // VelocityTracker internally calculates px/s, not px/ms
    val vx = cos(theta) * endVelocity / 1000
    val vy = sin(theta) * endVelocity / 1000

    // Note: it would be more precise to do `theta = atan2(-y, x)`, because atan2 expects a
    // coordinate system where positive y goes up and in our coordinate system positive y goes
    // down. However, in that case we would also have to inverse `vy` to convert the velocity
    // back to our own coordinate system. But then it's just a double negation, so we can skip
    // both conversions entirely.

    // To get the desired velocity, generate fx and fy such that VelocityTracker calculates
    // the right velocity. VelocityTracker makes a polynomial fit through the points
    // (-age, x) and (-age, y) for vx and vy respectively, which is accounted for in
    // f(Long, Long, Float, Float, Float).
    val durationMs = duration.inMilliseconds()
    val fx = createFunctionForVelocity(durationMs, globalStart.x.value, globalEnd.x.value, vx)
    val fy = createFunctionForVelocity(durationMs, globalStart.y.value, globalEnd.y.value, vy)

    semanticsTreeInteraction.sendInput {
        it.sendSwipe({ t -> PxPosition(fx(t).px, fy(t).px) }, duration)
    }
}

/**
 * Performs a swipe up gesture on the associated component. The gesture starts slightly above the
 * bottom of the component and ends at the top.
 *
 * Throws [AssertionError] when the component doesn't have a bounding rectangle set
 */
fun GestureScope.sendSwipeUp() {
    val bounds = getGlobalBounds()
    val x = bounds.width / 2
    val y0 = bounds.height * (1 - edgeFuzzFactor)
    val y1 = 0f
    val start = PxPosition(x.px, y0.px)
    val end = PxPosition(x.px, y1.px)
    sendSwipe(start, end, 200.milliseconds)
}

/**
 * Performs a swipe down gesture on the associated component. The gesture starts slightly below the
 * top of the component and ends at the bottom.
 *
 * Throws [AssertionError] when the component doesn't have a bounding rectangle set
 */
fun GestureScope.sendSwipeDown() {
    val bounds = getGlobalBounds()
    val x = bounds.width / 2
    val y0 = bounds.height * edgeFuzzFactor
    val y1 = bounds.height
    val start = PxPosition(x.px, y0.px)
    val end = PxPosition(x.px, y1.px)
    sendSwipe(start, end, 200.milliseconds)
}

/**
 * Performs a swipe left gesture on the associated component. The gesture starts slightly left of
 * the right side of the component and ends at the left side.
 *
 * Throws [AssertionError] when the component doesn't have a bounding rectangle set
 */
fun GestureScope.sendSwipeLeft() {
    val bounds = getGlobalBounds()
    val x0 = bounds.width * (1 - edgeFuzzFactor)
    val x1 = 0f
    val y = bounds.height / 2
    val start = PxPosition(x0.px, y.px)
    val end = PxPosition(x1.px, y.px)
    sendSwipe(start, end, 200.milliseconds)
}

/**
 * Performs a swipe right gesture on the associated component. The gesture starts slightly right of
 * the left side of the component and ends at the right side.
 *
 * Throws [AssertionError] when the component doesn't have a bounding rectangle set
 */
fun GestureScope.sendSwipeRight() {
    val bounds = getGlobalBounds()
    val x0 = bounds.width * edgeFuzzFactor
    val x1 = bounds.width
    val y = bounds.height / 2
    val start = PxPosition(x0.px, y.px)
    val end = PxPosition(x1.px, y.px)
    sendSwipe(start, end, 200.milliseconds)
}

/**
 * Generate a function of the form `f(t) = a*(t-T)^2 + b*(t-T) + c` that satisfies
 * `f(0) = [start]`, `f([duration]) = [end]`, `T = [duration]` and `b = [velocity]`.
 *
 * Filling in `f([duration]) = [end]`, `T = [duration]` and `b = [velocity]` gives:
 * * `a * (duration - duration)^2 + velocity * (duration - duration) + c = end`
 * * `c = end`
 *
 * Filling in `f(0) = [start]`, `T = [duration]` and `b = [velocity]` gives:
 * * `a * (0 - duration)^2 + velocity * (0 - duration) + c = start`
 * * `a * duration^2 - velocity * duration + to = start`
 * * `a * duration^2 = start - to + velocity * duration`
 * * `a = (start - to + velocity * duration) / duration^2`
 *
 * @param duration The duration of the fling
 * @param start The start x or y position
 * @param end The end x or y position
 * @param velocity The desired velocity in the x or y direction at the [end] position
 */
private fun createFunctionForVelocity(
    duration: Long,
    start: Float,
    end: Float,
    velocity: Float
): (Long) -> Float {
    val a = (start - end + velocity * duration) / (duration * duration)
    val function = { t: Long ->
        val tMinusDuration = t - duration
        // `f(t) = a*(t-T)^2 + b*(t-T) + c`
        a * tMinusDuration * tMinusDuration + velocity * tMinusDuration + end
    }

    // High velocities often result in curves that start off in the wrong direction, like a bow
    // being strung to reach a high velocity at the end coordinate. For a gesture, that is not
    // desirable, and can be mitigated by using the fact that VelocityTracker only uses the last
    // 100 ms of the gesture. Anything before that doesn't need to follow the curve.

    // Does the function go in the correct direction at the start?
    if (sign(function(1) - start) == sign(end - start)) {
        return function
    } else {
        // If not, lerp between 0 and `duration - 100` in an attempt to prevent the function from
        // going in the wrong direction. This does not affect the velocity at f(duration), as
        // VelocityTracker only uses the last 100ms. This only works if f(duration - 100) is
        // between from and to, log a warning if this is not the case.
        val cutOffTime = duration - 100
        val cutOffValue = function(cutOffTime)
        require(sign(cutOffValue - start) == sign(end - start)) {
            "Creating a gesture between $start and $end with a duration of $duration and a " +
                    "resulting velocity of $velocity results in a movement that goes outside " +
                    "of the range [$start..$end]"
        }
        return { t ->
            if (t < cutOffTime) {
                lerp(start, cutOffValue, t / cutOffTime.toFloat())
            } else {
                function(t)
            }
        }
    }
}
