package com.myra.assistant.call

import com.myra.assistant.util.CallerInfo

object PendingIncomingCall {
    @Volatile
    var current: CallerInfo? = null

    fun set(caller: CallerInfo) {
        current = caller
    }

    fun clear() {
        current = null
    }
}
