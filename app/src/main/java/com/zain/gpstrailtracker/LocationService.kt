package com.zain.gpstrailtracker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.location.Location
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.zain.gpstrailtracker.SharedPreferenceUtil.getDistanceBetweenTwoPoints
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import java.io.FileOutputStream
import java.io.StringWriter
import java.lang.reflect.Type
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.Transformer
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import kotlin.collections.ArrayList
import android.os.Environment

import android.os.Build



class LocationService : Service() {
    /*
     * Checks whether the bound activity has really gone away (foreground service with notification
     * created) or simply orientation change (no-op).
     */
    private var configurationChange = false

    private var serviceRunningInForeground = false

    private val localBinder = LocalBinder()

    private lateinit var notificationManager: NotificationManager

    private var previousLocation:Location?=null
    private var totalDistance: Long = 0
    private var averageSpeed: Long = 0
    private var speedSum: Long = 0
    private var intervals: Long = 0
    private lateinit var trkseg:Element
    private lateinit var doc:Document



    // FusedLocationProviderClient - Main class for receiving location updates.
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient

    // LocationRequest - Requirements for the location updates, i.e., how often you should receive
    // updates, the priority, etc.
    private lateinit var locationRequest: LocationRequest

    // LocationCallback - Called when FusedLocationProviderClient has a new Location.
    private lateinit var locationCallback: LocationCallback

    // Used only for local storage of the last known location. Usually, this would be saved to your
    // database, but because this is a simplified sample without a full database, we only need the
    // last location to create a Notification if the user navigates away from the app.
    private var currentLocation: Location? = null

    override fun onCreate() {

        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)

        locationRequest = LocationRequest.create().apply {
            // Sets the desired interval for active location updates.

            interval = TimeUnit.SECONDS.toMillis(5)

            // Sets the fastest rate for active location updates. This interval is exact, and your
            fastestInterval = TimeUnit.SECONDS.toMillis(5)

            // Sets the maximum time when batched location updates are delivered. Updates may be
            maxWaitTime = TimeUnit.SECONDS.toMillis(5)

            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }


        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)

                currentLocation = locationResult.lastLocation

                currentLocation?.also {
                    calculateTracking(it)
                }



                if (serviceRunningInForeground) {
                    notificationManager.notify(
                        NOTIFICATION_ID,
                        generateNotification(currentLocation))
                }
            }
        }


        initXmlWriter()
    }


    //process locations data and generate required values
    private fun calculateTracking(location: Location) {

        addTrackPoints(location)

        intervals++


        previousLocation?.also {

            val meters=getDistanceBetweenTwoPoints(it.latitude,it.longitude,location.latitude,location.longitude)
            totalDistance= (totalDistance+meters).toLong()
            val meterPerSecond=meters/5
            val metersPerHour=3600*meterPerSecond
            val kmPerHour=metersPerHour/1000
            speedSum= (speedSum+kmPerHour).toLong()
            averageSpeed=speedSum/intervals

            val intent = Intent(ACTION_LOCATION_BROADCAST)
            intent.putExtra(EXTRA_CURRENT_SPEED, kmPerHour)
            intent.putExtra(EXTRA_AVERAGE_SPEED, averageSpeed)
            intent.putExtra(EXTRA_DISTANCE, totalDistance)
            LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)

        }

        previousLocation=location

    }



    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {

        val cancelLocationTrackingFromNotification =
            intent.getBooleanExtra(EXTRA_CANCEL_LOCATION_TRACKING_FROM_NOTIFICATION, false)

        if (cancelLocationTrackingFromNotification) {
            unsubscribeToLocationUpdates()
            previousLocation=null
            stopSelf()
        }
        // Tells the system not to recreate the service after it's been killed.
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent): IBinder {

        // MainActivity (client) comes into foreground and binds to service, so the service can
        // become a background services.
        stopForeground(true)
        serviceRunningInForeground = false
        configurationChange = false
        return localBinder
    }

    override fun onRebind(intent: Intent) {
        Log.d(TAG, "onRebind()")

        // MainActivity (client) returns to the foreground and rebinds to service, so the service
        // can become a background services.
        stopForeground(true)
        serviceRunningInForeground = false
        configurationChange = false
        super.onRebind(intent)
    }

    override fun onUnbind(intent: Intent): Boolean {
        Log.d(TAG, "onUnbind()")

        // MainActivity (client) leaves foreground, so service needs to become a foreground service

        if (!configurationChange && SharedPreferenceUtil.isLocationTracking(this)) {
            Log.d(TAG, "Start foreground service")
            val notification = generateNotification(currentLocation)
            startForeground(NOTIFICATION_ID, notification)
            serviceRunningInForeground = true
        }

        // Ensures onRebind() is called if MainActivity (client) rebinds.
        return true
    }



    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        configurationChange = true
    }

    fun subscribeToLocationUpdates() {

        SharedPreferenceUtil.saveLocationTracking(this, true)

        // Binding to this service doesn't actually trigger onStartCommand(). That is needed to
          startService(Intent(applicationContext, LocationService::class.java))

        try {
            fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())

        } catch (unlikely: SecurityException) {
            SharedPreferenceUtil.saveLocationTracking(this, false)
        }
    }

    fun unsubscribeToLocationUpdates() {

        try {
            val removeTask = fusedLocationProviderClient.removeLocationUpdates(locationCallback)
            removeTask.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    previousLocation=null
                    saveDataToGPXFile()
                    stopSelf()
                }
            }

            SharedPreferenceUtil.saveLocationTracking(this, false)


        } catch (unlikely: SecurityException) {
            SharedPreferenceUtil.saveLocationTracking(this, true)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.v("LocationService","OnDestroy")

    }

    /*
     * Generates a BIG_TEXT_STYLE Notification that represent latest location.
     */
    private fun generateNotification(location: Location?): Notification {

        // 0. Get data
        val mainNotificationText = location?.toText() ?: getString(R.string.no_location_text)
        val titleText = getString(R.string.app_name)

        // 1. Create Notification Channel for O+ and beyond devices (26+).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val notificationChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID, titleText, NotificationManager.IMPORTANCE_DEFAULT)


            notificationManager.createNotificationChannel(notificationChannel)
        }

        // 2. Build the BIG_TEXT_STYLE.
        val bigTextStyle = NotificationCompat.BigTextStyle()
            .bigText(mainNotificationText)
            .setBigContentTitle(titleText)

        // 3. Set up main Intent/Pending Intents for notification.
        val launchActivityIntent = Intent(this, MainActivity::class.java)

        val cancelIntent = Intent(this, LocationService::class.java)
        cancelIntent.putExtra(EXTRA_CANCEL_LOCATION_TRACKING_FROM_NOTIFICATION, true)


        // 4. Build and issue the notification.
        val notificationCompatBuilder =
            NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)

        return notificationCompatBuilder
            .setStyle(bigTextStyle)
            .setContentTitle(titleText)
            .setContentText(mainNotificationText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

            .build()
    }


    inner class LocalBinder : Binder() {
        internal val service: LocationService
            get() = this@LocationService
    }

    companion object {
        private const val TAG = "LocationService"

        private const val PACKAGE_NAME = "com.zain.gpstrailtracker"

        internal const val ACTION_LOCATION_BROADCAST =
            "$PACKAGE_NAME.action.FOREGROUND_ONLY_LOCATION_BROADCAST"

        internal const val EXTRA_CURRENT_SPEED = "$PACKAGE_NAME.extra.EXTRA_CURRENT_SPEED"

        internal const val EXTRA_AVERAGE_SPEED = "$PACKAGE_NAME.extra.EXTRA_AVERAGE_SPEED"

        internal const val EXTRA_DISTANCE = "$PACKAGE_NAME.extra.EXTRA_DISTANCE"

        private const val EXTRA_CANCEL_LOCATION_TRACKING_FROM_NOTIFICATION =
            "$PACKAGE_NAME.extra.CANCEL_LOCATION_TRACKING_FROM_NOTIFICATION"

        private const val NOTIFICATION_ID = 1001

        private const val NOTIFICATION_CHANNEL_ID = "location_channel_01"
        lateinit var FILE_PATH:File



    }



    //here we are initializing document builder to write locations data in xml format
    private fun initXmlWriter(){

        doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument()
        // create root element gpx
        val gpx = doc.createElement("gpx")
        doc.appendChild(gpx)

        // create track element
        val trk = doc.createElement("trk")
        gpx.appendChild(trk)

        // create name element
        val name = doc.createElement("name")
        trk.appendChild(name)

        trkseg = doc.createElement("trkseg")
        trk.appendChild(trkseg)


    }


    //add track location points to xml document
    private fun addTrackPoints(location:Location){


        val trkpt = doc.createElement("trkpt")
        trkseg.appendChild(trkpt)

        trkpt.setAttribute("lat",location.latitude.toString())
        trkpt.setAttribute("lon",location.longitude.toString())
        val ele = doc.createElement("ele")
        ele.textContent=location.altitude.toString()
        trkpt.appendChild(ele)
        val time = doc.createElement("time")
        time.textContent=currentTime()
        trkpt.appendChild(time)

    }


    //provide current data and time with specified format
    private fun currentTime(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
    }


    //save data to xml file and store xml file to external storage
    private fun saveDataToGPXFile(){

        val transformer: Transformer = TransformerFactory.newInstance().newTransformer()
        val writer = StringWriter()
        val result = StreamResult(writer)
        transformer.transform(DOMSource(doc), result)


        val path=getExternalFilesDir(null)
        val file = File(path,  currentTime()+".xml")
        FILE_PATH=file

        val stream = FileOutputStream(file)
        stream.use { stream ->
            stream.write(writer.toString().toByteArray())
        }



    }




}
