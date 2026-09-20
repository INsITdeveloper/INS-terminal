package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.system.TerminalActionType
import com.example.ui.ConsoleEntry
import com.example.ui.InsViewModel
import com.example.ui.components.PuppeteerResultCard
import com.example.ui.components.StoragePermissionDialog
import com.example.ui.components.TerminalCameraViewfinder
import com.example.ui.components.TerminalMediaPreviewCard
import com.example.ui.components.TerminalNanoEditor
import com.example.ui.components.TmpfilesUploadDialog
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun TerminalScreen(
    viewModel: InsViewModel,
    modifier: Modifier = Modifier
) {
    val consoleLogs by viewModel.consoleLogs.collectAsState()
    val commandInput by viewModel.commandInput.collectAsState()
    val isRunning by viewModel.isCommandRunning.collectAsState()
    val sessions by viewModel.sessions.collectAsState()
    val activeSessionId by viewModel.activeSessionId.collectAsState()
    val currentDir by viewModel.activeCurrentDir.collectAsState()
    val isMediaPreviewEnabled by viewModel.isMediaPreviewEnabled.collectAsState()
    val isCameraOpen by viewModel.isCameraPreviewOpen.collectAsState()
    val isGestureTracking by viewModel.isGestureTrackingActive.collectAsState()
    val isNanoEditorOpen by viewModel.isNanoEditorOpen.collectAsState()
    val isTorchOn by viewModel.isTorchOn.collectAsState()
    val isStoragePermissionGranted by viewModel.isStoragePermissionGranted.collectAsState()
    val isStorageModalOpen by viewModel.isStorageModalOpen.collectAsState()
    val isUploadModalOpen by viewModel.isUploadModalOpen.collectAsState()
    val puppeteerResult by viewModel.lastPuppeteerResult.collectAsState()

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    val quickCommands = listOf(
        "help",
        "freeform status",
        "freeform enable",
        "freeform list",
        "tmpfiles",
        "nano script.py",
        "python gesture.py",
        "cam --gesture",
        "opencv",
        "ls -la",
        "cd /storage/emulated/0",
        "cd /storage/emulated/0/AOPtimize",
        "pwd",
        "perm",
        "neofetch",
        "puppeteer windows --test",
        "download video",
        "download image",
        "media on",
        "cam --preview",
        "cam --snap",
        "torch on",
        "torch off",
        "vibrate 250",
        "battery",
        "sensors",
        "ifconfig",
        "powershell",
        "dir",
        "top",
        "uname -a",
        "df -h",
        "free -m",
        "ins net opt",
        "clear"
    )

    val extraKeys = listOf(
        "ESC", "TAB", "CTRL", "ALT", "↑", "↓", "←", "→", "/", "-", "|", "~", "$", "CLEAR", "CAM", "PUPPETEER"
    )

    LaunchedEffect(consoleLogs.size) {
        if (consoleLogs.isNotEmpty()) {
            listState.animateScrollToItem(consoleLogs.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SleekBackground)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .background(SleekContainerSlate)
                .border(
                    BorderStroke(1.dp, SleekBorder),
                    RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                )
                .padding(horizontal = 12.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(9.dp).clip(RoundedCornerShape(50)).background(SleekCoral))
                Spacer(modifier = Modifier.width(5.dp))
                Box(modifier = Modifier.size(9.dp).clip(RoundedCornerShape(50)).background(SleekAmber))
                Spacer(modifier = Modifier.width(5.dp))
                Box(modifier = Modifier.size(9.dp).clip(RoundedCornerShape(50)).background(SleekMint))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "ins-desktop@android: $currentDir",
                    color = SleekTextPrimary,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {

                IconButton(
                    onClick = { viewModel.openNanoEditor("$currentDir/script.py") },
                    modifier = Modifier
                        .size(28.dp)
                        .testTag("action_nano_edit_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.EditNote,
                        contentDescription = "Nano File Editor",
                        tint = if (isNanoEditorOpen) SleekMint else SleekIceBlue,
                        modifier = Modifier.size(17.dp)
                    )
                }

                IconButton(
                    onClick = {
                        if (isGestureTracking) viewModel.closeGestureCamera()
                        else viewModel.openGestureCamera("gesture_recognition.py")
                    },
                    modifier = Modifier
                        .size(28.dp)
                        .testTag("action_gesture_cam_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = "OpenCV Gesture Camera",
                        tint = if (isGestureTracking) SleekMint else SleekTextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                IconButton(
                    onClick = { viewModel.showUploadModal() },
                    modifier = Modifier
                        .size(28.dp)
                        .testTag("action_upload_tmpfiles_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudUpload,
                        contentDescription = "Upload to tmpfiles.org",
                        tint = SleekMint,
                        modifier = Modifier.size(16.dp)
                    )
                }

                IconButton(
                    onClick = { viewModel.requestStoragePermission() },
                    modifier = Modifier
                        .size(28.dp)
                        .testTag("action_storage_perm_btn")
                ) {
                    Icon(
                        imageVector = if (isStoragePermissionGranted) Icons.Default.FolderOpen else Icons.Default.FolderSpecial,
                        contentDescription = "Storage Permission",
                        tint = if (isStoragePermissionGranted) SleekMint else SleekAmber,
                        modifier = Modifier.size(16.dp)
                    )
                }

                IconButton(
                    onClick = { viewModel.toggleMediaPreview() },
                    modifier = Modifier
                        .size(28.dp)
                        .testTag("action_media_toggle_btn")
                ) {
                    Icon(
                        imageVector = if (isMediaPreviewEnabled) Icons.Default.SmartDisplay else Icons.Default.HideImage,
                        contentDescription = "Toggle Media Output Preview",
                        tint = if (isMediaPreviewEnabled) Color(0xFF06B6D4) else SleekTextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                IconButton(
                    onClick = { viewModel.toggleCameraPreview() },
                    modifier = Modifier
                        .size(28.dp)
                        .testTag("action_cam_toggle_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "Camera Viewfinder",
                        tint = if (isCameraOpen) SleekMint else SleekTextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                IconButton(
                    onClick = { viewModel.executeTerminalCommand("puppeteer windows --test") },
                    modifier = Modifier
                        .size(28.dp)
                        .testTag("action_puppeteer_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = "Puppeteer Windows",
                        tint = SleekIceBlue,
                        modifier = Modifier.size(16.dp)
                    )
                }

                IconButton(
                    onClick = { viewModel.toggleTorch() },
                    modifier = Modifier
                        .size(28.dp)
                        .testTag("action_torch_btn")
                ) {
                    Icon(
                        imageVector = if (isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        contentDescription = "Torch Flashlight",
                        tint = if (isTorchOn) SleekAmber else SleekTextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                IconButton(
                    onClick = { viewModel.triggerVibrate() },
                    modifier = Modifier
                        .size(28.dp)
                        .testTag("action_vibrate_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Vibration,
                        contentDescription = "Haptic Vibrate",
                        tint = SleekLavender,
                        modifier = Modifier.size(16.dp)
                    )
                }

                IconButton(
                    onClick = {
                        val allText = consoleLogs.joinToString("\n") { "[${it.timestamp}] ${it.command}\n${it.output}" }
                        clipboardManager.setText(AnnotatedString(allText))
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy Terminal Logs",
                        tint = SleekTextSecondary,
                        modifier = Modifier.size(15.dp)
                    )
                }

                IconButton(
                    onClick = { viewModel.clearConsole() },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Clear Terminal",
                        tint = SleekTextSecondary,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SleekSurface)
                .border(BorderStroke(1.dp, SleekBorderSubtle))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(sessions) { sess ->
                    val isSelected = sess.id == activeSessionId
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) SleekContainerSlate else SleekSurfaceCard)
                            .border(
                                BorderStroke(1.dp, if (isSelected) SleekIceBlue else SleekBorderSubtle),
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { viewModel.switchSession(sess.id) }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) SleekMint else SleekTextSubtle)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = sess.name,
                            color = if (isSelected) SleekTextPrimary else SleekTextSecondary,
                            fontSize = 10.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontFamily = FontFamily.Monospace
                        )
                        if (sessions.size > 1) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close session",
                                tint = SleekTextSubtle,
                                modifier = Modifier
                                    .size(12.dp)
                                    .clickable { viewModel.closeSession(sess.id) }
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(SleekSurfaceCard)
                    .border(BorderStroke(0.8.dp, SleekBorder), RoundedCornerShape(8.dp))
                    .clickable { viewModel.addNewSession() }
                    .padding(horizontal = 7.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+",
                    color = SleekIceBlue,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        if (!isStoragePermissionGranted) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = SleekAmber.copy(alpha = 0.12f)),
                border = BorderStroke(1.dp, SleekAmber.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderSpecial,
                            contentDescription = "Storage Permission Required",
                            tint = SleekAmber,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Storage Permission required for /storage/emulated/0/ (AOPtimize, DCIM, etc.)",
                            color = SleekTextPrimary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 2
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Button(
                        onClick = { viewModel.requestStoragePermission() },
                        colors = ButtonDefaults.buttonColors(containerColor = SleekAmber),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text(
                            text = "GRANT ACCESS",
                            color = Color.Black,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
                .background(SleekContainerDark)
                .border(
                    BorderStroke(1.dp, SleekBorder),
                    RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
                )
                .padding(8.dp)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {

                if (isCameraOpen) {
                    item(key = "camera_viewfinder") {
                        TerminalCameraViewfinder(
                            viewModel = viewModel,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                if (puppeteerResult != null) {
                    item(key = "puppeteer_result") {
                        PuppeteerResultCard(
                            result = puppeteerResult!!,
                            onClose = {  },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                if (consoleLogs.isEmpty() && !isCameraOpen && puppeteerResult == null) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Session buffer clean. Type 'help', 'cam --preview', or 'puppeteer windows --test'.",
                                color = SleekTextSecondary,
                                fontSize = 11.5.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                items(consoleLogs, key = { it.id }) { log ->
                    SelectionContainer {
                        Column(modifier = Modifier.fillMaxWidth()) {

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "[${log.timestamp}] root@desktop-ins:$currentDir$ ",
                                    color = SleekMint,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = log.command,
                                    color = SleekIceBlue,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))

                            if (log.output.isNotBlank()) {
                                Text(
                                    text = log.output,
                                    color = if (log.isError) SleekCoral else SleekTextPrimary,
                                    fontSize = 10.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    lineHeight = 15.sp,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }

                            if (isMediaPreviewEnabled &&
                                (log.actionType == TerminalActionType.RENDER_MEDIA_IMAGE || log.actionType == TerminalActionType.RENDER_MEDIA_VIDEO) &&
                                !log.extraData.isNullOrBlank()
                            ) {
                                Spacer(modifier = Modifier.height(4.dp))
                                TerminalMediaPreviewCard(
                                    mediaUrlOrPath = log.extraData,
                                    actionType = log.actionType
                                )
                            }
                        }
                    }
                }

                if (isRunning) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp, horizontal = 8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 1.5.dp,
                                color = SleekIceBlue
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "executing in POSIX desktop sandbox...",
                                color = SleekIceBlue,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(extraKeys) { key ->
                val isSpecial = key in listOf("ESC", "TAB", "CTRL", "ALT", "CLEAR", "CAM", "PUPPETEER")
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSpecial) SleekContainerSlate else SleekSurfaceCard)
                        .border(
                            BorderStroke(0.8.dp, if (isSpecial) SleekIceBlue.copy(alpha = 0.5f) else SleekBorderSubtle),
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { viewModel.handleExtraKey(key) }
                        .padding(horizontal = 9.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = key,
                        color = if (isSpecial) SleekIceBlue else SleekTextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(quickCommands) { cmd ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(SleekSurfaceCard)
                        .border(BorderStroke(0.8.dp, SleekBorder), RoundedCornerShape(10.dp))
                        .clickable { viewModel.executeTerminalCommand(cmd) }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = cmd,
                        color = SleekMint,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(SleekSurfaceCardElevated)
                .border(BorderStroke(1.dp, if (commandInput.isNotEmpty()) SleekIceBlue else SleekBorder), RoundedCornerShape(14.dp))
                .padding(horizontal = 10.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = ">_",
                color = SleekMint,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.width(6.dp))
            TextField(
                value = commandInput,
                onValueChange = { viewModel.updateCommandInput(it) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("terminal_command_input"),
                placeholder = {
                    Text(
                        text = "Type command (e.g. download video, imgview, puppeteer, cd /sdcard)...",
                        color = SleekTextSecondary,
                        fontSize = 11.5.sp,
                        fontFamily = FontFamily.Monospace
                    )
                },
                textStyle = TextStyle(
                    color = SleekTextPrimary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                ),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = SleekIceBlue
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (commandInput.isNotBlank()) {
                            viewModel.executeTerminalCommand()
                        }
                    }
                ),
                singleLine = true
            )
            IconButton(
                onClick = {
                    if (commandInput.isNotBlank()) {
                        viewModel.executeTerminalCommand()
                    }
                },
                enabled = commandInput.isNotBlank() && !isRunning,
                modifier = Modifier.testTag("terminal_send_btn")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Execute Command",
                    tint = if (commandInput.isNotBlank()) SleekIceBlue else SleekTextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }

    if (isNanoEditorOpen) {
        TerminalNanoEditor(
            viewModel = viewModel
        )
    }

    StoragePermissionDialog(
        isOpen = isStorageModalOpen,
        isStoragePermissionGranted = isStoragePermissionGranted,
        onDismiss = { viewModel.hideStorageModal() },
        onOpenAllFilesSettings = { viewModel.openAllFilesSettings() },
        onOpenAppDetails = { viewModel.openAppDetailsSettings() },
        onRefreshPermission = { viewModel.checkStoragePermission() }
    )

    TmpfilesUploadDialog(
        isOpen = isUploadModalOpen,
        onDismiss = { viewModel.hideUploadModal() },
        onLogToTerminal = { msg -> viewModel.logDirectMessage(msg) }
    )
}
