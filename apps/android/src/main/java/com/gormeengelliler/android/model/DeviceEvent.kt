package com.gormeengelliler.android.model

import org.json.JSONObject

enum class EventType(val value: Int) {
    THEME_ROTATE(0),
    MAIN_ROTATE(1),
    SUB_ROTATE(2),
    CONFIRM(3),
    EVENT_CANCEL(4),
    AI_PRESS(5),
    AI_RELEASE(6);
    
    companion object {
        fun fromInt(value: Int): EventType? {
            return values().find { it.value == value }
        }
    }
}

data class DeviceEvent(
    val type: EventType,
    val themeIndex: Int,
    val mainIndex: Int = 0,
    val subIndex: Int = 0,
    val ts: Long = 0
) {
    companion object {
        fun fromJson(jsonString: String): DeviceEvent? {
            return try {
                val json = JSONObject(jsonString)
                val typeValue = json.getInt("type")
                val type = EventType.fromInt(typeValue) ?: return null
                
                DeviceEvent(
                    type = type,
                    themeIndex = json.getInt("themeIndex"),
                    mainIndex = json.optInt("mainIndex", 0),
                    subIndex = json.optInt("subIndex", 0),
                    ts = json.getLong("ts")
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

