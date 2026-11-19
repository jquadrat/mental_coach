package com.mentalcoach.data.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mentalcoach.domain.Lang
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

data class Settings(
    val lang: Lang = Lang.EN,
    val maxExercises: Int = 3,
    val voiceName: String = "",
    val rate: Float = 0.95f,
    val voiceOn: Boolean = true,
    /** Help contacts shown on the crisis screen, one per line. Empty = the built-in defaults. */
    val helplines: String = "",
    val demoMode: Boolean = false,
)

interface SettingsRepository {
    val settings: Flow<Settings>

    suspend fun update(f: (Settings) -> Settings)
}

private val Context.dataStore by preferencesDataStore(name = "settings")

class DataStoreSettingsRepository(
    private val context: Context,
    private val defaults: Settings,
) : SettingsRepository {
    private object K {
        val lang = stringPreferencesKey("lang")
        val n = intPreferencesKey("maxExercises")
        val voice = stringPreferencesKey("voiceName")
        val rate = floatPreferencesKey("rate")
        val voiceOn = booleanPreferencesKey("voiceOn")
        val helplines = stringPreferencesKey("helplines")
        val demo = booleanPreferencesKey("demoMode")
    }

    override val settings: Flow<Settings> = context.dataStore.data.map { it.toSettings() }

    private fun Preferences.toSettings() =
        Settings(
            lang = this[K.lang]?.let { Lang.from(it) } ?: defaults.lang,
            maxExercises = this[K.n] ?: defaults.maxExercises,
            voiceName = this[K.voice] ?: defaults.voiceName,
            rate = this[K.rate] ?: defaults.rate,
            voiceOn = this[K.voiceOn] ?: defaults.voiceOn,
            helplines = this[K.helplines] ?: defaults.helplines,
            demoMode = this[K.demo] ?: defaults.demoMode,
        )

    override suspend fun update(f: (Settings) -> Settings) {
        context.dataStore.edit { p ->
            val s = f(p.toSettings())
            p[K.lang] = s.lang.code
            p[K.n] = s.maxExercises.coerceIn(1, 10)
            p[K.voice] = s.voiceName
            p[K.rate] = s.rate
            p[K.voiceOn] = s.voiceOn
            p[K.helplines] = s.helplines
            p[K.demo] = s.demoMode
        }
    }
}

class InMemorySettingsRepository(
    initial: Settings = Settings(),
) : SettingsRepository {
    override val settings = MutableStateFlow(initial)

    override suspend fun update(f: (Settings) -> Settings) {
        settings.value = f(settings.value)
    }
}
