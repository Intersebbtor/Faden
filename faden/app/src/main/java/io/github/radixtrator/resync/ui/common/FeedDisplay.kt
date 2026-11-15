package io.github.radixtrator.resync.ui.common

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import io.github.radixtrator.resync.data.DataSaving
import io.github.radixtrator.resync.data.NsfwMode
import io.github.radixtrator.resync.data.PostLayout
import io.github.radixtrator.resync.data.Settings
import io.github.radixtrator.resync.data.SwipeAction
import io.github.radixtrator.resync.data.ViewOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn

/**
 * The few settings (and app state) that feed rows read, held apart from [Settings] so that a change to any other
 * setting leaves the rows alone: equal values don't invalidate the readers of [LocalFeedDisplay].
 */
@Immutable
data class FeedDisplay(
    val layout: PostLayout = PostLayout.CARD,
    val nsfw: NsfwMode = NsfwMode.BLUR,
    val blurSpoilers: Boolean = true,
    val blurNsfwOnlyFrontpageAndAll: Boolean = false,
    val dataSaving: DataSaving = DataSaving.NEVER,
    val shortScores: Boolean = true,
    val showPostAuthor: Boolean = true,
    val showPostFlair: Boolean = true,
    val postSwipe: Boolean = true,
    val postSwipeStages: List<SwipeAction> = emptyList(),
    val viewOptions: Map<PostLayout, ViewOptions> = emptyMap(),
    /** Whether the connection is metered right now, which "Data saving mode: on mobile data" goes by. */
    val metered: Boolean = false,
    /** Lowercase names of the communities the account moderates, for the Mod tools button. */
    val moderated: Set<String> = emptySet(),
) {
    fun viewOptionsFor(layout: PostLayout): ViewOptions = viewOptions[layout] ?: ViewOptions.DEFAULT

    /** Whether Sync's "Data saving mode" applies right now. */
    val dataSavingActive: Boolean
        get() = when (dataSaving) {
            DataSaving.NEVER -> false
            DataSaving.ALWAYS -> true
            DataSaving.MOBILE -> metered
        }

    companion object {
        fun of(settings: Settings, metered: Boolean, moderated: Set<String>) = FeedDisplay(
            layout = settings.layout,
            nsfw = settings.nsfw,
            blurSpoilers = settings.blurSpoilers,
            blurNsfwOnlyFrontpageAndAll = settings.blurNsfwOnlyFrontpageAndAll,
            dataSaving = settings.dataSaving,
            shortScores = settings.shortScores,
            showPostAuthor = settings.showPostAuthor,
            showPostFlair = settings.showPostFlair,
            postSwipe = settings.postSwipe,
            postSwipeStages = settings.postSwipeStages,
            viewOptions = settings.viewOptions,
            metered = metered,
            moderated = moderated,
        )
    }
}

/** Provided by MainActivity from the settings; feed rows read this instead of [io.github.radixtrator.resync.ui.navigation.LocalSettings]. */
val LocalFeedDisplay = compositionLocalOf { FeedDisplay() }

/**
 * Whether the active connection is metered, kept current by one network callback while collected; the feed's
 * "Data saving mode: on mobile data" reads it instead of asking the system for every card.
 */
fun meteredNetworkFlow(context: Context, scope: CoroutineScope): StateFlow<Boolean> {
    val app = context.applicationContext
    val manager = app.getSystemService(ConnectivityManager::class.java)
    return callbackFlow {
        if (manager == null) {
            trySend(false)
            awaitClose()
            return@callbackFlow
        }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                trySend(!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED))
            }

            override fun onLost(network: Network) {
                trySend(false)
            }
        }
        manager.registerDefaultNetworkCallback(callback)
        awaitClose { manager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged().stateIn(scope, SharingStarted.WhileSubscribed(5_000), app.onMeteredNetwork())
}

/** A coarse clock: the current minute since the epoch, ticking once a minute, for ages ("4h") shown in rows. */
val LocalMinuteClock = staticCompositionLocalOf<State<Long>> { mutableLongStateOf(0L) }

/** The one [LocalMinuteClock] ticker: a single coroutine, awake only while the app is on screen. */
@Composable
fun rememberMinuteClock(): State<Long> {
    val minute = remember { mutableLongStateOf(System.currentTimeMillis() / 60_000) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val now = System.currentTimeMillis()
                minute.longValue = now / 60_000
                delay(60_000 - now % 60_000)
            }
        }
    }
    return minute
}
