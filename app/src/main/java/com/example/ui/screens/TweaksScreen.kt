package com.example.ui.screens

import android.view.MotionEvent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.InsViewModel
import com.example.ui.components.CyberCard
import com.example.ui.components.CyberSectionTitle
import com.example.ui.theme.*

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TweaksScreen(
    viewModel: InsViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val signalStatus by viewModel.signalStatus.collectAsState()
    val dispState by viewModel.displayState.collectAsState()
    val displayCaps by viewModel.displayCaps.collectAsState()
    val telemetryState by viewModel.telemetryState.collectAsState()
    val perfState by viewModel.perfState.collectAsState()
    val networkState by viewModel.networkState.collectAsState()
    val isOverlayActive by viewModel.isFloatingOverlayActive.collectAsState()
    val activeOverlayCount by viewModel.activeOverlayWindowsCount.collectAsState()
    val freeformStatus by viewModel.freeformStatus.collectAsState()
    val installedApps by viewModel.installedApps.collectAsState()
    val isLoadingApps by viewModel.isLoadingInstalledApps.collectAsState()
    val activeFloatingAppsCount by viewModel.activeFloatingAppsCount.collectAsState()
    val hdCaptureResult by viewModel.hdCaptureResult.collectAsState()
    val isHdRecording by viewModel.isHdRecording.collectAsState()
    val adbStatusMessage by viewModel.adbStatusMessage.collectAsState()
    val isAdbBusy by viewModel.isAdbBusy.collectAsState()
    val privilegeStatus by viewModel.privilegeStatus.collectAsState()
    var adbPairPort by remember { mutableStateOf("") }
    var adbPairCode by remember { mutableStateOf("") }
    var adbConnPort by remember { mutableStateOf("") }

    var searchQuery by remember { mutableStateOf("") }
    var selectedTabApps by remember { mutableStateOf("ALL") }

    LaunchedEffect(Unit) {
        if (installedApps.isEmpty()) {
            viewModel.loadInstalledApps()
        }
    }

    var measuredTouchHz by remember { mutableIntStateOf(120) }
    var touchTouchCount by remember { mutableIntStateOf(0) }
    var isTouchingTestPad by remember { mutableStateOf(false) }
    var lastEventUptimeMs by remember { mutableLongStateOf(0L) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(CyberVoid)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)
    ) {

        item {
            CyberSectionTitle(
                title = "PENGUNCI SINYAL 4G / 5G (NON-ROOT)",
                icon = Icons.Default.Lock,
                accentColor = NeonEmerald
            )
        }

        item {
            CyberCard(borderColor = NeonEmerald, glowEffect = true) {

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.SignalCellularAlt,
                            contentDescription = null,
                            tint = NeonEmerald,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Kunci Sinyal 4G / 5G Only",
                            style = MaterialTheme.typography.titleMedium,
                            color = TermWhite,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(NeonEmerald.copy(alpha = 0.2f))
                            .border(1.dp, NeonEmerald, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "100% WORK NO ROOT",
                            color = NeonEmerald,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Kunci jaringan HP Anda ke 4G (LTE Only) atau 5G (NR Only) secara permanen agar sinyal tidak pernah drop ke 3G atau 2G/E di tempat minim sinyal.",
                    color = TermMuted,
                    fontSize = 11.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = { viewModel.launchRadioInfo(context) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("btn_launch_radio_info"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonEmerald,
                        contentColor = Color.Black
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = Color.Black
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "🎯 BUKA MENU KUNCI SINYAL (RADIO INFO)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = Color.Black
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.launchNetworkOperatorSettings(context) },
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .testTag("btn_operator_settings"),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, NeonCyan),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan)
                    ) {
                        Icon(imageVector = Icons.Default.CellTower, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "JARINGAN",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    OutlinedButton(
                        onClick = { viewModel.launchWifiSettings(context) },
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .testTag("btn_wifi_settings"),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, NeonEmerald),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonEmerald)
                    ) {
                        Icon(imageVector = Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "WI-FI",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    OutlinedButton(
                        onClick = { viewModel.launchDataRoamingSettings(context) },
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .testTag("btn_apn_settings"),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, CyberBorder),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TermWhite)
                    ) {
                        Icon(imageVector = Icons.Default.SettingsInputAntenna, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "APN",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(CyberSurfaceDark)
                        .border(1.dp, CyberBorder, RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "📋 PANDUAN CARA KUNCI SINYAL:",
                            color = NeonAmber,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        GuideStepItem(number = "1", text = "Ketuk tombol hijau 'BUKA MENU KUNCI SINYAL' di atas.")
                        GuideStepItem(number = "2", text = "Di menu sistem yang terbuka, cari menu dropdown 'Set Preferred Network Type' (Atur Jenis Jaringan yang Disukai).")
                        GuideStepItem(number = "3", text = "Pilih 'LTE only' untuk mengunci 4G permanen, atau 'NR only' untuk 5G murni.")
                        GuideStepItem(number = "4", text = "Selesai! Sinyal Anda terkunci permanen tanpa bisa turun ke 3G/2G.")
                    }
                }
            }
        }

        item {
            CyberSectionTitle(
                title = "MONITOR SINYAL & JARINGAN AKTIF",
                icon = Icons.Default.NetworkCheck,
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
                            text = signalStatus.operatorName,
                            color = TermWhite,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${signalStatus.networkType} • ${if (signalStatus.isWifiConnected) "Wi-Fi: " + signalStatus.wifiSsid else "Koneksi Seluler"}",
                            color = NeonCyan,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        for (i in 1..4) {
                            val isActive = i <= signalStatus.signalBars
                            Box(
                                modifier = Modifier
                                    .width(6.dp)
                                    .height((8 + i * 5).dp)
                                    .clip(RoundedCornerShape(1.dp))
                                    .background(
                                        if (isActive) {
                                            when (signalStatus.signalBars) {
                                                4 -> NeonEmerald
                                                3 -> NeonCyan
                                                2 -> NeonAmber
                                                else -> NeonCrimson
                                            }
                                        } else CyberBorder
                                    )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SignalMetricTile(
                        label = "KUAT SINYAL",
                        value = "${signalStatus.signalDbm} dBm",
                        subValue = "${signalStatus.signalAsu} ASU",
                        color = if (signalStatus.signalDbm > -90) NeonEmerald else NeonAmber,
                        modifier = Modifier.weight(1f)
                    )
                    SignalMetricTile(
                        label = "LIVE PING (RTT)",
                        value = "${signalStatus.livePingMs} ms",
                        subValue = "Ke 1.1.1.1 (DNS)",
                        color = if (signalStatus.livePingMs < 35) NeonEmerald else NeonCyan,
                        modifier = Modifier.weight(1f)
                    )
                    SignalMetricTile(
                        label = "STATUS RADIO",
                        value = if (signalStatus.isKeepAliveActive) "CONNECTED" else "STANDBY",
                        subValue = if (signalStatus.isKeepAliveActive) "Anti-Sleep ON" else "Standard RRC",
                        color = if (signalStatus.isKeepAliveActive) NeonEmerald else TermMuted,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        item {
            CyberSectionTitle(
                title = "STABILIZER SINYAL LOW & ANTI-RTO",
                icon = Icons.Default.Bolt,
                accentColor = NeonAmber
            )
        }

        item {
            CyberCard(borderColor = NeonAmber) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Radio Keep-Alive Agresif & Anti-Jitter",
                            style = MaterialTheme.typography.titleMedium,
                            color = TermWhite,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Mencegah radio seluler & Wi-Fi tidur (dormancy). Mengunci radio dalam mode aktif (RRC_CONNECTED) konstan bebas RTO & ping stabil.",
                            color = TermMuted,
                            fontSize = 11.sp
                        )
                    }

                    Switch(
                        checked = signalStatus.isKeepAliveActive,
                        onCheckedChange = { viewModel.toggleRadioKeepAlive(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = NeonAmber,
                            checkedTrackColor = NeonAmber.copy(alpha = 0.3f),
                            uncheckedTrackColor = CyberBorder
                        ),
                        modifier = Modifier.testTag("switch_radio_keep_alive")
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(CyberSurfaceDark)
                        .border(1.dp, if (signalStatus.isKeepAliveActive) NeonAmber.copy(alpha = 0.4f) else CyberBorder, RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Paket Keep-Alive Terkirim: ${signalStatus.packetsSent}",
                                color = TermWhite,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "Potensi RTO Dicegah: ${signalStatus.rtoPreventedCount}",
                                color = NeonAmber,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Text(
                            text = if (signalStatus.isKeepAliveActive) "● AKTIF (MENCEGAH SLEEP)" else "○ NONAKTIF",
                            color = if (signalStatus.isKeepAliveActive) NeonAmber else TermMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Penguat Sinyal 1 ms (Ultra-Low Latency)",
                                color = TermWhite,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(NeonEmerald.copy(alpha = 0.2f))
                                    .border(1.dp, NeonEmerald, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = "TARGET 1 MS",
                                    color = NeonEmerald,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                        Text(
                            text = "Socket TCP_NODELAY + IP_TOS Low Delay. Menghilangkan buffering kernel agar transmisi paket langsung terkirim tanpa antrean Nagle.",
                            color = TermMuted,
                            fontSize = 11.sp
                        )
                    }

                    Switch(
                        checked = signalStatus.isUltraLowLatencyMode,
                        onCheckedChange = { viewModel.toggleUltraLowLatencyPing(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = NeonEmerald,
                            checkedTrackColor = NeonEmerald.copy(alpha = 0.3f),
                            uncheckedTrackColor = CyberBorder
                        ),
                        modifier = Modifier.testTag("switch_ultra_low_latency")
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { viewModel.applyUltraSignalAndCinemaBoost() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .testTag("btn_boost_signal_max"),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonEmerald,
                        contentColor = Color.Black
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.NetworkCheck,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color.Black
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "AKTIFKAN SEMUA PENGUAT SINYAL & CARRIER AGGREGATION ⚡",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = Color.Black
                    )
                }
            }
        }

        item {
            CyberSectionTitle(
                title = "JENDELA MENGAMBANG GAME HUD (MULTI-WINDOW)",
                icon = Icons.Default.Layers,
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Pop-up Floating Window HUD",
                                style = MaterialTheme.typography.titleMedium,
                                color = TermWhite,
                                fontWeight = FontWeight.Bold
                            )
                            if (isOverlayActive) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(NeonCyan.copy(alpha = 0.2f))
                                        .border(1.dp, NeonCyan, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "$activeOverlayCount JENDELA AKTIF",
                                        color = NeonCyan,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                        Text(
                            text = "Jendela melayang yang bisa digeser di atas game/aplikasi. Dukungan multi-window: Anda dapat membuka lebih dari 1 jendela mengambang sekaligus!",
                            color = TermMuted,
                            fontSize = 11.sp
                        )
                    }

                    Switch(
                        checked = isOverlayActive,
                        onCheckedChange = { viewModel.toggleFloatingOverlay(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = NeonCyan,
                            checkedTrackColor = NeonCyan.copy(alpha = 0.3f),
                            uncheckedTrackColor = CyberBorder
                        ),
                        modifier = Modifier.testTag("switch_floating_overlay")
                    )
                }

                if (isOverlayActive) {
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.addFloatingOverlayWindow() },
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .testTag("btn_add_floating_window"),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NeonCyan,
                                contentColor = Color.Black
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = Color.Black
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "TAMBAH JENDELA (+1)",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = Color.Black
                            )
                        }

                        Button(
                            onClick = { viewModel.toggleFloatingOverlay(false) },
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .testTag("btn_close_all_floating"),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CyberSurfaceDark,
                                contentColor = TermWhite
                            ),
                            border = BorderStroke(1.dp, NeonCrimson)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = NeonCrimson
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "TUTUP SEMUA",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = NeonCrimson
                            )
                        }
                    }
                }
            }
        }

        item {
            CyberSectionTitle(
                title = "BUKA APLIKASI DI JENDELA MENGAMBANG (MULTI-APPS)",
                icon = Icons.Default.Launch,
                accentColor = NeonEmerald
            )
        }

        item {
            CyberCard(borderColor = NeonEmerald) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Peluncur Multi-Jendela Bebas",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TermWhite,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(NeonEmerald.copy(alpha = 0.2f))
                                        .border(1.dp, NeonEmerald, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (freeformStatus.isFreeformSupported) "FREEFORM SIAP" else "PERLU SETUP",
                                        color = if (freeformStatus.isFreeformSupported) NeonEmerald else NeonAmber,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                            Text(
                                text = "Buka TikTok, WhatsApp, YouTube, Game, dll secara bersamaan dalam jendela mengambang bebas yang bisa di-resize!",
                                color = TermMuted,
                                fontSize = 11.sp
                            )
                        }

                        IconButton(
                            onClick = { viewModel.loadInstalledApps() },
                            modifier = Modifier.testTag("btn_refresh_installed_apps")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh Apps",
                                tint = NeonEmerald
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Surface(
                        color = CyberSurfaceDark,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, CyberBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Status: ${freeformStatus.activeMode}",
                                    color = if (freeformStatus.isFreeformSupported) NeonEmerald else NeonAmber,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                if (!freeformStatus.isForceResizableEnabled) {
                                    Button(
                                        onClick = { viewModel.enableFreeformSystemWide() },
                                        shape = RoundedCornerShape(6.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = NeonEmerald.copy(alpha = 0.2f),
                                            contentColor = NeonEmerald
                                        ),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Text("AKTIFKAN", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            if (!freeformStatus.isForceResizableEnabled) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Opsi Pengembang: Aktifkan 'Paksa aktivitas dapat diubah ukurannya' & 'Aktifkan jendela freeform'",
                                        color = TermMuted,
                                        fontSize = 10.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    OutlinedButton(
                                        onClick = { viewModel.copyFreeformAdbCommand(context) },
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.height(26.dp)
                                    ) {
                                        Text("SALIN ADB", fontSize = 9.sp, color = TermWhite)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Cari aplikasi (contoh: WhatsApp, TikTok, YouTube)...", fontSize = 11.sp, color = TermMuted) },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = TermMuted, modifier = Modifier.size(16.dp))
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear", tint = TermMuted, modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonEmerald,
                            unfocusedBorderColor = CyberBorder,
                            focusedContainerColor = CyberSurfaceDark,
                            unfocusedContainerColor = CyberSurfaceDark,
                            focusedTextColor = TermWhite,
                            unfocusedTextColor = TermWhite
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    if (isLoadingApps) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = NeonEmerald,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Memindai aplikasi di perangkat...", color = TermMuted, fontSize = 12.sp)
                        }
                    } else {
                        val filtered = installedApps.filter {
                            searchQuery.isEmpty() || it.appName.contains(searchQuery, ignoreCase = true) || it.packageName.contains(searchQuery, ignoreCase = true)
                        }.take(25)

                        if (filtered.isEmpty()) {
                            Text(
                                text = if (installedApps.isEmpty()) "Tekan ikon refresh di atas untuk memuat daftar aplikasi." else "Tidak ada aplikasi yang cocok.",
                                color = TermMuted,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        } else {
                            Text(
                                text = "PILIH APLIKASI UNTUK DIBUKA MELAYANG (${filtered.size} ditampilkan):",
                                color = NeonEmerald,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                for (app in filtered) {
                                    Surface(
                                        color = CyberSurfaceDark,
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, CyberBorder.copy(alpha = 0.6f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 10.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                modifier = Modifier.weight(1f),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(32.dp)
                                                        .clip(CircleShape)
                                                        .background(CyberVoid)
                                                        .border(1.dp, CyberBorder, CircleShape),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Apps,
                                                        contentDescription = null,
                                                        tint = NeonEmerald,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Column {
                                                    Text(
                                                        text = app.appName,
                                                        color = TermWhite,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1
                                                    )
                                                    Text(
                                                        text = app.packageName,
                                                        color = TermMuted,
                                                        fontSize = 10.sp,
                                                        maxLines = 1,
                                                        fontFamily = FontFamily.Monospace
                                                    )
                                                }
                                            }

                                            Button(
                                                onClick = { viewModel.launchAppInFloatingWindow(app) },
                                                shape = RoundedCornerShape(6.dp),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = NeonEmerald,
                                                    contentColor = Color.Black
                                                ),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                modifier = Modifier.height(30.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.OpenInNew,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(12.dp),
                                                    tint = Color.Black
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "BUKA MELAYANG",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = Color.Black
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            CyberSectionTitle(
                title = "HD SCREENSHOT & REKAMAN (NATIVE RES)",
                icon = Icons.Default.PhotoCamera,
                accentColor = NeonCyan
            )
        }

        item {
            CyberCard(borderColor = NeonCyan) {
                Text(
                    text = "Screenshot PNG lossless di resolusi panel penuh (tanpa downscale, tidak pecah saat di-zoom). Rekaman MP4 H.264 bitrate tinggi.",
                    color = TermMuted,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.captureHdScreenshot() },
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .testTag("btn_hd_screenshot"),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyberSurfaceDark,
                            contentColor = TermWhite
                        ),
                        border = BorderStroke(1.dp, NeonCyan)
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp), tint = NeonCyan)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("SCREENSHOT HD", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                    Button(
                        onClick = { viewModel.toggleHdRecording(!isHdRecording) },
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .testTag("btn_hd_record"),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isHdRecording) NeonCrimson.copy(alpha = 0.25f) else CyberSurfaceDark,
                            contentColor = TermWhite
                        ),
                        border = BorderStroke(1.dp, if (isHdRecording) NeonCrimson else NeonCyan)
                    ) {
                        Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(16.dp), tint = if (isHdRecording) NeonCrimson else NeonCyan)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isHdRecording) "STOP REKAM" else "REKAM HD", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                }
                hdCaptureResult?.let { result ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = result.message,
                        color = if (result.isSuccess) NeonEmerald else NeonCrimson,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        item {
            CyberSectionTitle(
                title = "ADB NIRKABEL (TANPA ROOT)",
                icon = Icons.Default.Wifi,
                accentColor = NeonAmber
            )
        }

        item {
            CyberCard(borderColor = NeonAmber) {
                Text(
                    text = "Mode akses: ${privilegeStatus.activeExecutionMode}",
                    color = NeonAmber,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "ADB tersambung: ${if (privilegeStatus.isAdbConnected) "YA (port ${privilegeStatus.adbPort})" else "BELUM"}",
                    color = if (privilegeStatus.isAdbConnected) NeonEmerald else TermMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Aktifkan 'Wireless debugging' + 'Pair device with pairing code' di Opsi Pengembang. Port pairing BEDA dengan port koneksi.",
                    color = TermMuted,
                    fontSize = 10.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = adbPairPort,
                        onValueChange = { adbPairPort = it },
                        label = { Text("Port pairing", fontSize = 10.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("field_adb_pair_port")
                    )
                    OutlinedTextField(
                        value = adbPairCode,
                        onValueChange = { adbPairCode = it },
                        label = { Text("Kode 6 digit", fontSize = 10.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("field_adb_pair_code")
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { viewModel.adbPair(adbPairPort, adbPairCode) },
                    enabled = !isAdbBusy,
                    modifier = Modifier.fillMaxWidth().height(40.dp).testTag("btn_adb_pair"),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CyberSurfaceDark, contentColor = TermWhite),
                    border = BorderStroke(1.dp, NeonAmber)
                ) {
                    Text("PAIR PERANGKAT", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = adbConnPort,
                    onValueChange = { adbConnPort = it },
                    label = { Text("Port koneksi (opsional)", fontSize = 10.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("field_adb_conn_port")
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.adbAutoConnect() },
                        enabled = !isAdbBusy,
                        modifier = Modifier.weight(1f).height(40.dp).testTag("btn_adb_auto"),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CyberSurfaceDark, contentColor = TermWhite),
                        border = BorderStroke(1.dp, NeonEmerald)
                    ) {
                        Text("AUTO CONNECT", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                    Button(
                        onClick = { viewModel.adbConnect(adbConnPort) },
                        enabled = !isAdbBusy,
                        modifier = Modifier.weight(1f).height(40.dp).testTag("btn_adb_connect"),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CyberSurfaceDark, contentColor = TermWhite),
                        border = BorderStroke(1.dp, NeonCyan)
                    ) {
                        Text("CONNECT", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { viewModel.adbGrantSecureSettings() },
                    enabled = !isAdbBusy && privilegeStatus.isAdbConnected,
                    modifier = Modifier.fillMaxWidth().height(40.dp).testTag("btn_adb_grant"),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CyberSurfaceDark, contentColor = TermWhite),
                    border = BorderStroke(1.dp, NeonViolet)
                ) {
                    Text("GRANT WRITE_SECURE_SETTINGS", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
                adbStatusMessage?.let { msg ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = msg, color = NeonCyan, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }

        item {
            CyberSectionTitle(
                title = "FPS & REFRESH RATE LAYAR",
                icon = Icons.Default.Speed,
                accentColor = NeonViolet
            )
        }

        item {
            CyberCard(borderColor = NeonViolet) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Frekuensi Layar (Refresh Rate)",
                            style = MaterialTheme.typography.titleMedium,
                            color = TermWhite,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Layar Terdeteksi: ${displayCaps.maxHardwareRefreshRate}Hz Maksimal",
                            color = NeonViolet,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(NeonViolet.copy(alpha = 0.2f))
                            .border(1.dp, NeonViolet, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "${dispState.refreshRateHz}Hz AKTIF",
                            color = NeonViolet,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val availableHz = listOf(60, 90, 120, 144)
                    availableHz.forEach { hz ->
                        val isSelected = dispState.refreshRateHz == hz
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) NeonViolet.copy(alpha = 0.25f) else CyberSurfaceDark)
                                .border(1.dp, if (isSelected) NeonViolet else CyberBorder, RoundedCornerShape(8.dp))
                                .clickable { viewModel.setDisplayRefreshRate(hz) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${hz}Hz",
                                color = if (isSelected) NeonViolet else TermWhite,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { viewModel.launchDeveloperSettings(context) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .testTag("btn_dev_options"),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyberSurfaceDark,
                        contentColor = TermWhite
                    ),
                    border = BorderStroke(1.dp, NeonViolet)
                ) {
                    Icon(imageVector = Icons.Default.DeveloperMode, contentDescription = null, modifier = Modifier.size(16.dp), tint = NeonViolet)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "BUKA OPSI PENGEMBANG (DEV OPTIONS)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = TermWhite
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Tip: Ubah 'Skala animasi jendela/transisi' ke 0.5x dan aktifkan 'Paksa refresh rate puncak'.",
                    color = TermMuted,
                    fontSize = 10.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Unlock Grafis Game & Anti-Lag",
                            color = TermWhite,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (dispState.isGameGraphicUnlockerActive)
                                "Grafis Ultra & 90/120 FPS Terbuka (Bypass GOS/Joyose)"
                            else "Bypass frame limiter & driver GPU sistem aktif",
                            color = if (dispState.isGameGraphicUnlockerActive) NeonEmerald else TermMuted,
                            fontSize = 11.sp
                        )
                    }

                    Switch(
                        checked = dispState.isGameGraphicUnlockerActive,
                        onCheckedChange = { viewModel.toggleGameGraphicUnlocker(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = NeonViolet,
                            checkedTrackColor = NeonViolet.copy(alpha = 0.3f),
                            uncheckedTrackColor = CyberBorder
                        ),
                        modifier = Modifier.testTag("switch_game_graphic_unlocker")
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.applyCreateBufferAndHaloSmooth() },
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .testTag("btn_anti_lag_buffer"),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyberSurfaceDark,
                            contentColor = TermWhite
                        ),
                        border = BorderStroke(1.dp, if (dispState.isAntiLagTripleBufferingActive) NeonEmerald else CyberBorder)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = if (dispState.isAntiLagTripleBufferingActive) NeonEmerald else TermMuted
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "FIX DELAY & MACET",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = if (dispState.isAntiLagTripleBufferingActive) NeonEmerald else TermWhite
                        )
                    }

                    Button(
                        onClick = { viewModel.toggleAntiLagTripleBuffering(!dispState.isAntiLagTripleBufferingActive) },
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .testTag("btn_triple_buffer"),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyberSurfaceDark,
                            contentColor = TermWhite
                        ),
                        border = BorderStroke(1.dp, if (dispState.isAntiLagTripleBufferingActive) NeonViolet else CyberBorder)
                    ) {
                        Text(
                            text = if (dispState.isAntiLagTripleBufferingActive) "BUFFER 3X [ON]" else "TRIPLE BUFFER",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = if (dispState.isAntiLagTripleBufferingActive) NeonViolet else TermWhite
                        )
                    }
                }
            }
        }

        item {
            CyberSectionTitle(
                title = "TESTER TOUCH SAMPLING & SENSITIVITAS",
                icon = Icons.Default.TouchApp,
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
                            text = "Uji Polling Rate Touch Layar",
                            style = MaterialTheme.typography.titleMedium,
                            color = TermWhite,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Geser jari di touchpad bawah untuk mengukur respon hardware digitizer nyata.",
                            color = TermMuted,
                            fontSize = 11.sp
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "${measuredTouchHz}Hz",
                            color = NeonCyan,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "TERUKUR",
                            color = TermMuted,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isTouchingTestPad) NeonCyan.copy(alpha = 0.12f) else CyberSurfaceDark)
                        .border(1.dp, if (isTouchingTestPad) NeonCyan else CyberBorder, RoundedCornerShape(10.dp))
                        .pointerInteropFilter { motionEvent ->
                            when (motionEvent.actionMasked) {
                                MotionEvent.ACTION_DOWN -> {
                                    isTouchingTestPad = true
                                    lastEventUptimeMs = motionEvent.eventTime
                                    touchTouchCount = 0
                                    true
                                }
                                MotionEvent.ACTION_MOVE -> {
                                    val now = motionEvent.eventTime
                                    val delta = now - lastEventUptimeMs
                                    if (delta > 0) {
                                        val instantHz = (1000f / delta).toInt().coerceIn(60, 600)

                                        measuredTouchHz = ((measuredTouchHz * 0.7f) + (instantHz * 0.3f)).toInt()
                                        lastEventUptimeMs = now
                                        touchTouchCount++
                                    }
                                    true
                                }
                                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                    isTouchingTestPad = false
                                    true
                                }
                                else -> false
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.TouchApp,
                            contentDescription = null,
                            tint = if (isTouchingTestPad) NeonCyan else TermMuted,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isTouchingTestPad) "Sedang Mengukur: ${measuredTouchHz}Hz ($touchTouchCount Sampel)" else "GESER JARI DI SINI UNTUK UJI TOUCH HZ",
                            color = if (isTouchingTestPad) NeonCyan else TermMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.launchPointerSpeedSettings(context) },
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .testTag("btn_pointer_speed"),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyberSurfaceDark,
                            contentColor = TermWhite
                        ),
                        border = BorderStroke(1.dp, NeonCyan)
                    ) {
                        Icon(imageVector = Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(15.dp), tint = NeonCyan)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "KECEPATAN PENUNJUK",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Button(
                        onClick = { viewModel.signalLocker.launchAccessibilitySettings() },
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .testTag("btn_accessibility"),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyberSurfaceDark,
                            contentColor = TermWhite
                        ),
                        border = BorderStroke(1.dp, CyberBorder)
                    ) {
                        Icon(imageVector = Icons.Default.Accessibility, contentDescription = null, modifier = Modifier.size(15.dp), tint = TermWhite)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "TOUCH DELAY",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Atur Rasio Polling Sentuhan (Scroll & Game):",
                    color = TermMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(120 to "120Hz", 240 to "240Hz", 360 to "360Hz", 480 to "480Hz").forEach { (hz, label) ->
                        val isSelected = dispState.touchSamplingRatioHz == hz
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(34.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) NeonCyan.copy(alpha = 0.25f) else CyberSurfaceDark)
                                .border(1.dp, if (isSelected) NeonCyan else CyberBorder, RoundedCornerShape(6.dp))
                                .clickable { viewModel.setTouchSamplingRatio(hz) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) NeonCyan else TermWhite,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = { viewModel.applyRealTouchOptimization(dispState.touchSamplingRatioHz) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .testTag("btn_apply_touch_tweak"),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonCyan,
                        contentColor = Color.Black
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.TouchApp,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color.Black
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "TERAPKAN SENTUHAN LICIN & RESPON CEPAT (${dispState.touchSamplingRatioHz}Hz)",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = Color.Black
                    )
                }
            }
        }

        item {
            CyberSectionTitle(
                title = "OPTIMISASI RAM & MEMORI",
                icon = Icons.Default.Memory,
                accentColor = NeonCrimson
            )
        }

        item {
            CyberCard(borderColor = NeonCrimson) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Pembersih RAM & Cache Proses",
                            style = MaterialTheme.typography.titleMedium,
                            color = TermWhite,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "RAM Terpakai: ${telemetryState.ramUsedMb}MB / ${telemetryState.ramTotalMb}MB (${telemetryState.ramUsagePercent}%)",
                            color = NeonCrimson,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = { viewModel.runQuickCacheCleanup() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("btn_quick_clean_ram"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonCrimson,
                        contentColor = Color.White
                    )
                ) {
                    Icon(imageVector = Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "BERSIHKAN RAM & CACHE SEKARANG ⚡",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto-Clear Cache Otomatis",
                            color = TermWhite,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (perfState.isAutoCacheClearDaemonActive)
                                "Daemon aktif di latar belakang (Setiap ${perfState.autoCacheClearIntervalSec}s)"
                            else "Pembersihan berkala di latar belakang mati",
                            color = if (perfState.isAutoCacheClearDaemonActive) NeonEmerald else TermMuted,
                            fontSize = 11.sp
                        )
                    }

                    Switch(
                        checked = perfState.isAutoCacheClearDaemonActive,
                        onCheckedChange = { viewModel.toggleAutoCacheClearDaemon(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = NeonCrimson,
                            checkedTrackColor = NeonCrimson.copy(alpha = 0.3f),
                            uncheckedTrackColor = CyberBorder
                        ),
                        modifier = Modifier.testTag("switch_auto_cache_daemon")
                    )
                }

                if (perfState.isAutoCacheClearDaemonActive) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(30 to "30d", 60 to "1m", 120 to "2m", 300 to "5m").forEach { (sec, label) ->
                            val isSel = perfState.autoCacheClearIntervalSec == sec
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(32.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSel) NeonCrimson.copy(alpha = 0.25f) else CyberSurfaceDark)
                                    .border(1.dp, if (isSel) NeonCrimson else CyberBorder, RoundedCornerShape(6.dp))
                                    .clickable { viewModel.setAutoCacheClearInterval(sec) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSel) NeonCrimson else TermWhite,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Total Dibersihkan: ${perfState.totalFreedAccumulatedMb} MB (${perfState.autoCleanCount}x pembersihan)",
                        color = TermMuted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
private fun GuideStepItem(number: String, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(NeonAmber),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number,
                color = Color.Black,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            color = TermWhite,
            fontSize = 11.sp,
            lineHeight = 15.sp
        )
    }
}

@Composable
private fun SignalMetricTile(
    label: String,
    value: String,
    subValue: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(CyberSurfaceDark)
            .border(1.dp, CyberBorder, RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        Column {
            Text(
                text = label,
                color = TermMuted,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                color = color,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = subValue,
                color = TermMuted,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
