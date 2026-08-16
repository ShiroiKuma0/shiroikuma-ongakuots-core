package com.maxrave.media3.carapp

import androidx.car.app.model.CarColor
import com.maxrave.domain.manager.DataStoreManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.mp.KoinPlatform.getKoin

/**
 * 白い熊 音楽乙's Android Auto colours.
 *
 * Two layers, because Auto has two ways of being coloured and only one of them can be dynamic:
 *
 * - The **static** layer is the `ShiroikumaCarAppTheme` resource, which is what `CarColor.PRIMARY`
 *   and `CarColor.SECONDARY` resolve to. It is a resource, so it cannot follow a setting.
 * - The **dynamic** layer is this object: the two Auto slots on the 白い熊 音楽乙 UI page are
 *   mirrored into two plain DataStore keys as `#AARRGGBB`, read here, and handed to the templates as
 *   `CarColor.createCustom`. That is why the keys are plain hex rather than the page's own theme
 *   JSON — `:media3` has no business knowing the shape of a Compose theme, and this way the
 *   dependency is one string each way.
 *
 * Everything else on an Auto screen — backgrounds, list chrome, text colour — is the host's, and no
 * app can change it. That is a driver-distraction rule, not an oversight.
 */
object SkCarColors {
    const val KEY_PRIMARY = "shiroikuma_car_primary"
    const val KEY_SECONDARY = "shiroikuma_car_secondary"

    /** #FFFF00 / #BEBE00 — the same pair the static car theme declares. */
    private const val DEFAULT_PRIMARY = 0xFFFFFF00.toInt()
    private const val DEFAULT_SECONDARY = 0xFFBEBE00.toInt()

    @Volatile private var primaryArgb: Int = DEFAULT_PRIMARY

    @Volatile private var secondaryArgb: Int = DEFAULT_SECONDARY

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile private var started = false

    /**
     * Begin following the two keys. Called once when the car app service is created — a car screen
     * is built on the main thread and cannot wait on a Flow, so the values are kept warm here and
     * read synchronously.
     */
    @Synchronized
    fun start() {
        if (started) return
        started = true
        val store = runCatching { getKoin().get<DataStoreManager>() }.getOrNull() ?: return
        store
            .getString(KEY_PRIMARY)
            .onEach { primaryArgb = parseArgb(it) ?: DEFAULT_PRIMARY }
            .launchIn(scope)
        store
            .getString(KEY_SECONDARY)
            .onEach { secondaryArgb = parseArgb(it) ?: DEFAULT_SECONDARY }
            .launchIn(scope)
    }

    /**
     * The accent, as the templates want it. `createCustom` takes a light and a dark variant; we pass
     * the same colour for both — the house yellow is chosen to carry on black, and letting the host
     * pick a different one on a light head unit would defeat the point of setting it.
     */
    fun primary(): CarColor = CarColor.createCustom(primaryArgb, primaryArgb)

    fun secondary(): CarColor = CarColor.createCustom(secondaryArgb, secondaryArgb)

    private fun parseArgb(s: String?): Int? {
        val h = s?.trim()?.removePrefix("#") ?: return null
        if (h.length != 8 && h.length != 6) return null
        val v = h.toLongOrNull(16) ?: return null
        return if (h.length == 8) v.toInt() else (0xFF000000L or v).toInt()
    }
}
