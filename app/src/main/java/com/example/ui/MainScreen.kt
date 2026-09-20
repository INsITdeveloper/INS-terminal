package com.example.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.CyberTopHeader
import com.example.ui.screens.*
import com.example.ui.theme.*

data class NavTabItem(
    val tab: InsScreenTab,
    val label: String,
    val icon: ImageVector,
    val testTag: String
)

@Composable
fun MainScreen(
    viewModel: InsViewModel,
    modifier: Modifier = Modifier
) {
    val currentTab by viewModel.currentTab.collectAsState()
    val telemetry by viewModel.telemetryState.collectAsState()
    val guardState by viewModel.guardState.collectAsState()

    val navItems = listOf(
        NavTabItem(InsScreenTab.TERMINAL, "TERMINAL", Icons.Default.Terminal, "tab_terminal"),
        NavTabItem(InsScreenTab.TWEAKS, "TWEAKS", Icons.Default.Bolt, "tab_tweaks"),
        NavTabItem(InsScreenTab.NPM_SYMLINK, "NPM/LINK", Icons.Default.Extension, "tab_npm_symlink"),
        NavTabItem(InsScreenTab.PROFILES_RESET, "RESET", Icons.Default.Restore, "tab_reset"),
        NavTabItem(InsScreenTab.DIAGNOSTICS, "HUD", Icons.Default.Analytics, "tab_diagnostics")
    )

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars),
        topBar = {
            CyberTopHeader(
                telemetry = telemetry,
                isVolatileGuard = guardState.isVolatileSessionActive,
                onResetStockClicked = { viewModel.revertToStockDefaults() }
            )
        },
        bottomBar = {
            NavigationBar(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .border(BorderStroke(1.dp, SleekBorder), RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)),
                containerColor = SleekSurface,
                tonalElevation = 6.dp
            ) {
                navItems.forEach { item ->
                    val selected = currentTab == item.tab
                    NavigationBarItem(
                        selected = selected,
                        onClick = { viewModel.selectTab(item.tab) },
                        icon = {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.label,
                                tint = if (selected) SleekIceBlue else SleekTextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        label = {
                            Text(
                                text = item.label,
                                color = if (selected) SleekIceBlue else SleekTextSecondary,
                                fontSize = 10.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                fontFamily = FontFamily.Monospace
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = SleekContainerSlate,
                            selectedIconColor = SleekIceBlue,
                            unselectedIconColor = SleekTextSecondary
                        ),
                        modifier = Modifier.testTag(item.testTag)
                    )
                }
            }
        },
        containerColor = SleekBackground
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(SleekBackground)
        ) {
            Crossfade(targetState = currentTab, label = "screen_transition") { tab ->
                when (tab) {
                    InsScreenTab.TERMINAL -> TerminalScreen(viewModel = viewModel)
                    InsScreenTab.TWEAKS -> TweaksScreen(viewModel = viewModel)
                    InsScreenTab.NPM_SYMLINK -> NpmSymlinkScreen(viewModel = viewModel)
                    InsScreenTab.PROFILES_RESET -> ProfilesResetScreen(viewModel = viewModel)
                    InsScreenTab.DIAGNOSTICS -> DiagnosticsScreen(viewModel = viewModel)
                }
            }
        }
    }
}
