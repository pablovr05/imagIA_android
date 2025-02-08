package com.churumbeai.imagia.ui.account

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.churumbeai.imagia.databinding.FragmentAccountBinding
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

class AccountFragment : Fragment() {

    private var _binding: FragmentAccountBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val accountViewModel = ViewModelProvider(this).get(AccountViewModel::class.java)
        _binding = FragmentAccountBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onResume() {
        super.onResume()

        val sharedPreferences = requireContext().getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        val username = sharedPreferences.getString("nickname", null)

        binding.userName.text = username;

        fetchUserInfo()
    }

    private fun fetchUserInfo() {
        val sharedPreferences = requireContext().getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        val userId = sharedPreferences.getString("userId", null)
        val token = sharedPreferences.getString("auth_token", null)

        if (userId.isNullOrEmpty() || token.isNullOrEmpty()) {
            Toast.makeText(requireContext(), "Información de usuario no disponible", Toast.LENGTH_SHORT).show()
            Log.e("AccountFragment", "userId o token están vacíos o nulos")
            return
        }

        val url = ServerConfig.getBaseUrl() + "/api/admin/usuaris/quota"
        val jsonRequest = JSONObject().apply {
            put("userId", userId)
            put("token", token)
        }

        Log.d("AccountFragment", "URL de la solicitud: $url")
        Log.d("AccountFragment", "Cuerpo de la solicitud: $jsonRequest")

        val client = OkHttpClient()
        val requestBody = jsonRequest.toString().toRequestBody("application/json".toMediaTypeOrNull())

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("Content-Type", "application/json")
            .build()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d("AccountFragment", "Enviando solicitud al servidor...")
                val response = client.newCall(request).execute()

                Log.d("AccountFragment", "Código de respuesta: ${response.code}")
                Log.d("AccountFragment", "Cabeceras de respuesta: ${response.headers}")

                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    Log.d("AccountFragment", "Respuesta exitosa: $responseBody")
                    responseBody?.let { parseUserInfo(it) }
                } else {
                    Log.e("AccountFragment", "Error en la respuesta: ${response.code}, ${response.message}")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Error al obtener información del usuario", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("AccountFragment", "Error de red: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Error de red", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private suspend fun parseUserInfo(responseBody: String) {
        withContext(Dispatchers.Main) {
            try {
                val jsonObject = JSONObject(responseBody)
                val dataObject = jsonObject.getJSONObject("data")

                val accountType = dataObject.optString("type_id", "FREE")
                val remainingQuota = dataObject.optInt("remainingQuote", 0)
                val totalQuota = dataObject.optInt("totalQuote", 0)
                val usedQuota = totalQuota - remainingQuota

                binding.userType.text = "Plan: $accountType"
                binding.userQueries.text = "Cuota usada/restante: $usedQuota / $totalQuota"

            } catch (e: Exception) {
                Log.e("AccountFragment", "Error al analizar JSON: ${e.message}")
                Toast.makeText(requireContext(), "Error al procesar la información", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
