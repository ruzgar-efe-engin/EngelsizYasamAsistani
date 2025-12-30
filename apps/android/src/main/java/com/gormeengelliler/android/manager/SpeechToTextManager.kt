package com.gormeengelliler.android.manager

import android.content.Context
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.util.Properties

class SpeechToTextManager(private val context: Context) {
    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private var audioFile: File? = null
    private var geminiApiKey: String = ""
    private val client = OkHttpClient()
    private val scope = CoroutineScope(Dispatchers.IO)
    
    init {
        loadGeminiAPIKey()
    }
    
    private fun loadGeminiAPIKey() {
        try {
            val localPropertiesFile = File(context.filesDir.parent, "local.properties")
            if (localPropertiesFile.exists()) {
                val properties = Properties()
                FileInputStream(localPropertiesFile).use { properties.load(it) }
                geminiApiKey = properties.getProperty("GEMINI_API_KEY", "")
                
                if (geminiApiKey.isEmpty()) {
                    // Varsayılan key (development için)
                    geminiApiKey = "AIzaSyBiR3IYsBKQnlZ1G2XvQ8g2fIKIP30GhwI"
                }
            } else {
                // Dosya yoksa varsayılan key kullan
                geminiApiKey = "AIzaSyBiR3IYsBKQnlZ1G2XvQ8g2fIKIP30GhwI"
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Hata durumunda varsayılan key
            geminiApiKey = "AIzaSyBiR3IYsBKQnlZ1G2XvQ8g2fIKIP30GhwI"
        }
    }
    
    fun startRecording(): Boolean {
        if (isRecording) {
            EventLogManager.logSTT("startRecording iptal", "zaten kayıt yapılıyor", true)
            return false
        }
        
        try {
            val audioDir = File(context.filesDir, "audio_recordings")
            if (!audioDir.exists()) {
                audioDir.mkdirs()
                EventLogManager.logSTT("Audio dizini oluşturuldu", audioDir.absolutePath)
            }
            
            val timestamp = System.currentTimeMillis()
            audioFile = File(audioDir, "recording_$timestamp.wav")
            EventLogManager.logSTT("startRecording başladı", "dosya: ${audioFile!!.name}")
            
            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setOutputFile(audioFile!!.absolutePath)
                prepare()
                start()
            }
            
            isRecording = true
            EventLogManager.logSTT("MediaRecorder başlatıldı", "kayıt başladı")
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            EventLogManager.logSTT("startRecording hatası", "${e.message}", true)
            return false
        }
    }
    
    suspend fun stopRecordingAndTranscribe(): String? {
        if (!isRecording || mediaRecorder == null) {
            EventLogManager.logSTT("stopRecording iptal", "kayıt yapılmıyor", true)
            return null
        }
        
        try {
            EventLogManager.logSTT("stopRecording başladı", "MediaRecorder durduruluyor")
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            isRecording = false
            EventLogManager.logSTT("MediaRecorder durduruldu", "kayıt tamamlandı")
            
            val file = audioFile ?: return null
            
            // Ses dosyasını base64 encode et
            val audioBytes = file.readBytes()
            EventLogManager.logSTT("Ses dosyası okundu", "${audioBytes.size} bytes")
            val base64Audio = android.util.Base64.encodeToString(audioBytes, android.util.Base64.NO_WRAP)
            EventLogManager.logSTT("Base64 encode edildi", "${base64Audio.length} karakter")
            
            // Cloud Speech-to-Text API'ye gönder
            EventLogManager.logSTT("Speech-to-Text API çağrısı", "transcribeAudio başladı")
            return transcribeAudio(base64Audio)
        } catch (e: Exception) {
            e.printStackTrace()
            EventLogManager.logSTT("stopRecording hatası", "${e.message}", true)
            return null
        }
    }
    
    private suspend fun transcribeAudio(base64Audio: String): String? {
        if (geminiApiKey.isEmpty()) {
            EventLogManager.logSTT("API key yok", "", true)
            return null
        }
        
        return try {
            val requestBody = JSONObject().apply {
                put("config", JSONObject().apply {
                    put("encoding", "AMR_NB")
                    put("sampleRateHertz", 8000)
                    put("languageCode", "tr-TR") // Varsayılan Türkçe, dil seçimine göre değiştirilebilir
                })
                put("audio", JSONObject().apply {
                    put("content", base64Audio)
                })
            }.toString()
            
            EventLogManager.logSTT("API request oluşturuldu", "encoding=AMR_NB, sampleRate=8000, languageCode=tr-TR")
            val request = Request.Builder()
                .url("https://speech.googleapis.com/v1/speech:recognize?key=$geminiApiKey")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()
            
            EventLogManager.logSTT("API çağrısı yapılıyor", "Speech-to-Text API")
            val response = client.newCall(request).execute()
            
            if (response.isSuccessful) {
                EventLogManager.logSTT("API başarılı", "response parse ediliyor")
                val responseBody = response.body?.string() ?: return null
                val jsonResponse = JSONObject(responseBody)
                val results = jsonResponse.optJSONArray("results")
                
                if (results != null && results.length() > 0) {
                    val firstResult = results.getJSONObject(0)
                    val alternatives = firstResult.optJSONArray("alternatives")
                    if (alternatives != null && alternatives.length() > 0) {
                        val firstAlternative = alternatives.getJSONObject(0)
                        val transcript = firstAlternative.optString("transcript", "")
                        EventLogManager.logSTT("Transkript alındı", "text='${transcript.take(50)}${if (transcript.length > 50) "..." else ""}'")
                        return transcript
                    } else {
                        EventLogManager.logSTT("Alternatives bulunamadı", "", true)
                    }
                } else {
                    EventLogManager.logSTT("Results bulunamadı", "", true)
                }
            } else {
                EventLogManager.logSTT("API hatası", "code=${response.code}", true)
            }
            null
        } catch (e: Exception) {
            e.printStackTrace()
            EventLogManager.logSTT("transcribeAudio hatası", "${e.message}", true)
            null
        }
    }
    
    fun isRecording(): Boolean {
        return isRecording
    }
    
    fun cancelRecording() {
        if (isRecording) {
            try {
                mediaRecorder?.apply {
                    stop()
                    release()
                }
                mediaRecorder = null
                isRecording = false
                
                // Ses dosyasını sil
                audioFile?.delete()
                audioFile = null
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

