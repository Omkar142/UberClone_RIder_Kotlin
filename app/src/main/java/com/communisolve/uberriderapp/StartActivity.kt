package com.communisolve.uberriderapp

import android.app.Activity
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.widget.Toast
import androidx.fragment.app.FragmentManager
import com.communisolve.uberriderapp.Common.Common
import com.communisolve.uberriderapp.Event.UserRegistrationBus
import com.communisolve.uberriderapp.Model.RiderModel
import com.communisolve.uberriderapp.Utils.UserUtils
import com.communisolve.uberriderapp.Utils.toast
import com.firebase.ui.auth.AuthMethodPickerLayout
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.IdpResponse
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.iid.FirebaseInstanceId
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.util.*

@Suppress("UNUSED_ANONYMOUS_PARAMETER")
class StartActivity : AppCompatActivity() {
    lateinit var fm: FragmentManager
    lateinit var alertDialogFragment: AlertDialogFragment

    companion object {
        private val LOGIN_REQUEST_CODE = 7171
        private val SPLASH_TIME_OUT: Long = 1000 // 1 sec
        private const val TAG = "StartActivity"

    }

    lateinit var providers: List<AuthUI.IdpConfig>
    lateinit var firebaseAuth: FirebaseAuth
    lateinit var listner: FirebaseAuth.AuthStateListener

    lateinit var database: FirebaseDatabase
    lateinit var driverInfoRef: DatabaseReference

    override fun onStart() {
        super.onStart()
        delaySplashScreen()
        EventBus.getDefault().register(this)

    }

    private fun delaySplashScreen() {
        Handler().postDelayed({

            firebaseAuth.addAuthStateListener(listner)

        }, SPLASH_TIME_OUT)

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_start)

        init()

    }

    private fun init() {
        database = FirebaseDatabase.getInstance()
        driverInfoRef = database.getReference(Common.RIDER_INFO_REFERENCE)


        fm = getSupportFragmentManager()
        alertDialogFragment =
                AlertDialogFragment.newInstance("Some Title")!!
        providers = Arrays.asList(
                AuthUI.IdpConfig.PhoneBuilder().build(),
                AuthUI.IdpConfig.GoogleBuilder().build()
        )

        firebaseAuth = FirebaseAuth.getInstance()
        listner = FirebaseAuth.AuthStateListener { myFirebaseAuth ->
            val user = myFirebaseAuth.currentUser
            if (user != null) {
                FirebaseInstanceId.getInstance().instanceId.addOnFailureListener { exception ->
                    toast(
                            exception.toString()
                    )
                }
                        .addOnSuccessListener { task ->
                            UserUtils.updateToken(this, task.token)
                            Log.d(TAG, "init: ${task.token}")



                        }
                checkUserFromDatabase()
            } else {
                showLogInLayout()
            }
        }
    }

    private fun checkUserFromDatabase() {

        driverInfoRef.child(FirebaseAuth.getInstance().currentUser!!.uid)
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onCancelled(error: DatabaseError) {
                        Toast.makeText(this@StartActivity, error.toString(), Toast.LENGTH_SHORT).show()
                    }

                    override fun onDataChange(snapshot: DataSnapshot) {

                        if (snapshot.exists()) {
                            Common.currentUser = snapshot.getValue(RiderModel::class.java)!!
                            startActivity(Intent(this@StartActivity, HomeActivity::class.java))
                            finish()

                        } else {
                            alertDialogFragment.show(fm, "fragment_edit_name")
                            alertDialogFragment.isCancelable = false
                        }

                    }

                })


    }


    private fun showLogInLayout() {


        val authMethodPickerLayout =
                AuthMethodPickerLayout.Builder(R.layout.login_methods_firebase_ui)
                        .setPhoneButtonId(R.id.sign_with_phone)
                        .setGoogleButtonId(R.id.sign_with_google)
                        .build();
        startActivityForResult(
                AuthUI.getInstance()
                        .createSignInIntentBuilder()
                        .setAuthMethodPickerLayout(authMethodPickerLayout)
                        .setTheme(R.style.LogInTheme)
                        .setAvailableProviders(providers)
                        .setIsSmartLockEnabled(false)
                        .build()
                , LOGIN_REQUEST_CODE
        )

    }

    override fun onStop() {
        EventBus.getDefault().unregister(this)
        if (firebaseAuth != null && listner != null) firebaseAuth.removeAuthStateListener(listner)
        super.onStop()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == LOGIN_REQUEST_CODE) {
            val response = IdpResponse.fromResultIntent(data)
            if (resultCode == Activity.RESULT_OK) {
                val user = FirebaseAuth.getInstance().currentUser
            } else {
                Toast.makeText(this, "Error:\n" + response!!.error, Toast.LENGTH_SHORT).show()
            }
        }
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    public fun registrationEvent(event: UserRegistrationBus) {
        if (event.isSuccess) {
            alertDialogFragment.dismiss()
            Toast.makeText(this, "" + event.msg, Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
        } else {
            alertDialogFragment.dismiss()
            Toast.makeText(this, "" + event.msg, Toast.LENGTH_SHORT).show()
        }
    }
}