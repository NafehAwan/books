package com.tinymotors.game.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import com.tinymotors.game.notify.Reminders

/**
 * Asks for permission to show the play reminders (Android 13+ needs it). With [openSettings], when
 * Android will not show its question any more (the player said no before), the app's notification
 * settings open instead, so a parent can switch them on there.
 */
@Composable
fun rememberReminderAsk(openSettings: Boolean = false): () -> Unit {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    return remember(context, activity, launcher, openSettings) {
        ask@{
            val blocked = when {
                Build.VERSION.SDK_INT >= 33 && Reminders.needsPermission(context) -> {
                    val askedAndRefused = Reminders.askedBefore(context) && activity != null &&
                        !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)
                    if (!askedAndRefused || !openSettings) {
                        Reminders.markAsked(context)
                        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        return@ask
                    }
                    true
                }
                else -> !Reminders.canNotify(context)
            }
            if (blocked && openSettings) {
                val intent = if (Build.VERSION.SDK_INT >= 26) {
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                } else {
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(intent) }
            }
        }
    }
}
