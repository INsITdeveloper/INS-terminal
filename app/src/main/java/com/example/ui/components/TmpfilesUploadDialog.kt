package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.system.TmpfilesUploadResult
import com.example.system.TmpfilesUploaderEngine
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun TmpfilesUploadDialog(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    onLogToTerminal: (String) -> Unit
) {
    if (!isOpen) return

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var manualPathInput by remember { mutableStateOf("/storage/emulated/0/AOPtimize/") }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var isUploading by remember { mutableStateOf(false) }
    var uploadResult by remember { mutableStateOf<TmpfilesUploadResult?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedUri = uri
            errorMessage = null
            uploadResult = null

            coroutineScope.launch {
                isUploading = true
                try {
                    val res = TmpfilesUploaderEngine.uploadUri(context, uri)
                    uploadResult = res
                    if (res.success) {
                        onLogToTerminal("[✓] Uploaded to tmpfiles.org: ${res.fileName} -> ${res.directDownloadUrl}")
                        Toast.makeText(context, "Upload Berhasil!", Toast.LENGTH_SHORT).show()
                    } else {
                        errorMessage = res.errorMessage ?: "Upload gagal"
                    }
                } catch (e: Exception) {
                    errorMessage = e.message
                } finally {
                    isUploading = false
                }
            }
        }
    }

    Dialog(
        onDismissRequest = { if (!isUploading) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(16.dp)
                .testTag("tmpfiles_upload_dialog"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SleekSurfaceCard),
            border = BorderStroke(1.2.dp, SleekMint.copy(alpha = 0.6f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
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
                            .background(SleekMint.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudUpload,
                            contentDescription = "Tmpfiles Upload",
                            tint = SleekMint,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Upload ke tmpfiles.org",
                            color = SleekTextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Hosting file sementara & bagikan link unduhan",
                            color = SleekTextSecondary,
                            fontSize = 11.5.sp
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        enabled = !isUploading,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Tutup",
                            tint = SleekTextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        filePickerLauncher.launch("*/*")
                    },
                    enabled = !isUploading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("btn_select_file_tmpfiles"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SleekMint)
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "PILIH FILE DARI HP & UPLOAD",
                        color = Color.Black,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "ATAU MASUKKAN PATH FILE SECARA MANUAL:",
                    color = SleekTextSubtle,
                    fontSize = 10.5.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = manualPathInput,
                    onValueChange = { manualPathInput = it },
                    placeholder = { Text("/storage/emulated/0/AOPtimize/file.txt", color = SleekTextSubtle, fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = LocalTextStyle.current.copy(
                        color = SleekTextPrimary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    ),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SleekIceBlue,
                        unfocusedBorderColor = SleekBorderSubtle,
                        focusedContainerColor = SleekContainerSlate,
                        unfocusedContainerColor = SleekContainerSlate
                    ),
                    singleLine = true,
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                if (manualPathInput.isNotBlank()) {
                                    coroutineScope.launch {
                                        isUploading = true
                                        errorMessage = null
                                        uploadResult = null
                                        val file = File(manualPathInput.trim())
                                        val res = TmpfilesUploaderEngine.uploadFile(file)
                                        uploadResult = res
                                        if (res.success) {
                                            onLogToTerminal("[✓] Uploaded: ${res.fileName} -> ${res.directDownloadUrl}")
                                            Toast.makeText(context, "Upload Berhasil!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            errorMessage = res.errorMessage
                                        }
                                        isUploading = false
                                    }
                                }
                            },
                            enabled = !isUploading && manualPathInput.isNotBlank()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = "Upload Path",
                                tint = SleekIceBlue,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                )

                if (isUploading) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = SleekContainerDark),
                        border = BorderStroke(1.dp, SleekIceBlue.copy(alpha = 0.4f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = SleekMint,
                                trackColor = SleekBorder
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Mengunggah file ke server tmpfiles.org...",
                                color = SleekTextPrimary,
                                fontSize = 11.5.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                if (errorMessage != null && !isUploading) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = SleekCoral.copy(alpha = 0.12f)),
                        border = BorderStroke(1.dp, SleekCoral.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = "Error",
                                tint = SleekCoral,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = errorMessage ?: "Upload gagal",
                                color = SleekCoral,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                uploadResult?.let { res ->
                    if (res.success) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = SleekContainerSlate),
                            border = BorderStroke(1.dp, SleekMint.copy(alpha = 0.5f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Success",
                                        tint = SleekMint,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "UPLOAD BERHASIL!",
                                        color = SleekMint,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "File: ${res.fileName} (${res.fileSizeFormatted})",
                                    color = SleekTextPrimary,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Spacer(modifier = Modifier.height(10.dp))
                                HorizontalDivider(color = SleekBorderSubtle, thickness = 0.8.dp)
                                Spacer(modifier = Modifier.height(10.dp))

                                Text(
                                    text = "DIRECT DOWNLOAD LINK:",
                                    color = SleekTextSubtle,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(SleekSurfaceCard)
                                        .border(BorderStroke(0.8.dp, SleekBorder), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = res.directDownloadUrl ?: "",
                                        color = SleekIceBlue,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    IconButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            val clip = ClipData.newPlainText("Direct URL", res.directDownloadUrl)
                                            clipboard.setPrimaryClip(clip)
                                            Toast.makeText(context, "Link Download Disalin!", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Copy Link",
                                            tint = SleekIceBlue,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            val clip = ClipData.newPlainText("Page URL", res.pageUrl)
                                            clipboard.setPrimaryClip(clip)
                                            Toast.makeText(context, "Page URL Disalin!", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(0.8.dp, SleekBorderSubtle),
                                        contentPadding = PaddingValues(vertical = 4.dp, horizontal = 8.dp)
                                    ) {
                                        Text("Salin Web URL", fontSize = 11.sp, color = SleekTextSecondary)
                                    }

                                    Button(
                                        onClick = {
                                            try {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(res.pageUrl ?: res.directDownloadUrl))
                                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                context.startActivity(intent)
                                            } catch (_: Exception) {}
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = SleekIceBlue),
                                        contentPadding = PaddingValues(vertical = 4.dp, horizontal = 8.dp)
                                    ) {
                                        Text("Buka Browser", fontSize = 11.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onDismiss,
                        enabled = !isUploading
                    ) {
                        Text(
                            text = "TUTUP",
                            color = SleekTextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}
