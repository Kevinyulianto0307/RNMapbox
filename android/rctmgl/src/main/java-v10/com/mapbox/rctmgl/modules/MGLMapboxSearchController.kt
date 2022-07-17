package com.mapbox.rctmgl.modules

import android.app.Application
import android.util.Log
import com.facebook.react.bridge.*
import com.mapbox.rctmgl.events.constants.StatusCode
import com.mapbox.rctmgl.events.constants.StatusType
import com.mapbox.search.*
import com.mapbox.search.result.SearchResult
import com.mapbox.search.result.SearchSuggestion
import java.lang.Exception

/**
 * Search SDK for handling query search, for now it will only handle forwardSearch
 */
class MGLMapboxSearchController(private val mReactContext: ReactApplicationContext): ReactContextBaseJavaModule(mReactContext) {

    private var mSearchEngine: SearchEngine? = null

    private var searchRequestTask: SearchRequestTask? = null

    private var selectResultTask: SearchRequestTask? = null

    private fun formatReactStatusPayload(code: Int, statusText: String) : ReadableNativeMap {
        val statusMap = WritableNativeMap()
        statusMap.putInt("code", code)
        statusMap.putString("statusText", statusText)

        return statusMap
    }

    @ReactMethod
    fun stopSearch() {
        Log.d(REACT_CLASS, "stopSearch")
        if (searchRequestTask?.isCancelled != true) {
            searchRequestTask?.cancel()
        }

        if (selectResultTask?.isCancelled != true) {
            selectResultTask?.cancel()
        }
    }

    @ReactMethod
    fun forwardSearch(querySearch: String, promise: Promise) {
        Log.d(REACT_CLASS, "forwardSearch with query: $querySearch")
        setMapboxSearchSDK()
        searchRequestTask = mSearchEngine!!.search(
            querySearch,
            SearchOptions(limit = 5), // search all country
            object : SearchSuggestionsCallback {
                override fun onSuggestions(suggestions: List<SearchSuggestion>, responseInfo: ResponseInfo) {
                    if (searchRequestTask?.isCancelled == true) {
                        return;
                    }

                    val suggestion = suggestions.firstOrNull()
                    if (suggestion == null) {
                        val failPayload = WritableNativeMap()
                        val failStatus = formatReactStatusPayload(StatusCode.ERROR_SEARCH_NO_RESULT_FOUND, StatusType.ERROR_SEARCH_NO_RESULT_FOUND)
                        failPayload.putMap("status", failStatus)
                        failPayload.putNull("point")
                        promise.resolve(failPayload)
                        return;
                    }

                    selectResultTask = mSearchEngine!!.select(suggestion, object:
                        SearchSelectionCallback {
                        override fun onCategoryResult(
                            suggestion: SearchSuggestion,
                            results: List<SearchResult>,
                            responseInfo: ResponseInfo
                        ) {
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

                        }

                    })
                }

                override fun onError(e: Exception) {
                    promise.reject(e)
                }
            }
        )
    }

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