package com.churumbeai.imagia

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.churumbeai.imagia.network.ServerConfig
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class RegisterActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.register_layout)

        val sharedPreferences = getSharedPreferences("user_prefs", Context.MODE_PRIVATE)

        // Si ya hay un token guardado, saltar al 2FA
        if (sharedPreferences.contains("auth_token")) {
            startActivity(Intent(this, TwoFactorAuthActivity::class.java))
            finish()
            return
        }

        //INPUTS REGISTRO
        val phoneEditText = findViewById<EditText>(R.id.phoneEditText)
        val nicknameEditText = findViewById<EditText>(R.id.nicknameEditText)
        val emailEditText = findViewById<EditText>(R.id.emailEditText)
        val passwordEditText = findViewById<EditText>(R.id.passwordEditText)
        val confirmPasswordEditText = findViewById<EditText>(R.id.confirmPasswordEditText)
        val registerButton = findViewById<Button>(R.id.registerButton)

        registerButton.setOnClickListener {
            val phone = phoneEditText.text.toString().trim()
            val nickname = nicknameEditText.text.toString().trim()
            val email = emailEditText.text.toString().trim()
            val password = passwordEditText.text.toString().trim()
            val confirmPassword = confirmPasswordEditText.text.toString().trim()

            val errorMessages = validateInputs(phone, nickname, email, password, confirmPassword)

            //SI no hay errores , porceder con el registro
            if (errorMessages.isNotEmpty()) {
                showValidationDialog(errorMessages)
            } else {
                // Realiza el registro llamando a la API
                registerUser(phone, nickname, email, password)
            }
        }
    }

    //Validacion del registro
    private fun validateInputs(
        phone: String,
        nickname: String,
        email: String,
        password: String,
        confirmPassword: String
    ): List<String> {
        val errors = mutableListOf<String>()

        if (phone.isEmpty() || nickname.isEmpty() || email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
            errors.add("Por favor, completa todos los campos.")
            return errors
        }

        if (!isValidEmail(email)) {
            errors.add("- Correo electrónico no válido.")
        }

        if (!isValidPhone(phone)) {
            errors.add("- Número de teléfono no válido (Debe contener solo números y tener entre 8 y 15 dígitos).")
        }

        if (password.length < 6) {
            errors.add("- La contraseña debe tener al menos 6 caracteres.")
        }

        if (password != confirmPassword) {
            errors.add("- Las contraseñas no coinciden.")
        }

        return errors
    }

    //DIALOGO DE ERRORES
    private fun showValidationDialog(errors: List<String>) {
        val message = errors.joinToString("\n\n")

        val dialogBuilder = AlertDialog.Builder(this)
        dialogBuilder.setTitle("Errores de Validación")
        dialogBuilder.setMessage(message)
        dialogBuilder.setPositiveButton("Aceptar") { dialog, _ ->
            dialog.dismiss()
        }

        val alertDialog = dialogBuilder.create()
        alertDialog.show()
    }

    private fun isValidEmail(email: String): Boolean {
        return Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    private fun isValidPhone(phone: String): Boolean {
        return phone.matches(Regex("^[0-9]{8,15}$"))
    }

    // API REGISTRO DE USUARIOS (PLACEHOLDER)
    private fun registerUser(phone: String, nickname: String, email: String, password: String) {
        val url = ServerConfig.getBaseUrl() + "/api/usuaris/registrar"

        val jsonBody = JSONObject().apply {
            put("phone", phone)  //
            put("nickname", nickname)
            put("email", email)
            put("password", password)
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

                // RESPUESTA BIEN
                if (response.isSuccessful && responseBody != null) {
                    Log.i("RegisterActivity", "Registro exitoso: $responseBody")

                    val jsonResponse = JSONObject(responseBody)
                    val status = jsonResponse.getString("status")
                    val message = jsonResponse.getString("message")

                    if (status == "OK") {
                        // Si la respuesta es exitosa, se obtiene el usuario creado (data)
                        val userData = jsonResponse.getJSONObject("data")
                        val createdNickname = userData.getString("nickname")
                        val createdEmail = userData.getString("email")

                        // TOKEN DE PLACEHOLDER PRUEBAS
                        val authToken = "PLACEHOLDER_TOKEN"
                        val sharedPreferences = getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
                        sharedPreferences.edit().putString("auth_token", authToken).apply()

                        withContext(Dispatchers.Main) {
                            //VISTA 2FA
                            Toast.makeText(applicationContext, "Registro exitoso", Toast.LENGTH_SHORT).show()
                            startActivity(Intent(applicationContext, TwoFactorAuthActivity::class.java))
                            finish()
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(applicationContext, "Error al registrar usuario: $message", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Log.e("RegisterActivity", "Error en el registro: ${response.code}")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(applicationContext, "Error al registrar usuario", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("RegisterActivity", "Error de red: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(applicationContext, "Error de red", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
