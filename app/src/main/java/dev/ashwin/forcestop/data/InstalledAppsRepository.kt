package dev.ashwin.forcestop.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val ICON_PX = 96

class InstalledAppsRepository(private val context: Context) {

    /** Apps the user can actually launch — the only ones worth force stopping. */
    suspend fun launchableApps(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

        pm.queryIntentActivities(launcherIntent, PackageManager.ResolveInfoFlags.of(0L))
            .asSequence()
            .map { it.activityInfo.applicationInfo }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .map { info ->
                InstalledApp(
                    packageName = info.packageName,
                    label = pm.getApplicationLabel(info).toString(),
                    icon = runCatching {
                        pm.getApplicationIcon(info).toBitmap(ICON_PX, ICON_PX).asImageBitmap()
                    }.getOrNull(),
                    isSystem = info.flags and
                        (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0,
                )
            }
            .sortedBy { it.label.lowercase() }
            .toList()
    }
}
