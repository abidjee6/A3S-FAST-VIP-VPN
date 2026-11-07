package com.a3s.fastvpn

import android.os.Bundle
import android.widget.FrameLayout
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity

class ToolsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolType = intent.getStringExtra("TOOL_TYPE") ?: "DNS_LEAK"

        val adContainer = findViewById<FrameLayout?>(R.id.adContainer)
        val wvBannerAd = findViewById<WebView?>(R.id.wvBannerAd)
        if (adContainer != null && wvBannerAd != null) {
            AdManager.attachBannerAd(this, adContainer, wvBannerAd)
        }
    }
}
