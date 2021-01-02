package com.communisolve.uberriderapp.Common

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import com.communisolve.uberriderapp.Model.AnimationModel
import com.communisolve.uberriderapp.Model.DriverGeoModel
import com.communisolve.uberriderapp.Model.RiderModel
import com.communisolve.uberriderapp.R
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker


object Common {

    val driversSubscribe: MutableMap<String, AnimationModel> = HashMap<String, AnimationModel>()
    val markerList: MutableMap<String, Marker> = HashMap<String, Marker>()
    val driversFound: MutableSet<DriverGeoModel> = HashSet<DriverGeoModel>()
    val DRIVER_LOCATION_REFERENCES: String = "DriversLocation"
    fun buildWelcomeMessage(): String {
        return StringBuilder("Welcome, ")
            .append(currentUser!!.firstName)
            .append(" ")
            .append(currentUser!!.lastName)
            .toString()

    }

    lateinit var currentUser: RiderModel
    val RIDER_INFO_REFERENCE: String = "Riders"

    fun showNotification(
        context: Context,
        id: Int,
        title: String?,
        body: String?,
        intent: Intent?
    ) {
        var pendingIntent: PendingIntent? = null

        if (intent != null) {
            pendingIntent =
                PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val NOTIFICATION_CHANNEL_ID = "edmt_dev_uber_remake"

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID, "Uber Remake",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationChannel
            notificationChannel.enableLights(true)
            notificationChannel.also {
                it.description = "Uber remake"
                it.enableLights(true)
                it.lightColor = Color.RED
                it.vibrationPattern = longArrayOf(0, 1000, 500, 1000)
                it.enableVibration(true)
            }

            val builder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)

            builder.also {
                it.setContentTitle(title)
                it.setContentText(body)
                it.setAutoCancel(false)
                it.setPriority(NotificationCompat.PRIORITY_HIGH)
                it.setDefaults(Notification.DEFAULT_VIBRATE)
                it.setSmallIcon(R.drawable.ic_baseline_directions_car_24)
                it.setLargeIcon(
                    BitmapFactory.decodeResource(
                        context.resources,
                        R.drawable.ic_baseline_directions_car_24
                    )
                )
            }

            if (pendingIntent != null)
                builder.setContentIntent(pendingIntent)

            val notification = builder.build()
            notificationManager.notify(id, notification)
        }
    }

    fun buildName(firstName: String?, lastName: String?): String? {

        return StringBuilder(firstName).append(" ").append(lastName).toString()
    }

    val NOTI_BODY: String? = "body"
    val NOTI_TITLE: String? = "title"
    val TOKEN_REFERENCE: String = "Token"
    val DRIVERS_LOCATION_REFERENCE: String = "DriversLocation"
    val DRIVER_INFO_REFERENCE: String = "DriverInfo"


    //DECODE POLY
    fun decodePoly(encoded: String): ArrayList<LatLng?>? {
        val poly = ArrayList<LatLng?>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0
        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].toInt() - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lat += dlat
            shift = 0
            result = 0
            do {
                b = encoded[index++].toInt() - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lng += dlng
            val p = LatLng(
                lat.toDouble() / 1E5,
                lng.toDouble() / 1E5
            )
            poly.add(p)
        }
        return poly
    }


    //GET BEARING
    fun getBearing(begin: LatLng, end: LatLng): Float {
        //You can copy this function by link at description
        val lat = Math.abs(begin.latitude - end.latitude)
        val lng = Math.abs(begin.longitude - end.longitude)
        if (begin.latitude < end.latitude && begin.longitude < end.longitude) return Math.toDegrees(
            Math.atan(lng / lat)
        )
            .toFloat() else if (begin.latitude >= end.latitude && begin.longitude < end.longitude) return (90 - Math.toDegrees(
            Math.atan(lng / lat)
        ) + 90).toFloat() else if (begin.latitude >= end.latitude && begin.longitude >= end.longitude) return (Math.toDegrees(
            Math.atan(lng / lat)
        ) + 180).toFloat() else if (begin.latitude < end.latitude && begin.longitude >= end.longitude) return (90 - Math.toDegrees(
            Math.atan(lng / lat)
        ) + 270).toFloat()
        return (-1).toFloat()
    }
}