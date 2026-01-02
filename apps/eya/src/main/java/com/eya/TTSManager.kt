package com.eya

import android.content.Context
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.util.Log
import com.eya.utils.GeminiTTSClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

class TTSManager(private val context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var ready = false
    private val voices: List<VoiceInfo>
    private var currentVoiceId: String
    private val geminiTTSClient = GeminiTTSClient()
    private val scope = CoroutineScope(Dispatchers.Main)
    private var useGeminiTTS = false

    data class VoiceInfo(val id: String, val name: String, val language: String, val provider: String? = null, val gender: String? = null)

    init {
        tts = TextToSpeech(context, this)
        val input = context.resources.openRawResource(R.raw.tts_voices)
        val text = input.bufferedReader().use { it.readText() }
        val obj = JSONObject(text)
        val arr = obj.getJSONArray("voices")
        val list = mutableListOf<VoiceInfo>()
        for (i in 0 until arr.length()) {
            val v = arr.getJSONObject(i)
            list.add(
                VoiceInfo(
                    id = v.getString("id"),
                    name = v.getString("name"),
                    language = v.getString("language"),
                    provider = v.optString("provider", null),
                    gender = v.optString("gender", null)
                )
            )
        }
        voices = list
        currentVoiceId = obj.optString("defaultVoiceId", voices.firstOrNull()?.id ?: "default")
        // İlk başta Android TTS kullan (Gemini TTS opsiyonel)
        useGeminiTTS = false
    }

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        Log.d("TTSManager", "TTS hazır mı: $ready")
        if (!ready) {
            Log.e("TTSManager", "TTS başlatılamadı, status: $status")
        }
    }

    fun setVoice(voiceId: String) {
        currentVoiceId = voiceId
        val voice = voices.firstOrNull { it.id == voiceId }
        useGeminiTTS = voice?.provider == "gemini"
        Log.d("TTSManager", "Ses set edildi: $voiceId, Gemini TTS: $useGeminiTTS")
    }

    fun getVoices(): List<VoiceInfo> = voices

    fun speak(text: String, language: String) {
        Log.d("TTSManager", "speak çağrıldı: '$text', Gemini TTS: $useGeminiTTS, ready: $ready")
        
        // Önce her zaman Android TTS ile konuş (hızlı ve güvenilir)
        speakWithAndroidTTS(text, language)
        
        // Eğer Gemini TTS seçilmişse, arka planda Gemini TTS'i de dene (opsiyonel)
        if (useGeminiTTS) {
            scope.launch {
                try {
                    val audioData = geminiTTSClient.synthesizeSpeech(text, currentVoiceId, language)
                    if (audioData != null && audioData.isNotEmpty()) {
                        // Geçici dosyaya kaydet ve çal
                        val tempFile = File(context.cacheDir, "tts_${System.currentTimeMillis()}.mp3")
                        FileOutputStream(tempFile).use { it.write(audioData) }
                        
                        val mediaPlayer = MediaPlayer()
                        mediaPlayer.setDataSource(tempFile.absolutePath)
                        mediaPlayer.prepare()
                        mediaPlayer.setOnCompletionListener {
                            it.release()
                            tempFile.delete()
                        }
                        mediaPlayer.start()
                        Log.d("TTSManager", "Gemini TTS başarılı")
                    }
                } catch (e: Exception) {
                    Log.e("TTSManager", "Gemini TTS hatası: ${e.message}")
                }
            }
        }
    }
    
    private fun speakWithAndroidTTS(text: String, language: String) {
        if (!ready) {
            Log.e("TTSManager", "TTS hazır değil")
            return
        }
        val locale = if (language == "en") Locale.US else Locale("tr", "TR")
        tts?.language = locale
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts-${System.currentTimeMillis()}")
    }

    fun shutdown() {
        tts?.shutdown()
    }
}

