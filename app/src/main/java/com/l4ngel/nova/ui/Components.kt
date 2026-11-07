package com.l4ngel.nova.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** İlerleme halkası; [progress] 0..1, değişince yumuşak geçer. */
@Composable
fun ProgressRing(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    stroke: Dp = 8.dp,
    track: Color = color.copy(alpha = 0.16f),
    content: @Composable () -> Unit = {},
) {
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), tween(600), label = "ring")
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val w = stroke.toPx()
            val inset = w / 2
            val arcSize = Size(size.width - w, size.height - w)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(w))
            if (p > 0f) drawArc(color, -90f, 360f * p, false, Offset(inset, inset), arcSize, style = Stroke(w, cap = StrokeCap.Round))
        }
        content()
    }
}

@Composable
fun HabitBubble(icon: String, color: Color, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(size * 0.32f)).background(color.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(habitIcon(icon), null, tint = color, modifier = Modifier.size(size * 0.55f))
    }
}

/** Tek seferlik alışkanlığın onay düğmesi: boşken çerçeve, yapılınca dolu ve hafif zıplar. */
@Composable
fun CheckButton(done: Boolean, color: Color, enabled: Boolean, onToggle: () -> Unit, label: String) {
    val haptic = LocalHapticFeedback.current
    val bg by animateColorAsState(if (done) color else Color.Transparent, tween(220), label = "bg")
    val scale by animateFloatAsState(if (done) 1f else 0.94f, spring(Spring.DampingRatioMediumBouncy), label = "scale")
    Surface(
        onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onToggle() },
        enabled = enabled,
        shape = CircleShape,
        color = bg,
        border = if (done) null else BorderStroke(2.dp, color.copy(alpha = 0.55f)),
        modifier = Modifier.size(44.dp).scale(scale).semantics { contentDescription = label },
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (done) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(26.dp))
        }
    }
}

/** Sayılı hedef düğmesi: dokununca +1, basılı tutunca değer girme penceresi. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CountButton(value: Int, target: Int, color: Color, enabled: Boolean, onIncrement: () -> Unit, onLongPress: () -> Unit, label: String) {
    val haptic = LocalHapticFeedback.current
    val done = value >= target
    val bg by animateColorAsState(if (done) color else Color.Transparent, tween(220), label = "bg")
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(bg)
            .combinedClickable(
                enabled = enabled,
                role = Role.Button,
                onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onIncrement() },
                onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onLongPress() },
            )
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        if (done) {
            Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(26.dp))
        } else {
            ProgressRing(value.toFloat() / target, color, Modifier.size(48.dp), stroke = 4.dp) {
                Text(
                    "$value",
                    style = MaterialTheme.typography.labelLarge,
                    color = color,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
fun SectionCard(modifier: Modifier = Modifier, title: String? = null, trailing: @Composable () -> Unit = {}, content: @Composable () -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Column(Modifier.padding(18.dp)) {
            if (title != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    trailing()
                }
                Spacer(Modifier.height(12.dp))
            }
            content()
        }
    }
}

@Composable
fun StatTile(value: String, label: String, icon: ImageVector, tint: Color, modifier: Modifier = Modifier) {
    Surface(modifier, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Column(Modifier.padding(16.dp)) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(10.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Gün hücresi: dolu daire = tamamlandı, kısmi halka = sayılı hedefte ilerleme, çerçeve = kaçırıldı. */
@Composable
fun DayCell(
    day: Int,
    fraction: Float,
    color: Color,
    missed: Boolean,
    isToday: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val done = fraction >= 1f
    val bg by animateColorAsState(if (done) color else Color.Transparent, tween(200), label = "cell")
    val textColor = when {
        done -> Color.White
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
        else -> MaterialTheme.colorScheme.onSurface
    }
    Box(modifier.aspectRatio(1f).padding(3.dp), contentAlignment = Alignment.Center) {
        Surface(
            onClick = onClick,
            enabled = enabled,
            shape = CircleShape,
            color = bg,
            border = when {
                isToday && !done -> BorderStroke(2.dp, color)
                missed && fraction <= 0f -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                else -> null
            },
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (!done && fraction > 0f) ProgressRing(fraction, color, Modifier.fillMaxWidth().aspectRatio(1f), stroke = 3.dp)
                Text(
                    "$day",
                    color = textColor,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isToday || done) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

/** Basit çubuk grafik; değerler 0..1. Etiketler yalnız [labelEvery] adımda bir yazılır. */
@Composable
fun BarChart(values: List<Float>, labels: List<String>, color: Color, modifier: Modifier = Modifier, labelEvery: Int = 1, highlightLast: Boolean = true) {
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(140.dp)) {
            if (values.isEmpty()) return@Canvas
            val gap = if (values.size > 40) 2.dp.toPx() else 6.dp.toPx()
            val barW = (size.width - gap * (values.size - 1)) / values.size
            val r = CornerRadius(minOf(barW / 2, 6.dp.toPx()))
            values.forEachIndexed { i, v ->
                val x = i * (barW + gap)
                drawRoundRect(track, Offset(x, 0f), Size(barW, size.height), r)
                val h = size.height * v.coerceIn(0f, 1f)
                if (h > 0f) {
                    val c = if (highlightLast && i != values.lastIndex) color.copy(alpha = 0.75f) else color
                    drawRoundRect(c, Offset(x, size.height - h), Size(barW, h), r)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            labels.forEachIndexed { i, l ->
                Text(
                    if (i % labelEvery == 0) l else "",
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

/** GitHub tarzı ısı haritası: sütun = hafta, satır = haftanın günü. */
@Composable
fun Heatmap(weeks: List<List<Float?>>, color: Color, rowLabels: List<String>, modifier: Modifier = Modifier) {
    val empty = MaterialTheme.colorScheme.surfaceContainerHigh
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(Modifier.width(26.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            rowLabels.forEachIndexed { i, l ->
                Box(Modifier.height(14.dp), contentAlignment = Alignment.CenterStart) {
                    Text(if (i % 2 == 0) l else "", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            weeks.forEach { week ->
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    week.forEach { v ->
                        val c = when {
                            v == null -> Color.Transparent
                            v <= 0f -> empty
                            else -> color.copy(alpha = 0.25f + 0.75f * v)
                        }
                        Box(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(3.dp)).background(c))
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyHint(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(88.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(44.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

/** Renk seçici yuvarlağı; seçiliyken içte beyaz onay ve dışta halka. */
@Composable
fun ColorDot(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .border(if (selected) 3.dp else 0.dp, if (selected) color.copy(alpha = 0.4f) else Color.Transparent, CircleShape)
            .padding(if (selected) 5.dp else 2.dp)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Surface(onClick = onClick, color = Color.Transparent, modifier = Modifier.matchParentSize()) {}
        if (selected) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
    }
}
