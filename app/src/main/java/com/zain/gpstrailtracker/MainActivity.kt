package com.zain.gpstrailtracker

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.material.snackbar.Snackbar
import com.zain.gpstrailtracker.Helper.READ_REQUEST
import com.zain.gpstrailtracker.Helper.WRITE_REQUEST
import com.zain.gpstrailtracker.Helper.isStoragePermissionGranted
import kotlinx.android.synthetic.main.activity_main.*

import android.os.Build
import com.zain.gpstrailtracker.SharedPreferenceUtil.getTime


class MainActivity : AppCompatActivity(), SharedPreferences.OnSharedPreferenceChangeListener {


    private  val TAG = "MainActivity"
    private  val Location_PERMISSIONS_REQUEST_CODE = 34

    private var locationServiceBound = false

    // Provides location updates for while-in-use feature.
    private var locationService: LocationService? = null

    // Listens for location broadcasts from LocationService.
    private lateinit var locationBroadcastReceiver: LocationBroadcastReceiver

    private lateinit var sharedPreferences: SharedPreferences
    private var startingTime: Long = 0

    companion object{
        const val KEY_LOCATIONS_LIST = "KEY_LOCATIONS_LIST"
        const val KEY_SECONDS = "KEY_MINUTES"
    }

    var seconds=0


    //Timer handler to track time
    var timerHandler: Handler = Handler(Looper.getMainLooper())
    var timerRunnable: Runnable = object : Runnable {
        override fun run() {
            val millis = System.currentTimeMillis() - startingTime
            seconds = (millis / 1000).toInt()

            totalTime.text = getTime(seconds.toString())
            timerHandler.postDelayed(this, 1000)
        }
    }

    // Monitors connection to the while-in-use service.
    private val foregroundOnlyServiceConnection = object : ServiceConnection {

        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val binder = service as LocationService.LocalBinder
            locationService = binder.service
            locationServiceBound = true
        }

        override fun onServiceDisconnected(name: ComponentName) {
            locationService = null
            locationServiceBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        locationBroadcastReceiver = LocationBroadcastReceiver()

        sharedPreferences =
            getSharedPreferences(getString(R.string.preference_file_key), Context.MODE_PRIVATE)


        startBtn.setOnClickListener {
          if(isStoragePermissionGranted(this)){startTracking()}
        }
    }

    private fun startTracking(){

        val enabled = sharedPreferences.getBoolean(
            SharedPreferenceUtil.KEY_FOREGROUND_ENABLED, false)

        if (enabled) {
            locationService?.unsubscribeToLocationUpdates()

        } else {

            if (locationPermissionApproved()) {
                startService()
            } else {
                requestLocationPermissions()
            }
        }
    }

    private fun startService(){
        locationService?.subscribeToLocationUpdates()
        SharedPreferenceUtil.saveStartingTime(this,System.currentTimeMillis())
        startTimer()
    }

    private fun startTimer(){

        startingTime = SharedPreferenceUtil.getStartingTime(this)
        timerHandler.postDelayed(timerRunnable, 0)
    }

    private fun stopTimer(){
        timerHandler.removeCallbacks(timerRunnable)
    }

    override fun onStart() {
        super.onStart()

        updateButtonState(
            sharedPreferences.getBoolean(SharedPreferenceUtil.KEY_FOREGROUND_ENABLED, false)
        )
        sharedPreferences.registerOnSharedPreferenceChangeListener(this)

        val serviceIntent = Intent(this, LocationService::class.java)
        bindService(serviceIntent, foregroundOnlyServiceConnection, Context.BIND_AUTO_CREATE)

        if(SharedPreferenceUtil.isLocationTracking(this)){
            startTimer()
        }
    }

    override fun onResume() {
        super.onResume()
        LocalBroadcastManager.getInstance(this).registerReceiver(
            locationBroadcastReceiver,
            IntentFilter(
                LocationService.ACTION_LOCATION_BROADCAST)
        )
    }

    override fun onPause() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(
            locationBroadcastReceiver
        )
        super.onPause()
    }

    override fun onStop() {
        if (locationServiceBound) {
            unbindService(foregroundOnlyServiceConnection)
            locationServiceBound = false
        }
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)

        super.onStop()
    }


    override fun onDestroy() {
        super.onDestroy()
        stopTimer()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        // Updates button states if new while in use location is added to SharedPreferences.
        if (key == SharedPreferenceUtil.KEY_FOREGROUND_ENABLED) {

            val serviceRunningStatus=sharedPreferences.getBoolean(
                SharedPreferenceUtil.KEY_FOREGROUND_ENABLED, false)

            updateButtonState(serviceRunningStatus)

            if(!serviceRunningStatus) {
                SharedPreferenceUtil.saveStartingTime(this, 0)
                stopTimer()
                val intent = Intent(this, ResultActivity::class.java)
                intent.putExtra(KEY_SECONDS, seconds)
                startActivity(intent)
            }
        }
    }


    private fun locationPermissionApproved(): Boolean {
        return PackageManager.PERMISSION_GRANTED == ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }


    private fun requestLocationPermissions() {
        val provideRationale = locationPermissionApproved()

        // If the user denied a previous request, but didn't check "Don't ask again", provide
        // additional rationale.
        if (provideRationale) {
            Snackbar.make(
                findViewById(R.id.activity_main),
                R.string.permission_rationale,
                Snackbar.LENGTH_LONG
            )
                .setAction(R.string.ok) {
                    // Request permission
                    ActivityCompat.requestPermissions(
                        this@MainActivity,
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                        Location_PERMISSIONS_REQUEST_CODE
                    )
                }
                .show()
        } else {
            Log.d(TAG, "Request foreground only permission")
            ActivityCompat.requestPermissions(
                this@MainActivity,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                Location_PERMISSIONS_REQUEST_CODE
            )
        }
    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            Location_PERMISSIONS_REQUEST_CODE -> when {
                grantResults.isEmpty() ->
                    // If user interaction was interrupted, the permission request
                    // is cancelled and you receive empty arrays.
                    Log.d(TAG, "User interaction was cancelled.")

                grantResults[0] == PackageManager.PERMISSION_GRANTED ->
                    // Permission was granted.
                   startService()

                else -> {
                    // Permission denied.
                    updateButtonState(false)

                    Snackbar.make(
                        findViewById(R.id.activity_main),
                        R.string.permission_denied_explanation,
                        Snackbar.LENGTH_LONG
                    )
                        .setAction(R.string.settings) {
                            // Build intent that displays the App settings screen.
                            val intent = Intent()
                            intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                            val uri = Uri.fromParts(
                                "package",
                               packageName,
                                null
                            )
                            intent.data = uri
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            startActivity(intent)
                        }
                        .show()
                }
            }
            READ_REQUEST ->{

                if (Build.VERSION.SDK_INT >= 26) {
                  isStoragePermissionGranted(this)
                    return
                }

                startTracking()

            }
            WRITE_REQUEST ->{

                startTracking()
            }
        }
    }



    private fun updateButtonState(trackingLocation: Boolean) {
        if (trackingLocation) {
            startBtn.text = getString(R.string.stop)
        } else {
            startBtn.text = getString(R.string.start)
        }
    }






    //Receiver for location broadcasts from [LocationService].

    private inner class LocationBroadcastReceiver : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {

            val currentSpeed = intent.getFloatExtra(
                LocationService.EXTRA_CURRENT_SPEED,0f
            )
            val averageSpeed = intent.getLongExtra(
                LocationService.EXTRA_AVERAGE_SPEED,0
            )
            val totalDistance = intent.getLongExtra(
                LocationService.EXTRA_DISTANCE,0
            )

            val distance=totalDistance*0.001



            val kmString= String.format("%.0f",currentSpeed)+" Km / hr"
            val averageSpeedString= String.format("%.1f",averageSpeed.toFloat())+" Km / hr"
            val distanceString= String.format("%.1f",distance)+" Km"
            currentSpeedTV.text=kmString
            averageSpeedTV.text=averageSpeedString
            distanceTV.text=distanceString


        }
    }





}
