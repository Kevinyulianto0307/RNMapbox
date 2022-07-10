import Foundation

protocol RCTMGLEventProtocol {

    func toJSON() -> [String: Any];
}

@objc
class RCTMGLEvent : NSObject, RCTMGLEventProtocol {
    var type: String = ""
    var payload: [String:Any]? = nil
    func toJSON() -> [String: Any]
    {
        if let payload = payload {
            return ["type": type, "payload": payload];
        } else {
            return ["type": type]
        }
    }
    
    enum StatusType: String {
       case ERROR_NO_ACCESS_TOKEN = "ERROR_NO_ACCESS_TOKEN"
       case ERROR_SIMULATION_NO_ORIGIN = "ERROR_SIMULATION_NO_ORIGIN"
       case ERROR_FIND_ROUTE_NO_ORIGIN = "ERROR_FIND_ROUTE_NO_ORIGIN"
       case ERROR_FIND_ROUTE_NO_DESTINATION = "ERROR_FIND_ROUTE_NO_DESTINATION"
       case ERROR_FIND_ROUTE_NO_ROUTES = "ERROR_FIND_ROUTE_NO_ROUTES"
       case ERROR_SEARCH_NO_RESULT_FOUND = "ERROR_SEARCH_NO_RESULT_FOUND"
       case ERROR_FIND_ROUTE_API = "ERROR_FIND_ROUTE_API"
       case ERROR_START_ROUTE_NO_ROUTES = "ERROR_START_ROUTE_NO_ROUTES"
       case ERROR_START_ROUTE_NO_DESTINATION = "ERROR_START_ROUTE_NO_DESTINATION"
       case ERROR_PERMISSION_LOCATION_REQUIRED = "ERROR_PERMISSION_LOCATION_REQUIRED"
       case SUCCESS = "SUCCESS"
    }
    
    enum StatusCode: NSInteger {
        case ERROR_NO_ACCESS_TOKEN = 401
        case ERROR_SIMULATION_NO_ORIGIN = 402
        case ERROR_FIND_ROUTE_NO_ORIGIN = 403
        case ERROR_FIND_ROUTE_NO_DESTINATION = 404
        case ERROR_FIND_ROUTE_NO_ROUTES = 405
        case ERROR_FIND_ROUTE_API = 406
        case ERROR_SEARCH_NO_RESULT_FOUND = 407
        case ERROR_START_ROUTE_NO_ROUTES = 408
        case ERROR_START_ROUTE_NO_DESTINATION = 409
        case ERROR_PERMISSION_LOCATION_REQUIRED = 400
        case SUCCESS = 200
    }

    enum EventType : String {
      case tap
      case longPress
      //case regionWillChange
      case regionIsChanging
      case regionDidChange
      case cameraChanged
      case mapIdle
      case imageMissing
      case didFinishLoadingMap
      case didFinishRenderingFully
      case didFinishRendering
      case didFinishLoadingStyle
      case offlineProgress
      case offlineError
      case offlineTileLimit
      case vectorSourceLayerPress
      case shapeSourceLayerPress
        
      case OnArrival
      case OnRouteOff
      case OnFindRouteSuccess
      case OnNavigationStarted
      case OnRouteProgressChange
      case OnLocationMatcherChange
      case OnError
    }
    
    init(type: EventType, payload: [String:Any]?) {
        self.type = type.rawValue
        self.payload = payload
    }
    
}
