package com.communisolve.uberriderapp.Services

import android.util.Log
import com.communisolve.uberriderapp.Common.Common
import com.communisolve.uberriderapp.Utils.UserUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlin.random.Random

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        if (FirebaseAuth.getInstance().currentUser !=null){
            UserUtils.updateToken(this,token)
        }
    }
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        val data = remoteMessage.data

        if (data != null)
        {
            val msg = StringBuilder("title: ${data[Common.NOTI_TITLE]}")
                .append("\nbody:${data[Common.NOTI_BODY]}")

            Log.d(TAG, "onMessageReceived: title: ${data[Common.NOTI_TITLE]} body:${data[Common.NOTI_BODY]}")
            Common.showNotification(this, Random.nextInt(),
                data[Common.NOTI_TITLE],
                data[Common.NOTI_BODY],
                null)

        }
    }

    companion object{
        private const val TAG = "MyFirebaseMessagingServ"
    }
}