package com.gormeengelliler.android.model

import org.json.JSONObject
import org.json.JSONArray

data class MenuName(
    val tr: String,
    val en: String,
    val de: String
) {
    fun get(language: String): String {
        com.gormeengelliler.android.manager.EventLogManager.logMenu("MenuName.get() BAŞLADI", "language='$language'")
        com.gormeengelliler.android.manager.EventLogManager.logMenu("Adım 1: Dil normalize ediliyor", "language.lowercase() çağrılıyor")
        val normalizedLanguage = language.lowercase()
        com.gormeengelliler.android.manager.EventLogManager.logMenu("✅ Dil normalize edildi", "normalizedLanguage='$normalizedLanguage'")
        com.gormeengelliler.android.manager.EventLogManager.logMenu("Adım 2: Mevcut dil seçenekleri", "tr='$tr', en='$en', de='$de'")
        com.gormeengelliler.android.manager.EventLogManager.logMenu("Adım 3: when() ile dil seçimi yapılıyor", "normalizedLanguage='$normalizedLanguage'")
        val selectedText = when (normalizedLanguage) {
            "tr" -> {
                com.gormeengelliler.android.manager.EventLogManager.logMenu("✅ Türkçe seçildi", "text='$tr'")
                tr
            }
            "en" -> {
                com.gormeengelliler.android.manager.EventLogManager.logMenu("✅ İngilizce seçildi", "text='$en'")
                en
            }
            "de" -> {
                com.gormeengelliler.android.manager.EventLogManager.logMenu("✅ Almanca seçildi", "text='$de'")
                de
            }
            else -> {
                com.gormeengelliler.android.manager.EventLogManager.logMenu("⚠️ Bilinmeyen dil, varsayılan (tr) kullanılıyor", "language='$language'", true)
                com.gormeengelliler.android.manager.EventLogManager.logMenu("✅ Varsayılan Türkçe text", "text='$tr'")
                tr
            }
        }
        com.gormeengelliler.android.manager.EventLogManager.logMenu("✅ MenuName.get() tamamlandı", "language=$normalizedLanguage, text='$selectedText'")
        return selectedText
    }
    
    companion object {
        fun fromJson(json: JSONObject): MenuName {
            return MenuName(
                tr = json.getString("tr"),
                en = json.getString("en"),
                de = json.getString("de")
            )
        }
    }
}

data class SubMenu(
    val id: Int,
    val name: MenuName,
    val clickResult: MenuName? = null,
    val subMenus: List<SubMenu>? = null
) {
    companion object {
        fun fromJson(json: JSONObject): SubMenu {
            val clickResultJson = json.optJSONObject("clickResult")
            val clickResult = clickResultJson?.let { MenuName.fromJson(it) }
            
            val subMenusArray = json.optJSONArray("subMenus")
            val subMenus = if (subMenusArray != null) {
                val list = mutableListOf<SubMenu>()
                for (i in 0 until subMenusArray.length()) {
                    list.add(fromJson(subMenusArray.getJSONObject(i)))
                }
                list
            } else {
                null
            }
            
            return SubMenu(
                id = json.getInt("id"),
                name = MenuName.fromJson(json.getJSONObject("name")),
                clickResult = clickResult,
                subMenus = subMenus
            )
        }
    }
}

data class MainMenu(
    val id: Int,
    val name: MenuName,
    val subMenus: List<SubMenu>
) {
    companion object {
        fun fromJson(json: JSONObject): MainMenu {
            val subMenusArray = json.getJSONArray("subMenus")
            val subMenus = mutableListOf<SubMenu>()
            for (i in 0 until subMenusArray.length()) {
                subMenus.add(SubMenu.fromJson(subMenusArray.getJSONObject(i)))
            }
            
            return MainMenu(
                id = json.getInt("id"),
                name = MenuName.fromJson(json.getJSONObject("name")),
                subMenus = subMenus
            )
        }
    }
}

data class MenuStructure(
    val mainMenus: List<MainMenu>
) {
    companion object {
        fun fromJson(jsonString: String): MenuStructure? {
            return try {
                val json = JSONObject(jsonString)
                // themes array'i yoksa direkt mainMenus'ü al
                val mainMenusArray = if (json.has("themes")) {
                    // Eski format: themes içinde mainMenus
                    val themesArray = json.getJSONArray("themes")
                    if (themesArray.length() > 0) {
                        themesArray.getJSONObject(0).getJSONArray("mainMenus")
                    } else {
                        json.getJSONArray("mainMenus")
                    }
                } else {
                    // Yeni format: direkt mainMenus
                    json.getJSONArray("mainMenus")
                }
                
                val mainMenus = mutableListOf<MainMenu>()
                for (i in 0 until mainMenusArray.length()) {
                    mainMenus.add(MainMenu.fromJson(mainMenusArray.getJSONObject(i)))
                }
                
                MenuStructure(mainMenus = mainMenus)
            } catch (e: Exception) {
                null
            }
        }
    }
}

