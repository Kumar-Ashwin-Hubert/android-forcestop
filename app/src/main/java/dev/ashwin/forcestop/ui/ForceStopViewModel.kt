package dev.ashwin.forcestop.ui

import android.app.Application
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.ashwin.forcestop.data.InstalledApp
import dev.ashwin.forcestop.data.InstalledAppsRepository
import dev.ashwin.forcestop.data.SelectionStore
import dev.ashwin.forcestop.data.SelectionError
import dev.ashwin.forcestop.service.AppTarget
import dev.ashwin.forcestop.service.ForceStopController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

data class AppListUiState(
    val loading: Boolean = true,
    val apps: List<InstalledApp> = emptyList(),
    val totalCount: Int = 0,
    val ambiguousLabels: Set<String> = emptySet(),
    val selected: Set<String> = emptySet(),
    val query: String = "",
    val showSystemApps: Boolean = false,
    val selectedOnly: Boolean = false,
    val selectionReady: Boolean = false,
    val selectionUsable: Boolean = false,
    val selectionError: SelectionError? = null,
)

private data class AppFilters(
    val query: String = "",
    val showSystemApps: Boolean = false,
    val selectedOnly: Boolean = false,
)

internal fun filterApps(
    apps: List<InstalledApp>,
    selected: Set<String>,
    query: String,
    showSystemApps: Boolean,
    selectedOnly: Boolean,
): List<InstalledApp> = apps.filter { app ->
    val isSelected = app.packageName in selected
    val visible = if (selectedOnly) isSelected else showSystemApps || !app.isSystem || isSelected
    visible && (app.label.contains(query.trim(), ignoreCase = true) ||
        app.packageName.contains(query.trim(), ignoreCase = true))
}

/** Labels carried by more than one installed app, where the name alone is not enough. */
internal fun ambiguousLabels(apps: List<InstalledApp>): Set<String> =
    apps.groupingBy { it.label.lowercase(Locale.ROOT) }
        .eachCount()
        .filterValues { it > 1 }
        .keys

/**
 * A package name is noise next to a name you already recognise. Show it only when the
 * label is shared with another app, or when the search matched the package instead of
 * the label and the row would otherwise look like it does not belong.
 */
internal fun shouldShowPackage(
    app: InstalledApp,
    query: String,
    ambiguousLabels: Set<String>,
): Boolean {
    if (app.label.lowercase(Locale.ROOT) in ambiguousLabels) return true
    val trimmed = query.trim()
    return trimmed.isNotEmpty() && !app.label.contains(trimmed, ignoreCase = true)
}

class ForceStopViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = InstalledAppsRepository(application)
    private val store = SelectionStore(application)

    private val allApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    private val loading = MutableStateFlow(true)
    private val filters = MutableStateFlow(AppFilters())

    val uiState: StateFlow<AppListUiState> = combine(
        allApps,
        store.state,
        filters,
        loading,
        allApps.map(::ambiguousLabels),
    ) { apps, selection, filters, loading, ambiguous ->
        val selected = selection.packages
        AppListUiState(
            loading = loading,
            apps = filterApps(apps, selected, filters.query, filters.showSystemApps, filters.selectedOnly),
            totalCount = apps.size,
            ambiguousLabels = ambiguous,
            selected = selected,
            query = filters.query,
            showSystemApps = filters.showSystemApps,
            selectedOnly = filters.selectedOnly,
            selectionReady = selection.ready,
            selectionUsable = selection.usable,
            selectionError = selection.error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppListUiState())

    /** Kept off [uiState] so it is not rebuilt on every keystroke. */
    val icons: StateFlow<Map<String, ImageBitmap>> = allApps
        .map { list -> list.mapNotNull { app -> app.icon?.let { app.packageName to it } }.toMap() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    init {
        viewModelScope.launch { store.observe() }
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
        filters.value = filters.value.copy(query = value)
    }

    fun onShowSystemAppsChange(value: Boolean) {
        filters.value = filters.value.copy(showSystemApps = value)
    }

    fun onSelectedOnlyChange(value: Boolean) {
        filters.value = filters.value.copy(selectedOnly = value)
    }

    fun toggle(packageName: String) {
        viewModelScope.launch { store.toggle(packageName) }
    }

    fun clearSelection() {
        viewModelScope.launch { store.clear() }
    }

    fun retrySelection() = store.retryRead()

    /** Runs over the whole saved selection, not just what the current filter shows. */
    fun startRun(): Boolean {
        val selection = store.state.value
        if (loading.value || !selection.ready) return false
        val selected = selection.packages
        val targets = allApps.value
            .filter { it.packageName in selected }
            .map { AppTarget(it.packageName, it.label) }
        return ForceStopController.start(targets)
    }
}
