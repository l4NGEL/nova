package com.l4ngel.nova.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.l4ngel.nova.MainActivity
import com.l4ngel.nova.NovaApp
import com.l4ngel.nova.R
import com.l4ngel.nova.domain.Boss
import com.l4ngel.nova.domain.HabitProgress
import com.l4ngel.nova.domain.Level
import com.l4ngel.nova.domain.Wallet
import com.l4ngel.nova.domain.dayScore
import com.l4ngel.nova.domain.goalLabel
import com.l4ngel.nova.domain.pointEvents
import com.l4ngel.nova.domain.weeklyBoss
import com.l4ngel.nova.notify.HabitReminders
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/** Widget'ın gösterdiği her şey; uygulamadaki hesaplarla aynı kaynaktan. */
data class WidgetHabit(val id: Long, val name: String, val color: Int, val value: Int, val target: Int, val done: Boolean, val detail: String)

data class WidgetState(
    val hasHabits: Boolean,
    val done: Int,
    val due: Int,
    val balance: Int,
    val level: Level,
    val boss: Boss?,
    val habits: List<WidgetHabit>,
)

private fun widgetState(app: NovaApp): Flow<WidgetState> = combine(
    app.repository.all,
    app.repository.redemptions,
    app.repository.skyObjects,
    app.preferences.weekStart,
) { all, redemptions, objects, weekStart ->
    val today = LocalDate.now()
    val progress = all.map { HabitProgress(it.habit, it.checks, weekStart) }
    val active = progress.filter { !it.habit.archived }
    val wallet = Wallet(pointEvents(progress, today, app.preferences.gameStart), redemptions, objects)
    val score = dayScore(active, today)
    // Bugün ekranıyla aynı liste ve sıra; işaretlenen satır yerinden kaymasın ki peş peşe dokunuş yanlış satıra düşmesin.
    val habits = active
        .filter { it.habit.schedule.isPlanned(today) && !today.isBefore(it.start) }
        .map { p ->
            val h = p.habit
            val v = p.value(today)
            val streak = p.currentStreak(today)
            val detail = when {
                h.isCounted -> "$v / ${h.goalLabel()}"
                streak > 0 -> "🔥 $streak ${p.streakUnit}"
                else -> ""
            }
            WidgetHabit(h.id, h.name, h.color, v, h.target, p.isDone(today), detail)
        }
    WidgetState(
        hasHabits = active.isNotEmpty(),
        done = score.done,
        due = score.due,
        balance = wallet.balance,
        level = wallet.level,
        boss = active.firstOrNull()?.let { weeklyBoss(active, it.weekOf(today), today) },
        habits = habits,
    )
}

class NovaWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(SMALL, WIDE, TALL))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as NovaApp
        val flow = widgetState(app)
        val initial = flow.first()
        provideContent {
            val state by flow.collectAsState(initial)
            Content(state)
        }
    }

    companion object {
        val SMALL = DpSize(120.dp, 120.dp)
        val WIDE = DpSize(250.dp, 120.dp)
        val TALL = DpSize(250.dp, 260.dp)

        suspend fun refresh(context: Context) = NovaWidget().updateAll(context)
    }
}

class NovaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NovaWidget()
}

private val HABIT_KEY = ActionParameters.Key<Long>("habit")

/** Widget'taki düğme: tek seferlikte tamamla / geri al, sayılıda +1 (dolunca sıfırla). */
class ToggleHabitAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[HABIT_KEY] ?: return
        val app = context.applicationContext as NovaApp
        val item = app.repository.get(id) ?: return
        val h = item.habit
        val today = LocalDate.now()
        val v = item.checks[today] ?: 0
        val next = when {
            v >= h.target -> 0
            h.isCounted -> v + 1
            else -> h.target
        }
        app.repository.setValue(id, today, next)
        if (next >= h.target) HabitReminders.dismiss(context, id)
    }
}

private val WHITE = ColorProvider(Color.White)
private val MUTED = ColorProvider(Color.White.copy(alpha = 0.65f))
private val PEACH = ColorProvider(Color(0xFFFFB783))
/** Widget zemininin ortası; halkanın içi zeminle aynı görünsün. */
private val RING_INNER = Color(0xFF16103F)
private val TRACK = ColorProvider(Color.White.copy(alpha = 0.14f))

@Composable
private fun Content(s: WidgetState) {
    val size = LocalSize.current
    val open = actionStartActivity<MainActivity>()
    Column(
        GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.widget_bg)).cornerRadius(24.dp).padding(14.dp),
    ) {
        Row(GlanceModifier.fillMaxWidth().clickable(open), verticalAlignment = Alignment.CenterVertically) {
            Text("✦ Nova", style = TextStyle(color = WHITE, fontSize = 15.sp, fontWeight = FontWeight.Bold))
            Spacer(GlanceModifier.defaultWeight())
            Text("✦ ${"%,d".format(java.util.Locale("tr"), s.balance)}", style = TextStyle(color = PEACH, fontSize = 14.sp, fontWeight = FontWeight.Bold))
        }
        if (!s.hasHabits) {
            Spacer(GlanceModifier.height(10.dp))
            Text(
                "İlk alışkanlığını ekle, evrenin ilk yıldızı doğsun.",
                style = TextStyle(color = MUTED, fontSize = 13.sp),
                modifier = GlanceModifier.clickable(open),
            )
            return@Column
        }
        Spacer(GlanceModifier.height(6.dp))
        val small = size.width < WIDE_THRESHOLD
        Text(
            if (s.due == 0) "Bugün plan yok" else "${s.done} / ${s.due} tamam",
            style = TextStyle(color = WHITE, fontSize = if (small) 22.sp else 18.sp, fontWeight = FontWeight.Bold),
            modifier = GlanceModifier.clickable(open),
        )
        Text("Seviye ${s.level.number} · ${s.level.title}", style = TextStyle(color = MUTED, fontSize = 12.sp), maxLines = 1)
        Spacer(GlanceModifier.height(6.dp))
        LinearProgressIndicator(
            progress = if (s.due == 0) 0f else s.done.toFloat() / s.due,
            modifier = GlanceModifier.fillMaxWidth().height(6.dp),
            color = ColorProvider(Color(0xFFBFC2FF)),
            backgroundColor = TRACK,
        )
        if (small) return@Column

        val boss = s.boss
        if (boss != null && size.height >= TALL_THRESHOLD) {
            Spacer(GlanceModifier.height(10.dp))
            Row(GlanceModifier.fillMaxWidth().clickable(open), verticalAlignment = Alignment.CenterVertically) {
                Text(boss.kind.emoji, style = TextStyle(fontSize = 20.sp))
                Spacer(GlanceModifier.width(8.dp))
                Column(GlanceModifier.defaultWeight()) {
                    Text(
                        if (boss.defeated) "${boss.kind.name} yenildi!" else boss.kind.name,
                        style = TextStyle(color = WHITE, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                        maxLines = 1,
                    )
                    Spacer(GlanceModifier.height(3.dp))
                    LinearProgressIndicator(
                        progress = boss.fraction,
                        modifier = GlanceModifier.fillMaxWidth().height(5.dp),
                        color = ColorProvider(Color(0xFFE5484D)),
                        backgroundColor = TRACK,
                    )
                }
                Spacer(GlanceModifier.width(8.dp))
                Text("${boss.hp} can", style = TextStyle(color = MUTED, fontSize = 11.sp))
            }
        }

        Spacer(GlanceModifier.height(8.dp))
        if (s.habits.isEmpty()) {
            Text("Bugün için planlı alışkanlık yok.", style = TextStyle(color = MUTED, fontSize = 12.sp))
        } else {
            LazyColumn(GlanceModifier.fillMaxWidth().defaultWeight()) {
                items(s.habits, itemId = { it.id }) { h -> HabitRow(h) }
            }
        }
    }
}

private val WIDE_THRESHOLD = 200.dp
private val TALL_THRESHOLD = 220.dp

@Composable
private fun HabitRow(h: WidgetHabit) {
    val color = Color(h.color)
    Row(GlanceModifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(GlanceModifier.size(8.dp).cornerRadius(4.dp).background(color)) {}
        Spacer(GlanceModifier.width(8.dp))
        Column(GlanceModifier.defaultWeight()) {
            Text(
                h.name,
                style = TextStyle(color = if (h.done) MUTED else WHITE, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
            )
            if (h.detail.isNotEmpty()) Text(h.detail, style = TextStyle(color = MUTED, fontSize = 11.sp), maxLines = 1)
        }
        Spacer(GlanceModifier.width(8.dp))
        // Yapılmamışken renkli halka ve soluk işaret: dokunulabilir olduğu ilk bakışta belli olsun.
        // Halka iç içe iki daireyle çizilir; uzak görünümde çizgi kenarlığı yok.
        val tap = actionRunCallback<ToggleHabitAction>(actionParametersOf(HABIT_KEY to h.id))
        Box(
            GlanceModifier.size(36.dp).cornerRadius(18.dp).background(color).padding(if (h.done) 0.dp else 2.5.dp).clickable(tap),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                GlanceModifier.fillMaxSize().cornerRadius(16.dp).background(if (h.done) color else lerp(RING_INNER, color, 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (!h.done && h.target > 1) "+1" else "✓",
                    style = TextStyle(
                        color = if (h.done) WHITE else ColorProvider(lerp(color, Color.White, 0.35f).copy(alpha = if (h.target > 1) 1f else 0.6f)),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    ),
                )
            }
        }
    }
}
