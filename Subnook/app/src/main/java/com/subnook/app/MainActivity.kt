package com.subnook.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.ui.unit.dp
import com.subnook.app.ui.components.FloatingNavBar
import com.subnook.app.ui.components.NavItem
import com.subnook.app.ui.components.StatusBarScrim
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.subnook.app.ui.MainViewModel
import com.subnook.app.ui.calendar.CalendarScreen
import com.subnook.app.ui.edit.EditScreen
import com.subnook.app.ui.edit.PresetPickerScreen
import com.subnook.app.ui.home.HomeScreen
import com.subnook.app.ui.settings.SettingsScreen
import com.subnook.app.ui.theme.SubnookTheme

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private val openSubId = mutableLongStateOf(-1L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent {
            val settings by vm.state.collectAsState()
            SubnookTheme(settings.settings.theme) {
                AppRoot(vm, openSubId.longValue) { openSubId.longValue = -1L }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val id = intent?.getLongExtra(EXTRA_SUB_ID, -1L) ?: -1L
        if (id > 0) openSubId.longValue = id
    }

    companion object {
        const val EXTRA_SUB_ID = "sub_id"
    }
}


private val tabs = listOf(
    NavItem("home", "订阅", Icons.Outlined.Layers, Icons.Rounded.Layers),
    NavItem("calendar", "日历", Icons.Outlined.CalendarMonth, Icons.Rounded.CalendarMonth),
    NavItem("settings", "设置", Icons.Outlined.Settings, Icons.Rounded.Settings),
)

object Routes {
    const val PRESETS = "presets"
    const val STATS = "stats"
    const val MANAGE_PAY = "manage_pay"
    const val MANAGE_PRESETS = "manage_presets"
    fun edit(id: Long = 0, preset: String? = null, name: String? = null, installment: Boolean = false) =
        "edit?id=$id&preset=${preset ?: ""}&name=${android.net.Uri.encode(name ?: "")}&inst=$installment"
}

@Composable
private fun AppRoot(vm: MainViewModel, openSubId: Long, consumeOpen: () -> Unit) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showBar = tabs.any { it.route == route }

    val notifPerm = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) notifPerm.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(openSubId) {
        if (openSubId > 0) {
            nav.navigate(Routes.edit(openSubId))
            consumeOpen()
        }
    }
    LifecycleResumeEffect(Unit) {
        vm.onResume()
        onPauseOrDispose { }
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbar, Modifier.padding(bottom = if (showBar) 84.dp else 16.dp)) { d ->
                androidx.compose.material3.Snackbar(
                    d, shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            AnimatedVisibility(showBar, enter = fadeIn(), exit = fadeOut()) {
                FloatingNavBar(tabs, route) { nav.switchTab(it) }
            }
        },
    ) { _ ->
        // 内容延伸到悬浮导航栏下方，各页面自己留出底部空间
        NavHost(
            nav, startDestination = "home",
            enterTransition = { fadeIn(androidx.compose.animation.core.tween(220)) },
            exitTransition = { fadeOut(androidx.compose.animation.core.tween(180)) },
        ) {
            composable("home") {
                androidx.compose.foundation.layout.Box {
                    HomeScreen(
                        vm,
                        onAddSubscription = { nav.navigate(Routes.PRESETS) },
                        onAddInstallment = { nav.navigate(Routes.edit(installment = true)) },
                        onOpen = { nav.navigate(Routes.edit(it)) },
                        onStats = { nav.navigate(Routes.STATS) },
                    )
                    StatusBarScrim()
                }
            }
            composable("calendar") {
                androidx.compose.foundation.layout.Box {
                    CalendarScreen(vm, onOpen = { nav.navigate(Routes.edit(it)) })
                    StatusBarScrim()
                }
            }
            composable("settings") {
                androidx.compose.foundation.layout.Box {
                    SettingsScreen(
                        vm = vm,
                        onManagePay = { nav.navigate(Routes.MANAGE_PAY) },
                        onManagePresets = { nav.navigate(Routes.MANAGE_PRESETS) },
                    )
                    StatusBarScrim()
                }
            }
            composable(Routes.MANAGE_PAY) {
                com.subnook.app.ui.settings.PayManageScreen(vm, onBack = { nav.popBackStack() })
            }
            composable(Routes.MANAGE_PRESETS) {
                com.subnook.app.ui.settings.PresetManageScreen(vm, onBack = { nav.popBackStack() })
            }
            composable(Routes.STATS) {
                com.subnook.app.ui.stats.StatsScreen(vm, onBack = { nav.popBackStack() }, onOpen = { nav.navigate(Routes.edit(it)) })
            }
            composable(Routes.PRESETS) {
                val st by vm.state.collectAsState()
                PresetPickerScreen(
                    customPresets = st.settings.customPresets,
                    disabledPresets = st.settings.disabledPresets.toSet(),
                    onBack = { nav.popBackStack() },
                    onPick = { key, query ->
                        nav.navigate(Routes.edit(0, key, if (key == null) query else null)) { popUpTo(Routes.PRESETS) { inclusive = true } }
                    },
                )
            }
            composable(
                "edit?id={id}&preset={preset}&name={name}&inst={inst}",
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType; defaultValue = 0L },
                    navArgument("preset") { type = NavType.StringType; defaultValue = "" },
                    navArgument("name") { type = NavType.StringType; defaultValue = "" },
                    navArgument("inst") { type = NavType.BoolType; defaultValue = false },
                ),
            ) { entry ->
                EditScreen(
                    vm = vm,
                    id = entry.arguments?.getLong("id") ?: 0L,
                    presetKey = entry.arguments?.getString("preset").orEmpty().ifBlank { null },
                    prefillName = entry.arguments?.getString("name").orEmpty(),
                    installment = entry.arguments?.getBoolean("inst") ?: false,
                    onBack = { nav.popBackStack() },
                )
            }
        }
    }
}

private fun NavHostController.switchTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}
