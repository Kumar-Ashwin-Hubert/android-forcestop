package dev.ashwin.forcestop.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.DisposableEffect
import dev.ashwin.forcestop.data.InstalledApp
import dev.ashwin.forcestop.service.ForceStopController
import dev.ashwin.forcestop.service.RunState
import dev.ashwin.forcestop.service.StopOutcome
import dev.ashwin.forcestop.service.StopResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppListScreen(viewModel: ForceStopViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val serviceConnected by ForceStopController.connected.collectAsStateWithLifecycle()
    val runState by ForceStopController.state.collectAsStateWithLifecycle()

    var canDrawOverlays by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    OnResume { canDrawOverlays = Settings.canDrawOverlays(context) }

    val running = runState is RunState.Running
    val ready = serviceConnected && canDrawOverlays

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text("Force Stop") }) },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Button(
                    onClick = { viewModel.startRun() },
                    enabled = ready && !running && state.selected.isNotEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp),
                ) {
                    Text(
                        when {
                            state.selected.isEmpty() -> "Pick apps to stop"
                            else -> "Force stop ${state.selected.size} app${plural(state.selected.size)}"
                        },
                    )
                }
            }
        },
    ) { insets ->
        Column(modifier = Modifier.padding(insets)) {
            if (!serviceConnected) {
                SetupCard(
                    title = "Turn on the accessibility service",
                    body = "Settings > Accessibility > Force Stop. This is what lets the app press " +
                        "“Force stop” for you.",
                    action = "Open accessibility settings",
                    onClick = { context.openAccessibilitySettings() },
                )
            }
            if (!canDrawOverlays) {
                SetupCard(
                    title = "Allow display over other apps",
                    body = "Android blocks apps in the background from opening screens. This " +
                        "permission is what lets the run move from one app to the next.",
                    action = "Grant permission",
                    onClick = { context.openOverlaySettings() },
                )
            }

            Filters(
                query = state.query,
                showSystemApps = state.showSystemApps,
                selectedCount = state.selected.size,
                onQueryChange = viewModel::onQueryChange,
                onShowSystemAppsChange = viewModel::onShowSystemAppsChange,
                onClearSelection = viewModel::clearSelection,
            )

            HorizontalDivider()

            if (state.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(state.apps, key = { it.packageName }) { app ->
                        AppRow(
                            app = app,
                            checked = app.packageName in state.selected,
                            onToggle = { viewModel.toggle(app.packageName) },
                        )
                    }
                }
            }
        }
    }

    when (val current = runState) {
        is RunState.Running -> ProgressDialog(current)
        is RunState.Finished -> ResultsDialog(
            results = current.results,
            onDismiss = ForceStopController::acknowledgeResults,
        )
        RunState.Idle -> Unit
    }
}

@Composable
private fun Filters(
    query: String,
    showSystemApps: Boolean,
    selectedCount: Int,
    onQueryChange: (String) -> Unit,
    onShowSystemAppsChange: (Boolean) -> Unit,
    onClearSelection: () -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text("Search apps") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = showSystemApps,
                onClick = { onShowSystemAppsChange(!showSystemApps) },
                label = { Text("System apps") },
            )
            Spacer(Modifier.weight(1f))
            if (selectedCount > 0) {
                TextButton(onClick = onClearSelection) { Text("Clear $selectedCount") }
            }
        }
    }
}

@Composable
private fun AppRow(app: InstalledApp, checked: Boolean, onToggle: () -> Unit) {
    ListItem(
        modifier = Modifier.toggleable(value = checked, role = Role.Checkbox) { onToggle() },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            if (app.icon != null) {
                Image(bitmap = app.icon, contentDescription = null, modifier = Modifier.size(40.dp))
            } else {
                Spacer(Modifier.size(40.dp))
            }
        },
        headlineContent = { Text(app.label) },
        supportingContent = {
            Text(app.packageName, style = MaterialTheme.typography.bodySmall)
        },
        trailingContent = { Checkbox(checked = checked, onCheckedChange = null) },
    )
}

@Composable
private fun SetupCard(title: String, body: String, action: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onClick) { Text(action) }
        }
    }
}

@Composable
private fun ProgressDialog(state: RunState.Running) {
    AlertDialog(
        onDismissRequest = {},
        confirmButton = {},
        title = { Text("Stopping apps") },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(24.dp))
                Spacer(Modifier.size(16.dp))
                Text("${state.label} (${state.index} of ${state.total})")
            }
        },
    )
}

@Composable
private fun ResultsDialog(results: List<StopResult>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        title = { Text("Finished") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(results, key = { it.target.packageName }) { result ->
                    Column {
                        Text(result.target.label, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = result.detail ?: result.outcome.summary(),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (result.outcome == StopOutcome.FAILED) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun OnResume(action: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentAction by rememberUpdatedState(action)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) currentAction()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

private fun StopOutcome.summary(): String = when (this) {
    StopOutcome.STOPPED -> "Stopped"
    StopOutcome.ALREADY_STOPPED -> "Was already stopped"
    StopOutcome.FAILED -> "Couldn't stop it"
}

private fun plural(count: Int) = if (count == 1) "" else "s"

private fun Context.openAccessibilitySettings() {
    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
}

private fun Context.openOverlaySettings() {
    startActivity(
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:$packageName".toUri()),
    )
}
