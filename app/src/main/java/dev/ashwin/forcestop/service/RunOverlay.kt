package dev.ashwin.forcestop.service

import android.accessibilityservice.AccessibilityService
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import android.view.LayoutInflater
import android.view.SurfaceControl
import android.view.SurfaceControlViewHost
import android.view.WindowManager
import android.widget.ProgressBar
import android.widget.TextView
import android.window.InputTransferToken
import dev.ashwin.forcestop.R

private const val TAG = "ForceStop"
private const val OVERLAY_LAYER = 1

/**
 * Covers the Settings screens a run drives through.
 *
 * Settings flags its activities HIDE_NON_SYSTEM_OVERLAY_WINDOWS as anti-tapjacking, so a
 * SYSTEM_ALERT_WINDOW overlay vanishes the moment App info opens. An accessibility-owned
 * overlay is exempt from that, which is why this goes through SurfaceControl instead.
 */
class RunOverlay(private val service: AccessibilityService) {

    private val handler = Handler(Looper.getMainLooper())
    private var host: SurfaceControlViewHost? = null

    fun show() {
        if (host != null) return
        runCatching { attach() }
            .onFailure { Log.w(TAG, "Could not attach the progress overlay", it) }
    }

    fun update(target: AppTarget, index: Int, total: Int) {
        val root = host?.view ?: return
        root.findViewById<TextView>(R.id.overlay_app).text = target.label
        root.findViewById<TextView>(R.id.overlay_package).text = target.packageName
        root.findViewById<TextView>(R.id.overlay_progress).text =
            root.context.getString(R.string.overlay_progress, index, total)
        root.findViewById<ProgressBar>(R.id.overlay_bar).apply {
            max = total
            setProgress(index, true)
        }
    }

    fun hide(delayMs: Long = 0L) {
        val current = host ?: return
        host = null
        val detach = Runnable { release(current) }
        if (delayMs <= 0L) detach.run() else handler.postDelayed(detach, delayMs)
    }

    private fun attach() {
        val display = service.getSystemService(DisplayManager::class.java)
            ?.getDisplay(Display.DEFAULT_DISPLAY)
        if (display == null) {
            Log.w(TAG, "Overlay: no default display")
            return
        }
        val uiContext = service.createDisplayContext(display)
            .createWindowContext(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, null)
        val bounds = uiContext.getSystemService(WindowManager::class.java)
            .maximumWindowMetrics.bounds

        val root = LayoutInflater.from(uiContext).inflate(R.layout.overlay_run, null)

        val viewHost = SurfaceControlViewHost(uiContext, display, null as InputTransferToken?)
        viewHost.setView(root, bounds.width(), bounds.height())

        val surface = viewHost.surfacePackage?.surfaceControl
        if (surface == null) {
            Log.w(TAG, "Overlay: no surface package")
            viewHost.release()
            return
        }
        service.attachAccessibilityOverlayToDisplay(Display.DEFAULT_DISPLAY, surface)
        // SurfaceControl layers start hidden and attaching only reparents, so show it explicitly.
        SurfaceControl.Transaction().use {
            it.setLayer(surface, OVERLAY_LAYER).setVisibility(surface, true).apply()
        }
        host = viewHost
    }

    private fun release(viewHost: SurfaceControlViewHost) {
        runCatching {
            viewHost.surfacePackage?.surfaceControl?.let { surface ->
                SurfaceControl.Transaction().use { it.reparent(surface, null).apply() }
            }
            viewHost.release()
        }.onFailure { Log.w(TAG, "Could not release the progress overlay", it) }
    }
}
