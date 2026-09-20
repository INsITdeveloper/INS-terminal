package com.example.ui.components

import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.system.TerminalActionType
import java.io.File

@Composable
fun TerminalMediaPreviewCard(
    mediaUrlOrPath: String,
    actionType: TerminalActionType,
    onDismiss: () -> Unit = {}
) {
    val isVideo = actionType == TerminalActionType.RENDER_MEDIA_VIDEO ||
            mediaUrlOrPath.endsWith(".mp4", ignoreCase = true) ||
            mediaUrlOrPath.endsWith(".webm", ignoreCase = true) ||
            mediaUrlOrPath.endsWith(".mkv", ignoreCase = true)

    var isExpanded by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .border(
                1.dp,
                if (isVideo) Color(0xFF8B5CF6).copy(alpha = 0.6f) else Color(0xFF06B6D4).copy(alpha = 0.6f),
                RoundedCornerShape(8.dp)
            ),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A).copy(alpha = 0.95f)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isVideo) Icons.Default.PlayCircleFilled else Icons.Default.Image,
                        contentDescription = null,
                        tint = if (isVideo) Color(0xFFA78BFA) else Color(0xFF22D3EE),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isVideo) "INTERACTIVE VIDEO STREAM [1080P HD]" else "RICH GRAPHIC IMAGE PREVIEW [HD]",
                        color = if (isVideo) Color(0xFFA78BFA) else Color(0xFF22D3EE),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Surface(
                    color = Color(0xFF1E293B),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = if (isVideo) "VIDEO" else "IMAGE",
                        color = Color(0xFF94A3B8),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Source: $mediaUrlOrPath",
                color = Color(0xFF64748B),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black)
                    .border(1.dp, Color(0xFF334155), RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (isVideo) {

                    val context = LocalContext.current
                    var videoViewRef by remember { mutableStateOf<VideoView?>(null) }

                    AndroidView(
                        factory = { ctx ->
                            VideoView(ctx).apply {
                                val uri = if (mediaUrlOrPath.startsWith("http://") || mediaUrlOrPath.startsWith("https://")) {
                                    Uri.parse(mediaUrlOrPath)
                                } else {
                                    Uri.fromFile(File(mediaUrlOrPath))
                                }
                                setVideoURI(uri)
                                val mc = MediaController(ctx)
                                mc.setAnchorView(this)
                                setMediaController(mc)
                                setOnPreparedListener { mp ->
                                    mp.isLooping = true
                                    start()
                                    isPlaying = true
                                }
                                setOnErrorListener { _, _, _ ->
                                    true
                                }
                                videoViewRef = this
                            }
                        },
                        onRelease = { view ->
                            view.stopPlayback()
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    if (!isPlaying) {
                        IconButton(
                            onClick = {
                                videoViewRef?.start()
                                isPlaying = true
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play Video",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                } else {

                    val context = LocalContext.current
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(mediaUrlOrPath)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Terminal Image Output",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable { isExpanded = true }
                    )

                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                            .clickable { isExpanded = true }
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Fullscreen,
                                contentDescription = "Expand",
                                tint = Color(0xFF22D3EE),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "TAP TO EXPAND",
                                color = Color(0xFF22D3EE),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "STATUS: 200 OK • GPU Accelerated",
                    color = Color(0xFF10B981),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium
                )

                Row {
                    OutlinedButton(
                        onClick = { isExpanded = true },
                        modifier = Modifier.height(28.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8))
                    ) {
                        Icon(
                            imageVector = Icons.Default.ZoomIn,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Full View", fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }

    if (isExpanded) {
        Dialog(onDismissRequest = { isExpanded = false }) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF0F172A),
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(8.dp)
                    .border(1.dp, Color(0xFF06B6D4), RoundedCornerShape(12.dp))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isVideo) "HD VIDEO PLAYER" else "FULL-RES HD IMAGE",
                            color = Color(0xFF22D3EE),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        IconButton(
                            onClick = { isExpanded = false },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isVideo) {
                            val context = LocalContext.current
                            AndroidView(
                                factory = { ctx ->
                                    VideoView(ctx).apply {
                                        val uri = if (mediaUrlOrPath.startsWith("http://") || mediaUrlOrPath.startsWith("https://")) {
                                            Uri.parse(mediaUrlOrPath)
                                        } else {
                                            Uri.fromFile(File(mediaUrlOrPath))
                                        }
                                        setVideoURI(uri)
                                        val mc = MediaController(ctx)
                                        mc.setAnchorView(this)
                                        setMediaController(mc)
                                        setOnPreparedListener { mp ->
                                            mp.isLooping = true
                                            start()
                                        }
                                    }
                                },
                                onRelease = { view ->
                                    view.stopPlayback()
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            val context = LocalContext.current
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(mediaUrlOrPath)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Full Image View",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = mediaUrlOrPath,
                        color = Color(0xFF94A3B8),
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}
