package com.l4ngel.nova.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.l4ngel.nova.BuildConfig
import com.l4ngel.nova.data.ThemeMode
import com.l4ngel.nova.notify.HabitReminders
import java.time.DayOfWeek
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: HabitViewModel, onOpen: (Long) -> Unit) {
    val context = LocalContext.current
    val theme by vm.theme.collectAsStateWithLifecycle()
    val weekStart by vm.weekStart.collectAsStateWithLifecycle()
    val celebrate by vm.celebrationsOn.collectAsStateWithLifecycle()
    val all by vm.all.collectAsStateWithLifecycle()
    val archived = all.orEmpty().filter { it.habit.archived }
    var confirmImport by remember { mutableStateOf<Uri?>(null) }

    // Kullanıcı sistem ayarlarından dönünce izin durumu tazelensin.
    var canNotify by remember { mutableStateOf(HabitReminders.canNotify(context)) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { canNotify = HabitReminders.canNotify(context) }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.export(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) confirmImport = uri
    }

    Scaffold(contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Ayarlar", style = MaterialTheme.typography.headlineLarge)

            SectionCard(title = "Görünüm") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Palette, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text("Tema", style = MaterialTheme.typography.bodyLarge)
                }
                Spacer(Modifier.height(10.dp))
                val modes = listOf(ThemeMode.SYSTEM to "Sistem", ThemeMode.LIGHT to "Açık", ThemeMode.DARK to "Koyu")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    modes.forEachIndexed { i, (m, label) ->
                        SegmentedButton(selected = theme == m, onClick = { vm.setTheme(m) }, shape = SegmentedButtonDefaults.itemShape(i, modes.size)) { Text(label) }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CalendarMonth, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text("Hafta başlangıcı", style = MaterialTheme.typography.bodyLarge)
                }
                Spacer(Modifier.height(10.dp))
                val starts = listOf(DayOfWeek.MONDAY to "Pazartesi", DayOfWeek.SUNDAY to "Pazar")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    starts.forEachIndexed { i, (d, label) ->
                        SegmentedButton(selected = weekStart == d, onClick = { vm.setWeekStart(d) }, shape = SegmentedButtonDefaults.itemShape(i, starts.size)) { Text(label) }
                    }
                }
            }

            SectionCard(title = "Kutlamalar") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Celebration, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Konfeti ve puan rozeti", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Puan kazanınca, kusursuz günde ve seviye atlayınca küçük bir kutlama.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(celebrate, onCheckedChange = vm::setCelebrations)
                }
            }

            SectionCard(title = "Bildirimler") {
                SettingRow(
                    Icons.Rounded.Notifications,
                    if (canNotify) "Bildirimler açık" else "Bildirimler kapalı",
                    if (canNotify) "Hatırlatma saatini her alışkanlığın düzenleme ekranından seçebilirsin."
                    else "Hatırlatmaların gelmesi için sistem ayarlarından bildirim iznini aç.",
                ) {
                    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    } else {
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName))
                    }
                    context.startActivity(intent)
                }
            }

            SectionCard(title = "Yedekleme") {
                SettingRow(Icons.Rounded.CloudUpload, "Yedek al", "Alışkanlıkları, geçmişi ve ödülleri bir JSON dosyasına kaydet.") {
                    exportLauncher.launch("nova-yedek-${LocalDate.now()}.json")
                }
                HorizontalDivider(Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                SettingRow(Icons.Rounded.CloudDownload, "Yedekten geri yükle", "Mevcut verinin yerine yedekteki veri gelir.") {
                    importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                }
            }

            SectionCard(title = "Arşiv") {
                if (archived.isEmpty()) {
                    Text("Arşivlenmiş alışkanlık yok. Ara verdiğin alışkanlıkları geçmişleri silinmeden burada saklayabilirsin.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    archived.forEach { item ->
                        val h = item.habit
                        Surface(onClick = { onOpen(h.id) }, color = Color.Transparent) {
                            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                HabitBubble(h.icon, Color(h.color), size = 36.dp)
                                Spacer(Modifier.width(12.dp))
                                Text(h.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                IconButton(onClick = { vm.setArchived(h, false) }) { Icon(Icons.Rounded.Unarchive, "Arşivden çıkar") }
                            }
                        }
                    }
                }
            }

            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Info, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Nova ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyLarge)
                        Text("Verilerin yalnız bu cihazda saklanır; hesap ve internet gerekmez.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(80.dp))
        }
    }

    confirmImport?.let { uri ->
        AlertDialog(
            onDismissRequest = { confirmImport = null },
            title = { Text("Yedek geri yüklensin mi?") },
            text = { Text("Şu anki tüm alışkanlıklar ve kayıtlar silinip yedekteki veriyle değiştirilecek.") },
            confirmButton = { TextButton(onClick = { vm.import(uri); confirmImport = null }) { Text("Geri yükle") } },
            dismissButton = { TextButton(onClick = { confirmImport = null }) { Text("Vazgeç") } },
        )
    }
}

@Composable
private fun SettingRow(icon: ImageVector, title: String, body: String, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color.Transparent, shape = MaterialTheme.shapes.small) {
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}
