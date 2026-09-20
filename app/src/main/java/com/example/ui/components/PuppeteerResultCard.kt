package com.example.ui.components

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.system.PuppeteerExecutionResult
import com.example.ui.theme.*

@Composable
fun PuppeteerResultCard(
    result: PuppeteerExecutionResult,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bitmap = remember(result.screenshotBase64) {
        result.screenshotBase64?.let { base64Str ->
            try {
                val decoded = Base64.decode(base64Str, Base64.DEFAULT)
                val options = BitmapFactory.Options().apply {
                    inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
                    inPremultiplied = true
                }
                BitmapFactory.decodeByteArray(decoded, 0, decoded.size, options)
            } catch (e: Exception) {
                null
            }
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(BorderStroke(1.dp, SleekBorder), RoundedCornerShape(14.dp)),
        colors = CardDefaults.cardColors(containerColor = SleekContainerDark),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = null,
                        tint = SleekIceBlue,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "PUPPETEER WINDOWS CHROMIUM RUNNER",
                        color = SleekIceBlue,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Puppeteer Result",
                        tint = SleekTextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = SleekContainerSlate,
                    border = BorderStroke(0.8.dp, SleekBorder)
                ) {
                    Text(
                        text = result.emulatedPlatform,
                        color = SleekTextPrimary,
                        fontSize = 9.5.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = SleekMint.copy(alpha = 0.15f),
                    border = BorderStroke(0.8.dp, SleekMint.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "${result.executionTimeMs}ms",
                        color = SleekMint,
                        fontSize = 9.5.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = SleekAmber.copy(alpha = 0.15f),
                    border = BorderStroke(0.8.dp, SleekAmber.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "Viewport: 1280x720",
                        color = SleekAmber,
                        fontSize = 9.5.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Title: ${result.pageTitle}",
                color = SleekTextPrimary,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "URL: ${result.currentUrl}",
                color = SleekTextSecondary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )

            if (bitmap != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(BorderStroke(1.dp, SleekBorder), RoundedCornerShape(8.dp))
                        .background(SleekSurface)
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Puppeteer Screenshot",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(SleekSurface)
                    .padding(8.dp)
            ) {
                Text(
                    text = "Automation Logs:",
                    color = SleekIceBlue,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                result.logs.takeLast(4).forEach { logLine ->
                    Text(
                        text = logLine,
                        color = SleekTextSecondary,
                        fontSize = 9.5.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}
