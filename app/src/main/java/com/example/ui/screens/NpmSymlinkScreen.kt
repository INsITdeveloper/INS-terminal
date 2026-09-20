package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.NpmPackageEntity
import com.example.data.entity.SymlinkEntity
import com.example.ui.InsViewModel
import com.example.ui.components.CyberCard
import com.example.ui.components.CyberSectionTitle
import com.example.ui.theme.*

@Composable
fun NpmSymlinkScreen(
    viewModel: InsViewModel,
    modifier: Modifier = Modifier
) {
    val npmPackages by viewModel.dbNpmPackages.collectAsState()
    val symlinks by viewModel.dbSymlinks.collectAsState()
    val isSymlinkSupportActive by viewModel.isSymlinkSupportActive.collectAsState()
    val jsCodeInput by viewModel.jsCodeInput.collectAsState()
    val jsOutput by viewModel.jsRunOutput.collectAsState()

    var showCreateSymlinkDialog by remember { mutableStateOf(false) }
    var targetPathInput by remember { mutableStateOf("/data/data/com.ins.terminal/files") }
    var linkPathInput by remember { mutableStateOf("/system/bin/ins_module") }
    var symlinkTypeInput by remember { mutableStateOf("SOFT") }

    var npmSearchQuery by remember { mutableStateOf("") }

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
                title = "NPM PACKAGE TOOLCHAIN",
                icon = Icons.Default.Extension,
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
                    Column {
                        Text(
                            text = "Node.js & NPM Registry Hub",
                            style = MaterialTheme.typography.titleMedium,
                            color = TermWhite,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Installed Packages: ${npmPackages.size} modules",
                            style = MaterialTheme.typography.bodySmall,
                            color = NeonAmber,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(NeonAmber.copy(alpha = 0.2f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "NODE v22.12.0",
                            color = NeonAmber,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Registry Quick Install:",
                    color = TermMuted,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(6.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(viewModel.npmEngine.registryCatalog) { pkg ->
                        val isInstalled = npmPackages.any { it.packageName == pkg.name }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isInstalled) NeonEmerald.copy(alpha = 0.15f) else CyberSurfaceDark)
                                .border(
                                    BorderStroke(1.dp, if (isInstalled) NeonEmerald else CyberBorder),
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    if (!isInstalled) {
                                        viewModel.installNpmPackage(pkg.name, pkg.description)
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = pkg.name,
                                        color = if (isInstalled) NeonEmerald else NeonAmber,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "v${pkg.version}",
                                        color = TermMuted,
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Text(
                                    text = if (isInstalled) "INSTALLED ✓" else "+ INSTALL",
                                    color = if (isInstalled) NeonEmerald else TermWhite,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Active Node Modules:",
                    color = TermMuted,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(6.dp))

                if (npmPackages.isEmpty()) {
                    Text(
                        text = "No NPM packages installed. Tap above to install.",
                        color = TermMuted,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                } else {
                    npmPackages.forEach { pkg ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(CyberSurfaceDark)
                                .border(BorderStroke(0.8.dp, CyberBorder), RoundedCornerShape(6.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${pkg.packageName} @ ${pkg.version}",
                                    color = TermWhite,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = pkg.description,
                                    color = TermMuted,
                                    fontSize = 10.sp,
                                    maxLines = 1
                                )
                            }

                            IconButton(
                                onClick = { viewModel.removeNpmPackage(pkg.packageName) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Uninstall Package",
                                    tint = NeonCrimson,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Node.js Sandbox Script Runner:",
                    color = TermWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(6.dp))

                TextField(
                    value = jsCodeInput,
                    onValueChange = { viewModel.updateJsCodeInput(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(BorderStroke(1.dp, CyberBorder), RoundedCornerShape(8.dp)),
                    textStyle = TextStyle(
                        color = NeonCyan,
                        fontSize = 11.5.sp,
                        fontFamily = FontFamily.Monospace
                    ),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = CyberSurfaceDark,
                        unfocusedContainerColor = CyberSurfaceDark,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = { viewModel.runJsSandbox() },
                    modifier = Modifier.fillMaxWidth().testTag("run_js_sandbox_btn"),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonAmber,
                        contentColor = CyberVoid
                    )
                ) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "EXECUTE IN NODE SANDBOX", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }

                if (jsOutput.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(CyberSurfaceDark)
                            .border(BorderStroke(1.dp, NeonEmerald.copy(alpha = 0.5f)), RoundedCornerShape(6.dp))
                            .padding(8.dp)
                    ) {
                        Text(
                            text = jsOutput,
                            color = TermGreen,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        item {
            CyberSectionTitle(
                title = "SYMLINK & ALIAS ENGINE",
                icon = Icons.Default.Link,
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
                            text = "Symlink Support Mode",
                            color = TermWhite,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isSymlinkSupportActive) "Enabled (Linux 'ln -s' Hard & Soft links active)" else "Disabled (Virtual Sandbox Aliases only)",
                            color = if (isSymlinkSupportActive) NeonEmerald else NeonAmber,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Switch(
                        checked = isSymlinkSupportActive,
                        onCheckedChange = { viewModel.toggleSymlinkSupport(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = NeonCyan,
                            checkedTrackColor = NeonCyan.copy(alpha = 0.3f),
                            uncheckedTrackColor = CyberBorder
                        )
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Active Symbolic Links (${symlinks.size}):",
                        color = TermMuted,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )

                    TextButton(
                        onClick = { showCreateSymlinkDialog = true },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(text = "NEW LINK", color = NeonCyan, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                if (symlinks.isEmpty()) {
                    Text(
                        text = "No symlinks created.",
                        color = TermMuted,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                } else {
                    symlinks.forEach { symlink ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(CyberSurfaceDark)
                                .border(BorderStroke(0.8.dp, CyberBorder), RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(imageVector = Icons.Default.Link, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = symlink.linkName,
                                            color = TermWhite,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(NeonCyan.copy(alpha = 0.15f))
                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = symlink.linkType,
                                                color = NeonCyan,
                                                fontSize = 8.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                    }
                                    Text(
                                        text = "${symlink.destinationPath} -> ${symlink.sourceTarget}",
                                        color = TermMuted,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }

                                IconButton(
                                    onClick = { viewModel.deleteSymlink(symlink) },
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Remove Symlink",
                                        tint = NeonCrimson,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateSymlinkDialog) {
        AlertDialog(
            onDismissRequest = { showCreateSymlinkDialog = false },
            title = {
                Text(
                    text = "Create Symbolic Link",
                    color = TermWhite,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "Target Source Path:", color = TermMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    OutlinedTextField(
                        value = targetPathInput,
                        onValueChange = { targetPathInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(color = TermWhite, fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                        singleLine = true
                    )

                    Text(text = "Destination Symlink Path:", color = TermMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    OutlinedTextField(
                        value = linkPathInput,
                        onValueChange = { linkPathInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(color = TermWhite, fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                        singleLine = true
                    )

                    Text(text = "Link Type:", color = TermMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("SOFT", "HARD", "VIRTUAL_ALIAS").forEach { type ->
                            val isSel = symlinkTypeInput == type
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSel) NeonCyan.copy(alpha = 0.2f) else CyberSurfaceDark)
                                    .border(BorderStroke(1.dp, if (isSel) NeonCyan else CyberBorder), RoundedCornerShape(6.dp))
                                    .clickable { symlinkTypeInput = type }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text(text = type, color = if (isSel) NeonCyan else TermWhite, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.createSymlink(targetPathInput, linkPathInput, symlinkTypeInput)
                        showCreateSymlinkDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = CyberVoid)
                ) {
                    Text(text = "CREATE LINK", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateSymlinkDialog = false }) {
                    Text(text = "CANCEL", color = TermMuted, fontFamily = FontFamily.Monospace)
                }
            },
            containerColor = CyberSurfaceCard,
            shape = RoundedCornerShape(14.dp)
        )
    }
}
