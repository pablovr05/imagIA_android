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

        val usernameEditText = findViewById<EditText>(R.id.usernameEditText)
        val loginButton = findViewById<Button>(R.id.loginButton)

        loginButton.setOnClickListener {
            val username = usernameEditText.text.toString().trim()

            if (username.isEmpty()) {
                Toast.makeText(this, "Por favor, introduce tu nombre de usuario", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            ServerConfig.testConnection { isConnected ->
                if (isConnected) {
                    Toast.makeText(this, "Conexión exitosa", Toast.LENGTH_SHORT).show()
                    Log.i("LoginActivity", "Conexión exitosa")

                    // Ir a MainActivity
                    val intent = Intent(this, MainActivity::class.java).apply {
                        putExtra("USERNAME", username)
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
