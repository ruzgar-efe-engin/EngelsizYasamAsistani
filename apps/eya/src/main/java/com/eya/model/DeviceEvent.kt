package com.eya.model

import org.json.JSONObject

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
                    ts = json.optLong("ts", 0)
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

