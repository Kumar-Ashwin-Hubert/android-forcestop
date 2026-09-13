package dev.ashwin.forcestop.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("force_stop")
private val SELECTED_PACKAGES = stringSetPreferencesKey("selected_packages")

class SelectionStore(private val context: Context) {

    val selected: Flow<Set<String>> =
        context.dataStore.data.map { it[SELECTED_PACKAGES].orEmpty() }

    suspend fun toggle(packageName: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[SELECTED_PACKAGES].orEmpty()
            prefs[SELECTED_PACKAGES] =
                if (packageName in current) current - packageName else current + packageName
        }
    }

    suspend fun clear() {
        context.dataStore.edit { it[SELECTED_PACKAGES] = emptySet() }
    }
}
