package com.mapbox.rctmgl.events

import android.view.View
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.WritableMap
import com.mapbox.rctmgl.events.constants.EventKeys
import com.mapbox.rctmgl.events.constants.EventTypes

class NavigationEvent @JvmOverloads constructor(
    view: View?,
    private val eventType: String?,
    private val mPayload: WritableMap = Arguments.createMap()
) : AbstractEvent(view, eventType) {
    override fun getKey(): String {
        return when(this.eventType) {
            EventTypes.ON_FIND_ROUTE_SUCCESS -> {
                EventKeys.ON_FIND_ROUTE_SUCCESS
            }
            EventTypes.ON_NAVIGATION_STARTED -> {
                EventKeys.ON_NAVIGATION_STARTED
            }
            EventTypes.ON_ARRIVAL -> {
                EventKeys.ON_ARRIVAL
            }
            EventTypes.ON_ROUTE_OFF -> {
                EventKeys.ON_ROUTE_OFF
            }
            else -> {
                ""
            }
        }
    }

    override fun getPayload(): WritableMap {
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
