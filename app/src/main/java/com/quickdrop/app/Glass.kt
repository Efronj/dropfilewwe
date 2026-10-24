package com.quickdrop.app

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

object QD {
    val Blue = Color(0xFF4F8CFF)
    val Violet = Color(0xFF8B5CF6)
    val Pink = Color(0xFFFF6BB5)
    val Mint = Color(0xFF34E0B7)
    val Text2 = Color(0xB3FFFFFF)
}

// ───────────────────────── background ─────────────────────────

@Composable
fun GlassBackground(content: @Composable BoxScope.() -> Unit) {
    val t = rememberInfiniteTransition(label = "bg")
    val a by t.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(16000, easing = LinearEasing), RepeatMode.Reverse),
        label = "a",
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF080B22), Color(0xFF160B33), Color(0xFF06182A))))
    ) {
        Blob(QD.Blue, 320.dp, (-100 + 130 * a).dp, 20.dp)
        Blob(QD.Violet, 280.dp, (160 - 110 * a).dp, 250.dp)
        Blob(QD.Pink, 240.dp, (-50 + 70 * a).dp, 540.dp)
        Blob(QD.Mint, 220.dp, (190 - 90 * a).dp, 690.dp)
        content()
    }
}

@Composable
private fun Blob(c: Color, size: Dp, x: Dp, y: Dp) {
    Box(
        Modifier
            .offset(x, y)
            .size(size)
            .background(Brush.radialGradient(listOf(c.copy(alpha = 0.55f), Color.Transparent)), CircleShape)
    )
}

// ───────────────────────── glass surface ─────────────────────────

fun Modifier.glass(radius: Dp = 28.dp, tint: Color = Color.White, strength: Float = 1f): Modifier {
    val shape = RoundedCornerShape(radius)
    return this
        .clip(shape)
        .background(
            Brush.linearGradient(
                listOf(tint.copy(alpha = 0.22f * strength), tint.copy(alpha = 0.07f * strength))
            ),
            shape,
        )
        .border(
            1.dp,
            Brush.linearGradient(
                listOf(Color.White.copy(alpha = 0.6f), Color.White.copy(alpha = 0.05f), Color.White.copy(alpha = 0.25f))
            ),
            shape,
        )
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth().glass(tint = tint).padding(16.dp), content = content)
}

/** Springy press animation + a light haptic tick, like iOS controls. */
fun Modifier.pressable(onClick: () -> Unit): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current
    val s by animateFloatAsState(
        targetValue = if (pressed) 0.93f else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    this
        .scale(s)
        .clickable(interactionSource = src, indication = null) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        }
}

@Composable
fun GlassButton(text: String, modifier: Modifier = Modifier, accent: Color? = null, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    var m = modifier.height(44.dp).pressable(onClick)
    m = if (accent != null) {
        m.clip(shape).background(Brush.horizontalGradient(listOf(accent, accent.copy(alpha = 0.65f))), shape)
    } else {
        m.glass(22.dp)
    }
    Box(m.padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
        Text(text, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1)
    }
}

@Composable
fun GlassProgress(f: Float, modifier: Modifier = Modifier) {
    val anim by animateFloatAsState(f, tween(250), label = "pg")
    Box(
        modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.14f))
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(anim.coerceIn(0.03f, 1f))
                .clip(CircleShape)
                .background(Brush.horizontalGradient(listOf(QD.Blue, QD.Violet, QD.Pink)))
        )
    }
}

@Composable
fun GlassField(
    value: String,
    onChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
) {
    Box(
        modifier
            .fillMaxWidth()
            .glass(16.dp, strength = 0.8f)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        if (value.isEmpty()) Text(hint, color = Color.White.copy(alpha = 0.4f), fontSize = 16.sp)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = singleLine,
            textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
            cursorBrush = SolidColor(Color.White),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ───────────────────────── the Send orb ─────────────────────────

@Composable
fun SendOrb(onClick: () -> Unit) {
    val t = rememberInfiniteTransition(label = "orb")
    val pulse by t.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "pulse",
    )
    Box(Modifier.size(230.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val base = size.minDimension * 0.27f
            drawCircle(
                Brush.radialGradient(
                    listOf(QD.Violet.copy(alpha = 0.55f), Color.Transparent),
                    center = center,
                    radius = base * 2.0f,
                ),
                radius = base * 2.0f,
                center = center,
            )
            for (i in 0..1) {
                val p = (pulse + i * 0.5f) % 1f
                drawCircle(
                    QD.Blue.copy(alpha = (1f - p) * 0.4f),
                    radius = base + p * base * 0.95f,
                    center = center,
                    style = Stroke(width = 3.dp.toPx()),
                )
            }
        }
        Box(
            Modifier
                .size(128.dp)
                .pressable(onClick)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Color(0xFF6EA8FF), QD.Violet, Color(0xFFB45CFF))))
                .border(
                    1.5.dp,
                    Brush.linearGradient(listOf(Color.White.copy(alpha = 0.9f), Color.White.copy(alpha = 0.1f))),
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            // glossy highlight, the "glass" look
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 6.dp)
                    .fillMaxWidth(0.72f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.55f), Color.Transparent)))
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("⬆", fontSize = 36.sp, color = Color.White)
                Text("Send", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

// ───────────────────────── radar ─────────────────────────

@Composable
fun Radar(peers: List<Peer>, onPick: (Peer) -> Unit) {
    val t = rememberInfiniteTransition(label = "radar")
    val p by t.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Restart),
        label = "p",
    )
    Box(Modifier.fillMaxWidth().height(340.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val maxR = size.minDimension / 2f
            for (i in 0..2) {
                val q = (p + i / 3f) % 1f
                drawCircle(QD.Blue.copy(alpha = (1f - q) * 0.45f), radius = maxR * q, style = Stroke(2.dp.toPx()))
            }
            drawCircle(Color.White.copy(alpha = 0.08f), radius = maxR * 0.66f, style = Stroke(1.dp.toPx()))
        }
        Box(Modifier.size(72.dp).glass(36.dp, strength = 1.6f), contentAlignment = Alignment.Center) {
            Text("You", color = Color.White, fontWeight = FontWeight.Bold)
        }
        val n = peers.size
        peers.forEachIndexed { i, peer ->
            val ang = 2.0 * Math.PI * i / n - Math.PI / 2
            val r = 108.0
            PeerBubble(peer, Modifier.offset(x = (r * cos(ang)).dp, y = (r * sin(ang)).dp), onPick)
        }
    }
}

@Composable
private fun PeerBubble(peer: Peer, modifier: Modifier, onPick: (Peer) -> Unit) {
    Column(modifier.pressable { onPick(peer) }, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(64.dp).glass(32.dp, tint = QD.Mint, strength = 1.8f), contentAlignment = Alignment.Center) {
            Text(peer.name.take(1).uppercase(), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
        Text(
            peer.name,
            fontSize = 12.sp,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(84.dp),
        )
    }
}
