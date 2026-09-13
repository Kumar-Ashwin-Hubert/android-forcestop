package dev.ashwin.forcestop.service

import android.content.Context

/**
 * The button labels are read out of the Settings app's own resources, so the automation
 * keeps working when the device is not in English. English values are only a fallback.
 */
data class SettingsLabels(
    val forceStop: List<String>,
    val confirm: List<String>,
) {
    companion object {
        fun load(context: Context, settingsPackage: String): SettingsLabels {
            val res = runCatching {
                context.packageManager.getResourcesForApplication(settingsPackage)
            }.getOrNull()

            fun string(name: String): String? = res
                ?.getIdentifier(name, "string", settingsPackage)
                ?.takeIf { it != 0 }
                ?.let { id -> runCatching { res.getString(id) }.getOrNull() }
                ?.trim()
                ?.takeIf { it.isNotEmpty() }

            val forceStop = listOfNotNull(string("force_stop")) + FORCE_STOP_FALLBACKS
            val confirm = listOfNotNull(
                string("dlg_ok"),
                string("force_stop"),
                context.getString(android.R.string.ok),
            ) + CONFIRM_FALLBACKS

            return SettingsLabels(
                forceStop = forceStop.distinct(),
                confirm = confirm.distinct(),
            )
        }

        private val FORCE_STOP_FALLBACKS = listOf("Force stop", "Force Stop")
        private val CONFIRM_FALLBACKS = listOf("OK", "Force stop")
    }
}
