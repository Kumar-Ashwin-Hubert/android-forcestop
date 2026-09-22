package dev.ashwin.forcestop.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("force_stop")
private val SELECTED_PACKAGES = stringSetPreferencesKey("selected_packages")

enum class SelectionError { READ, SAVE }

data class SelectionState(
    val packages: Set<String> = emptySet(),
    val loaded: Boolean = false,
    val saving: Boolean = false,
    val error: SelectionError? = null,
) {
    /** Safe to start a run: nothing is in flight and the data is trustworthy. */
    val ready: Boolean get() = loaded && !saving && error == null

    /**
     * The data is trustworthy, whether or not a write is in flight. An in-flight write is
     * not a problem the user needs to see, so UI state hangs off this rather than [ready].
     */
    val usable: Boolean get() = loaded && error == null
}

class SelectionStore(private val dataStore: DataStore<Preferences>) {

    constructor(context: Context) : this(context.dataStore)

    private val mutableState = MutableStateFlow(SelectionState())
    val state = mutableState.asStateFlow()
    private val readAttempts = MutableStateFlow(0L)
    private val writes = Mutex()

    suspend fun observe() {
        readAttempts.collectLatest {
            mutableState.update { it.copy(loaded = false, error = null) }
            try {
                dataStore.data.collect { prefs ->
                    mutableState.update {
                        it.copy(packages = prefs[SELECTED_PACKAGES].orEmpty().toSet(), loaded = true)
                    }
                }
            } catch (_: IOException) {
                mutableState.update { it.copy(loaded = false, error = SelectionError.READ) }
            }
        }
    }

    fun retryRead() {
        if (state.value.saving) return
        mutableState.update { it.copy(loaded = false, error = null) }
        readAttempts.update { it + 1 }
    }

    suspend fun toggle(packageName: String) {
        write { current ->
            if (packageName in current) current - packageName else current + packageName
        }
    }

    suspend fun clear() {
        write { emptySet() }
    }

    private suspend fun write(transform: (Set<String>) -> Set<String>) = writes.withLock {
        if (!state.value.ready) return@withLock
        mutableState.update { it.copy(saving = true) }
        try {
            val saved = dataStore.edit { prefs ->
                prefs[SELECTED_PACKAGES] = transform(prefs[SELECTED_PACKAGES].orEmpty())
            }
            mutableState.update { it.copy(packages = saved[SELECTED_PACKAGES].orEmpty().toSet()) }
        } catch (_: IOException) {
            mutableState.update { it.copy(error = SelectionError.SAVE) }
        } finally {
            mutableState.update { it.copy(saving = false) }
        }
    }
}
