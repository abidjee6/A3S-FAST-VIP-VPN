package com.a3s.fastvpn

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.TrafficStats
import android.net.VpnService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.WebView
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import okhttp3.*
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private class A3STunnel(private val tunnelName: String) : Tunnel {
        override fun getName(): String = tunnelName
        override fun onStateChange(newState: Tunnel.State) {}
    }

    private lateinit var btnConnect: MaterialButton
    private lateinit var viewPulseRing: View
    private lateinit var pbConnectingRing: ProgressBar
    private lateinit var tvStatus: TextView
    private lateinit var tvSessionTimer: TextView
    private lateinit var tvPublicIp: TextView
    private lateinit var tvPing: TextView
    private lateinit var tvDataUsage: TextView
    private lateinit var tvCountryName: TextView
    private lateinit var tvCountryFlagEmoji: TextView
    private lateinit var adContainer: FrameLayout
    private lateinit var wvBannerAd: WebView

    private var isConnected = false
    private var isConnecting = false
    private val httpClient = OkHttpClient()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var wgBackend: GoBackend? = null
    private val activeTunnel = A3STunnel("A3SFastVPN")
    private val VPN_REQUEST_CODE = 1001

    private var retryCount = 0
    private val MAX_RETRIES = 3
    private var startRxBytes = 0L
    private var startTxBytes = 0L

    private var secondsElapsed = 0L
    private val timerRunnable = object : Runnable {
        override fun run() {
            if (isConnected) {
                secondsElapsed++
                val h = secondsElapsed / 3600
                val m = (secondsElapsed % 3600) / 60
                val s = secondsElapsed % 60
                tvSessionTimer.text = String.format("%02d:%02d:%02d", h, m, s)
                mainHandler.postDelayed(this, 1000)
            }
        }
    }

    // منتخب شدہ سرور (پہلے سے جرمنی VIP)
    private var selectedServer = ServerModel(
        id = "de_vip",
        countryName = "Germany",
        countryCode = "DE",
        flagEmoji = "🇩🇪",
        ip = "2.31.89.14",
        port = 51820,
        isVip = true,
        pingMs = 45
    )

    private val serverList = mutableListOf<ServerModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnConnect = findViewById(R.id.btnConnect)
        viewPulseRing = findViewById(R.id.viewPulseRing)
        pbConnectingRing = findViewById(R.id.pbConnectingRing)
        tvStatus = findViewById(R.id.tvConnectionStatus)
        tvSessionTimer = findViewById(R.id.tvSessionTimer)
        tvPublicIp = findViewById(R.id.tvPublicIp)
        tvPing = findViewById(R.id.tvPing)
        tvDataUsage = findViewById(R.id.tvDataUsage)
        tvCountryName = findViewById(R.id.tvCountryName)
        tvCountryFlagEmoji = findViewById(R.id.tvCountryFlagEmoji)
        adContainer = findViewById(R.id.adContainer)
        wvBannerAd = findViewById(R.id.wvBannerAd)

        initBackend()
        startPulseEffect()
        updateSelectedServerUi()

        // مونیٹائزیشن ایڈ کو جوڑنا
        AdManager.attachBannerAd(this, adContainer, wvBannerAd)

        // پروفائل پاپ اپ
        findViewById<ImageView>(R.id.ivProfile)?.setOnClickListener { showProfilePopup() }

        // سرور لسٹ کا باٹم کارڈ
        findViewById<View>(R.id.cardServerSelect)?.setOnClickListener { openServerListBottomSheet() }

        // باٹم نیویگیشن
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.menu_home -> true
                R.id.menu_speed_test -> {
                    startActivity(Intent(this, SpeedTestActivity::class.java))
                    true
                }
                R.id.menu_dns_leak -> {
                    openTool("DNS_LEAK")
                    true
                }
                R.id.menu_clean_cache -> {
                    clearVpnCache()
                    true
                }
                else -> false
            }
        }

        // کنیکٹ بٹن
        btnConnect.setOnClickListener {
            if (isConnecting) return@setOnClickListener
            if (!isConnected) {
                retryCount = 0
                askPermissionAndConnect()
            } else {
                disconnectVpnTunnel()
            }
        }

        fetchAvailableServersFromServer()
        startDataUsageTracker()
    }

    private fun updateSelectedServerUi() {
        tvCountryName.text = if (selectedServer.isVip) "${selectedServer.countryName} (VIP)" else selectedServer.countryName
        tvCountryFlagEmoji.text = selectedServer.flagEmoji
    }

    private fun askPermissionAndConnect() {
        val vpnIntent = VpnService.prepare(this)
        if (vpnIntent != null) {
            startActivityForResult(vpnIntent, VPN_REQUEST_CODE)
        } else {
            startTunnelConnectingFlow()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VPN_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                startTunnelConnectingFlow()
            } else {
                Toast.makeText(this, "VPN connection cancelled", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startTunnelConnectingFlow() {
        initBackend()
        val prefs = getSharedPreferences("a3s_vpn_prefs", Context.MODE_PRIVATE)
        var configStr = prefs.getString("wg_config", "") ?: ""

        if (configStr.isEmpty()) {
            Toast.makeText(this, "Configuration missing, please re-login", Toast.LENGTH_LONG).show()
            return
        }

        if (!configStr.contains("DNS =")) {
            configStr = configStr.replace("[Interface]", "[Interface]\nDNS = 94.140.14.14, 94.140.15.15\n")
        }

        isConnecting = true
        btnConnect.isEnabled = false
        btnConnect.text = ""
        pbConnectingRing.visibility = View.VISIBLE

        tvStatus.text = if (retryCount > 0) "RECONNECTING (${retryCount}/$MAX_RETRIES)..." else "CONNECTING..."
        tvStatus.setTextColor(Color.parseColor("#FFC107"))

        mainHandler.postDelayed({
            try {
                val config = Config.parse(ByteArrayInputStream(configStr.toByteArray(StandardCharsets.UTF_8)))
                if (wgBackend == null) initBackend()
                wgBackend?.setState(activeTunnel, Tunnel.State.UP, config)

                startRxBytes = TrafficStats.getTotalRxBytes()
                startTxBytes = TrafficStats.getTotalTxBytes()

                setConnectedUi()
                retryCount = 0
                Toast.makeText(this, "Connected Successfully", Toast.LENGTH_SHORT).show()

                tvPublicIp.text = "Resolving..."
                mainHandler.postDelayed({
                    fetchIpAndLocation()
                    measurePing()
                }, 2000)

            } catch (e: Exception) {
                handleConnectionFailure()
            }
        }, 2500)
    }

    private fun handleConnectionFailure() {
        if (retryCount < MAX_RETRIES) {
            retryCount++
            tvStatus.text = "FAILED. RETRYING ($retryCount/$MAX_RETRIES)..."
            tvStatus.setTextColor(Color.parseColor("#FF9800"))

            var waitSec = 3
            val retryRunnable = object : Runnable {
                override fun run() {
                    if (waitSec > 0) {
                        tvStatus.text = "RETRYING IN ${waitSec}s ($retryCount/$MAX_RETRIES)"
                        waitSec--
                        mainHandler.postDelayed(this, 1000)
                    } else {
                        startTunnelConnectingFlow()
                    }
                }
            }
            mainHandler.post(retryRunnable)
        } else {
            isConnecting = false
            setDisconnectedUi()
            tvStatus.text = "UNABLE TO CONNECT. TAP TO RETRY"
            tvStatus.setTextColor(Color.parseColor("#D32F2F"))
            Toast.makeText(this, "Could not connect after 3 attempts.", Toast.LENGTH_LONG).show()
        }
    }

    private fun setConnectedUi() {
        isConnected = true
        isConnecting = false
        pbConnectingRing.visibility = View.GONE
        btnConnect.isEnabled = true
        btnConnect.text = "DISCONNECT"
        btnConnect.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#00E676"))
        btnConnect.strokeColor = ColorStateList.valueOf(Color.parseColor("#FFFFFF"))
        tvStatus.text = "VPN CONNECTED"
        tvStatus.setTextColor(Color.parseColor("#00E676"))
        viewPulseRing.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#00E676"))

        secondsElapsed = 0
        mainHandler.post(timerRunnable)
    }

    private fun setDisconnectedUi() {
        isConnected = false
        isConnecting = false
        pbConnectingRing.visibility = View.GONE
        btnConnect.isEnabled = true
        btnConnect.text = "CONNECT"
        btnConnect.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#D32F2F"))
        btnConnect.strokeColor = ColorStateList.valueOf(Color.parseColor("#FFC107"))
        tvStatus.text = "DISCONNECTED"
        tvStatus.setTextColor(Color.parseColor("#D32F2F"))
        viewPulseRing.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FFC107"))

        mainHandler.removeCallbacks(timerRunnable)
        tvSessionTimer.text = "00:00:00"
    }

    private fun disconnectVpnTunnel() {
        initBackend()
        try {
            wgBackend?.setState(activeTunnel, Tunnel.State.DOWN, null)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        setDisconnectedUi()
        tvPublicIp.text = "Reverting..."
        mainHandler.postDelayed({
            fetchIpAndLocation()
            measurePing()
        }, 1500)
    }

    private fun fetchAvailableServersFromServer() {
        val request = Request.Builder().url("http://2.31.89.14:3000/api/servers/available").build()
        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                setupFallbackServers()
            }
            override fun onResponse(call: Call, response: Response) {
                val res = response.body?.string() ?: ""
                try {
                    val json = JSONObject(res)
                    val array = json.optJSONArray("servers")
                    serverList.clear()
                    if (array != null) {
                        for (i in 0 until array.length()) {
                            val obj = array.getJSONObject(i)
                            serverList.add(
                                ServerModel(
                                    id = obj.optString("id"),
                                    countryName = obj.optString("country_name"),
                                    countryCode = obj.optString("country_code"),
                                    flagEmoji = obj.optString("flag_emoji", "🌐"),
                                    ip = obj.optString("ip"),
                                    port = obj.optInt("port", 51820),
                                    isVip = obj.optBoolean("is_vip", false),
                                    wgConfigTemplate = obj.optString("wg_config", "")
                                )
                            )
                        }
                    }
                    measureServerPings()
                } catch (e: Exception) {
                    setupFallbackServers()
                }
            }
        })
    }

    private fun setupFallbackServers() {
        serverList.clear()
        serverList.add(ServerModel("de_vip", "Germany", "DE", "🇩🇪", "2.31.89.14", 51820, true, 45))
        serverList.add(ServerModel("nl_free", "Netherlands", "NL", "🇳🇱", "185.220.101.5", 51820, false, 65))
        serverList.add(ServerModel("us_free", "United States", "US", "🇺🇸", "142.93.12.1", 51820, false, 145))
    }

    private fun measureServerPings() {
        for (server in serverList) {
            val start = System.currentTimeMillis()
            val pingReq = Request.Builder().url("http://${server.ip}:3000/api/speedtest/ping").build()
            httpClient.newCall(pingReq).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    server.pingMs = 999
                }
                override fun onResponse(call: Call, response: Response) {
                    server.pingMs = (System.currentTimeMillis() - start).toInt()
                }
            })
        }
    }

    private fun openServerListBottomSheet() {
        val dialog = BottomSheetDialog(this)
        val sheetView = layoutInflater.inflate(R.layout.bottom_sheet_servers, null)
        val rv = sheetView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvServers)
        rv.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)

        val adapter = ServerAdapter(serverList) { chosen ->
            if (isConnected) {
                Toast.makeText(this, "Please disconnect before switching location", Toast.LENGTH_SHORT).show()
            } else {
                selectedServer = chosen
                updateSelectedServerUi()
                if (chosen.wgConfigTemplate.isNotEmpty()) {
                    val prefs = getSharedPreferences("a3s_vpn_prefs", Context.MODE_PRIVATE)
                    prefs.edit().putString("wg_config", chosen.wgConfigTemplate).apply()
                }
                dialog.dismiss()
            }
        }
        rv.adapter = adapter
        dialog.setContentView(sheetView)
        dialog.show()
    }

    private fun fetchIpAndLocation() {
        val request = Request.Builder().url("http://ip-api.com/json/?fields=status,country,query").build()
        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) {
                val res = response.body?.string() ?: ""
                try {
                    val json = JSONObject(res)
                    if (json.optString("status") == "success") {
                        val ip = json.optString("query", "Unknown")
                        runOnUiThread { tvPublicIp.text = ip }
                    }
                } catch (e: Exception) {}
            }
        })
    }

    private fun measurePing() {
        val startTime = System.currentTimeMillis()
        val request = Request.Builder().url("http://${selectedServer.ip}:3000/api/speedtest/ping").build()
        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { tvPing.text = "Timeout" }
            }
            override fun onResponse(call: Call, response: Response) {
                val latency = System.currentTimeMillis() - startTime
                runOnUiThread { tvPing.text = "$latency ms" }
            }
        })
    }

    private fun startDataUsageTracker() {
        startRxBytes = TrafficStats.getTotalRxBytes()
        startTxBytes = TrafficStats.getTotalTxBytes()

        val runnable = object : Runnable {
            override fun run() {
                if (isConnected) {
                    val currentRx = (TrafficStats.getTotalRxBytes() - startRxBytes).coerceAtLeast(0)
                    val currentTx = (TrafficStats.getTotalTxBytes() - startTxBytes).coerceAtLeast(0)
                    val totalMb = (currentRx + currentTx) / (1024.0 * 1024.0)
                    tvDataUsage.text = String.format("%.2f MB", totalMb)
                } else {
                    tvDataUsage.text = "0.00 MB"
                }
                mainHandler.postDelayed(this, 1500)
            }
        }
        mainHandler.post(runnable)
    }

    private fun showProfilePopup() {
        val prefs = getSharedPreferences("a3s_vpn_prefs", Context.MODE_PRIVATE)
        val username = prefs.getString("username", "A3S User") ?: "A3S User"
        val device = android.os.Build.MODEL

        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_profile_popup, null)

        view.findViewById<TextView>(R.id.tvPopupUsername)?.text = "Username: $username"
        view.findViewById<TextView>(R.id.tvPopupDevice)?.text = "Device: $device"

        view.findViewById<Button>(R.id.btnPopupLogout)?.setOnClickListener {
            dialog.dismiss()
            disconnectVpnTunnel()
            prefs.edit().clear().apply()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        dialog.setContentView(view)
        dialog.show()
    }

    private fun startPulseEffect() {
        val scaleX = android.animation.ObjectAnimator.ofFloat(viewPulseRing, "scaleX", 1.0f, 1.08f, 1.0f)
        val scaleY = android.animation.ObjectAnimator.ofFloat(viewPulseRing, "scaleY", 1.0f, 1.08f, 1.0f)
        scaleX.repeatCount = android.animation.ValueAnimator.INFINITE
        scaleY.repeatCount = android.animation.ValueAnimator.INFINITE
        scaleX.duration = 2000
        scaleY.duration = 2000
        scaleX.start()
        scaleY.start()
    }

    private fun initBackend() {
        if (wgBackend == null) {
            try {
                wgBackend = GoBackend(applicationContext)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun openTool(type: String) {
        val intent = Intent(this, ToolsActivity::class.java)
        intent.putExtra("TOOL_TYPE", type)
        startActivity(intent)
    }

    private fun clearVpnCache() {
        try {
            cacheDir.deleteRecursively()
            Toast.makeText(this, "Network Cache Cleared", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Done", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        wvBannerAd.destroy()
    }
}
