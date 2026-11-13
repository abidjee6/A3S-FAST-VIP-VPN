package com.a3s.fastvpn

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.TrafficStats
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
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.Locale

class MainActivity : AppCompatActivity() {

    companion object {
        private const val PRIVACY_PREFS = "a3s_privacy_prefs"
        private const val POLICY_VERSION = "draft-1"
    }

    private class A3STunnel(private val tunnelName: String) : Tunnel {
        override fun getName(): String = tunnelName
        override fun onStateChange(newState: Tunnel.State) {}
    }

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
    private val BASE_URL = "https://2.31.89.14:8443"

    private val serverList = ArrayList<ServerModel>()
    private var selectedServer: ServerModel? = null

    private var wgBackend: GoBackend? = null
    private val activeTunnel = A3STunnel("A3SFastVPN")
    private val VPN_REQUEST_CODE = 1001

    private var isConnected = false
    private var isConnecting = false
    private var secondsElapsed = 0
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var timerRunnable: Runnable

    private var startRxBytes = 0L
    private var startTxBytes = 0L

    private fun hasValidPrivacyConsent(): Boolean {
        val prefs = getSharedPreferences(
            PRIVACY_PREFS,
            Context.MODE_PRIVATE
        )

        return prefs.getBoolean("consent_accepted", false) &&
            prefs.getString("policy_version", null) == POLICY_VERSION
    }

    private fun enforcePrivacyConsent(): Boolean {
        if (hasValidPrivacyConsent()) return true

        val intent = Intent(this, WelcomeActivity::class.java)
        intent.flags =
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
        return false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!enforcePrivacyConsent()) return

        setContentView(R.layout.activity_main)

        initViews()
        initWireGuardBackend()
        setupTimer()
        setupBottomNav()
        setupProfilePopup()

        // Banner advertisement
        val adContainer = findViewById<FrameLayout?>(R.id.adContainer)
        val wvBannerAd = findViewById<WebView?>(R.id.wvBannerAd)
        if (adContainer != null && wvBannerAd != null) {
            AdManager.attachBannerAd(this, adContainer, wvBannerAd)
        }

        // Load servers from backend
        loadServersFromDatabase()

        cardSelectServer.setOnClickListener {
            showServerSelectionSheet()
        }

        btnConnect.setOnClickListener {
            if (!hasValidPrivacyConsent()) {
                enforcePrivacyConsent()
                return@setOnClickListener
            }

            if (isConnecting) return@setOnClickListener

            if (isConnected) {
                disconnectVpnTunnel()
            } else {
                prepareAndConnectWireGuard()
            }
        }

        startDataUsageTracker()
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

    private fun initWireGuardBackend() {
        if (wgBackend == null) {
            try {
                wgBackend = GoBackend(applicationContext)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onResume() {
        super.onResume()

        if (!enforcePrivacyConsent()) return
        if (!::tvStatus.isInitialized) return

        checkLiveTunnelState()

        if (!isConnected) {
            fetchPublicIp()
            measurePing()
        }
    }

    private fun checkLiveTunnelState() {
        initWireGuardBackend()

        try {
            val state = wgBackend?.getState(activeTunnel)

            if (state == Tunnel.State.UP) {
                setConnectedUi()
                fetchPublicIp()
            } else {
                setDisconnectedUi()
            }
        } catch (e: Exception) {
            setDisconnectedUi()
        }
    }

    private fun prepareAndConnectWireGuard() {
        if (!enforcePrivacyConsent()) return

        val vpnIntent = VpnService.prepare(this)

        if (vpnIntent != null) {
            startActivityForResult(vpnIntent, VPN_REQUEST_CODE)
        } else {
            startTunnelConnectingFlow()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == VPN_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                if (enforcePrivacyConsent()) {
                    startTunnelConnectingFlow()
                }
            } else {
                Toast.makeText(
                    this,
                    "وی پی این اجازت ضروری ہے",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun startTunnelConnectingFlow() {
        if (!enforcePrivacyConsent()) return

        initWireGuardBackend()

        val prefs = getSharedPreferences(
            "a3s_vpn_prefs",
            Context.MODE_PRIVATE
        )

        var configStr = prefs.getString("wg_config", "") ?: ""

        if (configStr.isEmpty()) {
            Toast.makeText(
                this,
                "کنفیگریشن موجود نہیں، دوبارہ لاگ ان کریں",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        // Add DNS if missing
        if (!configStr.contains("DNS =") &&
            !configStr.contains("DNS=")
        ) {
            configStr = configStr.replace(
                "[Interface]",
                "[Interface]\nDNS = 1.1.1.1, 8.8.8.8\n"
            )
        }

        // Existing endpoint replacement logic
        selectedServer?.let { server ->
            if (server.endpoint.isNotEmpty() &&
                configStr.contains("Endpoint =")
            ) {
                configStr = configStr.replace(
                    Regex("Endpoint\\s*=.*"),
                    "Endpoint = ${server.endpoint}"
                )
            }
        }

        isConnecting = true
        btnConnect.isEnabled = false
        tvStatus.text = "CONNECTING..."
        tvStatus.setTextColor(Color.parseColor("#FFC107"))

        mainHandler.postDelayed({
            if (!hasValidPrivacyConsent()) {
                isConnecting = false
                enforcePrivacyConsent()
                return@postDelayed
            }

            try {
                val config = Config.parse(
                    ByteArrayInputStream(
                        configStr.toByteArray(StandardCharsets.UTF_8)
                    )
                )

                if (wgBackend == null) {
                    initWireGuardBackend()
                }

                wgBackend?.setState(
                    activeTunnel,
                    Tunnel.State.UP,
                    config
                )

                startRxBytes = TrafficStats.getTotalRxBytes()
                startTxBytes = TrafficStats.getTotalTxBytes()

                setConnectedUi()

                Toast.makeText(
                    this,
                    "A3S FAST VIP VPN Connected!",
                    Toast.LENGTH_SHORT
                ).show()

                tvPublicIp.text = "Resolving..."

                mainHandler.postDelayed({
                    fetchPublicIp()
                    measurePing()
                }, 2000)

            } catch (e: Exception) {
                isConnecting = false
                setDisconnectedUi()

                Toast.makeText(
                    this,
                    "ٹنل شروع نہ ہو سکی: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }, 1500)
    }

    private fun disconnectVpnTunnel() {
        initWireGuardBackend()

        try {
            wgBackend?.setState(
                activeTunnel,
                Tunnel.State.DOWN,
                null
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }

        setDisconnectedUi()
        tvPublicIp.text = "Reverting..."

        mainHandler.postDelayed({
            fetchPublicIp()
            measurePing()
        }, 1500)

        Toast.makeText(
            this,
            "وی پی این منقطع کر دیا گیا",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun setConnectedUi() {
        isConnected = true
        isConnecting = false
        btnConnect.isEnabled = true
        btnConnect.text = "DISCONNECT"
        btnConnect.setBackgroundColor(Color.parseColor("#1B5E20"))

        tvStatus.text = "VPN CONNECTED"
        tvStatus.setTextColor(Color.parseColor("#00E676"))

        secondsElapsed = 0
        mainHandler.removeCallbacks(timerRunnable)
        mainHandler.post(timerRunnable)
    }

    private fun setDisconnectedUi() {
        isConnected = false
        isConnecting = false
        btnConnect.isEnabled = true
        btnConnect.text = "CONNECT"
        btnConnect.setBackgroundColor(Color.parseColor("#FFC107"))

        tvStatus.text = "NOT CONNECTED"
        tvStatus.setTextColor(Color.parseColor("#94A3B8"))
        tvTimer.text = "00:00:00"
        tvPing.text = "-- ms"

        mainHandler.removeCallbacks(timerRunnable)
    }

    private fun setupTimer() {
        timerRunnable = object : Runnable {
            override fun run() {
                if (isConnected) {
                    secondsElapsed++

                    val hours = secondsElapsed / 3600
                    val minutes = (secondsElapsed % 3600) / 60
                    val secs = secondsElapsed % 60

                    tvTimer.text = String.format(
                        Locale.getDefault(),
                        "%02d:%02d:%02d",
                        hours,
                        minutes,
                        secs
                    )

                    mainHandler.postDelayed(this, 1000)
                }
            }
        }
    }

    private fun startDataUsageTracker() {
        val runnable = object : Runnable {
            override fun run() {
                if (isConnected) {
                    val currentRx = (
                        TrafficStats.getTotalRxBytes() - startRxBytes
                    ).coerceAtLeast(0)

                    val currentTx = (
                        TrafficStats.getTotalTxBytes() - startTxBytes
                    ).coerceAtLeast(0)

                    val totalMb =
                        (currentRx + currentTx) / (1024.0 * 1024.0)

                    tvDataUsage.text = String.format(
                        Locale.getDefault(),
                        "%.2f MB",
                        totalMb
                    )
                } else {
                    tvDataUsage.text = "0.00 MB"
                }

                mainHandler.postDelayed(this, 1500)
            }
        }

        mainHandler.post(runnable)
    }

    private fun fetchPublicIp() {
        val request = Request.Builder()
            .url("http://ip-api.com/json/?fields=status,country,query")
            .build()

        httpClient.newCall(request).enqueue(
            object : Callback {
                override fun onFailure(
                    call: Call,
                    e: IOException
                ) {}

                override fun onResponse(
                    call: Call,
                    response: Response
                ) {
                    response.use {
                        val res = it.body?.string() ?: ""

                        try {
                            val json = JSONObject(res)

                            if (
                                json.optString("status") == "success"
                            ) {
                                val ip = json.optString(
                                    "query",
                                    "Unknown"
                                )

                                runOnUiThread {
                                    tvPublicIp.text = ip
                                }
                            }
                        } catch (e: Exception) {
                            // Ignore malformed response
                        }
                    }
                }
            }
        )
    }

    private fun measurePing() {
        val startTime = System.currentTimeMillis()

        val request = Request.Builder()
            .url("$BASE_URL/api/speedtest/ping")
            .build()

        httpClient.newCall(request).enqueue(
            object : Callback {
                override fun onFailure(
                    call: Call,
                    e: IOException
                ) {
                    runOnUiThread {
                        tvPing.text = "-- ms"
                    }
                }

                override fun onResponse(
                    call: Call,
                    response: Response
                ) {
                    response.use {
                        val latency =
                            System.currentTimeMillis() - startTime

                        runOnUiThread {
                            tvPing.text = "$latency ms"
                        }
                    }
                }
            }
        )
    }

    private fun loadServersFromDatabase() {
        val request = Request.Builder()
            .url("$BASE_URL/api/servers")
            .get()
            .addHeader("Accept", "application/json")
            .build()

        httpClient.newCall(request).enqueue(
            object : Callback {
                override fun onFailure(
                    call: Call,
                    e: IOException
                ) {
                    runOnUiThread {
                        setupDefaultServers()
                    }
                }

                override fun onResponse(
                    call: Call,
                    response: Response
                ) {
                    val responseData = response.use {
                        it.body?.string() ?: ""
                    }

                    try {
                        val list = ArrayList<ServerModel>()

                        if (responseData.trim().startsWith("[")) {
                            val arr = JSONArray(responseData)

                            for (i in 0 until arr.length()) {
                                list.add(
                                    ServerModel.fromJson(
                                        arr.getJSONObject(i)
                                    )
                                )
                            }
                        } else if (
                            responseData.trim().startsWith("{")
                        ) {
                            val obj = JSONObject(responseData)

                            val arr =
                                obj.optJSONArray("servers")
                                    ?: obj.optJSONArray("data")

                            if (arr != null) {
                                for (i in 0 until arr.length()) {
                                    list.add(
                                        ServerModel.fromJson(
                                            arr.getJSONObject(i)
                                        )
                                    )
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

                            selectedServer =
                                serverList.firstOrNull()

                            updateSelectedServerUi()
                        }

                    } catch (e: Exception) {
                        runOnUiThread {
                            setupDefaultServers()
                        }
                    }
                }
            }
        )
    }

    private fun setupDefaultServers() {
        serverList.clear()

        serverList.add(
            ServerModel(
                "1",
                "Germany (VIP)",
                "Germany",
                "🇩🇪",
                "2.31.89.14:51820",
                "",
                "10.0.0.2/32",
                "1.1.1.1",
                true,
                42
            )
        )

        serverList.add(
            ServerModel(
                "2",
                "Netherlands (Free)",
                "Netherlands",
                "🇳🇱",
                "2.31.89.14:51821",
                "",
                "10.0.0.3/32",
                "1.1.1.1",
                false,
                65
            )
        )

        serverList.add(
            ServerModel(
                "3",
                "USA - New York",
                "United States",
                "🇺🇸",
                "2.31.89.14:51822",
                "",
                "10.0.0.4/32",
                "1.1.1.1",
                true,
                110
            )
        )

        selectedServer = serverList.firstOrNull()
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

        val sheetView = LayoutInflater.from(this)
            .inflate(R.layout.bottom_sheet_servers, null)

        dialog.setContentView(sheetView)

        val rvServers =
            sheetView.findViewById<RecyclerView>(R.id.rvServers)

        rvServers.layoutManager = LinearLayoutManager(this)

        val adapter = ServerAdapter(serverList) { clickedServer ->
            if (isConnected) {
                disconnectVpnTunnel()
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

            val popupView = LayoutInflater.from(this)
                .inflate(R.layout.dialog_profile_popup, null)

            dialog.setContentView(popupView)

            val prefs = getSharedPreferences(
                "a3s_vpn_prefs",
                Context.MODE_PRIVATE
            )

            val username =
                prefs.getString("LOGGED_USER", "client") ?: "client"

            popupView.findViewById<TextView>(
                R.id.tvProfileUsername
            ).text = "User: $username"

            popupView.findViewById<TextView>(
                R.id.tvProfileDevice
            ).text = "Device: ${
                Build.MANUFACTURER.replaceFirstChar {
                    it.uppercase()
                }
            } ${Build.MODEL}"

            popupView.findViewById<MaterialButton>(
                R.id.btnLogout
            ).setOnClickListener {
                if (isConnected) {
                    disconnectVpnTunnel()
                }

                prefs.edit().clear().apply()
                dialog.dismiss()

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

            dialog.show()
        }
    }

    private fun setupBottomNav() {
        bottomNav.setOnItemSelectedListener { item ->
            val title = item.title?.toString() ?: ""

            when {
                title.contains(
                    "Home",
                    ignoreCase = true
                ) -> true

                title.contains(
                    "Speed",
                    ignoreCase = true
                ) -> {
                    startActivity(
                        Intent(
                            this,
                            SpeedTestActivity::class.java
                        )
                    )
                    false
                }

                title.contains(
                    "DNS",
                    ignoreCase = true
                ) -> {
                    val intent = Intent(
                        this,
                        ToolsActivity::class.java
                    )

                    intent.putExtra(
                        "TOOL_TYPE",
                        "DNS_LEAK"
                    )

                    startActivity(intent)
                    false
                }

                title.contains(
                    "Cache",
                    ignoreCase = true
                ) -> {
                    try {
                        cacheDir.deleteRecursively()

                        Toast.makeText(
                            this,
                            "کیشے میموری کامیابی سے صاف ہو گئی!",
                            Toast.LENGTH_SHORT
                        ).show()

                    } catch (e: Exception) {
                        Toast.makeText(
                            this,
                            "کیشے پہلے ہی صاف ہے",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    false
                }

                else -> false
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacks(timerRunnable)
    }
}
