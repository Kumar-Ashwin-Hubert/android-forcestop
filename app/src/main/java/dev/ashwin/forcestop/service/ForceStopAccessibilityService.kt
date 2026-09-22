package dev.ashwin.forcestop.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import dev.ashwin.forcestop.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "ForceStop"
private const val DEFAULT_SETTINGS_PACKAGE = "com.android.settings"
private const val DIALOG_POSITIVE_BUTTON_ID = "android:id/button1"

private const val POLL_INTERVAL_MS = 120L
private const val APP_INFO_TIMEOUT_MS = 6_000L
private const val DIALOG_TIMEOUT_MS = 2_500L
private const val VERIFY_TIMEOUT_MS = 5_000L
private const val SETTLE_MS = 300L
private const val OVERLAY_EXIT_MS = 450L

/**
 * Drives Settings > App info > Force stop for each selected app.
 *
 * The configuration filters Settings events, not window-content access. During a user-started
 * run, [pollSettings] checks each active root's package before searching for or clicking controls.
 */
class ForceStopAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var runJob: Job? = null
    private val overlay by lazy { RunOverlay(this) }

    private var settingsPackage: String = DEFAULT_SETTINGS_PACKAGE
    private var labels: SettingsLabels = SettingsLabels(emptyList(), emptyList())

    /** App info pages are recycled, so a stale page must not be mistaken for the next one. */
    private var lastAppInfoWindowId: Int? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        settingsPackage = resolveSettingsPackage()
        labels = SettingsLabels.load(this, settingsPackage)
        ForceStopController.attach(this)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        runJob?.cancel()
        ForceStopController.detach()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        scope.cancel()
        overlay.hide()
        ForceStopController.detach()
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    fun startRun(targets: List<AppTarget>): Boolean {
        if (runJob?.isActive == true) return false
        runJob = scope.launch { runSequence(targets) }
        return true
    }

    private suspend fun runSequence(targets: List<AppTarget>) {
        lastAppInfoWindowId = null
        val results = mutableListOf<StopResult>()
        overlay.show()

        try {
            targets.forEachIndexed { index, target ->
                ForceStopController.publish(RunState.Running(target, index + 1, targets.size))
                overlay.update(target, index + 1, targets.size)
                results += stopOne(target)
            }
            performGlobalAction(GLOBAL_ACTION_BACK)
            delay(SETTLE_MS)
        } finally {
            // Runs on cancellation too — a stuck full-screen overlay would lock the phone.
            returnToApp()
            overlay.hide(delayMs = OVERLAY_EXIT_MS)
            ForceStopController.publish(RunState.Finished(results))
        }
    }

    private suspend fun stopOne(target: AppTarget): StopResult {
        openAppInfo(target.packageName)
        delay(SETTLE_MS)

        val appInfo = pollSettings(APP_INFO_TIMEOUT_MS) { root ->
            if (root.windowId == lastAppInfoWindowId) return@pollSettings null
            root.findForceStopButton()?.let { button -> root.windowId to button }
        } ?: return StopResult(
            target,
            StopOutcome.FAILED,
            "Couldn't find \u201CForce stop\u201D on the App info screen",
        )

        val (windowId, button) = appInfo
        lastAppInfoWindowId = windowId

        if (!button.isEnabled) return StopResult(target, StopOutcome.ALREADY_STOPPED)

        val clickable = button.clickableSelfOrAncestor()
            ?: return StopResult(target, StopOutcome.FAILED, "\u201CForce stop\u201D was not clickable")
        clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)

        // Most builds ask to confirm in a separate dialog window; some stop straight away.
        pollSettings(DIALOG_TIMEOUT_MS) { root ->
            if (root.windowId == windowId) null else root.findConfirmButton()
        }?.clickableSelfOrAncestor()?.performAction(AccessibilityNodeInfo.ACTION_CLICK)

        val stopped = pollSettings(VERIFY_TIMEOUT_MS) { root ->
            // Settings greys the button out once the process is gone — that is the proof.
            true.takeIf { root.windowId == windowId && root.findForceStopButton()?.isEnabled == false }
        } ?: false

        if (!stopped) Log.w(TAG, "Force stop not confirmed for ${target.packageName}")

        return if (stopped) {
            StopResult(target, StopOutcome.STOPPED)
        } else {
            StopResult(target, StopOutcome.FAILED, "Tapped \u201CForce stop\u201D but the app never stopped")
        }
    }

    /**
     * Matches on the visible label only. "Uninstall" sits on the same row as "Force stop",
     * so clicking by view id or position is never safe here.
     */
    private fun AccessibilityNodeInfo.findForceStopButton(): AccessibilityNodeInfo? =
        findNodesLabelled(labels.forceStop).firstOrNull()

    private fun AccessibilityNodeInfo.findConfirmButton(): AccessibilityNodeInfo? =
        findAccessibilityNodeInfosByViewId(DIALOG_POSITIVE_BUTTON_ID).orEmpty().firstOrNull()
            ?: findNodesLabelled(labels.confirm).firstOrNull { it.clickableSelfOrAncestor() != null }

    private suspend fun <T : Any> pollSettings(
        timeoutMs: Long,
        read: (AccessibilityNodeInfo) -> T?,
    ): T? = withTimeoutOrNull(timeoutMs) {
        var result: T? = null
        while (isActive && result == null) {
            val root = rootInActiveWindow
            if (root != null && root.packageName?.toString() == settingsPackage) {
                result = runCatching { read(root) }.getOrNull()
            }
            if (result == null) delay(POLL_INTERVAL_MS)
        }
        result
    }

    private fun openAppInfo(packageName: String) {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
    }

    private fun returnToApp() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        )
    }

    private fun resolveSettingsPackage(): String {
        val probe = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", packageName, null))
        return packageManager
            .resolveActivity(probe, PackageManager.ResolveInfoFlags.of(0L))
            ?.activityInfo
            ?.packageName
            ?: DEFAULT_SETTINGS_PACKAGE
    }
}
