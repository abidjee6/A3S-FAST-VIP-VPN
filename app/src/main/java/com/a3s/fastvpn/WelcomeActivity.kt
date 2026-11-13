package com.a3s.fastvpn

import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class WelcomeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
            setBackgroundColor(0xFF100D13.toInt())
        }

        val title = TextView(this).apply {
            text = "A3S FAST VPN"
            textSize = 30f
            gravity = Gravity.CENTER
            setTextColor(0xFFF5C65A.toInt())
            setTypeface(null, 1)
        }

        val subtitle = TextView(this).apply {
            text = "Secure • Private • Fast"
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, 16, 0, 48)
        }

        val freeButton = Button(this).apply {
            text = "Continue with Free VPN"
            setOnClickListener {
                Toast.makeText(
                    this@WelcomeActivity,
                    "Free VPN setup coming next",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        val vipButton = Button(this).apply {
            text = "Login / Restore VIP"
            setOnClickListener {
                Toast.makeText(
                    this@WelcomeActivity,
                    "VIP login setup coming next",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        root.addView(title)
        root.addView(subtitle)

        root.addView(
            freeButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        root.addView(
            vipButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 20
            }
        )

        setContentView(root)
    }
}
