package com.l4ngel.nova.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.l4ngel.nova.domain.Level
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/** Puan simgesi; uygulama genelinde aynı işaret kullanılsın. */
const val POINT = "✦"

/**
 * Uygulamanın üstünde duran kutlama katmanı: kazanılan puan kısa bir rozetle yukarıdan süzülür,
 * kusursuz gün ve ödül almada konfeti yağar, seviye atlayınca pencere açılır. Ayarlardan kapatılabilir.
 */
@Composable
fun BoxScope.CelebrationLayer(vm: HabitViewModel) {
    val enabled by vm.celebrationsOn.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current
    var confetti by remember { mutableIntStateOf(0) }
    var pill by remember { mutableStateOf<Pill?>(null) }
    var level by remember { mutableStateOf<Level?>(null) }

    LaunchedEffect(enabled) {
        if (!enabled) return@LaunchedEffect
        vm.celebrations.collect { c ->
            when (c) {
                // Peş peşe dokunuşlar tek rozette toplansın.
                is Celebration.Points -> pill = Pill((pill?.amount ?: 0) + c.amount, (pill?.key ?: 0) + 1)
                is Celebration.LevelUp -> { level = c.level; confetti++; haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
                Celebration.PerfectDay -> { confetti++; haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
                is Celebration.Redeemed, is Celebration.SkyAdded -> confetti++
                is Celebration.BossDefeated -> { confetti++; haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
            }
        }
    }
    LaunchedEffect(pill?.key) {
        if (pill == null) return@LaunchedEffect
        delay(1400)
        pill = null
    }

    Confetti(confetti, Modifier.fillMaxSize())
    PointsPill(pill?.amount, Modifier.align(Alignment.TopCenter))
    level?.let { LevelUpDialog(it) { level = null } }
}

private data class Pill(val amount: Int, val key: Int)

@Composable
private fun PointsPill(amount: Int?, modifier: Modifier) {
    // Kaybolurken son değer görünmeye devam etsin.
    var last by remember { mutableIntStateOf(0) }
    if (amount != null) last = amount
    AnimatedVisibility(
        amount != null,
        modifier = modifier.windowInsetsPadding(WindowInsets.safeDrawing).padding(top = 12.dp),
        enter = slideInVertically(spring(Spring.DampingRatioMediumBouncy)) { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
    ) {
        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.tertiary, shadowElevation = 6.dp) {
            Row(Modifier.padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Bolt, null, tint = MaterialTheme.colorScheme.onTertiary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text("+$last puan", color = MaterialTheme.colorScheme.onTertiary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun LevelUpDialog(level: Level, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Box(contentAlignment = Alignment.Center) {
                val scale = remember { Animatable(0.4f) }
                LaunchedEffect(Unit) { scale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)) }
                LevelMedal(level.number, Modifier.size(96.dp * scale.value))
            }
        },
        title = { Text("Seviye atladın!", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Artık ${level.title} seviyesindesin.", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Sonraki seviye için ${level.span} puan gerekiyor. Evrenin büyümeye devam etsin!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Harika") } },
    )
}

private class Particle(val x: Float, val vx: Float, val vy: Float, val size: Float, val spin: Float, val phase: Float, val color: Color, val round: Boolean)

/** [trigger] her arttığında ekranın üstünden ~2,5 sn süren konfeti yağar. */
@Composable
fun Confetti(trigger: Int, modifier: Modifier = Modifier) {
    if (trigger == 0) return
    val particles = remember(trigger) {
        val r = Random(trigger * 7919L + System.nanoTime())
        List(110) {
            Particle(
                x = r.nextFloat(),
                vx = (r.nextFloat() - 0.5f) * 0.25f,
                vy = 0.15f + r.nextFloat() * 0.35f,
                size = 6f + r.nextFloat() * 8f,
                spin = (r.nextFloat() - 0.5f) * 720f,
                phase = r.nextFloat() * 2f * PI.toFloat(),
                color = Color(HABIT_COLORS[r.nextInt(HABIT_COLORS.size)]),
                round = r.nextInt(4) == 0,
            )
        }
    }
    val t = remember(trigger) { Animatable(0f) }
    LaunchedEffect(trigger) { t.animateTo(1f, tween(2600, easing = LinearEasing)) }
    if (t.value >= 1f) return
    Canvas(modifier) {
        val time = t.value * 2.6f
        val alpha = if (t.value > 0.75f) (1f - t.value) / 0.25f else 1f
        for (p in particles) {
            // Yukarıdan atılır, yerçekimiyle hızlanır, yanlara salınır.
            val y = -0.08f + p.vy * time + 0.22f * time * time
            val x = p.x + p.vx * time + 0.03f * sin(time * 4f + p.phase)
            val c = Offset(x * size.width, y * size.height)
            val s = p.size * density / 2.5f
            if (p.round) {
                drawCircle(p.color.copy(alpha = alpha), s / 2, c)
            } else {
                rotate(p.spin * time, c) {
                    drawRect(p.color.copy(alpha = alpha), Offset(c.x - s / 2, c.y - s / 4), Size(s, s / 2))
                }
            }
        }
    }
}
