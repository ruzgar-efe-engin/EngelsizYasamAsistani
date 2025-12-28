package com.gormeengelliler.android.model

import org.json.JSONObject

enum class EventType(val value: Int) {
    MAIN_ROTATE(0),
    SUB_ROTATE(1),
    CONFIRM(2),
    EVENT_CANCEL(3),
    AI_PRESS(4),
    AI_RELEASE(5);
    
    companion object {
        fun fromInt(value: Int): EventType? {
            return values().find { it.value == value }
        }
    }
}

data class DeviceEvent(
    val type: EventType,
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

