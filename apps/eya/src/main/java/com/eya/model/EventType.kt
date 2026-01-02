package com.eya.model

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

