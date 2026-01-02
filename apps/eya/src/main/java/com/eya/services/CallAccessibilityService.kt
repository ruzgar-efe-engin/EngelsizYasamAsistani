package com.eya.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.util.Log

class CallAccessibilityService : AccessibilityService() {
    
    private var lastYouTubeMusicEventTime = 0L
    private var youtubeMusicPlayAttempted = false
    
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            val packageName = event.packageName?.toString() ?: return
            val className = event.className?.toString() ?: ""
            
            Log.d("CallAccessibility", "Event: package=$packageName, class=$className")
            
            // YouTube Music kontrolü
            if (packageName == "com.google.android.apps.youtube.music") {
                val currentTime = System.currentTimeMillis()
                // Her 2 saniyede bir kontrol et
                if (currentTime - lastYouTubeMusicEventTime > 2000) {
                    lastYouTubeMusicEventTime = currentTime
                    Log.d("CallAccessibility", "YouTube Music bulundu! Play butonunu arıyor...")
                    // Arama sonuçları yüklendikten sonra play butonuna tıkla - birden fazla deneme
                    if (!youtubeMusicPlayAttempted) {
                        // İlk deneme: 2 saniye sonra
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            performYouTubeMusicPlayAction()
                        }, 2000)
                        
                        // İkinci deneme: 4 saniye sonra
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            performYouTubeMusicPlayAction()
                        }, 4000)
                        
                        // Üçüncü deneme: 6 saniye sonra
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            performYouTubeMusicPlayAction()
                        }, 6000)
                        
                        youtubeMusicPlayAttempted = true
                        // 15 saniye sonra flag'i sıfırla (yeni arama için)
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            youtubeMusicPlayAttempted = false
                        }, 15000)
                    }
                }
                return
            }
            
            // Tüm dialer paket isimlerini kontrol et
            val dialerPackages = listOf(
                "dialer", "phone", "com.android.phone", "com.android.dialer",
                "com.google.android.dialer", "com.samsung.android.dialer",
                "com.huawei.android.dialer", "com.miui.dialer", "com.coloros.dialer",
                "com.oppo.dialer", "com.vivo.dialer", "com.oneplus.dialer"
            )
            
            val isDialer = dialerPackages.any { packageName.contains(it, ignoreCase = true) } ||
                          className.contains("Dialer", ignoreCase = true) ||
                          className.contains("InCall", ignoreCase = true) ||
                          className.contains("Dialpad", ignoreCase = true) ||
                          className.contains("Call", ignoreCase = true)
            
            if (isDialer) {
                Log.d("CallAccessibility", "Dialer bulundu! Ara butonunu arıyor...")
                // Kısa bir gecikme sonrası "Ara" butonunu bul ve tıkla
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    performCallAction()
                }, 2000) // 2 saniye bekle (dialer'ın tam yüklenmesi için)
            }
        }
    }
    
    private fun performCallAction() {
        val rootNode = rootInActiveWindow ?: run {
            Log.w("CallAccessibility", "rootInActiveWindow null")
            return
        }
        
        try {
            Log.d("CallAccessibility", "Ara butonu aranıyor...")
            val callButtons = mutableListOf<AccessibilityNodeInfo>()
            
            // Önce metinle arama yap
            val searchTexts = listOf("Ara", "Call", "Çağır", "Arama", "Dial", "Çevir", "Calling", "Dialing")
            for (text in searchTexts) {
                val nodes = rootNode.findAccessibilityNodeInfosByText(text)
                callButtons.addAll(nodes)
                Log.d("CallAccessibility", "'$text' ile ${nodes.size} buton bulundu")
            }
            
            // Tüm node'ları tarayarak daha agresif arama
            val allNodes = mutableListOf<AccessibilityNodeInfo>()
            collectAllNodes(rootNode, allNodes)
            Log.d("CallAccessibility", "Toplam ${allNodes.size} node bulundu")
            
            for (node in allNodes) {
                val text = node.text?.toString()?.lowercase() ?: ""
                val contentDesc = node.contentDescription?.toString()?.lowercase() ?: ""
                val viewId = node.viewIdResourceName?.lowercase() ?: ""
                val className = node.className?.toString()?.lowercase() ?: ""
                
                // Daha geniş arama kriterleri
                val isCallButton = (text.contains("ara") || text.contains("call") || text.contains("dial") ||
                                   contentDesc.contains("ara") || contentDesc.contains("call") || contentDesc.contains("dial") ||
                                   viewId.contains("call") || viewId.contains("dial") || viewId.contains("button") ||
                                   className.contains("button")) &&
                                  node.isClickable && node.isEnabled
                
                if (isCallButton && !callButtons.contains(node)) {
                    callButtons.add(node)
                    Log.d("CallAccessibility", "Buton bulundu: text='$text', desc='$contentDesc', id='$viewId'")
                }
            }
            
            Log.d("CallAccessibility", "Toplam ${callButtons.size} tıklanabilir buton bulundu")
            
            // En büyük veya en görünür butonu bul (genellikle en büyük buton "Ara" butonudur)
            var bestButton: AccessibilityNodeInfo? = null
            var maxArea = 0
            
            for (button in callButtons) {
                if (button.isClickable && button.isEnabled) {
                    val bounds = android.graphics.Rect()
                    button.getBoundsInScreen(bounds)
                    val area = bounds.width() * bounds.height()
                    
                    if (area > maxArea) {
                        maxArea = area
                        bestButton = button
                    }
                }
            }
            
            // En iyi butonu tıkla
            bestButton?.let { button ->
                val success = button.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (success) {
                    Log.d("CallAccessibility", "Ara butonuna başarıyla tıklandı!")
                } else {
                    Log.w("CallAccessibility", "Ara butonuna tıklama başarısız")
                }
            } ?: run {
                Log.w("CallAccessibility", "Hiç buton bulunamadı, tüm tıklanabilir butonları deniyor...")
                // Son çare: tüm tıklanabilir butonları dene
                for (button in callButtons) {
                    if (button.isClickable && button.isEnabled) {
                        button.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        Log.d("CallAccessibility", "Buton tıklandı (son çare)")
                        break
                    }
                }
            }
            
            // Butonları temizle
            for (button in callButtons) {
                if (button != bestButton) {
                    button.recycle()
                }
            }
            bestButton?.recycle()
        } catch (e: Exception) {
            Log.e("CallAccessibility", "Ara butonuna tıklanırken hata: ${e.message}", e)
        } finally {
            rootNode.recycle()
        }
    }
    
    private fun collectAllNodes(node: AccessibilityNodeInfo, nodes: MutableList<AccessibilityNodeInfo>) {
        nodes.add(node)
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                collectAllNodes(child, nodes)
                child.recycle()
            }
        }
    }
    
    private fun performYouTubeMusicPlayAction() {
        val rootNode = rootInActiveWindow ?: run {
            Log.w("CallAccessibility", "rootInActiveWindow null (YouTube Music)")
            return
        }
        
        try {
            Log.d("CallAccessibility", "YouTube Music Play butonu aranıyor...")
            
            // Tüm node'ları topla
            val allNodes = mutableListOf<AccessibilityNodeInfo>()
            collectAllNodes(rootNode, allNodes)
            Log.d("CallAccessibility", "Toplam ${allNodes.size} node bulundu")
            
            // YouTube Music'te play butonunu veya ilk arama sonucunu bul
            val playButtonTexts = listOf("Play", "Oynat", "Çal", "Başlat", "▶", "▶️", "Oynatma", "Playback")
            val playKeywords = listOf("play", "oynat", "çal", "başlat", "start", "resume")
            
            // 1. Önce text ile ara
            for (text in playButtonTexts) {
                val playButtons = rootNode.findAccessibilityNodeInfosByText(text)
                for (button in playButtons) {
                    if (button.isClickable && button.isEnabled) {
                        Log.d("CallAccessibility", "YouTube Music Play butonu bulundu (text): $text")
                        button.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        playButtons.forEach { if (it != button) it.recycle() }
                        button.recycle()
                        allNodes.forEach { it.recycle() }
                        return
                    }
                }
                playButtons.forEach { it.recycle() }
            }
            
            // 2. Content description ve text ile ara (tüm node'larda)
            var bestPlayButton: AccessibilityNodeInfo? = null
            var maxPlayScore = 0
            
            for (node in allNodes) {
                if (!node.isClickable || !node.isEnabled) continue
                
                val contentDesc = node.contentDescription?.toString()?.lowercase() ?: ""
                val text = node.text?.toString()?.lowercase() ?: ""
                val viewId = node.viewIdResourceName?.lowercase() ?: ""
                
                var score = 0
                // Play kelimesi geçiyorsa yüksek puan
                for (keyword in playKeywords) {
                    if (contentDesc.contains(keyword)) score += 10
                    if (text.contains(keyword)) score += 10
                    if (viewId.contains(keyword)) score += 5
                }
                
                // Button veya clickable içeriyorsa ekstra puan
                if (viewId.contains("button") || viewId.contains("play") || viewId.contains("click")) {
                    score += 5
                }
                
                if (score > maxPlayScore) {
                    maxPlayScore = score
                    bestPlayButton = node
                }
            }
            
            if (bestPlayButton != null && maxPlayScore > 0) {
                Log.d("CallAccessibility", "YouTube Music Play butonu bulundu (score: $maxPlayScore)")
                bestPlayButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                allNodes.forEach { if (it != bestPlayButton) it.recycle() }
                bestPlayButton.recycle()
                return
            }
            
            // 3. İlk tıklanabilir öğeyi bul (genellikle ilk arama sonucu)
            val clickableNodes = mutableListOf<AccessibilityNodeInfo>()
            findClickableNodes(rootNode, clickableNodes)
            
            Log.d("CallAccessibility", "Toplam ${clickableNodes.size} tıklanabilir öğe bulundu")
            
            // İlk birkaç tıklanabilir öğeyi dene (genellikle ilk arama sonucu)
            for (i in 0 until minOf(5, clickableNodes.size)) {
                val clickable = clickableNodes[i]
                // Çok küçük butonları atla (genellikle menü butonları)
                val bounds = android.graphics.Rect()
                clickable.getBoundsInScreen(bounds)
                val area = bounds.width() * bounds.height()
                
                if (area > 1000) { // Minimum alan kontrolü
                    Log.d("CallAccessibility", "YouTube Music tıklanabilir öğe $i bulundu (alan: $area), tıklanıyor...")
                    clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    clickableNodes.forEach { it.recycle() }
                    allNodes.forEach { it.recycle() }
                    return
                }
            }
            
            // Son çare: En büyük tıklanabilir öğeyi bul
            var largestClickable: AccessibilityNodeInfo? = null
            var maxArea = 0
            
            for (clickable in clickableNodes) {
                val bounds = android.graphics.Rect()
                clickable.getBoundsInScreen(bounds)
                val area = bounds.width() * bounds.height()
                
                if (area > maxArea) {
                    maxArea = area
                    largestClickable = clickable
                }
            }
            
            if (largestClickable != null && maxArea > 500) {
                Log.d("CallAccessibility", "YouTube Music en büyük tıklanabilir öğe bulundu (alan: $maxArea), tıklanıyor...")
                largestClickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            } else {
                Log.e("CallAccessibility", "YouTube Music Play butonu bulunamadı")
            }
            
            clickableNodes.forEach { it.recycle() }
            allNodes.forEach { it.recycle() }
        } catch (e: Exception) {
            Log.e("CallAccessibility", "YouTube Music Play butonu tıklanırken hata: ${e.message}", e)
        } finally {
            rootNode.recycle()
        }
    }
    
    private fun findClickableNodes(node: AccessibilityNodeInfo, result: MutableList<AccessibilityNodeInfo>) {
        if (node.isClickable && node.isEnabled) {
            result.add(node)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                findClickableNodes(child, result)
                child.recycle()
            }
        }
    }
    
    override fun onInterrupt() {
        // Servis kesildiğinde
    }
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or 
                        AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                        AccessibilityEvent.TYPE_VIEW_CLICKED or
                        AccessibilityEvent.TYPE_VIEW_SCROLLED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                   AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 50 // Daha hızlı tepki için
        }
        setServiceInfo(info)
        Log.d("CallAccessibility", "Accessibility Service bağlandı (YouTube Music desteği ile)")
    }
}

