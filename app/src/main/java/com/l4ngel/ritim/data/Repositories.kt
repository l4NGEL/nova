package com.l4ngel.ritim.data

import android.content.Context
import com.l4ngel.ritim.domain.Habit
import com.l4ngel.ritim.domain.Redemption
import com.l4ngel.ritim.domain.Reward
import com.l4ngel.ritim.domain.SkyObject
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import java.time.DayOfWeek
import java.time.LocalDate

/** Bir alışkanlık ve günlere göre değerleri; ekranların okuduğu birim bu. */
data class HabitWithChecks(val habit: Habit, val checks: Map<LocalDate, Int>)

class HabitRepository(private val dao: HabitDao) {
    /** Kayıt tablosu küçük (yıllarca kullanımda birkaç bin satır); tümünü izleyip gruplamak yeter. */
    val all: Flow<List<HabitWithChecks>> = combine(dao.observeHabits(), dao.observeChecks()) { habits, checks ->
        val byHabit = checks.groupBy { it.habitId }
        habits.map { e ->
            HabitWithChecks(
                e.toDomain(),
                byHabit[e.id].orEmpty().associate { LocalDate.ofEpochDay(it.epochDay) to it.value },
            )
        }
    }

    suspend fun get(id: Long): HabitWithChecks? {
        val e = dao.habit(id) ?: return null
        return HabitWithChecks(e.toDomain(), dao.checksFor(id).associate { LocalDate.ofEpochDay(it.epochDay) to it.value })
    }

    suspend fun allHabits(): List<Habit> = dao.allHabits().map { it.toDomain() }

    /** Yeni kayıtsa listenin sonuna eklenir; dönen değer kimlik. */
    suspend fun save(habit: Habit): Long =
        if (habit.id == 0L) dao.insert(habit.copy(sortOrder = dao.nextSortOrder()).toEntity())
        else { dao.update(habit.toEntity()); habit.id }

    suspend fun value(id: Long, date: LocalDate): Int = dao.value(id, date.toEpochDay()) ?: 0

    suspend fun setValue(id: Long, date: LocalDate, value: Int) {
        if (value <= 0) dao.deleteCheck(id, date.toEpochDay())
        else dao.upsertCheck(CheckEntity(id, date.toEpochDay(), value))
    }

    suspend fun setArchived(id: Long, archived: Boolean) = dao.setArchived(id, archived)
    suspend fun delete(id: Long) = dao.delete(id)
    suspend fun reorder(ids: List<Long>) = dao.reorder(ids)

    val rewards: Flow<List<Reward>> = dao.observeRewards().map { l -> l.map { it.toDomain() } }
    val redemptions: Flow<List<Redemption>> = dao.observeRedemptions().map { l -> l.map { it.toDomain() } }

    suspend fun saveReward(reward: Reward): Long =
        if (reward.id == 0L) dao.upsertReward(reward.copy(sortOrder = dao.nextRewardOrder()).toEntity())
        else { dao.upsertReward(reward.toEntity()); reward.id }

    val skyObjects: Flow<List<SkyObject>> = dao.observeSkyObjects().map { l -> l.mapNotNull { it.toDomain() } }
    suspend fun saveSkyObject(o: SkyObject): Long =
        if (o.id == 0L) dao.upsertSkyObject(o.toEntity()) else { dao.upsertSkyObject(o.toEntity()); o.id }
    suspend fun deleteSkyObject(id: Long) = dao.deleteSkyObject(id)

    suspend fun deleteReward(id: Long) = dao.deleteReward(id)
    suspend fun redeem(r: Redemption): Long = dao.insertRedemption(r.toEntity())
    suspend fun deleteRedemption(id: Long) = dao.deleteRedemption(id)

    suspend fun exportJson(): String =
        Backup.encode(Backup.Content(dao.allHabits(), dao.allChecks(), dao.allRewards(), dao.allRedemptions(), dao.allSkyObjects()))

    suspend fun importJson(json: String): Int {
        val content = Backup.decode(json)
        dao.replaceAll(content)
        return content.habits.size
    }
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Görünüm tercihleri; küçük ve senkron okunması gerekiyor, SharedPreferences yeter. */
class Preferences(context: Context) {
    private val prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
    private val _theme = MutableStateFlow(
        runCatching { ThemeMode.valueOf(prefs.getString(KEY_THEME, null) ?: "") }.getOrDefault(ThemeMode.SYSTEM),
    )
    private val _weekStart = MutableStateFlow(DayOfWeek.of(prefs.getInt(KEY_WEEK_START, DayOfWeek.MONDAY.value)))
    val theme: StateFlow<ThemeMode> = _theme.asStateFlow()
    val weekStart: StateFlow<DayOfWeek> = _weekStart.asStateFlow()
    private val _celebrations = MutableStateFlow(prefs.getBoolean(KEY_CELEBRATE, true))
    /** Konfeti ve puan rozeti; sade arayüz isteyen kapatabilsin. */
    val celebrations: StateFlow<Boolean> = _celebrations.asStateFlow()
    /** Görev ve canavar ödülleri bu günden itibaren sayılır; ilk açılışta yazılır. */
    val gameStart: LocalDate = LocalDate.ofEpochDay(
        prefs.getLong(KEY_GAME_START, -1L).takeIf { it >= 0 }
            ?: LocalDate.now().toEpochDay().also { prefs.edit().putLong(KEY_GAME_START, it).apply() },
    )
    private val _goalReward = MutableStateFlow(prefs.getLong(KEY_GOAL, 0L).takeIf { it > 0 })
    /** Bugün ekranında hedef olarak gösterilen ödül. */
    val goalReward: StateFlow<Long?> = _goalReward.asStateFlow()

    fun setTheme(mode: ThemeMode) { prefs.edit().putString(KEY_THEME, mode.name).apply(); _theme.value = mode }
    fun setWeekStart(day: DayOfWeek) { prefs.edit().putInt(KEY_WEEK_START, day.value).apply(); _weekStart.value = day }
    fun setCelebrations(on: Boolean) { prefs.edit().putBoolean(KEY_CELEBRATE, on).apply(); _celebrations.value = on }
    fun setGoalReward(id: Long?) { prefs.edit().putLong(KEY_GOAL, id ?: 0L).apply(); _goalReward.value = id }

    private companion object {
        const val KEY_THEME = "theme"
        const val KEY_WEEK_START = "week_start"
        const val KEY_CELEBRATE = "celebrate"
        const val KEY_GOAL = "goal_reward"
        const val KEY_GAME_START = "game_start"
    }
}
