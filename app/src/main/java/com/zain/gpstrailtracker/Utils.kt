package com.zain.gpstrailtracker

import android.content.Context
import android.location.Location
import androidx.core.content.edit

//Returns the `location` object as a human readable string.

fun Location?.toText(): String {
    return if (this != null) {
        "($latitude, $longitude)"
    } else {
        "Unknown location"
    }
}

/*
 * Provides access to SharedPreferences for location to Activities and Services.
 */
internal object SharedPreferenceUtil {

    const val KEY_FOREGROUND_ENABLED = "tracking_foreground_location"
    private const val KEY_STARTING_TIME = "starting_time"


    /*
     * Returns true if requesting location updates, otherwise returns false.
     */
    fun isLocationTracking(context: Context): Boolean =
        context.getSharedPreferences(
            context.getString(R.string.preference_file_key), Context.MODE_PRIVATE)
            .getBoolean(KEY_FOREGROUND_ENABLED, false)

    /*
     * Stores the location updates state in SharedPreferences.
     */
    fun saveLocationTracking(context: Context, requestingLocationUpdates: Boolean) =
        context.getSharedPreferences(
            context.getString(R.string.preference_file_key),
            Context.MODE_PRIVATE).edit {
            putBoolean(KEY_FOREGROUND_ENABLED, requestingLocationUpdates)
        }


    //starting time when service starts
    fun getStartingTime(context: Context): Long =
        context.getSharedPreferences(
            context.getString(R.string.preference_file_key), Context.MODE_PRIVATE)
            .getLong(KEY_STARTING_TIME, 0)

    //starting time when service starts
    fun saveStartingTime(context: Context, time: Long) =
        context.getSharedPreferences(
            context.getString(R.string.preference_file_key),
            Context.MODE_PRIVATE).edit {
            putLong(KEY_STARTING_TIME, time)
        }


     fun getDistanceBetweenTwoPoints(
        lat1: Double?,
        lon1: Double?,
        lat2: Double?,
        lon2: Double?
    ): Float {
        val distance = FloatArray(2)
        if (lat1 != null) {
            if (lon1 != null) {
                if (lat2 != null) {
                    if (lon2 != null) {
                        Location.distanceBetween(
                            lat1, lon1,
                            lat2, lon2, distance
                        )
                    }
                }
            }
        }
        return distance[0]
    }

    //convert total time to hours and minutes
     fun getTime(time: String): String {

        val totalSecs = time.toInt()

        val hours = totalSecs / 3600
        val minutes = (totalSecs % 3600) / 60
        val seconds = totalSecs % 60;

        return String.format("%02d:%02d:%02d", hours, minutes, seconds)


    }

}
