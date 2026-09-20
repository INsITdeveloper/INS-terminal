package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.system.GovernorMode
import com.example.ui.InsViewModel
import com.example.ui.components.CyberCard
import com.example.ui.components.CyberSectionTitle
import com.example.ui.theme.*

@Composable
fun ProfilesResetScreen(
    viewModel: InsViewModel,
    modifier: Modifier = Modifier
) {
    val guardState by viewModel.guardState.collectAsState()
    val netState by viewModel.networkState.collectAsState()
    val perfState by viewModel.perfState.collectAsState()
    val dispState by viewModel.displayState.collectAsState()

    var showResetConfirmDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(CyberVoid)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
    ) {

        item {
            CyberSectionTitle(
                title = "DEFAULT SYSTEM RESTORE & SAFETY",
                icon = Icons.Default.Security,
                accentColor = NeonCrimson
            )
        }

        item {
            CyberCard(borderColor = NeonCrimson, glowEffect = true) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "1-Click Restore to Phone Defaults",
                            style = MaterialTheme.typography.titleMedium,
                            color = TermWhite,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Instantly roll back all DNS, TCP, Display, and Governor tweaks to OEM factory baseline.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TermMuted
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.Restore,
                        contentDescription = null,
                        tint = NeonCrimson,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = { showResetConfirmDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("restore_stock_defaults_main_btn"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonCrimson,
                        contentColor = CyberVoid
                    )
                ) {
                    Icon(imageVector = Icons.Default.RestartAlt, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "RESTORE PHONE OEM DEFAULTS",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(CyberSurfaceDark)
                        .border(BorderStroke(1.dp, CyberBorder), RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Volatile Session Guard",
                                color = TermWhite,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(NeonEmerald.copy(alpha = 0.2f))
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "REVERT ON REBOOT",
                                    color = NeonEmerald,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                        Text(
                            text = "When ON, all system tweaks are held in volatile memory and automatically revert to stock when the device restarts.",
                            color = TermMuted,
                            fontSize = 11.sp
                        )
                    }

                    Switch(
                        checked = guardState.isVolatileSessionActive,
                        onCheckedChange = { viewModel.toggleVolatileSession(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = NeonEmerald,
                            checkedTrackColor = NeonEmerald.copy(alpha = 0.3f),
                            uncheckedTrackColor = CyberBorder
                        )
                    )
                }
            }
        }

        item {
            CyberSectionTitle(
                title = "LIVE CONFIG VS OEM BASELINE",
                icon = Icons.Default.CompareArrows,
                accentColor = NeonCyan
            )
        }

        item {
            val snapshot = guardState.stockSnapshot
            CyberCard(borderColor = NeonCyan) {
                Text(
                    text = "Hardware Baseline: ${snapshot.deviceModel} (${snapshot.androidVersion})",
                    color = NeonCyan,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(10.dp))

                ComparisonRow(
                    label = "DNS Provider",
                    currentVal = netState.activeDnsName.split(" ")[0],
                    stockVal = snapshot.defaultDns
                )
                ComparisonRow(
                    label = "TCP Congestion",
                    currentVal = netState.tcpAlgorithm,
                    stockVal = snapshot.defaultTcpCongestion
                )
                ComparisonRow(
                    label = "Animation Scale",
                    currentVal = "${dispState.animationScale}x",
                    stockVal = "${snapshot.defaultAnimationScale}x"
                )
                ComparisonRow(
                    label = "Refresh Rate",
                    currentVal = "${dispState.refreshRateHz}Hz",
                    stockVal = "${snapshot.defaultRefreshRate}Hz"
                )
                ComparisonRow(
                    label = "CPU Mode",
                    currentVal = perfState.activeGovernor.title.split(" ")[0],
                    stockVal = "Stock OEM"
                )
                ComparisonRow(
                    label = "Swappiness",
                    currentVal = "${perfState.swappiness}%",
                    stockVal = "${snapshot.defaultSwappiness}%"
                )
            }
        }

        item {
            CyberSectionTitle(
                title = "PRESET SYSTEM PROFILES",
                icon = Icons.Default.Tune,
                accentColor = NeonEmerald
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PresetProfileCard(
                    title = "⚡ Extreme Gaming Rig",
                    description = "Turbo CPU Governor, 120Hz refresh, BBR TCP, 0.25x Lightning UI",
                    color = NeonCyan,
                    onClick = {
                        viewModel.setGovernorMode(GovernorMode.TURBO)
                        viewModel.setTcpAlgorithm("BBR")
                        viewModel.setRefreshRate(120)
                        viewModel.setAnimationScale(0.25f)
                    }
                )

                PresetProfileCard(
                    title = "🔋 Ultra Eco Battery Guard",
                    description = "Eco CPU Governor, 60Hz display, Aggressive Doze, 30% Swappiness",
                    color = NeonEmerald,
                    onClick = {
                        viewModel.setGovernorMode(GovernorMode.ECO)
                        viewModel.setRefreshRate(60)
                        viewModel.setAnimationScale(1.0f)
                    }
                )

                PresetProfileCard(
                    title = "🌐 Low-Latency Stream & 5G",
                    description = "Cloudflare 1.1.1.1 DNS, BBR Congestion, MTU 1500, Touch 360Hz",
                    color = NeonViolet,
                    onClick = {
                        viewModel.selectDns(viewModel.netEngine.availableDnsServers[0])
                        viewModel.setTcpAlgorithm("BBR")
                        viewModel.optimizeNetwork()
                    }
                )

                PresetProfileCard(
                    title = "📦 Stock Factory Standard",
                    description = "100% Unmodified original phone parameters",
                    color = TermMuted,
                    onClick = {
                        viewModel.revertToStockDefaults()
                    }
                )
            }
        }
    }

    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            title = {
                Text(
                    text = "Confirm Restore to OEM Defaults?",
                    color = NeonCrimson,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            },
            text = {
                Text(
                    text = "This will immediately revert all network DNS routing, TCP congestion algorithms, animation speeds, refresh rates, and governor tweaks back to OEM phone defaults.",
                    color = TermWhite,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.revertToStockDefaults()
                        showResetConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCrimson, contentColor = CyberVoid)
                ) {
                    Text(text = "CONFIRM RESTORE", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmDialog = false }) {
                    Text(text = "CANCEL", color = TermMuted, fontFamily = FontFamily.Monospace)
                }
            },
            containerColor = CyberSurfaceCard,
            shape = RoundedCornerShape(14.dp)
        )
    }
}

@Composable
fun ComparisonRow(label: String, currentVal: String, stockVal: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(CyberSurfaceDark)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = TermWhite, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = currentVal, color = NeonCyan, fontWeight = FontWeight.Bold, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            Text(text = "  (OEM: $stockVal)", color = TermMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
fun PresetProfileCard(
    title: String,
    description: String,
    color: Color,
    onClick: () -> Unit
) {
    CyberCard(borderColor = color) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = TermWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = description,
                    color = TermMuted,
                    fontSize = 11.sp
                )
            }

            Button(
                onClick = onClick,
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = CyberVoid),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(text = "APPLY", fontWeight = FontWeight.Bold, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}
