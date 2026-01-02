package com.eya.utils

import android.content.Context
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import com.eya.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import android.util.Base64
import android.content.pm.PackageManager

class SpeechToTextManager(private val context: Context) {
    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private var audioFile: File? = null
    private val client = OkHttpClient()
    private val apiKey = BuildConfig.API_KEY
    
    fun startRecording(language: String = "tr-TR"): Boolean {
        if (isRecording) {
            return false
        }
        
        // İzin kontrolü
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                android.util.Log.e("STT", "RECORD_AUDIO izni yok")
                return false
            }
        }
        
        try {
            val audioDir = File(context.filesDir, "audio_recordings")
            if (!audioDir.exists()) {
                audioDir.mkdirs()
            }
            
            val timestamp = System.currentTimeMillis()
            audioFile = File(audioDir, "recording_$timestamp.3gp")
            
            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setOutputFile(audioFile!!.absolutePath)
                prepare()
                start()
            }
            
            isRecording = true
            android.util.Log.d("STT", "Kayıt başladı")
            return true
        } catch (e: Exception) {
            android.util.Log.e("STT", "Kayıt başlatma hatası: ${e.message}")
            e.printStackTrace()
            return false
        }
    }
    
    suspend fun stopRecordingAndTranscribe(language: String = "tr-TR"): String? {
        if (!isRecording || mediaRecorder == null) {
            return null
        }
        
        return withContext(Dispatchers.IO) {
            try {
                mediaRecorder?.apply {
                    stop()
                    release()
                }
                mediaRecorder = null
                isRecording = false
                
                val file = audioFile ?: return@withContext null
                
                val audioBytes = file.readBytes()
                val base64Audio = Base64.encodeToString(audioBytes, Base64.NO_WRAP)
                
                transcribeAudio(base64Audio, language)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
    
    private suspend fun transcribeAudio(base64Audio: String, languageCode: String): String? {
        if (apiKey.isEmpty()) {
            return null
        }
        
        return withContext(Dispatchers.IO) {
            try {
                val requestBody = JSONObject().apply {
                    put("config", JSONObject().apply {
                        put("encoding", "AMR_NB")
                        put("sampleRateHertz", 8000)
                        put("languageCode", languageCode)
                    })
                    put("audio", JSONObject().apply {
                        put("content", base64Audio)
                    })
                }.toString()
                
                val request = Request.Builder()
                    .url("https://speech.googleapis.com/v1/speech:recognize?key=$apiKey")
                    .post(requestBody.toRequestBody("application/json".toMediaType()))
                    .build()
                
                val response = client.newCall(request).execute()
                
                if (response.isSuccessful) {
                    val responseBody = response.body?.string() ?: return@withContext null
                    val jsonResponse = JSONObject(responseBody)
                    val results = jsonResponse.optJSONArray("results")
                    
                    if (results != null && results.length() > 0) {
                        val firstResult = results.getJSONObject(0)
                        val alternatives = firstResult.optJSONArray("alternatives")
                        if (alternatives != null && alternatives.length() > 0) {
                            val firstAlternative = alternatives.getJSONObject(0)
                            return@withContext firstAlternative.optString("transcript", "")
                        }
                    }
                }
                null
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
    
    fun isRecording(): Boolean = isRecording
    
    fun cancelRecording() {
        if (isRecording) {
            try {
                mediaRecorder?.apply {
                    stop()
                    release()
                }
                mediaRecorder = null
                isRecording = false
                audioFile?.delete()
                audioFile = null
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

