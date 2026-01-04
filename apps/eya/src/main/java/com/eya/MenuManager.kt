package com.eya

import android.content.Context
import org.json.JSONObject
import com.eya.R

class MenuManager(context: Context) {
    private val menuJson: JSONObject

    init {
        val input = context.resources.openRawResource(R.raw.menu)
        val text = input.bufferedReader().use { it.readText() }
        menuJson = JSONObject(text)
    }

    fun normalize(index: Int, size: Int): Int {
        if (size <= 0) return 0
        return ((index % size) + size) % size
    }

    fun getMainMenuName(mainIndex: Int, language: String): String? {
        val mainMenus = menuJson.getJSONArray("mainMenus")
        val idx = normalize(mainIndex, mainMenus.length())
        val main = mainMenus.getJSONObject(idx)
        val nameObj = main.getJSONObject("name")
        return nameObj.optString(language, nameObj.optString("tr"))
    }

    fun getSubMenuName(mainIndex: Int, subIndex: Int, language: String): String? {
        val mainMenus = menuJson.getJSONArray("mainMenus")
        val idx = normalize(mainIndex, mainMenus.length())
        val main = mainMenus.getJSONObject(idx)
        val subMenus = main.getJSONArray("subMenus")
        val sIdx = normalize(subIndex, subMenus.length())
        val sub = subMenus.getJSONObject(sIdx)
        val nameObj = sub.getJSONObject("name")
        return nameObj.optString(language, nameObj.optString("tr"))
    }
    
    fun getMainMenuCount(): Int {
        val mainMenus = menuJson.getJSONArray("mainMenus")
        return mainMenus.length()
    }
    
    fun getSubMenuCount(mainIndex: Int): Int {
        val mainMenus = menuJson.getJSONArray("mainMenus")
        val idx = normalize(mainIndex, mainMenus.length())
        val main = mainMenus.getJSONObject(idx)
        val subMenus = main.getJSONArray("subMenus")
        return subMenus.length()
    }
}

