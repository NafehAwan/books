package com.tinymotors.game.notify

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.graphics.scale
import com.tinymotors.game.R
import com.tinymotors.game.engine.Cars
import com.tinymotors.game.engine.GameState
import java.util.Calendar

/**
 * Friendly "come back and play" notes, all made on the phone (no server, no data sent anywhere).
 *
 * When the player leaves, three notes are set up: a present is ready (1 day later), what comes
 * after their best car (3 days), and the mystery cars still hiding in the Garage (7 days). They
 * arrive around 5 pm. Opening the game cancels them, and after the third one nothing more is sent
 * until the game is played again. Parents can switch them off in Settings.
 */
object Reminders {
    const val CHANNEL = "reminders"
    const val ACTION_SHOW = "com.tinymotors.game.SHOW_REMINDER"
    const val EXTRA_INDEX = "index"
    private const val PREFS = "reminders"
    private const val KEY_ASKED = "asked_permission"
    private const val NOTIFY_ID = 7

    /** Days after leaving, and the local hour the notes arrive. */
    val DAYS = intArrayOf(1, 3, 7)
    const val HOUR = 17

    data class Note(val title: String, val text: String)

    /** The three notes for a player in [game]'s state. */
    fun notesFor(res: Resources, game: GameState): List<Note> {
        val best = (Cars.MAX_LEVEL downTo Cars.MIN_LEVEL).firstOrNull { game.isUnlocked(it) } ?: Cars.MIN_LEVEL
        val hidden = Cars.MAX_LEVEL - game.unlockedCount
        val gift = Note(res.getString(R.string.remind_gift_title), res.getString(R.string.remind_gift_text))
        val curious = if (best < Cars.MAX_LEVEL) {
            Note(res.getString(R.string.remind_next_title, res.getString(CAR_NAMES[best - 1])), res.getString(R.string.remind_next_text))
        } else {
            Note(res.getString(R.string.remind_best_title), res.getQuantityString(R.plurals.remind_best_text, game.best.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), game.best))
        }
        val miss = Note(
            res.getString(R.string.remind_miss_title),
            if (hidden > 0) res.getQuantityString(R.plurals.remind_miss_hidden, hidden, hidden) else res.getString(R.string.remind_miss_all),
        )
        return listOf(gift, curious, miss)
    }

    /** When note [days] days after [now] should arrive: that day at [HOUR]:00, local time. */
    fun fireTime(now: Long, days: Int): Long = Calendar.getInstance().run {
        timeInMillis = now
        add(Calendar.DAY_OF_YEAR, days)
        set(Calendar.HOUR_OF_DAY, HOUR)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        timeInMillis
    }

    /** Replaces any waiting notes with [notes], counted from now. */
    fun schedule(context: Context, notes: List<Note>) {
        val now = System.currentTimeMillis()
        prefs(context).edit {
            notes.take(DAYS.size).forEachIndexed { i, n ->
                putLong("at_$i", fireTime(now, DAYS[i]))
                putString("title_$i", n.title)
                putString("text_$i", n.text)
            }
        }
        arm(context)
    }

    /** Cancels waiting notes and removes one already showing (the player is back). */
    fun cancel(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        for (i in DAYS.indices) alarms?.cancel(alarmIntent(context, i))
        prefs(context).edit {
            for (i in DAYS.indices) {
                remove("at_$i"); remove("title_$i"); remove("text_$i")
            }
        }
        NotificationManagerCompat.from(context).cancel(NOTIFY_ID)
    }

    /** Sets the alarms for saved notes (also after a restart, which clears alarms). */
    fun arm(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val p = prefs(context)
        val now = System.currentTimeMillis()
        for (i in DAYS.indices) {
            val at = p.getLong("at_$i", 0L)
            if (at <= now) continue
            // Inexact and not waking the phone: the note just needs to arrive some time that evening.
            alarms.set(AlarmManager.RTC, at, alarmIntent(context, i))
        }
    }

    /** Shows saved note [index], if notifications are allowed. */
    fun show(context: Context, index: Int) {
        val p = prefs(context)
        val title = p.getString("title_$index", null) ?: return
        val text = p.getString("text_$index", null) ?: return
        p.edit { remove("at_$index"); remove("title_$index"); remove("text_$index") }
        if (!canNotify(context)) return
        ensureChannel(context)
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            ?: return
        val open = PendingIntent.getActivity(context, 0, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val towy = BitmapFactory.decodeResource(context.resources, R.drawable.ui_towy_wave)?.let {
            val s = 192
            it.scale(s, (it.height * s / it.width.toFloat()).toInt().coerceAtLeast(1))
        }
        val note = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notify)
            .setColor(0xFF3F7FEA.toInt())
            .setLargeIcon(towy)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFY_ID, note)
        } catch (_: SecurityException) {
            // permission was taken away in between
        }
    }

    fun canNotify(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

    /** Android 13+ needs the player's OK before showing notes; we ask once, at a calm moment. */
    fun needsPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

    fun askedBefore(context: Context): Boolean = prefs(context).getBoolean(KEY_ASKED, false)
    fun markAsked(context: Context) = prefs(context).edit { putBoolean(KEY_ASKED, true) }

    private fun ensureChannel(context: Context) {
        val channel = NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_DEFAULT)
            .setName(context.getString(R.string.remind_channel))
            .setDescription(context.getString(R.string.remind_channel_sub))
            .build()
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    private fun alarmIntent(context: Context, index: Int): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).setAction(ACTION_SHOW).putExtra(EXTRA_INDEX, index)
        return PendingIntent.getBroadcast(context, index, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val CAR_NAMES = intArrayOf(
        R.string.car_01, R.string.car_02, R.string.car_03, R.string.car_04, R.string.car_05,
        R.string.car_06, R.string.car_07, R.string.car_08, R.string.car_09, R.string.car_10,
        R.string.car_11,
    )
}

/** Shows a note when its alarm goes off, and sets the alarms again after a restart or an update. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Reminders.ACTION_SHOW -> Reminders.show(context, intent.getIntExtra(Reminders.EXTRA_INDEX, -1))
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> Reminders.arm(context)
        }
    }
}
