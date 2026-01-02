package com.eya.handlers

import com.eya.TTSManager
import com.eya.utils.GeminiClient
import com.eya.utils.OpenAIClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AIHandler {
    private val geminiClient = GeminiClient()
    private val openAIClient = OpenAIClient()
    private val scope = CoroutineScope(Dispatchers.Main)
    
    fun handleGeminiChat(
        userMessage: String,
        ttsManager: TTSManager,
        language: String
    ) {
        scope.launch {
            try {
                val response = geminiClient.generateContent(userMessage, language)
                if (response != null && response.isNotEmpty()) {
                    ttsManager.speak(response, language)
                } else {
                    val errorMsg = if (language == "tr") {
                        "Yanıt alınamadı"
                    } else {
                        "Could not get response"
                    }
                    ttsManager.speak(errorMsg, language)
                }
            } catch (e: Exception) {
                val errorMsg = if (language == "tr") {
                    "Hata oluştu"
                } else {
                    "An error occurred"
                }
                ttsManager.speak(errorMsg, language)
            }
        }
    }
    
    fun handleChatGPTChat(
        userMessage: String,
        ttsManager: TTSManager,
        language: String
    ) {
        scope.launch {
            try {
                val response = openAIClient.chat(userMessage, language)
                if (response != null && response.isNotEmpty()) {
                    ttsManager.speak(response, language)
                } else {
                    val errorMsg = if (language == "tr") {
                        "Yanıt alınamadı"
                    } else {
                        "Could not get response"
                    }
                    ttsManager.speak(errorMsg, language)
                }
            } catch (e: Exception) {
                val errorMsg = if (language == "tr") {
                    "Hata oluştu"
                } else {
                    "An error occurred"
                }
                ttsManager.speak(errorMsg, language)
            }
        }
    }
    
    fun clearChatHistory() {
        openAIClient.clearHistory()
    }
}

