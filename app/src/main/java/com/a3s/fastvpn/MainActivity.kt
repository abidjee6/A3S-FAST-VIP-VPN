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
import android.util.Patterns
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
import java.util.concurrent.TimeUnit

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
    private val ipHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(12, TimeUnit.SECONDS)
        .build()
    private var ipRequestGeneration = 0
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
        if (!enforcePrivacyConsent() || isConnecting || isConnected) return

        val server = selectedServer
        if (server == null || !isUsableServer(server)) {
            Toast.makeText(this, "No valid VPN server available. Refresh the server list.", Toast.LENGTH_LONG).show()
            return
        }

        initWireGuardBackend()
        val backend = wgBackend
        if (backend == null) {
            Toast.makeText(this, "WireGuard backend is unavailable.", Toast.LENGTH_LONG).show()
            return
        }

        val prefs = getSharedPreferences("a3s_vpn_prefs", Context.MODE_PRIVATE)
        var configStr = prefs.getString("wg_config", "") ?: ""
        if (configStr.isBlank()) {
            Toast.makeText(this, "VPN configuration missing. Please log in again.", Toast.LENGTH_LONG).show()
            return
        }

        // The login configuration is the source of the user's private key and address.
        // Only replace the remote peer endpoint and public key for the chosen server.
        // Do not replace the client address with ServerModel's placeholder clientIp.
        try {
            val peerCount = Regex("(?m)^\\s*\\[Peer]\\s*$").findAll(configStr).count()
            if (peerCount != 1) {
                throw IllegalArgumentException("Expected exactly one WireGuard peer")
            }

            if (!Regex("(?m)^\\s*DNS\\s*=").containsMatchIn(configStr)) {
                configStr = configStr.replaceFirst("[Interface]", "[Interface]\nDNS = 1.1.1.1, 8.8.8.8")
            }

            val endpointPattern = Regex("(?m)^\\s*Endpoint\\s*=.*$")
            val publicKeyPattern = Regex("(?m)^\\s*PublicKey\\s*=.*$")
            if (!endpointPattern.containsMatchIn(configStr) ||
                !publicKeyPattern.containsMatchIn(configStr)) {
                throw IllegalArgumentException("VPN peer endpoint or public key missing")
            }

            configStr = endpointPattern.replace(configStr, "Endpoint = ${server.endpoint.trim()}")
            configStr = publicKeyPattern.replace(configStr, "PublicKey = ${server.publicKey.trim()}")
        } catch (e: Exception) {
            Toast.makeText(this, "Invalid VPN configuration: ${e.message}", Toast.LENGTH_LONG).show()
            return
        }

        isConnecting = true
        btnConnect.isEnabled = false
        tvStatus.text = "CONNECTING..."
        tvStatus.setTextColor(Color.parseColor("#FFC107"))

        mainHandler.postDelayed({
            if (!hasValidPrivacyConsent()) {
                setDisconnectedUi()
                enforcePrivacyConsent()
                return@postDelayed
            }

            try {
                val config = Config.parse(
                    ByteArrayInputStream(configStr.toByteArray(StandardCharsets.UTF_8))
                )
                val state = backend.setState(activeTunnel, Tunnel.State.UP, config)
                if (state != Tunnel.State.UP || backend.getState(activeTunnel) != Tunnel.State.UP) {
                    throw IllegalStateException("WireGuard tunnel did not start")
                }

                startRxBytes = TrafficStats.getTotalRxBytes()
                startTxBytes = TrafficStats.getTotalTxBytes()
                setConnectedUi()
                Toast.makeText(this, "WireGuard tunnel started", Toast.LENGTH_SHORT).show()
                tvPublicIp.text = "Resolving..."
                mainHandler.postDelayed({
                    if (isConnected) {
                        fetchPublicIp()
                        measurePing()
                    }
                }, 2000)
            } catch (e: Exception) {
                setDisconnectedUi()
                Toast.makeText(this, "VPN connection failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }, 1500)
    }

    private fun disconnectVpnTunnel(): Boolean {
        initWireGuardBackend()
        val backend = wgBackend
        if (backend == null) {
            Toast.makeText(this, "WireGuard backend unavailable", Toast.LENGTH_LONG).show()
            return false
        }

        return try {
            val state = backend.setState(activeTunnel, Tunnel.State.DOWN, null)
            if (state != Tunnel.State.DOWN || backend.getState(activeTunnel) != Tunnel.State.DOWN) {
                throw IllegalStateException("Tunnel is still active")
            }
            setDisconnectedUi()
            tvPublicIp.text = "Reverting..."
            mainHandler.postDelayed({
                fetchPublicIp()
                measurePing()
            }, 1500)
            Toast.makeText(this, "VPN disconnected", Toast.LENGTH_SHORT).show()
            true
        } catch (e: Exception) {
            Toast.makeText(this, "Disconnect failed: ${e.message}", Toast.LENGTH_LONG).show()
            checkLiveTunnelState()
            false
        }
    }

    private fun setConnectedUi() {
        ipRequestGeneration++ // Invalidate responses from previous connection state.
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
        ipRequestGeneration++ // Invalidate responses from previous connection state.
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
        val generation = ++ipRequestGeneration
        val expectedConnected = isConnected
        tvPublicIp.text = "Resolving..."
        requestPublicIp(generation, expectedConnected, useFallback = false)
    }

    private fun requestPublicIp(
        generation: Int,
        expectedConnected: Boolean,
        useFallback: Boolean
    ) {
        val url = if (useFallback) {
            "https://api64.ipify.org"
        } else {
            "https://api.ipify.org?format=json"
        }

        val request = Request.Builder()
            .url(url)
            .header("Accept", if (useFallback) "text/plain" else "application/json")
            .header("Cache-Control", "no-cache")
            .build()

        ipHttpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                finishIpAttempt(generation, expectedConnected, useFallback, null)
            }

            override fun onResponse(call: Call, response: Response) {
                val ip = try {
                    response.use {
                        if (!it.isSuccessful) null
                        else {
                            val raw = it.body?.string()?.trim().orEmpty()
                            val candidate = if (useFallback) raw
                                else JSONObject(raw).optString("ip", "")
                            candidate.takeIf { value ->
                                value.isNotBlank() && Patterns.IP_ADDRESS.matcher(value).matches()
                            }
                        }
                    }
                } catch (e: Exception) {
                    null
                }
                finishIpAttempt(generation, expectedConnected, useFallback, ip)
            }
        })
    }

    private fun finishIpAttempt(
        generation: Int,
        expectedConnected: Boolean,
        usedFallback: Boolean,
        ip: String?
    ) {
        runOnUiThread {
            if (isFinishing || isDestroyed || generation != ipRequestGeneration ||
                expectedConnected != isConnected) return@runOnUiThread

            when {
                ip != null -> tvPublicIp.text = ip
                !usedFallback -> requestPublicIp(generation, expectedConnected, useFallback = true)
                else -> tvPublicIp.text = "IP Unavailable"
            }
        }
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

    private fun isUsableServer(server: ServerModel): Boolean {
        val endpoint = server.endpoint.trim()
        val key = server.publicKey.trim()
        // Avoid selecting placeholder entries or incomplete server records.
        return endpoint.isNotEmpty() &&
            endpoint.contains(":") &&
            !endpoint.any { it.isWhitespace() } &&
            key.isNotEmpty() &&
            key.length == 44
    }

    private fun loadServersFromDatabase() {
        val request = Request.Builder()
            .url("$BASE_URL/api/servers")
            .get()
            .addHeader("Accept", "application/json")
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { handleServerLoadFailure() }
            }

            override fun onResponse(call: Call, response: Response) {
                val successful = response.isSuccessful
                val responseData = response.use { it.body?.string() ?: "" }
                if (!successful) {
                    runOnUiThread { handleServerLoadFailure() }
                    return
                }

                try {
                    val arr = if (responseData.trim().startsWith("[")) {
                        JSONArray(responseData)
                    } else {
                        val obj = JSONObject(responseData)
                        obj.optJSONArray("servers") ?: obj.optJSONArray("data")
                            ?: throw IllegalArgumentException("Server list missing")
                    }

                    val list = ArrayList<ServerModel>()
                    for (i in 0 until arr.length()) {
                        val server = ServerModel.fromJson(arr.getJSONObject(i))
                        if (isUsableServer(server)) list.add(server)
                    }

                    runOnUiThread {
                        // Never replace a selected server while its tunnel is active.
                        if (isConnected || isConnecting) return@runOnUiThread
                        val previousId = selectedServer?.id
                        serverList.clear()
                        serverList.addAll(list)
                        selectedServer = serverList.firstOrNull { it.id == previousId }
                            ?: serverList.firstOrNull { it.country.equals("Germany", true) }
                            ?: serverList.firstOrNull()
                        updateSelectedServerUi()
                        if (serverList.isEmpty()) {
                            Toast.makeText(this@MainActivity, "No valid VPN servers returned by backend", Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread { handleServerLoadFailure() }
                }
            }
        })
    }

    private fun handleServerLoadFailure() {
        // No fabricated Netherlands/USA servers. Keep a previously loaded list,
        // but do not invent endpoints or public keys when the API is unavailable.
        if (serverList.isEmpty()) {
            selectedServer = null
            updateSelectedServerUi()
        }
        Toast.makeText(this, "Could not load VPN servers. Check internet and reopen the app.", Toast.LENGTH_LONG).show()
    }

    private fun updateSelectedServerUi() {
        val server = selectedServer
        if (server == null) {
            tvSelectedServer.text = "No server available"
            tvPing.text = "-- ms"
        } else {
            tvSelectedServer.text = "${server.flag} ${server.name}"
            tvPing.text = "${server.ping} ms"
        }
    }

    private fun showServerSelectionSheet() {
        if (isConnecting) {
            Toast.makeText(this, "Please wait for connection to finish", Toast.LENGTH_SHORT).show()
            return
        }
        if (serverList.isEmpty()) {
            Toast.makeText(this, "No servers available. Please reopen the app to retry.", Toast.LENGTH_LONG).show()
            return
        }

        val dialog = BottomSheetDialog(this)
        val sheetView = LayoutInflater.from(this)
            .inflate(R.layout.bottom_sheet_servers, null)
        dialog.setContentView(sheetView)
        val rvServers = sheetView.findViewById<RecyclerView>(R.id.rvServers)
        rvServers.layoutManager = LinearLayoutManager(this)
        rvServers.adapter = ServerAdapter(serverList) { clickedServer ->
            if (isConnected && !disconnectVpnTunnel()) {
                // Keep current selection if the old tunnel could not be stopped.
                return@ServerAdapter
            }
            selectedServer = clickedServer
            updateSelectedServerUi()
            dialog.dismiss()
        }
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
                if (isConnected && !disconnectVpnTunnel()) {
                    return@setOnClickListener
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
        ipRequestGeneration++
        ipHttpClient.dispatcher.cancelAll()
        super.onDestroy()
        mainHandler.removeCallbacks(timerRunnable)
    }
}
