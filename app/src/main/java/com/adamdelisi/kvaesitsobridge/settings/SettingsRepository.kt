package com.adamdelisi.kvaesitsobridge.settings

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.bridgeDataStore by preferencesDataStore(
    name = "bridge_settings",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

class SettingsRepository(context: Context) {
    private val store = context.applicationContext.bridgeDataStore
    val settings = store.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }.map(::decode)

    suspend fun update(transform: (BridgeSettings) -> BridgeSettings) {
        store.edit { preferences -> encode(preferences, transform(decode(preferences))) }
    }
    suspend fun observeApps(apps: Map<String, String>) {
        if (apps.isEmpty()) return
        store.edit { p ->
            apps.forEach { (pkg, name) -> p[stringPreferencesKey("app.name.$pkg")] = name }
        }
    }
    suspend fun removeApp(packageName: String) {
        store.edit { p ->
            p.remove(stringPreferencesKey("app.name.$packageName"))
            p.remove(stringPreferencesKey("app.mode.$packageName"))
            p.remove(booleanPreferencesKey("app.noise.$packageName"))
        }
    }
    private fun decode(p: Preferences): BridgeSettings {
        val names = p.asMap().entries.filter { it.key.name.startsWith("app.name.") }
            .associate { it.key.name.removePrefix("app.name.") to (it.value as String) }
        val packages = names.keys + p.asMap().keys.filter { it.name.startsWith("app.mode.") }
            .map { it.name.removePrefix("app.mode.") }
        return BridgeSettings(
            allowOngoing = p[booleanPreferencesKey("ongoing")] ?: false,
            maxNotifications = (p[intPreferencesKey("max")] ?: 5).coerceIn(1, 8),
            newestFirst = p[booleanPreferencesKey("newest")] ?: true,
            hideContent = p[booleanPreferencesKey("private")] ?: false,
            hideContentWhenLocked = p[booleanPreferencesKey("locked")] ?: true,
            appNameSize = (p[intPreferencesKey("appSize")] ?: 14).coerceIn(12, 18),
            contentSize = (p[intPreferencesKey("textSize")] ?: 15).coerceIn(12, 20),
            maxLines = (p[intPreferencesKey("lines")] ?: 2).coerceIn(1, 3),
            comfortableSpacing = p[booleanPreferencesKey("spacing")] ?: true,
            textTone = enumValues<TextTone>().find { it.name == p[stringPreferencesKey("tone")] } ?: TextTone.AUTOMATIC,
            appRules = packages.associateWith { pkg ->
                AppRule(enumValues<AppMode>().find { it.name == p[stringPreferencesKey("app.mode.$pkg")] } ?: AppMode.FULL,
                    p[booleanPreferencesKey("app.noise.$pkg")] ?: false)
            },
            observedApps = names,
        )
    }
    private fun encode(p: MutablePreferences, s: BridgeSettings) {
        p[booleanPreferencesKey("ongoing")] = s.allowOngoing
        p[intPreferencesKey("max")] = s.maxNotifications.coerceIn(1, 8)
        p[booleanPreferencesKey("newest")] = s.newestFirst
        p[booleanPreferencesKey("private")] = s.hideContent
        p[booleanPreferencesKey("locked")] = s.hideContentWhenLocked
        p[intPreferencesKey("appSize")] = s.appNameSize.coerceIn(12, 18)
        p[intPreferencesKey("textSize")] = s.contentSize.coerceIn(12, 20)
        p[intPreferencesKey("lines")] = s.maxLines.coerceIn(1, 3)
        p[booleanPreferencesKey("spacing")] = s.comfortableSpacing
        p[stringPreferencesKey("tone")] = s.textTone.name
        s.appRules.forEach { (pkg, rule) ->
            p[stringPreferencesKey("app.mode.$pkg")] = rule.mode.name
            p[booleanPreferencesKey("app.noise.$pkg")] = rule.includeNoise
        }
    }
}
