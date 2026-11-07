package com.l4ngel.nova

import android.app.Application
import com.l4ngel.nova.data.AppDatabase
import com.l4ngel.nova.data.HabitRepository
import com.l4ngel.nova.data.Preferences
import com.l4ngel.nova.notify.HabitReminders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NovaApp : Application() {
    private val db by lazy { AppDatabase.build(this) }
    val repository by lazy { HabitRepository(db.habits()) }
    val preferences by lazy { Preferences(this) }

    /** Ekrandan bağımsız işler (alarm kurma, bildirim eylemleri) için uygulama ömrü kadar yaşayan kapsam. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        HabitReminders.createChannel(this)
        // Alarmlar uygulama güncellenince ya da zorla durdurulunca silinebiliyor; her açılışta yeniden kur.
        scope.launch { HabitReminders.rescheduleAll(this@NovaApp) }
    }
}
