package com.l4ngel.ritim.domain

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime

private val SHORT_DAY = mapOf(
    DayOfWeek.MONDAY to "Pzt", DayOfWeek.TUESDAY to "Sal", DayOfWeek.WEDNESDAY to "Çar",
    DayOfWeek.THURSDAY to "Per", DayOfWeek.FRIDAY to "Cum", DayOfWeek.SATURDAY to "Cmt", DayOfWeek.SUNDAY to "Paz",
)

fun DayOfWeek.shortTr(): String = SHORT_DAY.getValue(this)

/** Haftanın günleri, kullanıcının seçtiği ilk günden başlayarak. */
fun weekDays(weekStart: DayOfWeek): List<DayOfWeek> = (0L until 7L).map { weekStart.plus(it) }

private val WEEKDAYS = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
private val WEEKEND = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

fun Schedule.label(weekStart: DayOfWeek = DayOfWeek.MONDAY): String = when (type) {
    ScheduleType.DAILY -> "Her gün"
    ScheduleType.TIMES_PER_WEEK -> "Haftada $timesPerWeek kez"
    ScheduleType.WEEKDAYS -> when (days) {
        DayOfWeek.entries.toSet() -> "Her gün"
        WEEKDAYS -> "Hafta içi"
        WEEKEND -> "Hafta sonu"
        else -> weekDays(weekStart).filter { it in days }.joinToString(", ") { it.shortTr() }
    }
}

/** "8 bardak" gibi; birim yoksa yalnız sayı. Tek seferlik hedeflerde boş. */
fun Habit.goalLabel(): String = if (!isCounted) "" else listOf(target.toString(), unit.trim()).filter { it.isNotEmpty() }.joinToString(" ")

/** Hatırlatmanın bir sonraki çalma anı: bugünkü saat geçmediyse bugün, geçtiyse yarın. */
fun nextTrigger(now: LocalDateTime, time: LocalTime): LocalDateTime {
    val today = now.toLocalDate().atTime(time)
    return if (today.isAfter(now)) today else today.plusDays(1)
}

/** Boş ekrandan tek dokunuşla eklenebilen hazır alışkanlıklar. */
data class HabitTemplate(val name: String, val icon: String, val color: Int, val target: Int = 1, val unit: String = "", val schedule: Schedule = Schedule.Daily)

val TEMPLATES = listOf(
    HabitTemplate("Su iç", "water", 0xFF2BA6DE.toInt(), target = 8, unit = "bardak"),
    HabitTemplate("Kitap oku", "book", 0xFFF0743E.toInt(), target = 20, unit = "sayfa"),
    HabitTemplate("Yürüyüş", "walk", 0xFF19A974.toInt()),
    HabitTemplate("Meditasyon", "meditate", 0xFF9B5DE5.toInt()),
    HabitTemplate("Spor", "fitness", 0xFFE5484D.toInt(), schedule = Schedule.perWeek(3)),
    HabitTemplate("Erken uyu", "sleep", 0xFF5B6CF0.toInt()),
)
