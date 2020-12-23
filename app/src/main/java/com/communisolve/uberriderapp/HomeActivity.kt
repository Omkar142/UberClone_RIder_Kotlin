package com.communisolve.uberriderapp

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.view.Menu
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.navigation.NavigationView
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import androidx.drawerlayout.widget.DrawerLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.bumptech.glide.Glide
import com.communisolve.uberriderapp.Common.Common
import com.communisolve.uberriderapp.Utils.UserUtils
import com.communisolve.uberriderapp.Utils.snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import com.google.firebase.storage.UploadTask
import java.lang.StringBuilder

class HomeActivity : AppCompatActivity() {


    private lateinit var imageUri: Uri
    lateinit var navView: NavigationView
    lateinit var drawerLayout: DrawerLayout
    lateinit var img_avatar: ImageView
    lateinit var navController: NavController

    lateinit var waitingDialog: AlertDialog
    lateinit var storageReference: StorageReference
    private lateinit var appBarConfiguration: AppBarConfiguration


    companion object{
        const val PICK_IMAGE_REQUEST = 1

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)


         drawerLayout = findViewById(R.id.drawer_layout)
         navView = findViewById(R.id.nav_view)
         navController = findNavController(R.id.nav_host_fragment)
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.nav_home, R.id.nav_gallery, R.id.nav_slideshow
            ), drawerLayout
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)
        init()

    }

    private fun init() {

        storageReference = FirebaseStorage.getInstance().reference

        waitingDialog = AlertDialog.Builder(this)
            .setMessage("Waiting...")
            .setCancelable(false).create()

        navView.setNavigationItemSelectedListener {
            if (it.itemId == R.id.nav_sign_out) {
                val builder = AlertDialog.Builder(this@HomeActivity)
                builder.setTitle("Sign Out")
                    .setMessage("Do you really want to sign out?")
                    .setNegativeButton("CANCEL") { dialog, which ->
                        dialog.dismiss()
                    }
                    .setPositiveButton("SIGN OUT") { dialog, which ->
                        FirebaseAuth.getInstance().signOut()
                        startActivity(
                            Intent(this,StartActivity::class.java)
                            .apply {
                                setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                            })
                        finish()

                    }.setCancelable(false)

                val dialog = builder.create()
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                        .setTextColor(ContextCompat.getColor(this,android.R.color.holo_red_dark))
                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                        .setTextColor(ContextCompat.getColor(this,R.color.colorAccent))

                }
                dialog.show()
            }
            true
        }

        val headerView = navView.getHeaderView(0)
        val txt_name = headerView.findViewById<View>(R.id.txt_name) as TextView
        val txt_phone = headerView.findViewById<View>(R.id.txt_phone) as TextView
        img_avatar = headerView.findViewById(R.id.img_avatar) as ImageView


        txt_name.setText(Common.buildWelcomeMessage())
        txt_phone.setText(Common.currentUser!!.phoneNumber)


        Common.currentUser.let {
            if(Common.currentUser !=null && Common.currentUser!!.avatar!=null && !TextUtils.isEmpty(Common.currentUser!!.avatar))
            {
                Glide.with(this)
                    .load(Common.currentUser!!.avatar)
                    .into(img_avatar)
            }
        }

        img_avatar.setOnClickListener {
            startActivityForResult(Intent.createChooser(Intent().apply {
                setType("image/*")
                setAction(Intent.ACTION_GET_CONTENT)
            },"Select Picture"),PICK_IMAGE_REQUEST)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK){
            if (data !=null && data.data !=null){
                imageUri = data.data!!
                img_avatar.setImageURI(imageUri)

                showDialogUpload()
            }
        }
    }

    private fun showDialogUpload() {

        val builder = AlertDialog.Builder(this@HomeActivity)
        builder.setTitle("Change Avatar")
            .setMessage("Do you really want to change Avatar?")
            .setNegativeButton("CANCEL") { dialog, which ->
                dialog.dismiss()
            }
            .setPositiveButton("CHANGE") { dialog, which ->

                if (imageUri != null){
                    waitingDialog.show()
                    val avatarFolder = storageReference.child("avatars/"+FirebaseAuth.getInstance().currentUser!!.uid)

                    avatarFolder.putFile(imageUri)
                        .addOnFailureListener { exception ->
                            snackbar(drawerLayout,exception.message!!)

                            waitingDialog.dismiss()
                        }.addOnCompleteListener {task ->
                            if (task.isSuccessful)
                            {
                                avatarFolder.downloadUrl.addOnSuccessListener { uri ->
                                    val update_data = HashMap<String,Any>()
                                    update_data.put("avatar",uri.toString())

                                    UserUtils.updateUser(drawerLayout,update_data)
                                }
                            }
                            waitingDialog.dismiss()
                        }.addOnProgressListener {snapshot: UploadTask.TaskSnapshot ->
                            val progress = (100.0*snapshot.bytesTransferred/snapshot.totalByteCount)
                            waitingDialog.setMessage(StringBuilder("Uploading: ").append(progress).append("%"))

                        }
                }

            }.setCancelable(false)

        val dialog = builder.create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setTextColor(resources.getColor(android.R.color.holo_red_dark))
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                .setTextColor(resources.getColor(R.color.colorAccent))

        }
        dialog.show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.home, menu)
        return true
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }
}