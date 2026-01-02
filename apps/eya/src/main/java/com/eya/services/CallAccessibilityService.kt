package com.eya.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.util.Log

class CallAccessibilityService : AccessibilityService() {
    
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // Dialer açıldığında ve 112 numarası tuşlandığında "Ara" butonuna tıkla
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            val packageName = event.packageName?.toString() ?: return
            val className = event.className?.toString() ?: ""
            
            Log.d("CallAccessibility", "Event: package=$packageName, class=$className")
            
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
    
    override fun onInterrupt() {
        // Servis kesildiğinde
    }
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            notificationTimeout = 100
        }
        setServiceInfo(info)
        Log.d("CallAccessibility", "Accessibility Service bağlandı")
    }
}

