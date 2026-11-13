package com.a3s.fastvpn

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class WelcomeActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_USER_MODE = "a3s_user_mode"
        const val MODE_FREE = "free"
        const val MODE_VIP = "vip"

        private const val PREFS_NAME = "a3s_privacy_prefs"
        private const val POLICY_VERSION = "draft-1"
    }

    private val backgroundColor = Color.rgb(18, 13, 19)
    private val goldColor = Color.rgb(245, 198, 90)
    private val redColor = Color.rgb(110, 29, 42)
    private val whiteColor = Color.WHITE
    private val mutedColor = Color.rgb(199, 171, 174)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scrollView = ScrollView(this).apply {
            isFillViewport = true
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
                openSelectedMode(MODE_FREE)
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
                openSelectedMode(MODE_VIP)
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

    private fun openSelectedMode(mode: String) {

        val prefs = getSharedPreferences(
            PREFS_NAME,
            MODE_PRIVATE
        )

        val consentAccepted = prefs.getBoolean(
            "consent_accepted",
            false
        )

        val savedVersion = prefs.getString(
            "policy_version",
            null
        )

        if (!consentAccepted || savedVersion != POLICY_VERSION) {

            val intent = Intent(
                this,
                PrivacyActivity::class.java
            )

            intent.putExtra(EXTRA_USER_MODE, mode)
            startActivity(intent)
            return
        }

        // Consent exists, but navigation to VPN/Login
        // will be enabled only after the full consent
        // and destination flow is implemented.

        val message = if (mode == MODE_FREE) {
            "Free VPN setup is coming next."
        } else {
            "VIP login setup is coming next."
        }

        Toast.makeText(
            this,
            message,
            Toast.LENGTH_SHORT
        ).show()
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
