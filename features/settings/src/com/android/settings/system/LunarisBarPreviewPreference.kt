/*
 * Copyright (C) 2026 Yet Another AOSP Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.settings.system

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.toArgb
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.star
import androidx.graphics.shapes.toPath
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.android.internal.util.lunaris.LunarisBarSettings
import com.android.settings.R
import kotlin.math.max
import kotlin.math.min

internal class LunarisBarPreviewPreference(context: Context) : Preference(context) {
    private var settings = LunarisBarSettings()
    private var widthScale = 100
    private var heightScale = 100
    private var expanded = false

    init {
        key = "lunaris_bar_inline_preview"
        order = Int.MIN_VALUE
        isPersistent = false
        isSelectable = false
        layoutResource = R.layout.lunaris_bar_preview
    }

    fun update(config: LunarisBarSettings, width: Int, height: Int) {
        settings = config
        widthScale = width
        heightScale = height
        notifyChanged()
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        holder.isDividerAllowedAbove = false
        holder.isDividerAllowedBelow = false
        val view = holder.findViewById(R.id.lunaris_bar_preview_view) as LunarisBarPreviewView
        view.bind(settings, widthScale, heightScale, expanded)
        view.setOnClickListener {
            expanded = !expanded
            view.bind(settings, widthScale, heightScale, expanded)
        }
    }
}

/** A local sample renderer; no Settings writes, media sessions, or event listeners. */
class LunarisBarPreviewView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var settings = LunarisBarSettings()
    private var widthScale = 1f
    private var heightScale = 1f
    private var expanded = false
    private var light = dynamicLightColorScheme(context)
    private var dark = dynamicDarkColorScheme(context)

    internal fun bind(config: LunarisBarSettings, width: Int, height: Int, isExpanded: Boolean) {
        settings = config
        widthScale = width.coerceIn(70, 140) / 100f
        heightScale = height.coerceIn(70, 140) / 100f
        expanded = isExpanded
        light = dynamicLightColorScheme(context)
        dark = dynamicDarkColorScheme(context)
        stateDescription = context.getString(if (expanded)
            R.string.lunaris_bar_preview_expanded else R.string.lunaris_bar_preview_compact)
        requestLayout()
        invalidate()
    }

    private fun dp(value: Float) = value * resources.displayMetrics.density

    private fun top() = dp(32f + settings.offsetY)

    private fun chipHeight() = dp(
        (if (settings.enabled("media_mini", true)) 32f
        else settings.integer("camera_height", 24, 8, 64).toFloat()) * heightScale * settings.miniScale,
    )

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val contentHeight = if (expanded) dp(128f) else chipHeight()
        val placement = if (expanded && settings.expandedMode == 1) dp(24f) else 0f
        setMeasuredDimension(
            MeasureSpec.getSize(widthMeasureSpec),
            resolveSize(max(dp(if (expanded) 172f else 96f), top() + placement + contentHeight + dp(16f)).toInt(), heightMeasureSpec),
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0) return
        val isDark = when (settings.theme) {
            1 -> false
            2 -> true
            else -> resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        }
        val scheme = if (isDark) dark else light
        val mini = !expanded
        val colors = palette(settings.palette(mini, isDark), scheme,
            settings.color(mini, isDark, false), settings.color(mini, isDark, true))
        val resting = mini && !settings.enabled("media_mini", true)
        val gap = dp(settings.integer("camera_width", 25, 0, 120).toFloat())
        val icon = dp(20f * heightScale * settings.miniScale)
        val chipWidth = (gap + if (resting) dp(16f) else icon * 2f + dp(26f * settings.miniScale)) * widthScale
        val contentWidth = if (expanded) {
            if (settings.isFullWidth) width.toFloat() - dp(16f) else min(dp(360f), width.toFloat() - dp(32f))
        } else min(chipWidth, dp(240f * widthScale) + gap)
        val margin = dp(8f)
        val desiredLeft = when (settings.alignment) {
            1 -> margin
            2 -> width - margin - contentWidth
            else -> (width - contentWidth) / 2f
        } + dp(settings.offsetX.toFloat())
        val left = desiredLeft.coerceIn(margin, max(margin, width - margin - contentWidth))
        val y = top() + if (expanded && settings.expandedMode == 1) dp(24f) else 0f
        val bounds = RectF(left, y, left + contentWidth, y + if (expanded) dp(128f) else chipHeight())
        val radius = dp(if (expanded && settings.expandedShape == 0) 48f else settings.cornerRadius.toFloat())
        val path = surfacePath(bounds, radius, !expanded || settings.expandedShape == 2)
        val opacity = if (resting) settings.collapsedOpacity else settings.opacity(mini, isDark)
        paint.color = colors.first
        paint.alpha = (Color.alpha(colors.first) * opacity).toInt()
        paint.style = if (resting && settings.collapsedBorder > 0) Paint.Style.STROKE else Paint.Style.FILL
        paint.strokeWidth = dp(settings.collapsedBorder.toFloat())
        if (!resting && settings.shadow(isDark)) {
            paint.setShadowLayer(dp(if (mini) min(settings.shadowElevation, 4).toFloat() else settings.shadowElevation.toFloat()), 0f, dp(2f), 0x33000000)
        }
        canvas.drawPath(path, paint)
        paint.clearShadowLayer()
        paint.alpha = 255
        if (!resting && settings.outline(isDark) && settings.outlineWidth > 0f) {
            paint.color = colors.second
            paint.alpha = if (isDark) 56 else 36
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(settings.outlineWidth)
            canvas.drawPath(path, paint)
            paint.alpha = 255
        }
        paint.style = Paint.Style.FILL
        paint.color = colors.second
        if (expanded) {
            val artwork = RectF(bounds.left + dp(20f), bounds.top + dp(20f), bounds.left + dp(64f), bounds.top + dp(64f))
            drawIcon(canvas, artwork, settings.artworkShape, colors.second)
            paint.textSize = dp(14f) * resources.configuration.fontScale
            val song = context.getString(R.string.lunaris_bar_preview_song)
            val text = android.text.TextUtils.ellipsize(song, android.text.TextPaint(paint), (bounds.width() - dp(104f)).coerceAtLeast(0f), android.text.TextUtils.TruncateAt.END)
            canvas.drawText(text.toString(), bounds.left + dp(80f), bounds.top + dp(45f), paint)
            if (settings.isSliderVisible) canvas.drawRoundRect(bounds.left + dp(24f), bounds.bottom - dp(26f), bounds.right - dp(24f), bounds.bottom - dp(22f), dp(2f), dp(2f), paint)
            for (offset in listOf(-32f, 0f, 32f)) {
                val cx = bounds.centerX() + dp(offset)
                drawIcon(canvas, RectF(cx - dp(9f), bounds.top + dp(76f), cx + dp(9f), bounds.top + dp(94f)), settings.integer("media_button_shape", 2, 0, 31), colors.second)
            }
        } else if (!resting) {
            val iconTop = bounds.centerY() - icon / 2f
            drawIcon(canvas, RectF(bounds.left + dp(8f), iconTop, bounds.left + dp(8f) + icon, iconTop + icon), settings.integer("mini_shape", 2, 0, 31), colors.second)
            for (index in 0..2) {
                val x = bounds.right - dp(8f) - icon + dp(index * 6f)
                val barHeight = icon * listOf(0.45f, 0.9f, 0.65f)[index]
                canvas.drawRoundRect(x, bounds.centerY() - barHeight / 2f, x + dp(3f), bounds.centerY() + barHeight / 2f, dp(1.5f), dp(1.5f), paint)
            }
        }
    }

    private fun palette(index: Int, scheme: ColorScheme, background: Int, foreground: Int): Pair<Int, Int> {
        val colors = when (index) {
            1 -> scheme.primaryContainer to scheme.onPrimaryContainer
            2 -> scheme.secondaryContainer to scheme.onSecondaryContainer
            3 -> scheme.tertiaryContainer to scheme.onTertiaryContainer
            4 -> return Color.BLACK to Color.WHITE
            5 -> scheme.primary to scheme.onPrimary
            6 -> return background to foreground
            7 -> light.surfaceBright to light.onSurface
            8 -> dark.surfaceBright to dark.onSurface
            9 -> light.primaryContainer to light.onPrimaryContainer
            10 -> dark.primaryContainer to dark.onPrimaryContainer
            11 -> light.secondaryContainer to light.onSecondaryContainer
            12 -> dark.secondaryContainer to dark.onSecondaryContainer
            13 -> light.tertiaryContainer to light.onTertiaryContainer
            14 -> dark.tertiaryContainer to dark.onTertiaryContainer
            15 -> light.primary to light.onPrimary
            16 -> dark.primary to dark.onPrimary
            17 -> scheme.tertiary to scheme.onTertiary
            18 -> light.tertiary to light.onTertiary
            19 -> dark.tertiary to dark.onTertiary
            20 -> scheme.surfaceContainerHighest to scheme.primary
            else -> scheme.surfaceBright to scheme.onSurface
        }
        return colors.first.toArgb() to colors.second.toArgb()
    }

    private fun surfacePath(bounds: RectF, radius: Float, squircle: Boolean): Path {
        val r = min(radius, min(bounds.width(), bounds.height()) / 2f)
        if (!squircle) return Path().apply { addRoundRect(bounds, r, r, Path.Direction.CW) }
        val h = r * 0.49477f
        return Path().apply {
            moveTo(bounds.left + r, bounds.top)
            lineTo(bounds.right - r, bounds.top)
            cubicTo(bounds.right - r + h, bounds.top, bounds.right, bounds.top + r - h, bounds.right, bounds.top + r)
            lineTo(bounds.right, bounds.bottom - r)
            cubicTo(bounds.right, bounds.bottom - r + h, bounds.right - r + h, bounds.bottom, bounds.right - r, bounds.bottom)
            lineTo(bounds.left + r, bounds.bottom)
            cubicTo(bounds.left + r - h, bounds.bottom, bounds.left, bounds.bottom - r + h, bounds.left, bounds.bottom - r)
            lineTo(bounds.left, bounds.top + r)
            cubicTo(bounds.left, bounds.top + r - h, bounds.left + r - h, bounds.top, bounds.left + r, bounds.top)
            close()
        }
    }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    private fun drawIcon(canvas: Canvas, bounds: RectF, shape: Int, color: Int) {
        val polygon = when (shape) {
            3 -> RoundedPolygon.star(10, radius = 68f, innerRadius = 55f, rounding = CornerRounding(20f), centerX = 63f, centerY = 63f)
            5 -> MaterialShapes.Square
            6 -> RoundedPolygon(6, radius = 63f, centerX = 63f, centerY = 63f, rounding = CornerRounding(20f))
            7 -> RoundedPolygon.star(4, radius = 90f, innerRadius = 50f, rounding = CornerRounding(30f, 0.5f), centerX = 63f, centerY = 63f)
            8 -> RoundedPolygon(4, radius = 68f, centerX = 63f, centerY = 63f, rounding = CornerRounding(30f))
            9 -> RoundedPolygon(3, radius = 80f, centerX = 58f, centerY = 63f, rounding = CornerRounding(15.75f, 0.5f))
            10 -> MaterialShapes.Triangle
            12 -> MaterialShapes.Arrow
            13 -> MaterialShapes.Sunny
            14 -> MaterialShapes.VerySunny
            15 -> MaterialShapes.Diamond
            16 -> MaterialShapes.Pentagon
            17 -> MaterialShapes.SemiCircle
            18 -> MaterialShapes.Arch
            19 -> MaterialShapes.Ghostish
            20 -> MaterialShapes.Pill
            21 -> MaterialShapes.Burst
            22 -> MaterialShapes.SoftBurst
            23 -> MaterialShapes.Clover8Leaf
            24 -> MaterialShapes.Clover4Leaf
            25 -> MaterialShapes.Flower
            26 -> MaterialShapes.ClamShell
            27 -> MaterialShapes.Cookie4Sided
            28 -> MaterialShapes.Fan
            29 -> MaterialShapes.PixelCircle
            30 -> MaterialShapes.Bun
            31 -> MaterialShapes.Heart
            else -> null
        }
        val path = (if (shape == 1) android.util.PathParser.createPathFromPathData(
            android.content.res.Resources.getSystem().getString(com.android.internal.R.string.config_icon_mask)) else null)
            ?: polygon?.toPath() ?: Path().apply {
                when (shape) {
                    0, 11 -> addRect(6f, 6f, 120f, 120f, Path.Direction.CW)
                    4 -> addRoundRect(3f, 3f, 123f, 123f, 32f, 32f, Path.Direction.CW)
                    else -> addCircle(63f, 63f, 60f, Path.Direction.CW)
                }
            }
        if (shape == 7) path.transform(Matrix().apply { setRotate(45f, 63f, 63f) })
        val source = RectF().also { path.computeBounds(it, true) }
        path.transform(Matrix().apply { setRectToRect(source, bounds, Matrix.ScaleToFit.FILL) })
        paint.color = color
        canvas.drawPath(path, paint)
    }
}
