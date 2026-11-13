package com.a3s.fastvpn

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class PrivacyActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_USER_MODE = "a3s_user_mode"
        const val MODE_FREE = "free"
        const val MODE_VIP = "vip"

        private const val PREFS_NAME = "a3s_privacy_prefs"
        private const val POLICY_VERSION = "draft-1"
    }

    private val backgroundColor = Color.rgb(18, 13, 19)
    private val goldColor = Color.rgb(245, 198, 90)
    private val mutedColor = Color.rgb(205, 184, 185)

    private var selectedMode = MODE_FREE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        selectedMode = intent.getStringExtra(EXTRA_USER_MODE)
            ?.takeIf { it == MODE_FREE || it == MODE_VIP }
            ?: MODE_FREE

        // Default result: no permission granted.
        setResult(Activity.RESULT_CANCELED)

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(backgroundColor)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(24), dp(36), dp(24), dp(32))
        }

        val title = TextView(this).apply {
            text = "Privacy & Consent"
            textSize = 27f
            gravity = Gravity.CENTER
            setTextColor(goldColor)
            setTypeface(null, Typeface.BOLD)
        }

        root.addView(title)

        val introduction = TextView(this).apply {
            text = "Welcome to A3S FAST VPN.\n\n" +
                "Please review the information below before " +
                "using our VPN service."
            textSize = 15f
            setTextColor(Color.WHITE)
            setPadding(0, dp(22), 0, dp(18))
        }

        root.addView(introduction)

        val details = TextView(this).apply {
            text = """
                1. Free VPN Access

                Free users do not need to provide a name,
                email address or password.

                2. Essential Service Information

                To provide VPN connectivity, the service
                may need to process a session identifier,
                selected VPN server, connection status,
                IP address and technical connection data.

                3. Device Diagnostics

                Device model, Android version, app version
                and technical error information may be
                processed for troubleshooting.

                4. Optional Analytics

                Optional analytics and crash reporting
                require separate permission. They are
                not necessary for basic VPN access.

                5. Privacy Choices

                You can decline this consent screen.
                The VPN service will not start unless
                you agree to the required processing.

                6. Full Privacy Policy

                The final published Privacy Policy,
                including retention, providers, user
                rights and contact details, must be
                available before public release.
            """.trimIndent()

            textSize = 14f
            setTextColor(mutedColor)
            setLineSpacing(dp(4).toFloat(), 1f)
        }

        root.addView(details)

        val requiredConsent = CheckBox(this).apply {
            text = "I have reviewed the information above " +
                "and agree to the required processing " +
                "for VPN service operation."
            setTextColor(Color.WHITE)
            buttonTintList = ColorStateList.valueOf(goldColor)
        }

        root.addView(
            requiredConsent,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(20)
            }
        )

        val optionalDiagnostics = CheckBox(this).apply {
            text = "I optionally agree to analytics " +
                "and crash reporting."
            setTextColor(mutedColor)
            buttonTintList = ColorStateList.valueOf(goldColor)
            isChecked = false
        }

        root.addView(optionalDiagnostics)

        val agreeButton = MaterialButton(this).apply {
            text = "Agree & Continue"
            isAllCaps = false
            cornerRadius = dp(14)
            backgroundTintList =
                ColorStateList.valueOf(goldColor)
            setTextColor(backgroundColor)

            setOnClickListener {

                if (!requiredConsent.isChecked) {
                    Toast.makeText(
                        this@PrivacyActivity,
                        "Please review and accept the required terms.",
                        Toast.LENGTH_LONG
                    ).show()
                    return@setOnClickListener
                }

                val saved = getSharedPreferences(
                    PREFS_NAME,
                    MODE_PRIVATE
                ).edit()
                    .putBoolean("consent_accepted", true)
                    .putString("policy_version", POLICY_VERSION)
                    .putLong(
                        "consent_timestamp",
                        System.currentTimeMillis()
                    )
                    .putBoolean(
                        "optional_diagnostics",
                        optionalDiagnostics.isChecked
                    )
                    .commit()

                if (!saved) {
                    Toast.makeText(
                        this@PrivacyActivity,
                        "Could not save consent. Please try again.",
                        Toast.LENGTH_LONG
                    ).show()
                    return@setOnClickListener
                }

                val resultIntent = Intent().apply {
                    putExtra(EXTRA_USER_MODE, selectedMode)
                }

                setResult(
                    Activity.RESULT_OK,
                    resultIntent
                )

                finish()
            }
        }

        root.addView(
            agreeButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(56)
            ).apply {
                topMargin = dp(24)
            }
        )

        val declineButton = MaterialButton(this).apply {
            text = "Decline"
            isAllCaps = false
            cornerRadius = dp(14)
            backgroundTintList =
                ColorStateList.valueOf(backgroundColor)
            strokeColor =
                ColorStateList.valueOf(goldColor)
            strokeWidth = dp(2)
            setTextColor(goldColor)

            setOnClickListener {
                setResult(Activity.RESULT_CANCELED)
                finish()
            }
        }

        root.addView(
            declineButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(56)
            ).apply {
                topMargin = dp(12)
            }
        )

        scroll.addView(root)
        setContentView(scroll)
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
