package com.a3s.fastvpn

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class WelcomeActivity : AppCompatActivity() {

    private val backgroundColor = Color.rgb(18, 13, 19)
    private val goldColor = Color.rgb(245, 198, 90)
    private val redColor = Color.rgb(110, 29, 42)
    private val whiteColor = Color.WHITE
    private val mutedColor = Color.rgb(199, 171, 174)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scrollView = ScrollView(this).apply {
            fillViewport = true
            setBackgroundColor(backgroundColor)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(
                dp(24),
                dp(40),
                dp(24),
                dp(32)
            )
        }

        val logo = ImageView(this).apply {
            setImageResource(R.drawable.ic_a3s_shield_logo)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "A3S FAST VPN Logo"
        }

        root.addView(
            logo,
            LinearLayout.LayoutParams(
                dp(150),
                dp(150)
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
        )

        val title = TextView(this).apply {
            text = "A3S FAST VPN"
            textSize = 29f
            gravity = Gravity.CENTER
            setTextColor(goldColor)
            setTypeface(null, Typeface.BOLD)
            letterSpacing = 0.06f
        }

        root.addView(
            title,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(20)
            }
        )

        val subtitle = TextView(this).apply {
            text = "SECURE • PRIVATE • FAST"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(mutedColor)
            letterSpacing = 0.12f
        }

        root.addView(
            subtitle,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(10)
                bottomMargin = dp(35)
            }
        )

        val freeCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(
                dp(20),
                dp(22),
                dp(20),
                dp(22)
            )

            background = roundedBackground(
                redColor,
                dp(18),
                goldColor,
                dp(1)
            )
        }

        val freeTitle = TextView(this).apply {
            text = "FREE VPN"
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(goldColor)
            setTypeface(null, Typeface.BOLD)
        }

        val freeDescription = TextView(this).apply {
            text = "Get started without an account"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(whiteColor)
        }

        freeCard.addView(freeTitle)

        freeCard.addView(
            freeDescription,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(8)
            }
        )

        root.addView(
            freeCard,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(25)
            }
        )

        val freeButton = MaterialButton(this).apply {
            text = "Continue with Free VPN"
            textSize = 15f
            isAllCaps = false
            cornerRadius = dp(14)
            backgroundTintList =
                ColorStateList.valueOf(goldColor)
            setTextColor(backgroundColor)

            setOnClickListener {
                Toast.makeText(
                    this@WelcomeActivity,
                    "Privacy consent setup coming next",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        root.addView(
            freeButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(56)
            )
        )

        val vipButton = MaterialButton(this).apply {
            text = "Login / Restore VIP"
            textSize = 15f
            isAllCaps = false
            cornerRadius = dp(14)
            backgroundTintList =
                ColorStateList.valueOf(backgroundColor)
            strokeColor =
                ColorStateList.valueOf(goldColor)
            strokeWidth = dp(2)
            setTextColor(goldColor)

            setOnClickListener {
                Toast.makeText(
                    this@WelcomeActivity,
                    "VIP login setup coming next",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        root.addView(
            vipButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(56)
            ).apply {
                topMargin = dp(14)
            }
        )

        val privacyText = TextView(this).apply {
            text = "Your privacy matters.\n" +
                "Review and accept our Privacy Policy " +
                "before using VPN services."

            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(mutedColor)
        }

        root.addView(
            privacyText,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(35)
            }
        )

        scrollView.addView(root)
        setContentView(scrollView)
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density)
            .toInt()
    }

    private fun roundedBackground(
        fillColor: Int,
        radius: Int,
        strokeColor: Int,
        strokeWidth: Int
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fillColor)
            cornerRadius = radius.toFloat()
            setStroke(strokeWidth, strokeColor)
        }
    }
}
