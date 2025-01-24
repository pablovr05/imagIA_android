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
        val ipEditText = findViewById<EditText>(R.id.ipEditText)
        val portEditText = findViewById<EditText>(R.id.portEditText)
        val connectButton = findViewById<Button>(R.id.connectButton)

        // Acción del botón "Conectar"
        connectButton.setOnClickListener {
            val ip = ipEditText.text.toString().trim()
            val port = portEditText.text.toString().trim()

            // Validación básica
            if (ip.isEmpty() || port.isEmpty()) {
                Toast.makeText(this, "Por favor, introduce IP y puerto", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!isValidPort(port)) {
                Toast.makeText(this, "El puerto debe ser un número entre 1 y 65535", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Configurar IP y Puerto en ServerConfig
            ServerConfig.setServerConfig(ip, port)

            // Probar la conexión
            ServerConfig.testConnection { isConnected ->
                if (isConnected) {
                    Toast.makeText(this, "Conexión exitosa", Toast.LENGTH_SHORT).show()
                    Log.i("LoginActivity", "Conexión exitosa")

                    // Ir a MainActivity
                    val intent = Intent(this, MainActivity::class.java).apply {
                        putExtra("SERVER_IP", ip)
                        putExtra("SERVER_PORT", port)
                    }
                    startActivity(intent)
                } else {
                    Toast.makeText(this, "No se pudo conectar al servidor", Toast.LENGTH_SHORT).show()
                    Log.e("LoginActivity", "Error al conectar")
                }
            }
        }

    }

    // Verifica que el puerto esté en el rango válido (1-65535)
    private fun isValidPort(port: String): Boolean {
        return try {
            val portNumber = port.toInt()
            portNumber in 1..65535
        } catch (e: NumberFormatException) {
            false
        }
    }
}
