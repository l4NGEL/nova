package com.l4ngel.nova.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.THURSDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class LabelsTest {
    @Test
    fun `siklik etiketleri`() {
        assertEquals("Her gün", Schedule.Daily.label())
        assertEquals("Hafta içi", Schedule.on(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY).label())
        assertEquals("Hafta sonu", Schedule.on(SATURDAY, SUNDAY).label())
        assertEquals("Pzt, Çar, Paz", Schedule.on(SUNDAY, WEDNESDAY, MONDAY).label())
        assertEquals("Paz, Pzt, Çar", Schedule.on(SUNDAY, WEDNESDAY, MONDAY).label(SUNDAY))
        assertEquals("Haftada 3 kez", Schedule.perWeek(3).label())
    }

    @Test
    fun `gun maskesi gidip gelir`() {
        val days = setOf(MONDAY, FRIDAY, SUNDAY)
        val mask = Schedule.maskOf(days)
        assertEquals(0b1010001, mask)
        assertEquals(days, Schedule.daysOf(mask))
    }

    @Test
    fun `hedef etiketi`() {
        val h = Habit(name = "Su", icon = "water", color = 0, target = 8, unit = "bardak", createdOn = LocalDate.now())
        assertEquals("8 bardak", h.goalLabel())
        assertEquals("", h.copy(target = 1).goalLabel())
        assertEquals("8", h.copy(unit = " ").goalLabel())
    }

    @Test
    fun `hatirlatma saati gectiyse yarin`() {
        val now = LocalDateTime.of(2026, 11, 4, 21, 0)
        assertEquals(LocalDateTime.of(2026, 11, 4, 22, 30), nextTrigger(now, LocalTime.of(22, 30)))
        assertEquals(LocalDateTime.of(2026, 11, 5, 21, 0), nextTrigger(now, LocalTime.of(21, 0)))
        assertEquals(LocalDateTime.of(2026, 11, 5, 8, 0), nextTrigger(now, LocalTime.of(8, 0)))
    }
}
