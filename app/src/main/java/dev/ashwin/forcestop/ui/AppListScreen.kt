package dev.ashwin.forcestop.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ashwin.forcestop.R
import dev.ashwin.forcestop.data.InstalledApp
import dev.ashwin.forcestop.data.SelectionError
import dev.ashwin.forcestop.service.ForceStopController
import dev.ashwin.forcestop.service.RunState
import dev.ashwin.forcestop.service.StopOutcome
import dev.ashwin.forcestop.service.StopResult
import kotlinx.coroutines.delay

private val CardShape = RoundedCornerShape(16.dp)
private val ControlShape = RoundedCornerShape(12.dp)
private val ChipShape = RoundedCornerShape(10.dp)
private val CheckShape = RoundedCornerShape(7.dp)
private val DialogShape = RoundedCornerShape(24.dp)

@Composable
fun AppListScreen(viewModel: ForceStopViewModel, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val icons by viewModel.icons.collectAsStateWithLifecycle()
    val serviceConnected by ForceStopController.connected.collectAsStateWithLifecycle()
    val runState by ForceStopController.state.collectAsStateWithLifecycle()

    var canDrawOverlays by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    OnResume { canDrawOverlays = Settings.canDrawOverlays(context) }

    val running = runState is RunState.Running
    val ready = serviceConnected && canDrawOverlays
    val selectable = state.selectionUsable && !running
    var searchFocused by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) focusManager.clearFocus()
    }

    // Latches once so rows scrolled in later start at rest instead of replaying the reveal.
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(state.loading) {
        if (!state.loading) {
            delay(80)
            revealed = true
        }
    }

    val blocker = when {
        !state.selectionUsable -> "Saved selection couldn't be read"
        !serviceConnected -> "Accessibility service is off"
        !canDrawOverlays -> "Display over other apps is off"
        state.selected.isEmpty() -> "Choose the apps you want stopped"
        else -> null
    }

    Scaffold(
        modifier = modifier.imePadding(),
        containerColor = c.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Column(Modifier.background(c.background).statusBarsPadding()) {
                Masthead(
                    selectedCount = state.selected.size,
                    totalCount = state.totalCount,
                    loading = state.loading,
                    canClear = state.selected.isNotEmpty() && selectable,
                    onClearSelection = viewModel::clearSelection,
                )
                Segmented(
                    selectedOnly = state.selectedOnly,
                    selectedCount = state.selected.size,
                    onChange = {
                        focusManager.clearFocus()
                        viewModel.onSelectedOnlyChange(it)
                    },
                )
                FilterRow(
                    query = state.query,
                    showSystemApps = state.showSystemApps,
                    showSystemToggle = !state.selectedOnly,
                    onQueryChange = viewModel::onQueryChange,
                    onShowSystemAppsChange = viewModel::onShowSystemAppsChange,
                    onSearchFocusChange = { searchFocused = it },
                )
                // While filtering, the viewport matters more than the reminder; the action
                // bar still names the blocker.
                if (!ready && !searchFocused) {
                    SetupCard(
                        serviceConnected = serviceConnected,
                        canDrawOverlays = canDrawOverlays,
                        onAccessibilityClick = { context.openAccessibilitySettings() },
                        onOverlayClick = { context.openOverlaySettings() },
                    )
                }
                state.selectionError?.let { NoticeCard(it, viewModel::retrySelection) }
                Spacer(Modifier.height(4.dp))
            }
        },
        bottomBar = {
            ActionBar(
                label = if (state.selected.isEmpty()) {
                    "Force stop"
                } else {
                    "Force stop ${state.selected.size} app${plural(state.selected.size)}"
                },
                blocker = blocker,
                enabled = ready && !running && !state.loading &&
                    state.selectionUsable && state.selected.isNotEmpty(),
                onClick = { viewModel.startRun() },
            )
        },
    ) { insets ->
        Box(Modifier.padding(insets)) {
            when {
                state.loading -> MessagePanel("Reading installed apps\u2026")
                state.apps.isEmpty() -> EmptyPanel(
                    state = state,
                    onClearQuery = { viewModel.onQueryChange("") },
                    onShowAll = { viewModel.onSelectedOnlyChange(false) },
                )
                else -> LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(bottom = 12.dp),
                ) {
                    itemsIndexed(state.apps, key = { _, app -> app.packageName }) { index, app ->
                        AppRow(
                            app = app,
                            checked = app.packageName in state.selected,
                            showPackage = shouldShowPackage(app, state.query, state.ambiguousLabels),
                            enabled = selectable,
                            revealed = revealed,
                            index = index,
                            onToggle = { viewModel.toggle(app.packageName) },
                        )
                    }
                }
            }
        }
    }

    when (val current = runState) {
        is RunState.Running -> ProgressPanel(current)
        is RunState.Finished -> ResultsPanel(
            results = current.results,
            icons = icons,
            onDismiss = ForceStopController::acknowledgeResults,
        )
        RunState.Idle -> Unit
    }
}

@Composable
private fun Masthead(
    selectedCount: Int,
    totalCount: Int,
    loading: Boolean,
    canClear: Boolean,
    onClearSelection: () -> Unit,
) {
    val c = AppTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(38.dp).clip(ChipShape).background(c.surfaceAlt),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_power),
                contentDescription = null,
                tint = c.accent,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text("Force Stop", style = MaterialTheme.typography.titleLarge, color = c.text)
            Spacer(Modifier.height(1.dp))
            Text(
                when {
                    loading -> "Loading your apps"
                    selectedCount == 0 -> "$totalCount apps \u00b7 none selected yet"
                    else -> "$selectedCount of $totalCount apps selected"
                },
                style = MaterialTheme.typography.bodySmall,
                color = c.textMuted,
            )
        }
        if (canClear) {
            Box(
                Modifier
                    .heightIn(min = 48.dp)
                    .clip(ChipShape)
                    .clickable(onClick = onClearSelection)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Clear",
                    style = MaterialTheme.typography.labelMedium,
                    color = c.accent,
                )
            }
        }
    }
}

@Composable
private fun Segmented(selectedOnly: Boolean, selectedCount: Int, onChange: (Boolean) -> Unit) {
    val c = AppTheme.colors
    val slide by animateFloatAsState(
        targetValue = if (selectedOnly) 1f else 0f,
        animationSpec = tween(260, easing = FastOutSlowInEasing),
        label = "segment",
    )
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(48.dp)
            .clip(ControlShape)
            .background(c.surfaceAlt),
    ) {
        // The 4dp inset lives on the pill, not the track, so each segment keeps a full-height tap area.
        Box(
            Modifier
                .fillMaxWidth(0.5f)
                .fillMaxHeight()
                .graphicsLayer { translationX = slide * size.width }
                .padding(4.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(c.raised),
        )
        Row(Modifier.fillMaxSize()) {
            Segment("All apps", !selectedOnly, Modifier.weight(1f)) { onChange(false) }
            Segment(
                if (selectedCount > 0) "Selected \u00b7 $selectedCount" else "Selected",
                selectedOnly,
                Modifier.weight(1f),
            ) { onChange(true) }
        }
    }
}

@Composable
private fun Segment(label: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = AppTheme.colors
    val color by animateColorAsState(
        targetValue = if (active) c.text else c.textMuted,
        animationSpec = tween(200),
        label = "segmentInk",
    )
    Box(
        modifier.fillMaxHeight().clip(RoundedCornerShape(9.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
            color = color,
        )
    }
}

@Composable
private fun FilterRow(
    query: String,
    showSystemApps: Boolean,
    showSystemToggle: Boolean,
    onQueryChange: (String) -> Unit,
    onShowSystemAppsChange: (Boolean) -> Unit,
    onSearchFocusChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SearchField(query, onQueryChange, onSearchFocusChange, Modifier.weight(1f))
        if (showSystemToggle) {
            TogglePill("System", showSystemApps) { onShowSystemAppsChange(!showSystemApps) }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onFocusChange: (Boolean) -> Unit,
    modifier: Modifier,
) {
    val c = AppTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val focusManager = LocalFocusManager.current
    LaunchedEffect(focused) { onFocusChange(focused) }
    val border by animateColorAsState(
        targetValue = if (focused) c.accent else Color.Transparent,
        animationSpec = tween(180),
        label = "searchBorder",
    )
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        interactionSource = interactionSource,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = c.text),
        cursorBrush = SolidColor(c.accent),
        modifier = modifier,
        decorationBox = { field ->
            Row(
                Modifier
                    .height(48.dp)
                    .clip(ControlShape)
                    .background(c.surfaceAlt)
                    .border(1.5.dp, border, ControlShape)
                    .padding(start = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    tint = if (focused) c.accent else c.textMuted,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            "Search apps",
                            style = MaterialTheme.typography.bodyMedium,
                            color = c.textMuted,
                        )
                    }
                    field()
                }
                if (query.isNotEmpty()) {
                    Icon(
                        Icons.Default.Clear,
                        contentDescription = "Clear search",
                        tint = c.textMuted,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(ControlShape)
                            .clickable { onQueryChange("") }
                            .padding(15.dp),
                    )
                } else {
                    Spacer(Modifier.width(14.dp))
                }
            }
        },
    )
}

@Composable
private fun TogglePill(label: String, active: Boolean, onClick: () -> Unit) {
    val c = AppTheme.colors
    val fill by animateColorAsState(
        targetValue = if (active) c.accentSoft else c.surfaceAlt,
        animationSpec = tween(180),
        label = "pillFill",
    )
    val ink by animateColorAsState(
        targetValue = if (active) c.accent else c.textMuted,
        animationSpec = tween(180),
        label = "pillInk",
    )
    Box(
        Modifier
            .height(48.dp)
            .clip(ControlShape)
            .background(fill)
            .clickable(onClick = onClick, role = Role.Switch)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
            color = ink,
        )
    }
}

@Composable
private fun SetupCard(
    serviceConnected: Boolean,
    canDrawOverlays: Boolean,
    onAccessibilityClick: () -> Unit,
    onOverlayClick: () -> Unit,
) {
    val c = AppTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 8.dp)
            .clip(CardShape)
            .background(c.surfaceAlt)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text("Finish setup", style = MaterialTheme.typography.titleMedium, color = c.text)
        Spacer(Modifier.height(3.dp))
        Text(
            "Force Stop needs these before it can run.",
            style = MaterialTheme.typography.bodySmall,
            color = c.textMuted,
        )
        Spacer(Modifier.height(6.dp))
        if (!serviceConnected) SetupLine("Accessibility service", onAccessibilityClick)
        if (!canDrawOverlays) SetupLine("Display over other apps", onOverlayClick)
    }
}

@Composable
private fun SetupLine(title: String, onClick: () -> Unit) {
    val c = AppTheme.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            color = c.text,
            modifier = Modifier.weight(1f),
        )
        Box(
            Modifier
                .heightIn(min = 48.dp)
                .clip(ChipShape)
                .background(c.accent)
                .clickable(onClick = onClick)
                .padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("Grant", style = MaterialTheme.typography.labelMedium, color = c.onAccent)
        }
    }
}

@Composable
private fun NoticeCard(error: SelectionError, onRetry: () -> Unit) {
    val c = AppTheme.colors
    val read = error == SelectionError.READ
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 8.dp)
            .clip(CardShape)
            .background(c.dangerSoft)
            .padding(16.dp),
    ) {
        Text(
            if (read) "Selection unavailable" else "Selection not saved",
            style = MaterialTheme.typography.titleMedium,
            color = c.danger,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            if (read) {
                "Your saved selection couldn't be read. Nothing has been overwritten."
            } else {
                "The change couldn't be saved. Reload the saved selection, then try again."
            },
            style = MaterialTheme.typography.bodySmall,
            color = c.textMuted,
        )
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .heightIn(min = 48.dp)
                .clip(ChipShape)
                .clickable(onClick = onRetry)
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (read) "Retry" else "Reload selection",
                style = MaterialTheme.typography.labelMedium,
                color = c.danger,
            )
        }
    }
}

@Composable
private fun AppRow(
    app: InstalledApp,
    checked: Boolean,
    showPackage: Boolean,
    enabled: Boolean,
    revealed: Boolean,
    index: Int,
    onToggle: () -> Unit,
) {
    val c = AppTheme.colors
    val reveal by animateFloatAsState(
        targetValue = if (revealed) 1f else 0f,
        animationSpec = tween(300, delayMillis = index.coerceAtMost(14) * 26, easing = FastOutSlowInEasing),
        label = "rowReveal",
    )
    val wash by animateColorAsState(
        targetValue = if (checked) c.accentSoft else Color.Transparent,
        animationSpec = tween(200),
        label = "rowWash",
    )
    Box(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = reveal
                translationY = (1f - reveal) * 10.dp.toPx()
            }
            .height(64.dp)
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .clip(CardShape)
            .background(wash)
            .toggleable(value = checked, enabled = enabled, role = Role.Checkbox) { onToggle() }
            .padding(horizontal = 10.dp),
    ) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(app.icon, app.label)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    app.label,
                    style = MaterialTheme.typography.titleSmall,
                    color = c.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                rowSubtitle(app, showPackage)?.let { subtitle ->
                    Spacer(Modifier.height(2.dp))
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Marker(checked, enabled)
        }
    }
}

@Composable
private fun AppIcon(icon: ImageBitmap?, label: String, size: Dp = 42.dp) {
    val c = AppTheme.colors
    if (icon != null) {
        Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(size))
    } else {
        Box(
            Modifier.size(size).clip(ChipShape).background(c.surfaceAlt),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label.take(1).uppercase(),
                style = MaterialTheme.typography.titleSmall,
                color = c.textMuted,
            )
        }
    }
}

@Composable
private fun Marker(checked: Boolean, enabled: Boolean) {
    val c = AppTheme.colors
    val fill by animateColorAsState(
        targetValue = if (checked) c.accent else Color.Transparent,
        animationSpec = tween(160),
        label = "markerFill",
    )
    val border by animateColorAsState(
        targetValue = when {
            !enabled -> c.outlineSoft
            checked -> c.accent
            else -> c.textMuted
        },
        animationSpec = tween(160),
        label = "markerBorder",
    )
    Box(
        Modifier.size(22.dp).clip(CheckShape).background(fill).border(1.5.dp, border, CheckShape),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = c.onAccent,
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

@Composable
private fun ActionBar(label: String, blocker: String?, enabled: Boolean, onClick: () -> Unit) {
    val c = AppTheme.colors
    Column(Modifier.background(c.background).navigationBarsPadding()) {
        if (blocker != null) {
            Text(
                blocker,
                style = MaterialTheme.typography.bodySmall,
                color = c.textMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .height(56.dp)
                .clip(CardShape)
                .background(if (enabled) c.actionFill else c.surfaceAlt)
                // Without an edge the disabled fill is within 2% of the background and reads as nothing at all.
                .border(1.dp, if (enabled) Color.Transparent else c.outline, CardShape)
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = if (enabled) c.actionInk else c.textFaint,
            )
        }
    }
}

@Composable
private fun MessagePanel(message: String) {
    val c = AppTheme.colors
    Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
    }
}

@Composable
private fun EmptyPanel(state: AppListUiState, onClearQuery: () -> Unit, onShowAll: () -> Unit) {
    val c = AppTheme.colors
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            when {
                !state.selectionUsable -> "Selection unavailable"
                state.query.isNotBlank() -> "No matches"
                state.selectedOnly -> "Nothing selected yet"
                else -> "No apps found"
            },
            style = MaterialTheme.typography.headlineSmall,
            color = c.text,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            when {
                state.query.isNotBlank() -> "No app name or package contains \u201c${state.query.trim()}\u201d."
                state.selectedOnly -> "Pick apps on the All apps tab to build your list."
                else -> "Nothing to show here."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = c.textMuted,
            textAlign = TextAlign.Center,
        )
        if (state.query.isNotEmpty() || state.selectedOnly) {
            Spacer(Modifier.height(20.dp))
            Box(
                Modifier
                    .heightIn(min = 48.dp)
                    .clip(ChipShape)
                    .background(c.surfaceAlt)
                    .clickable { if (state.query.isNotEmpty()) onClearQuery() else onShowAll() }
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (state.query.isNotEmpty()) "Clear search" else "Browse all apps",
                    style = MaterialTheme.typography.labelMedium,
                    color = c.text,
                )
            }
        }
    }
}

@Composable
private fun ProgressPanel(state: RunState.Running) {
    val c = AppTheme.colors
    PanelDialog(onDismiss = null) {
        Text("Stopping apps", style = MaterialTheme.typography.headlineSmall, color = c.text)
        Spacer(Modifier.height(22.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(state.index, state.total)
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    state.target.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = c.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    state.target.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ProgressRing(index: Int, total: Int) {
    val c = AppTheme.colors
    val progress by animateFloatAsState(
        targetValue = if (total == 0) 0f else index.toFloat() / total,
        animationSpec = tween(340, easing = FastOutSlowInEasing),
        label = "ring",
    )
    Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(56.dp)) {
            val stroke = 3.dp.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)
            drawArc(
                color = c.outline,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = c.accent,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        Text(
            "$index/$total",
            style = MaterialTheme.typography.bodySmall,
            color = c.textMuted,
        )
    }
}

@Composable
private fun ResultsPanel(
    results: List<StopResult>,
    icons: Map<String, ImageBitmap>,
    onDismiss: () -> Unit,
) {
    val c = AppTheme.colors
    val failed = results.count { it.outcome == StopOutcome.FAILED }
    val stopped = results.size - failed
    val listState = rememberLazyListState()

    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { settled = true }
    val counter by animateIntAsState(
        targetValue = if (settled) stopped else 0,
        animationSpec = tween(640, delayMillis = 140, easing = FastOutSlowInEasing),
        label = "counter",
    )

    PanelDialog(onDismiss = onDismiss) {
        Text(
            "$counter",
            style = MaterialTheme.typography.displaySmall,
            color = if (failed > 0) c.danger else c.accent,
        )
        Spacer(Modifier.height(2.dp))
        Text(resultHeadline(results), style = MaterialTheme.typography.headlineSmall, color = c.text)
        Spacer(Modifier.height(4.dp))
        Text(resultSummary(results), style = MaterialTheme.typography.bodySmall, color = c.textMuted)
        Spacer(Modifier.height(18.dp))
        Box {
            LazyColumn(state = listState, modifier = Modifier.heightIn(max = 360.dp)) {
                itemsIndexed(
                    resultsWithFailuresFirst(results),
                    key = { _, result -> result.target.packageName },
                ) { index, result ->
                    ResultRow(result, icons[result.target.packageName], settled, index)
                }
            }
            if (listState.canScrollForward) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(40.dp)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, c.surface))),
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(CardShape)
                .background(c.actionFill)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Text("Done", style = MaterialTheme.typography.labelLarge, color = c.actionInk)
        }
    }
}

@Composable
private fun ResultRow(result: StopResult, icon: ImageBitmap?, settled: Boolean, index: Int) {
    val c = AppTheme.colors
    val failed = result.outcome == StopOutcome.FAILED
    val reveal by animateFloatAsState(
        targetValue = if (settled) 1f else 0f,
        animationSpec = tween(280, delayMillis = 220 + index.coerceAtMost(10) * 36),
        label = "resultReveal",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = reveal
                translationY = (1f - reveal) * 8.dp.toPx()
            }
            .clip(ControlShape)
            .background(if (failed) c.dangerSoft else Color.Transparent)
            .padding(horizontal = 8.dp, vertical = 9.dp),
        verticalAlignment = Alignment.Top,
    ) {
        AppIcon(icon, result.target.label, size = 30.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                result.target.label,
                style = MaterialTheme.typography.bodyLarge,
                color = c.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                result.target.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = c.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (failed) {
                result.detail?.takeIf { it.isNotBlank() }?.let { detail ->
                    Spacer(Modifier.height(5.dp))
                    Text(detail, style = MaterialTheme.typography.bodySmall, color = c.danger)
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        // Success stays quiet on purpose; only failures need to be noticed.
        Text(
            result.outcome.summary(),
            style = MaterialTheme.typography.bodySmall,
            color = if (failed) c.danger else c.textMuted,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun PanelDialog(onDismiss: (() -> Unit)?, content: @Composable () -> Unit) {
    val c = AppTheme.colors
    Dialog(
        onDismissRequest = { onDismiss?.invoke() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = onDismiss != null,
            dismissOnClickOutside = onDismiss != null,
        ),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .clip(DialogShape)
                .background(c.surface)
                .padding(horizontal = 24.dp, vertical = 26.dp),
        ) {
            content()
        }
    }
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

internal fun resultHeadline(results: List<StopResult>): String {
    val failed = results.count { it.outcome == StopOutcome.FAILED }
    return when {
        results.isEmpty() -> "No apps processed"
        failed > 0 -> "$failed app${plural(failed)} couldn't be stopped"
        results.size == 1 -> "1 app stopped"
        else -> "All ${results.size} apps are stopped"
    }
}

internal fun resultSummary(results: List<StopResult>): String {
    val failed = results.count { it.outcome == StopOutcome.FAILED }
    return "${results.size - failed} stopped \u00b7 $failed failed"
}

internal fun resultsWithFailuresFirst(results: List<StopResult>): List<StopResult> =
    results.sortedBy { if (it.outcome == StopOutcome.FAILED) 0 else 1 }

internal fun StopOutcome.summary(): String = when (this) {
    StopOutcome.STOPPED, StopOutcome.ALREADY_STOPPED -> "Stopped"
    StopOutcome.FAILED -> "Failed"
}

private fun plural(count: Int) = if (count == 1) "" else "s"

/** The System marker stays independent of the package name: it is a safety cue, not a detail. */
internal fun rowSubtitle(app: InstalledApp, showPackage: Boolean): String? = when {
    app.isSystem && showPackage -> "System \u00b7 ${app.packageName}"
    app.isSystem -> "System"
    showPackage -> app.packageName
    else -> null
}

private fun Context.openAccessibilitySettings() {
    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
}

private fun Context.openOverlaySettings() {
    startActivity(
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:$packageName".toUri()),
    )
}
