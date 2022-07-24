package com.mapbox.rctmgl.components.mapview

import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.common.MapBuilder
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.annotations.ReactProp
import com.mapbox.geojson.Point
import com.mapbox.rctmgl.events.constants.EventKeys
import com.mapbox.rctmgl.modules.RCTMGLModule
import com.mapbox.rctmgl.utils.extensions.toCoordinate
import okhttp3.internal.toImmutableMap

class AndroidMapboxViewManager(val context: ReactApplicationContext?) :
    RCTMGLMapViewManager(context) {

    override fun getName(): String {
        return REACT_CLASS
    }

    override fun createViewInstance(themedReactContext: ThemedReactContext): AndroidMapboxView {
        return AndroidMapboxView(themedReactContext, this, RCTMGLModule.getAccessToken(this.context))
    }

    override fun customEvents(): Map<String, String> {
        val parentCommandsMap = super.customEvents()!!
        val map =  MapBuilder.builder<String, String>()
            .put(EventKeys.ON_MAP_ERROR, "onMapError")
            .put(EventKeys.ON_FIND_ROUTE_SUCCESS, "onFindRouteSuccess")
            .put(EventKeys.ON_NAVIGATION_STARTED, "onNavigationStarted")
            .put(EventKeys.ON_ROUTE_OFF, "onRouteOff")
            .put(EventKeys.ON_ARRIVAL, "onArrival")
            .build()
        val newMap = mutableMapOf<String, String>()
        newMap.putAll(parentCommandsMap)
        newMap.putAll(map)

        return newMap.toImmutableMap()
    }

    override fun getCommandsMap(): Map<String, Int> {
        val parentCommandsMap = super.getCommandsMap()!!
        val map =  MapBuilder.builder<String, Int>()
            .put("findRoute", METHOD_FIND_ROUTE)
            .put("startRoute", METHOD_START_NAVIGATION)
            .put("stopRoute", METHOD_STOP_NAVIGATION)
            .put("resetRoute", METHOD_RESET_ROUTE)
            .put("recenter", METHOD_RECENTER)
            .build()
        val newMap = mutableMapOf<String, Int>()
        newMap.putAll(parentCommandsMap)
        newMap.putAll(map)
        return newMap.toImmutableMap()
    }

    override fun receiveCommand(mapView: RCTMGLMapView, commandID: Int, args: ReadableArray?) {
        super.receiveCommand(mapView, commandID, args)
        when (commandID) {
            METHOD_FIND_ROUTE -> {
                (mapView as AndroidMapboxView).findRoute(
                    args?.getArray(1)?.toCoordinate(),
                    args?.getArray(2)?.toCoordinate()
                )
            }
            METHOD_STOP_NAVIGATION -> {
                (mapView as AndroidMapboxView).stopRoute(args?.getBoolean(1)?: false)
            }
            METHOD_START_NAVIGATION -> {
                (mapView as AndroidMapboxView).startRoute(args?.getArray(1)?.toCoordinate(), args?.getBoolean(2)?: false)
            }
            METHOD_RESET_ROUTE -> {
                (mapView as AndroidMapboxView).clearRoute()
            }
            METHOD_RECENTER -> {
                (mapView as AndroidMapboxView).recenter()
            }
        }
    }

    @ReactProp(name = "origin")
    fun setOrigin(view: AndroidMapboxView, sources: ReadableArray?) {
        if (sources == null) {
            view.setOrigin(null)
            return
        }
        view.setOrigin(Point.fromLngLat(sources.getDouble(0), sources.getDouble(1)))
    }

    @ReactProp(name = "destination")
    fun setDestination(view: AndroidMapboxView, sources: ReadableArray?) {
        if (sources == null) {
            view.setDestination(null)
            return
        }
        view.setDestination(Point.fromLngLat(sources.getDouble(0), sources.getDouble(1)))
    }


    companion object {
        const val REACT_CLASS = "AndroidMapboxViewManager"
        const val LOG_TAG = "AndroidMapboxViewManager"

        const val METHOD_FIND_ROUTE = 14
        const val METHOD_STOP_NAVIGATION = 15
        const val METHOD_START_NAVIGATION = 16
        const val METHOD_RESET_ROUTE = 17
        const val METHOD_RECENTER = 18
    }
}