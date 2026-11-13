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
        val sheetView = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_servers, null)
        dialog.setContentView(sheetView)

        val rvServers = sheetView.findViewById<RecyclerView>(R.id.rvServers)
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
            val popupView = LayoutInflater.from(this).inflate(R.layout.dialog_profile_popup, null)
            dialog.setContentView(popupView)

            val prefs = getSharedPreferences("a3s_vpn_prefs", Context.MODE_PRIVATE)
            val username = prefs.getString("LOGGED_USER", "client") ?: "client"

            popupView.findViewById<TextView>(R.id.tvProfileUsername).text = "User: $username"
            popupView.findViewById<TextView>(R.id.tvProfileDevice).text = "Device: ${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"

            popupView.findViewById<MaterialButton>(R.id.btnLogout).setOnClickListener {
                if (isConnected) {
                    disconnectVpnTunnel()
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
