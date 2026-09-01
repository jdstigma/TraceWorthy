package com.traceworthy.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.traceworthy.app.ui.theme.Coral
import com.traceworthy.app.ui.theme.CoralDeep
import com.traceworthy.app.ui.theme.Navy
import com.traceworthy.app.ui.theme.SlateLight
import com.traceworthy.app.ui.theme.Teal
import com.traceworthy.app.ui.theme.TealDeep
import com.traceworthy.app.ui.theme.TraceWorthyTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CaseStore.ensureMigrated(this)
        setContent {
            var themeMode by remember { mutableStateOf(SettingsStore.themeMode(this@MainActivity)) }
            val dark = when (themeMode) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }
            TraceWorthyTheme(darkTheme = dark) {
                var accepted by remember { mutableStateOf(AgreementStore.isAccepted(this@MainActivity)) }
                if (accepted) {
                    TraceWorthyApp(
                        themeMode = themeMode,
                        onThemeModeChange = {
                            themeMode = it
                            SettingsStore.setThemeMode(this@MainActivity, it)
                        },
                    )
                } else {
                    AgreementScreen(
                        onAccept = {
                            AgreementStore.setAccepted(this@MainActivity)
                            accepted = true
                        },
                        onDecline = { finish() },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TraceWorthyApp(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    var cases by remember { mutableStateOf(CaseStore.all(context).ifEmpty { listOf(CaseStore.ensureAtLeastOne(context)) }) }
    var activeId by remember { mutableStateOf(CaseStore.activeCaseId(context) ?: cases.first().id) }
    val activeCase = cases.firstOrNull { it.id == activeId } ?: cases.first()
    var myInfo by remember { mutableStateOf(MyInfoStore.load(context)) }

    var current by remember { mutableStateOf<Destination>(Destination.Case(CaseScreen.Storyboard)) }
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG)
                == PackageManager.PERMISSION_GRANTED
        )
    }
    var entries by remember { mutableStateOf<List<CallEntry>>(emptyList()) }
    var knownCallersVersion by remember { mutableStateOf(0) }

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        granted = isGranted
        if (isGranted) entries = CallLogRepository.readAll(context)
    }

    LaunchedEffect(granted) {
        if (granted) entries = CallLogRepository.readAll(context)
    }

    fun refreshEntries() {
        if (granted) entries = CallLogRepository.readAll(context)
    }

    fun onKnownCallersChanged() {
        knownCallersVersion++   // refresh the Settings list even without call-log permission
        refreshEntries()        // re-tag the log so the evidence view updates
    }

    // Everything evidence-facing (analysis, flagged list, documents, CSV) runs on
    // the log minus the user's "known callers"; the Call log screen keeps the raw list.
    val evidenceEntries = remember(entries) { entries.filterNot { it.isSafeListed } }

    fun saveCase(updated: Case) {
        cases = CaseStore.upsert(context, updated)
    }

    fun switchCase(id: String) {
        activeId = id
        CaseStore.setActiveCaseId(context, id)
        current = Destination.Case(CaseScreen.Storyboard)
    }

    fun createCase(type: CaseType) {
        val c = Case(type = type)
        cases = CaseStore.upsert(context, c)
        switchCase(c.id)
    }

    fun deleteCase(c: Case) {
        cases = CaseStore.delete(context, c.id)
        if (cases.isEmpty()) cases = listOf(CaseStore.ensureAtLeastOne(context))
        switchCase(CaseStore.activeCaseId(context) ?: cases.first().id)
    }

    fun go(dest: Destination) {
        current = dest
        scope.launch { drawerState.close() }
    }

    val phoneScreens = setOf(CaseScreen.Storyboard, CaseScreen.CallLog, CaseScreen.Analysis, CaseScreen.FlaggedNumbers)

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            DrawerSheet(
                activeCase = activeCase,
                current = current,
                onSelect = { go(it) },
            )
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        CaseSwitcherTitle(
                            title = if (current is Destination.Case) activeCase.title else current.title,
                            cases = cases,
                            activeId = activeId,
                            onSwitch = { switchCase(it) },
                            onNew = { go(Destination.NewCase) },
                            enabled = current is Destination.Case,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Filled.Menu, contentDescription = "Open menu")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                )
            },
            containerColor = MaterialTheme.colorScheme.background,
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize()) {
                val onPhoneScreen = (current as? Destination.Case)?.screen in phoneScreens
                if (!granted && onPhoneScreen) {
                    PermissionBanner(
                        onGrant = { permissionLauncher.launch(Manifest.permission.READ_CALL_LOG) }
                    )
                }
                when (val dest = current) {
                    is Destination.Case -> when (dest.screen) {
                        CaseScreen.Storyboard -> CaseStoryboardScreen(
                            case = activeCase, myInfo = myInfo, entries = evidenceEntries, granted = granted,
                            onNavigate = { go(Destination.Case(it)) },
                            onCaseChange = { saveCase(it) },
                        )
                        CaseScreen.CaseDetail -> CaseDetailScreen(
                            case = activeCase, myInfo = myInfo, onSave = { saveCase(it) },
                        )
                        CaseScreen.CallLog -> CallLogScreen(
                            entries,
                            onRefresh = { refreshEntries() },
                            onKnownCallersChanged = { onKnownCallersChanged() },
                        )
                        CaseScreen.Analysis -> AnalysisScreen(evidenceEntries, onNotesChanged = { refreshEntries() })
                        CaseScreen.FlaggedNumbers -> FlaggedNumbersScreen(
                            evidenceEntries,
                            onKnownCallersChanged = { onKnownCallersChanged() },
                        )
                        CaseScreen.CallTrace -> CallTraceScreen()
                        CaseScreen.FraudItems -> FraudItemsScreen(case = activeCase, onSave = { saveCase(it) })
                        CaseScreen.Documents -> DocumentsScreen(
                            entries = entries, case = activeCase, myInfo = myInfo,
                            onEditInfo = { go(Destination.Shared(SharedScreen.MyInfo)) },
                        )
                    }
                    is Destination.Shared -> when (dest.screen) {
                        SharedScreen.Cases -> CaseListScreen(
                            cases = cases, activeId = activeId,
                            onOpen = { switchCase(it.id) },
                            onNew = { go(Destination.NewCase) },
                            onDelete = { deleteCase(it) },
                        )
                        SharedScreen.Learn -> LearnScreen()
                        SharedScreen.StateHelp -> StateResourcesScreen(myInfo)
                        SharedScreen.MyInfo -> MyInfoScreen(myInfo, onSave = {
                            myInfo = it
                            MyInfoStore.save(context, it)
                        })
                        SharedScreen.Settings -> SettingsScreen(
                            current = SettingsStore.flagThresholdSeconds(context),
                            onSave = { seconds ->
                                SettingsStore.setFlagThresholdSeconds(context, seconds)
                                refreshEntries()
                            },
                            knownCallers = remember(knownCallersVersion) {
                                SafeNumberStore.all(context).toList().sorted()
                            },
                            onRemoveKnownCaller = { number ->
                                SafeNumberStore.remove(context, number)
                                onKnownCallersChanged()
                            },
                            themeMode = themeMode,
                            onThemeModeChange = onThemeModeChange,
                        )
                    }
                    Destination.NewCase -> NewCaseScreen(
                        existing = cases,
                        onCreate = { createCase(it) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CaseSwitcherTitle(
    title: String,
    cases: List<Case>,
    activeId: String,
    onSwitch: (String) -> Unit,
    onNew: () -> Unit,
    enabled: Boolean,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = if (enabled) Modifier.clickable { open = true } else Modifier,
        ) {
            Text(title, fontWeight = FontWeight.SemiBold)
            if (enabled) Icon(Icons.Filled.ArrowDropDown, contentDescription = "Switch case")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            cases.forEach { c ->
                DropdownMenuItem(
                    text = { Text(c.title + if (c.id == activeId) "  ✓" else "") },
                    onClick = { open = false; onSwitch(c.id) },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("New case…") },
                leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                onClick = { open = false; onNew() },
            )
        }
    }
}

@Composable
private fun DrawerSheet(
    activeCase: Case,
    current: Destination,
    onSelect: (Destination) -> Unit,
) {
    ModalDrawerSheet(
        drawerContainerColor = Navy,
        modifier = Modifier.fillMaxWidth(0.80f),
    ) {
        Row(
            Modifier.padding(start = 20.dp, top = 24.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(shape = RoundedCornerShape(10.dp), color = Coral, modifier = Modifier.size(36.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Shield, contentDescription = null, tint = CoralDeep, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("TraceWorthy", color = androidx.compose.ui.graphics.Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text("Evidence, organized", color = SlateLight, fontSize = 12.sp)
            }
        }

        DrawerGroupLabel(activeCase.title.uppercase())
        activeCase.type.drawerScreens.forEach { screen ->
            DrawerItem(
                title = screen.title,
                icon = screen.icon,
                selected = (current as? Destination.Case)?.screen == screen,
                onClick = { onSelect(Destination.Case(screen)) },
            )
        }

        Spacer(Modifier.height(12.dp))
        DrawerGroupLabel("EVERYTHING ELSE")
        SharedScreen.entries.forEach { screen ->
            DrawerItem(
                title = screen.title,
                icon = screen.icon,
                selected = (current as? Destination.Shared)?.screen == screen,
                onClick = { onSelect(Destination.Shared(screen)) },
            )
        }
    }
}

@Composable
private fun DrawerGroupLabel(text: String) {
    Text(
        text,
        color = SlateLight,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 28.dp, top = 4.dp, bottom = 4.dp),
    )
}

@Composable
private fun DrawerItem(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    NavigationDrawerItem(
        label = { Text(title) },
        icon = { Icon(icon, contentDescription = null) },
        selected = selected,
        onClick = onClick,
        colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = Teal,
            selectedTextColor = TealDeep,
            selectedIconColor = TealDeep,
            unselectedContainerColor = Navy,
            unselectedTextColor = SlateLight,
            unselectedIconColor = SlateLight,
        ),
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
    )
}

@Composable
private fun PermissionBanner(onGrant: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            Modifier.padding(14.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Call log access needed",
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    "So TraceWorthy can build your evidence record.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Spacer(Modifier.width(12.dp))
            Button(onClick = onGrant, shape = RoundedCornerShape(10.dp)) { Text("Grant") }
        }
    }
}
