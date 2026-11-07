package com.l4ngel.nova

import android.app.Application
import com.l4ngel.nova.data.AppDatabase
import com.l4ngel.nova.data.HabitRepository
import com.l4ngel.nova.data.Preferences
import com.l4ngel.nova.notify.HabitReminders
import com.l4ngel.nova.widget.NovaWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
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
        // Uygulamada, bildirimde ya da widget'ta ne değişirse değişsin ana ekrandaki widget da güncellensin.
        scope.launch {
            combine(repository.all, repository.redemptions, repository.skyObjects) { _, _, _ -> }
                .drop(1)
                .debounce(300)
                .collect { NovaWidget.refresh(this@NovaApp) }
        }
    }
}
