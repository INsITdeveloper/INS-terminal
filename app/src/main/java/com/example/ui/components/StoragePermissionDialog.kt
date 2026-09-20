package com.example.ui.components

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.system.StorageAccessEngine
import com.example.ui.theme.*

@Composable
fun StoragePermissionDialog(
    isOpen: Boolean,
    isStoragePermissionGranted: Boolean,
    onDismiss: () -> Unit,
    onOpenAllFilesSettings: () -> Unit,
    onOpenAppDetails: () -> Unit,
    onRefreshPermission: () -> Unit
) {
    if (!isOpen) return

    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        onRefreshPermission()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SleekSurfaceCard),
            border = BorderStroke(1.2.dp, SleekIceBlue.copy(alpha = 0.6f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isStoragePermissionGranted) SleekMint.copy(alpha = 0.15f) else SleekAmber.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isStoragePermissionGranted) Icons.Default.FolderOpen else Icons.Default.FolderSpecial,
                            contentDescription = "Storage Status",
                            tint = if (isStoragePermissionGranted) SleekMint else SleekAmber,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Izin Akses Penyimpanan (Storage)",
                            color = SleekTextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isStoragePermissionGranted) "Status: Akses Penuh Aktif [UNLOCKED]" else "Status: Butuh Izin Akses Semua File",
                            color = if (isStoragePermissionGranted) SleekMint else SleekAmber,
                            fontSize = 11.5.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = SleekTextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(SleekContainerSlate)
                        .border(BorderStroke(1.dp, SleekBorderSubtle), RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Column {
                        Text(
                            text = "💡 Panduan Android 11 s/d Android 16:",
                            color = SleekIceBlue,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Untuk membaca folder penyimpanan internal/eksternal seperti:\n" +
                                    "• /storage/emulated/0/\n" +
                                    "• /storage/emulated/0/AOPtimize/\n" +
                                    "• /sdcard/DCIM/ Camera, dll.\n\n" +
                                    "Android menempatkan izin ini pada menu 'Akses Semua File' (All Files Access / MANAGE_EXTERNAL_STORAGE), bukan di izin aplikasi biasa.",
                            color = SleekTextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        onOpenAllFilesSettings()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isStoragePermissionGranted) SleekMint else SleekIceBlue
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.SettingsSuggest,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isStoragePermissionGranted) "Buka Ulang Menu Akses Semua File" else "1. Buka Menu 'Akses Semua File'",
                        color = Color.Black,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = {
                        val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            arrayOf(
                                Manifest.permission.READ_MEDIA_IMAGES,
                                Manifest.permission.READ_MEDIA_VIDEO,
                                Manifest.permission.READ_MEDIA_AUDIO,
                                Manifest.permission.ACCESS_MEDIA_LOCATION
                            )
                        } else {
                            arrayOf(
                                Manifest.permission.READ_EXTERNAL_STORAGE,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE
                            )
                        }
                        permissionLauncher.launch(permissionsToRequest)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, SleekBorderSubtle),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SleekTextPrimary)
                ) {
                    Icon(
                        imageVector = Icons.Default.PermMedia,
                        contentDescription = null,
                        tint = SleekLavender,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "2. Minta Izin Media & Storage Runtime",
                        color = SleekTextPrimary,
                        fontSize = 11.5.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = {
                        onOpenAppDetails()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, SleekBorderSubtle),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SleekTextSecondary)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = SleekTextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "3. Buka Pengaturan Info Aplikasi (App Info)",
                        color = SleekTextSecondary,
                        fontSize = 11.5.sp
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = {
                            onRefreshPermission()
                            onDismiss()
                        }
                    ) {
                        Text(
                            text = "SELESAI & CEK STATUS",
                            color = SleekIceBlue,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
