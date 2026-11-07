package com.a3s.fastvpn

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private class A3STunnel(private val tunnelName: String) : Tunnel {
        override fun getName(): String = tunnelName
        override fun onStateChange(newState: Tunnel.State) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("a3s_vpn_prefs", Context.MODE_PRIVATE)
        val isLoggedIn = prefs.getBoolean("is_logged_in", false)
        val isTunnelRunning = checkTunnelRunning()

        // 1. اگر VPN کنیکٹڈ ہے تو براہ راست بغیر اسپلش کے ہوم پیج
        if (isLoggedIn && isTunnelRunning) {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
            return
        }

        // 2. اگر ڈسکنیکٹ ہے تو باقاعدہ اسپلش دکھائیں
        setContentView(R.layout.activity_splash)

        Handler(Looper.getMainLooper()).postDelayed({
            if (isLoggedIn) {
                startActivity(Intent(this, MainActivity::class.java))
            } else {
                startActivity(Intent(this, LoginActivity::class.java))
            }
            finish()
        }, 1500)
    }

    private fun checkTunnelRunning(): Boolean {
        return try {
            val backend = GoBackend(applicationContext)
            backend.getState(A3STunnel("A3SFastVPN")) == Tunnel.State.UP
        } catch (e: Exception) {
            false
        }
    }
}
