package com.churumbeai.imagia.network

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

object ServerConfig {
    var serverIp: String = "imagia2.ieti.site" // IP predeterminada
    var serverPort: String = "443"            // Puerto predeterminado (HTTPS)

    /**
     * Configura la IP y el puerto del servidor.
     */
    fun setServerConfig(ip: String, port: String) {
        serverIp = ip
        serverPort = port
    }

    /**
     * Obtiene la URL base en formato completo.
     */
    fun getBaseUrl(): String {
        return "https://$serverIp:$serverPort"
    }

    /**
     * Verifica la conexión al servidor haciendo una petición GET simple.
     */
    fun testConnection(onResult: (Boolean) -> Unit) {
        val client = OkHttpClient()
        val request = Request.Builder()
            .url(getBaseUrl()) // Usa la URL base configurada
            .build()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response: Response = client.newCall(request).execute()
                val isSuccess = response.isSuccessful
                Log.i("ServerConfig", "Conexión exitosa a ${getBaseUrl()}. Respuesta: ${response.body?.string()}")

                // Volver al hilo principal para notificar el resultado
                withContext(Dispatchers.Main) {
                    onResult(isSuccess)
                }
            } catch (e: IOException) {
                Log.e("ServerConfig", "Error al intentar conectar: ${e.message}", e)

                // Volver al hilo principal para notificar el fallo
                withContext(Dispatchers.Main) {
                    onResult(false)
                }
            }
        }
    }

}
