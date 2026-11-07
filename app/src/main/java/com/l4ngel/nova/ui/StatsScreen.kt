package com.l4ngel.nova.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.l4ngel.nova.domain.dayScore
import com.l4ngel.nova.domain.shortTr
import com.l4ngel.nova.domain.weekDays
import com.l4ngel.nova.domain.weekdayDistribution
import kotlin.math.roundToInt

private val PERIODS = listOf(7 to "7 gün", 30 to "30 gün", 90 to "90 gün")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(vm: HabitViewModel, onOpen: (Long) -> Unit) {
    val loaded by vm.progress.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()
    val weekStart by vm.weekStart.collectAsStateWithLifecycle()
    val progress = loaded ?: return
    var period by rememberSaveable { mutableIntStateOf(30) }

    Scaffold(contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("İstatistikler", style = MaterialTheme.typography.headlineLarge)
            if (progress.isEmpty()) {
                EmptyHint(Icons.Rounded.Insights, "Henüz veri yok", "Bir alışkanlık ekleyip işaretlemeye başladığında ilerlemen burada görünecek.")
                return@Column
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                PERIODS.forEachIndexed { i, (days, label) ->
                    SegmentedButton(
                        selected = period == days,
                        onClick = { period = days },
                        shape = SegmentedButtonDefaults.itemShape(i, PERIODS.size),
                    ) { Text(label) }
                }
            }

            val stats = remember(progress, today, period) {
                val dates = (period - 1 downTo 0).map { today.minusDays(it.toLong()) }
                val scores = dates.map { dayScore(progress, it) }
                // Bugün henüz bitmediği için genel oranı düşürmesin; yalnız tamamlananlar sayılır.
                val counted = scores.dropLast(1) + scores.last().let { s -> listOf(s.copy(due = s.done)) }
                val due = counted.sumOf { it.due }
                val done = counted.sumOf { it.done }
                Triple(dates, scores, if (due == 0) null else done.toFloat() / due)
            }
            val (dates, scores, overall) = stats
            val best = progress.maxByOrNull { it.currentStreak(today) }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(overall?.let { "%${(it * 100).roundToInt()}" } ?: "—", "Genel başarı", Icons.Rounded.TrendingUp, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                StatTile("${scores.sumOf { it.done }}", "Tamamlanan", Icons.Rounded.CheckCircle, Color(0xFF19A974), Modifier.weight(1f))
            }
            if (best != null && best.currentStreak(today) > 0) {
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Icon(Icons.Rounded.LocalFireDepartment, null, tint = MaterialTheme.colorScheme.tertiary)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Şu an süren en uzun serin", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                            Text(
                                "${best.habit.name} · ${best.currentStreak(today)} ${best.streakUnit}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        }
                    }
                }
            }

            SectionCard(title = "Günlük tamamlama") {
                if (period <= 30) {
                    BarChart(
                        scores.map { it.fraction },
                        dates.map { if (period == 7) it.dayOfWeek.shortTr() else "${it.dayOfMonth}" },
                        MaterialTheme.colorScheme.primary,
                        labelEvery = if (period == 7) 1 else 5,
                    )
                } else {
                    // 90 günde günlük çubuk okunmaz; haftalık ortalamaya topla.
                    val weekly = scores.chunked(7).map { w -> w.sumOf { it.done }.toFloat() / w.sumOf { it.due }.coerceAtLeast(1) }
                    val labels = dates.chunked(7).map { "${it.first().dayOfMonth}.${it.first().monthValue}" }
                    BarChart(weekly, labels, MaterialTheme.colorScheme.primary, labelEvery = 3)
                }
            }

            SectionCard(title = "Alışkanlıklar") {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    progress
                        .map { it to (it.completionRate(period, today) ?: 0f) }
                        .sortedByDescending { it.second }
                        .forEach { (p, rate) ->
                            val c = Color(p.habit.color)
                            Surface(onClick = { onOpen(p.habit.id) }, color = Color.Transparent) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    HabitBubble(p.habit.icon, c, size = 36.dp)
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Row {
                                            Text(p.habit.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text("%${(rate * 100).roundToInt()}", style = MaterialTheme.typography.labelLarge, color = c)
                                        }
                                        Spacer(Modifier.height(6.dp))
                                        LinearProgressIndicator(
                                            progress = { rate },
                                            color = c,
                                            trackColor = c.copy(alpha = 0.15f),
                                            strokeCap = StrokeCap.Round,
                                            gapSize = 0.dp,
                                            drawStopIndicator = {},
                                            modifier = Modifier.fillMaxWidth().height(6.dp),
                                        )
                                    }
                                }
                            }
                        }
                }
            }

            SectionCard(title = "Haftanın günleri") {
                val dist = remember(progress, today, period) { weekdayDistribution(progress, maxOf(period, 28), today) }
                val days = weekDays(weekStart)
                val max = dist.values.maxOrNull()?.coerceAtLeast(1) ?: 1
                BarChart(days.map { dist.getValue(it).toFloat() / max }, days.map { it.shortTr() }, MaterialTheme.colorScheme.tertiary, highlightLast = false)
                Spacer(Modifier.height(8.dp))
                val top = days.maxByOrNull { dist.getValue(it) }
                if (top != null && dist.getValue(top) > 0) {
                    Text(
                        "En verimli günün: ${top.shortTr()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(80.dp))
        }
    }
}
