package com.l4ngel.ritim.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.l4ngel.ritim.domain.Difficulty
import com.l4ngel.ritim.domain.ScheduleType
import com.l4ngel.ritim.domain.shortTr
import com.l4ngel.ritim.domain.weekDays
import com.l4ngel.ritim.notify.HabitReminders
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val TIME = DateTimeFormatter.ofPattern("HH:mm")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditHabitScreen(vm: EditHabitViewModel, weekStart: DayOfWeek, onDone: () -> Unit) {
    val loaded by vm.form.collectAsStateWithLifecycle()
    val f = loaded ?: return
    val context = LocalContext.current
    var pickTime by remember { mutableStateOf(false) }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        // İzin verilmese de saat kaydedilir; ayarlardan açılınca hatırlatmalar çalışır.
        pickTime = true
        if (!granted) android.widget.Toast.makeText(context, "Bildirim izni kapalı; ayarlardan açabilirsin.", android.widget.Toast.LENGTH_LONG).show()
    }
    val color = Color(f.color)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (vm.isNew) "Yeni alışkanlık" else "Düzenle") },
                navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.Rounded.Close, "Kapat") } },
                actions = {
                    TextButton(onClick = { vm.save(onDone) }, enabled = f.isValid) { Text("Kaydet") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HabitBubble(f.icon, color, size = 56.dp)
                Spacer(Modifier.width(14.dp))
                OutlinedTextField(
                    value = f.name,
                    onValueChange = { s -> vm.update { it.copy(name = s.take(40)) } },
                    label = { Text("Ad") },
                    placeholder = { Text("Ör. Kitap oku") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.weight(1f),
                )
            }

            SectionCard(title = "Simge") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HABIT_ICONS.forEach { (key, icon) ->
                        val sel = key == f.icon
                        Surface(
                            onClick = { vm.update { it.copy(icon = key) } },
                            shape = RoundedCornerShape(12.dp),
                            color = if (sel) color else MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.size(44.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(icon, null, tint = if (sel) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
            }

            SectionCard(title = "Renk") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HABIT_COLORS.forEach { c -> ColorDot(Color(c), c == f.color) { vm.update { it.copy(color = c) } } }
                }
            }

            SectionCard(title = "Sıklık") {
                val types = listOf(ScheduleType.DAILY to "Her gün", ScheduleType.WEEKDAYS to "Belirli günler", ScheduleType.TIMES_PER_WEEK to "Haftada X")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    types.forEachIndexed { i, (t, label) ->
                        SegmentedButton(
                            selected = f.scheduleType == t,
                            onClick = { vm.update { it.copy(scheduleType = t) } },
                            shape = SegmentedButtonDefaults.itemShape(i, types.size),
                            label = { Text(label, maxLines = 1) },
                        )
                    }
                }
                when (f.scheduleType) {
                    ScheduleType.WEEKDAYS -> {
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            weekDays(weekStart).forEach { d ->
                                val sel = d in f.days
                                // Çip yerine kare düğme: yedisi dar ekrana sığarken gün adı kısalmasın.
                                Surface(
                                    onClick = { vm.update { it.copy(days = if (sel) it.days - d else it.days + d) } },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (sel) color else MaterialTheme.colorScheme.surfaceContainer,
                                    modifier = Modifier.weight(1f).aspectRatio(1f),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            d.shortTr(),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (sel) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            softWrap = false,
                                        )
                                    }
                                }
                            }
                        }
                        if (f.days.isEmpty()) Text("En az bir gün seç.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    ScheduleType.TIMES_PER_WEEK -> {
                        Spacer(Modifier.height(12.dp))
                        Stepper("Haftada", f.timesPerWeek, "kez", 1..7) { n -> vm.update { it.copy(timesPerWeek = n) } }
                    }
                    ScheduleType.DAILY -> {}
                }
            }

            SectionCard(
                title = "Sayılı hedef",
                trailing = { Switch(f.counted, onCheckedChange = { on -> vm.update { it.copy(counted = on) } }) },
            ) {
                if (!f.counted) {
                    Text(
                        "Kapalıyken tek dokunuşla tamamlanır. Açarsan günlük bir miktar hedeflersin (ör. 8 bardak su).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = if (f.target == 0) "" else f.target.toString(),
                            onValueChange = { s -> vm.update { it.copy(target = s.filter(Char::isDigit).take(4).toIntOrNull() ?: 0) } },
                            label = { Text("Günlük hedef") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            isError = f.target < 2,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(10.dp))
                        OutlinedTextField(
                            value = f.unit,
                            onValueChange = { s -> vm.update { it.copy(unit = s.take(16)) } },
                            label = { Text("Birim") },
                            placeholder = { Text("bardak") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (f.target < 2) Text("Hedef en az 2 olmalı.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }

            SectionCard(title = "Zorluk") {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    Difficulty.entries.forEachIndexed { i, d ->
                        SegmentedButton(
                            selected = Difficulty.of(f.points) == d,
                            onClick = { vm.update { it.copy(points = d.points) } },
                            shape = SegmentedButtonDefaults.itemShape(i, Difficulty.entries.size),
                            label = { Text(d.label, maxLines = 1) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Her tamamladığında +${Difficulty.of(f.points).points} puan. 7 günlük seride ×3 bonus. Puanlarla kendi ödüllerini alırsın.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionCard(
                title = "Hatırlatma",
                trailing = {
                    Switch(
                        f.reminder != null,
                        onCheckedChange = { on ->
                            if (!on) vm.update { it.copy(reminder = null) }
                            else if (Build.VERSION.SDK_INT >= 33 && !HabitReminders.canNotify(context)) askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            else pickTime = true
                        },
                    )
                },
            ) {
                val r = f.reminder
                if (r == null) {
                    Text("Belirlediğin saatte, o gün henüz yapmadıysan bildirim gelir.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Surface(onClick = { pickTime = true }, shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainer) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Notifications, null, tint = color)
                            Spacer(Modifier.width(10.dp))
                            Text(r.format(TIME), style = MaterialTheme.typography.titleLarge)
                            Spacer(Modifier.width(10.dp))
                            Text("Değiştir", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            OutlinedTextField(
                value = f.note,
                onValueChange = { s -> vm.update { it.copy(note = s.take(300)) } },
                label = { Text("Not (isteğe bağlı)") },
                placeholder = { Text("Neden önemli? Nasıl yapacaksın?") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            Button(onClick = { vm.save(onDone) }, enabled = f.isValid, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(if (vm.isNew) "Alışkanlığı oluştur" else "Değişiklikleri kaydet")
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (pickTime) {
        val initial = f.reminder ?: LocalTime.of(20, 0)
        val state = rememberTimePickerState(initial.hour, initial.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            title = { Text("Hatırlatma saati") },
            text = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimePicker(state) } },
            confirmButton = {
                TextButton(onClick = { vm.update { it.copy(reminder = LocalTime.of(state.hour, state.minute)) }; pickTime = false }) { Text("Tamam") }
            },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text("Vazgeç") } },
        )
    }
}

@Composable
private fun Stepper(prefix: String, value: Int, suffix: String, range: IntRange, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(prefix, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceContainer) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onChange(value - 1) }, enabled = value > range.first) { Text("−", style = MaterialTheme.typography.titleLarge) }
                Text("$value $suffix", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 4.dp))
                IconButton(onClick = { onChange(value + 1) }, enabled = value < range.last) { Text("+", style = MaterialTheme.typography.titleLarge) }
            }
        }
    }
}
