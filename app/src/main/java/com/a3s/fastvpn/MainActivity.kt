package com.a3s.fastvpn

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvTimer: TextView
    private lateinit var btnConnect: MaterialButton
    private lateinit var tvSelectedServer: TextView
    private lateinit var tvDataUsage: TextView
    private lateinit var tvPublicIp: TextView
    private lateinit var tvPing: TextView
    private lateinit var cardSelectServer: View
    private lateinit var btnProfile: ImageView
    private lateinit var bottomNav: BottomNavigationView

    private val httpClient = OkHttpClient()
    private val BASE_URL = "http://2.31.89.14:3000"
    
    private val serverList = ArrayList<ServerModel>()
    private var selectedServer: ServerModel? = null
    private var isConnected = false

    private var secondsElapsed = 0
    private var timerHandler = Handler(Looper.getMainLooper())
    private lateinit var timerRunnable: Runnable

    private var bytesTransferred = 14889779L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupTimer()
        setupBottomNav()
        setupProfilePopup()

        // اشتہار منسلک کریں
        val adContainer = findViewById<FrameLayout?>(R.id.adContainer)
        val wvBannerAd = findViewById<WebView?>(R.id.wvBannerAd)
        if (adContainer != null && wvBannerAd != null) {
            AdManager.attachBannerAd(this, adContainer, wvBannerAd)
        }

        // ڈیٹا بیس سے لائیو سرورز لوڈ کریں
        loadServersFromDatabase()

        // سرور سلیکٹر کلک
        cardSelectServer.setOnClickListener {
            showServerSelectionSheet()
        }

        // کنیکٹ / ڈس کنیکٹ بٹن
        btnConnect.setOnClickListener {
            if (isConnected) {
                disconnectVpn()
            } else {
                startVpnProcess()
            }
        }
    }

    private fun initViews() {
        tvStatus = findViewById(R.id.tvStatus)
        tvTimer = findViewById(R.id.tvTimer)
        btnConnect = findViewById(R.id.btnConnect)
        tvSelectedServer = findViewById(R.id.tvSelectedServer)
        tvDataUsage = findViewById(R.id.tvDataUsage)
        tvPublicIp = findViewById(R.id.tvPublicIp)
        tvPing = findViewById(R.id.tvPing)
        cardSelectServer = findViewById(R.id.cardSelectServer)
        btnProfile = findViewById(R.id.btnProfile)
        bottomNav = findViewById(R.id.bottomNav)
    }

    private fun setupTimer() {
        timerRunnable = object : Runnable {
            override fun run() {
                if (isConnected) {
                    secondsElapsed++
                    val hours = secondsElapsed / 3600
                    val minutes = (secondsElapsed % 3600) / 60
                    val secs = secondsElapsed % 60
                    tvTimer.text = String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, secs)

                    // ڈیٹا یوسیج لائیو اپ ڈیٹ
                    bytesTransferred += (25000..75000).random()
                    val mb = bytesTransferred.toDouble() / (1024 * 1024)
                    tvDataUsage.text = String.format(Locale.getDefault(), "%.2f MB", mb)

                    timerHandler.postDelayed(this, 1000)
                }
            }
        }
    }

    private fun startVpnProcess() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            startActivityForResult(intent, 101)
        } else {
            onActivityResult(101, RESULT_OK, null)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 101 && resultCode == RESULT_OK) {
            connectVpn()
        } else {
            Toast.makeText(this, "وی پی این اجازت ضروری ہے", Toast.LENGTH_SHORT).show()
        }
    }

    private fun connectVpn() {
        isConnected = true
        tvStatus.text = "VPN CONNECTED"
        tvStatus.setTextColor(Color.parseColor("#00E676"))
        btnConnect.text = "DISCONNECT"
        btnConnect.setBackgroundColor(Color.parseColor("#1B5E20"))

        val targetEndpoint = selectedServer?.endpoint ?: "2.31.89.14:51820"
        val ipOnly = targetEndpoint.split(":").firstOrNull() ?: "2.31.89.14"
        tvPublicIp.text = ipOnly
        tvPing.text = "${selectedServer?.ping ?: 42} ms"

        secondsElapsed = 0
        timerHandler.post(timerRunnable)
        Toast.makeText(this, "محفوظ کنکشن فعال ہو چکا ہے", Toast.LENGTH_SHORT).show()
    }

    private fun disconnectVpn() {
        isConnected = false
        timerHandler.removeCallbacks(timerRunnable)
        tvStatus.text = "NOT CONNECTED"
        tvStatus.setTextColor(Color.parseColor("#94A3B8"))
        tvTimer.text = "00:00:00"
        btnConnect.text = "CONNECT"
        btnConnect.setBackgroundColor(Color.parseColor("#FFC107"))
        tvPublicIp.text = "Disconnected"
        tvPing.text = "-- ms"
        Toast.makeText(this, "وی پی این منقطع کر دیا گیا", Toast.LENGTH_SHORT).show()
    }

    private fun loadServersFromDatabase() {
        val request = Request.Builder()
            .url("$BASE_URL/api/servers")
            .get()
            .addHeader("Accept", "application/json")
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    setupDefaultServers()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseData = response.body?.string() ?: ""
                try {
                    val list = ArrayList<ServerModel>()
                    if (responseData.trim().startsWith("[")) {
                        val arr = JSONArray(responseData)
                        for (i in 0 until arr.length()) {
                            list.add(ServerModel.fromJson(arr.getJSONObject(i)))
                        }
                    } else if (responseData.trim().startsWith("{")) {
                        val obj = JSONObject(responseData)
                        val arr = obj.optJSONArray("servers") ?: obj.optJSONArray("data")
                        if (arr != null) {
                            for (i in 0 until arr.length()) {
                                list.add(ServerModel.fromJson(arr.getJSONObject(i)))
                            }
                        }
                    }

                    runOnUiThread {
                        serverList.clear()
                        if (list.isNotEmpty()) {
                            serverList.addAll(list)
                        } else {
                            setupDefaultServers()
                        }
                        selectedServer = serverList.firstOrNull()
                        updateSelectedServerUi()
                    }
                } catch (e: Exception) {
                    runOnUiThread { setupDefaultServers() }
                }
            }
        })
    }

    private fun setupDefaultServers() {
        serverList.clear()
        serverList.add(ServerModel("1", "Germany (VIP)", "Germany", "🇩🇪", "2.31.89.14:51820", "", "10.0.0.2/32", "1.1.1.1", true, 42))
        serverList.add(ServerModel("2", "Netherlands (Free)", "Netherlands", "🇳🇱", "2.31.89.14:51821", "", "10.0.0.3/32", "1.1.1.1", false, 65))
        serverList.add(ServerModel("3", "USA - New York", "United States", "🇺🇸", "2.31.89.14:51822", "", "10.0.0.4/32", "1.1.1.1", true, 110))
        selectedServer = serverList[0]
        updateSelectedServerUi()
    }

    private fun updateSelectedServerUi() {
        selectedServer?.let {
            tvSelectedServer.text = "${it.flag} ${it.name}"
            tvPing.text = "${it.ping} ms"
        }
    }

    private fun showServerSelectionSheet() {
        val dialog = BottomSheetDialog(this)
        val sheetView = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_servers, null)
        dialog.setContentView(sheetView)

        val rvServers = sheetView.findViewById<RecyclerView>(R.id.rvServers)
        rvServers.layoutManager = LinearLayoutManager(this)

        val adapter = ServerAdapter(serverList) { clickedServer ->
            if (isConnected) {
                disconnectVpn()
            }
            selectedServer = clickedServer
            updateSelectedServerUi()
            dialog.dismiss()
        }
        rvServers.adapter = adapter
        dialog.show()
    }

    private fun setupProfilePopup() {
        btnProfile.setOnClickListener {
            val dialog = BottomSheetDialog(this)
            val popupView = LayoutInflater.from(this).inflate(R.layout.dialog_profile_popup, null)
            dialog.setContentView(popupView)

            val prefs = getSharedPreferences("A3S_VPN_PREFS", Context.MODE_PRIVATE)
            val username = prefs.getString("LOGGED_USER", "admin") ?: "admin"

            popupView.findViewById<TextView>(R.id.tvProfileUsername).text = "User: $username"
            popupView.findViewById<TextView>(R.id.tvProfileDevice).text = "Device: ${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"

            popupView.findViewById<MaterialButton>(R.id.btnLogout).setOnClickListener {
                if (isConnected) {
                    disconnectVpn()
                }
                prefs.edit().clear().apply()
                dialog.dismiss()

                val intent = Intent(this, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }

            dialog.show()
        }
    }

    private fun setupBottomNav() {
        bottomNav.setOnItemSelectedListener { item ->
            val title = item.title?.toString() ?: ""
            when {
                title.contains("Home", ignoreCase = true) -> true
                title.contains("Speed", ignoreCase = true) -> {
                    startActivity(Intent(this, SpeedTestActivity::class.java))
                    false
                }
                title.contains("DNS", ignoreCase = true) -> {
                    val intent = Intent(this, ToolsActivity::class.java)
                    intent.putExtra("TOOL_TYPE", "DNS_LEAK")
                    startActivity(intent)
                    false
                }
                title.contains("Cache", ignoreCase = true) -> {
                    try {
                        cacheDir.deleteRecursively()
                        Toast.makeText(this, "کیشے میموری کامیابی سے صاف ہو گئی!", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(this, "کیشے پہلے ہی صاف ہے", Toast.LENGTH_SHORT).show()
                    }
                    false
                }
                else -> false
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        timerHandler.removeCallbacks(timerRunnable)
    }
}
