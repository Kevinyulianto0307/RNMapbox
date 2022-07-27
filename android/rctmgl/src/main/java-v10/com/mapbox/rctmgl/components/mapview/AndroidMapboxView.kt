package com.mapbox.rctmgl.components.mapview

import android.content.Context
import android.graphics.Color
import android.location.Location
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.LifecycleEventListener
import com.facebook.react.bridge.ReactContext
import com.mapbox.android.gestures.MoveGestureDetector
import com.mapbox.api.directions.v5.DirectionsCriteria
import com.mapbox.api.directions.v5.models.DirectionsRoute
import com.mapbox.api.directions.v5.models.RouteOptions
import com.mapbox.bindgen.Expected
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.plugin.animation.MapAnimationOptions
import com.mapbox.maps.plugin.animation.camera
import com.mapbox.maps.plugin.compass.compass
import com.mapbox.maps.plugin.gestures.OnMoveListener
import com.mapbox.maps.plugin.gestures.gestures
import com.mapbox.maps.plugin.scalebar.scalebar
import com.mapbox.navigation.base.TimeFormat
import com.mapbox.navigation.base.extensions.applyDefaultNavigationOptions
import com.mapbox.navigation.base.extensions.applyLanguageAndVoiceUnitOptions
import com.mapbox.navigation.base.formatter.DistanceFormatterOptions
import com.mapbox.navigation.base.formatter.UnitType
import com.mapbox.navigation.base.options.NavigationOptions
import com.mapbox.navigation.base.route.*
import com.mapbox.navigation.base.trip.model.RouteLegProgress
import com.mapbox.navigation.base.trip.model.RouteProgress
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.core.MapboxNavigationProvider
import com.mapbox.navigation.core.arrival.ArrivalObserver
import com.mapbox.navigation.core.directions.session.RoutesObserver
import com.mapbox.navigation.core.formatter.MapboxDistanceFormatter
import com.mapbox.navigation.core.formatter.MapboxDistanceUtil
import com.mapbox.navigation.core.replay.MapboxReplayer
import com.mapbox.navigation.core.replay.ReplayLocationEngine
import com.mapbox.navigation.core.replay.route.ReplayProgressObserver
import com.mapbox.navigation.core.replay.route.ReplayRouteMapper
import com.mapbox.navigation.core.trip.session.LocationMatcherResult
import com.mapbox.navigation.core.trip.session.LocationObserver
import com.mapbox.navigation.core.trip.session.OffRouteObserver
import com.mapbox.navigation.core.trip.session.RouteProgressObserver
import com.mapbox.navigation.core.trip.session.VoiceInstructionsObserver
import com.mapbox.navigation.ui.base.util.MapboxNavigationConsumer
import com.mapbox.navigation.ui.maneuver.api.MapboxManeuverApi
import com.mapbox.navigation.ui.maps.camera.NavigationCamera
import com.mapbox.navigation.ui.maps.camera.data.MapboxNavigationViewportDataSource
import com.mapbox.navigation.ui.maps.camera.lifecycle.NavigationBasicGesturesHandler
import com.mapbox.navigation.ui.maps.camera.transition.NavigationCameraTransitionOptions
import com.mapbox.navigation.ui.maps.location.NavigationLocationProvider
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineApi
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineView
import com.mapbox.navigation.ui.maps.route.line.model.*
import com.mapbox.navigation.ui.tripprogress.api.MapboxTripProgressApi
import com.mapbox.navigation.ui.tripprogress.model.*
import com.mapbox.rctmgl.events.MapChangeEvent
import com.mapbox.rctmgl.events.constants.EventTypes
import com.mapbox.rctmgl.events.constants.StatusCode
import com.mapbox.rctmgl.events.constants.StatusType
import com.mapbox.rctmgl.utils.EventHelper
import java.util.*
import com.mapbox.navigation.ui.voice.api.MapboxSpeechApi
import com.mapbox.navigation.ui.voice.api.MapboxVoiceInstructionsPlayer
import com.mapbox.navigation.ui.voice.model.SpeechAnnouncement
import com.mapbox.navigation.ui.voice.model.SpeechError
import com.mapbox.navigation.ui.voice.model.SpeechValue
import com.mapbox.navigation.ui.voice.model.SpeechVolume
import com.mapbox.navigation.utils.internal.toPoint


@SuppressWarnings("MissingPermission")
class AndroidMapboxView(
    context: Context,
    manager: RCTMGLMapViewManager,
    private val accessToken: String,
) : RCTMGLMapView(context, manager) {

    private var origin : Point? = null

    private var destination: Point? = null

    private var isRouting: Boolean = false

    private var firstLocationUpdateReceived: Boolean = false

    private var eventHelper: EventHelper? = null

    private var mLifeCycleListener: LifecycleEventListener? = null

    /**
     * Stored temporary for searched routes
     */
    private var searchedRoutes: List<NavigationRoute> = emptyList()

    /**
     * Debug tool used to play, pause and seek route progress events that can be used to produce mocked location updates along the route.
     */
    private var mMapboxReplayer: MapboxReplayer? = null

    /**
     * Debug tool that mocks location updates with an input from the [mapboxReplayer].
     */
    private var replayLocationEngine: ReplayLocationEngine? = null

    /**
     * Debug observer that makes sure the replayer has always an up-to-date information to generate mock updates.
     */
    private var replayProgressObserver: ReplayProgressObserver? = null


    /** Create Move Gesture Handler to handle auto following after user moving the map camera during navigation (set timeout 3000 ms) */
    private var moveGesturehandler = Handler(Looper.getMainLooper());

    /** Runnable handling to re-enable following after user moving the map camera after 3 seconds */
    private var moveGestureRunnable = Runnable {
        NavigationCameraTransitionOptions.Builder().maxDuration(500L).build()
        navigationCamera.requestNavigationCameraToFollowing()
    }

    /**
     * Used to execute camera transitions based on the data generated by the [viewportDataSource].
     * This includes transitions from route overview to route following and continuously updating the camera as the location changes.
     */
    private lateinit var navigationCamera: NavigationCamera

    /**
     * Produces the camera frames based on the location and routing data for the [navigationCamera] to execute.
     */
    private lateinit var viewportDataSource: MapboxNavigationViewportDataSource

    /**
     * Generates updates for the [routeLineView] with the geometries and properties of the routes that should be drawn on the map.
     */
    private lateinit var routeLineApi: MapboxRouteLineApi

    private lateinit var maneuverApi: MapboxManeuverApi

    /**
     * Draws route lines on the map based on the data from the [routeLineApi]
     */
    private lateinit var routeLineView: MapboxRouteLineView

    /**
     * Generates updates for the [MapboxTripProgressView] that include remaining time and distance to the destination.
     */
    private lateinit var tripProgressApi: MapboxTripProgressApi

    private var mMapboxNavigation: MapboxNavigation? = null

    /**
     * [NavigationLocationProvider] is a utility class that helps to provide location updates generated by the Navigation SDK
     * to the Maps SDK in order to update the user location indicator on the map.
     */
    private var navigationLocationProvider: NavigationLocationProvider = NavigationLocationProvider()

    /**
     * SpeechAPI
     * Set as null because we will set it again before navigation started
     */
    private var speechApi :MapboxSpeechApi? = null

    private var voiceInstructionsPlayer: MapboxVoiceInstructionsPlayer? = null

    /**
     * Observes when a new voice instruction should be played.
     */
    private val voiceInstructionsObserver = VoiceInstructionsObserver { voiceInstructions ->
        speechApi?.generate(voiceInstructions, speechCallback)
    }


    /**
     * Based on whether the synthesized audio file is available, the callback plays the file
     * or uses the fall back which is played back using the on-device Text-To-Speech engine.
     */
    private val speechCallback =
        MapboxNavigationConsumer<Expected<SpeechError, SpeechValue>> { expected ->
            expected.fold(
                { error ->
                    // play the instruction via fallback text-to-speech engine
                    voiceInstructionsPlayer?.play(
                        error.fallback,
                        voiceInstructionsPlayerCallback
                    )
                },
                { value ->
                    // play the sound file from the external generator
                    voiceInstructionsPlayer?.play(
                        value.announcement,
                        voiceInstructionsPlayerCallback
                    )
                }
            )
        }

    /**
     * When a synthesized audio file was downloaded, this callback cleans up the disk after it was played.
     */
    private val voiceInstructionsPlayerCallback =
        MapboxNavigationConsumer<SpeechAnnouncement> { value ->
            // remove already consumed file to free-up space
            speechApi?.clean(value)
        }

    /**
     * When off routes, the Navigation SDK will automatically request a new route.
     */
    private val offRouteObserver =
        OffRouteObserver { offRoute ->
            val payload = Arguments.createMap()
            payload.putBoolean("isOffRoute", offRoute)
            val onRouteOffEvent = MapChangeEvent(this, EventTypes.ON_ROUTE_OFF, payload)
            mManager.handleEvent(onRouteOffEvent)
        }



    private fun updateCamera(location: Location) {
        val mapAnimationOptionsBuilder = MapAnimationOptions.Builder()
        mapAnimationOptionsBuilder.duration(1500L)
        this.camera.easeTo(
            CameraOptions.Builder()
                .center(Point.fromLngLat(location.longitude, location.latitude))
                .bearing(location.bearing.toDouble())
                .pitch(45.0)
                .zoom(17.0)
                .padding(EdgeInsets(200.0, 10.0, 100.0, 10.0))
                .build(),
            mapAnimationOptionsBuilder.build()
        )
    }

    /**
     * Gets notified with location updates.
     *
     * Exposes raw updates coming directly from the location services
     * and the updates enhanced by the Navigation SDK (cleaned up and matched to the road).
     */
    private val locationObserver = object : LocationObserver {


        override fun onNewRawLocation(rawLocation: Location) {
        }

        /**
         * provides the most accurate location update possible. The location is snapped to the route, or if possible, to the road network.
         */
        override fun onNewLocationMatcherResult(locationMatcherResult: LocationMatcherResult) {
            val enhancedLocation = locationMatcherResult.enhancedLocation
            this@AndroidMapboxView.origin = enhancedLocation.toPoint()
            Log.d("locationObserver", "onNewRawLocation" + enhancedLocation.longitude + ' ' + enhancedLocation.longitude)
            // update location puck's position on the map
            navigationLocationProvider.changePosition(
                location = enhancedLocation,
                keyPoints = locationMatcherResult.keyPoints,
            )
//            origin = enhancedLocation.toPoint()

            // update camera position to account for new location
            viewportDataSource.onLocationChanged(enhancedLocation)
            viewportDataSource.evaluate()

            if (!firstLocationUpdateReceived) {
                navigationCamera.requestNavigationCameraToFollowing(
                    stateTransitionOptions = NavigationCameraTransitionOptions.Builder()
                        .maxDuration(0) // instant transition
                        .build()
                )
                firstLocationUpdateReceived = true
            }

            val payload = Arguments.createMap()
            payload.putDouble("longitude", enhancedLocation.longitude)
            payload.putDouble("latitude", enhancedLocation.latitude)
            payload.putDouble("altitude", enhancedLocation.altitude)
            payload.putDouble("accuracy", enhancedLocation.accuracy.toDouble())
            payload.putDouble("bearing", enhancedLocation.bearing.toDouble())
            payload.putDouble("bearingAccuracyDegrees", enhancedLocation.bearingAccuracyDegrees.toDouble())
            payload.putDouble("speed", enhancedLocation.speed.toDouble())
            payload.putDouble("time", enhancedLocation.time.toDouble())

            val onLocationMatcherChangeEvent = MapChangeEvent(this@AndroidMapboxView, EventTypes.ON_LOCATION_MATCHER_CHANGE, payload)
            mManager.handleEvent(onLocationMatcherChangeEvent)
        }
    }

    /**
     * Gets notified with progress along the currently active route.
     */
    private val routeProgressObserver = RouteProgressObserver { routeProgress ->
        Log.d("routeProgressObserver", "routeProgress")
//         update the camera position to account for the progressed fragment of the route

        viewportDataSource.onRouteProgressChanged(routeProgress)
        viewportDataSource.evaluate()

        val activeLegPayload = Arguments.createMap()
        activeLegPayload.putString("directionRoutes", routeProgress.route.toJson())
        if (routeProgress.currentLegProgress == null) {
            activeLegPayload.putNull("currentLegIndex")
            activeLegPayload.putNull("routeLeg")
            activeLegPayload.putNull("currentStep")
            activeLegPayload.putNull("currentStepName")
            activeLegPayload.putNull("currentStepIndex")
        } else {
            activeLegPayload.putInt("currentLegIndex", routeProgress.currentLegProgress!!.legIndex)
            if (routeProgress.currentLegProgress!!.routeLeg != null) {
                activeLegPayload.putString("routeLeg", routeProgress.currentLegProgress!!.routeLeg!!.toJson())
            } else {
                activeLegPayload.putNull("routeLeg")
            }
            if (routeProgress.currentLegProgress!!.currentStepProgress == null || routeProgress.currentLegProgress!!.currentStepProgress?.step == null) {
                activeLegPayload.putNull("currentStepName")
                activeLegPayload.putNull("currentStepIndex")
            } else {
                activeLegPayload.putString("currentStepName", routeProgress.currentLegProgress!!.currentStepProgress!!.step!!.name())
                activeLegPayload.putInt("currentStepIndex", routeProgress.currentLegProgress!!.currentStepProgress!!.stepIndex)
            }
        }

        val distance: Double = routeProgress.distanceRemaining.toDouble()
        val distanceFormat =
            MapboxDistanceUtil.formatDistance(distance, 0, UnitType.METRIC, context)
        val currentLegDistance = "${distanceFormat.distanceAsString}${distanceFormat.distanceSuffix}"

        val routeProgressPayload = Arguments.createMap()
        routeProgressPayload.putMap("activeLegPayload", activeLegPayload)
        routeProgressPayload.putDouble("distanceTraveled", routeProgress.distanceTraveled.toDouble())
        routeProgressPayload.putDouble("durationRemaining", routeProgress.durationRemaining)
        routeProgressPayload.putDouble("fractionTraveled", routeProgress.fractionTraveled.toDouble())
        routeProgressPayload.putDouble("distanceRemaining", routeProgress.distanceRemaining.toDouble())
        routeProgressPayload.putString("distanceRemainingDisplay", currentLegDistance)
        val onRouteProgressChangeEvent = MapChangeEvent(this, EventTypes.ON_ROUTE_PROGRESS_CHANGE, routeProgressPayload)
        mManager.handleEvent(onRouteProgressChangeEvent)
    }

    /**
     * Gets notified whenever the tracked routes change.
     *
     * A change can mean:
     * - routes get changed with [MapboxNavigation.setRoutes]
     * - routes annotations get refreshed (for example, congestion annotation that indicate the live traffic along the route)
     * - driver got off route and a reroute was executed
     */
    private val routesObserver = RoutesObserver { routeUpdateResult ->
        if (routeUpdateResult.navigationRoutes.toDirectionsRoutes().isNotEmpty()) {
            // generate route geometries asynchronously and render them
            searchedRoutes = routeUpdateResult.navigationRoutes
            val routeLines = routeUpdateResult.navigationRoutes.toDirectionsRoutes().map { RouteLine(it, null) }
            routeLineApi.setNavigationRouteLines(
                routeLines
                    .toNavigationRouteLines()
            ) { value ->
                getStyle {
                    routeLineView.renderRouteDrawData(it, value)
                    // update the camera position to account for the new route
                }
            }
            viewportDataSource.onRouteChanged(
                routeUpdateResult.navigationRoutes.toDirectionsRoutes().first().toNavigationRoute())
            viewportDataSource.evaluate()
        } else {
            searchedRoutes = routeUpdateResult.navigationRoutes

            clearRouteLineView()
            // remove the route line and route arrow from the map
//            getStyle {
//                routeLineApi.clearRouteLine { value ->
//                    routeLineView.renderClearRouteLineValue(
//                        it,
//                        value
//                    )
//                }
//            }
//            viewportDataSource.clearRouteData()
//            viewportDataSource.evaluate()

        }
    }

    private val arrivalObserver = object : ArrivalObserver {
        override fun onWaypointArrival(routeProgress: RouteProgress) {
            // do something when the user arrives at a waypoint
        }

        override fun onNextRouteLegStart(routeLegProgress: RouteLegProgress) {
            // do something when the user starts a new leg
        }

        override fun onFinalDestinationArrival(routeProgress: RouteProgress) {
//            val style = getMapboxMap().getStyle()
//            if (style != null) {
//                routeArrowApi.getArrows().forEach {
//                    val removeArrowValue = routeArrowApi.removeArrow(it)
//                    routeArrowView.render(style, removeArrowValue)
//                }
//            }
            navigationCamera.requestNavigationCameraToOverview()
            navigationCamera.requestNavigationCameraToIdle()
            val onArrivalEvent = MapChangeEvent(this@AndroidMapboxView, EventTypes.ON_ARRIVAL)
            mManager.handleEvent(onArrivalEvent)
        }
    }

    override fun onStop() {
//        unregisterObserver()
        val reactContext = context as ReactContext
        reactContext.removeLifecycleEventListener(mLifeCycleListener)
        isRouting = false
//        cleanUp()
        super.onStop()
    }

    override fun onDestroy() {
//        super.onDestroy()
        isRouting = false
    }

    private fun cleanUp() {
//        clearRouteLineView()
//        searchedRoutes = emptyList()
        if (mMapboxNavigation != null && !(mMapboxNavigation != null && mMapboxNavigation!!.isDestroyed)) {
            mMapboxNavigation?.setNavigationRoutes(searchedRoutes)
            mMapboxNavigation?.stopTripSession()
        }
        routeLineView?.cancel()
        routeLineApi?.cancel()
        maneuverApi?.cancel()
        mMapboxReplayer?.stop()
        MapboxNavigationProvider.destroy()
    }

    private fun setMapboxNavigation(shouldSimulate: Boolean) {
        if (mMapboxNavigation != null) {
            mMapboxNavigation!!.onDestroy()
            replayProgressObserver = null
            mMapboxReplayer = null
            replayLocationEngine = null
        }

        val currentLocale = resources.configuration.locales.get(0)
        val distanceFormatterOptions =
            DistanceFormatterOptions.Builder(context.applicationContext).locale(currentLocale).build()
        mMapboxNavigation = when {
            MapboxNavigationProvider.isCreated() -> {
                Log.d("MapboxNavigationProvider", "IsCreated");
                MapboxNavigationProvider.retrieve()
            }
            shouldSimulate -> {
                mMapboxReplayer = MapboxReplayer()
                replayLocationEngine = ReplayLocationEngine(mMapboxReplayer!!)
                replayProgressObserver = ReplayProgressObserver(mMapboxReplayer!!)
                Log.d("MapboxNavigationProvider", "ShouldSimulate");
                MapboxNavigationProvider.create(
                    NavigationOptions.Builder(context)
                        .accessToken(accessToken)
                        .distanceFormatterOptions(distanceFormatterOptions)
                        .locationEngine(replayLocationEngine!!)
                        .build()
                )
            }
            else -> {
                Log.d("MapboxNavigationProvider", "Real");
                MapboxNavigationProvider.create(
                    NavigationOptions.Builder(context)
                        .distanceFormatterOptions(distanceFormatterOptions)
                        .accessToken(accessToken)
                        .build()
                )
            }
        }

//        val distanceFormatterOptions = mMapboxNavigation!!.navigationOptions.distanceFormatterOptions
        maneuverApi = MapboxManeuverApi(
            MapboxDistanceFormatter(distanceFormatterOptions)
        )
        tripProgressApi = MapboxTripProgressApi(
            TripProgressUpdateFormatter.Builder(context)
                .distanceRemainingFormatter(
                    DistanceRemainingFormatter(distanceFormatterOptions)
                )
                .timeRemainingFormatter(
                    TimeRemainingFormatter(context)
                )
                .percentRouteTraveledFormatter(
                    PercentDistanceTraveledFormatter()
                )
                .estimatedTimeToArrivalFormatter(
                    EstimatedTimeToArrivalFormatter(context, TimeFormat.TWELVE_HOURS)
                )
                .build()
        )

        val customColorResources = RouteLineColorResources.Builder()
            .routeDefaultColor(Color.parseColor("#FF0000"))
            .inActiveRouteLegsColor(Color.parseColor("#FF0000"))
//            .routeLineTraveledCasingColor(Color.parseColor("#FF0000"))
            .routeClosureColor(Color.parseColor("#FF0000"))
            .routeUnknownCongestionColor(Color.parseColor("#FF0000"))
            .routeLowCongestionColor(Color.parseColor("#FF0000"))
            .routeSevereCongestionColor(Color.parseColor("#FF0000"))
            .restrictedRoadColor(Color.parseColor("#FF0000"))
            .routeCasingColor(Color.parseColor("#FF0000"))
            .alternativeRouteRestrictedRoadColor(Color.parseColor("#FF0000"))
            .build()

        val routeLineResources = RouteLineResources.Builder()
            .routeLineColorResources(customColorResources)
            .build()

        val mapboxRouteLineOptions = MapboxRouteLineOptions.Builder(context)
            .withRouteLineResources(routeLineResources)
            .withRouteLineBelowLayerId("road-label")
            .withVanishingRouteLineEnabled(true)
            .build()
        routeLineApi = MapboxRouteLineApi(mapboxRouteLineOptions)
        routeLineView = MapboxRouteLineView(mapboxRouteLineOptions)

    }

    private fun setLifecycleListeners() {
        val reactContext = context as ReactContext
        mLifeCycleListener = object : LifecycleEventListener {
            override fun onHostResume() {
                if (isRouting) {
                    registerObserver()
                }
            }

            override fun onHostPause() {
            }

            override fun onHostDestroy() {
//                onDestroy()
                dispose()
            }
        }
        reactContext.addLifecycleEventListener(mLifeCycleListener)
    }

    private fun setRouteNavigation(routes: List<NavigationRoute>) {
        searchedRoutes = routes

        // set routes, where the first route in the list is the primary route that
        // will be used for active guidance
        mMapboxNavigation?.setNavigationRoutes(routes)

        val routeLines = routes.toDirectionsRoutes().map { RouteLine(it, null) }
        routeLineApi.setNavigationRouteLines(
            routeLines
                .toNavigationRouteLines()
        ) { value ->
            getStyle {
                routeLineView.renderRouteDrawData(it, value)
            }
            if (routes.isNotEmpty()) {
                val onFindRouteSuccessEvent =
                    MapChangeEvent(this, EventTypes.ON_FIND_ROUTE_SUCCESS)
                mManager.handleEvent(onFindRouteSuccessEvent)
            }
        }
    }

    private fun registerObserver() {
        if (mMapboxNavigation == null || (mMapboxNavigation != null && mMapboxNavigation!!.isDestroyed)) return
        mMapboxNavigation?.registerRoutesObserver(routesObserver)
        mMapboxNavigation?.registerRouteProgressObserver(routeProgressObserver)
        mMapboxNavigation?.registerLocationObserver(locationObserver)
        mMapboxNavigation?.registerArrivalObserver(arrivalObserver)
        if (replayProgressObserver != null) {
            mMapboxNavigation?.registerRouteProgressObserver(replayProgressObserver!!)
        }
//        mMapboxNavigation?.registerVoiceInstructionsObserver(voiceInstructionsObserver)
        mMapboxNavigation?.registerOffRouteObserver(offRouteObserver)
    }

    private fun unregisterObserver() {
        if (mMapboxNavigation == null || (mMapboxNavigation != null && mMapboxNavigation!!.isDestroyed)) return

        mMapboxNavigation?.unregisterRoutesObserver(routesObserver)
        mMapboxNavigation?.unregisterRouteProgressObserver(routeProgressObserver)
        mMapboxNavigation?.unregisterArrivalObserver(arrivalObserver)
        mMapboxNavigation?.unregisterOffRouteObserver(offRouteObserver)
//        mMapboxNavigation?.unregisterVoiceInstructionsObserver(voiceInstructionsObserver)
        if (replayProgressObserver != null) {
            mMapboxNavigation?.unregisterRouteProgressObserver(replayProgressObserver!!)
        }
    }

    private fun stopNavigation(shouldSimulateRoute: Boolean) {
        if (shouldSimulateRoute) {
            mMapboxReplayer!!.stop()
        }
        mMapboxNavigation?.stopTripSession()

    }

    override fun onDetachedFromWindow() {
        dispose()
        cleanUp()
        super.onDetachedFromWindow()
    }


    fun dispose() {
        onDestroy()
    }

    /** React Method **/
    fun findRoute(origin: Point?, destination: Point?) {
        if (origin == null) {
            eventHelper?.sendErrorToReact(this, StatusType.ERROR_FIND_ROUTE_NO_ORIGIN, StatusCode.ERROR_FIND_ROUTE_NO_ORIGIN, "Unable to find route due no origin coordinate")
            return
        } else if (destination == null) {
            eventHelper?.sendErrorToReact(this, StatusType.ERROR_FIND_ROUTE_NO_DESTINATION, StatusCode.ERROR_FIND_ROUTE_NO_DESTINATION, "Unable to find route. Please select the destination")
            return
        }

        val routeBuilder = RouteOptions.builder()
            .applyDefaultNavigationOptions()
            .applyLanguageAndVoiceUnitOptions(context)
            .coordinatesList(listOf(origin, destination))
            .profile(DirectionsCriteria.PROFILE_DRIVING_TRAFFIC)
            .steps(true)

//        if (originLocation is Location) {
//            routeBuilder = routeBuilder.bearingsList(
//                listOf(
//                    Bearing.builder()
//                        .angle(originLocation.bearing.toDouble())
//                        .degrees(45.0)
//                        .build(),
//                    null
//                )
//            )
//        } else {
//            routeBuilder =  routeBuilder.bearingsList(
//                listOf(
//                    Bearing.builder()
//                        .degrees(45.0)
//                        .build(),
//                    null
//                )
//            )
//        }
        val routeOptions = routeBuilder.build()

        setMapboxNavigation(false)
        mMapboxNavigation!!.requestRoutes(
            routeOptions,
            object : NavigationRouterCallback {
                override fun onRoutesReady(
                    routes: List<NavigationRoute>,
                    routerOrigin: RouterOrigin
                ) {
                    if (routes.isEmpty()) {
                        searchedRoutes = emptyList()
                        eventHelper?.sendErrorToReact(this@AndroidMapboxView, StatusType.ERROR_FIND_ROUTE_NO_ROUTES, StatusCode.ERROR_FIND_ROUTE_NO_ROUTES, "Unable to find route from origin to destination")
                        return
                    }
                    setRouteNavigation(routes)
                }

                override fun onFailure(
                    reasons: List<RouterFailure>,
                    routeOptions: RouteOptions
                ) {
                    searchedRoutes = emptyList()
                    eventHelper?.sendErrorToReact(this@AndroidMapboxView, StatusType.ERROR_FIND_ROUTE_API, StatusCode.ERROR_FIND_ROUTE_API, reasons.firstOrNull()?.message?: "Unable to find route due unexpected error")
                }

                override fun onCanceled(routeOptions: RouteOptions, routerOrigin: RouterOrigin) {
                    searchedRoutes = emptyList()
//                    eventHelper?.sendErrorToReact(this@AndroidMapboxView, StatusType.ERROR_FIND_ROUTE_NO_ROUTES, StatusCode.ERROR_FIND_ROUTE_NO_ROUTES, "Unable to find route from origin to destination")
                }
            }
        )
    }

    private val moveListener = object: OnMoveListener {
        override fun onMoveBegin(moveGestureDetector: MoveGestureDetector) {
            moveGesturehandler.removeCallbacks(moveGestureRunnable)
            Log.d("moveGestureDetector", "user move begin")
            navigationCamera.requestNavigationCameraToIdle()
        }

        override fun onMove(moveGestureDetector: MoveGestureDetector): Boolean {
            Log.d("moveGestureDetector", "user still on moving")
            return false
        }

        override fun onMoveEnd(moveGestureDetector: MoveGestureDetector) {
            Log.d("moveGestureDetector", "user stop moving")
            moveGesturehandler.postDelayed(moveGestureRunnable, 3000)
//            NavigationCameraTransitionOptions.Builder().maxDuration(500L).build()
//            navigationCamera.requestNavigationCameraToFollowing()
        }
    }

    /** React Method **/
    fun startRoute(origin: Point?, shouldSimulate: Boolean) {
        if (origin == null) {
            eventHelper?.sendErrorToReact(this, StatusType.ERROR_SIMULATION_NO_ORIGIN, StatusCode.ERROR_SIMULATION_NO_ORIGIN, "Unable to start navigation due no origin")
            return
        }
        if (searchedRoutes.isEmpty()) {
            eventHelper?.sendErrorToReact(this, StatusType.ERROR_START_ROUTE_NO_ROUTES, StatusCode.ERROR_START_ROUTE_NO_ROUTES, "Unable to start navigation due no route")
            return
        }

//        val currentLocale = resources.configuration.locales.get(0)
//        speechApi = MapboxSpeechApi(context, accessToken, currentLocale.toLanguageTag())
//        voiceInstructionsPlayer = MapboxVoiceInstructionsPlayer(
//            context,
//            accessToken,
//            currentLocale.toLanguageTag()
//        )
//
//        voiceInstructionsPlayer!!.volume(SpeechVolume(0.5f))

        this.gestures.addOnMoveListener(moveListener)
        setMapboxNavigation(shouldSimulate)
        registerObserver()
        setRouteNavigation(searchedRoutes)

        val onNavigationStartedEvent = MapChangeEvent(this, EventTypes.ON_NAVIGATION_STARTED)
        mManager.handleEvent(onNavigationStartedEvent)
        mMapboxNavigation!!.startTripSession(true) // start listening session
        isRouting = true
        val mapAnimationOptionsBuilder = MapAnimationOptions.Builder()
        mapAnimationOptionsBuilder.duration(500L)

//        val cameraPosition = CameraOptions.Builder()
//            .center(origin)
//            .pitch(45.0)
//            .zoom(17.0)
//            .padding(EdgeInsets(200.0, 10.0, 100.0, 10.0))
//            .build()
//        // set camera position
//        getMapboxMap().setCamera(cameraPosition)

//        navigationCamera.requestNavigationCameraToFollowing(NavigationCameraTransitionOptions.Builder().maxDuration(500L).build())

        if (shouldSimulate) {
            //  start simulation
            // if simulation is enabled (ReplayLocationEngine set to NavigationOptions)
            // but we're not simulating yet,
            // push a single location sample to establish origin
            startSimulation(origin, searchedRoutes.first().directionsRoute)
        }
    }

    private fun startSimulation(origin: Point, route: DirectionsRoute) {
        mMapboxReplayer!!.run {
            stop()
            clearEvents()
//            mMapboxReplayer.pushEvents(
//                listOf(
//                    ReplayRouteMapper.mapToUpdateLocation(
//                        eventTimestamp = 0.0,
//                        point = origin
//                    )
//                )
//            )
            pushRealLocation(this@AndroidMapboxView.context, 0.0)
            val replayEvents =
                ReplayRouteMapper().mapDirectionsRouteGeometry(route)
            pushEvents(replayEvents)
            seekTo(replayEvents.first())
            play()
        }
    }

    /** React Method **/
    fun stopRoute(shouldSimulate: Boolean) {
        val cameraPosition = CameraOptions.Builder()
            .pitch(0.0)
            .zoom(14.0)
//            .padding(EdgeInsets(200.0, 10.0, 200.0, 10.0))
            .build()
        getMapboxMap().setCamera(cameraPosition)
        firstLocationUpdateReceived = false
        this.gestures.removeOnMoveListener(moveListener)
        isRouting = false
        unregisterObserver()
        stopNavigation(shouldSimulate)
        navigationCamera.requestNavigationCameraToIdle()

//        voiceInstructionsPlayer?.shutdown()
//        speechApi?.cancel()
    }

    fun clearRouteLineView() {
        getStyle {
            routeLineApi.clearRouteLine { value ->
                routeLineView.renderClearRouteLineValue(
                    it,
                    value
                )
            }
        }
        viewportDataSource.clearRouteData()
        viewportDataSource.evaluate()
    }

    /** REACT_METHOD **/
    fun clearRoute() {
        firstLocationUpdateReceived = false
        isRouting = false
        setRouteNavigation(emptyList())
    }

    /** REACT_METHOD **/
    fun recenter() {
        if (isRouting) {
            navigationCamera.requestNavigationCameraToFollowing()
        }
    }

    fun setOrigin(origin:Point?) {
        this.origin = origin
    }

    fun setDestination(destination:Point?) {
        this.destination = destination
    }

    init {
        scalebar.enabled = false
        compass.enabled = false
        setLifecycleListeners()
        setMapboxNavigation(false)
        eventHelper = EventHelper(manager = manager)
        viewportDataSource = MapboxNavigationViewportDataSource(getMapboxMap())

        val pixelDensity = context.resources.displayMetrics.density
        viewportDataSource.followingPadding = EdgeInsets(
            200.0 * pixelDensity,
            40.0 * pixelDensity,
            200.0 * pixelDensity,
            40.0 * pixelDensity
        )
        viewportDataSource.followingPitchPropertyOverride(45.0)
        viewportDataSource.followingZoomPropertyOverride(17.0)

        navigationCamera = NavigationCamera(
            getMapboxMap(),
            this.camera,
            viewportDataSource
        )

        camera.addCameraAnimationsLifecycleListener(
            NavigationBasicGesturesHandler(navigationCamera)
        )
    }

    companion object {
        const val LOG_TAG = "AndroidMapboxView"
    }
}