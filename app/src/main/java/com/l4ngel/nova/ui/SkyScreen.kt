package com.l4ngel.nova.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.l4ngel.nova.domain.SKY_HEIGHT
import com.l4ngel.nova.domain.SkyKind
import com.l4ngel.nova.domain.SkyObject
import java.time.format.DateTimeFormatter

private val ADDED = DateTimeFormatter.ofPattern("d MMMM yyyy", TR)

/** Evren sekmesinin başındaki gökyüzü kartı; dokununca tam ekran açılır. */
@Composable
fun SkyCard(state: RewardState, onOpen: () -> Unit) {
    Surface(onClick = onOpen, shape = MaterialTheme.shapes.large, color = SPACE_TOP, modifier = Modifier.fillMaxWidth()) {
        Box {
            SkyCanvas(state.sky, state.objects, state.today, Modifier.fillMaxWidth().aspectRatio(0.9f))
            Column(Modifier.padding(18.dp)) {
                Text("Evrenin", style = MaterialTheme.typography.titleLarge, color = Color.White)
                Text(
                    "${state.sky.totalStars.grouped()} yıldız · ${state.sky.galaxies.size} galaksi · ${state.objects.size} gök cismi",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                )
            }
            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.14f),
                modifier = Modifier.align(Alignment.BottomEnd).padding(14.dp).size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.OpenInFull, "Büyüt", tint = Color.White, modifier = Modifier.size(20.dp)) }
            }
            if (state.sky.totalStars == 0) {
                Text(
                    "Bir alışkanlığı tamamladığında ilk yıldızın burada doğacak.",
                    color = Color.White.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                )
            }
        }
    }
}

/** Tam ekran evren: galaksi adları görünür, cisim yerleştirilir ya da cisme dokunup taşınır. */
@Composable
fun SkyScreen(vm: HabitViewModel, onBack: () -> Unit) {
    val loaded by vm.rewards.collectAsStateWithLifecycle()
    val placing by vm.placing.collectAsStateWithLifecycle()
    val state = loaded ?: return
    var selected by remember { mutableStateOf<SkyObject?>(null) }
    // Hafif açılı başla ki derinlik hemen görünsün; yerleştirirken önden bakışa dön.
    var cam by remember { mutableStateOf(SkyCamera(yaw = 0.3f, pitch = 0.15f)) }
    LaunchedEffect(placing) { if (placing != null) cam = SkyCamera() }

    Box(
        Modifier.fillMaxSize().background(SPACE_TOP).pointerInput(placing) {
            if (placing != null) return@pointerInput
            detectTransformGestures { _, pan, zoom, _ ->
                cam = cam.copy(
                    yaw = cam.yaw + pan.x * 0.004f,
                    pitch = (cam.pitch - pan.y * 0.004f).coerceIn(-1.2f, 1.2f),
                    zoom = (cam.zoom * zoom).coerceIn(0.6f, 2.6f),
                )
            }
        },
    ) {
        SkyCanvas(
            state.sky,
            // Taşınan cisim eski yerinde görünmesin.
            state.objects.filter { it.id != placing?.id || placing?.id == 0L },
            state.today,
            Modifier.fillMaxSize(),
            labels = true,
            camera = cam,
            onTap = if (placing != null) ({ x, y -> vm.place(x, y) }) else null,
            onObject = { selected = it },
        )
        Row(Modifier.windowInsetsPadding(WindowInsets.safeDrawing).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.cancelPlacing(); onBack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Geri", tint = Color.White) }
            Column {
                Text("Evrenin", style = MaterialTheme.typography.titleLarge, color = Color.White)
                Text(
                    "${state.sky.totalStars.grouped()} yıldız · Seviye ${state.wallet.level.number} ${state.wallet.level.title}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                )
            }
        }
        AnimatedVisibility(
            placing != null,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            val p = placing
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp).fillMaxWidth(),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (p != null) SkyObjectPreview(p.kind, Color(p.color), Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p?.name.orEmpty(), style = MaterialTheme.typography.titleMedium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.TouchApp, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(4.dp))
                            Text("Yerleştirmek için gökyüzüne dokun", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    TextButton(onClick = { vm.cancelPlacing() }) { Text("Vazgeç") }
                }
            }
        }
        if (placing == null) {
            Row(
                Modifier.align(Alignment.BottomStart).windowInsetsPadding(WindowInsets.safeDrawing).padding(12.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.sky.galaxies.filter { !it.archived }.forEach { g ->
                    Surface(shape = RoundedCornerShape(50), color = Color.White.copy(alpha = 0.1f)) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(Color(g.color)))
                            Spacer(Modifier.width(6.dp))
                            Text("${g.name} · ${g.stars}", color = Color.White, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }

    selected?.let { o ->
        AlertDialog(
            onDismissRequest = { selected = null },
            icon = { SkyObjectPreview(o.kind, Color(o.color), Modifier.size(88.dp).clip(CircleShape)) },
            title = { Text(o.name) },
            text = { Text("${o.kind.title} · ${o.date.format(ADDED)} tarihinde eklendi", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(onClick = { vm.move(o); selected = null }) { Text("Taşı") } },
            dismissButton = {
                TextButton(onClick = { vm.removeSky(o); selected = null }) { Text("Kaldır", color = MaterialTheme.colorScheme.error) }
            },
        )
    }
}

/** Kozmik dükkân: puanla gök cismi al. */
@Composable
fun CosmicShop(balance: Int, onBuy: (SkyKind) -> Unit) {
    SectionCard(title = "Kozmik dükkân") {
        Text(
            "Puanlarınla evrenine gök cisimleri ekle. Rengini ve adını sen seç, istediğin yere yerleştir.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SkyKind.entries.forEach { k ->
                val ok = balance >= k.cost
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.width(128.dp)) {
                    Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        SkyObjectPreview(k, Color(HABIT_COLORS[(k.ordinal * 3 + 1) % HABIT_COLORS.size]), Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(14.dp)))
                        Spacer(Modifier.height(8.dp))
                        Text(k.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("$POINT ${k.cost.grouped()}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(6.dp))
                        FilledTonalButton(onClick = { onBuy(k) }, enabled = ok, modifier = Modifier.fillMaxWidth().alpha(if (ok) 1f else 0.7f)) {
                            Text(if (ok) "Al" else "${(k.cost - balance).grouped()} eksik", maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BuySkyDialog(kind: SkyKind, balance: Int, onDismiss: () -> Unit, onConfirm: (Int, String) -> Unit) {
    var color by rememberSaveable { mutableIntStateOf(HABIT_COLORS[(kind.ordinal * 3 + 1) % HABIT_COLORS.size]) }
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { SkyObjectPreview(kind, Color(color), Modifier.size(120.dp).clip(CircleShape)) },
        title = { Text(kind.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(kind.description, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(24) },
                    label = { Text("Adı (isteğe bağlı)") },
                    placeholder = { Text(kind.title) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (kind.colorable) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        HABIT_COLORS.forEach { c -> ColorDot(Color(c), c == color) { color = c } }
                    }
                }
                Text(
                    "$POINT ${kind.cost.grouped()} puan · kalan ${(balance - kind.cost).grouped()}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(color, name) }) { Text("Al ve yerleştir") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
    )
}
