package com.gormeengelliler.android.manager

class CancelHandler(
    private val ttsManager: TTSManager,
    private val menuManager: MenuManager
) {
    fun handleCancel() {
        // KRİTİK: TTS Interrupt - Mevcut sesi anında kes
        ttsManager.stop()
        
        // Menu navigasyonunu sıfırla (gerekirse)
        // Şu an için sadece TTS'i durduruyoruz
    }
}

