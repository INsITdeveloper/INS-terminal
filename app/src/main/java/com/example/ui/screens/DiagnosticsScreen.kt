package com.example.ui.screens

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.InsViewModel
import com.example.ui.components.CyberCard
import com.example.ui.components.CyberSectionTitle
import com.example.ui.theme.*

@Composable
fun DiagnosticsScreen(
    viewModel: InsViewModel,
    modifier: Modifier = Modifier
) {
    val telemetry by viewModel.telemetryState.collectAsState()
    val perfState by viewModel.perfState.collectAsState()
    val netState by viewModel.networkState.collectAsState()

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
                title = "HARDWARE & KERNEL TELEMETRY",
                icon = Icons.Default.Analytics,
                accentColor = NeonCyan
            )
        }

        item {
            CyberCard(borderColor = NeonCyan) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "CPU Cluster Load & Clock",
                            style = MaterialTheme.typography.titleMedium,
                            color = TermWhite,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${telemetry.hardwareSoc} • ${Runtime.getRuntime().availableProcessors()} Active Cores",
                            color = TermMuted,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Text(
                        text = "${telemetry.cpuUsagePercent}%",
                        color = if (telemetry.cpuUsagePercent > 60) NeonCrimson else NeonCyan,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                LinearProgressIndicator(
                    progress = { telemetry.cpuUsagePercent / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp)),
                    color = if (telemetry.cpuUsagePercent > 60) NeonCrimson else NeonCyan,
                    trackColor = CyberSurfaceDark
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (telemetry.cpuCores.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        telemetry.cpuCores.take(3).forEach { core ->
                            CorePill(name = core.name, freq = core.frequencyGhz, load = core.loadPercent)
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CorePill(name = "LITTLE (0-3)", freq = "1.80 GHz", load = telemetry.cpuUsagePercent.coerceAtLeast(10))
                        CorePill(name = "MID (4-6)", freq = "2.85 GHz", load = (telemetry.cpuUsagePercent * 1.1).toInt().coerceIn(0, 100))
                        CorePill(name = "PRIME (7)", freq = "3.36 GHz", load = (telemetry.cpuUsagePercent * 1.3).toInt().coerceIn(0, 100))
                    }
                }
            }
        }

        item {
            CyberCard(borderColor = NeonEmerald) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "RAM & ZRAM Memory Distribution",
                            style = MaterialTheme.typography.titleMedium,
                            color = TermWhite,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${telemetry.ramUsedMb} MB / ${telemetry.ramTotalMb} MB Allocated",
                            color = TermMuted,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Text(
                        text = "${telemetry.ramUsagePercent}% ALLOC",
                        color = NeonEmerald,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                LinearProgressIndicator(
                    progress = { (telemetry.ramUsagePercent / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp)),
                    color = NeonEmerald,
                    trackColor = CyberSurfaceDark
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    InfoTile(
                        label = "Physical RAM",
                        value = "${String.format("%.1f", telemetry.ramTotalMb / 1024.0)} GB",
                        modifier = Modifier.weight(1f)
                    )
                    InfoTile(
                        label = "ZRAM Swap",
                        value = "${String.format("%.1f", telemetry.zramTotalMb / 1024.0)} GB",
                        modifier = Modifier.weight(1f)
                    )
                    InfoTile(
                        label = "Swappiness",
                        value = "${perfState.swappiness}%",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        item {
            CyberCard(borderColor = NeonViolet) {
                Text(
                    text = "Bandwidth Throughput & Thermals",
                    style = MaterialTheme.typography.titleMedium,
                    color = TermWhite,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    InfoTile(
                        label = "Download (RX)",
                        value = String.format("%.1f kB/s", telemetry.networkRxKbps),
                        valueColor = NeonCyan,
                        modifier = Modifier.weight(1f)
                    )
                    InfoTile(
                        label = "Upload (TX)",
                        value = String.format("%.1f kB/s", telemetry.networkTxKbps),
                        valueColor = NeonEmerald,
                        modifier = Modifier.weight(1f)
                    )
                    InfoTile(
                        label = "Thermal Zone",
                        value = String.format("%.1f°C", telemetry.cpuTempC),
                        valueColor = NeonAmber,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    InfoTile(
                        label = "Battery Voltage",
                        value = "${telemetry.batteryVoltageMv} mV",
                        modifier = Modifier.weight(1f)
                    )
                    InfoTile(
                        label = "Display FPS",
                        value = "${telemetry.activeFps} FPS",
                        valueColor = NeonViolet,
                        modifier = Modifier.weight(1f)
                    )
                    InfoTile(
                        label = "System Uptime",
                        value = telemetry.uptimeFormatted,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        item {
            CyberCard(borderColor = CyberBorder) {
                Text(
                    text = "Universal System Environment Properties",
                    style = MaterialTheme.typography.titleMedium,
                    color = TermWhite,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                PropRow(key = "OS Version", value = telemetry.androidVersion)
                PropRow(key = "API Level", value = "API ${Build.VERSION.SDK_INT}")
                PropRow(key = "Device Model", value = telemetry.deviceModel)
                PropRow(key = "Hardware SoC", value = telemetry.hardwareSoc)
                PropRow(key = "ABI Support", value = Build.SUPPORTED_ABIS.joinToString(", "))
                PropRow(key = "Kernel Release", value = telemetry.kernelRelease)
                PropRow(key = "Engine Subsystem", value = "AOPtimize Universal Live Engine")
            }
        }
    }
}

@Composable
fun CorePill(name: String, freq: String, load: Int) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(CyberSurfaceDark)
            .border(BorderStroke(0.8.dp, CyberBorder), RoundedCornerShape(6.dp))
            .padding(8.dp)
    ) {
        Column {
            Text(text = name, color = TermMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            Text(text = freq, color = NeonCyan, fontWeight = FontWeight.Bold, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            Text(text = "$load% load", color = TermWhite, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
fun InfoTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = TermWhite
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(CyberSurfaceDark)
            .border(BorderStroke(0.8.dp, CyberBorder), RoundedCornerShape(6.dp))
            .padding(8.dp)
    ) {
        Column {
            Text(text = label, color = TermMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = value, color = valueColor, fontWeight = FontWeight.Bold, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
fun PropRow(key: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = key, color = TermMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        Text(text = value, color = TermWhite, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
    }
}
