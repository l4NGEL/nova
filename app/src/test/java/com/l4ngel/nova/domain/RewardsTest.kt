package com.l4ngel.nova.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalDate

class RewardsTest {
    // 2026-11-02 Pazartesi
    private val mon = LocalDate.of(2026, 11, 2)

    private fun habit(id: Long = 1, schedule: Schedule = Schedule.Daily, points: Int = 10, archived: Boolean = false) =
        Habit(id = id, name = "H$id", icon = "star", color = 0, schedule = schedule, createdOn = mon.minusDays(30), points = points, archived = archived)

    private fun days(from: LocalDate, count: Int) = (0 until count).map { from.plusDays(it.toLong()) }

    private fun progress(h: Habit, done: List<LocalDate>) = HabitProgress(h, done.associateWith { h.target })

    private fun sum(events: List<PointEvent>, kind: PointKind) = events.filter { it.kind == kind }.sumOf { it.amount }

    @Test
    fun `her tamamlanan gun aliskanlik puani kadar`() {
        val p = progress(habit(points = 20), listOf(mon, mon.plusDays(2)))
        val e = pointEvents(listOf(p), mon.plusDays(3))
        assertEquals(40, sum(e, PointKind.HABIT))
        assertEquals(0, sum(e, PointKind.STREAK))
    }

    @Test
    fun `yedi gunluk seride bonus, kirilinca sayac sifirlanir`() {
        val today = mon.plusDays(20)
        // 7 gün + boşluk + 7 gün: iki bonus; 14 değil iki kez 7.
        val done = days(mon, 7) + days(mon.plusDays(8), 7)
        val e = pointEvents(listOf(progress(habit(), done)), today)
        val streaks = e.filter { it.kind == PointKind.STREAK }
        assertEquals(2, streaks.size)
        assertTrue(streaks.all { it.title.endsWith("7 günlük seri") })
        assertEquals(2 * 10 * PointRules.STREAK_MULTIPLIER, sum(e, PointKind.STREAK))
    }

    @Test
    fun `plan disi gun seriyi bozmaz ama puan getirir`() {
        val h = habit(schedule = Schedule.on(MONDAY, WEDNESDAY))
        // 4 hafta Pzt+Çar = 8 planlı gün, araya bir Salı eklendi.
        val done = (0 until 4).flatMap { w -> listOf(mon.plusWeeks(w.toLong()), mon.plusWeeks(w.toLong()).plusDays(2)) } + mon.plusDays(1)
        val e = pointEvents(listOf(progress(h, done)), mon.plusWeeks(4))
        assertEquals(90, sum(e, PointKind.HABIT))
        assertEquals(1, e.count { it.kind == PointKind.STREAK })
    }

    @Test
    fun `haftalik hedef dolunca bonus hedefin doldugu gune yazilir`() {
        val h = habit(schedule = Schedule.perWeek(2))
        val e = pointEvents(listOf(progress(h, listOf(mon, mon.plusDays(3), mon.plusDays(4)))), mon.plusDays(6))
        val goal = e.single { it.kind == PointKind.WEEK_GOAL }
        assertEquals(mon.plusDays(3), goal.date)
        assertEquals(10 * PointRules.WEEK_GOAL_MULTIPLIER, goal.amount)
    }

    @Test
    fun `mukemmel gun arsivi saymaz`() {
        val a = progress(habit(1), listOf(mon))
        val b = progress(habit(2), listOf())
        val archived = progress(habit(3, archived = true), listOf())
        assertEquals(listOf<LocalDate>(), perfectDays(listOf(a, b), mon))
        assertEquals(listOf(mon), perfectDays(listOf(a, archived), mon))
    }

    @Test
    fun `gelecek gun puan getirmez`() {
        val e = pointEvents(listOf(progress(habit(), listOf(mon.plusDays(1)))), mon)
        assertEquals(0, e.sumOf { it.amount })
    }

    @Test
    fun `seviye esikleri`() {
        assertEquals(Level(1, 0, 150), Level.of(0))
        assertEquals(Level(1, 149, 150), Level.of(149))
        assertEquals(Level(2, 0, 250), Level.of(150))
        assertEquals(Level(3, 10, 350), Level.of(410))
        assertEquals("Yıldız", Level.of(410).title)
        assertEquals("Evren", Level.of(1_000_000).title)
    }

    @Test
    fun `harcama bakiyeyi dusurur seviyeyi dusurmez`() {
        val events = listOf(PointEvent(mon, PointKind.HABIT, "x", 300))
        val w = Wallet(events, listOf(Redemption(1, 5, "Dizi", "🎬", 120, mon, 0), Redemption(2, 5, "Dizi", "🎬", 120, mon, 0)))
        assertEquals(60, w.balance)
        assertEquals(Level.of(300), w.level)
        assertEquals(2, w.timesRedeemed(5))
    }

    @Test
    fun `rozetler ilerlemeyi gosterir`() {
        val p = progress(habit(), days(mon, 7))
        val today = mon.plusDays(6)
        val w = Wallet(pointEvents(listOf(p), today), emptyList())
        val b = badges(listOf(p), w, today).associateBy { it.id }
        assertTrue(b.getValue("first").unlocked)
        assertTrue(b.getValue("streak7").unlocked)
        assertFalse(b.getValue("streak30").unlocked)
        assertEquals(7f / 30, b.getValue("streak30").fraction, 1e-4f)
        // Tek alışkanlık her gün yapıldı: 7 kusursuz gün üst üste.
        assertTrue(b.getValue("perfect7").unlocked)
        assertFalse(b.getValue("reward1").unlocked)
    }

    @Test
    fun `ardisik gun dizisi`() {
        assertEquals(0, longestRun(emptyList()))
        assertEquals(3, longestRun(listOf(mon, mon.plusDays(5), mon.plusDays(1), mon.plusDays(2))))
    }

    @Test
    fun `zorluk en yakin degere eslenir`() {
        assertEquals(Difficulty.EASY, Difficulty.of(5))
        assertEquals(Difficulty.HARD, Difficulty.of(40))
        assertEquals(Difficulty.MEDIUM, Difficulty.of(12))
    }
}
