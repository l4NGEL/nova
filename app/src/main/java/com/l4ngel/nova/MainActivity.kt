package com.l4ngel.nova

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.IntOffset
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.l4ngel.nova.data.ThemeMode
import com.l4ngel.nova.notify.HabitReminders
import com.l4ngel.nova.ui.EditHabitScreen
import com.l4ngel.nova.ui.EditHabitViewModel
import com.l4ngel.nova.ui.HabitDetailScreen
import com.l4ngel.nova.ui.CelebrationLayer
import com.l4ngel.nova.ui.HabitViewModel
import com.l4ngel.nova.ui.RewardsScreen
import com.l4ngel.nova.ui.NovaTheme
import com.l4ngel.nova.ui.SettingsScreen
import com.l4ngel.nova.ui.SkyScreen
import com.l4ngel.nova.ui.StatsScreen
import com.l4ngel.nova.ui.TodayScreen

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab("today", "Bugün", Icons.Rounded.Today),
    Tab("stats", "İstatistik", Icons.Rounded.BarChart),
    Tab("rewards", "Evren", Icons.Rounded.AutoAwesome),
    Tab("settings", "Ayarlar", Icons.Rounded.Settings),
)

class MainActivity : ComponentActivity() {
    /** Bildirimden gelindiyse açılacak alışkanlık; gezinme yapılınca sıfırlanır. */
    private var openHabit by mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) openHabit = intent.habitExtra()
        val app = application as NovaApp
        // Veritabanı ilk değeri gelene kadar açılış ekranı dursun; boş liste yanıp sönmesin.
        var ready = false
        splash.setKeepOnScreenCondition { !ready }

        setContent {
            val vm: HabitViewModel = viewModel()
            val theme by app.preferences.theme.collectAsStateWithLifecycle()
            val loaded by vm.progress.collectAsStateWithLifecycle()
            ready = loaded != null
            val dark = when (theme) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // Sistem çubuklarının simge rengi uygulama temasını izlesin (sistem açıkken koyu tema seçilmiş olabilir).
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { dark },
                )
                onDispose {}
            }
            NovaTheme(dark) {
                Box(Modifier.fillMaxSize()) {
                    AppNav(vm)
                    CelebrationLayer(vm)
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun AppNav(vm: HabitViewModel) {
        val nav = rememberNavController()
        val snackbar = remember { SnackbarHostState() }
        val entry by nav.currentBackStackEntryAsState()
        val route = entry?.destination?.route
        val onTab = TABS.any { it.route == route }
        val weekStart by vm.weekStart.collectAsStateWithLifecycle()

        LaunchedEffect(openHabit) {
            openHabit?.let { id -> nav.navigate("habit/$id") { launchSingleTop = true } }
            openHabit = null
        }
        LaunchedEffect(Unit) {
            vm.messages.collect { m ->
                snackbar.currentSnackbarData?.dismiss()
                val r = snackbar.showSnackbar(m.text, actionLabel = m.undo?.let { "Geri al" }, duration = SnackbarDuration.Short)
                if (r == SnackbarResult.ActionPerformed) m.undo?.invoke()
            }
        }

        Scaffold(
            contentWindowInsets = WindowInsets(0),
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                AnimatedVisibility(onTab, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
                        TABS.forEach { tab ->
                            val selected = entry?.destination?.hierarchy?.any { it.route == tab.route } == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    nav.navigate(tab.route) {
                                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(tab.icon, null) },
                                label = { Text(tab.label) },
                            )
                        }
                    }
                }
            },
        ) { inner ->
            val motion = tween<IntOffset>(360, easing = FastOutSlowInEasing)
            NavHost(
                nav,
                startDestination = "today",
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(inner),
                enterTransition = {
                    if (targetState.destination.route in TAB_ROUTES && initialState.destination.route in TAB_ROUTES) fadeIn(tween(220))
                    else slideIntoContainer(SlideDirection.Start, motion, initialOffset = { it / 4 }) + fadeIn(tween(280))
                },
                exitTransition = {
                    if (targetState.destination.route in TAB_ROUTES && initialState.destination.route in TAB_ROUTES) fadeOut(tween(160))
                    else slideOutOfContainer(SlideDirection.Start, motion, targetOffset = { it / 8 }) + fadeOut(tween(200))
                },
                popEnterTransition = { slideIntoContainer(SlideDirection.End, motion, initialOffset = { it / 8 }) + fadeIn(tween(280)) },
                popExitTransition = { slideOutOfContainer(SlideDirection.End, motion, targetOffset = { it / 4 }) + fadeOut(tween(200)) },
            ) {
                composable("today") {
                    TodayScreen(
                        vm,
                        onOpen = { nav.navigate("habit/$it") },
                        onAdd = { nav.navigate("edit") },
                        onRewards = {
                            nav.navigate("rewards") {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
                composable("rewards") { RewardsScreen(vm, onOpenSky = { nav.navigate("sky") { launchSingleTop = true } }) }
                composable("sky") { SkyScreen(vm, onBack = { nav.popBackStack() }) }
                composable("stats") {
                    StatsScreen(vm, onOpen = { nav.navigate("habit/$it") })
                }
                composable("settings") {
                    SettingsScreen(vm, onOpen = { nav.navigate("habit/$it") })
                }
                composable("habit/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                    val id = e.arguments?.getLong("id") ?: return@composable
                    HabitDetailScreen(vm, id, onBack = { nav.popBackStack() }, onEdit = { nav.navigate("edit?id=$id") })
                }
                composable(
                    "edit?id={id}",
                    arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = -1L }),
                ) {
                    val editVm: EditHabitViewModel = viewModel()
                    EditHabitScreen(editVm, weekStart, onDone = { nav.popBackStack() })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openHabit = intent.habitExtra()
    }

    private fun Intent.habitExtra(): Long? = getLongExtra(HabitReminders.EXTRA_HABIT, -1L).takeIf { it > 0 }

    private companion object {
        val TAB_ROUTES = TABS.map { it.route }.toSet()
        // Üç tuşlu gezinmede androidx'in varsayılan perdeleri.
        val LIGHT_SCRIM = AndroidColor.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val DARK_SCRIM = AndroidColor.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}
