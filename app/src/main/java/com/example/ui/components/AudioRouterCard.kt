package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.window.Dialog
import com.example.system.InstalledAppInfo
import com.example.system.SeparateAppSoundState
import com.example.ui.InsViewModel
import com.example.ui.theme.*

@Composable
fun AudioRouterCard(
    state: SeparateAppSoundState,
    viewModel: InsViewModel,
    modifier: Modifier = Modifier
) {
    var showAllAppsDialog by remember { mutableStateOf(false) }
    var showCustomPkgDialog by remember { mutableStateOf(false) }
    var customPkgText by remember { mutableStateOf("") }
    var appSearchQuery by remember { mutableStateOf("") }

    val installedApps by viewModel.installedAppsList.collectAsState()

    CyberCard(
        borderColor = SleekIceBlue,
        glowEffect = state.isSeparateSoundEnabled,
        modifier = modifier
    ) {

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(SleekIceBlue.copy(alpha = 0.15f))
                        .border(1.dp, SleekIceBlue.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Headphones,
                        contentDescription = null,
                        tint = SleekIceBlue,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Separate App Sound",
                        color = TermWhite,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Pemisah Suara Bluetooth & Speaker",
                        color = SleekIceBlue,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Switch(
                checked = state.isSeparateSoundEnabled,
                onCheckedChange = { viewModel.toggleSeparateAppSound(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = SleekIceBlue,
                    checkedTrackColor = SleekIceBlue.copy(alpha = 0.3f),
                    uncheckedTrackColor = CyberBorder
                ),
                modifier = Modifier.testTag("switch_separate_app_sound")
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Surface(
            color = if (state.isBluetoothConnected) NeonEmerald.copy(alpha = 0.12f) else CyberSurfaceDark,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, if (state.isBluetoothConnected) NeonEmerald.copy(alpha = 0.4f) else CyberBorder),
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
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (state.isBluetoothConnected) Icons.Default.BluetoothConnected else Icons.Default.BluetoothDisabled,
                        contentDescription = null,
                        tint = if (state.isBluetoothConnected) NeonEmerald else TermMuted,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (state.isBluetoothConnected) {
                            "Bluetooth: ${state.connectedBluetoothDeviceName ?: "Device Connected"}"
                        } else {
                            "Bluetooth Audio: Disconnected"
                        },
                        color = if (state.isBluetoothConnected) NeonEmerald else TermMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }

                IconButton(
                    onClick = {
                        viewModel.refreshBluetoothAudioStatus()
                        viewModel.refreshInstalledApps()
                    },
                    modifier = Modifier.size(24.dp).testTag("action_refresh_bt_status")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh Bluetooth Status",
                        tint = SleekIceBlue,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "1. Aplikasi Target ke Bluetooth 🎧:",
                color = TermWhite,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "${state.selectedBluetoothPackages.size} Dipilih",
                color = SleekIceBlue,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        Spacer(modifier = Modifier.height(6.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {

            item {
                Surface(
                    color = SleekIceBlue.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, SleekIceBlue),
                    modifier = Modifier
                        .clickable { showAllAppsDialog = true }
                        .testTag("btn_open_all_apps_picker")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Apps,
                            contentDescription = null,
                            tint = SleekIceBlue,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "SEMUA APLIKASI (${installedApps.size})...",
                            color = SleekIceBlue,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            items(viewModel.audioRouterEngine.presetApps) { (pkg, name) ->
                val isSelected = state.selectedBluetoothPackages.contains(pkg) || state.selectedAppPackage == pkg
                Surface(
                    color = if (isSelected) SleekIceBlue.copy(alpha = 0.25f) else CyberSurfaceDark,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, if (isSelected) SleekIceBlue else CyberBorder),
                    modifier = Modifier
                        .clickable { viewModel.toggleAppInBluetoothList(pkg, name) }
                        .testTag("app_chip_$pkg")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (isSelected) SleekIceBlue else TermMuted,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = name,
                            color = if (isSelected) SleekIceBlue else TermWhite,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            item {
                Surface(
                    color = CyberSurfaceDark,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, NeonAmber.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .clickable { showCustomPkgDialog = true }
                        .testTag("app_chip_custom")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = NeonAmber,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Ketik Package ID...",
                            color = NeonAmber,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "2. Jalur Suara Speaker Internal HP (Select All):",
            color = TermWhite,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(6.dp))

        Surface(
            color = if (state.isAllOtherAppsToSpeaker) NeonEmerald.copy(alpha = 0.15f) else CyberSurfaceDark,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, if (state.isAllOtherAppsToSpeaker) NeonEmerald else CyberBorder),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { viewModel.selectAllRemainingAppsToSpeaker() }
                .testTag("card_select_all_speaker")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = null,
                            tint = if (state.isAllOtherAppsToSpeaker) NeonEmerald else TermMuted,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "SEMUA APLIKASI LAIN KE SPEAKER HP 🔊",
                            color = if (state.isAllOtherAppsToSpeaker) NeonEmerald else TermWhite,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Game (MLBB, PUBG, FF) & notifikasi otomatis bersuara di speaker HP (tanpa bocor ke Bluetooth)",
                        color = TermMuted,
                        fontSize = 10.sp
                    )
                }

                Switch(
                    checked = state.isAllOtherAppsToSpeaker,
                    onCheckedChange = { viewModel.setAllOtherAppsToSpeaker(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = NeonEmerald,
                        checkedTrackColor = NeonEmerald.copy(alpha = 0.3f),
                        uncheckedTrackColor = CyberBorder
                    ),
                    modifier = Modifier.testTag("switch_all_other_to_speaker")
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
                Text(
                    text = "Dual Stream Multi-Audio Focus",
                    color = TermWhite,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Musik di BT & suara game di Speaker bunyi bersamaan tanpa pause otomatis",
                    color = TermMuted,
                    fontSize = 10.sp
                )
            }
            Switch(
                checked = state.isMultiAudioFocusEnabled,
                onCheckedChange = { viewModel.toggleMultiAudioFocus(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = SleekIceBlue,
                    checkedTrackColor = SleekIceBlue.copy(alpha = 0.3f),
                    uncheckedTrackColor = CyberBorder
                )
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = { viewModel.fixAllAudioAndMicIssues() },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("btn_fix_audio_and_mic"),
            colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald.copy(alpha = 0.18f)),
            border = BorderStroke(1.dp, NeonEmerald),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Build,
                contentDescription = null,
                tint = NeonEmerald,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "🛠️ FIX AUDIO (VN WA PELAN, MLBB MUTE, SUARA HILANG)",
                color = NeonEmerald,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }

        if (state.lastExecutionLog != null && state.lastExecutionLog.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                color = CyberVoid,
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, SleekIceBlue.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = state.lastExecutionLog,
                    color = SleekIceBlue,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }

    if (showAllAppsDialog) {
        Dialog(onDismissRequest = { showAllAppsDialog = false }) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = SleekSurface,
                border = BorderStroke(1.dp, SleekIceBlue),
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Pilih Aplikasi ke Bluetooth",
                                color = TermWhite,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Pilih app yang suaranya keluar ke Headset BT",
                                color = SleekIceBlue,
                                fontSize = 11.sp
                            )
                        }
                        IconButton(onClick = { showAllAppsDialog = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Tutup", tint = TermMuted)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = appSearchQuery,
                        onValueChange = { appSearchQuery = it },
                        placeholder = { Text("Cari nama aplikasi atau package...", color = TermMuted, fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = SleekIceBlue, modifier = Modifier.size(16.dp)) },
                        trailingIcon = {
                            if (appSearchQuery.isNotEmpty()) {
                                IconButton(onClick = { appSearchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Hapus", tint = TermMuted, modifier = Modifier.size(14.dp))
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SleekIceBlue,
                            unfocusedBorderColor = CyberBorder
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    val filteredApps = remember(installedApps, appSearchQuery) {
                        if (appSearchQuery.isBlank()) {
                            installedApps
                        } else {
                            installedApps.filter {
                                it.appName.contains(appSearchQuery, ignoreCase = true) ||
                                it.packageName.contains(appSearchQuery, ignoreCase = true)
                            }
                        }
                    }

                    Text(
                        text = "Ditemukan ${filteredApps.size} aplikasi",
                        color = TermMuted,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(filteredApps, key = { it.packageName }) { app ->
                            val isSelected = state.selectedBluetoothPackages.contains(app.packageName)
                            Surface(
                                color = if (isSelected) SleekIceBlue.copy(alpha = 0.15f) else CyberSurfaceDark,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, if (isSelected) SleekIceBlue else CyberBorder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.toggleAppInBluetoothList(app.packageName, app.appName) }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = app.appName,
                                                color = if (isSelected) SleekIceBlue else TermWhite,
                                                fontSize = 12.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                            )
                                            if (app.isGame) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Surface(
                                                    color = NeonAmber.copy(alpha = 0.2f),
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        text = "GAME",
                                                        color = NeonAmber,
                                                        fontSize = 8.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = app.packageName,
                                            color = TermMuted,
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }

                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { viewModel.toggleAppInBluetoothList(app.packageName, app.appName) },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = SleekIceBlue,
                                            uncheckedColor = CyberBorder
                                        )
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = { showAllAppsDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = SleekIceBlue),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("SELESAI & SIMPAN (${state.selectedBluetoothPackages.size} APP)", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showCustomPkgDialog) {
        AlertDialog(
            onDismissRequest = { showCustomPkgDialog = false },
            title = { Text("Input Package Name Aplikasi", color = TermWhite, fontSize = 14.sp) },
            text = {
                Column {
                    Text(
                        text = "Masukkan ID package aplikasi yang ingin dipisah audionya (contoh: com.netease.ch117):",
                        color = TermMuted,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customPkgText,
                        onValueChange = { customPkgText = it },
                        placeholder = { Text("com.example.app", color = TermMuted, fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SleekIceBlue,
                            unfocusedBorderColor = CyberBorder
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (customPkgText.isNotBlank()) {
                            val pkg = customPkgText.trim()
                            val name = pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }
                            viewModel.selectSeparateSoundApp(pkg, name)
                        }
                        showCustomPkgDialog = false
                    }
                ) {
                    Text("TAMBAH", color = SleekIceBlue)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomPkgDialog = false }) {
                    Text("BATAL", color = TermMuted)
                }
            },
            containerColor = SleekSurface
        )
    }
}

