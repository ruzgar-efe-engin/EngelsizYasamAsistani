package com.gormeengelliler.android.manager

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import com.gormeengelliler.android.manager.MenuManager
import com.gormeengelliler.android.manager.EventLogManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.json.JSONArray
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
    private var geminiApiKey: String = ""
    private val client = OkHttpClient()
    private var currentAudioTrack: AudioTrack? = null
    private var isSpeaking = false
    private val scope = CoroutineScope(Dispatchers.IO)
    
    init {
        loadTTSConfig()
        loadAPIKey()
        loadGeminiAPIKey()
    }
    
    private fun loadTTSConfig() {
        try {
            android.util.Log.d("TTSManager", "📦 TTS config yükleniyor...")
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
                    
                    val config = TTSConfig(
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
                    add(config)
                    android.util.Log.d("TTSManager", "✅ TTS config yüklendi: ${config.code} - ${config.googleTTS.voiceId}")
                }
            }
            android.util.Log.d("TTSManager", "✅ Toplam ${ttsConfigs.size} dil config'i yüklendi")
        } catch (e: Exception) {
            android.util.Log.e("TTSManager", "❌ TTS config yükleme hatası: ${e.message}", e)
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
                if (apiKey.isNotEmpty()) {
                    android.util.Log.d("TTSManager", "✅ Google TTS API key yüklendi")
                } else {
                    android.util.Log.w("TTSManager", "⚠️ Google TTS API key bulunamadı")
                }
            } else {
                android.util.Log.w("TTSManager", "⚠️ local.properties dosyası bulunamadı")
            }
        } catch (e: Exception) {
            android.util.Log.e("TTSManager", "❌ API key yükleme hatası: ${e.message}", e)
            e.printStackTrace()
        }
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
                    android.util.Log.d("TTSManager", "⚠️ Gemini API key bulunamadı, varsayılan key kullanılıyor")
                } else {
                    android.util.Log.d("TTSManager", "✅ Gemini API key yüklendi")
                }
            } else {
                // Dosya yoksa varsayılan key kullan
                geminiApiKey = "AIzaSyBiR3IYsBKQnlZ1G2XvQ8g2fIKIP30GhwI"
                android.util.Log.d("TTSManager", "⚠️ local.properties bulunamadı, varsayılan Gemini API key kullanılıyor")
            }
        } catch (e: Exception) {
            android.util.Log.e("TTSManager", "❌ Gemini API key yükleme hatası: ${e.message}", e)
            e.printStackTrace()
            // Hata durumunda varsayılan key
            geminiApiKey = "AIzaSyBiR3IYsBKQnlZ1G2XvQ8g2fIKIP30GhwI"
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
        android.util.Log.d("TTSManager", "🔊 speak çağrıldı: text='$text', language=$language")
        EventLogManager.logTTS("speak başladı", "text='${text.take(50)}${if (text.length > 50) "..." else ""}', language=$language")
        
        try {
            // Mevcut sesi anında kes (ama isSpeaking'i false yapma, çünkü yeni ses başlayacak)
            EventLogManager.logTTS("Mevcut ses kontrol ediliyor", "currentAudioTrack=${currentAudioTrack != null}")
            currentAudioTrack?.let { track ->
                try {
                    if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                        track.stop()
                        EventLogManager.logTTS("Mevcut ses durduruldu", "playState=PLAYING")
                    } else {
                        EventLogManager.logTTS("Mevcut ses zaten durmuş", "playState=${track.playState}")
                    }
                    track.release()
                    EventLogManager.logTTS("Mevcut AudioTrack release edildi")
                } catch (e: Exception) {
                    android.util.Log.e("TTSManager", "❌ stop() hatası: ${e.message}", e)
                    EventLogManager.logTTS("Mevcut ses durdurma hatası", e.message ?: "Bilinmeyen hata", true)
                }
            } ?: run {
                EventLogManager.logTTS("Mevcut ses yok", "currentAudioTrack=null")
            }
            currentAudioTrack = null
            
            val selectedLanguage = language ?: menuManager.getSelectedLanguage()
            android.util.Log.d("TTSManager", "📝 Seçili dil: $selectedLanguage")
            EventLogManager.logTTS("Dil seçildi", "language=$selectedLanguage")
        
            // Önce Gemini TTS dene
            EventLogManager.logTTS("Gemini API key kontrol ediliyor", "geminiApiKey.isEmpty()=${geminiApiKey.isEmpty()}")
            if (geminiApiKey.isNotEmpty()) {
                android.util.Log.d("TTSManager", "🤖 Gemini TTS deneniyor...")
                EventLogManager.logTTS("Gemini TTS deneniyor", "text='${text.take(30)}...'")
                val geminiResult = tryGeminiTTS(text, selectedLanguage)
                EventLogManager.logTTS("Gemini TTS sonucu", "result=$geminiResult")
                if (geminiResult) {
                    android.util.Log.d("TTSManager", "✅ Gemini TTS başarılı")
                    EventLogManager.logTTS("Gemini TTS başarılı", "playAudio çağrıldı")
                    return true
                }
                android.util.Log.d("TTSManager", "⚠️ Gemini TTS başarısız, Google TTS'e geçiliyor")
                EventLogManager.logTTS("Gemini TTS başarısız", "Google TTS'e geçiliyor")
            } else {
                android.util.Log.d("TTSManager", "⚠️ Gemini API key yok, Google TTS kullanılıyor")
                EventLogManager.logTTS("Gemini API key yok", "Google TTS kullanılıyor")
            }
        
            // Gemini TTS başarısız olursa Google TTS'e fallback
            EventLogManager.logTTS("Google TTS config alınıyor", "language=$selectedLanguage")
            val config = getTTSConfigForLanguage(selectedLanguage)
            if (config == null) {
                android.util.Log.e("TTSManager", "❌ TTS config bulunamadı: $selectedLanguage")
                EventLogManager.logTTS("TTS config bulunamadı", "language=$selectedLanguage", true)
                isSpeaking = false
                return false
            }
            
            android.util.Log.d("TTSManager", "🔊 Google TTS kullanılıyor: ${config.googleTTS.voiceId}")
            EventLogManager.logTTS("Google TTS kullanılıyor", "voiceId=${config.googleTTS.voiceId}, languageCode=${config.googleTTS.languageCode}")
            
            EventLogManager.logTTS("Google TTS API key kontrol ediliyor", "apiKey.isEmpty()=${apiKey.isEmpty()}")
            if (apiKey.isEmpty()) {
                android.util.Log.e("TTSManager", "❌ Google TTS API key yok")
                EventLogManager.logTTS("Google TTS API key yok", "", true)
                isSpeaking = false
                return false
            }
            
            isSpeaking = true
            EventLogManager.logTTS("isSpeaking=true yapıldı", "Google TTS API çağrısı başlatılıyor")
        
            return try {
                // Google TTS API çağrısı
                EventLogManager.logTTS("Google TTS request body oluşturuluyor", "text length=${text.length}")
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
                
                android.util.Log.d("TTSManager", "📤 Google TTS API çağrısı yapılıyor...")
                EventLogManager.logTTS("Google TTS API çağrısı", "text='${text.take(30)}...', voiceId=${config.googleTTS.voiceId}")
                val request = Request.Builder()
                    .url("https://texttospeech.googleapis.com/v1/text:synthesize?key=$apiKey")
                    .post(requestBody.toRequestBody("application/json".toMediaType()))
                    .build()
                
                EventLogManager.logTTS("Google TTS API request gönderiliyor", "execute() çağrılıyor")
                val response = client.newCall(request).execute()
                EventLogManager.logTTS("Google TTS API response alındı", "isSuccessful=${response.isSuccessful}, code=${response.code}, isSpeaking=$isSpeaking")
                
                if (response.isSuccessful && isSpeaking) {
                    val responseBody = response.body?.string() ?: run {
                        EventLogManager.logTTS("Google TTS response body null", "", true)
                        isSpeaking = false
                        return false
                    }
                    EventLogManager.logTTS("Google TTS response body parse ediliyor", "responseBody length=${responseBody.length}")
                    val audioContent = JSONObject(responseBody).getString("audioContent")
                    val audioBytes = android.util.Base64.decode(audioContent, android.util.Base64.DEFAULT)
                    
                    android.util.Log.d("TTSManager", "✅ Google TTS API başarılı, ${audioBytes.size} bytes alındı")
                    EventLogManager.logTTS("Google TTS API başarılı", "${audioBytes.size} bytes alındı, playAudio() çağrılıyor")
                    
                    // AudioTrack ile çal
                    if (isSpeaking) {
                        playAudio(audioBytes)
                        true
                    } else {
                        android.util.Log.w("TTSManager", "⚠️ isSpeaking=false, ses çalınmadı")
                        EventLogManager.logTTS("isSpeaking=false", "ses çalınmadı", true)
                        false
                    }
                } else {
                    android.util.Log.e("TTSManager", "❌ Google TTS API hatası: ${response.code}, isSpeaking=$isSpeaking")
                    EventLogManager.logTTS("Google TTS API hatası", "code=${response.code}, isSpeaking=$isSpeaking", true)
                    isSpeaking = false
                    false
                }
            } catch (e: Exception) {
                android.util.Log.e("TTSManager", "❌ speak() hatası: ${e.message}", e)
                EventLogManager.logTTS("speak() exception", "${e.message}", true)
                e.printStackTrace()
                isSpeaking = false
                return false
            }
        } catch (e: Exception) {
            android.util.Log.e("TTSManager", "❌ speak() dış try-catch hatası: ${e.message}", e)
            EventLogManager.logTTS("speak() dış exception", "${e.message}", true)
            isSpeaking = false
            return false
        }
    }
    
    private suspend fun tryGeminiTTS(text: String, language: String): Boolean {
        EventLogManager.logTTS("tryGeminiTTS başladı", "text='${text.take(30)}...', language=$language")
        if (geminiApiKey.isEmpty()) {
            android.util.Log.d("TTSManager", "⚠️ tryGeminiTTS: API key yok")
            EventLogManager.logTTS("tryGeminiTTS API key yok", "", true)
            return false
        }
        
        android.util.Log.d("TTSManager", "🤖 tryGeminiTTS: Gemini TTS deneniyor, text='$text'")
        isSpeaking = true
        EventLogManager.logTTS("isSpeaking=true yapıldı (Gemini)", "Gemini TTS API çağrısı başlatılıyor")
        
        return try {
            // Gemini TTS API çağrısı (gemini-2.5-flash-tts)
            EventLogManager.logTTS("Gemini TTS request body oluşturuluyor", "text length=${text.length}")
            val requestBody = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", text)
                            })
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("responseModalities", JSONArray().apply {
                        put("AUDIO")
                    })
                    put("audioConfig", JSONObject().apply {
                        put("speakingRate", 1.0)
                        put("pitch", 0.0)
                    })
                })
            }.toString()
            
            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-tts:generateContent?key=$geminiApiKey")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()
            
            android.util.Log.d("TTSManager", "📤 Gemini TTS API çağrısı yapılıyor...")
            EventLogManager.logTTS("Gemini TTS API çağrısı", "text='${text.take(30)}...', execute() çağrılıyor")
            val response = client.newCall(request).execute()
            EventLogManager.logTTS("Gemini TTS API response alındı", "isSuccessful=${response.isSuccessful}, code=${response.code}, isSpeaking=$isSpeaking")
            
            if (response.isSuccessful && isSpeaking) {
                val responseBody = response.body?.string() ?: run {
                    EventLogManager.logTTS("Gemini TTS response body null", "", true)
                    isSpeaking = false
                    return false
                }
                val jsonResponse = JSONObject(responseBody)
                
                android.util.Log.d("TTSManager", "✅ Gemini TTS API başarılı, response parse ediliyor...")
                EventLogManager.logTTS("Gemini TTS API başarılı", "response parse ediliyor, responseBody length=${responseBody.length}")
                
                // Gemini TTS response formatı: candidates[0].content.parts[0].inlineData.data
                val candidates = jsonResponse.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val candidate = candidates.getJSONObject(0)
                    val content = candidate.optJSONObject("content")
                    if (content != null) {
                        val parts = content.optJSONArray("parts")
                        if (parts != null && parts.length() > 0) {
                            val part = parts.getJSONObject(0)
                            val inlineData = part.optJSONObject("inlineData")
                            if (inlineData != null) {
                                val audioContent = inlineData.optString("data", "")
                                if (audioContent.isNotEmpty()) {
                                    val audioBytes = android.util.Base64.decode(audioContent, android.util.Base64.DEFAULT)
                                    android.util.Log.d("TTSManager", "✅ Gemini TTS audio alındı, ${audioBytes.size} bytes")
                                    EventLogManager.logTTS("Gemini TTS audio alındı", "${audioBytes.size} bytes, isSpeaking=$isSpeaking, playAudio() çağrılıyor")
                                    if (isSpeaking) {
                                        playAudio(audioBytes)
                                        return true
                                    } else {
                                        EventLogManager.logTTS("Gemini TTS: isSpeaking=false", "playAudio çağrılmadı", true)
                                        return false
                                    }
                                } else {
                                    android.util.Log.w("TTSManager", "⚠️ Gemini TTS: inlineData.data boş")
                                    EventLogManager.logTTS("Gemini TTS inlineData.data boş", "", true)
                                }
                            } else {
                                android.util.Log.w("TTSManager", "⚠️ Gemini TTS: inlineData bulunamadı")
                                EventLogManager.logTTS("Gemini TTS inlineData bulunamadı", "", true)
                            }
                        } else {
                            android.util.Log.w("TTSManager", "⚠️ Gemini TTS: parts bulunamadı")
                            EventLogManager.logTTS("Gemini TTS parts bulunamadı", "", true)
                        }
                    } else {
                        android.util.Log.w("TTSManager", "⚠️ Gemini TTS: content bulunamadı")
                        EventLogManager.logTTS("Gemini TTS content bulunamadı", "", true)
                    }
                } else {
                    android.util.Log.w("TTSManager", "⚠️ Gemini TTS: candidates bulunamadı")
                    EventLogManager.logTTS("Gemini TTS candidates bulunamadı", "", true)
                }
            } else {
                android.util.Log.e("TTSManager", "❌ Gemini TTS API hatası: ${response.code}, isSpeaking=$isSpeaking")
                EventLogManager.logTTS("Gemini TTS API hatası", "code=${response.code}, isSpeaking=$isSpeaking", true)
            }
            isSpeaking = false
            false
        } catch (e: Exception) {
            android.util.Log.e("TTSManager", "❌ tryGeminiTTS() hatası: ${e.message}", e)
            EventLogManager.logTTS("tryGeminiTTS() exception", "${e.message}", true)
            e.printStackTrace()
            isSpeaking = false
            false
        }
    }
    
    fun speakAsync(text: String, language: String? = null, callback: ((Boolean) -> Unit)? = null) {
        android.util.Log.d("TTSManager", "🔊 speakAsync çağrıldı: text='$text', language=$language")
        EventLogManager.logTTS("speakAsync çağrıldı", "text='${text.take(50)}${if (text.length > 50) "..." else ""}', language=$language")
        scope.launch {
            try {
                EventLogManager.logTTS("Coroutine başladı", "speak() çağrılıyor")
                val result = speak(text, language)
                android.util.Log.d("TTSManager", "✅ speakAsync tamamlandı: result=$result")
                EventLogManager.logTTS("speakAsync tamamlandı", "result=$result")
                callback?.invoke(result)
            } catch (e: Exception) {
                android.util.Log.e("TTSManager", "❌ speakAsync coroutine hatası: ${e.message}", e)
                EventLogManager.logTTS("speakAsync coroutine hatası", "${e.message}", true)
                e.printStackTrace()
                callback?.invoke(false)
            }
        }
    }
    
    private suspend fun playAudio(audioBytes: ByteArray) {
        if (!isSpeaking) {
            android.util.Log.d("TTSManager", "❌ playAudio: isSpeaking=false, çıkılıyor")
            EventLogManager.logTTS("playAudio iptal", "isSpeaking=false", true)
            return
        }
        
        try {
            android.util.Log.d("TTSManager", "🔊 playAudio: Ses çalınıyor, ${audioBytes.size} bytes")
            EventLogManager.logTTS("playAudio başladı", "${audioBytes.size} bytes")
            
            val sampleRate = 24000
            val channelConfig = AudioFormat.CHANNEL_OUT_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val bufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            EventLogManager.logTTS("AudioTrack parametreleri", "sampleRate=$sampleRate, bufferSize=$bufferSize")
            
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
            
            // MODE_STATIC kullan - tüm data bir seferde yazılıyor
            val track = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && audioAttributes != null && audioFormatBuilder != null) {
                AudioTrack.Builder()
                    .setAudioAttributes(audioAttributes)
                    .setAudioFormat(audioFormatBuilder.build())
                    .setBufferSizeInBytes(audioBytes.size.coerceAtLeast(bufferSize))
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
            } else {
                AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    audioBytes.size.coerceAtLeast(bufferSize),
                    AudioTrack.MODE_STATIC
                )
            }
            
            currentAudioTrack = track
            EventLogManager.logTTS("AudioTrack oluşturuldu", "MODE_STATIC")
            
            // MODE_STATIC: Önce write, sonra play
            val writeResult = track.write(audioBytes, 0, audioBytes.size, AudioTrack.WRITE_BLOCKING)
            if (writeResult < 0) {
                android.util.Log.e("TTSManager", "❌ playAudio: write() başarısız: $writeResult")
                EventLogManager.logTTS("AudioTrack write() başarısız", "result=$writeResult", true)
                track.release()
                currentAudioTrack = null
                return
            }
            
            android.util.Log.d("TTSManager", "✅ playAudio: ${writeResult} bytes yazıldı, play() çağrılıyor")
            EventLogManager.logTTS("Audio yazıldı", "$writeResult bytes, play() çağrılıyor")
            track.play()
            EventLogManager.logTTS("Audio çalınıyor", "play() çağrıldı")
            
            // Ses bitene kadar bekle (coroutine delay kullan)
            while (isSpeaking && track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                delay(50)
            }
            
            if (isSpeaking) {
                android.util.Log.d("TTSManager", "✅ playAudio: Ses çalma tamamlandı")
                EventLogManager.logTTS("Ses çalma tamamlandı", "track.stop() çağrılıyor")
                track.stop()
            } else {
                android.util.Log.d("TTSManager", "⚠️ playAudio: Ses kesildi (isSpeaking=false)")
                EventLogManager.logTTS("Ses kesildi", "isSpeaking=false")
            }
            
            track.release()
            currentAudioTrack = null
            EventLogManager.logTTS("AudioTrack temizlendi", "track.release()")
        } catch (e: Exception) {
            android.util.Log.e("TTSManager", "❌ playAudio hatası: ${e.message}", e)
            EventLogManager.logTTS("playAudio hatası", "${e.message}", true)
            e.printStackTrace()
        }
    }
    
    fun isSpeaking(): Boolean {
        return isSpeaking
    }
}

