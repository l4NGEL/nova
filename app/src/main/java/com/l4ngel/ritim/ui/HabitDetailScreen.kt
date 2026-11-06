package com.l4ngel.ritim.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Percent
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.l4ngel.ritim.domain.HabitProgress
import com.l4ngel.ritim.domain.goalLabel
import com.l4ngel.ritim.domain.label
import com.l4ngel.ritim.domain.shortTr
import com.l4ngel.ritim.domain.weekDays
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val TIME = DateTimeFormatter.ofPattern("HH:mm")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitDetailScreen(vm: HabitViewModel, habitId: Long, onBack: () -> Unit, onEdit: () -> Unit) {
    val all by vm.all.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()
    val weekStart by vm.weekStart.collectAsStateWithLifecycle()
    val item = all?.firstOrNull { it.habit.id == habitId }
    if (item == null) {
        // Silindiyse ya da yedekten geri yüklemede kaybolduysa geri dön.
        if (all != null) androidx.compose.runtime.LaunchedEffect(Unit) { onBack() }
        return
    }
    val p = remember(item, weekStart) { HabitProgress(item.habit, item.checks, weekStart) }
    val h = p.habit
    val color = Color(h.color)
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var editingDay by remember { mutableStateOf<LocalDate?>(null) }
    var monthOffset by rememberSaveable { mutableStateOf(0L) }
    val month = YearMonth.from(today).minusMonths(monthOffset)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Geri") } },
                actions = {
                    IconButton(onClick = onEdit) { Icon(Icons.Rounded.EditNote, "Düzenle") }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Diğer") }
                        DropdownMenu(menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text(if (h.archived) "Arşivden çıkar" else "Arşivle") },
                                leadingIcon = { Icon(if (h.archived) Icons.Rounded.Unarchive else Icons.Rounded.Archive, null) },
                                onClick = {
                                    menu = false
                                    vm.setArchived(h, !h.archived)
                                    if (!h.archived) onBack()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Sil") },
                                leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                                onClick = { menu = false; confirmDelete = true },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HabitBubble(h.icon, color, size = 64.dp)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(h.name, style = MaterialTheme.typography.headlineMedium)
                    val parts = listOfNotNull(
                        h.schedule.label(weekStart),
                        h.goalLabel().takeIf { it.isNotEmpty() }?.let { "Hedef $it" },
                    )
                    Text(parts.joinToString("  ·  "), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    h.reminder?.let {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Notifications, null, Modifier.padding(end = 4.dp).height(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(it.format(TIME), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            val rate = p.completionRate(30, today)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("${p.currentStreak(today)}", "Güncel seri (${p.streakUnit})", Icons.Rounded.LocalFireDepartment, MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
                StatTile("${p.bestStreak(today)}", "En iyi seri (${p.streakUnit})", Icons.Rounded.EmojiEvents, Color(0xFFF5A623), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(rate?.let { "%${(it * 100).roundToInt()}" } ?: "—", "Son 30 gün başarı", Icons.Rounded.Percent, color, Modifier.weight(1f))
                StatTile("${p.totalDone()}", "Toplam tamamlanan", Icons.Rounded.CheckCircle, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
            }

            SectionCard(
                title = month.atDay(1).monthLabel(),
                trailing = {
                    IconButton(onClick = { monthOffset++ }) { Icon(Icons.Rounded.ChevronLeft, "Önceki ay") }
                    IconButton(onClick = { monthOffset-- }, enabled = monthOffset > 0) { Icon(Icons.Rounded.ChevronRight, "Sonraki ay") }
                },
            ) {
                MonthGrid(p, month, today, weekStart) { d ->
                    if (h.isCounted) editingDay = d else vm.toggle(h, d, p.value(d))
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "İşaretlemek ya da geri almak için bir güne dokun.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            SectionCard(title = "Son 20 hafta") {
                val days = weekDays(weekStart)
                val firstWeek = p.weekOf(today).minusWeeks(19)
                val weeks = (0L until 20L).map { w ->
                    (0L until 7L).map { i ->
                        val d = firstWeek.plusWeeks(w).plusDays(i)
                        if (d.isAfter(today)) null else p.fraction(d)
                    }
                }
                Heatmap(weeks, color, days.map { it.shortTr() })
            }

            if (h.note.isNotBlank()) {
                SectionCard(title = "Not") {
                    Text(h.note, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    editingDay?.let { d ->
        ValueDialog(h.name, h.unit, h.target, p.value(d), onDismiss = { editingDay = null }, onSave = { vm.setValue(h, d, it); editingDay = null })
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("“${h.name}” silinsin mi?") },
            text = { Text("Tüm geçmiş kayıtları da silinir ve geri alınamaz. Ara vermek istiyorsan arşivlemeyi düşün.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.delete(h); onBack() }) {
                    Text("Sil", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Vazgeç") } },
        )
    }
}

@Composable
private fun MonthGrid(p: HabitProgress, month: YearMonth, today: LocalDate, weekStart: java.time.DayOfWeek, onDay: (LocalDate) -> Unit) {
    val color = Color(p.habit.color)
    val days = weekDays(weekStart)
    Row(Modifier.fillMaxWidth()) {
        days.forEach {
            Text(
                it.shortTr(),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Spacer(Modifier.height(4.dp))
    val first = month.atDay(1)
    val lead = (first.dayOfWeek.value - weekStart.value + 7) % 7
    val cells = lead + month.lengthOfMonth()
    val rows = (cells + 6) / 7
    for (r in 0 until rows) {
        Row(Modifier.fillMaxWidth()) {
            for (c in 0 until 7) {
                val idx = r * 7 + c - lead
                if (idx < 0 || idx >= month.lengthOfMonth()) {
                    Spacer(Modifier.weight(1f))
                } else {
                    val d = first.plusDays(idx.toLong())
                    val future = d.isAfter(today)
                    val missed = !future && d != today && !d.isBefore(p.start) && p.habit.schedule.isPlanned(d) && !p.isDone(d) &&
                        p.habit.schedule.type != com.l4ngel.ritim.domain.ScheduleType.TIMES_PER_WEEK
                    DayCell(
                        day = d.dayOfMonth,
                        fraction = p.fraction(d),
                        color = color,
                        missed = missed,
                        isToday = d == today,
                        enabled = !future,
                        onClick = { onDay(d) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
