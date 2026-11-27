package com.ogmediaapps.eventbox.model

import android.widget.ImageView
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes

/**
 * How the small floating bubble (and the logo in the panel header) looks. Build one with [Builder]
 * and hand it to `OGEventBoxLog.setBubbleStyle`, or edit the current style in place:
 *
 * ```
 * OGEventBoxLog.setBubbleStyle {
 *     iconRes(R.mipmap.my_logo)
 *     iconScaleType(ImageView.ScaleType.CENTER_CROP)
 *     backgroundColor(Color.parseColor("#0D9379"))
 *     borderColor(Color.parseColor("#CCE6E1"))
 *     borderWidthDp(1.5f)
 *     fullyRound()
 * }
 * ```
 *
 * A value left at its default (0, null or a negative number) means "use the Event Box default".
 */
class OGEventBoxBubbleStyle private constructor(
    /** Drawable shown in the bubble, or 0 for the built-in bolt. */
    @DrawableRes val iconRes: Int,
    /** How [iconRes] is scaled; null means fit-center. Use CENTER_CROP for a logo that fills the circle. */
    val iconScaleType: ImageView.ScaleType?,
    /** Fill behind the icon, or 0 for the default accent color. */
    @ColorInt val backgroundColor: Int,
    /** Color of the border ring, or 0 for the default. */
    @ColorInt val borderColor: Int,
    /** Border width in dp; 0 draws no border and a negative value uses the default. */
    val borderWidthDp: Float,
    /** Corner radius in dp; a negative value makes the bubble fully round (the default). */
    val cornerRadiusDp: Float,
) {
    /** A [Builder] that starts from this style, to change a few values. */
    fun toBuilder(): Builder = Builder(this)

    class Builder() {
        private var iconRes = 0
        private var iconScaleType: ImageView.ScaleType? = null
        private var backgroundColor = 0
        private var borderColor = 0
        private var borderWidthDp = -1f
        private var cornerRadiusDp = -1f

        internal constructor(from: OGEventBoxBubbleStyle) : this() {
            iconRes = from.iconRes
            iconScaleType = from.iconScaleType
            backgroundColor = from.backgroundColor
            borderColor = from.borderColor
            borderWidthDp = from.borderWidthDp
            cornerRadiusDp = from.cornerRadiusDp
        }

        /** The drawable shown in the bubble and in the panel header. */
        fun iconRes(@DrawableRes iconRes: Int) = apply { this.iconRes = iconRes }

        /** How the icon is scaled inside the bubble. */
        fun iconScaleType(scaleType: ImageView.ScaleType?) = apply { this.iconScaleType = scaleType }

        /** Fill color behind the icon. */
        fun backgroundColor(@ColorInt color: Int) = apply { this.backgroundColor = color }

        /** Color of the border ring. */
        fun borderColor(@ColorInt color: Int) = apply { this.borderColor = color }

        /** Border width in dp. */
        fun borderWidthDp(widthDp: Float) = apply { this.borderWidthDp = widthDp }

        /** Removes the border. */
        fun noBorder() = borderWidthDp(0f)

        /** Corner radius in dp; it never exceeds a full circle. */
        fun cornerRadiusDp(radiusDp: Float) = apply { this.cornerRadiusDp = radiusDp }

        /** Makes the bubble a full circle (the default). */
        fun fullyRound() = cornerRadiusDp(-1f)

        fun build() = OGEventBoxBubbleStyle(iconRes, iconScaleType, backgroundColor, borderColor, borderWidthDp, cornerRadiusDp)
    }

    companion object {
        /** The built-in look: bolt icon, accent color, default border, fully round. */
        @JvmField
        val DEFAULT: OGEventBoxBubbleStyle = Builder().build()
    }
}
