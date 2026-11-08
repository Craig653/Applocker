package com.kidlock.app

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView

/** A 4-digit PIN keypad. Calls [onComplete] once 4 digits are entered. */
class PinPad(context: Context, private val onComplete: (String) -> Unit) : LinearLayout(context) {

    private val entered = StringBuilder()
    private val titleView = TextView(context)
    private val dotsView = TextView(context)
    private val messageView = TextView(context)
    private var acceptingInput = true

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        val p = dp(16)
        setPadding(p, p, p, p)

        titleView.apply {
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
        }
        dotsView.apply {
            textSize = 34f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(0, dp(16), 0, dp(4))
        }
        messageView.apply {
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(0xFFFFB4AB.toInt())
            minHeight = dp(28)
        }
        addView(titleView)
        addView(dotsView)
        addView(messageView)

        val grid = GridLayout(context).apply { columnCount = 3 }
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "⌫")
        for (k in keys) {
            val b = Button(context).apply {
                text = k
                textSize = 26f
                setTextColor(Color.WHITE)
                isAllCaps = false
                stateListAnimator = null
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(0x33FFFFFF)
                }
                if (k.isEmpty()) visibility = View.INVISIBLE
                setOnClickListener { press(k) }
            }
            val lp = GridLayout.LayoutParams().apply {
                width = dp(80)
                height = dp(80)
                setMargins(dp(10), dp(10), dp(10), dp(10))
            }
            grid.addView(b, lp)
        }
        addView(grid, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(8)
        })
        refreshDots()
    }

    fun setTitle(text: String) { titleView.text = text }

    fun setMessage(text: String) { messageView.text = text }

    fun clear() {
        entered.setLength(0)
        acceptingInput = true
        refreshDots()
    }

    private fun press(key: String) {
        if (!acceptingInput) return
        when {
            key == "⌫" -> if (entered.isNotEmpty()) entered.deleteCharAt(entered.length - 1)
            key.isNotEmpty() && entered.length < 4 -> entered.append(key)
        }
        refreshDots()
        if (entered.length == 4) {
            acceptingInput = false
            val pin = entered.toString()
            postDelayed({ onComplete(pin) }, 120)
        }
    }

    private fun refreshDots() {
        dotsView.text = (0 until 4).joinToString("  ") { if (it < entered.length) "●" else "○" }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
