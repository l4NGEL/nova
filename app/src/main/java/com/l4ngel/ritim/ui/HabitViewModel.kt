package com.l4ngel.ritim.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.l4ngel.ritim.RitimApp
import com.l4ngel.ritim.data.HabitWithChecks
import com.l4ngel.ritim.data.ThemeMode
import com.l4ngel.ritim.domain.Badge
import com.l4ngel.ritim.domain.Boss
import com.l4ngel.ritim.domain.Quest
import com.l4ngel.ritim.domain.dailyQuests
import com.l4ngel.ritim.domain.weeklyBoss
import com.l4ngel.ritim.domain.Difficulty
import com.l4ngel.ritim.domain.Habit
import com.l4ngel.ritim.domain.HabitProgress
import com.l4ngel.ritim.domain.Level
import com.l4ngel.ritim.domain.PointKind
import com.l4ngel.ritim.domain.Redemption
import com.l4ngel.ritim.domain.Reward
import com.l4ngel.ritim.domain.SkyKind
import com.l4ngel.ritim.domain.SkyLayout
import com.l4ngel.ritim.domain.SkyObject
import com.l4ngel.ritim.domain.skyLayout
import com.l4ngel.ritim.domain.Wallet
import com.l4ngel.ritim.domain.badges
import com.l4ngel.ritim.domain.pointEvents
import com.l4ngel.ritim.domain.HabitTemplate
import com.l4ngel.ritim.domain.Schedule
import com.l4ngel.ritim.domain.ScheduleType
import com.l4ngel.ritim.notify.HabitReminders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class HabitViewModel(app: Application) : AndroidViewModel(app) {
    private val ritim = app as RitimApp
    private val repo = ritim.repository
    private val prefs = ritim.preferences

    /** Gece yarısı geçince ekranlar kendiliğinden yeni güne geçsin. */
    val today: StateFlow<LocalDate> = flow {
        while (true) { emit(LocalDate.now()); delay(30_000) }
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Eagerly, LocalDate.now())

    val weekStart: StateFlow<DayOfWeek> = prefs.weekStart
    val theme: StateFlow<ThemeMode> = prefs.theme

    /** null = henüz yüklenmedi; boş liste ile ayırt edilsin ki ilk açılışta boş ekran yanıp sönmesin. */
    val all: StateFlow<List<HabitWithChecks>?> = repo.all.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val progress: StateFlow<List<HabitProgress>?> = combine(all, weekStart) { list, ws ->
        list?.filter { !it.habit.archived }?.map { HabitProgress(it.habit, it.checks, ws) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Puanlar arşivdekiler dahil tüm alışkanlıklardan; arşivlemek kazanılanı silmesin. */
    val rewards: StateFlow<RewardState?> = combine(
        combine(all, weekStart) { list, ws -> list?.map { HabitProgress(it.habit, it.checks, ws) } },
        repo.rewards,
        combine(repo.redemptions, repo.skyObjects) { r, s -> r to s },
        prefs.goalReward,
        today,
    ) { progress, rewards, (redemptions, objects), goal, day ->
        progress ?: return@combine null
        val wallet = Wallet(pointEvents(progress, day, prefs.gameStart), redemptions, objects)
        val active = progress.filter { !it.habit.archived }
        RewardState(
            day, wallet, rewards, badges(progress, wallet, day), rewards.firstOrNull { it.id == goal }, skyLayout(progress, day), objects,
            quests = dailyQuests(active, day),
            boss = active.firstOrNull()?.let { weeklyBoss(active, it.weekOf(day), day) },
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val celebrationsOn: StateFlow<Boolean> = prefs.celebrations

    private val _celebrations = MutableSharedFlow<Celebration>(extraBufferCapacity = 8)
    val celebrations: SharedFlow<Celebration> = _celebrations

    /** Yedek geri yüklenirken binlerce puanlık sıçrama kutlanmasın. */
    @Volatile private var quietUntil = 0L

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<UiMessage> = _messages
    private fun say(text: String, undo: (() -> Unit)? = null) { _messages.tryEmit(UiMessage(text, undo)) }

    init {
        // Kutlamalar durum farkından çıkar: puan arttıysa rozet, seviye değiştiyse pencere, bugün kusursuzlaştıysa konfeti.
        viewModelScope.launch {
            var prev: RewardState? = null
            rewards.filterNotNull().collect { s ->
                val p = prev
                prev = s
                if (p == null || p.today != s.today || System.currentTimeMillis() < quietUntil) return@collect
                val gained = s.wallet.earned - p.wallet.earned
                if (gained <= 0) return@collect
                _celebrations.tryEmit(Celebration.Points(gained))
                if (s.wallet.level.number > p.wallet.level.number) _celebrations.tryEmit(Celebration.LevelUp(s.wallet.level))
                if (s.perfectToday && !p.perfectToday) _celebrations.tryEmit(Celebration.PerfectDay)
                val boss = s.boss
                if (boss != null && boss.defeated && p.boss?.defeated == false) _celebrations.tryEmit(Celebration.BossDefeated(boss))
            }
        }
    }

    fun setValue(habit: Habit, date: LocalDate, value: Int) = viewModelScope.launch {
        val v = value.coerceIn(0, 9999)
        repo.setValue(habit.id, date, v)
        if (date == today.value && v >= habit.target) HabitReminders.dismiss(ritim, habit.id)
    }

    fun toggle(habit: Habit, date: LocalDate, current: Int) =
        setValue(habit, date, if (current >= habit.target) 0 else habit.target)

    fun increment(habit: Habit, date: LocalDate, current: Int) = setValue(habit, date, current + 1)

    fun addTemplate(t: HabitTemplate) = viewModelScope.launch {
        repo.save(Habit(name = t.name, icon = t.icon, color = t.color, target = t.target, unit = t.unit, schedule = t.schedule, createdOn = today.value))
    }

    fun setArchived(habit: Habit, archived: Boolean): Job = viewModelScope.launch {
        repo.setArchived(habit.id, archived)
        HabitReminders.schedule(ritim, habit.copy(archived = archived))
        if (archived) HabitReminders.dismiss(ritim, habit.id)
        if (archived) say("“${habit.name}” arşivlendi") { setArchived(habit, false) }
        else say("“${habit.name}” etkin listeye döndü")
    }

    fun delete(habit: Habit) = viewModelScope.launch {
        HabitReminders.cancel(ritim, habit.id)
        repo.delete(habit.id)
        say("“${habit.name}” silindi")
    }

    fun reorder(ids: List<Long>) = viewModelScope.launch { repo.reorder(ids) }

    fun saveReward(reward: Reward) = viewModelScope.launch { repo.saveReward(reward) }

    fun deleteReward(reward: Reward) = viewModelScope.launch {
        repo.deleteReward(reward.id)
        if (prefs.goalReward.value == reward.id) prefs.setGoalReward(null)
        say("“${reward.name}” silindi") { saveReward(reward) }
    }

    fun setGoal(reward: Reward?) = prefs.setGoalReward(reward?.id)

    fun redeem(reward: Reward) = viewModelScope.launch {
        val id = repo.redeem(
            Redemption(rewardId = reward.id, name = reward.name, emoji = reward.emoji, cost = reward.cost, date = today.value, at = System.currentTimeMillis()),
        )
        if (!reward.repeatable && prefs.goalReward.value == reward.id) prefs.setGoalReward(null)
        _celebrations.tryEmit(Celebration.Redeemed(reward))
        say("${reward.emoji} ${reward.name} senin, tadını çıkar!") { viewModelScope.launch { repo.deleteRedemption(id) } }
    }

    /** Yerleştirilmeyi bekleyen gök cismi: yeni alınan ya da taşınan. Puan yerleştirilince düşer. */
    private val _placing = MutableStateFlow<SkyObject?>(null)
    val placing: StateFlow<SkyObject?> = _placing.asStateFlow()

    fun buySky(kind: SkyKind, color: Int, name: String) {
        _placing.value = SkyObject(kind = kind, color = color, name = name.trim().ifEmpty { kind.title }, x = 0.5f, y = 0.5f, cost = kind.cost, date = today.value)
    }

    fun move(o: SkyObject) { _placing.value = o }
    fun cancelPlacing() { _placing.value = null }

    fun place(x: Float, y: Float) = viewModelScope.launch {
        val o = _placing.value ?: return@launch
        _placing.value = null
        val id = repo.saveSkyObject(o.copy(x = x, y = y))
        if (o.id == 0L) {
            _celebrations.tryEmit(Celebration.SkyAdded(o))
            say("“${o.name}” evrenine eklendi") { viewModelScope.launch { repo.deleteSkyObject(id) } }
        }
    }

    fun removeSky(o: SkyObject) = viewModelScope.launch {
        repo.deleteSkyObject(o.id)
        say("“${o.name}” kaldırıldı, ${o.cost} puan iade edildi") { viewModelScope.launch { repo.saveSkyObject(o) } }
    }

    fun setCelebrations(on: Boolean) = prefs.setCelebrations(on)
    fun setTheme(mode: ThemeMode) = prefs.setTheme(mode)
    fun setWeekStart(day: DayOfWeek) = prefs.setWeekStart(day)

    fun export(uri: Uri) = viewModelScope.launch {
        val result = runCatching {
            val json = repo.exportJson()
            withContext(Dispatchers.IO) {
                ritim.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(json.toByteArray()) }
            }
        }
        say(if (result.isSuccess) "Yedek kaydedildi" else "Yedek kaydedilemedi")
    }

    fun import(uri: Uri) = viewModelScope.launch {
        val result = runCatching {
            val json = withContext(Dispatchers.IO) {
                ritim.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
            }
            quietUntil = System.currentTimeMillis() + 3_000
            repo.importJson(json).also { HabitReminders.rescheduleAll(ritim) }
        }
        say(
            result.fold(
                onSuccess = { "$it alışkanlık geri yüklendi" },
                onFailure = { (it as? IllegalArgumentException)?.message ?: "Yedek okunamadı" },
            ),
        )
    }
}

/** Ekranın altındaki kısa bilgi; [undo] varsa "Geri al" düğmesiyle gösterilir. */
data class UiMessage(val text: String, val undo: (() -> Unit)? = null)

/** Ödül ekranının ve başlıktaki puan rozetinin okuduğu durum. */
data class RewardState(
    val today: LocalDate,
    val wallet: Wallet,
    val rewards: List<Reward>,
    val badges: List<Badge>,
    val goal: Reward?,
    val sky: SkyLayout,
    val objects: List<SkyObject>,
    val quests: List<Quest> = emptyList(),
    val boss: Boss? = null,
) {
    val perfectToday: Boolean get() = wallet.events.any { it.kind == PointKind.PERFECT_DAY && it.date == today }

    /** Bir kez alınabilen ödül alındıysa rafta "alındı" görünür. */
    fun isClaimed(r: Reward): Boolean = !r.repeatable && wallet.timesRedeemed(r.id) > 0
}

sealed interface Celebration {
    data class Points(val amount: Int) : Celebration
    data class LevelUp(val level: Level) : Celebration
    data object PerfectDay : Celebration
    data class Redeemed(val reward: Reward) : Celebration
    data class SkyAdded(val obj: SkyObject) : Celebration
    data class BossDefeated(val boss: Boss) : Celebration
}

/** Düzenleme formunun durumu; döndürmede kaybolmasın diye ayrı ViewModel'de. */
data class HabitForm(
    val id: Long = 0,
    val name: String = "",
    val icon: String = "star",
    val color: Int = HABIT_COLORS.first(),
    val scheduleType: ScheduleType = ScheduleType.DAILY,
    val days: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
    val timesPerWeek: Int = 3,
    val counted: Boolean = false,
    val target: Int = 5,
    val unit: String = "",
    val reminder: LocalTime? = null,
    val note: String = "",
    val createdOn: LocalDate? = null,
    val archived: Boolean = false,
    val sortOrder: Int = 0,
    val points: Int = Difficulty.MEDIUM.points,
) {
    val isValid: Boolean
        get() = name.isNotBlank() && (scheduleType != ScheduleType.WEEKDAYS || days.isNotEmpty()) && (!counted || target >= 2)

    fun toHabit(today: LocalDate) = Habit(
        id = id,
        name = name.trim(),
        icon = icon,
        color = color,
        schedule = Schedule(scheduleType, days, timesPerWeek),
        target = if (counted) target.coerceAtLeast(2) else 1,
        unit = if (counted) unit.trim() else "",
        reminder = reminder,
        createdOn = createdOn ?: today,
        archived = archived,
        sortOrder = sortOrder,
        note = note.trim(),
        points = points,
    )

    companion object {
        fun of(h: Habit) = HabitForm(
            id = h.id, name = h.name, icon = h.icon, color = h.color,
            scheduleType = h.schedule.type, days = h.schedule.days, timesPerWeek = h.schedule.timesPerWeek,
            counted = h.isCounted, target = if (h.isCounted) h.target else 5, unit = h.unit,
            reminder = h.reminder, note = h.note, createdOn = h.createdOn, archived = h.archived, sortOrder = h.sortOrder,
            points = h.points,
        )
    }
}

class EditHabitViewModel(app: Application, handle: SavedStateHandle) : AndroidViewModel(app) {
    private val ritim = app as RitimApp
    private val habitId: Long = handle.get<Long>("id")?.takeIf { it > 0 } ?: 0L
    val isNew = habitId == 0L

    private val _form = MutableStateFlow(if (isNew) HabitForm(color = HABIT_COLORS.random()) else null)
    val form: StateFlow<HabitForm?> = _form.asStateFlow()

    init {
        if (!isNew) viewModelScope.launch {
            _form.value = ritim.repository.get(habitId)?.habit?.let(HabitForm::of) ?: HabitForm()
        }
    }

    fun update(change: (HabitForm) -> HabitForm) = _form.update { it?.let(change) }

    fun save(onSaved: () -> Unit) = viewModelScope.launch {
        val f = _form.value ?: return@launch
        if (!f.isValid) return@launch
        val habit = f.toHabit(LocalDate.now())
        val id = ritim.repository.save(habit)
        HabitReminders.schedule(ritim, habit.copy(id = id))
        onSaved()
    }
}
