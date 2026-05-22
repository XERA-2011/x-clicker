package dev.xera.xclicker.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {

    companion object {
        private val KEY_SERVICE_ENABLED = booleanPreferencesKey("service_enabled")
        private val KEY_GLOBAL_DELAY = longPreferencesKey("global_delay")
    }

    val serviceEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[KEY_SERVICE_ENABLED] ?: false }

    val globalDelay: Flow<Long> = context.dataStore.data
        .map { preferences -> preferences[KEY_GLOBAL_DELAY] ?: 0L }

    suspend fun setServiceEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_SERVICE_ENABLED] = enabled
        }
    }

    suspend fun setGlobalDelay(delay: Long) {
        context.dataStore.edit { preferences ->
            preferences[KEY_GLOBAL_DELAY] = delay
        }
    }
}
