package com.gormeengelliler.android.model

import org.json.JSONObject
import org.json.JSONArray

data class MenuName(
    val tr: String,
    val en: String,
    val de: String
) {
    fun get(language: String): String {
        return when (language.lowercase()) {
            "tr" -> tr
            "en" -> en
            "de" -> de
            else -> tr
        }
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
    val name: MenuName
) {
    companion object {
        fun fromJson(json: JSONObject): SubMenu {
            return SubMenu(
                id = json.getInt("id"),
                name = MenuName.fromJson(json.getJSONObject("name"))
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

data class Theme(
    val id: Int,
    val name: MenuName,
    val mainMenus: List<MainMenu>
) {
    companion object {
        fun fromJson(json: JSONObject): Theme {
            val mainMenusArray = json.getJSONArray("mainMenus")
            val mainMenus = mutableListOf<MainMenu>()
            for (i in 0 until mainMenusArray.length()) {
                mainMenus.add(MainMenu.fromJson(mainMenusArray.getJSONObject(i)))
            }
            
            return Theme(
                id = json.getInt("id"),
                name = MenuName.fromJson(json.getJSONObject("name")),
                mainMenus = mainMenus
            )
        }
    }
}

data class MenuStructure(
    val themes: List<Theme>
) {
    companion object {
        fun fromJson(jsonString: String): MenuStructure? {
            return try {
                val json = JSONObject(jsonString)
                val themesArray = json.getJSONArray("themes")
                val themes = mutableListOf<Theme>()
                
                for (i in 0 until themesArray.length()) {
                    themes.add(Theme.fromJson(themesArray.getJSONObject(i)))
                }
                
                MenuStructure(themes = themes)
            } catch (e: Exception) {
                null
            }
        }
    }
}

