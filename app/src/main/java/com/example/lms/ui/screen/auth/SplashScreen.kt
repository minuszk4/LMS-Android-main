package com.example.lms.ui.screen.auth

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay


@Composable
fun SplashScreen(
    onProgressComplete: () -> Unit = {}
) {
    val primaryColor = Color(0xFF4B5CC4)
    val secondaryColor = Color(0xFF6A79D6)
    val surfacePurple = Color(0xFFE2E9FC)
    val progress = remember { Animatable(0f) }
    val infiniteTransition = rememberInfiniteTransition(label = "splash_infinite")

    val logoScale by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "logo_scale"
    )
    val logoOffsetY by infiniteTransition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "logo_offset"
    )

    val titleVisible = progress.value > 0.08f
    val subtitleVisible = progress.value > 0.14f
    val titleAlpha by animateFloatAsState(
        targetValue = if (titleVisible) 1f else 0f,
        animationSpec = tween(500),
        label = "title_alpha"
    )
    val subtitleAlpha by animateFloatAsState(
        targetValue = if (subtitleVisible) 1f else 0f,
        animationSpec = tween(650),
        label = "subtitle_alpha"
    )

    LaunchedEffect(Unit) {
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 0.72f,
            animationSpec = tween(durationMillis = 1450, easing = FastOutLinearInEasing)
        )
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1050, easing = LinearOutSlowInEasing)
        )
        delay(150)
        onProgressComplete()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    listOf(
                        Color(0xFFF7F8FD),
                        Color(0xFFF1F3FC),
                        Color(0xFFECEFFB)
                    )
                )
            )
    ) {
        AnimatedBackdrop()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            Box(
                modifier = Modifier
                    .size(154.dp)
                    .drawBehind {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(primaryColor.copy(alpha = 0.22f), Color.Transparent)
                            ),
                            radius = size.minDimension * 0.5f
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .graphicsLayer {
                            scaleX = logoScale
                            scaleY = logoScale
                            translationY = logoOffsetY
                        }
                        .background(
                            color = surfacePurple,
                            shape = RoundedCornerShape(28.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .padding(6.dp)
                            .border(
                                width = 1.dp,
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.92f),
                                        Color.White.copy(alpha = 0.92f)
                                    )
                                ),
                                shape = RoundedCornerShape(22.dp)
                            )
                    )

                    Icon(
                        imageVector = Icons.Default.School,
                        contentDescription = null,
                        tint = primaryColor,
                        modifier = Modifier.size(52.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "UniLearn",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF111827),
                modifier = Modifier.graphicsLayer {
                    alpha = titleAlpha
                    translationY = (1f - titleAlpha) * 14f
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Nắm lấy tương lai",
                fontSize = 15.sp,
                color = Color(0xFF6B7280),
                modifier = Modifier.graphicsLayer {
                    alpha = subtitleAlpha
                    translationY = (1f - subtitleAlpha) * 10f
                }
            )

            Spacer(modifier = Modifier.height(42.dp))

            SplashProgressBar(
                progress = progress.value,
                modifier = Modifier
                    .fillMaxWidth(0.84f)
                    .height(10.dp),
                primaryColor = primaryColor,
                secondaryColor = secondaryColor
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "${(progress.value * 100).toInt()}%",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = primaryColor
            )
        }
    }
}

@Composable
private fun SplashProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    primaryColor: Color,
    secondaryColor: Color
) {
    val infiniteTransition = rememberInfiniteTransition(label = "progress_shimmer")
    val shimmerShift by infiniteTransition.animateFloat(
        initialValue = -220f,
        targetValue = 420f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_shift"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color(0xFFDCE2F8))
            .drawBehind {
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.4f),
                    style = Stroke(width = 1.dp.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(999.dp.toPx(), 999.dp.toPx())
                )
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .clip(RoundedCornerShape(999.dp))
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(primaryColor, secondaryColor)
                    )
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = 0.4f),
                                Color.Transparent
                            ),
                            start = Offset(shimmerShift - 160f, 0f),
                            end = Offset(shimmerShift, 0f)
                        )
                    )
            )
        }
    }
}

@Composable
private fun AnimatedBackdrop() {
    val infiniteTransition = rememberInfiniteTransition(label = "backdrop")
    val shift1 by infiniteTransition.animateFloat(
        initialValue = -40f,
        targetValue = 40f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blob_1"
    )
    val shift2 by infiniteTransition.animateFloat(
        initialValue = 30f,
        targetValue = -30f,
        animationSpec = infiniteRepeatable(
            animation = tween(5000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blob_2"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x664B5CC4), Color.Transparent)
                    ),
                    radius = size.minDimension * 0.42f,
                    center = Offset(size.width * 0.2f + shift1, size.height * 0.16f)
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x406A79D6), Color.Transparent)
                    ),
                    radius = size.minDimension * 0.48f,
                    center = Offset(size.width * 0.84f + shift2, size.height * 0.84f)
                )
            }
    )
}

@Preview(
    showBackground = true,
    showSystemUi = true
)
@Composable
fun SplashScreenPreview() {
    MaterialTheme {
        SplashScreen()
    }
}