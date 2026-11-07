package com.l4ngel.nova.domain

import java.time.LocalDate

/** Alışkanlığın zorluğu; her tamamlanan günün kazandırdığı puanı belirler. */
enum class Difficulty(val points: Int, val label: String) {
    EASY(5, "Kolay"),
    MEDIUM(10, "Orta"),
    HARD(20, "Zor");

    companion object {
        /** Eski kayıtlarda ya da yedekte ara bir değer varsa en yakın zorluğu göster. */
        fun of(points: Int): Difficulty = entries.minBy { kotlin.math.abs(it.points - points) }
    }
}

/** Kullanıcının kendine koyduğu ödül: puanla "satın alınır". */
data class Reward(
    val id: Long = 0,
    val name: String,
    val emoji: String,
    val cost: Int,
    /** false ise bir kez alınınca rafa kalkar (ör. yeni kulaklık). */
    val repeatable: Boolean = true,
    val sortOrder: Int = 0,
)

/** Alınmış bir ödül. Ödül sonradan silinse ya da değişse de geçmiş bozulmasın diye ad ve bedel kopyalanır. */
data class Redemption(
    val id: Long = 0,
    val rewardId: Long?,
    val name: String,
    val emoji: String,
    val cost: Int,
    val date: LocalDate,
    val at: Long,
)

enum class PointKind { HABIT, STREAK, WEEK_GOAL, PERFECT_DAY, QUEST, BOSS }

data class PointEvent(val date: LocalDate, val kind: PointKind, val title: String, val amount: Int, val habitId: Long? = null)

object PointRules {
    /** Günlük seride her 7 günde bir: alışkanlık puanının katı. */
    const val STREAK_EVERY = 7
    const val STREAK_MULTIPLIER = 3
    /** Haftada X kez türünde hafta hedefi dolunca. */
    const val WEEK_GOAL_MULTIPLIER = 2
    /** O günün planlı alışkanlıklarının hepsi bittiyse. */
    const val PERFECT_DAY = 25
}

/**
 * Puanlar saklanmaz, kayıtlardan her seferinde yeniden hesaplanır. Böylece işaret geri alınınca puan da geri
 * gider (aç-kapa ile puan kasılamaz) ve geçmişe dönük düzeltmeler tutarlı kalır.
 *
 * Kurallar seri kurallarıyla aynı: plan dışı günde yapılan kayıt puan getirir ama seriyi uzatmaz.
 * Arşivdeki alışkanlıkların kazandırdıkları durur; yalnız "mükemmel gün" etkin listeye bakar.
 */
fun pointEvents(all: List<HabitProgress>, today: LocalDate, gameFrom: LocalDate? = null): List<PointEvent> {
    val events = mutableListOf<PointEvent>()
    for (p in all) {
        val h = p.habit
        for ((date, _) in p.checks) {
            if (!date.isAfter(today) && p.isDone(date)) events += PointEvent(date, PointKind.HABIT, h.name, h.points, h.id)
        }
        if (h.schedule.type == ScheduleType.TIMES_PER_WEEK) {
            val target = h.schedule.timesPerWeek.coerceIn(1, 7)
            var w = p.weekOf(p.start)
            while (!w.isAfter(today)) {
                // Ödül hedefin dolduğu gün yazılır ki geçmişte doğru yerde görünsün.
                val reached = (0L until 7L).map { w.plusDays(it) }.filter { !it.isAfter(today) && p.isDone(it) }.getOrNull(target - 1)
                if (reached != null) events += PointEvent(reached, PointKind.WEEK_GOAL, "${h.name} · haftalık hedef", h.points * PointRules.WEEK_GOAL_MULTIPLIER, h.id)
                w = w.plusWeeks(1)
            }
        } else {
            var run = 0
            var d = p.start
            while (!d.isAfter(today)) {
                if (h.schedule.isPlanned(d)) {
                    if (p.isDone(d)) {
                        run++
                        if (run % PointRules.STREAK_EVERY == 0) {
                            events += PointEvent(d, PointKind.STREAK, "${h.name} · $run günlük seri", h.points * PointRules.STREAK_MULTIPLIER, h.id)
                        }
                    } else if (d != today) run = 0
                }
                d = d.plusDays(1)
            }
        }
    }
    for (d in perfectDays(all, today)) events += PointEvent(d, PointKind.PERFECT_DAY, "Mükemmel gün", PointRules.PERFECT_DAY)
    if (gameFrom != null) events += gameEvents(all, today, gameFrom)
    return events.sortedWith(compareByDescending<PointEvent> { it.date }.thenBy { it.kind })
}

/** Etkin alışkanlıkların o gün beklenenlerinin tamamının yapıldığı günler. */
fun perfectDays(all: List<HabitProgress>, today: LocalDate): List<LocalDate> {
    val active = all.filter { !it.habit.archived }
    val first = active.minOfOrNull { it.start } ?: return emptyList()
    val days = mutableListOf<LocalDate>()
    var d = first
    while (!d.isAfter(today)) {
        val s = dayScore(active, d)
        if (s.due > 0 && s.done == s.due) days += d
        d = d.plusDays(1)
    }
    return days
}

/** Seviye: harcanan değil toplam kazanılan puana göre; ödül almak seviyeyi düşürmez. */
data class Level(val number: Int, val into: Int, val span: Int) {
    val title: String get() = LEVEL_TITLES.getOrElse(number - 1) { LEVEL_TITLES.last() }
    val toNext: Int get() = span - into
    val fraction: Float get() = into.toFloat() / span

    companion object {
        /**
         * Her seviye bir öncekinden 100 puan uzun: ilki birkaç günde gelir, onuncusu (Efsane) günde
         * altı alışkanlıkla üç ay kadar sürer.
         */
        fun span(number: Int): Int = 150 + 100 * (number - 1)

        fun of(points: Int): Level {
            var n = 1
            var rest = points.coerceAtLeast(0)
            while (rest >= span(n)) { rest -= span(n); n++ }
            return Level(n, rest, span(n))
        }
    }
}

/** Evren büyüdükçe: tozdan evrene. */
val LEVEL_TITLES = listOf("Toz bulutu", "Kıvılcım", "Yıldız", "Takımyıldız", "Güneş sistemi", "Nebula", "Galaksi", "Süperküme", "Kozmos", "Evren")

/** Puan cüzdanı: kazanılanlar kayıtlardan, harcananlar alınan ödüllerden ve evrene eklenen cisimlerden. */
class Wallet(val events: List<PointEvent>, val redemptions: List<Redemption>, val objects: List<SkyObject> = emptyList()) {
    val earned: Int = events.sumOf { it.amount }
    val spent: Int = redemptions.sumOf { it.cost } + objects.sumOf { it.cost }
    /** Geçmiş bir işaret geri alınırsa eksiye düşebilir; gizlemiyoruz, dürüst kalsın. */
    val balance: Int get() = earned - spent
    val level: Level = Level.of(earned)

    fun earnedOn(date: LocalDate): Int = events.filter { it.date == date }.sumOf { it.amount }
    fun timesRedeemed(rewardId: Long): Int = redemptions.count { it.rewardId == rewardId }
}

data class Badge(
    val id: String,
    val title: String,
    val description: String,
    val icon: String,
    val current: Int,
    val goal: Int,
) {
    val unlocked: Boolean get() = current >= goal
    val fraction: Float get() = (current.toFloat() / goal).coerceIn(0f, 1f)
}

fun badges(all: List<HabitProgress>, wallet: Wallet, today: LocalDate): List<Badge> {
    val total = all.sumOf { it.totalDone() }
    val daily = all.filter { it.habit.schedule.type != ScheduleType.TIMES_PER_WEEK }
    val weekly = all.filter { it.habit.schedule.type == ScheduleType.TIMES_PER_WEEK }
    val bestDay = daily.maxOfOrNull { it.bestStreak(today) } ?: 0
    val bestWeek = weekly.maxOfOrNull { it.bestStreak(today) } ?: 0
    val perfect = wallet.events.filter { it.kind == PointKind.PERFECT_DAY }.map { it.date }
    val perfectRun = longestRun(perfect)
    val active = all.count { !it.habit.archived }
    return listOf(
        Badge("first", "İlk adım", "İlk alışkanlığını tamamla", "spark", total, 1),
        Badge("streak7", "Bir hafta", "7 günlük seri yakala", "fire", bestDay, 7),
        Badge("streak30", "Ay boyu", "30 günlük seri yakala", "flame", bestDay, 30),
        Badge("weekly4", "İstikrar", "Haftalık hedefi 4 hafta üst üste tuttur", "calendar", bestWeek, 4),
        Badge("perfect", "Kusursuz", "Bir günün tüm alışkanlıklarını bitir", "diamond", perfect.size, 1),
        Badge("perfect7", "Kusursuz hafta", "7 gün üst üste kusursuz gün", "crown", perfectRun, 7),
        Badge("hundred", "Yüzlük", "Toplam 100 kez tamamla", "hundred", total, 100),
        Badge("collector", "Koleksiyoncu", "5 etkin alışkanlık edin", "grid", active, 5),
        Badge("points1000", "Bin puan", "Toplam 1000 puan kazan", "bolt", wallet.earned, 1000),
        Badge("level5", "Güneş sistemi", "5. seviyeye ulaş", "sun", wallet.level.number, 5),
        Badge("reward1", "Kendini şımart", "İlk ödülünü al", "gift", wallet.redemptions.size, 1),
        Badge("sky5", "Gök mimarı", "Evrenine 5 gök cismi ekle", "planet", wallet.objects.size, 5),
        Badge("boss1", "İlk zafer", "Haftanın canavarını yen", "sword", wallet.events.count { it.kind == PointKind.BOSS }, 1),
        Badge("quest20", "Görev ustası", "20 görev tamamla", "flag", wallet.events.count { it.kind == PointKind.QUEST }, 20),
    )
}

/** Sıralı tarihler içindeki en uzun ardışık gün dizisi. */
internal fun longestRun(dates: List<LocalDate>): Int {
    var best = 0
    var run = 0
    var prev: LocalDate? = null
    for (d in dates.sorted()) {
        run = if (prev != null && d == prev.plusDays(1)) run + 1 else 1
        best = maxOf(best, run)
        prev = d
    }
    return best
}

/** Boş ödül rafında tek dokunuşla eklenebilen öneriler. */
val REWARD_TEMPLATES = listOf(
    Reward(name = "Bir bölüm dizi", emoji = "🎬", cost = 50),
    Reward(name = "Dışarıda kahve", emoji = "☕", cost = 80),
    Reward(name = "Tatlı molası", emoji = "🍰", cost = 100),
    Reward(name = "Bir saat oyun", emoji = "🎮", cost = 120),
    Reward(name = "Sinema gecesi", emoji = "🍿", cost = 300),
    Reward(name = "Yeni bir kitap", emoji = "📚", cost = 400, repeatable = false),
)

/** Ödül düzenlemede seçilebilen emojiler; istenirse kullanıcı kendisi de yazabilir. */
val REWARD_EMOJIS = listOf(
    "🎁", "🎬", "🍿", "☕", "🍰", "🍫", "🍕", "🍔", "🍦", "🎮", "📚", "🎧",
    "🎵", "🛍️", "👟", "💆", "🛁", "🌴", "✈️", "🚲", "🎨", "📱", "🧸", "🌸",
    "😴", "🏖️", "🍷", "🎟️", "💐", "⭐",
)
