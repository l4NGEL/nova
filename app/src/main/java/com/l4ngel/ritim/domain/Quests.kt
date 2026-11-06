package com.l4ngel.ritim.domain

import java.time.LocalDate

/**
 * Oyun katmanı: günün görevleri ve haftanın canavarı. İkisi de puan gibi kayıtlardan türetilir; saklanan bir
 * "görev tamamlandı" bilgisi yoktur. Yalnız oyunun başladığı gün saklanır ki eski kayıtlar geriye dönük
 * görev ve canavar ödülü yağdırmasın.
 */
data class Quest(
    val id: String,
    val title: String,
    val icon: String,
    val current: Int,
    val goal: Int,
    val reward: Int,
) {
    val done: Boolean get() = current >= goal
    val fraction: Float get() = (current.toFloat() / goal).coerceIn(0f, 1f)
}

object QuestRules {
    const val PER_DAY = 3
    const val COMPLETE = 20
    const val HARD = 25
    const val COUNTED = 20
    const val PERFECT = 40
    const val STREAK = 15
    const val COMEBACK = 30
    const val BOSS = 100
    /** Canavarın canı, haftanın beklenen puanının bu kadarı: her şeyi yapmak gerekmez ama çoğunu yapmak gerekir. */
    const val BOSS_HP_SHARE = 0.75f
}

/**
 * O günün görevleri. Aday görevler o gün planlı alışkanlıklardan çıkar; "N alışkanlık tamamla" hep vardır,
 * kalanlar tarihe bağlı karıştırmayla seçilir ki gün içinde değişmesin ama her gün farklı olsun.
 * Seri ve oran dünden ölçülür: bugün işaretledikçe görevin kendisi değişmesin.
 */
fun dailyQuests(active: List<HabitProgress>, date: LocalDate): List<Quest> {
    val due = active.filter { it.isDue(date) }
    if (due.isEmpty()) return emptyList()
    val day = date.toEpochDay()
    fun pick(list: List<HabitProgress>, salt: Long) = list.minByOrNull { hash01(day * 31 + salt, it.habit.id) }
    val doneDue = due.count { it.isDone(date) }

    val fixed = mutableListOf<Quest>()
    if (due.size >= 2) {
        val n = if (due.size >= 4) 3 else 2
        fixed += Quest("complete", "$n alışkanlık tamamla", "check", minOf(doneDue, n), n, QuestRules.COMPLETE)
    } else {
        val p = due.single()
        fixed += Quest("one:${p.habit.id}", "Günün tek işi: ${p.habit.name}", "check", if (p.isDone(date)) 1 else 0, 1, QuestRules.COMPLETE)
    }

    val pool = mutableListOf<Quest>()
    pick(due.filter { it.habit.points >= Difficulty.HARD.points }, 1)?.let { p ->
        pool += Quest("hard:${p.habit.id}", "Zor olanı hallet: ${p.habit.name}", "bolt", if (p.isDone(date)) 1 else 0, 1, QuestRules.HARD)
    }
    pick(due.filter { it.habit.isCounted }, 2)?.let { p ->
        val h = p.habit
        pool += Quest("count:${h.id}", "${h.name}: ${h.goalLabel()} hedefini doldur", "target", minOf(p.value(date), h.target), h.target, QuestRules.COUNTED)
    }
    if (due.size >= 2) pool += Quest("perfect", "Kusursuz gün: hepsini bitir", "diamond", doneDue, due.size, QuestRules.PERFECT)
    val yesterday = date.minusDays(1)
    due.filter { it.currentStreak(yesterday) >= 3 }.maxByOrNull { it.currentStreak(yesterday) }?.let { p ->
        pool += Quest("streak:${p.habit.id}", "${p.habit.name} serini koru · ${p.currentStreak(yesterday)} ${p.streakUnit}", "fire", if (p.isDone(date)) 1 else 0, 1, QuestRules.STREAK)
    }
    due.filter { !it.isDone(yesterday) && (it.completionRate(14, yesterday) ?: 1f) < 0.5f }
        .minByOrNull { it.completionRate(14, yesterday) ?: 1f }
        ?.let { p -> pool += Quest("comeback:${p.habit.id}", "Geri dön: ${p.habit.name}", "refresh", if (p.isDone(date)) 1 else 0, 1, QuestRules.COMEBACK) }

    val chosen = pool.sortedBy { hash01(day, it.id.hashCode().toLong()) }.take(QuestRules.PER_DAY - fixed.size)
    return fixed + chosen
}

/** Kötü alışkanlıklar canavar olarak; her hafta sıradaki gelir. */
data class BossKind(val name: String, val emoji: String, val taunt: String)

val BOSSES = listOf(
    BossKind("Erteleme Canavarı", "🐙", "“Yarın yaparsın…” diye fısıldıyor"),
    BossKind("Bahane Asteroidi", "☄️", "Her gün yeni bir bahaneyle geliyor"),
    BossKind("Uyuşukluk Ejderhası", "🐉", "Seni koltuğa çivilemek istiyor"),
    BossKind("Dağınıklık Kasırgası", "🌪️", "Dikkatini dağıtıp duruyor"),
    BossKind("Ekran Hayaleti", "👻", "Telefonu bırakmanı istemiyor"),
    BossKind("Tembellik Golemi", "🗿", "Taş gibi ağır ama kırılır"),
)

data class Boss(
    val kind: BossKind,
    val week: LocalDate,
    val maxHp: Int,
    val damage: Int,
    /** Canının bittiği gün; yenilmediyse null. */
    val defeatedOn: LocalDate?,
) {
    val hp: Int get() = (maxHp - damage).coerceAtLeast(0)
    val defeated: Boolean get() = defeatedOn != null
    val fraction: Float get() = hp.toFloat() / maxHp
}

/**
 * [week] haftasının canavarı. Canı o hafta beklenen puanların [QuestRules.BOSS_HP_SHARE] kadarı; her tamamlanan
 * gün alışkanlığın puanı kadar vurur, böylece zor alışkanlık daha sert vurur. Haftada X kez türünde hedefi
 * aşan günler vurmaz ki canavar tek alışkanlıkla dövülmesin.
 */
fun weeklyBoss(active: List<HabitProgress>, week: LocalDate, today: LocalDate): Boss? {
    if (active.isEmpty()) return null
    val days = (0L until 7L).map { week.plusDays(it) }
    var expected = 0
    val hits = mutableListOf<Pair<LocalDate, Int>>()
    for (p in active) {
        val h = p.habit
        val inRange = days.filter { !it.isBefore(p.start) }
        if (h.schedule.type == ScheduleType.TIMES_PER_WEEK) {
            if (inRange.isEmpty()) continue
            val cap = h.schedule.timesPerWeek.coerceIn(1, 7)
            expected += h.points * cap
            inRange.filter { !it.isAfter(today) && p.isDone(it) }.take(cap).forEach { hits += it to h.points }
        } else {
            expected += h.points * inRange.count { h.schedule.isPlanned(it) }
            inRange.filter { !it.isAfter(today) && p.isDone(it) }.forEach { hits += it to h.points }
        }
    }
    if (expected == 0) return null
    val maxHp = ((expected * QuestRules.BOSS_HP_SHARE / 10).toInt() * 10).coerceAtLeast(30)
    var total = 0
    var defeatedOn: LocalDate? = null
    for ((d, dmg) in hits.sortedBy { it.first }) {
        total += dmg
        if (defeatedOn == null && total >= maxHp) defeatedOn = d
    }
    val kind = BOSSES[Math.floorMod(week.toEpochDay() / 7, BOSSES.size.toLong()).toInt()]
    return Boss(kind, week, maxHp, total, defeatedOn)
}

/** Oyun başladığından beri tamamlanan görevler ve yenilen canavarlar için puan. */
fun gameEvents(all: List<HabitProgress>, today: LocalDate, from: LocalDate): List<PointEvent> {
    val active = all.filter { !it.habit.archived }
    if (active.isEmpty() || from.isAfter(today)) return emptyList()
    val events = mutableListOf<PointEvent>()
    var d = from
    while (!d.isAfter(today)) {
        for (q in dailyQuests(active, d)) if (q.done) events += PointEvent(d, PointKind.QUEST, "Görev: ${q.title}", q.reward)
        d = d.plusDays(1)
    }
    var w = active.first().weekOf(from)
    while (!w.isAfter(today)) {
        weeklyBoss(active, w, today)?.let { b ->
            b.defeatedOn?.let { events += PointEvent(it, PointKind.BOSS, "${b.kind.emoji} ${b.kind.name} yenildi", QuestRules.BOSS) }
        }
        w = w.plusWeeks(1)
    }
    return events
}
