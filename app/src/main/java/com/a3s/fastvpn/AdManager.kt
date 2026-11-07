package com.a3s.fastvpn

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.FrameLayout
import okhttp3.*
import org.json.JSONObject
import java.io.IOException

object AdManager {

    @SuppressLint("SetJavaScriptEnabled")
    fun attachBannerAd(context: Context, container: FrameLayout, webView: WebView) {
        val settings: WebSettings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.allowFileAccess = true
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        webView.setBackgroundColor(Color.TRANSPARENT)

        val client = OkHttpClient()
        val request = Request.Builder().url("http://2.31.89.14:3000/api/app/ad-config").build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) {
                val res = response.body?.string() ?: ""
                try {
                    val json = JSONObject(res)
                    val showAds = json.optBoolean("show_ads", false)
                    val adScript = json.optString("ad_script", "")

                    if (showAds && adScript.isNotEmpty()) {
                        val html = """
                            <!DOCTYPE html>
                            <html>
                            <head>
                                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                                <style>
                                    body {
                                        margin: 0;
                                        padding: 0;
                                        display: flex;
                                        justify-content: center;
                                        align-items: center;
                                        background: transparent;
                                        overflow: hidden;
                                    }
                                </style>
                            </head>
                            <body>
                                $adScript
                            </body>
                            </html>
                        """.trimIndent()

                        (context as? android.app.Activity)?.runOnUiThread {
                            container.visibility = View.VISIBLE
                            webView.loadDataWithBaseURL("https://checkiqama.net/", html, "text/html", "UTF-8", null)
                        }
                    }
                } catch (e: Exception) {}
            }
        })
    }
}
