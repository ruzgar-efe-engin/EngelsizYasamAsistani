package com.gormeengelliler.android.manager

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import com.gormeengelliler.android.manager.MenuManager
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

data class TTSConfig(
    val code: String,
    val name: String,
    val googleTTS: GoogleTTSConfig,
    val iosNative: IOSNativeConfig
)

data class GoogleTTSConfig(
    val voiceId: String,
    val languageCode: String,
    val ssmlGender: String
)

data class IOSNativeConfig(
    val voiceIdentifier: String
)

class TTSManager(private val context: Context, private val menuManager: MenuManager) {
    private var ttsConfigs: List<TTSConfig> = emptyList()
    private var apiKey: String = ""
    private val client = OkHttpClient()
    private var currentAudioTrack: AudioTrack? = null
    private var isSpeaking = false
    private val scope = CoroutineScope(Dispatchers.IO)
    
    init {
        loadTTSConfig()
        loadAPIKey()
    }
    
    private fun loadTTSConfig() {
        try {
            val inputStream = context.resources.openRawResource(
                context.resources.getIdentifier("tts_config", "raw", context.packageName)
            )
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(jsonString)
            val languagesArray = json.getJSONArray("languages")
            
            ttsConfigs = mutableListOf<TTSConfig>().apply {
                for (i in 0 until languagesArray.length()) {
                    val langObj = languagesArray.getJSONObject(i)
                    val googleTTSObj = langObj.getJSONObject("googleTTS")
                    val iosNativeObj = langObj.getJSONObject("iosNative")
                    
                    add(
                        TTSConfig(
                            code = langObj.getString("code"),
                            name = langObj.getString("name"),
                            googleTTS = GoogleTTSConfig(
                                voiceId = googleTTSObj.getString("voiceId"),
                                languageCode = googleTTSObj.getString("languageCode"),
                                ssmlGender = googleTTSObj.getString("ssmlGender")
                            ),
                            iosNative = IOSNativeConfig(
                                voiceIdentifier = iosNativeObj.getString("voiceIdentifier")
                            )
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun loadAPIKey() {
        try {
            val localPropertiesFile = File(context.filesDir.parent, "local.properties")
            if (localPropertiesFile.exists()) {
                val properties = Properties()
                FileInputStream(localPropertiesFile).use { properties.load(it) }
                apiKey = properties.getProperty("GOOGLE_TTS_API_KEY", "")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun getTTSConfigForLanguage(language: String): TTSConfig? {
        return ttsConfigs.find { it.code == language.lowercase() }
    }
    
    // KRİTİK: TTS Interrupt Mekanizması
    fun stop() {
        isSpeaking = false
        currentAudioTrack?.let { track ->
            try {
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    track.stop()
                }
                track.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        currentAudioTrack = null
    }
    
    suspend fun speak(text: String, language: String? = null): Boolean {
        // Mevcut sesi anında kes
        stop()
        
        val selectedLanguage = language ?: menuManager.getSelectedLanguage()
        val config = getTTSConfigForLanguage(selectedLanguage) ?: return false
        
        if (apiKey.isEmpty()) {
            return false
        }
        
        isSpeaking = true
        
        return try {
            // Google TTS API çağrısı
            val requestBody = JSONObject().apply {
                put("input", JSONObject().apply {
                    put("text", text)
                })
                put("voice", JSONObject().apply {
                    put("languageCode", config.googleTTS.languageCode)
                    put("name", config.googleTTS.voiceId)
                    put("ssmlGender", config.googleTTS.ssmlGender)
                })
                put("audioConfig", JSONObject().apply {
                    put("audioEncoding", "LINEAR16")
                    put("sampleRateHertz", 24000)
                })
            }.toString()
            
            val request = Request.Builder()
                .url("https://texttospeech.googleapis.com/v1/text:synthesize?key=$apiKey")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()
            
            val response = client.newCall(request).execute()
            
            if (response.isSuccessful && isSpeaking) {
                val responseBody = response.body?.string() ?: return false
                val audioContent = JSONObject(responseBody).getString("audioContent")
                val audioBytes = android.util.Base64.decode(audioContent, android.util.Base64.DEFAULT)
                
                // AudioTrack ile çal
                if (isSpeaking) {
                    playAudio(audioBytes)
                    true
                } else {
                    false
                }
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    fun speakAsync(text: String, language: String? = null, callback: ((Boolean) -> Unit)? = null) {
        scope.launch {
            val result = speak(text, language)
            callback?.invoke(result)
        }
    }
    
    private fun playAudio(audioBytes: ByteArray) {
        if (!isSpeaking) return
        
        try {
            val sampleRate = 24000
            val channelConfig = AudioFormat.CHANNEL_OUT_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val bufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            
            val audioAttributes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            } else {
                null
            }
            
            val audioFormatBuilder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(audioFormat)
                    .setChannelMask(channelConfig)
            } else {
                null
            }
            
            val track = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && audioAttributes != null && audioFormatBuilder != null) {
                AudioTrack.Builder()
                    .setAudioAttributes(audioAttributes)
                    .setAudioFormat(audioFormatBuilder.build())
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
            } else {
                AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSize,
                    AudioTrack.MODE_STREAM
                )
            }
            
            currentAudioTrack = track
            track.play()
            track.write(audioBytes, 0, audioBytes.size)
            
            // Ses bitene kadar bekle
            while (isSpeaking && track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                Thread.sleep(100)
            }
            
            if (isSpeaking) {
                track.stop()
            }
            track.release()
            currentAudioTrack = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    fun isSpeaking(): Boolean {
        return isSpeaking
    }
}

