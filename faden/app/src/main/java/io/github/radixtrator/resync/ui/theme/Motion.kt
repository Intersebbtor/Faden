package io.github.radixtrator.resync.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.material3.MotionScheme

/**
 * Shared motion. Things that appear and disappear ([enter], [exit]) use Material's expressive motion scheme, the one
 * [FadenTheme] provides: its effects springs are critically damped, so a sheet or fade arrives the moment it's asked
 * to and settles without overshooting its edge. Whole-page pushes and pops ([page]) stay timed, short and with a
 * strong ease-out: they are coupled to the slide-back gesture and the system's predictive back, which both finish
 * on a fixed duration ([PAGE]) that a spring wouldn't match.
 *
 * Call sites outside composition (navigation transition specs) can't read the theme's `MaterialTheme.motionScheme`,
 * so [enter] and [exit] read the same expressive scheme directly; composables use `MaterialTheme.motionScheme`.
 */
object Motion {
    private val scheme = MotionScheme.expressive()

    /** Material 3's emphasized curve: a fast start and a long, soft landing. For page movement. */
    val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** Page pushes and pops. */
    const val PAGE = 280

    /** Sheets, panels and fades. */
    const val SHORT = 200

    /** Fades that only need to soften a change (media viewers, crossfades). */
    const val FADE = 150

    fun <T> page(): FiniteAnimationSpec<T> = tween(PAGE, easing = Emphasized)

    /** Things arriving on screen. [duration] at [FADE] or less asks for the faster of the scheme's two effects springs. */
    fun <T> enter(duration: Int = SHORT): FiniteAnimationSpec<T> =
        if (duration <= FADE) scheme.fastEffectsSpec() else scheme.defaultEffectsSpec()

    /** Things leaving the screen: quick, so they're out of the way of what's next. */
    fun <T> exit(duration: Int = FADE): FiniteAnimationSpec<T> =
        if (duration <= FADE) scheme.fastEffectsSpec() else scheme.defaultEffectsSpec()
}
