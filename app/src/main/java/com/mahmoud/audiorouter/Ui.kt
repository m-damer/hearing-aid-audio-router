package com.mahmoud.audiorouter

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** Tiny view helpers, so the screens stay readable without XML layouts. */
object Ui {
    const val INK = 0xFF1B1B1F.toInt()
    const val MUTED = 0xFF5F6368.toInt()
    const val BLUE = 0xFF1A73E8.toInt()
    const val GREEN = 0xFF188038.toInt()
    const val RED = 0xFFC5221F.toInt()

    fun dp(c: Context, v: Int) = (v * c.resources.displayMetrics.density).toInt()

    fun text(c: Context, s: String, size: Float = 15f, bold: Boolean = false, color: Int = INK): TextView =
        TextView(c).apply {
            text = s
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            setLineSpacing(0f, 1.15f)
        }

    fun button(c: Context, label: String, onClick: () -> Unit): Button =
        Button(c).apply {
            text = label
            isAllCaps = false
            setOnClickListener { onClick() }
        }

    fun card(c: Context, vararg children: View): LinearLayout =
        LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(c, 16)
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(c, 20).toFloat()
                setStroke(dp(c, 1), 0xFFE3E6EE.toInt())
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(c, 12) }
            children.forEach { addView(it) }
        }

    fun gap(c: Context, h: Int = 8) = View(c).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(c, h))
    }

    fun row(c: Context, vararg children: View): LinearLayout =
        LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            children.forEach { addView(it) }
        }
}
