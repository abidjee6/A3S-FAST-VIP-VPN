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

            // سب سے پہلے پچھلی ورکنگ ایپ کا بنیادی روٹ /api/login ٹرائی کریں
            sendLoginRequest(username, password, "/api/login") { success ->
                if (!success) {
                    // اگر /api/login پر 404 آئے تو بیک اپ روٹ /login ٹرائی کریں
                    sendLoginRequest(username, password, "/login") { finalSuccess ->
                        if (!finalSuccess) {
                            runOnUiThread {
                                btnLogin.isEnabled = true
                                progressBar.visibility = View.GONE
                                Toast.makeText(this@LoginActivity, "سرور روٹ نہیں ملا (404 Not Found)", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun sendLoginRequest(user: String, pass: String, endpoint: String, callback: (Boolean) -> Unit) {
        val btnLogin = findViewById<MaterialButton>(R.id.btnLogin)
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)

        val jsonPayload = JSONObject().apply {
            put("username", user)
            put("password", pass)
        }

        val requestBody = jsonPayload.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

        val request = Request.Builder()
            .url("$BASE_URL$endpoint")
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
                callback(false)
            }

            override fun onResponse(call: Call, response: Response) {
                val statusCode = response.code
                val rawResponse = response.body?.string() ?: ""

                if (statusCode == 404) {
                    // اگر یہ روٹ سرور پر نہیں ہے تو دوسرے روٹ کی طرف بڑھیں
                    callback(false)
                    return
                }

                runOnUiThread {
                    btnLogin.isEnabled = true
                    progressBar.visibility = View.GONE

                    if (statusCode == 401 || statusCode == 403) {
                        Toast.makeText(this@LoginActivity, "غلط یوزر نیم یا پاس ورڈ", Toast.LENGTH_LONG).show()
                        callback(true)
                        return@runOnUiThread
                    }

                    try {
                        val result = LoginResponse.parse(rawResponse)

                        if (result.success || statusCode in 200..299) {
                            val prefs = getSharedPreferences("A3S_VPN_PREFS", Context.MODE_PRIVATE)
                            prefs.edit().apply {
                                putBoolean("IS_LOGGED_IN", true)
                                putString("LOGGED_USER", result.username ?: user)
                                putString("AUTH_TOKEN", result.token ?: "")
                                putBoolean("IS_VIP", result.isVip)
                                apply()
                            }

                            Toast.makeText(this@LoginActivity, "لاگ ان کامیاب!", Toast.LENGTH_SHORT).show()
                            val intent = Intent(this@LoginActivity, MainActivity::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            startActivity(intent)
                            finish()
                            callback(true)
                        } else {
                            Toast.makeText(this@LoginActivity, result.message, Toast.LENGTH_LONG).show()
                            callback(true)
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this@LoginActivity, "لاگ ان رسپانس ایرر: $rawResponse", Toast.LENGTH_LONG).show()
                        callback(true)
                    }
                }
            }
        })
    }
}
