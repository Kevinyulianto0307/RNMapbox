package com.mapbox.rctmgl.modules

import android.app.Application
import android.util.Log
import com.facebook.react.bridge.*
import com.mapbox.geojson.Point
import com.mapbox.rctmgl.events.constants.StatusCode
import com.mapbox.rctmgl.events.constants.StatusType
import com.mapbox.search.*
import com.mapbox.search.MapboxSearchSdk.serviceProvider
import com.mapbox.search.record.HistoryRecord
import com.mapbox.search.result.SearchAddress
import com.mapbox.search.result.SearchResult
import com.mapbox.search.result.SearchSuggestion
import java.lang.Exception

/**
 * Search SDK for handling query search, for now it will only handle forwardSearch
 */
class MGLMapboxSearchController(private val mReactContext: ReactApplicationContext): ReactContextBaseJavaModule(mReactContext) {

    private var queryOptionsTypeList = arrayListOf(
        QueryType.COUNTRY, QueryType.ADDRESS, QueryType.NEIGHBORHOOD, QueryType.POSTCODE, QueryType.PLACE, QueryType.DISTRICT, QueryType.REGION, QueryType.LOCALITY
    )

    private val searchSuggestionArrayList: ArrayList<SearchSuggestion> = arrayListOf()

    private var mSearchEngine: SearchEngine? = null

    private var searchRequestTask: SearchRequestTask? = null

    private var selectResultTask: SearchRequestTask? = null

    private fun formatReactStatusPayload(code: Int, statusText: String) : ReadableNativeMap {
        val statusMap = WritableNativeMap()
        statusMap.putInt("code", code)
        statusMap.putString("statusText", statusText)

        return statusMap
    }

    private fun formatHistoryRecordPayload(it: HistoryRecord) : ReadableNativeMap {
        val dataMapping = WritableNativeMap()
        dataMapping.putString("suggestionID", it.id)
        dataMapping.putString("name", it.name)
        dataMapping.putString("description", it.descriptionText)
        dataMapping.putNull("matchingName")
        dataMapping.putNull("etaMinutes")
        dataMapping.putNull("distanceMeters")
        dataMapping.putInt("timestamp", it.timestamp.toInt())

        if (it.address == null) {
            dataMapping.putNull("address")
        } else {
            dataMapping.putString("address", it.address!!.formattedAddress(style = SearchAddress.FormatStyle.Full))
        }

        if (it.coordinate == null) {
            dataMapping.putNull("coordinate")
        } else {
            val coordinateMap = WritableNativeMap()
            coordinateMap.putDouble("latitude", it.coordinate!!.latitude())
            coordinateMap.putDouble("longitude", it.coordinate!!.longitude())
            dataMapping.putMap("coordinate", coordinateMap)
        }

        return dataMapping
    }

    @ReactMethod
    fun stopSearch() {
        // Used when user might kill the app or close the screen

        Log.d(REACT_CLASS, "stopSearch")

        if (searchRequestTask?.isCancelled != true) {
            searchRequestTask?.cancel()
        }

        if (selectResultTask?.isCancelled != true) {
            selectResultTask?.cancel()
        }
    }

//    @ReactMethod
//    fun retrieveHistoryRecords(promise: Promise) {
//        setMapboxSearchSDK()
//        val historyDataProvider = serviceProvider.historyDataProvider()
//
//        val searchHistoryCallback: CompletionCallback<List<HistoryRecord>> = object : CompletionCallback<List<HistoryRecord>> {
//            override fun onComplete(result: List<HistoryRecord>) {
//                Log.i("SearchApiExample", "History records: $result")
//                val historyListResult = WritableNativeArray()
//                val status = formatReactStatusPayload(StatusCode.SUCCESS, StatusType.SUCCESS)
//
//                result.forEach {
//                    historyListResult.pushMap(formatHistoryRecordPayload(it))
//                }
//
//                val payload = WritableNativeMap()
//                payload.putMap("status", status)
//                payload.putArray("data", historyListResult)
//                promise.resolve(payload)
//            }
//
//            override fun onError(e: Exception) {
//                Log.i("SearchApiExample", "Unable to retrieve history records", e)
//                promise.reject(e)
//            }
//        }
//
//    }

    @ReactMethod
    fun retrieveSuggestions(querySearch: String, proximityPoint: ReadableArray?, promise: Promise) {
        Log.d(REACT_CLASS, "searchSuggestions with query: $querySearch")
        setMapboxSearchSDK()

        if (mSearchEngine == null) {
            promise.reject(Throwable("Initialization failed, please refresh the page and try again"))
            return
        }

        var proximityPointObject: Point? = null
        if (proximityPoint != null) {
            proximityPointObject = Point.fromLngLat(proximityPoint.getDouble(0), proximityPoint.getDouble(1))
        }

        searchRequestTask = mSearchEngine!!.search(
            querySearch,
            SearchOptions(proximity = proximityPointObject, requestDebounce= 1000, types = queryOptionsTypeList, fuzzyMatch = true, origin = proximityPointObject),
            object : SearchSuggestionsCallback {

                override fun onSuggestions(suggestions: List<SearchSuggestion>, responseInfo: ResponseInfo) {
                    if (searchRequestTask?.isCancelled == true) {
                        val status = formatReactStatusPayload(StatusCode.ERROR_SEARCH_CANCELLED, StatusType.ERROR_SEARCH_CANCELLED)
                        val array = WritableNativeArray()
                        val payload = WritableNativeMap()
                        payload.putMap("status", status)
                        payload.putArray("data", array)
                        return
                    }

                    val data = WritableNativeArray()

                    suggestions.forEach {
                        val dataMapping = WritableNativeMap()
                        dataMapping.putString("suggestionID", it.id)
                        dataMapping.putString("name", it.name)
                        dataMapping.putString("type", it.type.toString())

                        if (it.descriptionText == null) {
                            dataMapping.putNull("description")
                        } else {
                            dataMapping.putString("description", it.descriptionText)
                        }

                        if (it.address == null) {
                            dataMapping.putNull("address")
                        } else {
                            dataMapping.putString("address", it.address!!.formattedAddress(style = SearchAddress.FormatStyle.Full))
                        }

                        if (it.matchingName == null) {
                            dataMapping.putNull("matchingName")
                        } else {
                            dataMapping.putString("matchingName", it.matchingName)
                        }

                        if (it.distanceMeters == null) {
                            dataMapping.putNull("distanceMeters")
                        } else {
                            dataMapping.putDouble("distanceMeters", it.distanceMeters!!)
                        }

                        if (it.etaMinutes == null) {
                            dataMapping.putNull("etaMinutes")
                        } else {
                            dataMapping.putDouble("etaMinutes", it.etaMinutes!!)
                        }

                        data.pushMap(dataMapping)
                    }

                    searchSuggestionArrayList.clear()
                    searchSuggestionArrayList.addAll(suggestions)
                    val status = formatReactStatusPayload(StatusCode.SUCCESS, StatusType.SUCCESS)
                    val payload = WritableNativeMap()
                    payload.putMap("status", status)
                    payload.putArray("data", data)
                    promise.resolve(payload)
                }

                override fun onError(e: Exception) {
                    searchSuggestionArrayList.clear()
                    promise.reject("${StatusCode.ERROR_SEARCH_ERROR}", e.localizedMessage ?: "")
                }
            }
        )
    }

    @ReactMethod
    fun forwardSearch(querySearch: String, suggestionId: String?, proximityPoint: ReadableArray?, promise: Promise) {
        Log.d(REACT_CLASS, "forwardSearch with query: $querySearch")
        setMapboxSearchSDK()

        var proximityPointObject: Point? = null
        if (proximityPoint != null) {
            proximityPointObject = Point.fromLngLat(proximityPoint.getDouble(0), proximityPoint.getDouble(1))
        }

        if (suggestionId != null && searchSuggestionArrayList.isNotEmpty()) {
            val searchSuggestion: SearchSuggestion? = searchSuggestionArrayList.find { it.id === suggestionId }
            if (searchSuggestion != null) {
                selectResultTask = mSearchEngine!!.select(searchSuggestion, object:
                    SearchSelectionCallback {
                    override fun onCategoryResult(
                        suggestion: SearchSuggestion,
                        results: List<SearchResult>,
                        responseInfo: ResponseInfo
                    ) {
                        Log.d("CategoryResults", "search here")
                    }

                    override fun onError(e: Exception) {
                        promise.reject(e)
                    }

                    override fun onResult(
                        suggestion: SearchSuggestion,
                        result: SearchResult,
                        responseInfo: ResponseInfo
                    ) {
                        val payload = WritableNativeMap()

                        // check coordinate
                        if (result.coordinate == null) {
                            val failStatus = formatReactStatusPayload(
                                StatusCode.ERROR_SEARCH_NO_RESULT_FOUND,
                                StatusType.ERROR_SEARCH_NO_RESULT_FOUND)
                            payload.putMap("status", failStatus)
                            payload.putNull("point")
                        } else {
                            val successStatus = formatReactStatusPayload(
                                StatusCode.SUCCESS,
                                StatusType.SUCCESS)
                            payload.putMap("status", successStatus)

                            val longitude = result.coordinate!!.longitude()
                            val latitude = result.coordinate!!.latitude()
                            val point = WritableNativeMap()
                            point.putDouble("latitude", latitude)
                            point.putDouble("longitude", longitude)
                            payload.putMap("point", point)
                        }

                        promise.resolve(payload)
                    }

                    override fun onSuggestions(
                        suggestions: List<SearchSuggestion>,
                        responseInfo: ResponseInfo
                    ) {
                        Log.d("onSuggestions", "search here")
                    }

                })
                return
            }
        }

        searchRequestTask = mSearchEngine!!.search(
            querySearch,
            SearchOptions(proximity = proximityPointObject, requestDebounce= 1000, types = queryOptionsTypeList, fuzzyMatch = true), // search all country
            object : SearchSuggestionsCallback {
                override fun onSuggestions(suggestions: List<SearchSuggestion>, responseInfo: ResponseInfo) {
                    if (searchRequestTask?.isCancelled == true) {
                        return
                    }

                    val suggestion = suggestions.firstOrNull()
                    if (suggestion == null) {
                        val failPayload = WritableNativeMap()
                        val failStatus = formatReactStatusPayload(StatusCode.ERROR_SEARCH_NO_RESULT_FOUND, StatusType.ERROR_SEARCH_NO_RESULT_FOUND)
                        failPayload.putMap("status", failStatus)
                        failPayload.putNull("point")
                        promise.resolve(failPayload)
                        return
                    }

                    selectResultTask = mSearchEngine!!.select(suggestion, object:
                        SearchSelectionCallback {
                        override fun onCategoryResult(
                            suggestion: SearchSuggestion,
                            results: List<SearchResult>,
                            responseInfo: ResponseInfo
                        ) {
                            Log.d("CategoryResults", "search here")
                        }

                        override fun onError(e: Exception) {
                            promise.reject(e)
                        }

                        override fun onResult(
                            suggestion: SearchSuggestion,
                            result: SearchResult,
                            responseInfo: ResponseInfo
                        ) {
                            val payload = WritableNativeMap()

                            // check coordinate
                            if (result.coordinate == null) {
                                val failStatus = formatReactStatusPayload(
                                    StatusCode.ERROR_SEARCH_NO_RESULT_FOUND,
                                    StatusType.ERROR_SEARCH_NO_RESULT_FOUND)
                                payload.putMap("status", failStatus)
                                payload.putNull("point")
                            } else {
                                val successStatus = formatReactStatusPayload(
                                    StatusCode.SUCCESS,
                                    StatusType.SUCCESS)
                                payload.putMap("status", successStatus)

                                val longitude = result.coordinate!!.longitude()
                                val latitude = result.coordinate!!.latitude()
                                val point = WritableNativeMap()
                                point.putDouble("latitude", latitude)
                                point.putDouble("longitude", longitude)
                                payload.putMap("point", point)
                            }

                            promise.resolve(payload)
                        }

                        override fun onSuggestions(
                            suggestions: List<SearchSuggestion>,
                            responseInfo: ResponseInfo
                        ) {
                            Log.d("onSuggestions", "search here")
                        }

                    })
                }

                override fun onError(e: Exception) {
                    promise.reject(e)
                }
            }
        )
    }

//    @ReactMethod
//    fun forwardSearch(querySearch: String, promise: Promise) {
//        Log.d(REACT_CLASS, "forwardSearch with query: $querySearch")
//        setMapboxSearchSDK()
//        searchRequestTask = mSearchEngine!!.search(
//            querySearch,
//            SearchOptions(limit = 5), // search all country
//            object : SearchSuggestionsCallback {
//                override fun onSuggestions(suggestions: List<SearchSuggestion>, responseInfo: ResponseInfo) {
//                    if (searchRequestTask?.isCancelled == true) {
//                        return;
//                    }
//
//                    val suggestion = suggestions.firstOrNull()
//                    if (suggestion == null) {
//                        val failPayload = WritableNativeMap()
//                        val failStatus = formatReactStatusPayload(StatusCode.ERROR_SEARCH_NO_RESULT_FOUND, StatusType.ERROR_SEARCH_NO_RESULT_FOUND)
//                        failPayload.putMap("status", failStatus)
//                        failPayload.putNull("point")
//                        promise.resolve(failPayload)
//                        return;
//                    }
//
//                    selectResultTask = mSearchEngine!!.select(suggestion, object:
//                        SearchSelectionCallback {
//                        override fun onCategoryResult(
//                            suggestion: SearchSuggestion,
//                            results: List<SearchResult>,
//                            responseInfo: ResponseInfo
//                        ) {
//                        }
//
//                        override fun onError(e: Exception) {
//                            promise.reject(e)
//                        }
//
//                        override fun onResult(
//                            suggestion: SearchSuggestion,
//                            result: SearchResult,
//                            responseInfo: ResponseInfo
//                        ) {
//                            val payload = WritableNativeMap()
//
//                            // check coordinate
//                            if (result.coordinate == null) {
//                                val failStatus = formatReactStatusPayload(
//                                    StatusCode.ERROR_SEARCH_NO_RESULT_FOUND,
//                                    StatusType.ERROR_SEARCH_NO_RESULT_FOUND)
//                                payload.putMap("status", failStatus)
//                                payload.putNull("point")
//                            } else {
//                                val successStatus = formatReactStatusPayload(
//                                    StatusCode.SUCCESS,
//                                    StatusType.SUCCESS)
//                                payload.putMap("status", successStatus)
//
//                                val longitude = result.coordinate!!.longitude()
//                                val latitude = result.coordinate!!.latitude()
//                                val point = WritableNativeMap()
//                                point.putDouble("latitude", latitude)
//                                point.putDouble("longitude", longitude)
//                                payload.putMap("point", point)
//                            }
//
//                            promise.resolve(payload)
//                        }
//
//                        override fun onSuggestions(
//                            suggestions: List<SearchSuggestion>,
//                            responseInfo: ResponseInfo
//                        ) {
//
//                        }
//
//                    })
//                }
//
//                override fun onError(e: Exception) {
//                    promise.reject(e)
//                }
//            }
//        )
//    }

    override fun getName(): String {
        return REACT_CLASS
    }

    private fun setMapboxSearchSDK(): Boolean {
        val application = mReactContext.applicationContext as Application

        val accessToken = RCTMGLModule.getAccessToken(mReactContext)
        
        if (mSearchEngine != null) {
            return false
        }
        
        try {
            MapboxSearchSdk.initialize(
                application = application,
                accessToken = accessToken,
            )
        } catch(e: Exception) {
            /**
             * handling unable to reinitialized MapboxSearchSDK, currently no method to override the initialization
             * or check whether has been initialized
             * **/
            // no error handling
            if (e.message !== "Already initialized") {
                throw IllegalStateException(e.message.toString())
            }
        }

        mSearchEngine = MapboxSearchSdk.getSearchEngine()
        return true
    }

    companion object {
        const val REACT_CLASS = "MGLMapboxSearchController"
    }
}