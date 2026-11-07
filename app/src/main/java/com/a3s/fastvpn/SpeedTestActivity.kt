package com.a3s.fastvpn

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import okhttp3.*
import java.io.IOException

class SpeedTestActivity : AppCompatActivity() {

    private lateinit var tvLiveSpeed: TextView
    private lateinit var tvResultPing: TextView
    private lateinit var tvResultDownload: TextView
    private lateinit var tvResultUpload: TextView
    private lateinit var btnStartTest: MaterialButton
    private val httpClient = OkHttpClient()
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_speed_test)

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }

        tvLiveSpeed = findViewById(R.id.tvLiveSpeed)
        tvResultPing = findViewById(R.id.tvResultPing)
        tvResultDownload = findViewById(R.id.tvResultDownload)
        tvResultUpload = findViewById(R.id.tvResultUpload)
        btnStartTest = findViewById(R.id.btnStartTest)

        val adContainer = findViewById<FrameLayout>(R.id.adContainer)
        val wvBannerAd = findViewById<WebView>(R.id.wvBannerAd)
        AdManager.attachBannerAd(this, adContainer, wvBannerAd)

        btnStartTest.setOnClickListener {
            runSpeedTestRoutine()
        }
    }

    private fun runSpeedTestRoutine() {
        btnStartTest.isEnabled = false
        btnStartTest.text = "TESTING..."

        // 1. پنگ کی پیمائش
        val startTime = System.currentTimeMillis()
        val request = Request.Builder().url("http://2.31.89.14:3000/api/speedtest/ping").build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    tvResultPing.text = "Timeout"
                    simulateDownloadUpload()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val ping = System.currentTimeMillis() - startTime
                runOnUiThread {
                    tvResultPing.text = "$ping ms"
                    simulateDownloadUpload()
                }
            }
        })
    }

    private fun simulateDownloadUpload() {
        var progress = 0
        val runnable = object : Runnable {
            override fun run() {
                if (progress <= 10) {
                    val currentSpeed = (20..55).random() + ((1..9).random() / 10.0)
                    tvLiveSpeed.text = String.format("%.1f", currentSpeed)
                    progress++
                    mainHandler.postDelayed(this, 200)
                } else {
                    val finalDown = (35..50).random() + ((1..9).random() / 10.0)
                    val finalUp = (15..25).random() + ((1..9).random() / 10.0)
                    tvResultDownload.text = String.format("%.1f Mbps", finalDown)
                    tvResultUpload.text = String.format("%.1f Mbps", finalUp)
                    tvLiveSpeed.text = String.format("%.1f", finalDown)
                    btnStartTest.isEnabled = true
                    btnStartTest.text = "TEST AGAIN"
                }
            }
        }
        mainHandler.post(runnable)
    }
}
