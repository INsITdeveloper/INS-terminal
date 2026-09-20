package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.InsViewModel
import com.example.ui.theme.*

@Composable
fun TerminalNanoEditor(
    viewModel: InsViewModel,
    modifier: Modifier = Modifier
) {
    val filePath by viewModel.nanoEditorFilePath.collectAsState()
    val fileName by viewModel.nanoEditorFileName.collectAsState()
    val rawContent by viewModel.nanoEditorContent.collectAsState()
    val statusMessage by viewModel.nanoEditorStatusMessage.collectAsState()

    var textContent by remember(rawContent) { mutableStateOf(rawContent) }
    var isTemplateMenuOpen by remember { mutableStateOf(false) }
    var isSearchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val lineCount = remember(textContent) { textContent.lines().size.coerceAtLeast(1) }
    val charCount = remember(textContent) { textContent.length }
    val verticalScrollState = rememberScrollState()
    val horizontalScrollState = rememberScrollState()

    Dialog(onDismissRequest = { viewModel.closeNanoEditor() }) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(16.dp))
                .border(BorderStroke(1.5.dp, SleekMint.copy(alpha = 0.6f)), RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = SleekContainerDark),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(SleekContainerDark)
            ) {

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SleekContainerSlate)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "GNU nano 8.0",
                            color = SleekMint,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "[$fileName]",
                            color = SleekTextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        IconButton(
                            onClick = { isTemplateMenuOpen = !isTemplateMenuOpen },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Code,
                                contentDescription = "Templates",
                                tint = SleekIceBlue,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Button(
                            onClick = {
                                viewModel.saveNanoFile(textContent)
                            },
                            modifier = Modifier
                                .height(28.dp)
                                .testTag("nano_save_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = SleekMint),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "SIMPAN",
                                color = Color.Black,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        IconButton(
                            onClick = { viewModel.closeNanoEditor() },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Exit Nano",
                                tint = SleekCoral,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SleekSurfaceCard)
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Path: $filePath",
                        color = SleekTextSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "Baris: $lineCount | Karakter: $charCount",
                        color = SleekIceBlue,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                if (isTemplateMenuOpen) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = SleekContainerSlate,
                        border = BorderStroke(1.dp, SleekBorder)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                text = "Pilih Template Skrip Cepat:",
                                color = SleekTextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Button(
                                    onClick = {
                                        textContent = """
                                            # Python OpenCV Camera & Gesture Recognition
                                            import cv2
                                            import numpy as np

                                            def run_gesture_detector():
                                                print("[+] Starting Gesture Camera Engine (/dev/video0)...")
                                                cap = cv2.VideoCapture(0)

                                                while cap.isOpened():
                                                    ret, frame = cap.read()
                                                    if not ret:
                                                        break

                                                    # Flip horizontal for mirror view
                                                    frame = cv2.flip(frame, 1)

                                                    # Gesture Landmark Overlay
                                                    cv2.putText(frame, "Gesture: OPEN_PALM (99.2%)", (30, 50),
                                                                cv2.FONT_HERSHEY_SIMPLEX, 0.9, (0, 255, 128), 2)
                                                    cv2.putText(frame, "FPS: 60.0 [Camera2 NPU]", (30, 90),
                                                                cv2.FONT_HERSHEY_SIMPLEX, 0.7, (255, 200, 0), 2)

                                                    cv2.imshow("Gesture Recognizer", frame)
                                                    if cv2.waitKey(1) & 0xFF == ord('q'):
                                                        break

                                                cap.release()
                                                cv2.destroyAllWindows()

                                            if __name__ == '__main__':
                                                run_gesture_detector()
                                        """.trimIndent()
                                        isTemplateMenuOpen = false
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = SleekMint.copy(alpha = 0.2f)),
                                    border = BorderStroke(1.dp, SleekMint),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("OpenCV Gesture (Py)", color = SleekMint, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                }

                                Button(
                                    onClick = {
                                        textContent = """
                                            #!/bin/bash
                                            # System Shell Performance & Optimization Script
                                            echo "[*] Initializing INS Device Optimizer..."
                                            echo 1 > /proc/sys/net/ipv4/tcp_fastopen
                                            echo 0 > /proc/sys/vm/swappiness
                                            echo "[✓] Fast RAM and Network low latency profile applied."
                                        """.trimIndent()
                                        isTemplateMenuOpen = false
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = SleekIceBlue.copy(alpha = 0.2f)),
                                    border = BorderStroke(1.dp, SleekIceBlue),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Bash Script (.sh)", color = SleekIceBlue, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(SleekBackground)
                        .padding(vertical = 6.dp)
                ) {

                    Column(
                        modifier = Modifier
                            .width(36.dp)
                            .verticalScroll(verticalScrollState)
                            .padding(end = 4.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        for (i in 1..lineCount) {
                            Text(
                                text = "$i",
                                color = SleekTextSecondary.copy(alpha = 0.6f),
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 18.sp
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(SleekBorder)
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(horizontal = 8.dp)
                            .verticalScroll(verticalScrollState)
                            .horizontalScroll(horizontalScrollState)
                    ) {
                        BasicTextField(
                            value = textContent,
                            onValueChange = {
                                textContent = it
                                viewModel.updateNanoContent(it)
                            },
                            textStyle = TextStyle(
                                color = SleekTextPrimary,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 18.sp
                            ),
                            cursorBrush = SolidColor(SleekMint),
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 400.dp)
                                .testTag("nano_text_editor_input")
                        )
                    }
                }

                if (statusMessage != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SleekMint.copy(alpha = 0.15f))
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = statusMessage ?: "",
                            color = SleekMint,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SleekContainerSlate)
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        NanoKeyGuideItem(key = "^O", label = "Simpan", onClick = { viewModel.saveNanoFile(textContent) })
                        NanoKeyGuideItem(key = "^X", label = "Keluar", onClick = { viewModel.closeNanoEditor() })
                        NanoKeyGuideItem(key = "^R", label = "Baca File", onClick = { viewModel.openNanoEditor(filePath) })
                        NanoKeyGuideItem(key = "^K", label = "Bersihkan", onClick = { textContent = "" })
                    }
                }
            }
        }
    }
}

@Composable
private fun NanoKeyGuideItem(
    key: String,
    label: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(4.dp),
        color = Color.Black.copy(alpha = 0.4f),
        border = BorderStroke(0.8.dp, SleekBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = key,
                color = SleekMint,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = label,
                color = SleekTextPrimary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
