package com.a3s.fastvpn

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class LoginActivity : AppCompatActivity() {

    private val httpClient = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val etUser = findViewById<EditText>(R.id.etUsername)
        val etPass = findViewById<EditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val pbLoading = findViewById<ProgressBar>(R.id.pbLoginLoading)

        btnLogin.setOnClickListener {
            val user = etUser.text.toString().trim()
            val pass = etPass.text.toString().trim()

            if (user.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, "Please enter Username and Password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnLogin.isEnabled = false
            pbLoading.visibility = View.VISIBLE

            val payload = JSONObject().apply {
                put("username", user)
                put("password", pass)
            }.toString()

            val body = payload.toRequestBody("application/json".toMediaTypeOrNull())
            val request = Request.Builder()
                .url("http://2.31.89.14:3000/api/auth/login")
                .post(body)
                .build()

            httpClient.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    runOnUiThread {
                        btnLogin.isEnabled = true
                        pbLoading.visibility = View.GONE
                        Toast.makeText(this@LoginActivity, "Connection Error to Server", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    val res = response.body?.string() ?: ""
                    runOnUiThread {
                        btnLogin.isEnabled = true
                        pbLoading.visibility = View.GONE
                        try {
                            val json = JSONObject(res)
                            if (json.optBoolean("success", false) || json.optString("status") == "success") {
                                val config = json.optString("wg_config", "")
                                val prefs = getSharedPreferences("a3s_vpn_prefs", Context.MODE_PRIVATE)
                                prefs.edit().apply {
                                    putBoolean("is_logged_in", true)
                                    putString("username", user)
                                    putString("wg_config", config)
                                    apply()
                                }
                                Toast.makeText(this@LoginActivity, "Login Successful", Toast.LENGTH_SHORT).show()
                                startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                                finish()
                            } else {
                                Toast.makeText(this@LoginActivity, json.optString("message", "Invalid Login"), Toast.LENGTH_LONG).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(this@LoginActivity, "Response parsing error", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            })
        }
    }
}
