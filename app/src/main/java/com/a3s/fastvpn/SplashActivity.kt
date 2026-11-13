package com.a3s.fastvpn

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private val splashHandler = Handler(Looper.getMainLooper())

    private val splashRunnable = Runnable {
        openNextScreen()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_splash)

        startSplashAnimation()

        splashHandler.postDelayed(
            splashRunnable,
            4000L
        )
    }

    private fun startSplashAnimation() {

        val logo = findViewById<View>(R.id.splashLogo)
        val title = findViewById<View>(R.id.splashTitle)
        val tagline = findViewById<View>(R.id.splashTagline)

        // Initial animation state
        logo.alpha = 0f
        logo.scaleX = 0.65f
        logo.scaleY = 0.65f

        title.alpha = 0f
        title.translationY = 30f

        tagline.alpha = 0f
        tagline.translationY = 20f

        // Shield logo zoom and fade
        logo.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(1200L)
            .setInterpolator(
                AccelerateDecelerateInterpolator()
            )
            .start()

        // Title reveal
        title.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(500L)
            .setDuration(1000L)
            .start()

        // Tagline reveal
        tagline.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(900L)
            .setDuration(1000L)
            .start()
    }

    private fun openNextScreen() {

        if (isFinishing || isDestroyed) return

        val intent = Intent(
            this,
            WelcomeActivity::class.java
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
