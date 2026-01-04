package com.eya.utils

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

/**
 * Android SpeechRecognizer kullanarak ücretsiz STT
 */
class VoiceCommandManager(
    private val context: Context,
    private val onCommand: (String) -> Unit,
    private val onError: ((String) -> Unit)? = null
) {
    private var recognizer: SpeechRecognizer? = null
    private var isListening = false
    private var pendingResult: String? = null
    private var lastPartialResult: String? = null
    private var resultReceived = false

    fun startListening(language: String = "tr-TR") {
        if (isListening) {
            Log.w("VoiceCommand", "Zaten dinleniyor")
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.e("VoiceCommand", "SpeechRecognizer mevcut değil")
            onError?.invoke("Ses tanıma mevcut değil")
            return
        }

        // Önceki recognizer'ı temizle
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        pendingResult = null
        lastPartialResult = null
        resultReceived = false

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle) {
                Log.d("VoiceCommand", "Konuşmaya hazır")
                isListening = true
            }

            override fun onBeginningOfSpeech() {
                Log.d("VoiceCommand", "Konuşma başladı")
            }

            override fun onRmsChanged(rmsdB: Float) {
                // Ses seviyesi değişimi (opsiyonel)
            }

            override fun onBufferReceived(buffer: ByteArray) {
                // Buffer alındı (opsiyonel)
            }

            override fun onEndOfSpeech() {
                Log.d("VoiceCommand", "Konuşma bitti")
            }

            override fun onError(error: Int) {
                val errorMessage = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Ses hatası"
                    SpeechRecognizer.ERROR_CLIENT -> "İstemci hatası"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "İzin yetersiz"
                    SpeechRecognizer.ERROR_NETWORK -> "Ağ hatası"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Ağ zaman aşımı"
                    SpeechRecognizer.ERROR_NO_MATCH -> "Eşleşme bulunamadı"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Tanıyıcı meşgul"
                    SpeechRecognizer.ERROR_SERVER -> "Sunucu hatası"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Konuşma zaman aşımı"
                    else -> "Bilinmeyen hata: $error"
                }
                Log.e("VoiceCommand", "STT hatası: $errorMessage ($error)")
                resultReceived = true
                isListening = false
                onError?.invoke(errorMessage)
            }

            override fun onResults(results: Bundle) {
                val list = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val confidence = results.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
                
                list?.firstOrNull()?.let { text ->
                    val trimmedText = text.trim()
                    Log.d("VoiceCommand", "STT sonucu: '$trimmedText' (güven: ${confidence?.firstOrNull()})")
                    pendingResult = trimmedText
                    resultReceived = true
                    isListening = false
                    onCommand(trimmedText)
                } ?: run {
                    Log.w("VoiceCommand", "STT sonucu boş")
                    resultReceived = true
                    isListening = false
                    onError?.invoke("Ses anlaşılamadı")
                }
            }

            override fun onPartialResults(partialResults: Bundle) {
                val list = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                list?.firstOrNull()?.let { text ->
                    val trimmedText = text.trim()
                    if (trimmedText.isNotEmpty()) {
                        lastPartialResult = trimmedText
                        Log.d("VoiceCommand", "STT kısmi sonuç: '$trimmedText'")
                    }
                }
            }

            override fun onEvent(eventType: Int, params: Bundle) {
                // Event (opsiyonel)
            }
        })

        try {
            recognizer?.startListening(intent)
            Log.d("VoiceCommand", "STT dinleme başlatıldı")
        } catch (e: Exception) {
            Log.e("VoiceCommand", "STT başlatma hatası: ${e.message}", e)
            isListening = false
            onError?.invoke("STT başlatılamadı: ${e.message}")
        }
    }

    fun stop() {
        if (isListening) {
            try {
                // stopListening() çağrıldığında onResults gelecek
                recognizer?.stopListening()
                Log.d("VoiceCommand", "stopListening() çağrıldı, onResults bekleniyor...")
            } catch (e: Exception) {
                Log.e("VoiceCommand", "STT durdurma hatası: ${e.message}")
                isListening = false
            }
        } else {
            // Zaten dinlemiyorsa direkt temizle
            recognizer?.destroy()
            recognizer = null
            Log.d("VoiceCommand", "STT durduruldu (zaten dinlemiyordu)")
        }
    }
    
    /**
     * SpeechRecognizer'ı tamamen temizle (onResults geldikten sonra çağrılmalı)
     */
    fun cleanup() {
        recognizer?.destroy()
        recognizer = null
        isListening = false
        pendingResult = null
        lastPartialResult = null
        resultReceived = false
        Log.d("VoiceCommand", "STT temizlendi")
    }
    
    /**
     * Son alınan sonucu al (onResults'tan sonra)
     * Eğer onResults gelmediyse, en son kısmi sonucu döndür
     */
    fun getPendingResult(): String? {
        return pendingResult ?: lastPartialResult
    }

    fun isListening(): Boolean = isListening
}

