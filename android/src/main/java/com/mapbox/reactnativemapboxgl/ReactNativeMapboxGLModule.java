
package com.mapbox.reactnativemapboxgl;

import android.os.Handler;
import android.util.Log;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.JSApplicationCausedNativeException;
import com.facebook.react.bridge.JSApplicationIllegalArgumentException;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.RCTNativeAppEventEmitter;
import com.mapbox.mapboxsdk.Mapbox;
import com.mapbox.mapboxsdk.constants.MyBearingTracking;
import com.mapbox.mapboxsdk.constants.MyLocationTracking;
import com.mapbox.mapboxsdk.constants.Style;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.geometry.LatLngBounds;
import com.mapbox.mapboxsdk.offline.OfflineManager;
import com.mapbox.mapboxsdk.offline.OfflineRegion;
import com.mapbox.mapboxsdk.offline.OfflineRegionError;
import com.mapbox.mapboxsdk.offline.OfflineRegionStatus;
import com.mapbox.mapboxsdk.offline.OfflineTilePyramidRegionDefinition;
import com.mapbox.services.android.telemetry.MapboxTelemetry;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

public class ReactNativeMapboxGLModule extends ReactContextBaseJavaModule {

    private static final String TAG = ReactNativeMapboxGLModule.class.getSimpleName();

    private static final int ANDROID_SDK_OFFLINE_PACK_STATE_INACTIVE = 0;
    private static final int ANDROID_SDK_OFFLINE_PACK_STATE_ACTIVE = 1;

    private static final int OFFLINE_PACK_STATE_UNKNOWN = 0;
    private static final int OFFLINE_PACK_STATE_INACTIVE = 1;
    private static final int OFFLINE_PACK_STATE_ACTIVE = 2;
    private static final int OFFLINE_PACK_STATE_COMPLETE = 3;

    private ReactApplicationContext context;
    private ReactNativeMapboxGLPackage aPackage;
    Handler mainHandler;
    private int throttleInterval = 300;

    private static boolean initialized = false;

    public ReactNativeMapboxGLModule(ReactApplicationContext reactContext, ReactNativeMapboxGLPackage thePackage) {
        super(reactContext);
        this.mainHandler = new Handler(reactContext.getApplicationContext().getMainLooper());
        this.context = reactContext;
        this.aPackage = thePackage;
        Log.d(TAG, "Context " + context);
        Log.d(TAG, "reactContext " + reactContext);
    }

    @Override
    public String getName() {
        return "MapboxGLManager";
    }

    static private ArrayList<Integer> serializeTracking(int locationTracking, int bearingTracking) {
        ArrayList<Integer> result = new ArrayList<Integer>();
        result.add(locationTracking);
        result.add(bearingTracking);
        return result;
    }

    public static final int[] locationTrackingModes = new int[] {
            MyLocationTracking.TRACKING_NONE,
            MyLocationTracking.TRACKING_FOLLOW,
            MyLocationTracking.TRACKING_FOLLOW,
            MyLocationTracking.TRACKING_FOLLOW
    };

    public static final int[] bearingTrackingModes = new int[] {
            MyBearingTracking.NONE,
            MyBearingTracking.NONE,
            MyBearingTracking.GPS,
            MyBearingTracking.COMPASS
    };

    @Override
    public @Nullable Map<String, Object> getConstants() {
        HashMap<String, Object> constants = new HashMap<String, Object>();

        HashMap<String, Object> userTrackingMode = new HashMap<String, Object>();
        HashMap<String, Object> mapStyles = new HashMap<String, Object>();
        HashMap<String, Object> userLocationVerticalAlignment = new HashMap<String, Object>();
        HashMap<String, Object> offlinePackState = new HashMap<String, Object>();

        // User tracking constants
        userTrackingMode.put("none", 0);
        userTrackingMode.put("follow", 1);
        userTrackingMode.put("followWithCourse", 2);
        userTrackingMode.put("followWithHeading", 3);

        // Style constants
        mapStyles.put("light", Style.LIGHT);
        mapStyles.put("dark", Style.DARK);
        mapStyles.put("streets", Style.MAPBOX_STREETS);
        mapStyles.put("outdoors", Style.OUTDOORS);
        mapStyles.put("satellite", Style.SATELLITE);
        mapStyles.put("hybrid", Style.SATELLITE_STREETS);

        // These need to be here for compatibility, even if they're not supported on Android
        userLocationVerticalAlignment.put("center", 0);
        userLocationVerticalAlignment.put("top", 1);
        userLocationVerticalAlignment.put("bottom", 2);

        // Offline Pack State constants
        offlinePackState.put("unknown", 0);
        offlinePackState.put("inactive", 1);
        offlinePackState.put("active", 2);
        offlinePackState.put("complete", 3);
        offlinePackState.put("invalid", 4);

        // Other constants
        constants.put("unknownResourceCount", Long.MAX_VALUE);

        constants.put("userTrackingMode", userTrackingMode);
        constants.put("mapStyles", mapStyles);
        constants.put("userLocationVerticalAlignment", userLocationVerticalAlignment);
        constants.put("offlinePackState", offlinePackState);

        return constants;
    }

    // Access Token

    @ReactMethod
    public void setAccessToken(final String accessToken, final Promise promise) {
        if (accessToken == null || accessToken.length() == 0 || accessToken.equals("your-mapbox.com-access-token")) {
            throw new JSApplicationIllegalArgumentException("Invalid access token. Register to mapbox.com and request an access token, then pass it to setAccessToken()");
        }
        if (initialized) {
            String oldToken = Mapbox.getAccessToken();
            if (!oldToken.equals(accessToken)) {
                JSApplicationIllegalArgumentException error =
                        new JSApplicationIllegalArgumentException("Mapbox access token cannot be initialized twice with different values");
                promise.reject(error);
                throw error;
            }
            promise.resolve(null);
            return;
        }
        initialized = true;
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                Mapbox.getInstance(context.getApplicationContext(), accessToken);
                promise.resolve(null);
            }
        });
    }

    // Connected
    @ReactMethod
    public void setConnected(boolean connected) {
        Mapbox.getInstance(context.getApplicationContext(), Mapbox.getAccessToken()).setConnected(connected);
    }

    // Metrics

    @ReactMethod
    public void getMetricsEnabled(final Promise promise) {
        try {
            promise.resolve(MapboxTelemetry.getInstance().isTelemetryEnabled());
        } catch (NullPointerException e) {
            promise.reject(new JSApplicationCausedNativeException("You should call getMetricsEnabled after setAccessToken"));
        }
    }

    @ReactMethod
    public void setMetricsEnabled(boolean value) {
        MapboxTelemetry.getInstance().setTelemetryEnabled(value);
    }

    // Offline packs

    // Offline pack events and initialization

    class OfflineRegionProgressObserver implements OfflineRegion.OfflineRegionObserver {
        ReactNativeMapboxGLModule module;
        OfflineRegion region;
        OfflineRegionStatus status;
        String name;
        boolean recentlyUpdated = false;
        boolean throttled = true;
        boolean invalid = false;

        OfflineRegionProgressObserver(ReactNativeMapboxGLModule module, OfflineRegion region, String name) {
            this.module = module;
            this.region = region;
            if (name == null) {
                this.name = getOfflineRegionName(region);
            } else {
                this.name = name;
            }
        }

        void fireUpdateEvent() {
            if (invalid) { return; }

            recentlyUpdated = true;
            WritableMap event = serializeOfflineRegionStatus(region, this.status);
            module.getReactApplicationContext().getJSModule(RCTNativeAppEventEmitter.class)
                    .emit("MapboxOfflineProgressDidChange", event);

            module.mainHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    recentlyUpdated = false;
                    if (throttled) {
                        throttled = false;
                        fireUpdateEvent();
                    }
                }
            }, throttleInterval);
        }

        @Override
        public void onStatusChanged(OfflineRegionStatus status) {
            if (invalid) { return; }

            this.status = status;

            if (!recentlyUpdated) {
                fireUpdateEvent();
            } else {
                throttled = true;
            }
        }

        @Override
        public void onError(OfflineRegionError error) {
            if (invalid) { return; }

            WritableMap event = Arguments.createMap();
            event.putString("name", getOfflineRegionName(region));
            event.putString("error", error.toString());

            module.getReactApplicationContext().getJSModule(RCTNativeAppEventEmitter.class)
                    .emit("MapboxOfflineError", event);
        }

        @Override
        public void mapboxTileCountLimitExceeded(long limit) {
            if (invalid) { return; }

            WritableMap event = Arguments.createMap();
            event.putString("name", getOfflineRegionName(region));
            event.putDouble("maxTiles", limit);

            module.getReactApplicationContext().getJSModule(RCTNativeAppEventEmitter.class)
                    .emit("MapboxOfflineMaxAllowedTiles", event);
        }

        public void invalidate() {
            invalid = true;
        }
    }

    private int uninitializedObserverCount = -1;
    private ArrayList<OfflineRegionProgressObserver> offlinePackObservers = new ArrayList<>();
    private ArrayList<Promise> offlinePackListingRequests = new ArrayList<>();

    void flushListingRequests() {
        WritableArray result = _getOfflinePacks();
        for (Promise promise : offlinePackListingRequests) {
            promise.resolve(result);
        }
        offlinePackListingRequests.clear();
    }

    class OfflineRegionsInitialRequest implements OfflineManager.ListOfflineRegionsCallback {
        private final ReactNativeMapboxGLModule module;

        OfflineRegionsInitialRequest(ReactNativeMapboxGLModule module) {
            this.module = module;
        }

        @Override
        public void onList(OfflineRegion[] offlineRegions) {
            uninitializedObserverCount = offlineRegions.length;
            for (OfflineRegion region : offlineRegions) {
                final OfflineRegionProgressObserver observer = new OfflineRegionProgressObserver(module, region, null);
                offlinePackObservers.add(observer);
                region.setObserver(observer);
                region.setDownloadState(OfflineRegion.STATE_ACTIVE);
                region.getStatus(new OfflineRegion.OfflineRegionStatusCallback() {
                    @Override
                    public void onStatus(OfflineRegionStatus status) {
                        observer.onStatusChanged(status);
                        uninitializedObserverCount--;
                        if (uninitializedObserverCount == 0) {
                            flushListingRequests();
                        }
                    }
                    @Override
                    public void onError(String error) {
                        Log.e(context.getApplicationContext().getPackageName(), error);
                    }
                });
            }

            module.context
                    .getJSModule(RCTNativeAppEventEmitter.class)
                    .emit("MapboxOfflinePacksLoaded", null);
        }

        @Override
        public void onError(String error) {
            Log.e(module.getReactApplicationContext().getPackageName(), error);
        }
    }

    @ReactMethod
    void initializeOfflinePacks() {
        final ReactNativeMapboxGLModule _this = this;
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                OfflineManager.getInstance(context.getApplicationContext()).listOfflineRegions(
                        new OfflineRegionsInitialRequest(_this)
                );
            }
        });

    }

    // Offline pack utils

    static WritableMap serializeOfflineRegionStatus(OfflineRegion region, OfflineRegionStatus status) {
        WritableMap result = Arguments.createMap();

        try {
            ByteArrayInputStream bis = new ByteArrayInputStream(region.getMetadata());
            ObjectInputStream ois = new ObjectInputStream(bis);

            result.putString("name", (String)ois.readObject());
            result.putString("metadata", (String)ois.readObject());

            ois.close();
        } catch (Throwable e) {
            e.printStackTrace();
        }

        result.putInt("state", normalizeOfflineRegionState(status));
        result.putInt("countOfBytesCompleted", (int)status.getCompletedResourceSize());
        result.putInt("countOfResourcesCompleted", (int)status.getCompletedResourceCount());
        result.putInt("countOfResourcesExpected", (int)status.getRequiredResourceCount());
        result.putInt("maximumResourcesExpected", (int)status.getRequiredResourceCount());

        return result;
    }

    /*
     * Normalizes offline region status state for the sake of parity with iOS for React Native
     * Essentially we force Android state to be the same as iOS state for ease of cross-platform development
     *
     * On iOS:
     * 0: Unknown
     * 1: Inactive
     * 2: Active
     * 3: Complete
     * 4: Invalid (iOS ONLY)
     *
     * On Android:
     * 0: Inactive (Complete is inactive, AND countOfResourcesCompleted == countOfResourcesExpected)
     * 1: Active
     */
    static int normalizeOfflineRegionState(OfflineRegionStatus status) {
        int state = (int)status.getDownloadState();
        boolean isComplete = (boolean)status.isComplete();

        switch (state) {
            case ANDROID_SDK_OFFLINE_PACK_STATE_INACTIVE:
                if (isComplete) {
                    state = OFFLINE_PACK_STATE_COMPLETE;
                } else {
                    state = OFFLINE_PACK_STATE_INACTIVE;
                }
                break;
            case ANDROID_SDK_OFFLINE_PACK_STATE_ACTIVE:
                state = OFFLINE_PACK_STATE_ACTIVE;
                break;
            default:
                state = OFFLINE_PACK_STATE_UNKNOWN;
        }

        return state;
    }

    static String getOfflineRegionName(OfflineRegion region) {
        try {
            ByteArrayInputStream bis = new ByteArrayInputStream(region.getMetadata());
            ObjectInputStream ois = new ObjectInputStream(bis);
            String name = (String)ois.readObject();
            ois.close();
            return name;
        } catch (Throwable e) {
            e.printStackTrace();
            return null;
        }
    }

    // Offline pack listing

    WritableArray _getOfflinePacks() {
        WritableArray result = Arguments.createArray();
        for (OfflineRegionProgressObserver observer : offlinePackObservers) {
            result.pushMap(serializeOfflineRegionStatus(observer.region, observer.status));
        }
        return result;
    }

    @ReactMethod
    public void getOfflinePacks(final Promise promise) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                promise.resolve(_getOfflinePacks());
            }
        });
    }

    // Offline pack insertion

    @ReactMethod
    public void addOfflinePack(ReadableMap options, final Promise promise) {
        if (!options.hasKey("name")) {
            promise.reject(new JSApplicationIllegalArgumentException("addOfflinePack(): name is required."));
            return;
        }
        if (!options.hasKey("minZoomLevel")) {
            promise.reject(new JSApplicationIllegalArgumentException("addOfflinePack(): minZoomLevel is required."));
            return;
        }
        if (!options.hasKey("maxZoomLevel")) {
            promise.reject(new JSApplicationIllegalArgumentException("addOfflinePack(): maxZoomLevel is required."));
            return;
        }
        if (!options.hasKey("bounds")) {
            promise.reject(new JSApplicationIllegalArgumentException("addOfflinePack(): bounds is required."));
            return;
        }
        if (!options.hasKey("styleURL")) {
            promise.reject(new JSApplicationIllegalArgumentException("addOfflinePack(): styleURL is required."));
            return;
        }
        if (!options.hasKey("type")) {
            promise.reject(new JSApplicationIllegalArgumentException("addOfflinePack(): type is required."));
            return;
        }
        if (!options.getString("type").equals("bbox")) {
            promise.reject(new JSApplicationIllegalArgumentException("addOfflinePack(): Offline pack type " +
                    options.getString("type") +
                    " not supported. Only \"bbox\" is currently supported."));
            return;
        }

        float pixelRatio = context.getResources().getDisplayMetrics().density;
        pixelRatio = pixelRatio < 1.5f ? 1.0f : 2.0f;

        ReadableArray boundsArray = options.getArray("bounds");
        LatLngBounds bounds = new LatLngBounds.Builder()
                .include(new LatLng(boundsArray.getDouble(0), boundsArray.getDouble(1)))
                .include(new LatLng(boundsArray.getDouble(2), boundsArray.getDouble(3)))
                .build();

        final OfflineTilePyramidRegionDefinition regionDef = new OfflineTilePyramidRegionDefinition(
                options.getString("styleURL"),
                bounds,
                options.getDouble("minZoomLevel"),
                options.getDouble("maxZoomLevel"),
                pixelRatio
        );

        byte [] metadata;

        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(bos);
            oos.writeObject(options.getString("name"));
            oos.writeObject(options.getString("metadata"));
            oos.close();
            metadata = bos.toByteArray();
        } catch (IOException e) {
            promise.reject(e);
            return;
        }

        final ReactNativeMapboxGLModule _this = this;
        final byte [] _metadata = metadata;
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                OfflineManager.getInstance(context.getApplicationContext()).createOfflineRegion(
                        regionDef,
                        _metadata,
                        new OfflineManager.CreateOfflineRegionCallback() {
                            @Override
                            public void onCreate(OfflineRegion offlineRegion) {
                                OfflineRegionProgressObserver observer = new OfflineRegionProgressObserver(_this, offlineRegion, null);
                                offlinePackObservers.add(observer);
                                offlineRegion.setObserver(observer);
                                offlineRegion.setDownloadState(OfflineRegion.STATE_ACTIVE);
                                promise.resolve(null);
                            }

                            @Override
                            public void onError(String error) {
                                promise.reject(new JSApplicationIllegalArgumentException(error));
                            }
                        }
                );
            }
        });
    }

    // Offline pack removal

    @ReactMethod
    public void removeOfflinePack(final String packName, final Promise promise) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                final OfflineRegionProgressObserver foundObserver = getObserver(packName);

                if (foundObserver == null) {
                    promise.resolve(Arguments.createMap());
                    return;
                }

                offlinePackObservers.remove(foundObserver);
                foundObserver.invalidate();
                foundObserver.region.setDownloadState(OfflineRegion.STATE_INACTIVE);
                foundObserver.region.delete(new OfflineRegion.OfflineRegionDeleteCallback() {
                    @Override
                    public void onDelete() {
                        WritableMap result = Arguments.createMap();
                        result.putString("deleted", foundObserver.name);
                        promise.resolve(result);
                    }

                    @Override
                    public void onError(String error) {
                        promise.reject(new JSApplicationIllegalArgumentException(error));
                    }
                });
            }
        });
    }

    @ReactMethod
    public void suspendOfflinePack(final String packName, final Promise promise) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                final OfflineRegionProgressObserver foundObserver = getObserver(packName);

                if (foundObserver == null) {
                    promise.resolve(Arguments.createMap());
                    return;
                }

                foundObserver.region.setDownloadState(OfflineRegion.STATE_INACTIVE);
                foundObserver.region.getStatus(new OfflineRegion.OfflineRegionStatusCallback() {
                    @Override
                    public void onStatus(OfflineRegionStatus status) {
                        foundObserver.onStatusChanged(status);
                        WritableMap result = Arguments.createMap();
                        result.putString("suspended", foundObserver.name);
                        promise.resolve(result);
                    }
                    @Override
                    public void onError(String error) {
                        Log.e(context.getApplicationContext().getPackageName(), error);
                        promise.reject(new JSApplicationIllegalArgumentException(error));
                    }
                });
            }
        });
    }

    @ReactMethod
    public void resumeOfflinePack(final String packName, final Promise promise) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                final OfflineRegionProgressObserver foundObserver = getObserver(packName);

                if (foundObserver == null) {
                    promise.resolve(Arguments.createMap());
                    return;
                }

                foundObserver.region.setDownloadState(OfflineRegion.STATE_ACTIVE);
                foundObserver.region.getStatus(new OfflineRegion.OfflineRegionStatusCallback() {
                    @Override
                    public void onStatus(OfflineRegionStatus status) {
                        foundObserver.onStatusChanged(status);
                        WritableMap result = Arguments.createMap();
                        result.putString("resumed", foundObserver.name);
                        promise.resolve(result);
                    }
                    @Override
                    public void onError(String error) {
                        Log.e(context.getApplicationContext().getPackageName(), error);
                        promise.reject(new JSApplicationIllegalArgumentException(error));
                    }
                });
            }
        });
    }

    OfflineRegionProgressObserver getObserver(String name) {
        OfflineRegionProgressObserver foundObserver = null;

        for (OfflineRegionProgressObserver observer : offlinePackObservers) {
            if (name.equals(observer.name)) {
                foundObserver = observer;
                break;
            }
        }

        return foundObserver;
    }

    // Offline throttle control

    @ReactMethod
    public void setOfflinePackProgressThrottleInterval(int milis) {
        throttleInterval = milis;
    }
}