package com.nafsguard.admin

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*

/** ডার্ক গ্রিন থিম (ইউজার অ্যাপের মতোই) */
object Ui {
    val BG_TOP = Color.parseColor("#03201A")
    val BG_BOTTOM = Color.parseColor("#0C4A33")
    val NEON = Color.parseColor("#22E566")
    val CARD = Color.parseColor("#0F3D2C")
    val CARD_STROKE = Color.parseColor("#1D6B4B")
    val TXT = Color.WHITE
    val TXT2 = Color.parseColor("#A6CDB6")
    val RED = Color.parseColor("#FF7A7A")
    val AMBER = Color.parseColor("#FFB74D")

    fun dp(c: Context, v: Int) = (v * c.resources.displayMetrics.density).toInt()
    fun dp(c: Context, v: Float) = (v * c.resources.displayMetrics.density).toInt()

    private fun lp(c: Context, top: Int) =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(c, top) }

    fun bg() = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(BG_TOP, BG_BOTTOM))

    fun bars(a: Activity) {
        a.window.statusBarColor = BG_TOP
        a.window.navigationBarColor = BG_BOTTOM
    }

    fun heroLogo(c: Context, size: Int): FrameLayout {
        val box = size + 44
        return FrameLayout(c).apply {
            background = GradientDrawable().apply {
                gradientType = GradientDrawable.RADIAL_GRADIENT
                gradientRadius = dp(c, box / 2).toFloat()
                setColors(intArrayOf(0x5522E566, 0x00000000))
            }
            layoutParams = LinearLayout.LayoutParams(dp(c, box), dp(c, box)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(c, 4)
            }
            addView(ImageView(c).apply { setImageResource(R.drawable.logo) },
                FrameLayout.LayoutParams(dp(c, size), dp(c, size), Gravity.CENTER))
        }
    }

    fun dtext(c: Context, s: String, size: Float = 15f, bold: Boolean = false, color: Int = TXT) =
        TextView(c).apply {
            text = s; textSize = size; setTextColor(color)
            if (bold) setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            layoutParams = lp(c, 6)
        }

    fun dcard(c: Context) = LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(c, 16), dp(c, 14), dp(c, 16), dp(c, 16))
        background = GradientDrawable().apply {
            setColor(CARD); setStroke(dp(c, 1), CARD_STROKE); cornerRadius = dp(c, 20).toFloat()
        }
        layoutParams = lp(c, 12)
    }

    fun dbutton(c: Context, label: String, outline: Boolean = false, danger: Boolean = false, onClick: () -> Unit) =
        Button(c).apply {
            val col = if (danger) RED else NEON
            text = label; isAllCaps = false; textSize = 16f
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            setTextColor(if (outline) col else Color.parseColor("#04281C"))
            background = GradientDrawable().apply {
                cornerRadius = dp(c, 16).toFloat()
                if (outline) { setColor(Color.TRANSPARENT); setStroke(dp(c, 1.5f), col) } else setColor(col)
            }
            stateListAnimator = null
            minHeight = dp(c, 50)
            layoutParams = lp(c, 10)
            setOnClickListener { onClick() }
        }

    fun dedit(c: Context, hint: String) = EditText(c).apply {
        this.hint = hint; setHintTextColor(Color.parseColor("#7FA891"))
        setTextColor(TXT)
        setPadding(dp(c, 16), dp(c, 14), dp(c, 16), dp(c, 14))
        background = GradientDrawable().apply {
            setColor(Color.parseColor("#082B1F")); setStroke(dp(c, 1.5f), CARD_STROKE)
            cornerRadius = dp(c, 14).toFloat()
        }
        layoutParams = lp(c, 10)
    }

    fun icon(c: Context, res: Int, onClick: () -> Unit) = ImageView(c).apply {
        setImageResource(res); setColorFilter(NEON)
        val p = dp(c, 11); setPadding(p, p, p, p)
        layoutParams = LinearLayout.LayoutParams(dp(c, 46), dp(c, 46))
        setOnClickListener { onClick() }
    }
}
