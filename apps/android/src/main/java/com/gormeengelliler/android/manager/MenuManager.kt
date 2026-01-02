package com.gormeengelliler.android.manager

import android.content.Context
import android.content.SharedPreferences
import com.gormeengelliler.android.model.MenuStructure
import java.io.InputStream

class MenuManager(private val context: Context) {
    private var menuStructure: MenuStructure? = null
    private val prefs: SharedPreferences = 
        context.getSharedPreferences("EngelsizYasamAsistani", Context.MODE_PRIVATE)
    
    private val LANGUAGE_KEY = "selected_language"
    private val DEFAULT_LANGUAGE = "tr"
    
    init {
        loadMenuStructure()
    }
    
    private fun loadMenuStructure() {
        try {
            EventLogManager.logMenu("Menu JSON yükleniyor", "menu_structure.json dosyası okunuyor")
            val inputStream: InputStream = context.resources.openRawResource(
                context.resources.getIdentifier("menu_structure", "raw", context.packageName)
            )
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            EventLogManager.logMenu("Menu JSON okundu", "${jsonString.length} karakter")
            val loadedStructure = MenuStructure.fromJson(jsonString)
            menuStructure = loadedStructure
            if (loadedStructure != null) {
                val mainMenuCount = loadedStructure.mainMenus.size
                EventLogManager.logMenu("Menu yapısı parse edildi", "$mainMenuCount ana menu yüklendi")
            } else {
                EventLogManager.logMenu("Menu yapısı parse edilemedi", "", true)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            EventLogManager.logMenu("Menu yükleme hatası", "${e.message}", true)
        }
    }
    
    fun getSelectedLanguage(): String {
        val language = prefs.getString(LANGUAGE_KEY, DEFAULT_LANGUAGE) ?: DEFAULT_LANGUAGE
        android.util.Log.d("MenuManager", "📝 Seçili dil: $language")
        EventLogManager.logMenu("Seçili dil sorgulandı", "language=$language")
        return language
    }
    
    fun setSelectedLanguage(language: String) {
        val oldLanguage = getSelectedLanguage()
        android.util.Log.d("MenuManager", "📝 Dil değiştiriliyor: $oldLanguage -> $language")
        EventLogManager.logMenu("Dil değiştiriliyor", "$oldLanguage -> $language")
        prefs.edit().putString(LANGUAGE_KEY, language).apply()
        android.util.Log.d("MenuManager", "✅ Dil değiştirildi: $language")
        EventLogManager.logMenu("Dil değiştirildi", "language=$language, SharedPreferences güncellendi")
    }
    
    fun getMainMenuName(mainIndex: Int): String? {
        EventLogManager.logMenu("═══════════════════════════════════", "")
        EventLogManager.logMenu("getMainMenuName BAŞLADI", "mainIndex=$mainIndex")
        EventLogManager.logMenu("Adım 1: Menu yapısı kontrol ediliyor", "menuStructure=${menuStructure != null}")
        
        val structure = menuStructure
        if (structure == null) {
            EventLogManager.logMenu("❌ HATA: Menu yapısı yüklenmemiş", "", true)
            return null
        }
        EventLogManager.logMenu("✅ Menu yapısı mevcut", "Toplam ${structure.mainMenus.size} ana menu var")
        EventLogManager.logMenu("Adım 2: Mevcut main menu ID'leri", "${structure.mainMenus.map { it.id }}")
        
        // NOT: Normalizasyon DeviceEventService'te yapılıyor, burada direkt kullanıyoruz
        EventLogManager.logMenu("Adım 3: mainIndex=$mainIndex ile menu aranıyor (normalizasyon zaten yapıldı)", "find() çağrılıyor")
        val mainMenu = structure.mainMenus.find { it.id == mainIndex }
        if (mainMenu == null) {
            EventLogManager.logMenu("❌ HATA: Main menu bulunamadı", "mainIndex=$mainIndex, mevcut ID'ler: ${structure.mainMenus.map { it.id }}", true)
            return null
        }
        EventLogManager.logMenu("✅ Main menu bulundu", "id=${mainMenu.id}")
        EventLogManager.logMenu("Adım 4: Menu name objesi içeriği", "name.tr='${mainMenu.name.tr}', name.en='${mainMenu.name.en}', name.de='${mainMenu.name.de}'")
        
        EventLogManager.logMenu("Adım 5: Aktif dil sorgulanıyor", "getSelectedLanguage() çağrılıyor")
        val language = getSelectedLanguage()
        EventLogManager.logMenu("✅ Aktif dil belirlendi", "language=$language")
        
        EventLogManager.logMenu("Adım 6: MenuName.get() çağrılıyor", "language=$language ile text alınıyor")
        val name = mainMenu.name.get(language)
        EventLogManager.logMenu("✅ Menu adı alındı", "mainIndex=$mainIndex, language=$language, text='$name'")
        EventLogManager.logMenu("═══════════════════════════════════", "")
        android.util.Log.d("MenuManager", "📝 Main menu adı: mainIndex=$mainIndex, dil=$language, ad='$name'")
        return name
    }
    
    fun getSubMenuName(mainIndex: Int, subIndex: Int, subSubIndex: Int? = null): String? {
        EventLogManager.logMenu("═══════════════════════════════════", "")
        EventLogManager.logMenu("getSubMenuName BAŞLADI", "mainIndex=$mainIndex, subIndex=$subIndex, subSubIndex=$subSubIndex")
        EventLogManager.logMenu("Adım 1: Menu yapısı kontrol ediliyor", "menuStructure=${menuStructure != null}")
        
        val structure = menuStructure
        if (structure == null) {
            EventLogManager.logMenu("❌ HATA: Menu yapısı yüklenmemiş", "", true)
            return null
        }
        EventLogManager.logMenu("✅ Menu yapısı mevcut", "Toplam ${structure.mainMenus.size} ana menu var")
        
        // NOT: Normalizasyon DeviceEventService'te yapılıyor, burada direkt kullanıyoruz
        EventLogManager.logMenu("Adım 2: mainIndex=$mainIndex ile main menu aranıyor (normalizasyon zaten yapıldı)", "find() çağrılıyor")
        val mainMenu = structure.mainMenus.find { it.id == mainIndex }
        if (mainMenu == null) {
            EventLogManager.logMenu("❌ HATA: Main menu bulunamadı", "mainIndex=$mainIndex, mevcut ID'ler: ${structure.mainMenus.map { it.id }}", true)
            return null
        }
        EventLogManager.logMenu("✅ Main menu bulundu", "id=${mainMenu.id}, ${mainMenu.subMenus.size} alt menu var")
        EventLogManager.logMenu("Adım 3: Mevcut sub menu ID'leri", "${mainMenu.subMenus.map { it.id }}")
        
        EventLogManager.logMenu("Adım 4: subIndex=$subIndex ile sub menu aranıyor (normalizasyon zaten yapıldı)", "find() çağrılıyor")
        val subMenu = mainMenu.subMenus.find { it.id == subIndex }
        if (subMenu == null) {
            EventLogManager.logMenu("❌ HATA: Sub menu bulunamadı", "mainIndex=$mainIndex, subIndex=$subIndex, mevcut ID'ler: ${mainMenu.subMenus.map { it.id }}", true)
            return null
        }
        EventLogManager.logMenu("✅ Sub menu bulundu", "id=${subMenu.id}")
        EventLogManager.logMenu("Adım 5: Sub menu name objesi içeriği", "name.tr='${subMenu.name.tr}', name.en='${subMenu.name.en}', name.de='${subMenu.name.de}'")
        
        EventLogManager.logMenu("Adım 6: Aktif dil sorgulanıyor", "getSelectedLanguage() çağrılıyor")
        val language = getSelectedLanguage()
        EventLogManager.logMenu("✅ Aktif dil belirlendi", "language=$language")
        
        val name = if (subSubIndex != null && subMenu.subMenus != null) {
            // Nested sub-menu - normalizasyon zaten yapıldı
            EventLogManager.logMenu("Adım 7: Nested sub-menu kontrolü", "subSubIndex=$subSubIndex, ${subMenu.subMenus.size} nested menu var")
            EventLogManager.logMenu("Adım 8: Nested sub menu ID'leri", "${subMenu.subMenus.map { it.id }}")
            EventLogManager.logMenu("Adım 9: subSubIndex=$subSubIndex ile nested menu aranıyor (normalizasyon zaten yapıldı)", "find() çağrılıyor")
            val nestedSubMenu = subMenu.subMenus.find { it.id == subSubIndex }
            if (nestedSubMenu == null) {
                EventLogManager.logMenu("❌ HATA: Nested sub-menu bulunamadı", "subSubIndex=$subSubIndex, mevcut ID'ler: ${subMenu.subMenus.map { it.id }}", true)
                return null
            }
            EventLogManager.logMenu("✅ Nested sub-menu bulundu", "id=${nestedSubMenu.id}")
            EventLogManager.logMenu("Adım 10: Nested menu name objesi içeriği", "name.tr='${nestedSubMenu.name.tr}', name.en='${nestedSubMenu.name.en}', name.de='${nestedSubMenu.name.de}'")
            EventLogManager.logMenu("Adım 11: MenuName.get() çağrılıyor (nested)", "language=$language ile text alınıyor")
            val nestedName = nestedSubMenu.name.get(language)
            EventLogManager.logMenu("✅ Nested menu adı alındı", "mainIndex=$mainIndex, subIndex=$subIndex, subSubIndex=$subSubIndex, language=$language, text='$nestedName'")
            nestedName
        } else {
            // Regular sub-menu
            EventLogManager.logMenu("Adım 7: Regular sub-menu adı alınıyor", "subIndex=$subIndex")
            EventLogManager.logMenu("Adım 8: MenuName.get() çağrılıyor (regular)", "language=$language ile text alınıyor")
            val regularName = subMenu.name.get(language)
            EventLogManager.logMenu("✅ Sub menu adı alındı", "mainIndex=$mainIndex, subIndex=$subIndex, language=$language, text='$regularName'")
            regularName
        }
        
        EventLogManager.logMenu("═══════════════════════════════════", "")
        android.util.Log.d("MenuManager", "📝 Sub-menu adı: mainIndex=$mainIndex, subIndex=$subIndex, subSubIndex=$subSubIndex, dil=$language, ad='$name'")
        return name
    }
    
    fun getSubMenuClickResult(mainIndex: Int, subIndex: Int, subSubIndex: Int? = null): String? {
        val structure = menuStructure ?: return null
        
        // NOT: Normalizasyon ConfirmHandler'da yapılıyor, burada direkt kullanıyoruz
        val mainMenu = structure.mainMenus.find { it.id == mainIndex } ?: return null
        val subMenu = mainMenu.subMenus.find { it.id == subIndex } ?: return null
        
        return if (subSubIndex != null && subMenu.subMenus != null) {
            // Nested sub-menu click result
            val nestedSubMenu = subMenu.subMenus.find { it.id == subSubIndex } ?: return null
            nestedSubMenu.clickResult?.get(getSelectedLanguage())
        } else {
            // Regular sub-menu click result
            subMenu.clickResult?.get(getSelectedLanguage())
        }
    }
    
    fun hasNestedSubMenus(mainIndex: Int, subIndex: Int): Boolean {
        val structure = menuStructure ?: return false
        
        // NOT: Normalizasyon DeviceEventService'te yapılıyor, burada direkt kullanıyoruz
        val mainMenu = structure.mainMenus.find { it.id == mainIndex } ?: return false
        val subMenu = mainMenu.subMenus.find { it.id == subIndex } ?: return false
        return subMenu.subMenus != null && subMenu.subMenus!!.isNotEmpty()
    }
    
    fun getNestedSubMenusCount(mainIndex: Int, subIndex: Int): Int {
        val structure = menuStructure ?: return 0
        val mainMenu = structure.mainMenus.find { it.id == mainIndex } ?: return 0
        val subMenu = mainMenu.subMenus.find { it.id == subIndex } ?: return 0
        return subMenu.subMenus?.size ?: 0
    }
    
    fun isMenuStructureLoaded(): Boolean {
        return menuStructure != null
    }
    
    // ============================================================================
    // INDEX NORMALIZASYON FONKSİYONLARI
    // ============================================================================
    
    /**
     * Modüler aritmetik ile index normalizasyonu
     * Pozitif ve negatif index'leri geçerli aralığa (0..count-1) dönüştürür
     * 
     * Örnekler (16 menu varsa):
     * - 16 → 0
     * - 50 → 2
     * - 255 → 15
     * - -1 → 15
     * - -2 → 14
     */
    private fun normalizeIndex(index: Int, count: Int): Int {
        if (count == 0) return 0
        return ((index % count) + count) % count
    }
    
    /**
     * Ana menu index'ini normalize et
     */
    fun normalizeMainIndex(index: Int): Int {
        val structure = menuStructure ?: return index
        val mainMenuCount = structure.mainMenus.size
        val normalized = normalizeIndex(index, mainMenuCount)
        android.util.Log.d("MenuManager", "📊 MainIndex normalizasyonu: $index → $normalized (toplam $mainMenuCount menu)")
        EventLogManager.logMenu("MainIndex normalizasyonu", "$index → $normalized (toplam $mainMenuCount menu)")
        return normalized
    }
    
    /**
     * Alt menu index'ini normalize et
     */
    fun normalizeSubIndex(mainIndex: Int, subIndex: Int): Int {
        val structure = menuStructure ?: return subIndex
        val normalizedMainIndex = normalizeMainIndex(mainIndex)
        val mainMenu = structure.mainMenus.find { it.id == normalizedMainIndex } ?: return subIndex
        val subMenuCount = mainMenu.subMenus.size
        val normalized = normalizeIndex(subIndex, subMenuCount)
        android.util.Log.d("MenuManager", "📊 SubIndex normalizasyonu: mainIndex=$mainIndex→$normalizedMainIndex, subIndex=$subIndex → $normalized (toplam $subMenuCount alt menu)")
        EventLogManager.logMenu("SubIndex normalizasyonu", "mainIndex=$mainIndex→$normalizedMainIndex, subIndex=$subIndex → $normalized (toplam $subMenuCount alt menu)")
        return normalized
    }
    
    /**
     * Nested alt menu index'ini normalize et
     */
    fun normalizeSubSubIndex(mainIndex: Int, subIndex: Int, subSubIndex: Int): Int {
        val structure = menuStructure ?: return subSubIndex
        val normalizedMainIndex = normalizeMainIndex(mainIndex)
        val mainMenu = structure.mainMenus.find { it.id == normalizedMainIndex } ?: return subSubIndex
        val normalizedSubIndex = normalizeSubIndex(normalizedMainIndex, subIndex)
        val subMenu = mainMenu.subMenus.find { it.id == normalizedSubIndex } ?: return subSubIndex
        val nestedSubMenus = subMenu.subMenus ?: return subSubIndex
        val nestedSubMenuCount = nestedSubMenus.size
        val normalized = normalizeIndex(subSubIndex, nestedSubMenuCount)
        android.util.Log.d("MenuManager", "📊 SubSubIndex normalizasyonu: mainIndex=$mainIndex→$normalizedMainIndex, subIndex=$subIndex→$normalizedSubIndex, subSubIndex=$subSubIndex → $normalized (toplam $nestedSubMenuCount nested menu)")
        EventLogManager.logMenu("SubSubIndex normalizasyonu", "mainIndex=$mainIndex→$normalizedMainIndex, subIndex=$subIndex→$normalizedSubIndex, subSubIndex=$subSubIndex → $normalized (toplam $nestedSubMenuCount nested menu)")
        return normalized
    }
}

