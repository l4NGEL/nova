package com.l4ngel.ritim.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class QuestsTest {
    // 2026-11-02 Pazartesi
    private val mon = LocalDate.of(2026, 11, 2)

    private fun habit(id: Long, points: Int = 10, target: Int = 1, schedule: Schedule = Schedule.Daily) =
        Habit(id = id, name = "H$id", icon = "star", color = 0, schedule = schedule, target = target, createdOn = mon.minusDays(30), points = points)

    private fun progress(h: Habit, done: List<LocalDate>) = HabitProgress(h, done.associateWith { h.target })

    @Test
    fun `gunde uc gorev, ilki hep tamamla`() {
        val list = (1L..5L).map { progress(habit(it, points = if (it == 1L) 20 else 10), emptyList()) }
        val q = dailyQuests(list, mon)
        assertEquals(3, q.size)
        assertEquals("complete", q.first().id)
        assertEquals(3, q.first().goal)
        assertEquals(q.map { it.id }.toSet().size, 3)
    }

    @Test
    fun `gorevler gun icinde isaretlenince degismez`() {
        val hs = (1L..4L).map { habit(it, points = 20) }
        val before = dailyQuests(hs.map { progress(it, emptyList()) }, mon).map { it.id }
        val after = dailyQuests(hs.map { progress(it, listOf(mon)) }, mon)
        assertEquals(before, after.map { it.id })
        assertTrue(after.all { it.done })
    }

    @Test
    fun `plan yoksa gorev yok`() {
        val p = progress(habit(1, schedule = Schedule.on(java.time.DayOfWeek.SUNDAY)), emptyList())
        assertEquals(emptyList<Quest>(), dailyQuests(listOf(p), mon))
    }

    @Test
    fun `canavarin cani beklenen puanin dortte ucu`() {
        // 2 günlük alışkanlık × 7 gün × 10 puan = 140 → 105 → 100 (onluğa yuvarlanır).
        val list = listOf(progress(habit(1), emptyList()), progress(habit(2), emptyList()))
        val b = weeklyBoss(list, mon, mon)!!
        assertEquals(100, b.maxHp)
        assertEquals(0, b.damage)
        assertFalse(b.defeated)
    }

    @Test
    fun `canavar yenildigi gun kaydedilir`() {
        val days = (0L until 5L).map { mon.plusDays(it) }
        val list = listOf(progress(habit(1), days), progress(habit(2), days))
        val b = weeklyBoss(list, mon, mon.plusDays(6))!!
        assertEquals(100, b.damage)
        // Her gün 20 vurur: 5. gün (Cuma) 100'e ulaşır.
        assertEquals(mon.plusDays(4), b.defeatedOn)
        assertEquals(0, b.hp)
    }

    @Test
    fun `haftalik hedefi asan gunler vurmaz`() {
        val h = habit(1, schedule = Schedule.perWeek(2))
        val b = weeklyBoss(listOf(progress(h, (0L until 7L).map { mon.plusDays(it) })), mon, mon.plusDays(6))!!
        assertEquals(20, b.damage)
    }

    @Test
    fun `oyun baslamadan once odul yok`() {
        val days = (0L until 14L).map { mon.minusDays(13).plusDays(it) }
        val list = listOf(progress(habit(1), days), progress(habit(2), days))
        assertEquals(emptyList<PointEvent>(), gameEvents(list, mon, mon.plusDays(1)))
        val events = gameEvents(list, mon, mon.minusDays(6))
        assertTrue(events.any { it.kind == PointKind.QUEST })
        assertTrue(events.all { !it.date.isBefore(mon.minusDays(6)) })
    }

    @Test
    fun `bos listede canavar yok`() {
        assertNull(weeklyBoss(emptyList(), mon, mon))
    }
}
