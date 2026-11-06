package com.l4ngel.ritim.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.l4ngel.ritim.domain.GALAXY_ARMS
import com.l4ngel.ritim.domain.Galaxy
import com.l4ngel.ritim.domain.SKY_HEIGHT
import com.l4ngel.ritim.domain.SkyKind
import com.l4ngel.ritim.domain.SkyLayout
import com.l4ngel.ritim.domain.SkyObject
import com.l4ngel.ritim.domain.Star
import com.l4ngel.ritim.domain.hash01
import java.time.LocalDate
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Uzay iki temada da karanlık; tema rengi değil sabit gece tonları. */
val SPACE_TOP = Color(0xFF0A0C26)
private val SPACE_MID = Color(0xFF16103F)
private val SPACE_BOTTOM = Color(0xFF2A0E3A)

/** Kamera: dikey eksen çevresinde [yaw], yatay eksen çevresinde [pitch] dönüş (radyan), [zoom] yakınlık. */
@Immutable
data class SkyCamera(val yaw: Float = 0f, val pitch: Float = 0f, val zoom: Float = 1f) {
    val isFront: Boolean get() = abs(yaw) < 0.01f && abs(pitch) < 0.01f && abs(zoom - 1f) < 0.01f
}

/** Kameranın evren merkezine uzaklığı (gökyüzü genişliği biriminde); küçüldükçe perspektif sertleşir. */
private const val CAM_DIST = 1.7f

private class Projected(val x: Float, val y: Float, val scale: Float, val depth: Float) {
    val offset: Offset get() = Offset(x, y)
}

/**
 * 3B evreni ekrana düşürür. Önden bakışta (yaw = pitch = 0, zoom = 1) z = 0 düzlemi 2B yerleşimle birebir
 * örtüşür; yerleştirme bu yüzden önden bakışta yapılır.
 */
private class Projector(size: Size, cam: SkyCamera) {
    val unit = min(size.width, size.height / SKY_HEIGHT)
    private val cx = size.width / 2
    private val cy = size.height / 2
    private val cyaw = cos(cam.yaw)
    private val syaw = sin(cam.yaw)
    private val cp = cos(cam.pitch)
    private val sp = sin(cam.pitch)
    private val zoom = cam.zoom

    fun project(x: Float, y: Float, z: Float): Projected? {
        val wx = x - 0.5f
        val wy = y - SKY_HEIGHT / 2
        val x1 = wx * cyaw + z * syaw
        val z1 = -wx * syaw + z * cyaw
        val y2 = wy * cp - z1 * sp
        val z2 = wy * sp + z1 * cp
        val d = CAM_DIST + z2
        if (d < 0.25f) return null
        val s = zoom * CAM_DIST / d
        return Projected(cx + x1 * s * unit, cy + y2 * s * unit, s, z2)
    }

    fun unproject(o: Offset): Pair<Float, Float> = ((o.x - cx) / (unit * zoom) + 0.5f) to ((o.y - cy) / (unit * zoom) + SKY_HEIGHT / 2)
}

private class Point3(val x: Float, val y: Float, val z: Float)

/** Galaksinin derinliği ve diskinin eğimi kimlikten gelir; her açılışta aynı. */
private fun galaxyDepth(id: Long) = (hash01(id, 13) - 0.5f) * 0.7f

private fun star3d(s: Star, g: Galaxy): Point3 {
    val incl = 0.45f + hash01(g.habitId, 11) * 0.75f
    val az = hash01(g.habitId, 12) * 2f * PI.toFloat()
    val dx = s.x - g.x
    val dy = s.y - g.y
    // Diski yatay eksen çevresinde eğ, sonra bakış ekseni çevresinde çevir.
    val ry = dy * cos(incl)
    val rz = dy * sin(incl)
    val ax = dx * cos(az) - ry * sin(az)
    val ay = dx * sin(az) + ry * cos(az)
    val thickness = (hash01(g.habitId, s.index + 500L) - 0.5f) * 0.012f
    return Point3(g.x + ax, g.y + ay, galaxyDepth(g.habitId) + rz + thickness)
}

private class Dust3(val p: Point3, val r: Float, val a: Float, val phase: Float)

/** Uzaktaki yıldızlar sönük; sis derinlikle artar. */
private fun fog(depth: Float) = (1.2f - 0.55f * (depth + 0.4f)).coerceIn(0.3f, 1f)

/**
 * Kullanıcının evreni, perspektifle çizilir. [camera] verilmezse kendi kendine hafifçe salınır (kart görünümü).
 * [onTap] verilirse dokunulan noktanın z = 0 düzlemindeki karşılığı bildirilir (yerleştirme, önden bakışta),
 * [onObject] verilirse dokunulan gök cismi.
 */
@Composable
fun SkyCanvas(
    sky: SkyLayout,
    objects: List<SkyObject>,
    today: LocalDate,
    modifier: Modifier = Modifier,
    labels: Boolean = false,
    camera: SkyCamera? = null,
    onTap: ((Float, Float) -> Unit)? = null,
    onObject: ((SkyObject) -> Unit)? = null,
) {
    val dust = remember {
        List(260) { i ->
            val k = i.toLong()
            Dust3(
                Point3(-0.7f + hash01(k, 1) * 2.4f, -0.6f + hash01(k, 2) * (SKY_HEIGHT + 1.2f), -0.8f + hash01(k, 6) * 3.2f),
                0.5f + hash01(k, 3) * 0.9f,
                0.2f + hash01(k, 4) * 0.5f,
                hash01(k, 5) * 6.28f,
            )
        }
    }
    val galaxies = remember(sky) { sky.galaxies.associateBy { it.habitId } }
    val stars3 = remember(sky) { sky.stars.map { s -> s to star3d(s, galaxies.getValue(s.habitId)) } }
    val transition = rememberInfiniteTransition(label = "sky")
    val time by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Restart), label = "t")
    val sway by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(26000, easing = LinearEasing), RepeatMode.Restart), label = "sway")
    val cam = camera ?: SkyCamera(yaw = 0.32f * sin(sway * 2f * PI.toFloat()), pitch = 0.16f * sin(sway * 2f * PI.toFloat() * 2f + 1f))
    val currentCam by rememberUpdatedState(cam)
    val measurer = rememberTextMeasurer()
    val recent = today.minusDays(6)

    val tapModifier = if (onTap == null && onObject == null) Modifier else Modifier.pointerInput(objects, onTap, onObject) {
        detectTapGestures { o ->
            val pr = Projector(Size(size.width.toFloat(), size.height.toFloat()), currentCam)
            if (onTap != null) {
                val (x, y) = pr.unproject(o)
                onTap(x.coerceIn(0.03f, 0.97f), y.coerceIn(0.03f, SKY_HEIGHT - 0.03f))
                return@detectTapGestures
            }
            objects
                .mapNotNull { ob -> pr.project(ob.x, ob.y, objectDepth(ob.kind))?.let { ob to (it.offset - o).getDistance() } }
                .minByOrNull { it.second }
                ?.takeIf { it.second < 34 * density }
                ?.let { onObject?.invoke(it.first) }
        }
    }

    Canvas(modifier.clipToBounds().then(tapModifier)) {
        val pr = Projector(size, cam)
        val u = pr.unit
        val wave = time * 2f * PI.toFloat()
        drawRect(Brush.verticalGradient(listOf(SPACE_TOP, SPACE_MID, SPACE_BOTTOM)))

        for (d in dust) {
            val p = pr.project(d.p.x, d.p.y, d.p.z) ?: continue
            val tw = 0.75f + 0.25f * sin(wave * 2 + d.phase)
            drawCircle(Color.White.copy(alpha = d.a * tw * fog(d.p.z)), d.r * density * (0.4f + 0.6f * p.scale), p.offset)
        }

        // Uzaktan yakına: önde kalan arkadakini örtsün.
        val layers = ArrayList<Pair<Float, DrawScope.() -> Unit>>(stars3.size + objects.size + sky.galaxies.size)

        for (g in sky.galaxies) {
            val p = pr.project(g.x, g.y, galaxyDepth(g.habitId)) ?: continue
            val c = Color(g.color)
            val reach = u * p.scale * (0.05f + 0.09f * min(1f, sqrt(g.stars / 120f)))
            val a = (if (g.archived) 0.08f else 0.2f) * fog(p.depth)
            // Hale en arkada; çekirdek kendi derinliğinde.
            layers += 10f to { drawCircle(Brush.radialGradient(listOf(c.copy(alpha = a), Color.Transparent), p.offset, reach * 1.6f), reach * 1.6f, p.offset) }
            val core = u * p.scale * (0.008f + 0.012f * min(1f, sqrt(g.stars / 120f)))
            val coreAlpha = (if (g.archived) 0.3f else 0.85f) * fog(p.depth)
            layers += p.depth to {
                drawCircle(
                    Brush.radialGradient(listOf(Color.White.copy(alpha = coreAlpha), lerp(c, Color.White, 0.4f).copy(alpha = 0.3f * coreAlpha), Color.Transparent), p.offset, core * 2.5f),
                    core * 2.5f,
                    p.offset,
                )
            }
        }

        // Takımyıldızlar: süren serinin yıldızları her kol boyunca birbirine bağlanır.
        val pos = HashMap<Pair<Long, Int>, Projected>(stars3.size)
        for ((s, q) in stars3) pr.project(q.x, q.y, q.z)?.let { pos[s.habitId to s.index] = it }
        for (g in sky.galaxies) {
            val line = sky.constellation(g.habitId)
            if (line.size < 2) continue
            val color = lerp(Color(g.color), Color.White, 0.3f).copy(alpha = 0.32f)
            for ((_, arm) in line.groupBy { it.index % GALAXY_ARMS }) {
                val pts = arm.mapNotNull { pos[it.habitId to it.index] }
                if (pts.size < 2) continue
                val path = Path()
                pts.forEachIndexed { i, p -> if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y) }
                layers += 9f to { drawPath(path, color, style = Stroke(0.9f * density, cap = StrokeCap.Round)) }
            }
        }

        val archived = sky.galaxies.filter { it.archived }.map { it.habitId }.toSet()
        for ((s, _) in stars3) {
            val p = pos[s.habitId to s.index] ?: continue
            val base = Color(s.color)
            val core = lerp(base, Color.White, 0.5f)
            val r = (0.9f + s.size * 0.9f) * density * (0.45f + 0.55f * p.scale)
            val dim = (if (s.habitId in archived) 0.35f else 1f) * fog(p.depth)
            val haze = u * 0.016f * p.scale
            val fresh = !s.date.isBefore(recent)
            val isToday = s.date == today
            val tw = 0.6f + 0.4f * sin(wave * 3 + s.index * 1.7f)
            layers += p.depth to {
                // Kolların pusu: üst üste binince kol bulutlanır.
                drawCircle(Brush.radialGradient(listOf(base.copy(alpha = 0.12f * dim), Color.Transparent), p.offset, haze), haze, p.offset)
                if (fresh) {
                    // Son haftanın yıldızları parlak ve titrek; bugünkü en parlak.
                    val glow = r * (if (isToday) 5f else 3.2f)
                    drawCircle(Brush.radialGradient(listOf(base.copy(alpha = 0.55f * tw * dim), Color.Transparent), p.offset, glow), glow, p.offset)
                    drawCircle(core.copy(alpha = dim), r * (if (isToday) 1.35f else 1.1f), p.offset)
                } else {
                    drawCircle(core.copy(alpha = 0.75f * dim), r, p.offset)
                }
            }
        }

        for (o in objects) {
            val z = objectDepth(o.kind)
            val p = pr.project(o.x, o.y, z) ?: continue
            layers += (if (o.kind == SkyKind.NEBULA) 8f else p.depth) to { drawSkyObject(o.kind, Color(o.color), p.offset, u * p.scale, wave) }
        }

        layers.sortByDescending { it.first }
        for ((_, draw) in layers) draw()

        if (labels) {
            val style = TextStyle(color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
            for (g in sky.galaxies) {
                val p = pr.project(g.x, g.y, galaxyDepth(g.habitId)) ?: continue
                val t = measurer.measure(g.name, style)
                drawText(t, topLeft = Offset(p.x - t.size.width / 2, p.y + u * p.scale * 0.085f), alpha = fog(p.depth))
            }
            val small = TextStyle(color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp)
            for (o in objects) {
                val p = pr.project(o.x, o.y, objectDepth(o.kind)) ?: continue
                val t = measurer.measure(o.name, small)
                drawText(t, topLeft = Offset(p.x - t.size.width / 2, p.y + u * p.scale * objectRadius(o.kind) + 4 * density))
            }
        }
    }
}

/** Nebula biraz geride dursun ki galaksiler önünden geçsin. */
private fun objectDepth(kind: SkyKind): Float = if (kind == SkyKind.NEBULA) 0.25f else 0f

private fun objectRadius(kind: SkyKind): Float = when (kind) {
    SkyKind.MOON -> 0.02f
    SkyKind.PLANET -> 0.032f
    SkyKind.RINGED -> 0.03f
    SkyKind.COMET -> 0.012f
    SkyKind.NEBULA -> 0.05f
    SkyKind.BLACK_HOLE -> 0.04f
}

/** Tek bir gök cismi; dükkândaki önizleme de bununla çizilir. [u] gökyüzü genişliği (piksel). */
fun DrawScope.drawSkyObject(kind: SkyKind, color: Color, c: Offset, u: Float, wave: Float) {
    when (kind) {
        SkyKind.MOON -> {
            val r = u * 0.02f
            drawCircle(Brush.radialGradient(listOf(Color(0x55E8E4DA), Color.Transparent), c, r * 2.2f), r * 2.2f, c)
            drawCircle(Brush.radialGradient(listOf(Color(0xFFF2EFE8), Color(0xFFB9B4AA)), c - Offset(r * 0.4f, r * 0.4f), r * 1.6f), r, c)
            for ((dx, dy, cr) in listOf(Triple(0.3f, -0.2f, 0.22f), Triple(-0.35f, 0.3f, 0.16f), Triple(0.1f, 0.45f, 0.12f))) {
                drawCircle(Color(0x33000000), r * cr, c + Offset(r * dx, r * dy))
            }
        }
        SkyKind.PLANET -> planet(color, c, u * 0.032f)
        SkyKind.RINGED -> {
            val r = u * 0.03f
            val ring = Size(r * 4.2f, r * 1.3f)
            val ringTop = c - Offset(ring.width / 2, ring.height / 2)
            rotate(-18f, c) {
                // Halkanın arkası gezegenin arkasında, önü önünde kalsın.
                drawArc(lerp(color, Color.White, 0.5f).copy(alpha = 0.8f), 180f, 180f, false, ringTop, ring, style = Stroke(r * 0.28f))
            }
            planet(color, c, r)
            rotate(-18f, c) {
                drawArc(lerp(color, Color.White, 0.6f), 0f, 180f, false, ringTop, ring, style = Stroke(r * 0.28f))
            }
        }
        SkyKind.COMET -> {
            val r = u * 0.009f
            val len = u * 0.11f
            rotate(-35f, c) {
                val tail = Path().apply {
                    moveTo(c.x, c.y - r * 1.2f)
                    lineTo(c.x + len, c.y - r * 0.2f)
                    lineTo(c.x + len, c.y + r * 0.2f)
                    lineTo(c.x, c.y + r * 1.2f)
                    close()
                }
                drawPath(tail, Brush.horizontalGradient(listOf(color.copy(alpha = 0.8f), Color.Transparent), c.x, c.x + len))
            }
            drawCircle(Brush.radialGradient(listOf(lerp(color, Color.White, 0.7f), Color.Transparent), c, r * 3f), r * 3f, c)
            drawCircle(Color.White, r, c)
        }
        SkyKind.NEBULA -> {
            val r = u * 0.13f
            val drift = sin(wave) * u * 0.006f
            val warm = lerp(color, Color(0xFFE0559B), 0.4f)
            val cool = lerp(color, Color(0xFF2BA6DE), 0.4f)
            for ((dx, dy, col) in listOf(Triple(-0.35f, -0.1f, color), Triple(0.3f, 0.2f, warm), Triple(0.05f, -0.35f, cool), Triple(0.1f, 0.3f, color))) {
                val p = c + Offset(r * dx + drift, r * dy - drift)
                drawCircle(Brush.radialGradient(listOf(col.copy(alpha = 0.3f), Color.Transparent), p, r * 0.75f), r * 0.75f, p)
            }
        }
        SkyKind.BLACK_HOLE -> {
            val r = u * 0.022f
            drawCircle(Brush.radialGradient(listOf(Color(0x66FF9A3C), Color.Transparent), c, r * 3.4f), r * 3.4f, c)
            rotate(-15f + sin(wave) * 3f, c) {
                val disk = Size(r * 4.4f, r * 1.4f)
                drawOval(
                    Brush.horizontalGradient(listOf(Color(0xFFFFD27A), Color(0xFFFF7A2E), Color(0xFFFFD27A)), c.x - disk.width / 2, c.x + disk.width / 2),
                    c - Offset(disk.width / 2, disk.height / 2),
                    disk,
                    style = Stroke(r * 0.35f),
                )
            }
            drawCircle(Color(0xFFFFB45A), r * 1.12f, c, style = Stroke(r * 0.18f))
            drawCircle(Color.Black, r, c)
        }
    }
}

private fun DrawScope.planet(color: Color, c: Offset, r: Float) {
    drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.35f), Color.Transparent), c, r * 1.9f), r * 1.9f, c)
    drawCircle(
        Brush.radialGradient(listOf(lerp(color, Color.White, 0.55f), color, lerp(color, Color.Black, 0.55f)), c - Offset(r * 0.45f, r * 0.45f), r * 2f),
        r,
        c,
    )
    // İnce bulut kuşakları.
    translate(0f, 0f) {
        drawArc(Color.White.copy(alpha = 0.12f), 200f, 140f, false, c - Offset(r * 0.8f, r * 0.35f), Size(r * 1.6f, r * 0.5f), style = Stroke(r * 0.12f))
    }
}

/** Dükkân kartlarında ve satın alma penceresinde önizleme. */
@Composable
fun SkyObjectPreview(kind: SkyKind, color: Color, modifier: Modifier = Modifier) {
    val time by rememberInfiniteTransition(label = "preview").animateFloat(
        0f, 1f, infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "t",
    )
    Canvas(modifier.clipToBounds()) {
        drawRect(Brush.radialGradient(listOf(SPACE_MID, SPACE_TOP), center, size.minDimension))
        // Cisim kartı doldursun: gökyüzü birimini nesnenin yarıçapına göre seç.
        val scale = when (kind) {
            SkyKind.MOON -> 14f
            SkyKind.PLANET -> 9f
            SkyKind.RINGED -> 5f
            SkyKind.COMET -> 6f
            SkyKind.NEBULA -> 2.6f
            SkyKind.BLACK_HOLE -> 7f
        }
        val u = size.minDimension * scale
        val c = if (kind == SkyKind.COMET) center - Offset(size.width * 0.18f, -size.height * 0.12f) else center
        drawSkyObject(kind, color, c, u, time * 2f * PI.toFloat())
    }
}
