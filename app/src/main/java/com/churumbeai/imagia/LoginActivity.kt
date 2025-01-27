package com.churumbeai.imagia

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.churumbeai.imagia.network.ServerConfig

class LoginActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.login_layout)

        // Referencias a los campos del formulario
        val usernameEditText = findViewById<EditText>(R.id.usernameEditText)
        val loginButton = findViewById<Button>(R.id.loginButton)

        // Acción del botón "Iniciar Sesión"
        loginButton.setOnClickListener {
            val username = usernameEditText.text.toString().trim()

            // Validación básica
            if (username.isEmpty()) {
                Toast.makeText(this, "Por favor, introduce tu nombre de usuario", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Probar la conexión al servidor
            ServerConfig.testConnection { isConnected ->
                if (isConnected) {
                    Toast.makeText(this, "Conexión exitosa", Toast.LENGTH_SHORT).show()
                    Log.i("LoginActivity", "Conexión exitosa")

                    // Ir a MainActivity
                    val intent = Intent(this, MainActivity::class.java).apply {
                        putExtra("USERNAME", username) // Enviar el nombre de usuario a MainActivity
                    }
                    startActivity(intent)
                } else {
                    Toast.makeText(this, "No se pudo conectar al servidor", Toast.LENGTH_SHORT).show()
                    Log.e("LoginActivity", "Error al conectar")
                }
            }
        }
    }
}
