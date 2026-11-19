package com.example.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.media.ImageCompressor
import com.example.ui.theme.CoolAsh
import com.example.ui.theme.CoralAlert
import com.example.ui.theme.DeepVelvetRed
import com.example.ui.theme.ElectricScarlet
import com.example.ui.theme.ElevatedOnyx
import com.example.ui.theme.EmeraldOnline
import com.example.ui.theme.HairlineSlate
import com.example.ui.theme.MoonWhite
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.RoseWhite
import com.example.ui.theme.SmokedOnyx
import com.example.ui.theme.XCrimson

@Composable
fun XChatBrandWordmark(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    Brush.linearGradient(listOf(XCrimson, ElectricScarlet))
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "X",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MoonWhite
            )
        }
        Text(
            text = "CHAT",
            style = MaterialTheme.typography.titleLarge,
            color = MoonWhite
        )
    }
}

@Composable
fun XChatCard(
    modifier: Modifier = Modifier,
    containerColor: Color = SmokedOnyx,
    borderColor: Color = HairlineSlate,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}

@Composable
fun AvatarDisplay(
    name: String,
    imageData: String?,
    size: Dp = 48.dp,
    showOnlineDot: Boolean = false,
    modifier: Modifier = Modifier
) {
    val decodedBitmap = remember(imageData) {
        if (!imageData.isNullOrBlank() && imageData.startsWith("data:image")) {
            ImageCompressor.decodeDataUriToBytes(imageData)?.let { bytes ->
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }
        } else null
    }

    Box(modifier = modifier.size(size)) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(listOf(DeepVelvetRed, ElevatedOnyx))
                )
                .border(1.5.dp, XCrimson.copy(alpha = 0.65f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            when {
                decodedBitmap != null -> {
                    Image(
                        bitmap = decodedBitmap,
                        contentDescription = "$name avatar",
                        modifier = Modifier.size(size).clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                }
                !imageData.isNullOrBlank() && imageData.startsWith("http") -> {
                    AsyncImage(
                        model = imageData,
                        contentDescription = "$name avatar",
                        modifier = Modifier.size(size).clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                }
                else -> {
                    val initials = name.trim()
                        .split(" ")
                        .filter { it.isNotBlank() }
                        .take(2)
                        .joinToString("") { it.first().uppercaseChar().toString() }
                        .ifBlank { "X" }
                    Text(
                        text = initials,
                        style = MaterialTheme.typography.titleMedium,
                        color = RoseWhite
                    )
                }
            }
        }
        if (showOnlineDot) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(EmeraldOnline)
                    .border(2.dp, ObsidianBlack, CircleShape)
            )
        }
    }
}

@Composable
fun MediaImageView(
    imageData: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val decodedBitmap = remember(imageData) {
        if (imageData.startsWith("data:image")) {
            ImageCompressor.decodeDataUriToBytes(imageData)?.let { bytes ->
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }
        } else null
    }
    if (decodedBitmap != null) {
        Image(
            bitmap = decodedBitmap,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale
        )
    } else if (imageData.startsWith("http")) {
        AsyncImage(
            model = imageData,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale
        )
    }
}

@Composable
fun StatusPill(
    text: String,
    containerColor: Color = DeepVelvetRed,
    contentColor: Color = RoseWhite,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = containerColor,
        border = BorderStroke(1.dp, contentColor.copy(alpha = 0.2f))
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun LoadingStateView(label: String = "Syncing with X Chat...") {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator(color = XCrimson, strokeWidth = 3.dp)
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = CoolAsh
            )
        }
    }
}

@Composable
fun EmptyStateView(
    icon: ImageVector = Icons.Rounded.AutoAwesome,
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    XChatCard(modifier = Modifier.padding(vertical = 8.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(DeepVelvetRed),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = ElectricScarlet)
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MoonWhite,
                textAlign = TextAlign.Center
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = CoolAsh,
                textAlign = TextAlign.Center
            )
            if (actionLabel != null && onAction != null) {
                Button(
                    onClick = onAction,
                    colors = ButtonDefaults.buttonColors(containerColor = XCrimson, contentColor = MoonWhite)
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
fun ErrorStateBanner(
    message: String,
    onDismiss: (() -> Unit)? = null
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = DeepVelvetRed,
        border = BorderStroke(1.dp, CoralAlert.copy(alpha = 0.6f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Rounded.ErrorOutline, contentDescription = "Error", tint = CoralAlert)
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = RoseWhite,
                modifier = Modifier.weight(1f)
            )
            if (onDismiss != null) {
                TextButton(onClick = onDismiss) {
                    Text("Dismiss", color = MoonWhite)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReportAbuseDialog(
    targetLabel: String,
    onDismiss: () -> Unit,
    onSubmit: (reason: String, description: String) -> Unit
) {
    val reasons = listOf("Harassment", "Spam or scam", "Inappropriate media", "Fake profile", "Underage concern")
    var selectedReason by remember { mutableStateOf(reasons.first()) }
    var details by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SmokedOnyx,
        icon = { Icon(Icons.Rounded.Flag, contentDescription = null, tint = XCrimson) },
        title = { Text("Report $targetLabel", color = MoonWhite) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Reports are confidential and reviewed by safety moderators.",
                    style = MaterialTheme.typography.bodySmall,
                    color = CoolAsh
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    reasons.forEach { reason ->
                        FilterChip(
                            selected = selectedReason == reason,
                            onClick = { selectedReason = reason },
                            label = { Text(reason) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = DeepVelvetRed,
                                selectedLabelColor = RoseWhite
                            )
                        )
                    }
                }
                OutlinedTextField(
                    value = details,
                    onValueChange = { details = it.take(400) },
                    label = { Text("Additional context (optional)") },
                    modifier = Modifier.fillMaxWidth().testTag("report_details_input"),
                    minLines = 2,
                    maxLines = 4
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(selectedReason, details) },
                modifier = Modifier.testTag("submit_report_button"),
                colors = ButtonDefaults.buttonColors(containerColor = XCrimson)
            ) {
                Text("Submit Report")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel", color = MoonWhite)
            }
        }
    )
}
