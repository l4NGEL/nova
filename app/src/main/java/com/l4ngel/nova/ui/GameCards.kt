package com.l4ngel.nova.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Diamond
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.l4ngel.nova.domain.Boss
import com.l4ngel.nova.domain.Quest
import com.l4ngel.nova.domain.QuestRules
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private val HP_RED = Color(0xFFE5484D)
private val WIN_GREEN = Color(0xFF19A974)

/**
 * Haftanın canavarı. Her vuruşta sarsılır ve verilen hasar üstünden süzülür; can bitince yenik düşer.
 * Uzay rengi iki temada da aynı, Evren sekmesiyle bağ kursun.
 */
@Composable
fun BossCard(boss: Boss, today: LocalDate, modifier: Modifier = Modifier) {
    val shake = remember { Animatable(0f) }
    val hit = remember { Animatable(0f) }
    var lastHit by remember { mutableIntStateOf(0) }
    var seen by remember { mutableIntStateOf(boss.damage) }
    LaunchedEffect(boss.damage) {
        val delta = boss.damage - seen
        seen = boss.damage
        if (delta <= 0) return@LaunchedEffect
        lastHit = delta
        launch {
            hit.snapTo(0f)
            hit.animateTo(1f, tween(1400))
        }
        shake.animateTo(0f, keyframes {
            durationMillis = 420
            -14f at 60
            12f at 140
            -8f at 220
            5f at 300
        })
    }
    val hpFraction by animateFloatAsState(boss.fraction, tween(500), label = "hp")
    val beaten = boss.defeated
    val daysLeft = ChronoUnit.DAYS.between(today, boss.week.plusDays(6)).toInt()

    Surface(shape = MaterialTheme.shapes.medium, color = SPACE_TOP, modifier = modifier.fillMaxWidth()) {
        Box(Modifier.background(Brush.horizontalGradient(listOf(SPACE_TOP, Color(0xFF2A0E3A))))) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                    Text(
                        boss.kind.emoji,
                        fontSize = 42.sp,
                        modifier = Modifier
                            .rotate(shake.value)
                            .scale(if (beaten) 0.8f else 1f)
                            .alpha(if (beaten) 0.45f else 1f)
                            .graphicsLayer { translationX = shake.value * 0.6f },
                    )
                    if (beaten) {
                        Box(Modifier.align(Alignment.BottomEnd).size(24.dp).clip(CircleShape).background(WIN_GREEN), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                    // Vuruş sayısı yukarı süzülüp kaybolur.
                    if (hit.value in 0.001f..0.999f) {
                        Text(
                            "−$lastHit",
                            color = Color(0xFFFF8A80),
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            modifier = Modifier.align(Alignment.TopEnd).offset(y = (-28 * hit.value).dp).alpha(if (hit.value < 0.5f) 1f else (1f - hit.value) * 2f),
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (beaten) "Haftanın canavarını yendin!" else "Haftanın canavarı",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (beaten) Color(0xFF7DDBA8) else Color.White.copy(alpha = 0.65f),
                    )
                    Text(boss.kind.name, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (!beaten) {
                        Text(boss.kind.taunt, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.12f))) {
                        Box(
                            Modifier.fillMaxWidth(hpFraction).height(8.dp).clip(RoundedCornerShape(50))
                                .background(Brush.horizontalGradient(listOf(Color(0xFFFF8A3C), HP_RED))),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        when {
                            beaten -> "+${QuestRules.BOSS} puan kazandın · yeni canavar Pazartesi"
                            daysLeft == 0 -> "${boss.hp.grouped()} / ${boss.maxHp.grouped()} can · bugün son gün"
                            else -> "${boss.hp.grouped()} / ${boss.maxHp.grouped()} can · $daysLeft gün kaldı · yenersen +${QuestRules.BOSS}"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}

private val QUEST_ICONS: Map<String, ImageVector> = mapOf(
    "check" to Icons.Rounded.CheckCircle,
    "bolt" to Icons.Rounded.Bolt,
    "target" to Icons.Rounded.TrackChanges,
    "diamond" to Icons.Rounded.Diamond,
    "fire" to Icons.Rounded.LocalFireDepartment,
    "refresh" to Icons.Rounded.Replay,
)

@Composable
fun QuestsCard(quests: List<Quest>, modifier: Modifier = Modifier) {
    val done = quests.count { it.done }
    SectionCard(
        modifier,
        title = "Günün görevleri",
        trailing = { Text("$done / ${quests.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            quests.forEach { q -> QuestRow(q) }
        }
    }
}

@Composable
private fun QuestRow(q: Quest) {
    val pop by animateFloatAsState(if (q.done) 1f else 0.9f, spring(Spring.DampingRatioMediumBouncy), label = "pop")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(36.dp).scale(pop).clip(CircleShape)
                .background(if (q.done) WIN_GREEN else MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (q.done) Icons.Rounded.Check else QUEST_ICONS[q.icon] ?: Icons.Rounded.CheckCircle,
                null,
                tint = if (q.done) Color.White else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                q.title,
                style = MaterialTheme.typography.bodyMedium,
                textDecoration = if (q.done) TextDecoration.LineThrough else null,
                color = if (q.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (q.goal > 1 && !q.done) {
                Spacer(Modifier.height(4.dp))
                Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    Box(Modifier.fillMaxWidth(q.fraction).height(4.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primary))
                }
                Text("${q.current} / ${q.goal}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            "+${q.reward} $POINT",
            style = MaterialTheme.typography.labelLarge,
            color = if (q.done) WIN_GREEN else MaterialTheme.colorScheme.tertiary,
        )
    }
}
