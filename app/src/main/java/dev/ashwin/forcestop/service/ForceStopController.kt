package dev.ashwin.forcestop.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppTarget(val packageName: String, val label: String)

enum class StopOutcome { STOPPED, ALREADY_STOPPED, FAILED }

data class StopResult(
    val target: AppTarget,
    val outcome: StopOutcome,
    val detail: String? = null,
)

sealed interface RunState {
    data object Idle : RunState

    data class Running(val label: String, val index: Int, val total: Int) : RunState

    data class Finished(val results: List<StopResult>) : RunState
}

/**
 * Bridge between the UI and the accessibility service, which the system owns and can
 * start or stop at any time.
 */
object ForceStopController {

    @Volatile
    private var service: ForceStopAccessibilityService? = null

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _state = MutableStateFlow<RunState>(RunState.Idle)
    val state: StateFlow<RunState> = _state.asStateFlow()

    internal fun attach(service: ForceStopAccessibilityService) {
        this.service = service
        _connected.value = true
    }

    internal fun detach() {
        service = null
        _connected.value = false
    }

    internal fun publish(state: RunState) {
        _state.value = state
    }

    fun start(targets: List<AppTarget>): Boolean {
        if (targets.isEmpty()) return false
        return service?.startRun(targets) == true
    }

    fun acknowledgeResults() {
        _state.value = RunState.Idle
    }
}
