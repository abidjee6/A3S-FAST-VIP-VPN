package com.a3s.fastvpn

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class LoginActivity : AppCompatActivity() {

    private val httpClient = OkHttpClient()
    private val BASE_URL = "http://2.31.89.14:3000"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val etUsername = findViewById<TextInputEditText>(R.id.etUsername)
        val etPassword = findViewById<TextInputEditText>(R.id.etPassword)
        val btnLogin = findViewById<MaterialButton>(R.id.btnLogin)
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)

        btnLogin.setOnClickListener {
            val username = etUsername.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "براہ کرم یوزر نیم اور پاس ورڈ درج کریں", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnLogin.isEnabled = false
            progressBar.visibility = View.VISIBLE

            val jsonPayload = JSONObject().apply {
                put("username", username)
                put("password", password)
            }

            val requestBody = jsonPayload.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

            // اصلی کلائنٹ روٹ /api/app/login استعمال کریں
            val request = Request.Builder()
                .url("$BASE_URL/api/app/login")
                .post(requestBody)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .build()

            httpClient.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    runOnUiThread {
                        btnLogin.isEnabled = true
                        progressBar.visibility = View.GONE
                        Toast.makeText(this@LoginActivity, "سرور سے رابطہ نہ ہو سکا: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    val rawResponse = response.body?.string() ?: ""

                    runOnUiThread {
                        btnLogin.isEnabled = true
                        progressBar.visibility = View.GONE

                        try {
                            val json = JSONObject(rawResponse)
                            val isSuccess = json.optBoolean("success", false) || response.isSuccessful

                            if (isSuccess && !json.has("error")) {
                                val userObj = json.optJSONObject("user")
                                val token = json.optString("token", "")
                                val savedUser = userObj?.optString("username") ?: username

                                val prefs = getSharedPreferences("A3S_VPN_PREFS", Context.MODE_PRIVATE)
                                prefs.edit().apply {
                                    putBoolean("IS_LOGGED_IN", true)
                                    putString("LOGGED_USER", savedUser)
                                    putString("AUTH_TOKEN", token)
                                    putBoolean("IS_VIP", true)
                                    apply()
                                }

                                Toast.makeText(this@LoginActivity, "لاگ ان کامیاب!", Toast.LENGTH_SHORT).show()
                                val intent = Intent(this@LoginActivity, MainActivity::class.java)
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                startActivity(intent)
                                finish()
                            } else {
                                val errorMsg = json.optString("error", json.optString("message", "غلط یوزر نیم یا پاس ورڈ"))
                                Toast.makeText(this@LoginActivity, errorMsg, Toast.LENGTH_LONG).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(this@LoginActivity, "سرور رسپانس: $rawResponse", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            })
        }
    }
}
