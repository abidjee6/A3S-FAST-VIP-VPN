package com.a3s.fastvpn

import org.json.JSONObject

data class ServerModel(
    val id: String,
    val name: String,
    val country: String,
    val flag: String,
    val endpoint: String,
    val publicKey: String,
    val clientIp: String,
    val dns: String,
    val isVip: Boolean,
    val ping: Int = 45
) {
    companion object {
        fun fromJson(json: JSONObject): ServerModel {
            return ServerModel(
                id = json.optString("id", json.optString("_id", "1")),
                name = json.optString("name", "Server Node"),
                country = json.optString("country", "Global"),
                flag = json.optString("flag", "🌐"),
                endpoint = json.optString("endpoint", "2.31.89.14:51820"),
                publicKey = json.optString("publicKey", json.optString("public_key", "")),
                clientIp = json.optString("clientIp", json.optString("client_ip", "10.0.0.2/32")),
                dns = json.optString("dns", "1.1.1.1"),
                isVip = json.optBoolean("isVip", json.optBoolean("is_vip", false)),
                ping = json.optInt("ping", 45)
            )
        }
    }
}

data class LoginResponse(
    val success: Boolean,
    val message: String,
    val token: String?,
    val username: String?,
    val isVip: Boolean
) {
    companion object {
        fun parse(responseString: String): LoginResponse {
            val json = JSONObject(responseString)
            // سرور کے مختلف ریسپانس کیز (success / status / user / token) کو ہینڈل کرنے کے لیے
            val isSuccess = json.optBoolean("success", false) || 
                            json.optString("status").equals("success", ignoreCase = true) ||
                            json.has("token")

            val msg = json.optString("message", if (isSuccess) "Login Successful" else "Login Failed")
            val token = json.optString("token", "")
            
            val userObj = json.optJSONObject("user")
            val username = userObj?.optString("username") ?: json.optString("username", "")
            val isVip = userObj?.optBoolean("isVip", false) ?: json.optBoolean("isVip", false)

            return LoginResponse(
                success = isSuccess,
                message = msg,
                token = if (token.isNotEmpty()) token else null,
                username = if (username.isNotEmpty()) username else null,
                isVip = isVip
            )
        }
    }
}
