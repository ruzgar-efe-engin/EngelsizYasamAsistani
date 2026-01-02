package com.eya.handlers

import android.media.MediaPlayer
import com.eya.TTSManager

class RadioHandler {
    private var mediaPlayer: MediaPlayer? = null
    
    // Radyo stream URL'leri (düzeltilmiş)
    private val radioStreams = mapOf(
        0 to "https://radio-trt-fm.live.trt.com.tr/master.m3u8", // TRT FM
        1 to "https://listen.powerapp.com.tr/powerfm/mpeg/icecast.audio", // Power FM
        2 to "https://stream.live.vc.bbcmedia.co.uk/bbc_radio_one.m3u8", // BBC Radio 1
        3 to "https://npr-ice.streamguys1.com/live.mp3" // NPR
    )
    
    fun isPlaying(): Boolean {
        return mediaPlayer?.isPlaying ?: false
    }
    
    private val radioNames = mapOf(
        0 to "TRT FM",
        1 to "Power FM",
        2 to "BBC Radio 1",
        3 to "NPR"
    )
    
    fun playRadio(
        subIndex: Int,
        ttsManager: TTSManager,
        language: String,
        onError: (String) -> Unit
    ) {
        stopRadio()
        
        val streamUrl = radioStreams[subIndex]
        val radioName = radioNames[subIndex] ?: "Radio"
        
        if (streamUrl == null) {
            val errorMsg = if (language == "tr") {
                "Radyo bulunamadı"
            } else {
                "Radio not found"
            }
            onError(errorMsg)
            return
        }
        
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(streamUrl)
                prepareAsync()
                setOnPreparedListener {
                    start()
                    val text = if (language == "tr") {
                        "$radioName açılıyor"
                    } else {
                        "Opening $radioName"
                    }
                    ttsManager.speak(text, language)
                }
                setOnErrorListener { _, _, _ ->
                    val errorMsg = if (language == "tr") {
                        "Radyo açılamadı"
                    } else {
                        "Could not open radio"
                    }
                    onError(errorMsg)
                    true
                }
            }
        } catch (e: Exception) {
            val errorMsg = if (language == "tr") {
                "Radyo açılırken hata oluştu"
            } else {
                "Error opening radio"
            }
            onError(errorMsg)
        }
    }
    
    fun stopRadio() {
        mediaPlayer?.apply {
            if (isPlaying) {
                stop()
            }
            release()
        }
        mediaPlayer = null
    }
}

