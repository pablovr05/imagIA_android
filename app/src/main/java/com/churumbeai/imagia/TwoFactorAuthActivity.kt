package com.churumbeai.imagia

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.churumbeai.imagia.network.ServerConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class TwoFactorAuthActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.login_2fa_layout)

        val code2FAEditText = findViewById<EditText>(R.id.code2FAEditText)
        val verifyButton = findViewById<Button>(R.id.verifyButton)

        verifyButton.setOnClickListener {
            val code2FA = code2FAEditText.text.toString().trim()

            if (code2FA.isEmpty()) {
                Toast.makeText(this, "Por favor, introduce el código OTP", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            verify2FA(code2FA)
        }
    }

    private fun verify2FA(code2F_func: String) {
        // Recoger datos guardados en local user
        val sharedPreferences = getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        val phone = sharedPreferences.getString("user_phone", null)

        // Check telefono
        if (phone == null) {
            Toast.makeText(this, "No se encontró el número de teléfono", Toast.LENGTH_SHORT).show()
            return
        }

        // Request validacion
        val url = ServerConfig.getBaseUrl() + "/api/usuaris/validar"

        val jsonBody = JSONObject().apply {
            put("telefon", phone)
            put("codi_validacio", code2F_func)
        }

        val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaTypeOrNull())

        val client = OkHttpClient()
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("Content-Type", "application/json")
            .build()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()

                if (response.isSuccessful && responseBody != null) {
                    Log.i("TwoFactorAuthActivity", "Verificación exitosa: $responseBody")

                    val jsonResponse = JSONObject(responseBody)
                    val status = jsonResponse.getString("status")

                    if (status == "OK") {
                        val apiKey = jsonResponse.getJSONObject("data").getString("api_key")

                        val sharedPreferences = getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
                        sharedPreferences.edit().putString("api_key", apiKey).apply()

                        withContext(Dispatchers.Main) {
                            Toast.makeText(applicationContext, "Verificación exitosa", Toast.LENGTH_SHORT).show()

                            startActivity(Intent(applicationContext, MainActivity::class.java))
                            finish()
                        }
                    } else {
                        val message = jsonResponse.getString("message")
                        withContext(Dispatchers.Main) {
                            Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Log.e("TwoFactorAuthActivity", "Error en la verificación: ${response.code}")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(applicationContext, "Código incorrecto", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                // Manejo de errores de red
                Log.e("TwoFactorAuthActivity", "Error de red: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(applicationContext, "Error de red", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
