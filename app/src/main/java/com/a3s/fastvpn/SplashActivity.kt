package com.a3s.fastvpn

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private val splashHandler = Handler(Looper.getMainLooper())

    private val splashRunnable = Runnable {
        openNextScreen()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Display existing A3S splash layout
        // Original logo and background are preserved
        setContentView(R.layout.activity_splash)

        // Splash duration: 4 seconds
        splashHandler.postDelayed(
            splashRunnable,
            4000L
        )
    }

    private fun openNextScreen() {

        if (isFinishing || isDestroyed) {
            return
        }

        // Temporary navigation:
        // WelcomeActivity will replace LoginActivity
        // after its implementation is completed.
        val intent = Intent(
            this,
            LoginActivity::class.java
        )

        intent.flags =
            Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_CLEAR_TASK

        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        splashHandler.removeCallbacks(splashRunnable)
        super.onDestroy()
    }
}
