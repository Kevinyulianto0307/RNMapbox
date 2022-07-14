import MapboxMaps

@objc(RCTMGLNavigationMapViewManager)
class RCTMGLNavigationMapViewManager: RCTViewManager {
    @objc
    override static func requiresMainQueueSetup() -> Bool {
        return true
    }
    
    func defaultFrame() -> CGRect {
        return UIScreen.main.bounds
    }
    
    override func view() -> UIView! {
        let result = RCTMGLNavigationMapView(frame: self.defaultFrame())
        return result
    }
}

// MARK: helpers

extension RCTMGLNavigationMapViewManager {
    func withNavigationMapView(
        _ reactTag: NSNumber,
        name: String,
        rejecter: @escaping RCTPromiseRejectBlock,
        fn: @escaping (_: RCTMGLNavigationMapView) -> Void) -> Void
    {
      self.bridge.uiManager.addUIBlock { (manager, viewRegistry) in
        let view = viewRegistry![reactTag]

        guard let view = view! as? RCTMGLNavigationMapView else {
          RCTLogError("Invalid react tag, could not find RCTNavigationMapView");
          rejecter(name, "Unknown find reactTag: \(reactTag)", nil)
          return;
        }
      
        fn(view)
      }
    }
    
    func withMapView(
        _ reactTag: NSNumber,
        name: String,
        rejecter: @escaping RCTPromiseRejectBlock,
        fn: @escaping (_: MapView) -> Void) -> Void
    {
        withNavigationMapView(reactTag, name: name, rejecter: rejecter) { view in
           guard let view = view.mapView else {
            RCTLogError("MapView is not yet available");
            rejecter(name, "MapView not loaded yet", nil)
            return;
          }
          
          fn(view)
        }
    }

    func withMapboxMap(
        _ reactTag: NSNumber,
        name: String,
        rejecter: @escaping RCTPromiseRejectBlock,
        fn: @escaping (_: MapboxMap) -> Void) -> Void
    {
      withMapView(reactTag, name: name, rejecter: rejecter) { view in
        guard let mapboxMap = view.mapboxMap else {
          RCTLogError("MapboxMap is not yet available");
          rejecter(name, "Map not loaded yet", nil)
          return;
        }
        
        fn(mapboxMap)
      }
    }
}

// MARK: - react methods

extension RCTMGLNavigationMapViewManager {
    @objc
    func takeSnap(_ reactTag: NSNumber,
                  writeToDisk: Bool,
                  resolver: @escaping RCTPromiseResolveBlock,
                  rejecter: @escaping RCTPromiseRejectBlock
    ) -> Void {
        withNavigationMapView(reactTag, name:"takeSnap", rejecter: rejecter) { view in
        let uri = view.takeSnap(writeToDisk: writeToDisk)
        resolver(["uri": uri.absoluteString])
      }
    }
  
    @objc
    func queryTerrainElevation(_ reactTag: NSNumber,
                               coordinates: [NSNumber],
                               resolver: @escaping RCTPromiseResolveBlock,
                               rejecter: @escaping RCTPromiseRejectBlock
    ) -> Void {
        withNavigationMapView(reactTag, name:"queryTerrainElevation", rejecter: rejecter) { view in
        let result = view.queryTerrainElevation(coordinates: coordinates)
        if let result = result {
          resolver(["data": NSNumber(value: result)])
        } else {
          resolver(nil)
        }
      }
    }
  
  @objc
  func setSourceVisibility(_ reactTag: NSNumber,
                      visible: Bool,
                      sourceId: String,
                      sourceLayerId: String?,
                      resolver: @escaping RCTPromiseResolveBlock,
                      rejecter: @escaping RCTPromiseRejectBlock) -> Void {
      withNavigationMapView(reactTag, name:"setSourceVisibility", rejecter: rejecter) { view in
      view.setSourceVisibility(visible, sourceId: sourceId, sourceLayerId:sourceLayerId)
      resolver(nil)
    }
  }

  @objc
  func getCenter(_ reactTag: NSNumber,
                 resolver: @escaping RCTPromiseResolveBlock,
                 rejecter: @escaping RCTPromiseRejectBlock) -> Void {
    withMapboxMap(reactTag, name:"getCenter", rejecter: rejecter) { mapboxMap in
      resolver(["center": [
        mapboxMap.cameraState.center.longitude,
        mapboxMap.cameraState.center.latitude
      ]])
    }
  }

  @objc
  func getCoordinateFromView(
    _ reactTag: NSNumber,
    atPoint point: CGPoint,
    resolver: @escaping RCTPromiseResolveBlock,
    rejecter: @escaping RCTPromiseRejectBlock) {
      withMapboxMap(reactTag, name:"getCoordinateFromView", rejecter: rejecter) { mapboxMap in
        let coordinates = mapboxMap.coordinate(for: point)
        resolver(["coordinateFromView": [coordinates.longitude, coordinates.latitude]])
      }
  }

  @objc
  func getPointInView(
    _ reactTag: NSNumber,
    atCoordinate coordinate: [NSNumber],
    resolver: @escaping RCTPromiseResolveBlock,
    rejecter: @escaping RCTPromiseRejectBlock) {
      withMapboxMap(reactTag, name:"getPointInView", rejecter: rejecter) { mapboxMap in
        let coordinate = CLLocationCoordinate2DMake(coordinate[1].doubleValue, coordinate[0].doubleValue)
        let point = mapboxMap.point(for: coordinate)
        resolver(["pointInView": [(point.x), (point.y)]])
      }
  }

  @objc
  func setHandledMapChangedEvents(
    _ reactTag: NSNumber,
    events: [String],
    resolver: @escaping RCTPromiseResolveBlock,
    rejecter: @escaping RCTPromiseRejectBlock) {
    withNavigationMapView(reactTag, name:"setHandledMapChangedEvents", rejecter: rejecter) { view in
        view.handleMapChangedEvents = Set(events.compactMap {
        RCTMGLEvent.EventType(rawValue: $0)
      })
    }
  }

  @objc
  func getZoom(
    _ reactTag: NSNumber,
    resolver: @escaping RCTPromiseResolveBlock,
    rejecter: @escaping RCTPromiseRejectBlock) {
      withMapboxMap(reactTag, name:"getZoom", rejecter: rejecter) { mapboxMap in
        resolver(["zoom": mapboxMap.cameraState.zoom])
      }
  }

  @objc
  func getVisibleBounds(
    _ reactTag: NSNumber,
    resolver: @escaping RCTPromiseResolveBlock,
    rejecter: @escaping RCTPromiseRejectBlock) {
      withMapView(reactTag, name:"getVisibleBounds", rejecter: rejecter) { mapView in
        resolver(["visibleBounds":  mapView.mapboxMap.coordinateBounds(for: mapView.bounds).toArray()])
      }
  }
    
    
  // MARK: react method
    
    @objc
    func findRoute( _ reactTag:NSNumber,
                    origin:[NSNumber]?,
                    destination:[NSNumber]?,
                    resolver:@escaping RCTPromiseResolveBlock,
                    rejecter:@escaping RCTPromiseRejectBlock) {
        withNavigationMapView(reactTag, name:"findRoute", rejecter: rejecter) { view in
           view.findRoute(origin, destination: destination)
           resolver(nil)
        }
    }
    
    @objc
    func resetRoute( _ reactTag: NSNumber,
                    resolver: @escaping RCTPromiseResolveBlock,
                    rejecter: @escaping RCTPromiseRejectBlock) {
        withNavigationMapView(reactTag, name:"resetRoute", rejecter: rejecter) { view in
           view.clearRoute()
           resolver(nil)
        }
    }
    
    @objc
    func stopRoute( _ reactTag: NSNumber,
                    shouldSimulate:Bool,
                    resolver: @escaping RCTPromiseResolveBlock,
                    rejecter: @escaping RCTPromiseRejectBlock) {
        withNavigationMapView(reactTag, name:"stopRoute", rejecter: rejecter) { view in
           view.stopRoute()
           resolver(nil)
        }
    }
    
    @objc
    func startRoute( _ reactTag: NSNumber,
                    shouldSimulate:Bool,
                    resolver: @escaping RCTPromiseResolveBlock,
                    rejecter: @escaping RCTPromiseRejectBlock) {
        withNavigationMapView(reactTag, name:"stopRoute", rejecter: rejecter) { view in
           view.startRoute(shouldSimulate)
           resolver(nil)
        }
    }
    
    @objc
    func recenter( _ reactTag: NSNumber,
                    resolver: @escaping RCTPromiseResolveBlock,
                    rejecter: @escaping RCTPromiseRejectBlock) {
        withNavigationMapView(reactTag, name:"recenter", rejecter: rejecter) { view in
           view.recenter()
           resolver(nil)
        }
    }
}

// MARK: - queryRenderedFeatures

extension RCTMGLNavigationMapViewManager {
  @objc
  func queryRenderedFeaturesAtPoint(
    _ reactTag: NSNumber,
    atPoint point: [NSNumber],
    withFilter filter: [Any]?,
    withLayerIDs layerIDs: [String]?,
    resolver: @escaping RCTPromiseResolveBlock,
    rejecter: @escaping RCTPromiseRejectBlock) -> Void {
      withMapboxMap(reactTag, name:"queryRenderedFeaturesAtPoint", rejecter: rejecter) { mapboxMap in
        let point = CGPoint(x: CGFloat(point[0].floatValue), y: CGFloat(point[1].floatValue))

        logged("queryRenderedFeaturesAtPoint.option", rejecter: rejecter) {
          let options = try RenderedQueryOptions(layerIds: layerIDs, filter: filter?.asExpression())
          
          mapboxMap.queryRenderedFeatures(at: point, options: options) { result in
            switch result {
            case .success(let features):
              resolver([
                "data": ["type": "FeatureCollection", "features": features.compactMap { queriedFeature in
                  logged("queryRenderedFeaturesAtPoint.feature.toJSON") { try queriedFeature.feature.toJSON() }
                }]
              ])
            case .failure(let error):
              rejecter("queryRenderedFeaturesAtPoint","failed to query features", error)
            }
          }
        }
      }
  }

  @objc
  func queryRenderedFeaturesInRect(
    _ reactTag: NSNumber,
    withBBox bbox: [NSNumber],
    withFilter filter: [Any]?,
    withLayerIDs layerIDs: [String]?,
    resolver: @escaping RCTPromiseResolveBlock,
    rejecter: @escaping RCTPromiseRejectBlock) -> Void {
      withMapboxMap(reactTag, name:"queryRenderedFeaturesInRect", rejecter: rejecter) { mapboxMap in
        let left = CGFloat(bbox[0].floatValue)
        let bottom = CGFloat(bbox[1].floatValue)
        let right = CGFloat(bbox[2].floatValue)
        let top = CGFloat(bbox[3].floatValue)
        let rect = CGRect(x: [left,right].min()!, y: [bottom,top].min()!, width: fabs(right-left), height: fabs(top-bottom))
        
        logged("queryRenderedFeaturesInRect.option", rejecter: rejecter) {
          let options = try RenderedQueryOptions(layerIds: layerIDs, filter: filter?.asExpression())
          
          mapboxMap.queryRenderedFeatures(in: rect, options: options) { result in
            switch result {
            case .success(let features):
              resolver([
                "data": ["type": "FeatureCollection", "features": features.compactMap { queriedFeature in
                  logged("queryRenderedFeaturesInRect.queriedfeature.map") { try queriedFeature.feature.toJSON() }
                }]
              ])
            case .failure(let error):
              rejecter("queryRenderedFeaturesAtPoint","failed to query features", error)
            }
          }
        }
      }
   }
}