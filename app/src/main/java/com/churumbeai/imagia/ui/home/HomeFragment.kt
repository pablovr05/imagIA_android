package com.churumbeai.imagia.ui.home

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.churumbeai.imagia.databinding.FragmentHomeBinding
import com.churumbeai.imagia.network.ServerConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs

class HomeFragment : Fragment(), SensorEventListener, TextToSpeech.OnInitListener {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService

    private lateinit var textToSpeech: TextToSpeech

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null

    // Variables para detectar el double thud
    private var lastThudTime: Long = 0
    private var thudCount = 0
    private val THUD_MIN_THRESHOLD = 1 // Umbral mínimo de aceleración para un thud
    private val THUD_MAX_THRESHOLD = 3 // Umbral máximo de aceleración para un thud
    private val DOUBLE_THUD_INTERVAL = 500 // Intervalo máximo entre thuds en milisegundos

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val homeViewModel =
            ViewModelProvider(this).get(HomeViewModel::class.java)

        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root

        textToSpeech = TextToSpeech(requireContext(), this)

        // Inicializar el sensor
        sensorManager = requireContext().getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)

        if (accelerometer == null) {
            Log.e("Sensor", "El sensor de aceleración lineal no está disponible en este dispositivo")
        } else {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
        }

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissions(REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        cameraExecutor = Executors.newSingleThreadExecutor()

        return root
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_LINEAR_ACCELERATION) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            // Calculamos la aceleración total
            val acceleration = abs(x) + abs(y) + abs(z)

            // Verificamos si la aceleración está dentro del rango válido para un thud
            if (acceleration >= THUD_MIN_THRESHOLD && acceleration <=THUD_MAX_THRESHOLD) {
                val currentTime = System.currentTimeMillis()

                if (currentTime - lastThudTime < DOUBLE_THUD_INTERVAL) {
                    thudCount++
                } else {
                    thudCount = 1 // Reiniciar contador si pasa mucho tiempo entre thuds
                }

                lastThudTime = currentTime

                if (thudCount == 2) {
                    Log.d("DoubleThud", "Se ha detectado un double thud")
                    takePhoto()
                    simulateServerResponse() // Simular respuesta de servidor con TTS
                    thudCount = 0 // Reiniciar después del double thud
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No necesitamos implementar este método para este caso
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return


        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())
        val contentValues = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        }

        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(
                requireContext().contentResolver,
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            )
            .build()


        imageCapture.takePicture(
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    super.onCaptureSuccess(image)

                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)


                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val msg = "Foto guardada: ${output.savedUri}"
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                    Log.d(TAG, msg)

                    image.close()

                    // Enviar la imagen al servidor
                    sendPhotoToServer(bytes)
                }

                override fun onError(exception: ImageCaptureException) {
                    super.onError(exception)
                    Log.e(TAG, "Error al capturar la foto: ${exception.message}", exception)
                    Toast.makeText(requireContext(), "Error al capturar la foto", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun sendPhotoToServer(photoData: ByteArray) {
        // URL del servidor (usa tu configuración del ServerConfig)
        val url = ServerConfig.getBaseUrl() + "/upload" // Ajusta el endpoint según sea necesario

        // Crear cliente y solicitud usando OkHttp
        val client = OkHttpClient()
        val requestBody = photoData.toRequestBody("image/jpeg".toMediaTypeOrNull())

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("Content-Type", "image/jpeg")
            .build()

        // Ejecutar la solicitud en un hilo secundario
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    Log.i(TAG, "Foto enviada con éxito: ${response.body?.string()}")

                    // Mostrar mensaje en la UI en el hilo principal
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Foto enviada con éxito", Toast.LENGTH_SHORT).show()
                        speakText("Imagen enviada al servidor con éxito")
                    }
                } else {
                    Log.e(TAG, "Error al enviar la foto: ${response.code}")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Error al enviar la foto", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "Error de red al enviar la foto: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Error de red al enviar la foto", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun simulateServerResponse() {
        val serverResponse = "Imagen recibida correctamente."
        speakText(serverResponse)
    }

    private fun speakText(text: String) {
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = androidx.camera.core.Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
                }

            imageCapture = ImageCapture.Builder()
                .build()

            val cameraSelector = androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture
                )
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            requireContext(), it
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        cameraExecutor.shutdown()
        sensorManager.unregisterListener(this)
        if (this::textToSpeech.isInitialized) {
            textToSpeech.stop()
            textToSpeech.shutdown()
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val langResult = textToSpeech.setLanguage(Locale("es", "ES"))
            if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                Toast.makeText(requireContext(), "Idioma no soportado", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(requireContext(), "Error al inicializar TTS", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val TAG = "CameraXApp"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS =
            mutableListOf(
                android.Manifest.permission.CAMERA
            ).toTypedArray()
    }
}
