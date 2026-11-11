package com.paisatrail.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, DARK, LIGHT }

data class AppSettings(
    val theme: ThemeMode = ThemeMode.DARK,
    val userName: String = "",
    val userUpiId: String = "",
    val geminiApiKey: String = "",
    val geminiModel: String = DEFAULT_GEMINI_MODEL,
    val remindersEnabled: Boolean = false,
    /** Switches off decorative animation (drifting backdrop, cascades, count-ups). */
    val reduceMotion: Boolean = false
) {
    companion object {
        const val DEFAULT_GEMINI_MODEL = "gemini-2.5-flash"
    }
}

@Singleton
class SettingsStore @Inject constructor(private val dataStore: DataStore<Preferences>) {

    val settings: Flow<AppSettings> = dataStore.data.map { prefs ->
        AppSettings(
            theme = runCatching { ThemeMode.valueOf(prefs[THEME] ?: ThemeMode.DARK.name) }.getOrDefault(ThemeMode.DARK),
            userName = prefs[USER_NAME].orEmpty(),
            userUpiId = prefs[USER_UPI].orEmpty(),
            geminiApiKey = prefs[GEMINI_KEY].orEmpty(),
            geminiModel = prefs[GEMINI_MODEL]?.takeIf { it.isNotBlank() } ?: AppSettings.DEFAULT_GEMINI_MODEL,
            remindersEnabled = prefs[REMINDERS] ?: false,
            reduceMotion = prefs[REDUCE_MOTION] ?: false
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setTheme(mode: ThemeMode) = dataStore.edit { it[THEME] = mode.name }

    suspend fun setProfile(name: String, upiId: String) = dataStore.edit {
        it[USER_NAME] = name.trim()
        it[USER_UPI] = upiId.trim()
    }

    suspend fun setGemini(apiKey: String, model: String) = dataStore.edit {
        it[GEMINI_KEY] = apiKey.trim()
        it[GEMINI_MODEL] = model.trim()
    }

    suspend fun setReminders(enabled: Boolean) = dataStore.edit { it[REMINDERS] = enabled }

    suspend fun setReduceMotion(enabled: Boolean) = dataStore.edit { it[REDUCE_MOTION] = enabled }

    private companion object {
        val THEME = stringPreferencesKey("theme")
        val USER_NAME = stringPreferencesKey("user_name")
        val USER_UPI = stringPreferencesKey("user_upi")
        val GEMINI_KEY = stringPreferencesKey("gemini_api_key")
        val GEMINI_MODEL = stringPreferencesKey("gemini_model")
        val REMINDERS = booleanPreferencesKey("reminders_enabled")
        val REDUCE_MOTION = booleanPreferencesKey("reduce_motion")
    }
}
