package com.l4ngel.nova.notify

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.l4ngel.nova.MainActivity
import com.l4ngel.nova.R
import com.l4ngel.nova.NovaApp
import com.l4ngel.nova.domain.Habit
import com.l4ngel.nova.domain.HabitProgress
import com.l4ngel.nova.domain.nextTrigger
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Her alışkanlık için günde bir alarm. Alarm çalınca o gün plandaysa ve henüz yapılmadıysa
 * bildirim çıkar, sonra ertesi güne yeniden kurulur. Tam zamanlı alarm izni istemiyoruz:
 * birkaç dakikalık pencere hatırlatma için yeterli ve kullanıcıya ek izin ekranı göstermez.
 */
object HabitReminders {
    const val CHANNEL = "hatirlatma"
    const val EXTRA_HABIT = "habit_id"
    private const val ACTION_FIRE = "com.l4ngel.nova.REMINDER"
    private const val ACTION_DONE = "com.l4ngel.nova.REMINDER_DONE"
    private const val WINDOW_MS = 5 * 60 * 1000L

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL, "Alışkanlık hatırlatmaları", NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = "Belirlediğin saatte, o gün henüz yapılmamış alışkanlıklar için" }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun alarmIntent(context: Context, id: Long): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).setAction(ACTION_FIRE).putExtra(EXTRA_HABIT, id)
        return PendingIntent.getBroadcast(context, id.toInt(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /** Hatırlatması yoksa ya da arşivdeyse mevcut alarmı kaldırır. */
    fun schedule(context: Context, habit: Habit) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = alarmIntent(context, habit.id)
        val time = habit.reminder
        if (time == null || habit.archived) { am.cancel(pi); return }
        val at = nextTrigger(LocalDateTime.now(), time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        am.setWindow(AlarmManager.RTC_WAKEUP, at, WINDOW_MS, pi)
    }

    fun cancel(context: Context, id: Long) {
        context.getSystemService(AlarmManager::class.java).cancel(alarmIntent(context, id))
        NotificationManagerCompat.from(context).cancel(id.toInt())
    }

    suspend fun rescheduleAll(context: Context) {
        val app = context.applicationContext as NovaApp
        for (h in app.repository.allHabits()) schedule(context, h)
    }

    /** Uygulama içinden işaretlenince bekleyen bildirim de kalksın. */
    fun dismiss(context: Context, id: Long) = NotificationManagerCompat.from(context).cancel(id.toInt())

    internal suspend fun onAlarm(context: Context, id: Long) {
        val app = context.applicationContext as NovaApp
        val item = app.repository.get(id) ?: return
        schedule(context, item.habit)
        notifyIfPending(context, id)
    }

    /** Bildirimdeki düğme: tek seferlikse tamamlar, sayılıysa bir artırır. */
    internal suspend fun onDone(context: Context, id: Long) {
        val app = context.applicationContext as NovaApp
        val item = app.repository.get(id) ?: return
        val today = LocalDate.now()
        val current = item.checks[today] ?: 0
        val next = if (item.habit.isCounted) current + 1 else item.habit.target
        app.repository.setValue(id, today, next)
        notifyIfPending(context, id)
    }

    private suspend fun notifyIfPending(context: Context, id: Long) {
        val app = context.applicationContext as NovaApp
        val item = app.repository.get(id) ?: return
        val habit = item.habit
        val today = LocalDate.now()
        val progress = HabitProgress(habit, item.checks, app.preferences.weekStart.value)
        val nm = NotificationManagerCompat.from(context)
        if (habit.archived || !progress.isDue(today) || progress.isDone(today)) { nm.cancel(id.toInt()); return }
        if (!canNotify(context)) return

        val value = progress.value(today)
        val body = if (habit.isCounted) {
            val left = habit.target - value
            "$value / ${habit.target} ${habit.unit}".trim() + " · hedefe $left kaldı"
        } else {
            val streak = progress.currentStreak(today)
            val unit = if (progress.streakUnit == "gün") "günlük" else "haftalık"
            if (streak > 0) "$streak $unit serini koru · +${habit.points} puan" else "Bugün tamamla, +${habit.points} puan kazan"
        }
        val open = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_HABIT, id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val openPi = PendingIntent.getActivity(context, id.toInt(), open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val done = Intent(context, ReminderReceiver::class.java).setAction(ACTION_DONE).putExtra(EXTRA_HABIT, id)
        val donePi = PendingIntent.getBroadcast(context, -id.toInt() - 1, done, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(habit.name)
            .setContentText(body)
            .setColor(habit.color)
            .setContentIntent(openPi)
            .addAction(0, if (habit.isCounted) "+1 ${habit.unit}".trim() else "Tamamlandı", donePi)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .build()
        try { nm.notify(id.toInt(), n) } catch (_: SecurityException) { }
    }

    internal fun isFire(action: String?) = action == ACTION_FIRE
    internal fun isDone(action: String?) = action == ACTION_DONE
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(HabitReminders.EXTRA_HABIT, -1L).takeIf { it > 0 } ?: return
        val app = context.applicationContext as NovaApp
        val pending = goAsync()
        app.scope.launch {
            try {
                when {
                    HabitReminders.isFire(intent.action) -> HabitReminders.onAlarm(context, id)
                    HabitReminders.isDone(intent.action) -> HabitReminders.onDone(context, id)
                }
            } finally {
                pending.finish()
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as NovaApp
        val pending = goAsync()
        app.scope.launch {
            try { HabitReminders.rescheduleAll(context) } finally { pending.finish() }
        }
    }
}
