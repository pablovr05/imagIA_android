package com.churumbeai.imagia.ui.home

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.camera.core.*
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

typealias LumaListener = (luma: Double) -> Unit

class HomeFragment : Fragment(), SensorEventListener, TextToSpeech.OnInitListener {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var textToSpeech: TextToSpeech

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null

    private var lastThudTime: Long = 0
    private var thudCount = 0
    private val THUD_MIN_THRESHOLD = 1
    private val THUD_MAX_THRESHOLD = 3
    private val DOUBLE_THUD_INTERVAL = 500

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

        sensorManager = requireContext().getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)

        if (accelerometer == null) {
            Log.e("Sensor", "El sensor de aceleración lineal no está disponible")
        } else {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
        }

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissions(REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        binding.imageCaptureButton.setOnClickListener { takePhoto() }

        cameraExecutor = Executors.newSingleThreadExecutor()

        return root
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_LINEAR_ACCELERATION) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            val acceleration = abs(x) + abs(y) + abs(z)

            if (acceleration >= THUD_MIN_THRESHOLD && acceleration <= THUD_MAX_THRESHOLD) {
                val currentTime = System.currentTimeMillis()

                if (currentTime - lastThudTime < DOUBLE_THUD_INTERVAL) {
                    thudCount++
                } else {
                    thudCount = 1
                }

                lastThudTime = currentTime

                if (thudCount == 2) {
                    Log.d("DoubleThud", "Se detectó un double thud")
                    takePhoto()
                    simulateServerResponse()
                    thudCount = 0
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        imageCapture.takePicture(
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    super.onCaptureSuccess(image)

                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)

                    image.close()

                    sendPhotoToServer(bytes)
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Error al capturar la foto: ${exception.message}", exception)
                    Toast.makeText(requireContext(), "Error al capturar la foto", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun sendPhotoToServer(photoData: ByteArray) {
        val url = ServerConfig.getBaseUrl() + "/upload"

        val client = OkHttpClient()
        val requestBody = photoData.toRequestBody("image/jpeg".toMediaTypeOrNull())

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("Content-Type", "image/jpeg")
            .build()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    Log.i(TAG, "Foto enviada: ${response.body?.string()}")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Foto enviada exitosamente", Toast.LENGTH_SHORT).show()
                        speakText("Imagen enviada al servidor con éxito")
                    }
                } else {
                    Log.e(TAG, "Error al enviar la foto: ${response.code}")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Error al enviar la foto", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "Error de red: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Error de red al enviar la foto", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun simulateServerResponse() {
        Toast.makeText(requireContext(), "Procesando imagen...", Toast.LENGTH_SHORT).show()
        val serverResponse = "Imagen procesada exitosamente"
        speakText(serverResponse)
    }

    private fun speakText(text: String) {
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
                }

            imageCapture = ImageCapture.Builder().build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture
                )
            } catch (exc: Exception) {
                Log.e(TAG, "Error al iniciar la cámara", exc)
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            requireContext(), it
        ) == PackageManager.PERMISSION_GRANTED
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
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS =
            mutableListOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
    }
}
