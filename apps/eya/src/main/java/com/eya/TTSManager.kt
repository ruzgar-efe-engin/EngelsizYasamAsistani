package com.eya

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import org.json.JSONObject
import java.util.Locale

class TTSManager(private val context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var ready = false
    private val voices: List<VoiceInfo>
    private var currentVoiceId: String

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
    }

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        Log.d("TTSManager", "Android TTS hazır mı: $ready")
        if (!ready) {
            Log.e("TTSManager", "TTS başlatılamadı, status: $status")
        } else {
            // Android TTS seslerini listele
            val availableVoices = tts?.voices
            Log.d("TTSManager", "Mevcut Android TTS sesleri: ${availableVoices?.size ?: 0}")
        }
    }

    fun setVoice(voiceId: String) {
        currentVoiceId = voiceId
        Log.d("TTSManager", "Ses set edildi: $voiceId (Android TTS)")
        
        // Android TTS'te ses seçimi (eğer voiceId bir Android TTS voice ID'si ise)
        if (ready) {
            val availableVoices = tts?.voices
            val selectedVoice = availableVoices?.firstOrNull { it.name == voiceId || it.locale.toString() == voiceId }
            if (selectedVoice != null) {
                tts?.voice = selectedVoice
                Log.d("TTSManager", "Android TTS sesi seçildi: ${selectedVoice.name}")
            }
        }
    }

    fun getVoices(): List<VoiceInfo> = voices

    fun speak(text: String, language: String) {
        if (!ready) {
            Log.e("TTSManager", "TTS hazır değil")
            return
        }
        
        Log.d("TTSManager", "Android TTS ile konuşuluyor: '$text'")
        val locale = if (language == "en") Locale.US else Locale("tr", "TR")
        tts?.language = locale
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts-${System.currentTimeMillis()}")
    }

    fun shutdown() {
        tts?.shutdown()
    }
}

