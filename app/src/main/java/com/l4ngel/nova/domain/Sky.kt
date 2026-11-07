package com.l4ngel.nova.domain

import java.time.LocalDate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * Kullanıcının evreni. Her alışkanlık bir galaksidir; her tamamlanan gün o galaksiye bir yıldız ekler.
 * Yıldızlar sırayla [GALAXY_ARMS] sarmal kola dağıtılır ve merkezden dışa doğru dizilir; galaksi büyüdükçe
 * kollar uzar. Konumlar yalnız alışkanlığın kimliğine ve yıldızın sırasına bağlıdır: yeni yıldız eklenince
 * eskiler yerinden oynamaz.
 *
 * Koordinatlar genişlik birimindedir: x 0..1, y 0..[SKY_HEIGHT].
 */
const val SKY_HEIGHT = 1.3f

/** Bir yılda galaksi yarıçapı dolar; sonrasında kollar biraz daha dışa uzar. */
private const val FULL_GALAXY = 365f
private const val GALAXY_RADIUS = 0.14f
const val GALAXY_ARMS = 3
/** Kolun merkezden kenara kadar dönüşü (radyan). */
private const val ARM_TWIST = 4.2f

/** Gökyüzünde galaksi yuvaları; dağınık dursun diye elle yerleştirildi. 12'den sonrası kaydırılarak tekrarlanır. */
private val SLOTS = listOf(
    0.28f to 0.22f, 0.74f to 0.32f, 0.34f to 0.64f, 0.76f to 0.80f, 0.26f to 1.06f, 0.68f to 1.14f,
    0.12f to 0.44f, 0.88f to 0.56f, 0.55f to 0.90f, 0.10f to 0.82f, 0.56f to 0.08f, 0.52f to 0.42f,
)

data class Galaxy(val habitId: Long, val name: String, val color: Int, val x: Float, val y: Float, val stars: Int, val archived: Boolean)

data class Star(
    val habitId: Long,
    val index: Int,
    val date: LocalDate,
    val x: Float,
    val y: Float,
    /** Alışkanlığın zorluğuna göre 0,6..1,4 arası ölçek. */
    val size: Float,
    val color: Int,
    /** Süren serinin yıldızı: takımyıldız çizgisine bağlanır. */
    val inStreak: Boolean,
)

class SkyLayout(val galaxies: List<Galaxy>, val stars: List<Star>) {
    val totalStars: Int get() = stars.size
    fun constellation(habitId: Long): List<Star> = stars.filter { it.habitId == habitId && it.inStreak }.sortedBy { it.index }
}

/** Basit ve kararlı karıştırma; [kotlin.random.Random] sürümler arası aynı diziyi garanti etmez. */
internal fun hash01(a: Long, b: Long): Float {
    var h = a * -0x61c8864680b583ebL + b * 0x5851f42d4c957f2dL
    h = (h xor (h ushr 31)) * -0x40a7b892e31b1a47L
    h = h xor (h ushr 29)
    return ((h ushr 40).toFloat() / (1L shl 24).toFloat())
}

fun galaxyCenter(slot: Int): Pair<Float, Float> {
    val (x, y) = SLOTS[slot % SLOTS.size]
    val round = slot / SLOTS.size
    // Yuvalar tükenince bir sonraki tur biraz kaydırılmış konumlara gelir.
    return ((x + round * 0.11f) % 1f).coerceIn(0.08f, 0.92f) to ((y + round * 0.17f) % SKY_HEIGHT).coerceIn(0.08f, SKY_HEIGHT - 0.08f)
}

/** Galaksideki [index]. yıldızın merkeze göre konumu. */
fun starOffset(habitId: Long, index: Int): Pair<Float, Float> {
    val k = index + 1
    // Kol boyunca ilerleme; üs 1'den küçük ki ilk yıldızlar merkeze yığılmasın, galaksi erken şekil alsın.
    val t = (k / FULL_GALAXY).toDouble().pow(0.45).toFloat()
    val arm = index % GALAXY_ARMS
    // Kollar çizgi gibi durmasın: yıldızlar kolun çevresine saçılır, merkeze yakın daha geniş açıyla.
    val jr = hash01(habitId, k.toLong()) - 0.5f
    val ja = hash01(habitId, k + 7919L) - 0.5f
    val r = GALAXY_RADIUS * t + 0.022f * jr * (0.35f + t)
    // Her galaksi kendi açısıyla başlasın ki hepsi aynı yöne bakmasın.
    val a = hash01(habitId, 0) * 2f * PI.toFloat() + arm * 2f * PI.toFloat() / GALAXY_ARMS + t * ARM_TWIST + 0.9f * ja * (1.1f - t)
    return r * cos(a) to r * sin(a)
}

/**
 * Yuvalar kimlik sırasına göre dağıtılır: yeni alışkanlık sona eklenir, var olanların yeri değişmez.
 * Arşivdeki alışkanlıkların galaksisi kalır (geçmiş emek kaybolmasın), yalnız sönük çizilir.
 */
fun skyLayout(all: List<HabitProgress>, today: LocalDate): SkyLayout {
    val galaxies = mutableListOf<Galaxy>()
    val stars = mutableListOf<Star>()
    all.sortedBy { it.habit.id }.forEachIndexed { slot, p ->
        val h = p.habit
        val (cx, cy) = galaxyCenter(slot)
        val days = p.checks.keys.filter { !it.isAfter(today) && p.isDone(it) }.sorted()
        val streak = p.currentStreak(today)
        // Takımyıldız: süren serinin içindeki son yıldızlar. Haftalık türde seri hafta sayar; o haftalardaki günler.
        val streakFrom = when {
            streak == 0 -> null
            h.schedule.type == ScheduleType.TIMES_PER_WEEK -> {
                val current = p.weekOf(today)
                current.minusWeeks((if (p.weekGoalMet(current)) streak - 1 else streak).toLong())
            }
            else -> days.takeLast(streak).firstOrNull()
        }
        val size = (0.6f + h.points / 25f).coerceIn(0.6f, 1.4f)
        days.forEachIndexed { i, d ->
            val (dx, dy) = starOffset(h.id, i)
            stars += Star(h.id, i, d, cx + dx, cy + dy, size, h.color, streakFrom != null && !d.isBefore(streakFrom))
        }
        galaxies += Galaxy(h.id, h.name, h.color, cx, cy, days.size, h.archived)
    }
    return SkyLayout(galaxies, stars)
}

/** Puanla alınıp gökyüzüne yerleştirilen cisimler. */
enum class SkyKind(val title: String, val cost: Int, val colorable: Boolean, val description: String) {
    MOON("Ay", 80, false, "Galaksilerinin yanında sessiz bir yoldaş"),
    PLANET("Gezegen", 150, true, "Rengini sen seç, adını sen koy"),
    COMET("Kuyruklu yıldız", 200, true, "Parlak kuyruğuyla gökyüzünü çizer"),
    RINGED("Halkalı gezegen", 300, true, "Satürn gibi halkalı bir dev"),
    NEBULA("Nebula", 400, true, "Gökyüzüne renkli bir bulut serper"),
    BLACK_HOLE("Kara delik", 1000, false, "Evrenin en gizemli köşesi"),
}

data class SkyObject(
    val id: Long = 0,
    val kind: SkyKind,
    val color: Int,
    val name: String,
    val x: Float,
    val y: Float,
    val cost: Int,
    val date: LocalDate,
)
