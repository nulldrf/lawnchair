package app.lawnchair.preferences2

import app.lawnchair.LawnchairApp
import com.patrykmichalik.opto.core.PreferenceImpl
import com.patrykmichalik.opto.core.firstBlocking
import com.patrykmichalik.opto.core.getFromPreferences
import com.patrykmichalik.opto.core.setBlocking
import kotlin.jvm.JvmOverloads

/**
 * Returns the current value of this preference from the in-memory cache.
 *
 * When backed by [CachedPreferencesDataStore] (all [PreferenceManager2] preferences), this reads
 * directly from the [CachedPreferencesDataStore.currentPreferences] snapshot without needing to
 * look up [PreferenceManager2.INSTANCE] and without touching disk.
 */
@JvmOverloads
fun <C, S> PreferenceImpl<C, S>.firstCached(
    prefs2: PreferenceManager2? = null,
): C {
    val ds = preferencesDataStore
    return when {
        ds is CachedPreferencesDataStore -> getFromPreferences(ds.currentPreferences)

        prefs2 != null && ds === prefs2.preferencesDataStore -> getFromPreferences(prefs2.getCachedPreferences())

        else -> {
            val resolvedPrefs2 = prefs2 ?: PreferenceManager2.getInstance(LawnchairApp.instance)
            if (ds === resolvedPrefs2.preferencesDataStore) {
                getFromPreferences(resolvedPrefs2.getCachedPreferences())
            } else {
                firstBlocking()
            }
        }
    }
}

/**
 * Updates this preference in memory immediately and schedules an asynchronous disk flush when
 * backed by [CachedPreferencesDataStore]. Safe to call synchronously from Kotlin or Java without
 * blocking on disk I/O.
 */
fun <C, S> PreferenceImpl<C, S>.setCached(value: C) {
    setBlocking(value)
}
