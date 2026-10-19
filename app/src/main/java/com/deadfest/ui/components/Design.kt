package com.deadfest.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deadfest.R
import com.deadfest.ui.theme.*
import kotlinx.coroutines.delay

/**
 * Dead-Fest design kit (v2.0.7.1).
 *
 * These are the reusable pieces every screen is built from. They exist so headings, cards,
 * buttons, empty states and spacing look identical across all 14 screens instead of each
 * screen inventing its own.
 *
 * Performance rules obeyed here:
 *  - No bitmap assets, no blur, no shadow passes. Everything is a gradient / solid fill.
 *  - Entrance animation is a single `graphicsLayer` (draw phase only, never re-layout).
 *  - Every animation is skipped entirely when Performance Mode is on.
 */

// ==================================================================== ambient background

/**
 * App-wide background: near-black ink with two warm radial washes and (when Performance Mode is
 * off) a faint terminal grid. Rendered once behind the whole NavHost, so per-screen cost is zero.
 */
@Composable
fun DeadFestBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val reduced = LocalPerformanceMode.current
    Box(modifier.fillMaxSize().background(Ink0)) {
        Canvas(Modifier.fillMaxSize()) {
            // Ember wash from the top, where the header sits.
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Ember.copy(alpha = 0.14f), Color.Transparent),
                    center = Offset(size.width * 0.5f, -size.height * 0.06f),
                    radius = size.height * 0.62f
                )
            )
            // Cool counter-glow so the lower half doesn't fall flat.
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Plasma.copy(alpha = 0.05f), Color.Transparent),
                    center = Offset(size.width * 1.08f, size.height * 0.34f),
                    radius = size.height * 0.45f
                )
            )
            // Faint amber corner for warmth.
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Amber.copy(alpha = 0.045f), Color.Transparent),
                    center = Offset(-size.width * 0.1f, size.height * 0.86f),
                    radius = size.height * 0.42f
                )
            )
            if (!reduced) {
                val step = 46.dp.toPx()
                val stroke = 1f
                var x = step
                while (x < size.width) {
                    drawLine(InkGrid, Offset(x, 0f), Offset(x, size.height), stroke)
                    x += step
                }
                var y = step
                while (y < size.height) {
                    drawLine(InkGrid, Offset(0f, y), Offset(size.width, y), stroke)
                    y += step
                }
            }
        }
        content()
    }
}

// ==================================================================== screen shell

/**
 * Standard screen frame: brand header + a body slot for the caller's own list/grid.
 *
 * The caller keeps ownership of its scroll container (LazyColumn / LazyVerticalGrid) so nested
 * scrolling never breaks; [body] just gets the remaining vertical space.
 */
@Composable
fun ScreenScaffold(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    header: (@Composable () -> Unit)? = null,
    body: @Composable BoxScope.() -> Unit
) {
    Column(modifier.fillMaxSize()) {
        ScreenHeader(title = title, subtitle = subtitle, onBack = onBack, trailing = trailing)
        if (header != null) {
            Box(Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter)) { header() }
            Spacer(Modifier.height(Spacing.sm))
        }
        Box(Modifier.fillMaxWidth().weight(1f), content = body)
    }
}

// ==================================================================== surfaces

/**
 * The standard Dead-Fest card. Pass an [accent] for a tinted, "highlighted" variant
 * (season panels, warnings, featured items).
 */
@Composable
fun SurfaceCard(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    onClick: (() -> Unit)? = null,
    padding: Dp = Spacing.md,
    shape: Shape = RoundedCornerShape(Radius.lg),
    content: @Composable ColumnScope.() -> Unit
) {
    val clickable = if (onClick != null) Modifier.pressClick(shape, onClick = onClick) else Modifier
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (accent == null) Ink2 else accent.copy(alpha = AccentFill))
            .borderHair(accent, shape)
            .then(clickable)
            .padding(padding),
        content = content
    )
}

/**
 * Fill strength for an accent-tinted card. Deliberately far fainter than [Tint.wash] (the icon
 * backing): a card is a large area, so the same alpha that reads as a subtle chip would wash out
 * the whole surface.
 */
private const val AccentFill = 0.06f

/** 1dp hairline border, optionally accent-tinted. Follows the card's own [shape]. */
private fun Modifier.borderHair(accent: Color?, shape: Shape): Modifier =
    border(
        border = BorderStroke(Stroke.hair, accent?.copy(alpha = Tint.edge) ?: InkLine),
        shape = shape
    )

/**
 * Feature panel with a gradient wash, used for the highest-priority block on a screen
 * (season countdown, level progression, active event).
 *
 * The brand skull is bled off the bottom-right corner at very low alpha as artwork. It is a single
 * tinted vector drawn under the content, so it costs one extra draw op and no bitmaps — and it is
 * what makes the priority block on any screen read as Dead-Fest rather than as a generic card.
 */
@Composable
fun HeroPanel(
    eyebrow: String,
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    accent: Color = Ember,
    gradientEnd: Color = accent,
    badge: (@Composable () -> Unit)? = null,
    watermark: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.xl))
            .background(
                Brush.verticalGradient(
                    listOf(accent.copy(alpha = 0.16f), gradientEnd.copy(alpha = 0.02f), Color.Transparent)
                )
            )
            .background(Ink2)
            .outline(Radius.xl, accent.copy(alpha = Tint.edge))
    ) {
        if (watermark) {
            Image(
                painter = painterResource(R.drawable.ic_deadfest_mark),
                contentDescription = null,
                colorFilter = ColorFilter.tint(accent.copy(alpha = 0.06f)),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 24.dp, y = 20.dp)
                    .size(128.dp)
            )
        }
        Column(Modifier.padding(Spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(Radius.sm))
                        .background(accent.copy(alpha = Tint.fill)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(Spacing.sm))
                Column(Modifier.weight(1f)) {
                    Text(
                        eyebrow.uppercase(),
                        color = accent,
                        style = DeadFestType.microLabel
                    )
                    Text(
                        title,
                        color = TextHi,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (badge != null) badge()
            }
            Spacer(Modifier.height(Spacing.md))
            content()
        }
    }
}

/** Accent-tinted hairline border used by [HeroPanel] and other bespoke panels. */
private fun Modifier.outline(radius: Dp, color: Color): Modifier =
    border(BorderStroke(Stroke.hair, color), RoundedCornerShape(radius))

// ==================================================================== actions

/** Full-width primary action. Solid ember, heavy label, 52dp touch target. */
@Composable
fun PrimaryAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    accent: Color = Ember
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(Radius.md),
        colors = ButtonDefaults.buttonColors(
            containerColor = accent,
            contentColor = Color.White,
            disabledContainerColor = Ink4,
            disabledContentColor = TextLow
        ),
        elevation = null
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Spacing.xs))
        }
        Text(text.uppercase(), style = DeadFestType.action)
    }
}

/** Quiet secondary action — outline only, no fill. */
@Composable
fun GhostAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    accent: Color = TextMid
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(Radius.md),
        border = BorderStroke(Stroke.hair, if (enabled) accent.copy(alpha = Tint.edge) else InkLine),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = accent)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(Spacing.xs))
        }
        Text(text.uppercase(), style = DeadFestType.chip)
    }
}

// ==================================================================== small parts

/** Horizontal label/value row used inside cards and settings groups. */
@Composable
fun KeyValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = TextHi,
    labelColor: Color = TextMid
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            color = labelColor,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            value,
            color = valueColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1
        )
    }
}

/** Selectable pill, used for filters, admin module switching and choice rows. */
@Composable
fun PillChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = Ember
) {
    val tint = if (selected) accent else TextMid
    Surface(
        modifier = modifier.pressClick(RoundedCornerShape(Radius.pill), onClick = onClick),
        shape = RoundedCornerShape(Radius.pill),
        color = if (selected) accent.copy(alpha = Tint.fill) else Ink3,
        border = BorderStroke(Stroke.hair, if (selected) accent.copy(alpha = Tint.edgeStrong) else InkLine)
    ) {
        Text(
            text.uppercase(),
            color = tint,
            style = DeadFestType.microLabel,
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
            maxLines = 1
        )
    }
}

/** Slim progress rail. No animation when Performance Mode is on. */
@Composable
fun DeadFestProgress(
    fraction: Float,
    modifier: Modifier = Modifier,
    accent: Color = Ember,
    height: Dp = 6.dp
) {
    val target = fraction.coerceIn(0f, 1f)
    val reduced = LocalPerformanceMode.current
    val value by androidx.compose.animation.core.animateFloatAsState(
        targetValue = target,
        animationSpec = if (reduced) androidx.compose.animation.core.snap()
        else tween(600, easing = FastOutSlowInEasing),
        label = "progress"
    )
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(Radius.pill))
            .background(Ink4)
    ) {
        if (value > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(value)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(Radius.pill))
                    .background(Brush.horizontalGradient(listOf(accent, accent.copy(alpha = 0.72f))))
            )
        }
    }
}

/** Medal for standings (1/2/3) or a neutral rank chip beyond that. */
@Composable
fun RankBadge(rank: Int, modifier: Modifier = Modifier) {
    val color = when (rank) {
        1 -> Amber
        2 -> Silver
        3 -> Bronze
        else -> null
    }
    if (color == null) {
        Text(
            "#$rank",
            color = TextLow,
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            modifier = modifier.width(34.dp)
        )
    } else {
        Box(
            modifier = modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = Tint.fill))
                .border(
                    BorderStroke(Stroke.hair, color.copy(alpha = Tint.edgeStrong)),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text("$rank", color = color, fontSize = 12.sp, fontWeight = FontWeight.Black)
        }
    }
}

/** Thin ember rule used to separate major blocks without a hard line. */
@Composable
fun GradientRule(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(Color.Transparent, Ember.copy(alpha = 0.35f), Color.Transparent)
                )
            )
    )
}

/**
 * Pointy-top hexagon. Used only by [ItemEmblem] — a hex plate reads as "game item" far better
 * than a rounded square, and costs one path instead of a bitmap.
 */
private val HexagonShape = object : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.5f, 0f)
            lineTo(w, h * 0.26f)
            lineTo(w, h * 0.74f)
            lineTo(w * 0.5f, h)
            lineTo(0f, h * 0.74f)
            lineTo(0f, h * 0.26f)
            close()
        }
        // Fully qualified: the theme also exports a colour named `Outline`, and the star import
        // would otherwise win expression resolution here.
        return androidx.compose.ui.graphics.Outline.Generic(path)
    }
}

/**
 * Item artwork for the Black Market.
 *
 * A hex plate in the item's own accent, gradient-filled from the top so it catches the light like
 * the rest of the UI, with the semantic glyph sitting on top. Each item type gets a distinct hue,
 * which is what makes the shop scannable — colour identifies the item before you read the name.
 *
 * Pure vector: no bitmaps, no density buckets, no per-item asset files.
 */
@Composable
fun ItemEmblem(
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(HexagonShape)
            .background(
                Brush.verticalGradient(
                    listOf(accent.copy(alpha = 0.34f), accent.copy(alpha = 0.06f))
                )
            )
            .background(Ink3)
            .border(BorderStroke(Stroke.hair, accent.copy(alpha = Tint.edgeStrong)), HexagonShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(size * 0.44f)
        )
    }
}

// ==================================================================== states

/** Centred spinner with a label. Used while a screen's first payload is loading. */
@Composable
fun LoadingState(text: String = "LOADING", modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        CircularProgressIndicator(
            color = Ember,
            strokeWidth = 2.dp,
            modifier = Modifier.size(28.dp)
        )
        Text(text.uppercase(), color = TextLow, style = DeadFestType.microLabel)
    }
}

/** Friendly recoverable-failure state. Prefer this over a bare error string. */
@Composable
fun ErrorState(
    message: String,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Box(
            Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Ember.copy(alpha = Tint.wash)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.CloudOff, contentDescription = null, tint = Ember, modifier = Modifier.size(26.dp))
        }
        Text("SIGNAL LOST", color = TextHi, fontWeight = FontWeight.Black, fontSize = 14.sp)
        Text(message, color = TextMid, fontSize = 12.sp, textAlign = TextAlign.Center, lineHeight = 17.sp)
        if (onRetry != null) {
            Spacer(Modifier.height(Spacing.xxs))
            GhostAction("Retry", onRetry)
        }
    }
}

/** Richer empty state with an optional call to action. */
@Composable
fun EmptyStateCard(
    icon: ImageVector = Icons.Default.Inbox,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.lg))
            .background(Ink2)
            .outline(Radius.lg, InkLine)
            .padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Box(
            Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(Ink3),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = TextLow, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(Spacing.xxs))
        Text(title, color = TextHi, fontWeight = FontWeight.Black, fontSize = 15.sp, textAlign = TextAlign.Center)
        Text(message, color = TextMid, fontSize = 12.sp, textAlign = TextAlign.Center, lineHeight = 17.sp)
        if (action != null) {
            Spacer(Modifier.height(Spacing.xs))
            action()
        }
    }
}

// ==================================================================== motion

/**
 * Subtle fade + rise when an item first appears. Draw-phase only (`graphicsLayer`), so it costs
 * no re-layout and never affects scroll performance. Skipped under Performance Mode.
 *
 * [index] staggers siblings; it is clamped so a 30-item list doesn't animate for seconds.
 */
@Composable
fun Modifier.enterOnMount(index: Int = 0, distance: Float = 22f): Modifier {
    if (LocalPerformanceMode.current) return this
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index.coerceIn(0, 7) * 38L)
        progress.animateTo(1f, tween(durationMillis = 280, easing = FastOutSlowInEasing))
    }
    val v = progress.value
    return this.graphicsLayer {
        alpha = v
        translationY = (1f - v) * distance
    }
}
