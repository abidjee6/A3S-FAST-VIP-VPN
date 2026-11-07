package com.a3s.fastvpn

import android.os.Bundle
import android.widget.FrameLayout
import android.widget.TextView
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import okhttp3.*
import java.io.IOException

class SpeedTestActivity : AppCompatActivity() {

    private val httpClient = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main) // عارضی لے آؤٹ یا مخصوص لے آؤٹ

        val adContainer = findViewById<FrameLayout?>(R.id.adContainer)
        val wvBannerAd = findViewById<WebView?>(R.id.wvBannerAd)
        if (adContainer != null && wvBannerAd != null) {
            AdManager.attachBannerAd(this, adContainer, wvBannerAd)
        }
    }
}
