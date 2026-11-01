package com.velvet.music.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.velvet.music.R
import com.velvet.music.data.VelvetAccent
import com.velvet.music.ui.components.AccentPicker
import com.velvet.music.ui.components.BrandMark
import com.velvet.music.ui.components.GradientPill
import com.velvet.music.ui.theme.Hairline
import com.velvet.music.ui.theme.Ink
import com.velvet.music.ui.theme.InstrumentSerifItalic
import com.velvet.music.ui.theme.LocalAccent
import com.velvet.music.ui.theme.TextPrimary
import com.velvet.music.ui.theme.TextSecondary
import com.velvet.music.ui.theme.TextTertiary

@Composable
fun OnboardingScreen(
    accent: VelvetAccent,
    onAccent: (VelvetAccent) -> Unit,
    onChooseFolder: () -> Unit
) {
    val palette = LocalAccent.current
    var showHowItWorks by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Ink,
                        Color(0xFF0E0916),
                        Color(0xFF09070D)
                    )
                )
            )
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(palette.glow.copy(alpha = 0.30f), Color.Transparent),
                    center = Offset(size.width * 0.88f, size.height * 0.10f),
                    radius = size.width * 0.9f
                ),
                radius = size.width * 0.9f,
                center = Offset(size.width * 0.88f, size.height * 0.10f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color(0xFFE8C97A).copy(alpha = 0.09f), Color.Transparent),
                    center = Offset(size.width * 0.05f, size.height * 0.74f),
                    radius = size.width * 0.8f
                ),
                radius = size.width * 0.8f,
                center = Offset(size.width * 0.05f, size.height * 0.74f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 26.dp)
        ) {
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                BrandMark(38.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "VELVET",
                        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 5.sp),
                        color = TextPrimary
                    )
                    Text(
                        text = "Private listening room",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary
                    )
                }
                AccentPicker(selected = accent, onSelect = onAccent)
            }

            Spacer(Modifier.height(30.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(246.dp),
                contentAlignment = Alignment.Center
            ) {
                VinylDisc(size = 228.dp)
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text = buildAnnotatedString {
                    append("Music that's\nactually ")
                    withStyle(
                        SpanStyle(
                            fontFamily = InstrumentSerifItalic,
                            color = palette.bright
                        )
                    ) {
                        append("yours.")
                    }
                },
                style = MaterialTheme.typography.displayLarge,
                color = TextPrimary
            )

            Spacer(Modifier.height(14.dp))

            Text(
                text = "Your folders. Your files. A listening room with velvet walls that never leaves your phone.",
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
                modifier = Modifier.padding(end = 20.dp)
            )

            Spacer(Modifier.height(18.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FeatureTag("On-device")
                FeatureTag("No account")
                FeatureTag("Hi-res ready")
            }

            Spacer(Modifier.height(32.dp))

            GradientPill(
                text = "Choose your folder",
                icon = painterResource(R.drawable.ic_folder),
                modifier = Modifier.fillMaxWidth(),
                onClick = onChooseFolder
            )

            Spacer(Modifier.height(10.dp))

            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TextButton(onClick = { showHowItWorks = true }) {
                    Text(
                        text = "How Velvet works",
                        color = TextSecondary,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            Text(
                text = "Velvet only reads the folder you select. Nothing is uploaded, ever.",
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(horizontal = 12.dp)
            )

            Spacer(Modifier.height(26.dp))
        }
    }

    if (showHowItWorks) {
        AlertDialog(
            onDismissRequest = { showHowItWorks = false },
            containerColor = Color(0xFF15111D),
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            shape = RoundedCornerShape(26.dp),
            title = {
                Text(
                    text = "How Velvet works",
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    HowStep("01", "Pick a folder", "Choose any folder on your phone. Velvet indexes every audio file inside it.")
                    HowStep("02", "Press play", "Playback lives in a background service, so the music keeps going when the screen turns off.")
                    HowStep("03", "Stay in control", "Lock screen and notification controls include previous, play, next and a full progress bar.")
                }
            },
            confirmButton = {
                TextButton(onClick = { showHowItWorks = false }) {
                    Text("Got it", color = palette.primary)
                }
            }
        )
    }
}

@Composable
private fun FeatureTag(text: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.035f))
            .border(1.dp, Hairline, RoundedCornerShape(50))
            .padding(horizontal = 11.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(4.dp)
                .clip(CircleShape)
                .background(LocalAccent.current.primary)
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
    }
}

@Composable
private fun HowStep(number: String, title: String, body: String) {
    Row {
        Text(
            text = number,
            style = MaterialTheme.typography.headlineSmall,
            color = LocalAccent.current.primary,
            modifier = Modifier.width(38.dp)
        )
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun VinylDisc(size: androidx.compose.ui.unit.Dp) {
    val accent = LocalAccent.current
    val transition = rememberInfiniteTransition(label = "vinyl")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(26000, easing = LinearEasing)
        ),
        label = "rotation"
    )
    val pulse by transition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(
        modifier = Modifier
            .size(size)
            .alpha(0.98f),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(accent.glow.copy(alpha = 0.34f * pulse), Color.Transparent),
                    center = center,
                    radius = this.size.minDimension * 0.55f
                ),
                radius = this.size.minDimension * 0.55f,
                center = center
            )
            drawCircle(
                color = Color(0xFF0D0A12),
                radius = this.size.minDimension * 0.455f,
                center = center
            )
            for (ring in 1..14) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.030f),
                    radius = this.size.minDimension * (0.16f + ring * 0.020f),
                    center = center,
                    style = Stroke(width = 1.dp.toPx())
                )
            }
            drawCircle(
                brush = Brush.linearGradient(
                    listOf(accent.gradientStart, accent.gradientEnd),
                    start = Offset(center.x - 60f, center.y - 60f),
                    end = Offset(center.x + 60f, center.y + 60f)
                ),
                radius = this.size.minDimension * 0.19f,
                center = center
            )
            rotate(rotation) {
                drawArc(
                    brush = Brush.sweepGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = 0.30f), Color.Transparent),
                        center = center
                    ),
                    startAngle = 0f,
                    sweepAngle = 52f,
                    useCenter = false,
                    topLeft = Offset(center.x - this.size.width * 0.44f, center.y - this.size.height * 0.44f),
                    size = Size(this.size.width * 0.88f, this.size.height * 0.88f),
                    style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            drawCircle(
                color = Color(0xFF07060A),
                radius = this.size.minDimension * 0.035f,
                center = center
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.30f),
                radius = this.size.minDimension * 0.008f,
                center = center
            )
        }
        Icon(
            painter = painterResource(R.drawable.ic_note),
            contentDescription = null,
            tint = accent.onAccent.copy(alpha = 0.9f),
            modifier = Modifier.size(28.dp)
        )
    }
}
