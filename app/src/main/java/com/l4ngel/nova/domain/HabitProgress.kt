package com.l4ngel.nova.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.max
import kotlin.math.min

/**
 * Bir alışkanlığın kayıtları üstünde seri, oran ve "bugün yapılmalı mı" hesapları.
 * Hepsi saf fonksiyon; bugün dışarıdan verilir ki testler saate bağlı olmasın.
 *
 * Kurallar:
 * - Bugün henüz yapılmadıysa seri bozulmaz; gün bitmeden kaçırılmış sayılmaz.
 * - Plan dışı bir günde yapılan kayıt seriyi uzatmaz ama bozmaz da.
 * - Haftada X kez türünde seri hafta sayısıdır; içinde bulunulan hafta hedefe ulaşmadıysa sayılmaz, bozmaz.
 */
class HabitProgress(
    val habit: Habit,
    val checks: Map<LocalDate, Int>,
    val weekStart: DayOfWeek = DayOfWeek.MONDAY,
) {
    /** Geçmişe dönük işaretleme oluşturma tarihinden önceye de gidebilir; en eskisinden başla. */
    val start: LocalDate = checks.keys.minOrNull()?.let { minOf(it, habit.createdOn) } ?: habit.createdOn

    private val weekly = habit.schedule.type == ScheduleType.TIMES_PER_WEEK
    private val weekTarget = habit.schedule.timesPerWeek.coerceIn(1, 7)

    fun value(date: LocalDate): Int = checks[date] ?: 0
    fun isDone(date: LocalDate): Boolean = value(date) >= habit.target
    fun fraction(date: LocalDate): Float = (value(date).toFloat() / habit.target).coerceIn(0f, 1f)

    fun weekOf(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(weekStart))

    /** O haftada tamamlanan gün sayısı; [before] verilirse yalnız ondan önceki günler. */
    fun doneInWeek(date: LocalDate, before: LocalDate? = null): Int {
        val w = weekOf(date)
        return (0L until 7L).count { i ->
            val d = w.plusDays(i)
            (before == null || d.isBefore(before)) && isDone(d)
        }
    }

    fun weekGoalMet(date: LocalDate): Boolean = doneInWeek(date) >= weekTarget

    /**
     * O gün listede "yapılacak" sayılır mı. Haftada X kez türünde hafta hedefi o günden önce
     * dolduysa artık beklenmez; o gün yapıldıysa yine sayılır ki günün oranı düşmesin.
     */
    fun isDue(date: LocalDate): Boolean {
        if (date.isBefore(start)) return false
        if (!weekly) return habit.schedule.isPlanned(date)
        return isDone(date) || doneInWeek(date, before = date) < weekTarget
    }

    val streakUnit: String get() = if (weekly) "hafta" else "gün"

    fun currentStreak(today: LocalDate): Int = if (weekly) currentWeekStreak(today) else currentDayStreak(today)
    fun bestStreak(today: LocalDate): Int = if (weekly) bestWeekStreak(today) else bestDayStreak(today)

    private fun currentDayStreak(today: LocalDate): Int {
        var d = if (isDone(today)) today else today.minusDays(1)
        var n = 0
        while (!d.isBefore(start)) {
            if (habit.schedule.isPlanned(d)) {
                if (isDone(d)) n++ else break
            }
            d = d.minusDays(1)
        }
        return n
    }

    private fun bestDayStreak(today: LocalDate): Int {
        var best = 0
        var run = 0
        var d = start
        while (!d.isAfter(today)) {
            if (habit.schedule.isPlanned(d)) {
                if (isDone(d)) { run++; best = max(best, run) } else if (d != today) run = 0
            }
            d = d.plusDays(1)
        }
        return best
    }

    private fun currentWeekStreak(today: LocalDate): Int {
        var w = weekOf(today)
        var n = 0
        if (weekGoalMet(w)) n++
        w = w.minusWeeks(1)
        val first = weekOf(start)
        while (!w.isBefore(first)) {
            if (weekGoalMet(w)) n++ else break
            w = w.minusWeeks(1)
        }
        return n
    }

    private fun bestWeekStreak(today: LocalDate): Int {
        val current = weekOf(today)
        var best = 0
        var run = 0
        var w = weekOf(start)
        while (!w.isAfter(current)) {
            if (weekGoalMet(w)) { run++; best = max(best, run) } else if (w != current) run = 0
            w = w.plusWeeks(1)
        }
        return best
    }

    /**
     * Son [days] gündeki başarı oranı (0..1). Bugün yapılmadıysa henüz sayılmaz.
     * Hiç beklenen gün yoksa null: "%0" göstermek yeni alışkanlığa haksızlık olur.
     */
    fun completionRate(days: Int, today: LocalDate): Float? {
        val from = maxOf(start, today.minusDays(days - 1L))
        if (from.isAfter(today)) return null
        if (weekly) {
            var score = 0f
            var weeks = 0
            var w = weekOf(from)
            val current = weekOf(today)
            while (!w.isAfter(current)) {
                val done = doneInWeek(w)
                if (w != current || done >= weekTarget) {
                    score += min(done, weekTarget).toFloat() / weekTarget
                    weeks++
                }
                w = w.plusWeeks(1)
            }
            return if (weeks == 0) null else score / weeks
        }
        var due = 0
        var done = 0
        var d = from
        while (!d.isAfter(today)) {
            if (habit.schedule.isPlanned(d) && (d != today || isDone(d))) {
                due++
                if (isDone(d)) done++
            }
            d = d.plusDays(1)
        }
        return if (due == 0) null else done.toFloat() / due
    }

    fun totalDone(): Int = checks.count { it.value >= habit.target }
}

/** Bir günün özeti: o gün beklenen alışkanlıklardan kaçı tamamlandı. */
data class DayScore(val done: Int, val due: Int) {
    val fraction: Float get() = if (due == 0) 0f else done.toFloat() / due
}

fun dayScore(progress: List<HabitProgress>, date: LocalDate): DayScore {
    val due = progress.filter { it.isDue(date) }
    return DayScore(due.count { it.isDone(date) }, due.size)
}

/** Son [days] gündeki tamamlanan kayıtların haftanın günlerine dağılımı. */
fun weekdayDistribution(progress: List<HabitProgress>, days: Int, today: LocalDate): Map<DayOfWeek, Int> {
    val from = today.minusDays(days - 1L)
    val counts = DayOfWeek.entries.associateWith { 0 }.toMutableMap()
    for (p in progress) {
        for ((date, _) in p.checks) {
            if (!date.isBefore(from) && !date.isAfter(today) && p.isDone(date)) {
                counts[date.dayOfWeek] = counts.getValue(date.dayOfWeek) + 1
            }
        }
    }
    return counts
}
