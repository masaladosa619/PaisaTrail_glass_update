package com.paisatrail.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.paisatrail.data.share.ShareInbox
import com.paisatrail.ui.calc.FdRdScreen
import com.paisatrail.ui.calc.TaxScreen
import com.paisatrail.ui.capture.CaptureScreen
import com.paisatrail.ui.components.AuroraBackground
import com.paisatrail.ui.components.LocalReduceMotion
import com.paisatrail.ui.components.bouncyClickable
import com.paisatrail.ui.components.glass
import com.paisatrail.ui.components.riseIn
import com.paisatrail.ui.components.tintedGlass
import com.paisatrail.ui.home.HomeScreen
import com.paisatrail.ui.importer.ImportScreen
import com.paisatrail.ui.planner.PlannerScreen
import com.paisatrail.ui.settings.SettingsScreen
import com.paisatrail.ui.split.AddSharedExpenseScreen
import com.paisatrail.ui.split.SettleScreen
import com.paisatrail.ui.split.SplitScreen
import com.paisatrail.ui.txn.TxnEditScreen

/** Every navigation route in the app, in one place. */
object Routes {
    const val HOME = "home"
    const val SPLIT = "split"
    const val PLANNER = "planner"
    const val SETTINGS = "settings"

    const val TXN = "txn?id={id}"
    const val CAPTURE = "capture"
    const val IMPORT = "import"
    const val SHARED_EXPENSE = "split/expense?friendId={friendId}&groupId={groupId}"
    const val SETTLE = "split/settle/{friendId}"
    const val FD_RD = "calc/fdrd"
    const val TAX = "calc/tax"

    fun txn(id: Long = -1L): String = "txn?id=$id"
    fun sharedExpense(friendId: Long, groupId: Long): String = "split/expense?friendId=$friendId&groupId=$groupId"
    fun settle(friendId: Long): String = "split/settle/$friendId"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab(Routes.HOME, "Home", Icons.Filled.Home),
    Tab(Routes.SPLIT, "Split", Icons.Filled.Groups),
    Tab(Routes.PLANNER, "Planner", Icons.Filled.CalendarMonth),
    Tab(Routes.SETTINGS, "Settings", Icons.Filled.Settings)
)

private fun isTabRoute(route: String?): Boolean = TABS.any { it.route == route }

/** Moving between two bottom-bar tabs fades through; anything else pushes and pops sideways. */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.isTabSwitch(): Boolean =
    isTabRoute(initialState.destination.route) && isTabRoute(targetState.destination.route)

private const val PUSH_MS = 340

@Composable
fun AppRoot(inbox: ShareInbox) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val onTab = isTabRoute(currentRoute)
    val reduceMotion = LocalReduceMotion.current
    val haptics = LocalHapticFeedback.current
    var showAddSheet by remember { mutableStateOf(false) }

    // A file arrived (share intent). Open the screen that will take it from the inbox.
    val pending by inbox.pending.collectAsStateWithLifecycle()
    LaunchedEffect(pending) {
        val item = pending ?: return@LaunchedEffect
        showAddSheet = false
        navController.navigate(if (item.isPdf) Routes.IMPORT else Routes.CAPTURE)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // One backdrop for the whole app. Screens are transparent, so their glass picks up its glow.
        AuroraBackground(modifier = Modifier.fillMaxSize())

        Scaffold(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            // Screens handle the status bar themselves; only the bottom bar is accounted for here.
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                AnimatedVisibility(
                    visible = onTab,
                    enter = slideInVertically(spring(dampingRatio = 0.8f, stiffness = 300f)) { it } + fadeIn(),
                    exit = slideOutVertically(tween(200)) { it } + fadeOut(tween(160))
                ) {
                    GlassTabBar(
                        currentRoute = currentRoute,
                        onSelect = { route ->
                            if (route != currentRoute) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                navController.openTab(route)
                            }
                        }
                    )
                }
            },
            floatingActionButton = {
                AnimatedVisibility(
                    visible = currentRoute == Routes.HOME,
                    enter = scaleIn(spring(dampingRatio = 0.5f, stiffness = 320f)) + fadeIn(),
                    exit = scaleOut(tween(140)) + fadeOut(tween(140))
                ) {
                    AddButton(
                        expanded = showAddSheet,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            showAddSheet = true
                        }
                    )
                }
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Routes.HOME,
                modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding),
                enterTransition = { screenEnter(reduceMotion, forward = true) },
                exitTransition = { screenExit(reduceMotion, forward = true) },
                popEnterTransition = { screenEnter(reduceMotion, forward = false) },
                popExitTransition = { screenExit(reduceMotion, forward = false) }
            ) {
                composable(Routes.HOME) {
                    HomeScreen(
                        onOpenTransaction = { id -> navController.navigate(Routes.txn(id)) },
                        onOpenPlanner = { navController.openTab(Routes.PLANNER) }
                    )
                }
                composable(Routes.SPLIT) {
                    SplitScreen(
                        onAddExpense = { friendId, groupId ->
                            navController.navigate(Routes.sharedExpense(friendId, groupId))
                        },
                        onSettle = { friendId -> navController.navigate(Routes.settle(friendId)) }
                    )
                }
                composable(Routes.PLANNER) {
                    PlannerScreen(
                        onOpenFdRd = { navController.navigate(Routes.FD_RD) },
                        onOpenTax = { navController.navigate(Routes.TAX) }
                    )
                }
                composable(Routes.SETTINGS) { SettingsScreen() }

                composable(
                    route = Routes.TXN,
                    arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = -1L })
                ) {
                    TxnEditScreen(onClose = { navController.goBack() })
                }
                composable(Routes.CAPTURE) {
                    CaptureScreen(
                        onClose = { navController.goBack() },
                        onEdit = { id ->
                            navController.navigate(Routes.txn(id)) {
                                popUpTo(Routes.CAPTURE) { inclusive = true }
                            }
                        }
                    )
                }
                composable(Routes.IMPORT) {
                    ImportScreen(onClose = { navController.goBack() })
                }
                composable(
                    route = Routes.SHARED_EXPENSE,
                    arguments = listOf(
                        navArgument("friendId") { type = NavType.LongType; defaultValue = -1L },
                        navArgument("groupId") { type = NavType.LongType; defaultValue = -1L }
                    )
                ) { entry ->
                    AddSharedExpenseScreen(
                        friendId = entry.arguments?.getLong("friendId") ?: -1L,
                        groupId = entry.arguments?.getLong("groupId") ?: -1L,
                        onClose = { navController.goBack() }
                    )
                }
                composable(
                    route = Routes.SETTLE,
                    arguments = listOf(navArgument("friendId") { type = NavType.LongType })
                ) { entry ->
                    SettleScreen(
                        friendId = entry.arguments?.getLong("friendId") ?: -1L,
                        onClose = { navController.goBack() },
                        onOpenSettings = { navController.openTab(Routes.SETTINGS) }
                    )
                }
                composable(Routes.FD_RD) { FdRdScreen(onClose = { navController.goBack() }) }
                composable(Routes.TAX) { TaxScreen(onClose = { navController.goBack() }) }
            }
        }
    }

    if (showAddSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAddSheet = false },
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Add to your trail",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp).riseIn(0)
                )
                AddOption(
                    index = 1,
                    icon = Icons.Filled.Edit,
                    title = "Type it in",
                    subtitle = "Add one expense or income by hand"
                ) {
                    showAddSheet = false
                    navController.navigate(Routes.txn())
                }
                AddOption(
                    index = 2,
                    icon = Icons.Filled.Image,
                    title = "From a payment screenshot",
                    subtitle = "Read on this phone, nothing is uploaded"
                ) {
                    showAddSheet = false
                    navController.navigate(Routes.CAPTURE)
                }
                AddOption(
                    index = 3,
                    icon = Icons.Filled.PictureAsPdf,
                    title = "Import a bank statement",
                    subtitle = "One-month PDF, reviewed before saving"
                ) {
                    showAddSheet = false
                    navController.navigate(Routes.IMPORT)
                }
            }
        }
    }
}

/* ---------- screen transitions ---------- */

private fun AnimatedContentTransitionScope<NavBackStackEntry>.screenEnter(
    reduceMotion: Boolean,
    forward: Boolean
): EnterTransition = when {
    reduceMotion -> fadeIn(tween(120))
    // Tabs: the old screen fades out first, then the new one fades up from slightly smaller.
    isTabSwitch() -> fadeIn(tween(240, delayMillis = 90)) + scaleIn(tween(240, delayMillis = 90), initialScale = 0.96f)
    // Push: the new screen slides in from the right. Pop: the old one comes back from the left.
    forward -> slideInHorizontally(tween(PUSH_MS, easing = FastOutSlowInEasing)) { it / 3 } +
        fadeIn(tween(240, delayMillis = 60))
    else -> slideInHorizontally(tween(PUSH_MS, easing = FastOutSlowInEasing)) { -it / 5 } +
        fadeIn(tween(240, delayMillis = 60))
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.screenExit(
    reduceMotion: Boolean,
    forward: Boolean
): ExitTransition = when {
    reduceMotion -> fadeOut(tween(120))
    isTabSwitch() -> fadeOut(tween(90))
    forward -> slideOutHorizontally(tween(PUSH_MS, easing = FastOutSlowInEasing)) { -it / 5 } + fadeOut(tween(150))
    else -> slideOutHorizontally(tween(PUSH_MS, easing = FastOutSlowInEasing)) { it / 3 } + fadeOut(tween(170))
}

/* ---------- floating glass tab bar ---------- */

@Composable
private fun GlassTabBar(currentRoute: String?, onSelect: (String) -> Unit) {
    val selectedIndex = TABS.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(66.dp)
                .glass(CircleShape, strong = true)
        ) {
            val slot = maxWidth / TABS.size
            // The highlight slides to the chosen tab and settles with a small overshoot.
            val highlightX by animateDpAsState(
                targetValue = slot * selectedIndex,
                animationSpec = spring(dampingRatio = 0.72f, stiffness = 380f),
                label = "tabHighlight"
            )
            Box(
                modifier = Modifier
                    .offset(x = highlightX)
                    .width(slot)
                    .fillMaxHeight()
                    .padding(6.dp)
                    .tintedGlass(CircleShape, MaterialTheme.colorScheme.primary)
            )
            Row(modifier = Modifier.fillMaxSize()) {
                TABS.forEachIndexed { index, tab ->
                    TabItem(
                        tab = tab,
                        selected = index == selectedIndex,
                        onClick = { onSelect(tab.route) },
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
            }
        }
    }
}

@Composable
private fun TabItem(tab: Tab, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val tint by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(220),
        label = "tabTint"
    )
    val iconScale by animateFloatAsState(
        targetValue = if (selected && !LocalReduceMotion.current) 1.16f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "tabIcon"
    )
    Column(
        modifier = modifier.selectable(
            selected = selected,
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            role = Role.Tab,
            onClick = onClick
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            tab.icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(22.dp).graphicsLayer {
                scaleX = iconScale
                scaleY = iconScale
            }
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            tab.label,
            color = tint,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1
        )
    }
}

/* ---------- the + button ---------- */

@Composable
private fun AddButton(expanded: Boolean, onClick: () -> Unit) {
    val reduceMotion = LocalReduceMotion.current
    val glow = MaterialTheme.colorScheme.primary
    // The plus turns into a cross while the sheet is open.
    val turn by animateFloatAsState(
        targetValue = if (expanded) 45f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "addTurn"
    )
    val ripple = if (reduceMotion) {
        null
    } else {
        rememberInfiniteTransition(label = "addGlow").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing)),
            label = "addGlowPhase"
        )
    }
    Box(
        modifier = Modifier
            .size(60.dp)
            .drawBehind {
                // A ring of light that keeps expanding outwards and fading.
                val phase = ripple?.value ?: return@drawBehind
                drawCircle(
                    color = glow.copy(alpha = (1f - phase) * 0.38f),
                    radius = size.minDimension / 2f * (1f + phase * 0.5f)
                )
            },
        contentAlignment = Alignment.Center
    ) {
        FloatingActionButton(
            onClick = onClick,
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = "Add a transaction",
                modifier = Modifier.graphicsLayer { rotationZ = turn }
            )
        }
    }
}

@Composable
private fun AddOption(index: Int, icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .riseIn(index)
            .bouncyClickable(shape = shape, onClick = onClick)
            .glass(shape)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        }
    }
}

/** Switches bottom-bar tab, keeping each tab's own state and a single copy on the back stack. */
private fun NavController.openTab(route: String) {
    navigate(route) {
        popUpTo(Routes.HOME) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Pops only when there is somewhere to go back to, so a double tap can never close Home. */
private fun NavController.goBack() {
    if (previousBackStackEntry != null) popBackStack()
}
