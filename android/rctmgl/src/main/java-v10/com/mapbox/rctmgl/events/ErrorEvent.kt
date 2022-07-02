package com.mapbox.rctmgl.events

import android.view.View
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.WritableMap
import com.mapbox.rctmgl.events.constants.EventKeys

class ErrorEvent @JvmOverloads constructor(
    view: View?,
    eventType: String?,
    private val mPayload: WritableMap = Arguments.createMap()
) : AbstractEvent(view, eventType) {
    override fun getKey(): String {
        return EventKeys.ON_ERROR
    }

    override fun getPayload(): WritableMap {
        // FMTODO
        val payloadClone = Arguments.createMap()
        payloadClone.merge(mPayload)
        return payloadClone
    }

    override fun canCoalesce(): Boolean {
        // Make sure EventDispatcher never merges EventKeys.MAP_ONCHANGE events.
        // This event name is used to emit events with different
        // com.mapbox.rctmgl.events.constants.EventTypes which are dispatched separately on
        // the JS side
        return false
    }
}