package com.a3s.fastvpn

data class ServerModel(
    val id: String,
    val countryName: String,
    val countryCode: String,
    val flagEmoji: String,
    val ip: String,
    val port: Int,
    val isVip: Boolean = false,
    var pingMs: Int = -1,
    val wgConfigTemplate: String = ""
)
