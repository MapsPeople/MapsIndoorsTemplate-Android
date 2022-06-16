package com.mapspeople.mapsindoorstemplate.positioning

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import androidx.annotation.Nullable
import com.google.android.gms.location.*
import com.google.android.gms.location.LocationRequest.PRIORITY_HIGH_ACCURACY
import com.mapsindoors.mapssdk.*
import org.jetbrains.annotations.NotNull


class GPSPositionProvider(context: Context) : PositionProvider {
    private val REQUIRED_PERMISSIONS = arrayOf(
        "android.permission.ACCESS_FINE_LOCATION",
        "android.permission.ACCESS_COARSE_LOCATION"
    )
    private var mIsRunning = false
    private var mIsEnabled = false
    protected val onStateChangedListenersList: MutableList<OnStateChangedListener> = ArrayList()
    protected val onPositionUpdateListeners: MutableList<OnPositionUpdateListener> = ArrayList()
    protected var mProviderId: String? = null
    protected var mContext: Context
    protected var mLatestPosition: PositionResult? = null
    private var fusedLocationClient: FusedLocationProviderClient? = null
    override fun getRequiredPermissions(): Array<String> {
        return REQUIRED_PERMISSIONS
    }

    override fun isPSEnabled(): Boolean {
        return mIsEnabled
    }

    @SuppressLint("MissingPermission")
    override fun startPositioning(@Nullable args: String?) {
        mIsRunning = true
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(mContext)
        val locationRequest: LocationRequest = LocationRequest.create()
        locationRequest.interval = 1000
        locationRequest.fastestInterval = 100
        locationRequest.priority = PRIORITY_HIGH_ACCURACY
        fusedLocationClient?.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    override fun stopPositioning(@Nullable args: String?) {
        fusedLocationClient?.removeLocationUpdates(locationCallback)
        mIsRunning = false
    }

    override fun isRunning(): Boolean {
        return mIsRunning
    }

    override fun addOnPositionUpdateListener(@Nullable onPositionUpdateListener: OnPositionUpdateListener?) {
        if (onPositionUpdateListener != null) {
            onPositionUpdateListeners.remove(onPositionUpdateListener)
            onPositionUpdateListeners.add(onPositionUpdateListener)
        }
    }

    override fun removeOnPositionUpdateListener(@Nullable onPositionUpdateListener: OnPositionUpdateListener?) {
        if (onPositionUpdateListener != null) {
            onPositionUpdateListeners.remove(onPositionUpdateListener)
        }
    }

    override fun setProviderId(@Nullable id: String?) {
        mProviderId = id
    }

    override fun addOnStateChangedListener(@Nullable onStateChangedListener: OnStateChangedListener?) {
        if (onStateChangedListener != null) {
            onStateChangedListenersList.remove(onStateChangedListener)
            onStateChangedListenersList.add(onStateChangedListener)
        }
    }

    override fun removeOnStateChangedListener(@Nullable onStateChangedListener: OnStateChangedListener?) {
        if (onStateChangedListener != null) {
            onStateChangedListenersList.remove(onStateChangedListener)
        }
    }

    override fun checkPermissionsAndPSEnabled(@Nullable permissionsAndPSListener: PermissionsAndPSListener?) {
        //TODO: Ignore this, or handle location permission check here.
    }

    @Nullable
    override fun getProviderId(): String? {
        return mProviderId
    }

    @Nullable
    override fun getLatestPosition(): PositionResult? {
        return mLatestPosition
    }

    override fun startPositioningAfter(i: Int, @Nullable s: String?) {}
    override fun terminate() {}

    private val locationCallback: LocationCallback = object : LocationCallback() {
        override fun onLocationResult(@NotNull locationResult: LocationResult) {
            super.onLocationResult(locationResult)
            onLocationChanged(locationResult.lastLocation)
        }
    }

    fun onLocationChanged(@Nullable location: Location?) {
        if (location == null) {
            mLatestPosition = null
            mIsEnabled = false
            return
        }
        if (mIsRunning) {
            mIsEnabled = true
            val recLocation: Location = Location(location)
            val newLocation = MPPositionResult(Point(recLocation), recLocation.accuracy)
            newLocation.androidLocation = recLocation

            // From Google's Santa tracker:
            // "Update our current location only if we've moved at least a metre, to avoid
            // jitter due to lack of accuracy in FusedLocationApi"
            if (mLatestPosition != null) {
                val prevPoint: Point? = mLatestPosition!!.point
                val newPoint: Point? = newLocation.point
                if (prevPoint != null && newPoint != null) {
                    // Check the distance between the prev and new position in 2D (lat/lng)
                    val dist: Double = prevPoint.distanceTo(newPoint)
                    if (dist <= 1.0) {

                        // Get the altitude too. Just imagine the lady/guy is using a lift/elevator/"spiral staircase"...
                        // Use the prev position "android location object" altitude value to run the check
                        val prevLocation = mLatestPosition!!.androidLocation
                        if (prevLocation != null) {
                            val altDiff = Math.abs(recLocation.altitude - prevLocation.altitude)
                            if (altDiff <= 2.0) {
                                return
                            }
                        }
                    }
                }
            }

            // GPS always gives the ground level
            newLocation.floor = Floor.DEFAULT_GROUND_FLOOR_INDEX
            mLatestPosition = newLocation
            mLatestPosition?.provider = this
            mLatestPosition?.androidLocation = recLocation

            //setLatestPosition(mLatestPosition);
            reportPositionUpdate()
        }
    }

    /**
     * Reports to listeners, upon new positioning
     */
    fun reportPositionUpdate() {
        if (mIsRunning) {
            for (listener in onPositionUpdateListeners) {
                if (listener != null && mLatestPosition != null) {
                    listener.onPositionUpdate(mLatestPosition!!)
                }
            }
        }
    }

    init {
        mContext = context
    }
}
