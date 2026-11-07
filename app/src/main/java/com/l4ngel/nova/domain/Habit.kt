package com.l4ngel.nova.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

enum class ScheduleType { DAILY, WEEKDAYS, TIMES_PER_WEEK }

/**
 * Ne zaman yapılacağı. [days] yalnız WEEKDAYS, [timesPerWeek] yalnız TIMES_PER_WEEK için anlamlı;
 * düzenleme ekranında tür değişince öbür alanın değeri kaybolmasın diye ikisi de hep tutuluyor.
 */
data class Schedule(
    val type: ScheduleType = ScheduleType.DAILY,
    val days: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
    val timesPerWeek: Int = 3,
) {
    /** Takvime bağlı günlerde o gün plandaysa true; haftada X kez türünde her gün açıktır. */
    fun isPlanned(date: LocalDate): Boolean = when (type) {
        ScheduleType.DAILY, ScheduleType.TIMES_PER_WEEK -> true
        ScheduleType.WEEKDAYS -> date.dayOfWeek in days
    }

    companion object {
        val Daily = Schedule()
        fun on(vararg days: DayOfWeek) = Schedule(ScheduleType.WEEKDAYS, days.toSet())
        fun perWeek(times: Int) = Schedule(ScheduleType.TIMES_PER_WEEK, timesPerWeek = times)

        /** Pazartesi bit 0 ... Pazar bit 6. */
        fun maskOf(days: Set<DayOfWeek>): Int = days.fold(0) { m, d -> m or (1 shl (d.value - 1)) }
        fun daysOf(mask: Int): Set<DayOfWeek> = DayOfWeek.entries.filter { mask and (1 shl (it.value - 1)) != 0 }.toSet()
    }
}

/**
 * Bir alışkanlık. [target] 1 ise "yaptım / yapmadım", büyükse sayılı hedef (ör. 8 bardak su).
 * Günlük kayıt değeri hedefe ulaşınca o gün tamamlanmış sayılır. [points] her tamamlanan günün
 * kazandırdığı puan; kullanıcı zorluğa göre seçer.
 */
data class Habit(
    val id: Long = 0,
    val name: String,
    val icon: String,
    val color: Int,
    val schedule: Schedule = Schedule.Daily,
    val target: Int = 1,
    val unit: String = "",
    val reminder: LocalTime? = null,
    val createdOn: LocalDate,
    val archived: Boolean = false,
    val sortOrder: Int = 0,
    val note: String = "",
    val points: Int = Difficulty.MEDIUM.points,
) {
    val isCounted: Boolean get() = target > 1
}
