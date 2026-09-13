package dev.ashwin.forcestop.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.ashwin.forcestop.data.InstalledApp
import dev.ashwin.forcestop.data.InstalledAppsRepository
import dev.ashwin.forcestop.data.SelectionStore
import dev.ashwin.forcestop.service.AppTarget
import dev.ashwin.forcestop.service.ForceStopController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AppListUiState(
    val loading: Boolean = true,
    val apps: List<InstalledApp> = emptyList(),
    val selected: Set<String> = emptySet(),
    val query: String = "",
    val showSystemApps: Boolean = false,
)

class ForceStopViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = InstalledAppsRepository(application)
    private val store = SelectionStore(application)

    private val allApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    private val loading = MutableStateFlow(true)
    private val query = MutableStateFlow("")
    private val showSystemApps = MutableStateFlow(false)

    val uiState: StateFlow<AppListUiState> = combine(
        allApps,
        store.selected,
        query,
        showSystemApps,
        loading,
    ) { apps, selected, query, showSystem, loading ->
        AppListUiState(
            loading = loading,
            apps = apps.filter { app ->
                // Already-selected apps stay listed so a hidden one can still be removed.
                val visible = showSystem || !app.isSystem || app.packageName in selected
                visible && app.label.contains(query, ignoreCase = true)
            },
            selected = selected,
            query = query,
            showSystemApps = showSystem,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppListUiState())

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            loading.value = true
            allApps.value = repository.launchableApps()
            loading.value = false
        }
    }

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun onShowSystemAppsChange(value: Boolean) {
        showSystemApps.value = value
    }

    fun toggle(packageName: String) {
        viewModelScope.launch { store.toggle(packageName) }
    }

    fun clearSelection() {
        viewModelScope.launch { store.clear() }
    }

    /** Runs over the whole saved selection, not just what the current filter shows. */
    fun startRun(): Boolean {
        val selected = uiState.value.selected
        val targets = allApps.value
            .filter { it.packageName in selected }
            .map { AppTarget(it.packageName, it.label) }
        return ForceStopController.start(targets)
    }
}
