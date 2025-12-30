package com.gormeengelliler.android.model

import com.gormeengelliler.android.manager.MenuManager

class MenuNavigationState(private val menuManager: MenuManager? = null) {
    var currentMainIndex: Int = 0
    var currentSubIndex: Int? = null
    var currentSubSubIndex: Int? = null
    
    fun resetSubMenus() {
        currentSubIndex = null
        currentSubSubIndex = null
    }
    
    fun resetAll() {
        currentMainIndex = 0
        resetSubMenus()
    }
    
    fun isAtMainMenu(): Boolean {
        return currentSubIndex == null
    }
    
    fun isAtSubMenu(): Boolean {
        return currentSubIndex != null && currentSubSubIndex == null
    }
    
    fun isAtNestedSubMenu(): Boolean {
        return currentSubIndex != null && currentSubSubIndex != null
    }
    
    fun setMainMenu(index: Int) {
        currentMainIndex = index
        resetSubMenus()
    }
    
    fun setSubMenu(index: Int) {
        currentSubIndex = index
        currentSubSubIndex = null
    }
    
    fun setNestedSubMenu(index: Int) {
        currentSubSubIndex = index
    }
}

