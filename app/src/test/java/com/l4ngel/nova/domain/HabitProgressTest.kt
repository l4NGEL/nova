package com.l4ngel.nova.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalDate

class HabitProgressTest {
    // 2026-11-02 Pazartesi
    private val mon = LocalDate.of(2026, 11, 2)

    private fun habit(schedule: Schedule = Schedule.Daily, target: Int = 1, created: LocalDate = mon.minusDays(30)) =
        Habit(id = 1, name = "Test", icon = "star", color = 0, schedule = schedule, target = target, createdOn = created)

    private fun progress(h: Habit, vararg done: LocalDate, value: Int = h.target) =
        HabitProgress(h, done.associateWith { value })

    private fun days(from: LocalDate, count: Int) = (0 until count).map { from.plusDays(it.toLong()) }.toTypedArray()

    @Test
    fun `gunluk seri bugun yapilmadiysa dunden sayilir`() {
        val today = mon.plusDays(4)
        val p = progress(habit(), *days(mon, 4)) // Pzt-Per yapıldı, Cuma (bugün) henüz değil
        assertEquals(4, p.currentStreak(today))
    }

    @Test
    fun `gunluk seri bugun yapilinca bugun dahil`() {
        val today = mon.plusDays(4)
        val p = progress(habit(), *days(mon, 5))
        assertEquals(5, p.currentStreak(today))
    }

    @Test
    fun `kacirilan gun seriyi bozar`() {
        val today = mon.plusDays(6)
        val p = progress(habit(), mon, mon.plusDays(1), mon.plusDays(3), mon.plusDays(4), mon.plusDays(5))
        assertEquals(3, p.currentStreak(today))
        assertEquals(3, p.bestStreak(today))
    }

    @Test
    fun `belirli gunlerde plan disi gunler seriyi bozmaz`() {
        val h = habit(Schedule.on(MONDAY, WEDNESDAY, FRIDAY))
        val today = mon.plusDays(9) // sonraki Çarşamba, henüz yapılmadı
        val p = progress(h, mon, mon.plusDays(2), mon.plusDays(4), mon.plusDays(7))
        assertEquals(4, p.currentStreak(today))
    }

    @Test
    fun `plan disi gunde yapilan kayit seriyi uzatmaz`() {
        val h = habit(Schedule.on(MONDAY))
        val p = progress(h, mon, mon.plusDays(1), mon.plusDays(7))
        assertEquals(2, p.currentStreak(mon.plusDays(7)))
    }

    @Test
    fun `en iyi seri gecmisteki en uzun kosu`() {
        val today = mon.plusDays(20)
        val p = progress(habit(), *days(mon, 6), *days(mon.plusDays(10), 3))
        assertEquals(6, p.bestStreak(today))
        assertEquals(0, p.currentStreak(today))
    }

    @Test
    fun `sayili hedef dolmadan tamamlanmis sayilmaz`() {
        val h = habit(target = 8)
        val p = HabitProgress(h, mapOf(mon to 5, mon.plusDays(1) to 8))
        assertFalse(p.isDone(mon))
        assertTrue(p.isDone(mon.plusDays(1)))
        assertEquals(5f / 8, p.fraction(mon), 1e-6f)
        assertEquals(1, p.totalDone())
    }

    @Test
    fun `haftada x kez seri hafta sayar ve suren hafta bozmaz`() {
        val h = habit(Schedule.perWeek(3), created = mon.minusWeeks(3))
        val p = progress(
            h,
            // 3 hafta önce: 3 kez, 2 hafta önce: 3 kez, geçen hafta: 4 kez, bu hafta: 1 kez (sürüyor)
            mon.minusWeeks(3), mon.minusWeeks(3).plusDays(2), mon.minusWeeks(3).plusDays(4),
            mon.minusWeeks(2), mon.minusWeeks(2).plusDays(1), mon.minusWeeks(2).plusDays(6),
            mon.minusWeeks(1), mon.minusWeeks(1).plusDays(1), mon.minusWeeks(1).plusDays(2), mon.minusWeeks(1).plusDays(3),
            mon,
        )
        val today = mon.plusDays(2)
        assertEquals(3, p.currentStreak(today))
        assertEquals("hafta", p.streakUnit)
        assertEquals(3, p.bestStreak(today))
    }

    @Test
    fun `haftada x kez hedef dolunca kalan gunler beklenmez`() {
        val h = habit(Schedule.perWeek(2))
        val p = progress(h, mon, mon.plusDays(1))
        assertTrue(p.isDue(mon))
        assertTrue(p.isDue(mon.plusDays(1)))
        assertFalse(p.isDue(mon.plusDays(2)))
        assertTrue(p.isDue(mon.plusDays(7))) // yeni hafta
    }

    @Test
    fun `hafta baslangici pazar secilince haftalar kayar`() {
        val h = habit(Schedule.perWeek(2))
        val sat = mon.plusDays(5)
        val sun = mon.plusDays(6)
        val monStart = HabitProgress(h, mapOf(sat to 1, sun to 1), MONDAY)
        val sunStart = HabitProgress(h, mapOf(sat to 1, sun to 1), SUNDAY)
        assertTrue(monStart.weekGoalMet(sat))
        assertFalse(sunStart.weekGoalMet(sat))
    }

    @Test
    fun `basari orani bugunu henuz saymaz`() {
        val today = mon.plusDays(3)
        val h = habit(created = mon)
        val p = progress(h, mon, mon.plusDays(2)) // Pzt ve Çar yapıldı, Salı kaçtı, Perşembe sürüyor
        assertEquals(2f / 3, p.completionRate(30, today)!!, 1e-6f)
    }

    @Test
    fun `yeni alisakanlikta oran yok`() {
        val h = habit(created = mon)
        assertNull(HabitProgress(h, emptyMap()).completionRate(30, mon))
    }

    @Test
    fun `olusturulmadan once beklenmez ama geriye isaretleme baslangici kaydirir`() {
        val h = habit(created = mon)
        assertFalse(HabitProgress(h, emptyMap()).isDue(mon.minusDays(1)))
        val back = HabitProgress(h, mapOf(mon.minusDays(3) to 1))
        assertEquals(mon.minusDays(3), back.start)
        assertTrue(back.isDue(mon.minusDays(1)))
    }

    @Test
    fun `gun ozeti beklenenleri sayar`() {
        val a = progress(habit(), mon)
        val b = progress(habit(Schedule.on(FRIDAY)).copyId(2))
        val c = progress(habit().copyId(3))
        val s = dayScore(listOf(a, b, c), mon)
        assertEquals(DayScore(1, 2), s)
        assertEquals(0.5f, s.fraction, 1e-6f)
    }

    @Test
    fun `haftanin gunleri dagilimi`() {
        val p = progress(habit(), mon, mon.plusDays(7), mon.plusDays(2))
        val d = weekdayDistribution(listOf(p), 28, mon.plusDays(10))
        assertEquals(2, d[MONDAY])
        assertEquals(1, d[WEDNESDAY])
        assertEquals(0, d[FRIDAY])
    }

    private fun Habit.copyId(id: Long) = copy(id = id)
}
