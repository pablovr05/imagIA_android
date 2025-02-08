package com.churumbeai.imagia.ui.history

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.churumbeai.imagia.R
import com.churumbeai.imagia.databinding.FragmentHistoryBinding
import com.churumbeai.imagia.network.ServerConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    data class LogItem(
        val prompt: String,
        val category: String,
        val createdAt: String
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val historyViewModel =
            ViewModelProvider(this).get(HistoryViewModel::class.java)

        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        val root: View = binding.root

        val recyclerView: RecyclerView = binding.logsRecyclerView
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        val sharedPreferences = requireContext().getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        val token = sharedPreferences.getString("auth_token", null)

        if (token != null) {
            fetchLogsFromServer(recyclerView)
        } else {
            Toast.makeText(requireContext(), "No se encontró el token de autenticación", Toast.LENGTH_SHORT).show()
        }

        return root
    }


    private fun fetchLogsFromServer(recyclerView: RecyclerView) {
        val sharedPreferences = requireContext().getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        val userId = sharedPreferences.getString("userId", null)
        val token = sharedPreferences.getString("auth_token", null)

        val url = ServerConfig.getBaseUrl() + "/api/admin/logs"
        val jsonRequest = JSONObject().apply {
            put("userId", userId)
            put("token", token)
        }

        Log.d("HistoryFragment", "URL de la solicitud: $url")
        Log.d("HistoryFragment", "Cuerpo de la solicitud: $jsonRequest")

        val client = OkHttpClient()
        val requestBody = jsonRequest.toString().toRequestBody("application/json".toMediaTypeOrNull())

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("Content-Type", "application/json")
            .build()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d("HistoryFragment", "Enviando solicitud al servidor...")
                val response = client.newCall(request).execute()

                Log.d("HistoryFragment", "Código de respuesta: ${response.code}")
                Log.d("HistoryFragment", "Cabeceras de respuesta: ${response.headers}")

                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    Log.d("HistoryFragment", "Respuesta exitosa: $responseBody")
                    responseBody?.let {
                        val logs = parseLogsResponse(it)
                        withContext(Dispatchers.Main) {
                            updateLogsUI(logs, recyclerView)
                        }
                    }
                } else {
                    Log.e("HistoryFragment", "Error en la respuesta: ${response.code}, ${response.message}")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Error al obtener los logs", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("HistoryFragment", "Error de red: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Error de red", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }


    private fun parseLogsResponse(responseBody: String): List<LogItem> {
        val logsList = mutableListOf<LogItem>()
        try {
            val jsonObject = JSONObject(responseBody)
            val logsData = jsonObject.optJSONObject("data")
            val logs = logsData?.optJSONObject("by_type")?.optJSONObject("logs")?.optJSONArray("INFO")

            // Definir el formato de fecha que esperamos (ISO 8601)
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
            val outputFormat = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault())

            logs?.let {
                for (i in 0 until it.length()) {
                    val logItem = it.getJSONObject(i)
                    val category = logItem.optString("category")
                    val prompt = logItem.optString("prompt")
                    val createdAt = logItem.optString("created_at")

                    if (category == "QUOTE" && prompt == "Solicitando uso de cuota") {
                        //Formatear fecha y sumar la hora de diff del server
                        val date = inputFormat.parse(createdAt)

                        val calendar = Calendar.getInstance()
                        calendar.time = date ?: Date()
                        calendar.add(Calendar.HOUR, 1)

                        val newDate = calendar.time
                        val formattedDate = outputFormat.format(newDate)

                        val log = LogItem(
                            prompt = "Solicitud de procesamiento de imagen",
                            category = "Imagen procesada",
                            createdAt = formattedDate
                        )
                        logsList.add(log)
                    }
                }
            }

            logsList.sortByDescending { logItem ->
                try {
                    outputFormat.parse(logItem.createdAt)?.time
                } catch (e: Exception) {
                    0L
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        Log.d("HistoryFragment", "Logs procesados: $logsList")

        return logsList
    }




    private fun updateLogsUI(logs: List<LogItem>, recyclerView: RecyclerView) {
        val adapter = LogsAdapter(logs)
        recyclerView.adapter = adapter
        adapter.notifyDataSetChanged()
    }

    class LogsAdapter(private val logs: List<LogItem>) : RecyclerView.Adapter<LogsAdapter.LogViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_log, parent, false)
            return LogViewHolder(view)
        }

        override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
            val logItem = logs[position]
            holder.categoryTextView.text = logItem.category
            holder.promptTextView.text = logItem.prompt
            holder.dateTextView.text = logItem.createdAt
        }

        override fun getItemCount() = logs.size

        class LogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val categoryTextView: TextView = itemView.findViewById(R.id.log_category)
            val promptTextView: TextView = itemView.findViewById(R.id.log_prompt)
            val dateTextView: TextView = itemView.findViewById(R.id.log_date)
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}