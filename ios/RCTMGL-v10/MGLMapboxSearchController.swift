import Foundation
import MapboxSearch


@objc(MGLMapboxSearchController)
class MGLMapboxSearchController: NSObject {
  let searchEngine = SearchEngine()
  var resolver: RCTPromiseResolveBlock? = nil
  var rejecter: RCTPromiseRejectBlock? = nil
  
  @objc
  func forwardSearch(_ query: String,
   resolver: @escaping RCTPromiseResolveBlock,
   rejecter: @escaping RCTPromiseRejectBlock)
  {
    self.resolver = resolver
    self.rejecter = rejecter
    searchEngine.delegate = self
    searchEngine.query = query // You also can call `searchEngine.search(query: "Mapbox")`
  }
  
  @objc
  static func requiresMainQueueSetup() -> Bool {
    // true if you need this class initialized on the main thread
    // false if the class can be initialized on a background thread
      return true
  }
  
}

extension MGLMapboxSearchController: SearchEngineDelegate {
  func suggestionsUpdated(suggestions: [SearchSuggestion], searchEngine: SearchEngine) {
    print("Number of search results: \(searchEngine.suggestions.count)")
    
    /// Simulate user selection with random algorithm
    guard let randomSuggestion: SearchSuggestion = searchEngine.suggestions.randomElement() else {
      self.rejecter?("suggestionsUpdated", "No Search results", nil)
      return
    }
    
    /// Callback to SearchEngine with chosen `SearchSuggestion`
    self.searchEngine.select(suggestion: randomSuggestion)
     
    /// We may expect `resolvedResult(result:)` to be called next
    /// or the new round of `resultsUpdated(searchEngine:)` in case if randomSuggestion represents category suggestion (like a 'bar' or 'cafe')
  }
  
  
  func resultResolved(result: SearchResult, searchEngine: SearchEngine) {
    self.resolver?([
      "status": [
        "code": 200
      ],
      "point": ["latitude": result.coordinate.latitude, "longitude": result.coordinate.longitude],
    ]);
  }
  
  func searchErrorHappened(searchError: SearchError, searchEngine: SearchEngine) {
    print("Error during search: \(searchError)")
    self.rejecter?("searchErrorHappened", searchError.localizedDescription, searchError)
  }
  
  
}


