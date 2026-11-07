package com.l4ngel.nova.data

import com.l4ngel.nova.domain.Habit
import com.l4ngel.nova.domain.Schedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class BackupTest {
    private val habit = Habit(
        id = 7, name = "Kitap “oku”", icon = "book", color = 0xFFF0743E.toInt(),
        schedule = Schedule.on(DayOfWeek.MONDAY, DayOfWeek.THURSDAY), target = 20, unit = "sayfa",
        reminder = LocalTime.of(21, 15), createdOn = LocalDate.of(2026, 10, 1), note = "Yatmadan önce",
    )

    @Test
    fun `yedek gidip gelir`() {
        val checks = listOf(CheckEntity(7, 20400, 20), CheckEntity(7, 20401, 5))
        val json = Backup.encode(Backup.Content(listOf(habit.toEntity(), habit.copy(id = 8, reminder = null, points = 20).toEntity()), checks))
        val back = Backup.decode(json)
        assertEquals(habit, back.habits[0].toDomain())
        assertEquals(null, back.habits[1].reminderMinute)
        assertEquals(20, back.habits[1].points)
        assertEquals(checks, back.checks)
    }

    @Test
    fun `sahipsiz ve sifir kayitlar ayiklanir`() {
        val json = Backup.encode(Backup.Content(listOf(habit.toEntity()), listOf(CheckEntity(7, 1, 1), CheckEntity(99, 1, 1), CheckEntity(7, 2, 0))))
        assertEquals(listOf(CheckEntity(7, 1, 1)), Backup.decode(json).checks)
    }

    @Test
    fun `baska dosya reddedilir`() {
        assertThrows(IllegalArgumentException::class.java) { Backup.decode("""{"app":"baska","version":1}""") }
        assertThrows(IllegalArgumentException::class.java) { Backup.decode("yedek değil") }
        assertThrows(IllegalArgumentException::class.java) { Backup.decode("""{"app":"ritim","version":99,"habits":[],"checks":[]}""") }
    }

    @Test
    fun `eksik alanlar varsayilanla acilir`() {
        val json = """{"app":"ritim","version":1,"habits":[{"id":1,"name":"Su","color":0,"createdEpochDay":20000}],"checks":[]}"""
        val h = Backup.decode(json).habits.single().toDomain()
        assertEquals(Schedule.Daily, h.schedule)
        assertEquals(1, h.target)
        assertEquals(null, h.reminder)
        assertEquals(10, h.points)
        assertEquals(emptyList<RewardEntity>(), Backup.decode(json).rewards)
    }

    @Test
    fun `odul ve alinan odul gidip gelir`() {
        val rewards = listOf(RewardEntity(3, "Dizi", "🎬", 50, true, 0), RewardEntity(4, "Kulaklık", "🎧", 900, false, 1))
        val red = listOf(
            RedemptionEntity(1, 3, "Dizi", "🎬", 50, 20400, 1_790_000_000_000),
            RedemptionEntity(2, null, "Silinmiş", "🎁", 70, 20401, 0),
        )
        val sky = listOf(SkyObjectEntity(9, "RINGED", 0xFF9B5DE5.toInt(), "Zühre", 0.25f, 1.1f, 300, 20402))
        val back = Backup.decode(Backup.encode(Backup.Content(listOf(habit.toEntity()), emptyList(), rewards, red, sky)))
        assertEquals(rewards, back.rewards)
        assertEquals(red, back.redemptions)
        assertEquals(sky, back.sky)
    }
}
