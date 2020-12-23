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
import android.util.Log
import androidx.core.app.NotificationCompat
import com.communisolve.uberriderapp.Model.RiderModel
import com.communisolve.uberriderapp.R
import java.lang.StringBuilder

object Common {

    fun buildWelcomeMessage(): String {
        return StringBuilder("Welcome, ")
            .append(currentUser!!.firstName)
            .append(" ")
            .append(currentUser!!.lastName)
            .toString()

    }
    lateinit var currentUser: RiderModel
    val RIDER_INFO_REFERENCE: String="Riders"

    fun showNotification(
        context: Context,
        id: Int,
        title: String?,
        body: String?,
        intent: Intent?
    ) {
        var pendingIntent: PendingIntent?=null

        if (intent != null){
            pendingIntent = PendingIntent.getActivity(context,id,intent, PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val NOTIFICATION_CHANNEL_ID = "edmt_dev_uber_remake"

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            val notificationChannel = NotificationChannel(NOTIFICATION_CHANNEL_ID,"Uber Remake",
                NotificationManager.IMPORTANCE_HIGH)
            notificationChannel
            notificationChannel.enableLights(true)
            notificationChannel.also {
                it.description = "Uber remake"
                it.enableLights(true)
                it.lightColor = Color.RED
                it.vibrationPattern = longArrayOf(0,1000,500,1000)
                it.enableVibration(true)
            }

            val builder = NotificationCompat.Builder(context,NOTIFICATION_CHANNEL_ID)

            builder.also {
                it.setContentTitle(title)
                it.setContentText(body)
                it.setAutoCancel(false)
                it.setPriority(NotificationCompat.PRIORITY_HIGH)
                it.setDefaults(Notification.DEFAULT_VIBRATE)
                it.setSmallIcon(R.drawable.ic_baseline_directions_car_24)
                it.setLargeIcon(BitmapFactory.decodeResource(context.resources,R.drawable.ic_baseline_directions_car_24))
            }

            if (pendingIntent !=null)
                builder.setContentIntent(pendingIntent)

            val notification = builder.build()
            notificationManager.notify(id,notification)
        }
    }

    val NOTI_BODY: String?="body"
    val NOTI_TITLE: String?="title"
    val TOKEN_REFERENCE: String="Token"
    val DRIVERS_LOCATION_REFERENCE: String="DriversLocation"
    val DRIVER_INFO_REFERENCE: String="DriverInfo"
}