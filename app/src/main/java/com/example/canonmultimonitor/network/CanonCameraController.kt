package com.example.canonmultimonitor.network

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.example.canonmultimonitor.model.CameraConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

class CanonCameraController(val config: CameraConfig) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // Continuous streaming without timeout
        .build()

    private val _currentFrame = MutableStateFlow<Bitmap?>(null)
    val currentFrame: StateFlow<Bitmap?> = _currentFrame

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected

    private val _fps = MutableStateFlow(0)
    val fps: StateFlow<Int> = _fps

    private var job: Job? = null
    private var isStreaming = false

    fun start(scope: CoroutineScope) {
        if (isStreaming) return
        isStreaming = true
        job = scope.launch(Dispatchers.IO) {
            while (isActive && isStreaming) {
                try {
                    connectAndStream()
                } catch (e: Exception) {
                    _isConnected.value = false
                    delay(2000) // Retry connection every 2 seconds
                }
            }
        }
    }

    fun stop() {
        isStreaming = false
        job?.cancel()
        _isConnected.value = false
        _currentFrame.value = null
    }

    private fun connectAndStream() {
        // Canon CCAPI Live View endpoint
        val streamUrl = "http://${config.ipAddress}:${config.port}/ccapi/ver100/shooting/liveview/flip"
        val request = Request.Builder().url(streamUrl).build()

        var response: Response? = null
        try {
            response = client.newCall(request).execute()
            if (response.isSuccessful) {
                _isConnected.value = true
                val inputStream = response.body?.byteStream() ?: return
                readMjpegStream(inputStream)
            } else {
                _isConnected.value = false
            }
        } catch (e: Exception) {
            _isConnected.value = false
        } finally {
            response?.close()
        }
    }

    // High performance zero-buffer MJPEG frame extractor
    private fun readMjpegStream(stream: InputStream) {
        val buffer = ByteArray(8192)
        val jpegStream = ByteArrayOutputStream(65536)
        var prevByte = -1
        var frameCount = 0
        var lastFpsCalcTime = System.currentTimeMillis()

        try {
            var bytesRead: Int
            while (isStreaming && stream.read(buffer).also { bytesRead = it } != -1) {
                for (i in 0 until bytesRead) {
                    val currentByte = buffer[i].toInt() and 0xFF

                    // Start of Image marker (0xFF 0xD8)
                    if (prevByte == 0xFF && currentByte == 0xD8) {
                        jpegStream.reset()
                        jpegStream.write(0xFF)
                        jpegStream.write(0xD8)
                    } else {
                        jpegStream.write(currentByte)
                    }

                    // End of Image marker (0xFF 0xD9)
                    if (prevByte == 0xFF && currentByte == 0xD9) {
                        val jpegBytes = jpegStream.toByteArray()
                        val opts = BitmapFactory.Options().apply {
                            inPreferredConfig = Bitmap.Config.RGB_565 // Optimized for speed & memory
                        }
                        val bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size, opts)
                        if (bitmap != null) {
                            _currentFrame.value = bitmap
                            frameCount++
                            val now = System.currentTimeMillis()
                            if (now - lastFpsCalcTime >= 1000) {
                                _fps.value = frameCount
                                frameCount = 0
                                lastFpsCalcTime = now
                            }
                        }
                    }
                    prevByte = currentByte
                }
            }
        } catch (e: Exception) {
            _isConnected.value = false
        }
    }

    // Trigger Shutter / Recording via Canon CCAPI
    suspend fun triggerShutter(): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = "http://${config.ipAddress}:${config.port}/ccapi/ver100/shooting/control/shutterbutton"
            val jsonBody = "{\"action\":\"half_and_full\",\"af\":true}"
            val body = jsonBody.toRequestBody("application/json".toMediaType())
            val request = Request.Builder().url(url).post(body).build()
            val response = client.newCall(request).execute()
            val success = response.isSuccessful
            response.close()
            success
        } catch (e: Exception) {
            false
        }
    }
}
