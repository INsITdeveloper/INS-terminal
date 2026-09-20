package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.TelemetryState
import com.example.ui.theme.*

@Composable
fun CyberCard(
    modifier: Modifier = Modifier,
    borderColor: Color = SleekBorder,
    glowEffect: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val brush = if (glowEffect) {
        Brush.verticalGradient(listOf(borderColor.copy(alpha = 0.7f), SleekBorder.copy(alpha = 0.3f)))
    } else {
        Brush.verticalGradient(listOf(SleekBorder, SleekBorderSubtle))
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(BorderStroke(1.dp, brush), RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(
            containerColor = SleekSurfaceCard
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            content = content
        )
    }
}

@Composable
fun CyberTopHeader(
    telemetry: TelemetryState,
    isVolatileGuard: Boolean,
    onResetStockClicked: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SleekBackground)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(SleekContainerSlate)
                        .border(BorderStroke(1.dp, SleekBorder), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = ">_",
                        color = SleekIceBlue,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "INS TERMINAL",
                            style = MaterialTheme.typography.titleMedium,
                            color = SleekTextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(SleekContainerSlate)
                                .border(BorderStroke(0.8.dp, SleekBorder), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "HYPERBOOST",
                                color = SleekIceBlue,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                    Text(
                        text = "System Subsystem • Live Hook",
                        style = MaterialTheme.typography.bodySmall,
                        color = SleekTextSecondary,
                        fontSize = 10.sp
                    )
                }
            }

            OutlinedButton(
                onClick = onResetStockClicked,
                modifier = Modifier
                    .height(34.dp)
                    .testTag("quick_reset_stock_btn"),
                shape = RoundedCornerShape(17.dp),
                border = BorderStroke(1.dp, SleekCoral.copy(alpha = 0.6f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = SleekCoralContainer.copy(alpha = 0.4f),
                    contentColor = SleekCoral
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Restore,
                    contentDescription = "Reset to Stock Defaults",
                    modifier = Modifier.size(14.dp),
                    tint = SleekCoral
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "OEM DEFAULT",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = SleekCoral
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(SleekContainerDark)
                .border(BorderStroke(0.8.dp, SleekBorder), RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (telemetry.cpuUsagePercent > 60) SleekCoral else SleekMint)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "CPU: ${telemetry.cpuUsagePercent}%",
                    color = SleekTextPrimary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Thermostat,
                    contentDescription = null,
                    tint = SleekAmber,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = String.format("%.1f°C", telemetry.cpuTempC),
                    color = SleekTextPrimary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Speed,
                    contentDescription = null,
                    tint = SleekIceBlue,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = String.format("%.0fK", telemetry.networkRxKbps),
                    color = SleekIceBlue,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isVolatileGuard) SleekMint.copy(alpha = 0.15f) else SleekAmber.copy(alpha = 0.15f))
                    .border(
                        BorderStroke(0.6.dp, if (isVolatileGuard) SleekMint.copy(alpha = 0.4f) else SleekAmber.copy(alpha = 0.4f)),
                        RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = if (isVolatileGuard) "VOLATILE: SAFE" else "PERSISTENT",
                    color = if (isVolatileGuard) SleekMint else SleekAmber,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
fun CyberSectionTitle(
    title: String,
    icon: ImageVector,
    accentColor: Color = SleekIceBlue
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = SleekTextPrimary,
            fontWeight = FontWeight.Bold
        )
    }
}
