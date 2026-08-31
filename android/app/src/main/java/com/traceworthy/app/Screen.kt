package com.traceworthy.app

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.FolderShared
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Screens that belong to a specific case. The drawer shows the subset a case type
 * declares in [CaseType.drawerScreens]; the app opens to [Storyboard].
 */
enum class CaseScreen(val title: String, val icon: ImageVector) {
    Storyboard("Storyboard", Icons.Filled.Map),
    CaseDetail("Case details", Icons.Filled.Tune),
    CallLog("Call log", Icons.Filled.Phone),
    Analysis("Analysis", Icons.Filled.BarChart),
    FlaggedNumbers("Flagged numbers", Icons.Filled.Flag),
    CallTrace("Call trace (*57)", Icons.Filled.TrackChanges),
    Documents("Documents", Icons.Filled.Description),
}

/** Screens that are the same across every case. Always in the drawer. */
enum class SharedScreen(val title: String, val icon: ImageVector) {
    Cases("Cases", Icons.Filled.FolderShared),
    Learn("Learn", Icons.AutoMirrored.Filled.MenuBook),
    StateHelp("State help", Icons.Filled.AccountBalance),
    MyInfo("My info", Icons.Filled.Person),
    Settings("Settings", Icons.Filled.Settings),
}

/** Where the app is currently showing. */
sealed interface Destination {
    data class Case(val screen: CaseScreen) : Destination
    data class Shared(val screen: SharedScreen) : Destination
    /** The transient "pick a type for a new case" screen. */
    data object NewCase : Destination

    val title: String
        get() = when (this) {
            is Case -> screen.title
            is Shared -> screen.title
            NewCase -> "New case"
        }
}
