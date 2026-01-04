package com.eya.handlers

import com.eya.TTSManager

class AIHandler {
    
    fun handleGeminiChat(
        userMessage: String,
        ttsManager: TTSManager,
        language: String
    ) {
        // API key gerektiren servis kaldırıldı
        val errorMsg = if (language == "tr") {
            "Yapay zeka özelliği şu anda kullanılamıyor"
        } else {
            "AI feature is currently unavailable"
        }
        ttsManager.speak(errorMsg, language)
    }
    
    fun handleChatGPTChat(
        userMessage: String,
        ttsManager: TTSManager,
        language: String
    ) {
        // API key gerektiren servis kaldırıldı
        val errorMsg = if (language == "tr") {
            "Yapay zeka özelliği şu anda kullanılamıyor"
        } else {
            "AI feature is currently unavailable"
        }
        ttsManager.speak(errorMsg, language)
    }
    
    fun clearChatHistory() {
        // Artık history yok
    }
}
