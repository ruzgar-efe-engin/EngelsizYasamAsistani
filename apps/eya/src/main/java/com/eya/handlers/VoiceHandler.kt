package com.eya.handlers

import android.content.Context
import com.eya.TTSManager

class VoiceHandler(private val context: Context) {
    private val ttsManager = TTSManager(context)
    
    fun handleVoiceSelection(subIndex: Int, language: String) {
        val voices = ttsManager.getVoices()
        if (subIndex < voices.size) {
            val voice = voices[subIndex]
            ttsManager.setVoice(voice.id)
            
            val text = if (language == "tr") {
                "Ben yapay zeka dil asistanınızım"
            } else {
                "I am your AI language assistant"
            }
            ttsManager.speak(text, language)
        }
    }
    
    fun handleVoiceRotate(subIndex: Int, language: String) {
        val voices = ttsManager.getVoices()
        if (subIndex < voices.size) {
            val voice = voices[subIndex]
            val text = if (language == "tr") {
                "Ben yapay zeka dil asistanınızım"
            } else {
                "I am your AI language assistant"
            }
            ttsManager.speak(text, language)
        }
    }
}

