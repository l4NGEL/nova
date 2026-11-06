package com.l4ngel.ritim.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SkyTest {
    private val mon = LocalDate.of(2026, 11, 2)

    private fun habit(id: Long, schedule: Schedule = Schedule.Daily) =
        Habit(id = id, name = "H$id", icon = "star", color = id.toInt(), schedule = schedule, createdOn = mon.minusDays(60))

    private fun progress(h: Habit, done: List<LocalDate>) = HabitProgress(h, done.associateWith { 1 })
    private fun days(from: LocalDate, n: Int) = (0 until n).map { from.plusDays(it.toLong()) }

    @Test
    fun `her tamamlanan gun bir yildiz`() {
        val sky = skyLayout(listOf(progress(habit(1), days(mon.minusDays(9), 10)), progress(habit(2), listOf(mon))), mon)
        assertEquals(11, sky.totalStars)
        assertEquals(listOf(10, 1), sky.galaxies.map { it.stars })
    }

    @Test
    fun `yeni yildiz eskileri yerinden oynatmaz`() {
        val before = skyLayout(listOf(progress(habit(1), days(mon.minusDays(9), 10))), mon)
        val after = skyLayout(listOf(progress(habit(1), days(mon.minusDays(9), 11))), mon.plusDays(1))
        assertEquals(before.stars.map { it.x to it.y }, after.stars.take(10).map { it.x to it.y })
    }

    @Test
    fun `yeni aliskanlik var olan galaksiyi tasimaz`() {
        val a = progress(habit(1), listOf(mon))
        val one = skyLayout(listOf(a), mon).galaxies.single()
        val two = skyLayout(listOf(progress(habit(5), listOf(mon)), a), mon).galaxies.first { it.habitId == 1L }
        assertEquals(one.x to one.y, two.x to two.y)
    }

    @Test
    fun `takimyildiz suren serinin yildizlari`() {
        // 5 gün yapıldı, bir gün boş, sonra 3 gün: seri 3.
        val done = days(mon.minusDays(8), 5) + days(mon.minusDays(2), 3)
        val sky = skyLayout(listOf(progress(habit(1), done)), mon)
        val c = sky.constellation(1)
        assertEquals(3, c.size)
        assertEquals(listOf(5, 6, 7), c.map { it.index })
    }

    @Test
    fun `yildizlar galaksisinin yakininda ve gokyuzunde`() {
        val sky = skyLayout((1L..14L).map { progress(habit(it), days(mon.minusDays(364), 365)) }, mon)
        for (s in sky.stars) {
            val g = sky.galaxies.first { it.habitId == s.habitId }
            val d = kotlin.math.hypot(s.x - g.x, s.y - g.y)
            assertTrue("yıldız galaksiden uzak: $d", d < 0.17f)
        }
        for (g in sky.galaxies) assertTrue(g.x in 0f..1f && g.y in 0f..SKY_HEIGHT)
    }

    @Test
    fun `karistirma araliga duser`() {
        for (i in 0L until 500L) assertTrue(hash01(i, i * 31) in 0f..1f)
    }
}
