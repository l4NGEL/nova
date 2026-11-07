package com.l4ngel.nova.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.l4ngel.nova.domain.HabitProgress
import com.l4ngel.nova.domain.ScheduleType
import com.l4ngel.nova.domain.TEMPLATES
import com.l4ngel.nova.domain.dayScore
import com.l4ngel.nova.domain.goalLabel
import com.l4ngel.nova.domain.label
import com.l4ngel.nova.domain.shortTr
import java.time.LocalDate

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TodayScreen(vm: HabitViewModel, onOpen: (Long) -> Unit, onAdd: () -> Unit, onRewards: () -> Unit) {
    val today by vm.today.collectAsStateWithLifecycle()
    val rewards by vm.rewards.collectAsStateWithLifecycle()
    val weekStart by vm.weekStart.collectAsStateWithLifecycle()
    val loaded by vm.progress.collectAsStateWithLifecycle()
    val progress = loaded ?: return
    var selectedDay by rememberSaveable(today) { mutableStateOf(today.toEpochDay()) }
    val selected = LocalDate.ofEpochDay(selectedDay)
    var editing by remember { mutableStateOf<HabitProgress?>(null) }

    val planned = progress.filter { it.habit.schedule.isPlanned(selected) && !selected.isBefore(it.start) }
    val other = progress.filter { it !in planned && !selected.isBefore(it.start) }
    val score = dayScore(progress, selected)

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        floatingActionButton = {
            if (progress.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = onAdd,
                    icon = { Icon(Icons.Rounded.Add, null) },
                    text = { Text("Yeni alışkanlık") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "header") {
                Row(Modifier.padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            when (selected) { today -> "Bugün"; today.minusDays(1) -> "Dün"; else -> selected.dayOfWeek.shortTr() + ", " + selected.dayOfMonth },
                            style = MaterialTheme.typography.headlineLarge,
                        )
                        Text(selected.longLabel(), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    rewards?.let { LevelChip(it, onRewards) }
                }
            }
            item(key = "week") {
                WeekStrip(today, selected, weekStart, progress) { selectedDay = it.toEpochDay() }
            }
            if (progress.isEmpty()) {
                item(key = "empty") { Onboarding(onAdd = onAdd, onTemplate = vm::addTemplate) }
                return@LazyColumn
            }
            item(key = "summary") { SummaryCard(score.done, score.due) }
            rewards?.goal?.let { goal ->
                item(key = "goal") { GoalCard(goal, rewards!!.wallet.balance, onRewards) }
            }
            // Oyun kartları yalnız bugüne bakar; geçmiş gün seçiliyken gizli.
            val game = rewards.takeIf { selected == today }
            game?.boss?.let { boss -> item(key = "boss") { BossCard(boss, today) } }
            items(planned, key = { it.habit.id }) { p ->
                HabitRow(p, selected, today, onOpen = { onOpen(p.habit.id) }, vm = vm, onEditValue = { editing = p }, modifier = Modifier.animateItem())
            }
            if (other.isNotEmpty()) {
                item(key = "other") {
                    Text(
                        "Bugün planda olmayanlar",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 14.dp, bottom = 2.dp),
                    )
                }
                items(other, key = { "o" + it.habit.id }) { p ->
                    HabitRow(p, selected, today, onOpen = { onOpen(p.habit.id) }, vm = vm, onEditValue = { editing = p }, dimmed = true, modifier = Modifier.animateItem())
                }
            }
            if (!game?.quests.isNullOrEmpty()) {
                item(key = "quests") { QuestsCard(game!!.quests, Modifier.padding(top = 8.dp)) }
            }
        }
    }

    editing?.let { p ->
        ValueDialog(
            name = p.habit.name,
            unit = p.habit.unit,
            target = p.habit.target,
            initial = p.value(selected),
            onDismiss = { editing = null },
            onSave = { vm.setValue(p.habit, selected, it); editing = null },
        )
    }
}

@Composable
private fun WeekStrip(today: LocalDate, selected: LocalDate, weekStart: java.time.DayOfWeek, progress: List<HabitProgress>, onSelect: (LocalDate) -> Unit) {
    val first = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(weekStart))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (i in 0L until 7L) {
            val d = first.plusDays(i)
            val future = d.isAfter(today)
            val isSel = d == selected
            val score = if (future) null else dayScore(progress, d)
            Surface(
                onClick = { onSelect(d) },
                enabled = !future,
                shape = MaterialTheme.shapes.small,
                color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLowest,
                modifier = Modifier.weight(1f).alpha(if (future) 0.45f else 1f),
            ) {
                Column(Modifier.padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    val fg = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    Text(d.dayOfWeek.shortTr(), style = MaterialTheme.typography.labelSmall, color = fg)
                    Spacer(Modifier.height(6.dp))
                    val ringColor = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                    ProgressRing(
                        score?.fraction ?: 0f,
                        ringColor,
                        Modifier.size(32.dp),
                        stroke = 3.dp,
                        track = ringColor.copy(alpha = 0.2f),
                    ) {
                        Text(
                            "${d.dayOfMonth}",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

/** Başlıktaki seviye ve bakiye; dokununca ödüller sekmesine gider. */
@Composable
private fun LevelChip(state: RewardState, onClick: () -> Unit) {
    val level = state.wallet.level
    Surface(onClick = onClick, shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Row(Modifier.padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(level.fraction, MaterialTheme.colorScheme.tertiary, Modifier.size(40.dp), stroke = 3.dp) {
                LevelMedal(level.number, Modifier.size(32.dp))
            }
            Spacer(Modifier.width(8.dp))
            Column {
                Text("$POINT ${state.wallet.balance.grouped()}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(level.title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Hedef seçilen ödüle ne kadar kaldı; yetince ödüller sekmesine çağırır. */
@Composable
private fun GoalCard(goal: com.l4ngel.nova.domain.Reward, balance: Int, onClick: () -> Unit) {
    val left = goal.cost - balance
    Surface(onClick = onClick, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(goal.emoji, fontSize = 30.sp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (left <= 0) "Hak ettin: ${goal.name}" else "Hedefin: ${goal.name}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { (balance.toFloat() / goal.cost).coerceIn(0f, 1f) },
                    color = MaterialTheme.colorScheme.tertiary,
                    trackColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f),
                    strokeCap = StrokeCap.Round,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (left <= 0) "Ödülünü almak için dokun" else "${left.grouped()} puan kaldı",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
                )
            }
        }
    }
}

@Composable
private fun SummaryCard(done: Int, due: Int) {
    val fraction = if (due == 0) 0f else done.toFloat() / due
    val (title, body) = when {
        due == 0 -> "Bugün dinlenme günü" to "Planlı alışkanlık yok; istersen yine de işaretleyebilirsin."
        done == due -> "Hepsi tamam!" to "Bugünün tüm alışkanlıklarını bitirdin. Evrenin bugün parladı."
        done == 0 -> "Güne başla" to "$due alışkanlık seni bekliyor. İlk adım en kolayı."
        fraction >= 0.5f -> "Yarıyı geçtin" to "${due - done} tane kaldı, bitiş çizgisi göründü."
        else -> "İyi gidiyor" to "$done / $due tamamlandı. Devam."
    }
    Surface(
        Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(fraction, MaterialTheme.colorScheme.primary, Modifier.size(76.dp), stroke = 9.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$done/$due", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.height(4.dp))
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
            }
        }
    }
}

@Composable
private fun HabitRow(
    p: HabitProgress,
    date: LocalDate,
    today: LocalDate,
    onOpen: () -> Unit,
    vm: HabitViewModel,
    onEditValue: () -> Unit,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false,
) {
    val h = p.habit
    val color = Color(h.color)
    val value = p.value(date)
    val done = p.isDone(date)
    val streak = p.currentStreak(today)
    val fade by animateFloatAsState(if (dimmed && !done) 0.6f else 1f, label = "fade")
    Surface(
        onClick = onOpen,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = modifier.fillMaxWidth().alpha(fade),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            HabitBubble(h.icon, color)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(h.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (streak > 0) {
                        Icon(Icons.Rounded.LocalFireDepartment, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(16.dp))
                        Text(
                            "$streak ${p.streakUnit}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.tertiary,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text("  ·  ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    val detail = when {
                        h.isCounted -> "$value / ${h.goalLabel()}"
                        h.schedule.type == ScheduleType.TIMES_PER_WEEK -> "Bu hafta ${p.doneInWeek(date)}/${h.schedule.timesPerWeek}"
                        else -> h.schedule.label()
                    }
                    Text(detail, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.width(10.dp))
            if (h.isCounted) {
                CountButton(
                    value = value,
                    target = h.target,
                    color = color,
                    enabled = true,
                    onIncrement = { if (done) onEditValue() else vm.increment(h, date, value) },
                    onLongPress = onEditValue,
                    label = "${h.name}: $value / ${h.target}, bir artır",
                )
            } else {
                CheckButton(done, color, enabled = true, onToggle = { vm.toggle(h, date, value) }, label = if (done) "${h.name} tamamlandı, geri al" else "${h.name} tamamla")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Onboarding(onAdd: () -> Unit, onTemplate: (com.l4ngel.nova.domain.HabitTemplate) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        EmptyHint(
            Icons.Rounded.AutoAwesome,
            "Evrenini kur",
            "Her alışkanlık gökyüzünde bir galaksi olur, tamamladığın her gün ona bir yıldız ekler. Hazır bir alışkanlıkla başla ya da kendininkini oluştur.",
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            TEMPLATES.forEach { t ->
                AssistChip(
                    onClick = { onTemplate(t) },
                    label = { Text(t.name) },
                    leadingIcon = { Icon(habitIcon(t.icon), null, tint = Color(t.color), modifier = Modifier.size(AssistChipDefaults.IconSize)) },
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        ExtendedFloatingActionButton(
            onClick = onAdd,
            icon = { Icon(Icons.Rounded.Add, null) },
            text = { Text("Kendi alışkanlığını oluştur") },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

/** Sayılı hedefte değeri elle girme; geçmiş günü düzeltmek için de kullanılır. */
@Composable
fun ValueDialog(name: String, unit: String, target: Int, initial: Int, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial.toString()) }
    val value = text.toIntOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(name) },
        text = {
            Column {
                Text("Hedef: $target $unit".trim(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { text = ((value ?: 0) - 1).coerceAtLeast(0).toString() }) { Text("−", style = MaterialTheme.typography.headlineSmall) }
                    OutlinedTextField(
                        value = text,
                        onValueChange = { s -> text = s.filter(Char::isDigit).take(4) },
                        singleLine = true,
                        suffix = { if (unit.isNotBlank()) Text(unit) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { text = ((value ?: 0) + 1).toString() }) { Text("+", style = MaterialTheme.typography.headlineSmall) }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { text = target.toString() }) { Text("Hedefi tamamla") }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(value ?: 0) }, enabled = value != null) { Text("Kaydet") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
    )
}
