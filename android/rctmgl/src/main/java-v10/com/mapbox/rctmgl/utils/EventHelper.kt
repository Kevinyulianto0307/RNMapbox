package com.mapbox.rctmgl.utils

import android.view.View
import com.facebook.react.bridge.Arguments
import com.mapbox.rctmgl.components.mapview.RCTMGLMapViewManager
import com.mapbox.rctmgl.events.ErrorEvent
import com.mapbox.rctmgl.events.constants.EventTypes

class EventHelper(val manager: RCTMGLMapViewManager) {

    fun sendErrorToReact(view: View, errorType: String, errorCode: Int, errorMessage: String) {
        val errorPayload = Arguments.createMap()
        errorPayload.putInt("code", errorCode)
        errorPayload.putString("statusText", errorType)
        errorPayload.putString("message", errorMessage)
        val event = ErrorEvent(view, EventTypes.ON_ERROR, errorPayload)
        this.manager.handleEvent(event)
    }
}