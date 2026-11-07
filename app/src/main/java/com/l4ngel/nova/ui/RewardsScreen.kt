package com.l4ngel.nova.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Diamond
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MilitaryTech
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.OutlinedFlag
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.SportsMartialArts
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.l4ngel.nova.domain.Badge
import com.l4ngel.nova.domain.PointKind
import com.l4ngel.nova.domain.REWARD_EMOJIS
import com.l4ngel.nova.domain.REWARD_TEMPLATES
import com.l4ngel.nova.domain.Reward
import com.l4ngel.nova.domain.SkyKind
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val SHORT_DATE = DateTimeFormatter.ofPattern("d MMM", TR)

fun Int.grouped(): String = String.format(TR, "%,d", this)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RewardsScreen(vm: HabitViewModel, onOpenSky: () -> Unit) {
    val loaded by vm.rewards.collectAsStateWithLifecycle()
    val state = loaded ?: return
    var editing by remember { mutableStateOf<Reward?>(null) }
    var redeeming by remember { mutableStateOf<Reward?>(null) }
    var badge by remember { mutableStateOf<Badge?>(null) }
    var buying by remember { mutableStateOf<SkyKind?>(null) }
    val balance = state.wallet.balance

    Scaffold(contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Evren", style = MaterialTheme.typography.headlineLarge)
            SkyCard(state, onOpenSky)
            LevelCard(state)
            CosmicShop(balance) { buying = it }

            SectionCard(
                title = "Kendine ödüller",
                trailing = {
                    TextButton(onClick = { editing = Reward(name = "", emoji = "🎁", cost = 100) }) {
                        Icon(Icons.Rounded.Add, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Ekle")
                    }
                },
            ) {
                if (state.rewards.isEmpty()) {
                    Text(
                        "Puanlarını gerçek hayatta da harca: kendine ne vaat edersin? Bir öneriyle başlayabilirsin:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        REWARD_TEMPLATES.forEach { t ->
                            AssistChip(onClick = { vm.saveReward(t) }, label = { Text("${t.emoji} ${t.name} · ${t.cost}") })
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        state.rewards.forEach { r ->
                            RewardRow(
                                r,
                                balance = balance,
                                claimed = state.isClaimed(r),
                                times = state.wallet.timesRedeemed(r.id),
                                isGoal = state.goal?.id == r.id,
                                onEdit = { editing = r },
                                onGoal = { vm.setGoal(if (state.goal?.id == r.id) null else r) },
                                onRedeem = { redeeming = r },
                            )
                        }
                        // Raf dolana kadar eklenmemiş öneriler altta dursun.
                        val names = state.rewards.map { it.name }.toSet()
                        val rest = REWARD_TEMPLATES.filter { it.name !in names }
                        if (rest.isNotEmpty() && state.rewards.size < 6) {
                            Text("Öneriler", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                rest.forEach { t ->
                                    AssistChip(onClick = { vm.saveReward(t) }, label = { Text("${t.emoji} ${t.name} · ${t.cost}") })
                                }
                            }
                        }
                    }
                }
            }

            val unlocked = state.badges.count { it.unlocked }
            SectionCard(title = "Rozetler", trailing = { Text("$unlocked / ${state.badges.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    state.badges.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { b -> BadgeCell(b, state.badges.indexOf(b), Modifier.weight(1f)) { badge = b } }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }

            History(state)
            Spacer(Modifier.height(80.dp))
        }
    }

    editing?.let { r ->
        RewardDialog(
            initial = r,
            onDismiss = { editing = null },
            onSave = { vm.saveReward(it); editing = null },
            onDelete = if (r.id == 0L) null else ({ vm.deleteReward(r); editing = null }),
        )
    }
    buying?.let { k ->
        BuySkyDialog(k, balance, onDismiss = { buying = null }) { color, name ->
            vm.buySky(k, color, name)
            buying = null
            onOpenSky()
        }
    }
    redeeming?.let { r ->
        AlertDialog(
            onDismissRequest = { redeeming = null },
            icon = { Text(r.emoji, fontSize = 44.sp) },
            title = { Text(r.name, textAlign = TextAlign.Center) },
            text = {
                Text(
                    "Bu ödül için $POINT ${r.cost.grouped()} puan harcanacak. Kalan bakiyen $POINT ${(balance - r.cost).grouped()} olur.",
                    textAlign = TextAlign.Center,
                )
            },
            confirmButton = { TextButton(onClick = { vm.redeem(r); redeeming = null }) { Text("Ödülü al") } },
            dismissButton = { TextButton(onClick = { redeeming = null }) { Text("Vazgeç") } },
        )
    }
    badge?.let { b ->
        AlertDialog(
            onDismissRequest = { badge = null },
            icon = { BadgeIcon(b, state.badges.indexOf(b), 72.dp) },
            title = { Text(b.title, textAlign = TextAlign.Center) },
            text = {
                Text(
                    b.description + if (b.unlocked) "\nKazanıldı!" else "\nİlerleme: ${minOf(b.current, b.goal).grouped()} / ${b.goal.grouped()}",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = { TextButton(onClick = { badge = null }) { Text("Tamam") } },
        )
    }
}

/**
 * Seviye madalyası: marka renginden turuncuya geçişli daire, ortada seviye numarası. Renkler temadan
 * alınmıyor; koyu temanın açık tonlarında beyaz rakam okunmuyordu.
 */
@Composable
fun LevelMedal(number: Int, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(Color(0xFF4E54C8), Color(0xFFE8772E)))),
        contentAlignment = Alignment.Center,
    ) {
        Text("$number", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall)
    }
}

@Composable
private fun LevelCard(state: RewardState) {
    val w = state.wallet
    val level = w.level
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LevelMedal(level.number, Modifier.size(64.dp))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text("Seviye ${level.number}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f))
                    Text(level.title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { level.fraction },
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                        strokeCap = StrokeCap.Round,
                        gapSize = 0.dp,
                        drawStopIndicator = {},
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Sonraki seviyeye ${level.toNext.grouped()} puan",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text("Harcanabilir puan", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f))
                    Text(
                        "$POINT ${w.balance.grouped()}",
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    val today = w.earnedOn(state.today)
                    Text("Bugün +${today.grouped()}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.tertiary)
                    Text(
                        "Toplam ${w.earned.grouped()} · harcanan ${w.spent.grouped()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}

@Composable
private fun RewardRow(
    r: Reward,
    balance: Int,
    claimed: Boolean,
    times: Int,
    isGoal: Boolean,
    onEdit: () -> Unit,
    onGoal: () -> Unit,
    onRedeem: () -> Unit,
) {
    val affordable = balance >= r.cost
    Surface(onClick = onEdit, color = Color.Transparent, modifier = Modifier.alpha(if (claimed) 0.55f else 1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.tertiaryContainer),
                contentAlignment = Alignment.Center,
            ) { Text(r.emoji, fontSize = 26.sp) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(r.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val sub = buildString {
                    append("$POINT ${r.cost.grouped()}")
                    if (claimed) append(" · alındı")
                    else if (!r.repeatable) append(" · tek sefer")
                    else if (times > 0) append(" · $times kez alındı")
                }
                Text(sub, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!claimed) {
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { (balance.toFloat() / r.cost).coerceIn(0f, 1f) },
                        color = if (affordable) Color(0xFF19A974) else MaterialTheme.colorScheme.tertiary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        strokeCap = StrokeCap.Round,
                        gapSize = 0.dp,
                        drawStopIndicator = {},
                        modifier = Modifier.fillMaxWidth().height(5.dp),
                    )
                }
            }
            if (!claimed) {
                IconButton(onClick = onGoal) {
                    Icon(
                        if (isGoal) Icons.Rounded.Flag else Icons.Rounded.OutlinedFlag,
                        if (isGoal) "Hedeften kaldır" else "Hedef yap",
                        tint = if (isGoal) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilledTonalButton(onClick = onRedeem, enabled = affordable) { Text("Al") }
            } else {
                Icon(Icons.Rounded.CheckCircle, "Alındı", tint = Color(0xFF19A974), modifier = Modifier.padding(horizontal = 12.dp))
            }
        }
    }
}

private val BADGE_ICONS: Map<String, ImageVector> = mapOf(
    "spark" to Icons.Rounded.AutoAwesome,
    "fire" to Icons.Rounded.LocalFireDepartment,
    "flame" to Icons.Rounded.Whatshot,
    "calendar" to Icons.Rounded.EventAvailable,
    "diamond" to Icons.Rounded.Diamond,
    "crown" to Icons.Rounded.WorkspacePremium,
    "hundred" to Icons.Rounded.MilitaryTech,
    "grid" to Icons.Rounded.Apps,
    "bolt" to Icons.Rounded.Bolt,
    "music" to Icons.Rounded.MusicNote,
    "gift" to Icons.Rounded.CardGiftcard,
    "trophy" to Icons.Rounded.EmojiEvents,
    "sun" to Icons.Rounded.WbSunny,
    "planet" to Icons.Rounded.Public,
    "sword" to Icons.Rounded.SportsMartialArts,
    "flag" to Icons.Rounded.Flag,
)

@Composable
private fun BadgeIcon(b: Badge, index: Int, size: androidx.compose.ui.unit.Dp) {
    val color = Color(HABIT_COLORS[(index * 5) % HABIT_COLORS.size])
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        if (b.unlocked) {
            Box(
                Modifier.size(size).clip(CircleShape).background(Brush.linearGradient(listOf(color, color.copy(alpha = 0.7f)))),
                contentAlignment = Alignment.Center,
            ) { Icon(BADGE_ICONS[b.icon] ?: Icons.Rounded.EmojiEvents, null, tint = Color.White, modifier = Modifier.size(size * 0.5f)) }
        } else {
            ProgressRing(b.fraction, color, Modifier.size(size), stroke = 3.dp, track = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Box(
                    Modifier.size(size - 10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(BADGE_ICONS[b.icon] ?: Icons.Rounded.EmojiEvents, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f), modifier = Modifier.size(size * 0.42f))
                }
            }
            Icon(
                Icons.Rounded.Lock,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.BottomEnd).size(size * 0.28f).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerLowest).padding(2.dp),
            )
        }
    }
}

@Composable
private fun BadgeCell(b: Badge, index: Int, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color.Transparent, shape = MaterialTheme.shapes.small, modifier = modifier) {
        Column(Modifier.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            BadgeIcon(b, index, 56.dp)
            Spacer(Modifier.height(6.dp))
            Text(
                b.title,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (b.unlocked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                if (b.unlocked) "Kazanıldı" else "${minOf(b.current, b.goal).grouped()}/${b.goal.grouped()}",
                style = MaterialTheme.typography.labelSmall,
                color = if (b.unlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private data class HistoryItem(val date: LocalDate, val at: Long, val title: String, val amount: Int, val icon: ImageVector)

@Composable
private fun History(state: RewardState) {
    var all by rememberSaveable { mutableStateOf(false) }
    val items = remember(state) {
        val earned = state.wallet.events.map { e ->
            val icon = when (e.kind) {
                PointKind.HABIT -> Icons.Rounded.CheckCircle
                PointKind.STREAK -> Icons.Rounded.LocalFireDepartment
                PointKind.WEEK_GOAL -> Icons.Rounded.EventAvailable
                PointKind.PERFECT_DAY -> Icons.Rounded.Diamond
                PointKind.QUEST -> Icons.Rounded.Flag
                PointKind.BOSS -> Icons.Rounded.SportsMartialArts
            }
            HistoryItem(e.date, 0, e.title, e.amount, icon)
        }
        val spent = state.wallet.redemptions.map { HistoryItem(it.date, it.at, "${it.emoji} ${it.name}", -it.cost, Icons.Rounded.CardGiftcard) } +
            state.objects.map { HistoryItem(it.date, it.id, if (it.name == it.kind.title) it.name else "${it.kind.title}: ${it.name}", -it.cost, Icons.Rounded.Public) }
        // Aynı gün içinde harcamalar üstte: en yeni olay odur.
        (earned + spent).sortedWith(compareByDescending<HistoryItem> { it.date }.thenByDescending { it.at })
    }
    if (items.isEmpty()) return
    SectionCard(Modifier.animateContentSize(), title = "Puan geçmişi") {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items.take(if (all) 60 else 8).forEach { h ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(h.icon, null, tint = if (h.amount < 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(h.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(dayLabel(h.date, state.today), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        if (h.amount < 0) "−${(-h.amount).grouped()}" else "+${h.amount.grouped()}",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (h.amount < 0) MaterialTheme.colorScheme.tertiary else Color(0xFF19A974),
                    )
                }
            }
            if (items.size > 8) {
                TextButton(onClick = { all = !all }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(if (all) "Daha az göster" else "Tümünü göster")
                }
            }
        }
    }
}

private fun dayLabel(d: LocalDate, today: LocalDate): String = when (d) {
    today -> "Bugün"
    today.minusDays(1) -> "Dün"
    else -> d.format(SHORT_DATE)
}

private val COSTS = listOf(25, 50, 100, 250, 500, 1000)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RewardDialog(initial: Reward, onDismiss: () -> Unit, onSave: (Reward) -> Unit, onDelete: (() -> Unit)?) {
    var name by rememberSaveable { mutableStateOf(initial.name) }
    var emoji by rememberSaveable { mutableStateOf(initial.emoji) }
    var cost by rememberSaveable { mutableStateOf(initial.cost.toString()) }
    var repeatable by rememberSaveable { mutableStateOf(initial.repeatable) }
    val costValue = cost.toIntOrNull()?.takeIf { it > 0 }
    val valid = name.isNotBlank() && costValue != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "Yeni ödül" else "Ödülü düzenle") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.tertiaryContainer),
                        contentAlignment = Alignment.Center,
                    ) { Text(emoji, fontSize = 28.sp) }
                    Spacer(Modifier.width(12.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(40) },
                        label = { Text("Ödül") },
                        placeholder = { Text("Ör. Bir bölüm dizi") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.weight(1f),
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    REWARD_EMOJIS.forEach { e ->
                        Surface(
                            onClick = { emoji = e },
                            shape = RoundedCornerShape(10.dp),
                            color = if (e == emoji) MaterialTheme.colorScheme.tertiaryContainer else Color.Transparent,
                            modifier = Modifier.size(38.dp),
                        ) { Box(contentAlignment = Alignment.Center) { Text(e, fontSize = 20.sp) } }
                    }
                }
                OutlinedTextField(
                    value = if (emoji in REWARD_EMOJIS) "" else emoji,
                    onValueChange = { s -> emoji = s.trim().take(8).ifEmpty { "🎁" } },
                    label = { Text("Ya da kendi emojin") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = cost,
                    onValueChange = { s -> cost = s.filter(Char::isDigit).take(5) },
                    label = { Text("Bedeli (puan)") },
                    prefix = { Text("$POINT ") },
                    singleLine = true,
                    isError = costValue == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    COSTS.forEach { c -> FilterChip(selected = costValue == c, onClick = { cost = c.toString() }, label = { Text("$c") }) }
                }
                Text(
                    "İpucu: orta zorlukta bir alışkanlık günde 10 puan getirir.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Tekrar alınabilir", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (repeatable) "Her biriktirdiğinde yeniden alabilirsin." else "Bir kez alınınca rafta \"alındı\" olarak kalır.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(repeatable, onCheckedChange = { repeatable = it })
                }
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("Ödülü sil", color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(initial.copy(name = name.trim(), emoji = emoji, cost = costValue ?: 1, repeatable = repeatable)) },
                enabled = valid,
            ) { Text("Kaydet") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
    )
}
