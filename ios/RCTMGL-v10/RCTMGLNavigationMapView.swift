//
//  RCTMGLNavigationMapView.swift
//  rnmapbox-maps
//
//  Created by Kevin Yulianto on 06/07/22.
//

@_spi(Restricted) import MapboxMaps
import Turf
import MapKit
import MapboxCoreNavigation
import MapboxNavigation
import MapboxDirections

@objc(RCTMGLNavigationMapView)
open class RCTMGLNavigationMapView: NavigationMapView {
    weak var selected : RCTMGLPointAnnotation? = nil
    
    var reactOnPress : RCTBubblingEventBlock?
    var reactOnLongPress : RCTBubblingEventBlock?
    var reactOnMapChange : RCTBubblingEventBlock?
    var reactOnMapError: RCTBubblingEventBlock?

    var styleLoaded: Bool = false
    var styleLoadWaiters : [(MapboxMap)->Void] = []

    var reactCamera : RCTMGLCamera?
    var images : [RCTMGLImages] = []
    var sources : [RCTMGLSource] = []
    
    // Routes
    var isRouting: Bool = false
    
    var routeIndex = 0 {
        didSet {
            showCurrentRoute()
        }
    }
    
    var routeResponse: RouteResponse? {
        didSet {
//            guard currentRoute != nil else {
//                self.removeRoutes()
//                return
//            }
            routeIndex = 0
        }
    }
    
    var routes: [Route]? {
        return routeResponse?.routes
    }
    
    var route: Route? {
        print("routeIndex \(routeIndex)")
        return routes?[routeIndex]
    }
 
    var currentNavigationRouteOptions: NavigationRouteOptions? = nil
    var navigationService: MapboxNavigationService? = nil
    

    var handleMapChangedEvents = Set<RCTMGLEvent.EventType>()
    
    var onStyleLoadedComponents: [RCTMGLMapComponent2] = []
    
    private var isPendingInitialLayout = true
    private var isGestureActive = false
    private var isAnimatingFromGesture = false

    var layerWaiters : [String:[(String) -> Void]] = [:]

    lazy var calloutAnnotationManager : MapboxMaps.PointAnnotationManager = {
        return self.mapView.annotations.makePointAnnotationManager(id: "rctmlg-callout")
    }()
    
    func showCurrentRoute() {
        guard let currentRoute = self.route else { return }
        self.overrideLineLayerColor()
        
        var routes = [currentRoute]
        routes.append(contentsOf: self.routes!.filter {
            $0 != currentRoute
        })
        
        self.showcase(routes, routesPresentationStyle: .all(shouldFit: true), animated: true)
        self.showWaypoints(on: currentRoute)
    }
    
    func addToMap(_ subview: UIView) {
      if let mapComponent = subview as? RCTMGLMapComponent2 {
        let style = self.mapView.mapboxMap.style
        if mapComponent.waitForStyleLoad() {
          onStyleLoadedComponents.append(mapComponent)
          if (style.isLoaded) {
              mapComponent.addToMap(self, style: style)
          }
        } else {
            mapComponent.addToMap(self, style: style)
        }
      } else {
        print("addToMap.Subviews: \(subview.reactSubviews())")
        subview.reactSubviews().forEach { addToMap($0) }
      }
      if let source = subview as? RCTMGLSource {
        sources.append(source)
      }
    }
    
    func removeFromMap(_ subview: UIView) {
      if let mapComponent = subview as? RCTMGLMapComponent2 {
        if mapComponent.waitForStyleLoad() {
          onStyleLoadedComponents.removeAll { $0 === mapComponent }
        }
        mapComponent.removeFromMap(self)
      } else {
        subview.reactSubviews().forEach { removeFromMap($0) }
      }
      if let source = subview as? RCTMGLSource {
        sources.removeAll { $0 == source }
      }
    }

    @objc open override func insertReactSubview(_ subview: UIView!, at atIndex: Int) {
      addToMap(subview)
      super.insertReactSubview(subview, at: atIndex)
    }
    
    @objc open override func removeReactSubview(_ subview:UIView!) {
      removeFromMap(subview)
      super.removeReactSubview(subview)
    }

    public required override init(frame:CGRect) {
      ResourceOptionsManager.init(accessToken: MGLModule.accessToken!)
      super.init(frame: frame)

      self.mapView.gestures.delegate = self.mapView as? GestureManagerDelegate

      self.delegate = self
        
      self.userLocationStyle = nil
    
//      overrideLineLayerColor()
      setupEvents()
        
      //@kevin: test add view port source
      self.navigationCamera.viewportDataSource = CustomViewportDataSource(self.mapView)
      self.navigationCamera.cameraStateTransition = CustomCameraStateTransition(self.mapView)
    }
    
    public required init (coder: NSCoder) {
        fatalError("not implemented")
    }
    
    func layerAdded (_ layer: Layer) {
        // TODO
    }
    
    func overrideLineLayerColor() {
        let red : UIColor = UIColor(red: 1.0, green: 0, blue: 0, alpha: 1)
        let lineColor : UIColor = UIColor.init(rgb: 0x92140C)
        let lineGrayColor : UIColor = UIColor.init(rgb: 0xADADAD)
        self.routeCasingColor = lineColor
        self.routeAlternateColor = lineGrayColor
        self.routeAlternateCasingColor = lineGrayColor
        self.traversedRouteColor = lineGrayColor
        self.maneuverArrowColor = lineColor
        self.maneuverArrowStrokeColor = lineColor
        

        self.trafficUnknownColor = lineColor
        self.trafficLowColor = lineColor
        self.trafficModerateColor = lineColor
        self.trafficHeavyColor = lineColor
        self.trafficSevereColor = lineColor
        self.alternativeTrafficUnknownColor = lineGrayColor
        self.alternativeTrafficLowColor = lineGrayColor
        self.alternativeTrafficModerateColor = lineGrayColor
        self.alternativeTrafficHeavyColor = lineGrayColor
        self.alternativeTrafficSevereColor = lineGrayColor
        self.routeAlternateCasingColor = lineColor
        self.routeRestrictedAreaColor = lineColor
    }
    
    func waitForLayerWithID(_ layerId: String, _  callback: @escaping (_ layerId: String) -> Void) {
      let style = self.mapView.mapboxMap.style;
      if style.layerExists(withId: layerId) {
        callback(layerId)
      } else {
        layerWaiters[layerId, default: []].append(callback)
      }
    }
    
    @objc public override func layoutSubviews() {
      super.layoutSubviews()
      if let camera = reactCamera {
        if (isPendingInitialLayout) {
          isPendingInitialLayout = false;

          camera.initialLayout()
        }
      }
    }

    public override func updateConstraints() {
      super.updateConstraints()
      if let camera = reactCamera {
        if (isPendingInitialLayout) {
          isPendingInitialLayout = false;

          camera.initialLayout()
        }
      }
    }
    
    func annotationManager(_ manager: AnnotationManager, didDetectTappedAnnotations annotations: [Annotation]) {
      guard annotations.count > 0 else {
        fatalError("didDetectTappedAnnotations: No annotations found")
      }

      for annotation in annotations {
        if let pointAnnotation = annotation as? PointAnnotation,
           let userInfo = pointAnnotation.userInfo {

          if let rctmglPointAnnotation = userInfo[RCTMGLPointAnnotation.key] as? WeakRef<RCTMGLPointAnnotation> {
            if let pt = rctmglPointAnnotation.object {
              if let selected = selected {
                selected.onDeselect()
              }
              pt.onSelect()
              selected = pt
            }
          }
        }
        /*

           let rctmglPointAnnotation = userInfo[RCTMGLPointAnnotation.key] as? WeakRef<RCTMGLPointAnnotation>,
           let rctmglPointAnnotation = rctmglPointAnnotation.object {
          rctmglPointAnnotation.didTap()
        }*/
      }
    }
    
    func handleTap(_ tap: UITapGestureRecognizer,  noAnnotationFound: @escaping (UITapGestureRecognizer) -> Void) {
      guard let layerId = self.pointAnnotationManager?.layerId else {
        return
      }
      guard let mapFeatureQueryable = mapView?.mapboxMap else {
        noAnnotationFound(tap)
        return
      }
      let options = RenderedQueryOptions(layerIds: [layerId], filter: nil)
      mapFeatureQueryable.queryRenderedFeatures(
          at: tap.location(in: tap.view),
          options: options) { [weak self] (result) in

          guard let self = self else { return }

          switch result {

          case .success(let queriedFeatures):

              // Get the identifiers of all the queried features
              let queriedFeatureIds: [String] = queriedFeatures.compactMap {
                  guard case let .string(featureId) = $0.feature.identifier else {
                      return nil
                  }
                  return featureId
              }

              // Find if any `queriedFeatureIds` match an annotation's `id`
              let tappedAnnotations = self.pointAnnotationManager!.annotations.filter { queriedFeatureIds.contains($0.id) }

              // If `tappedAnnotations` is not empty, call delegate
              if !tappedAnnotations.isEmpty {
                self.annotationManager(
                  self.pointAnnotationManager!,
                  didDetectTappedAnnotations: tappedAnnotations)

              } else {
                noAnnotationFound(tap)
              }

          case .failure(let error):
            noAnnotationFound(tap)
            Logger.log(level:.warn, message:"Failed to query map for annotations due to error: \(error)")

          }
      }
    }

    
    // MARK: - React Native properties
    
    @objc func setReactAttributionEnabled(_ value: Bool) {
      mapView.ornaments.options.attributionButton.visibility = value ? .visible : .hidden
    }
    
    @objc func setReactAttributionPosition(_ position: [String: Int]!) {
      if let ornamentOptions = self.getOrnamentOptionsFromPosition(position) {
        mapView.ornaments.options.attributionButton.position = ornamentOptions.position
        mapView.ornaments.options.attributionButton.margins = ornamentOptions.margins
      }
    }
    
    @objc func setReactLogoEnabled(_ value: Bool) {
      mapView.ornaments.options.logo.visibility = value ? .visible : .hidden
    }
    
    @objc func setReactLogoPosition(_ position: [String: Int]!) {
      if let ornamentOptions = self.getOrnamentOptionsFromPosition(position) {
        mapView.ornaments.options.logo.position = ornamentOptions.position
        mapView.ornaments.options.logo.margins = ornamentOptions.margins
      }
    }
    
    @objc func setReactCompassEnabled(_ value: Bool) {
      mapView.ornaments.options.compass.visibility = value ? .visible : .hidden
    }
    
    @objc func setReactCompassPosition(_ position: [String: Int]!) {
      if let ornamentOptions = self.getOrnamentOptionsFromPosition(position) {
        mapView.ornaments.options.compass.position = ornamentOptions.position
        mapView.ornaments.options.compass.margins = ornamentOptions.margins
      }
    }
    
    @objc func setReactScaleBarEnabled(_ value: Bool) {
      self.mapView.ornaments.options.scaleBar.visibility = value ? .visible : .hidden
    }
    
    @objc func setReactScaleBarPosition(_ position: [String: Int]!) {
      if let ornamentOptions = self.getOrnamentOptionsFromPosition(position) {
        mapView.ornaments.options.scaleBar.position = ornamentOptions.position
        mapView.ornaments.options.scaleBar.margins = ornamentOptions.margins
      }
    }

    @objc func setReactZoomEnabled(_ value: Bool) {
      self.mapView.gestures.options.quickZoomEnabled = value
      self.mapView.gestures.options.doubleTapToZoomInEnabled = value
      self.mapView.gestures.options.pinchZoomEnabled = value
    }

    @objc func setReactScrollEnabled(_ value: Bool) {
      self.mapView.gestures.options.panEnabled = value
      self.mapView.gestures.options.pinchPanEnabled = value
    }

    @objc func setReactRotateEnabled(_ value: Bool) {
      self.mapView.gestures.options.pinchRotateEnabled = value
    }

    @objc func setReactPitchEnabled(_ value: Bool) {
      self.mapView.gestures.options.pitchEnabled = value
    }
    
    @objc func setReactStyleURL(_ value: String?) {
      self.styleLoaded = false
      if let value = value {
        if let _ = URL(string: value) {
          mapView.mapboxMap.loadStyleURI(StyleURI(rawValue: value)!)
        } else {
          if RCTJSONParse(value, nil) != nil {
            mapView.mapboxMap.loadStyleJSON(value)
          }
        }
      }
    }

    private func getOrnamentOptionsFromPosition(_ position: [String: Int]!) -> (position: OrnamentPosition, margins: CGPoint)? {
      let left = position["left"]
      let right = position["right"]
      let top = position["top"]
      let bottom = position["bottom"]
      
      if let left = left, let top = top {
        return (OrnamentPosition.topLeft, CGPoint(x: left, y: top))
      } else if let right = right, let top = top {
        return (OrnamentPosition.topRight, CGPoint(x: right, y: top))
      } else if let bottom = bottom, let right = right {
        return (OrnamentPosition.bottomRight, CGPoint(x: right, y: bottom))
      } else if let bottom = bottom, let left = left {
        return (OrnamentPosition.bottomLeft, CGPoint(x: left, y: bottom))
      }
      
      return nil
    }
}


// MARK: - navigation
extension RCTMGLNavigationMapView: NavigationServiceDelegate {
    
    private func setNavigationService(_ service: MapboxNavigationService) {
          self.navigationService = service
    }
    
    
    // STUB
    public func navigationService(_ service: NavigationService, didUpdate progress: RouteProgress, with location: CLLocation, rawLocation: CLLocation) {
    
        let locationMatchEvent = RCTMGLEvent(type: .OnLocationMatcherChange, payload: [
            "latitude": location.coordinate.latitude,
            "longitude": location.coordinate.longitude
        ])
        self.fireEvent(event:locationMatchEvent, callback: reactOnMapChange)
        
        let activeLegPayload = [
            "currentStepName": progress.currentLegProgress.currentStepProgress.step.names ?? [],
            "currentStepIndex": progress.currentLegProgress.stepIndex
        ] as [String : Any]
        
        let distanceRemaining = progress.distanceRemaining
        let distance = distanceRemaining > 5 ? distanceRemaining : 0
        
        let distanceFormatter = DistanceFormatter()
        let distanceRemainingDisplay = distanceFormatter.attributedString(for: distance)!.string
        print("distance: \(distance)")
        print("distanceRemainingDisplay: \(distanceRemainingDisplay)")
        
        let routeProgressEvent = RCTMGLEvent(type: .OnRouteProgressChange, payload: [
            "activeLegPayload": activeLegPayload,
            "distanceTraveled": progress.distanceTraveled,
            "distanceRemaining": distance,
            "distanceRemainingDisplay": distanceRemainingDisplay,
            "durationRemaining": progress.durationRemaining,
            "fractionTraveled": progress.fractionTraveled,
        ])
        self.fireEvent(event: routeProgressEvent, callback: reactOnMapChange)
        
        
        //@Kevin: test
        let customViewportDataSource = self.navigationCamera.viewportDataSource as? CustomViewportDataSource;
        customViewportDataSource?.progressRouteDidChange(location, routeProgress: progress);
        
//        if progress.isFinalLeg && progress.currentLegProgress.userHasArrivedAtWaypoint && progress.currentLegProgress.distanceRemaining <= 0 {
//            self.removeRoutes()
//        } else {
//            updateUpcomingRoutePointIndex(routeProgress: progress)
//            travelAlongRouteLine(to: location.coordinate)
//        }

        // update route line
//        self.show([progress.route], legIndex: progress.legIndex)
        
        
        updateRouteLine(routeProgress: progress, coordinate: CLLocationCoordinate2D.init(latitude: location.coordinate.latitude, longitude: location.coordinate.longitude), shouldRedraw: true)
        
    }
    
    public func navigationService(_ service: NavigationService, willBeginSimulating progress: RouteProgress, becauseOf reason: SimulationIntent) {
        let event = RCTMGLEvent(type: .OnNavigationStarted, payload: nil);
        self.fireEvent(event:event, callback: reactOnMapChange)
    }
    
    public func navigationService(_ service: NavigationService, didFailToRerouteWith error: Error) {
        print("failReroute:", error.localizedDescription)
    }
    
    public func navigationService(_ service: NavigationService, didArriveAt waypoint: Waypoint) -> Bool {
        let locationMatchEvent = RCTMGLEvent(type: .OnLocationMatcherChange, payload: [
            "latitude": waypoint.coordinate.latitude,
            "longitude": waypoint.coordinate.longitude
        ])
        self.fireEvent(event: locationMatchEvent, callback: reactOnMapChange)
        
        let onArrivalEvent = RCTMGLEvent(type: .OnArrival, payload: nil);
        self.fireEvent(event:onArrivalEvent, callback: reactOnMapChange)
        self.navigationService?.endNavigation()
        return true
    }
    
    public func navigationService(_ service: NavigationService, didEndSimulating progress: RouteProgress, becauseOf reason: SimulationIntent) {
        self.navigationService = nil;
    }
    
    @objc
    func startRoute(_ origin:[NSNumber]?, shouldSimulate: Bool) -> Void {
        if (self.routeResponse == nil || self.routeResponse?.routes == nil || self.currentNavigationRouteOptions == nil) {
            let payload = [
                "code": RCTMGLEvent.StatusCode.ERROR_START_ROUTE_NO_ROUTES.rawValue,
                "statusText": RCTMGLEvent.StatusType.ERROR_START_ROUTE_NO_ROUTES.rawValue,
                "message": "Unable to start navigation due no route"
            ] as [String : Any]
            RCTMGLUtils.errorCallback(.OnError, payload: payload, callback: reactOnMapError)
            return
        }
        
        let simulating = shouldSimulate ? SimulationMode.always : SimulationMode.never
        
        if let mapView = self.mapView {
            let customViewportDataSource = CustomViewportDataSource(mapView)
            self.navigationCamera.viewportDataSource = customViewportDataSource
             
            let customCameraStateTransition = CustomCameraStateTransition(mapView)
            self.navigationCamera.cameraStateTransition = customCameraStateTransition
        }
        
        
//        let navigationLocManager = NavigationLocationManager()
//        navigationLocManager.simulatesLocation = shouldSimulate
        let navigationService = MapboxNavigationService(routeResponse: self.routeResponse!, routeIndex: routeIndex, routeOptions: self.currentNavigationRouteOptions!, customRoutingProvider: NavigationSettings.shared.directions, credentials: NavigationSettings.shared.directions.credentials, locationSource: nil, eventsManagerType: nil, simulating: simulating, routerType: nil)
     
        navigationService.delegate = self
        
        self.routeLineTracksTraversal = true
        
        //to speed up the simulation
        navigationService.simulationSpeedMultiplier = 3.0
        navigationService.start()
        
        //camera start following
        self.navigationCamera.follow()
        
        self.isRouting = true
        self.navigationService = navigationService
    }
    
    @objc
    func stopRoute() -> Void {
        self.navigationCamera.stop()
        self.isRouting = false
        self.navigationService?.stop()
        self.navigationService?.endNavigation()
    }
    
    @objc
    func clearRoute() -> Void {
        self.stopRoute()
        self.removeRoutes()
        self.routeResponse = nil
        self.routeIndex = 0;
        self.currentNavigationRouteOptions = nil
    }
    
    @objc
    func findRoute(_ origin:[NSNumber]?, destination:[NSNumber]?) -> Void {
        guard let origins = origin else {
            let payload = [
                "code": RCTMGLEvent.StatusCode.ERROR_FIND_ROUTE_NO_ORIGIN.rawValue,
                "statusText": RCTMGLEvent.StatusType.ERROR_FIND_ROUTE_NO_ORIGIN.rawValue,
                "message": "Unable to find route due no origin coordinate"
            ] as [String : Any]
            RCTMGLUtils.errorCallback(.OnError, payload: payload, callback: reactOnMapError)
            return
        }

        guard let destinations = destination else {
            let payload = [
                "code": RCTMGLEvent.StatusCode.ERROR_FIND_ROUTE_NO_DESTINATION.rawValue,
                "statusText": RCTMGLEvent.StatusType.ERROR_FIND_ROUTE_NO_DESTINATION.rawValue,
                "message": "Unable to find route due no origin coordinate"
            ] as [String : Any]
            RCTMGLUtils.errorCallback(.OnError, payload: payload , callback: reactOnMapError)
            return
        }
      
        let originWaypoint = Waypoint(coordinate: CLLocationCoordinate2D(latitude: origins[1] as! CLLocationDegrees, longitude: origins[0] as! CLLocationDegrees))

        let destinationWaypoint = Waypoint(coordinate: CLLocationCoordinate2D(latitude: destinations[1] as! CLLocationDegrees, longitude: destinations[0] as! CLLocationDegrees))
        let routeOptions = NavigationRouteOptions(waypoints: [originWaypoint, destinationWaypoint], profileIdentifier: .automobile)
        
        // request routes
        Directions.shared.calculate(routeOptions) { [weak self] (session, result) in
            switch result {
                case .failure(let error):
                    let payload = [
                        "code": RCTMGLEvent.StatusCode.ERROR_FIND_ROUTE_API.rawValue,
                        "statusText": RCTMGLEvent.StatusType.ERROR_FIND_ROUTE_API.rawValue,
                        "message": error.localizedDescription
                    ] as [String : Any]
                    RCTMGLUtils.errorCallback(.OnError, payload: payload , callback: self?.reactOnMapError)
                case .success(let response): do {
                    guard self != nil else {
                            return
                        }
                    
                    if (response.routes == nil || response.routes!.isEmpty) {
                        let payload = [
                            "code": RCTMGLEvent.StatusCode.ERROR_FIND_ROUTE_NO_ROUTES.rawValue,
                            "statusText": RCTMGLEvent.StatusType.ERROR_FIND_ROUTE_NO_ROUTES.rawValue,
                            "message": "Unable to find route from origin to destination"
                        ] as [String : Any]
                        RCTMGLUtils.errorCallback(.OnError, payload: payload , callback: self?.reactOnMapError)
                        return;
                    }
                
                    self?.routeResponse = response
                    self?.currentNavigationRouteOptions = routeOptions
                    self?.overrideLineLayerColor()
                    
                    if let routes = self?.routes,
                       let currentRoute = self?.route {
                       self?.showcase(routes, routesPresentationStyle: .all(shouldFit: true), animated: true)
                       self?.showWaypoints(on: currentRoute)
                       let event = RCTMGLEvent(type: .OnFindRouteSuccess, payload: nil)
                       self?.fireEvent(event: event, callback: self?.reactOnMapChange)
                    }
                    
                    
//                    let route = response.routes?.first ?? nil
//                    if ((route) != nil) {
//                        self?.showWaypoints(on: route!)
//                    }
                
                }
            }
        }
    }
    
}


// MARK: - NavigationMapViewDelegate
extension RCTMGLNavigationMapView: NavigationMapViewDelegate {
    
    func lineWidthExpression(_ multiplier: Double = 1.0) -> Expression {
        let lineWidthExpression = Exp(.interpolate) {
            Exp(.linear)
            Exp(.zoom)
            // It's possible to change route line width depending on zoom level, by using expression
            // instead of constant. Navigation SDK for iOS also exposes `RouteLineWidthByZoomLevel`
            // public property, which contains default values for route lines on specific zoom levels.
            RouteLineWidthByZoomLevel.multiplied(by: multiplier)
        }
     
        return lineWidthExpression
    }
    
    public func navigationMapView(_ navigationMapView: NavigationMapView, routeLineLayerWithIdentifier identifier: String, sourceIdentifier: String) -> LineLayer? {
        var lineLayer = LineLayer(id: identifier)
        lineLayer.source = sourceIdentifier
        

        // `identifier` parameter contains unique identifier of the route layer or its casing.
        // Such identifier consists of several parts: unique address of route object, whether route is
        // main or alternative, and whether route is casing or not. For example: identifier for
        // main route line will look like this: `0x0000600001168000.main.route_line`, and for
        // alternative route line casing will look like this: `0x0000600001ddee80.alternative.route_line_casing`.
        
        let redColor : UIColor = UIColor.init(rgb: 0x92140C)
        let grayColor : UIColor = UIColor.init(rgb: 0xADADAD)

        lineLayer.lineColor = .constant(.init(identifier.contains("main") ? redColor : grayColor))
//        lineLayer.lineWidth = .constant(8.0)
        lineLayer.lineWidth = .expression(lineWidthExpression())
        lineLayer.lineJoin = .constant(.round)
        lineLayer.lineCap = .constant(.round)
        return lineLayer
    }

    public func navigationMapView(_ navigationMapView: NavigationMapView, routeCasingLineLayerWithIdentifier identifier: String, sourceIdentifier: String) -> LineLayer? {
        var lineLayer = LineLayer(id: identifier)
        lineLayer.source = sourceIdentifier

//        let lineColor : UIColor = UIColor.init(rgb: 0x92140C)
//        let lineGrayColor : UIColor = UIColor.init(rgb: 0xADADAD)
//        lineLayer.lineColor = .constant(.init(#colorLiteral(red: 1.0, green: 0, blue: 0, alpha: 1)))
//        lineLayer.lineColor = .constant(.init(lineGrayColor))
        
        // Based on information stored in `identifier` property (whether route line is main or not)
        // route line will be colored differently.
        
        let redColor : UIColor = UIColor.init(rgb: 0x92140C)
        let grayColor : UIColor = UIColor.init(rgb: 0xADADAD)

        
        lineLayer.lineColor = .constant(.init(identifier.contains("main") ? redColor : grayColor))
//        lineLayer.lineWidth = .constant(8.0)
        lineLayer.lineWidth = .expression(lineWidthExpression(1.2))
        lineLayer.lineJoin = .constant(.round)
        lineLayer.lineCap = .constant(.round)
        return lineLayer
    }

    public func navigationMapView(_ navigationMapView: NavigationMapView, didSelect route: Route) {
        self.routeIndex = self.routes?.firstIndex(of: route) ?? 0
    }
    
}

// MARK: - event handlers

extension RCTMGLNavigationMapView {
  @objc func setReactOnMapError(_ value: @escaping RCTBubblingEventBlock) {
     self.reactOnMapError = value
  }
    
  @objc func setReactOnMapChange(_ value: @escaping RCTBubblingEventBlock) {
    self.reactOnMapChange = value
    
    self.mapView.mapboxMap.onEvery(.cameraChanged, handler: { cameraEvent in
      if self.handleMapChangedEvents.contains(.regionIsChanging) {
        let event = RCTMGLEvent(type:.regionIsChanging, payload: self.buildRegionObject());
        self.fireEvent(event: event, callback: self.reactOnMapChange)
      } else if self.handleMapChangedEvents.contains(.cameraChanged) {
        let event = RCTMGLEvent(type:.cameraChanged, payload: self.buildStateObject());
        self.fireEvent(event: event, callback: self.reactOnMapChange)
      }
    })

    self.mapView.mapboxMap.onEvery(.mapIdle, handler: { cameraEvent in
      if self.handleMapChangedEvents.contains(.regionDidChange) {
        let event = RCTMGLEvent(type:.regionDidChange, payload: self.buildRegionObject());
        self.fireEvent(event: event, callback: self.reactOnMapChange)
      } else if self.handleMapChangedEvents.contains(.mapIdle) {
        let event = RCTMGLEvent(type:.mapIdle, payload: self.buildStateObject());
        self.fireEvent(event: event, callback: self.reactOnMapChange)
      }
    })
  }

  private func fireEvent(event: RCTMGLEvent, callback: RCTBubblingEventBlock?) {
    guard let callback = callback else {
      Logger.log(level: .error, message: "fireEvent failed: \(event) - callback is null")
      return
    }
    fireEvent(event: event, callback: callback)
  }

  private func fireEvent(event: RCTMGLEvent, callback: @escaping RCTBubblingEventBlock) {
    callback(event.toJSON())
  }
  
  private func buildStateObject() -> [String: [String: Any]] {
    let cameraOptions = CameraOptions(cameraState: self.mapView.cameraState)
    let bounds = mapView.mapboxMap.coordinateBounds(for: cameraOptions)
    
    return [
      "properties": [
        "center": Point(mapView.cameraState.center).coordinates.toArray(),
        "bounds": [
          "ne": bounds.northeast.toArray(),
          "sw": bounds.southwest.toArray()
        ],
        "zoom" : Double(mapView.cameraState.zoom),
        "heading": Double(mapView.cameraState.bearing),
        "pitch": Double(mapView.cameraState.pitch),
      ],
      "gestures": [
        "isGestureActive": isGestureActive,
        "isAnimatingFromGesture": isAnimatingFromGesture
      ]
    ]
  }
  
  private func buildRegionObject() -> [String: Any] {
    let cameraOptions = CameraOptions(cameraState: self.mapView.cameraState)
    let bounds = mapView.mapboxMap.coordinateBounds(for: cameraOptions)
    let boundsArray : JSONArray = [
      [.number(bounds.northeast.longitude),.number(bounds.northeast.latitude)],
      [.number(bounds.southwest.longitude),.number(bounds.southwest.latitude)]
    ]

    var result = Feature(
       geometry: .point(Point(mapView.cameraState.center))
    )
    result.properties = [
      "zoomLevel": .number(mapView.cameraState.zoom),
      "heading": .number(mapView.cameraState.bearing),
      "bearing": .number(mapView.cameraState.bearing),
      "pitch": .number(mapView.cameraState.pitch),
      "visibleBounds": .array(boundsArray),
      "isUserInteraction": .boolean(isGestureActive),
      "isAnimatingFromUserInteraction": .boolean(isAnimatingFromGesture),
    ]
    return logged("buildRegionObject", errorResult: { ["error":["toJSON":$0.localizedDescription]] }) {
      try result.toJSON()
    }
  }
  
  public func setupEvents() {
    self.mapView.mapboxMap.onEvery(.mapLoadingError, handler: {(event) in
      if let data = event.data as? [String:Any], let message = data["message"] {
        Logger.log(level: .error, message: "MapLoad error \(message)")
      } else {
        Logger.log(level: .error, message: "MapLoad error \(event)")
      }
    })
    
    self.mapView.mapboxMap.onEvery(.styleImageMissing) { (event) in
      if let data = event.data as? [String:Any] {
        if let imageName = data["id"] as? String {

          self.images.forEach {
            if $0.addMissingImageToStyle(style: self.mapView.mapboxMap.style, imageName: imageName) {
              return
            }
          }
          
          self.images.forEach {
            $0.sendImageMissingEvent(imageName: imageName, event: event)
          }
        }
      }
    }

    self.mapView.mapboxMap.onEvery(.renderFrameFinished, handler: { (event) in
      var type = RCTMGLEvent.EventType.didFinishRendering
      var payload : [String:Any]? = nil
      if let data = event.data as? [String:Any] {
        if let renderMode = data["render-mode"], let renderMode = renderMode as? String, renderMode == "full" {
          type = .didFinishRenderingFully
        }
        payload = data
      }
      let event = RCTMGLEvent(type: type, payload: payload);
      self.fireEvent(event: event, callback: self.reactOnMapChange)
    })

     self.mapView.mapboxMap.onNext(.mapLoaded, handler: { (event) in
      let event = RCTMGLEvent(type:.didFinishLoadingMap, payload: nil);
      self.fireEvent(event: event, callback: self.reactOnMapChange)
    })
    
    self.mapView.mapboxMap.onEvery(.styleLoaded, handler: { (event) in
      self.onStyleLoadedComponents.forEach { (component) in
        component.addToMap(self, style: self.mapView.mapboxMap.style)
      }

      if !self.styleLoaded {
        self.styleLoaded = true
        if let mapboxMap = self.mapView.mapboxMap {
          let waiters = self.styleLoadWaiters
          self.styleLoadWaiters = []
          waiters.forEach { $0(mapboxMap) }
        }
      }

      let event = RCTMGLEvent(type:.didFinishLoadingStyle, payload: nil)
      self.fireEvent(event: event, callback: self.reactOnMapChange)
    })
  }
}

// MARK: - gestures

extension RCTMGLNavigationMapView {
  @objc func setReactOnPress(_ value: @escaping RCTBubblingEventBlock) {
    self.reactOnPress = value
    
//    self.mapView.gestures.singleTapGestureRecognizer.removeTarget(self.pointAnnotationManager, action: nil)
//    self.mapView.gestures.singleTapGestureRecognizer.addTarget(self, action: #selector(doHandleTap(_:)))
  }

  @objc func setReactOnLongPress(_ value: @escaping RCTBubblingEventBlock) {
    self.reactOnLongPress = value

    let longPressGestureRecognizer = UILongPressGestureRecognizer(target: self, action: #selector(doHandleLongPress(_:)))
    self.mapView.addGestureRecognizer(longPressGestureRecognizer)
  }
    
    @objc
    func recenter() -> Void {
        if (self.isRouting) {
            self.navigationCamera.follow()
        }
    }
    
    @objc
    func changeCameraBearingMode(_ mode: String) -> Void {
        let customViewportDataSource = self.navigationCamera.viewportDataSource as? CustomViewportDataSource;
        customViewportDataSource?.changeCameraBearingMode(mode);
    }
 
}

extension RCTMGLNavigationMapView: GestureManagerDelegate {
  private func touchableSources() -> [RCTMGLSource] {
    return sources.filter { $0.isTouchable() }
  }

  private func doHandleTapInSources(sources: [RCTMGLSource], tapPoint: CGPoint, hits: [String: [QueriedFeature]], touchedSources: [RCTMGLSource], callback: @escaping (_ hits: [String: [QueriedFeature]], _ touchedSources: [RCTMGLSource]) -> Void) {
    DispatchQueue.main.async {
      if let source = sources.first {
        let hitbox = source.hitbox;
        
        let halfWidth = (hitbox["width"]?.doubleValue ?? RCTMGLSource.hitboxDefault) / 2.0;
        let halfHeight = (hitbox["height"]?.doubleValue  ?? RCTMGLSource.hitboxDefault) / 2.0;

        let top = tapPoint.y - halfHeight;
        let left = tapPoint.x - halfWidth;
        
        let hitboxRect = CGRect(x: left, y: top, width: halfWidth * 2.0, height: halfHeight * 2.0)

        let options = RenderedQueryOptions(
          layerIds: source.getLayerIDs(), filter: nil
        )
          self.mapView.mapboxMap.queryRenderedFeatures(in: hitboxRect, options: options) {
          result in
          
          var newHits = hits
          var newTouchedSources = touchedSources;
          switch result {
           case .failure(let error):
            Logger.log(level: .error, message: "Error during handleTapInSources source.id=\(source.id ?? "n/a") error:\(error)")
          case .success(let features):
            if !features.isEmpty {
              newHits[source.id] = features
              newTouchedSources.append(source)
            }
            break
          }
          var nSources = sources
          nSources.removeFirst()
          self.doHandleTapInSources(sources: nSources, tapPoint: tapPoint, hits: newHits, touchedSources: newTouchedSources, callback: callback)
        }
      } else {
        callback(hits, touchedSources)
      }
    }
  }
  
  func highestZIndex(sources: [RCTMGLSource]) -> RCTMGLSource? {
    return sources.first
  }
  
  @objc
  func doHandleTap(_ sender: UITapGestureRecognizer) {
    let tapPoint = sender.location(in: self)
      self.handleTap(sender) { (_: UITapGestureRecognizer) in
      DispatchQueue.main.async {
        let touchableSources = self.touchableSources()
        self.doHandleTapInSources(sources: touchableSources, tapPoint: tapPoint, hits: [:], touchedSources: []) { (hits, touchedSources) in
          
          if let source = self.highestZIndex(sources: touchedSources),
             source.hasPressListener,
             let onPress = source.onPress {
            guard let hitFeatures = hits[source.id] else {
              Logger.log(level:.error, message: "doHandleTap, no hits found when it should have")
              return
            }
            let features = hitFeatures.compactMap { queriedFeature in
              logged("doHandleTap.hitFeatures") { try queriedFeature.feature.toJSON() } }
            let location = self.mapView.mapboxMap.coordinate(for: tapPoint)
            let event = RCTMGLEvent(
              type: (source is RCTMGLVectorSource) ? .vectorSourceLayerPress : .shapeSourceLayerPress,
              payload: [
                "features": features,
                "point": [
                  "x": Double(tapPoint.x),
                  "y": Double(tapPoint.y),
                ],
                "coordinates": [
                  "latitude": Double(location.latitude),
                  "longitude": Double(location.longitude),
                ]
              ]
            )
            self.fireEvent(event: event, callback: onPress)
            
          } else {
            if let reactOnPress = self.reactOnPress {
              let location = self.mapView.mapboxMap.coordinate(for: tapPoint)
              var geojson = Feature(geometry: .point(Point(location)));
              geojson.properties = [
                "screenPointX": .number(Double(tapPoint.x)),
                "screenPointY": .number(Double(tapPoint.y))
              ]
              let event = RCTMGLEvent(type:.tap, payload: logged("reactOnPress") { try geojson.toJSON() })
              self.fireEvent(event: event, callback: reactOnPress)
            }
          }
        }
      }
    }
  }
  
  @objc
  func doHandleLongPress(_ sender: UILongPressGestureRecognizer) {
    let position = sender.location(in: self)

    if let reactOnLongPress = self.reactOnLongPress, sender.state == .began {
      let coordinate = self.mapView.mapboxMap.coordinate(for: position)
      var geojson = Feature(geometry: .point(Point(coordinate)));
      geojson.properties = [
        "screenPointX": .number(Double(position.x)),
        "screenPointY": .number(Double(position.y))
      ]
      let event = RCTMGLEvent(type:.longPress, payload: logged("doHandleLongPress") { try geojson.toJSON() })
      self.fireEvent(event: event, callback: reactOnLongPress)
    }
  }
  
  public func gestureManager(_ gestureManager: GestureManager, didBegin gestureType: GestureType) {
    isGestureActive = true
  }
  
  public func gestureManager(_ gestureManager: GestureManager, didEnd gestureType: GestureType, willAnimate: Bool) {
    isGestureActive = false
    if willAnimate {
      isAnimatingFromGesture = true
    }
  }
  
  public func gestureManager(_ gestureManager: GestureManager, didEndAnimatingFor gestureType: GestureType) {
    isGestureActive = false
    isAnimatingFromGesture = false
  }
}

extension RCTMGLNavigationMapView
{
  @objc func takeSnap(
    writeToDisk:Bool) -> URL
  {
    UIGraphicsBeginImageContextWithOptions(self.bounds.size, true, 0);

    self.drawHierarchy(in: self.bounds, afterScreenUpdates: true)
    let snapshot = UIGraphicsGetImageFromCurrentImageContext();
    UIGraphicsEndImageContext();

    return writeToDisk ? RNMBImageUtils.createTempFile(snapshot!) :  RNMBImageUtils.createBase64(snapshot!)
  }
}

extension RCTMGLNavigationMapView {
  func queryTerrainElevation(coordinates: [NSNumber]) -> Double? {
      return self.mapView.mapboxMap.elevation(at: CLLocationCoordinate2D(latitude: coordinates[1].doubleValue, longitude: coordinates[0].doubleValue))
  }
    
    func onMapStyleLoaded(block: @escaping (MapboxMap) -> Void) {
        guard let mapboxMap = self.mapView.mapboxMap else {
        fatalError("mapboxMap is null")
      }
      
      if styleLoaded {
        block(mapboxMap)
      } else {
        styleLoadWaiters.append(block)
      }
    }
}

extension RCTMGLNavigationMapView {
  func setSourceVisibility(_ visible: Bool, sourceId: String, sourceLayerId: String?) -> Void {
      let style = self.mapView.mapboxMap.style
    
    style.allLayerIdentifiers.forEach { layerInfo in
      let layer = logged("setSourceVisibility.layer", info: { "\(layerInfo.id)" }) {
        try style.layer(withId: layerInfo.id)
      }
      if let layer = layer {
        if layer.source == sourceId {
          var good = true
          if let sourceLayerId = sourceLayerId {
            if sourceLayerId != layer.sourceLayer {
              good = false
            }
          }
          if good {
            do {
              try style.setLayerProperty(for: layer.id, property: "visibility", value: visible ? "visible" : "none")
            } catch {
              Logger.log(level: .error, message: "Cannot change visibility of \(layer.id) with source: \(sourceId)")
            }
          }
        }
      }
    }
  }
}

extension UIColor {
   convenience init(red: Int, green: Int, blue: Int) {
       assert(red >= 0 && red <= 255, "Invalid red component")
       assert(green >= 0 && green <= 255, "Invalid green component")
       assert(blue >= 0 && blue <= 255, "Invalid blue component")

       self.init(red: CGFloat(red) / 255.0, green: CGFloat(green) / 255.0, blue: CGFloat(blue) / 255.0, alpha: 1.0)
   }

   convenience init(rgb: Int) {
       self.init(
           red: (rgb >> 16) & 0xFF,
           green: (rgb >> 8) & 0xFF,
           blue: rgb & 0xFF
       )
   }
}
