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
            val inputStream: InputStream = context.resources.openRawResource(
                context.resources.getIdentifier("menu_structure", "raw", context.packageName)
            )
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            menuStructure = MenuStructure.fromJson(jsonString)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    fun getSelectedLanguage(): String {
        return prefs.getString(LANGUAGE_KEY, DEFAULT_LANGUAGE) ?: DEFAULT_LANGUAGE
    }
    
    fun setSelectedLanguage(language: String) {
        prefs.edit().putString(LANGUAGE_KEY, language).apply()
    }
    
    fun getMainMenuName(mainIndex: Int): String? {
        val structure = menuStructure ?: return null
        val mainMenu = structure.mainMenus.find { it.id == mainIndex } ?: return null
        return mainMenu.name.get(getSelectedLanguage())
    }
    
    fun getSubMenuName(mainIndex: Int, subIndex: Int): String? {
        val structure = menuStructure ?: return null
        val mainMenu = structure.mainMenus.find { it.id == mainIndex } ?: return null
        val subMenu = mainMenu.subMenus.find { it.id == subIndex } ?: return null
        return subMenu.name.get(getSelectedLanguage())
    }
    
    fun isMenuStructureLoaded(): Boolean {
        return menuStructure != null
    }
}

