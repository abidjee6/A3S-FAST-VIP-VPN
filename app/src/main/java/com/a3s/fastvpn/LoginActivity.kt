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

            // سب سے پہلے کلاسک JSON فارمیٹ میں کوشش کریں
            performAuthAttempt(username, password, isJson = true) { success, serverErrorMsg ->
                if (!success) {
                    // اگر JSON پر سرور ریجیکٹ کرے تو فارم ڈیٹا (FormBody) فارمیٹ آزمائیں
                    performAuthAttempt(username, password, isJson = false) { finalSuccess, finalMsg ->
                        runOnUiThread {
                            btnLogin.isEnabled = true
                            progressBar.visibility = View.GONE
                            if (!finalSuccess) {
                                val errorDetail = if (!finalMsg.isNullOrEmpty()) finalMsg else "غلط یوزر نیم یا پاس ورڈ"
                                Toast.makeText(this@LoginActivity, errorDetail, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun performAuthAttempt(
        user: String,
        pass: String,
        isJson: Boolean,
        onResult: (Boolean, String?) -> Unit
    ) {
        val requestBody: RequestBody
        val contentType: String

        if (isJson) {
            val json = JSONObject().apply {
                put("username", user)
                put("password", pass)
                put("user", user)
                put("pass", pass)
            }
            contentType = "application/json; charset=utf-8"
            requestBody = json.toString().toRequestBody(contentType.toMediaTypeOrNull())
        } else {
            contentType = "application/x-www-form-urlencoded"
            requestBody = FormBody.Builder()
                .add("username", user)
                .add("password", pass)
                .add("user", user)
                .add("pass", pass)
                .build()
        }

        val request = Request.Builder()
            .url("$BASE_URL/api/login")
            .post(requestBody)
            .addHeader("Content-Type", contentType)
            .addHeader("Accept", "application/json")
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    onResult(false, "سرور سے رابطہ نہ ہو سکا: ${e.message}")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val statusCode = response.code
                val rawResponse = response.body?.string() ?: ""

                // 404 آنے کی صورت میں /login ٹرائی کریں
                if (statusCode == 404) {
                    onResult(false, "API route not found (404)")
                    return
                }

                try {
                    val result = LoginResponse.parse(rawResponse)

                    if (result.success || statusCode in 200..299) {
                        runOnUiThread {
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
                        }
                        onResult(true, null)
                    } else {
                        // سرور کی طرف سے ملا ہوا تفصیلی پیغام
                        val msg = if (result.message.isNotEmpty()) result.message else "Authentication failed ($statusCode)"
                        onResult(false, msg)
                    }
                } catch (e: Exception) {
                    if (statusCode in 200..299) {
                        // اگر سرور نے 200 OK دیا لیکن رسپانس سادہ ٹیکسٹ تھا
                        runOnUiThread {
                            val prefs = getSharedPreferences("A3S_VPN_PREFS", Context.MODE_PRIVATE)
                            prefs.edit().apply {
                                putBoolean("IS_LOGGED_IN", true)
                                putString("LOGGED_USER", user)
                                apply()
                            }
                            val intent = Intent(this@LoginActivity, MainActivity::class.java)
                            startActivity(intent)
                            finish()
                        }
                        onResult(true, null)
                    } else {
                        onResult(false, "سرور جواب: $rawResponse")
                    }
                }
            }
        })
    }
}
