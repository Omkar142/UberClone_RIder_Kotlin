package com.communisolve.uberriderapp.Utils

import android.content.Context
import android.view.View
import com.communisolve.uberriderapp.Common.Common
import com.communisolve.uberriderapp.Model.TokenModel
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

object UserUtils {
    fun updateUser(
        view: View?,
        updateData:Map<String,Any>
    ){
        FirebaseDatabase.getInstance()
            .getReference(Common.RIDER_INFO_REFERENCE)
            .child(FirebaseAuth.getInstance().currentUser!!.uid)
            .updateChildren(updateData)
            .addOnFailureListener { exception ->
                Snackbar.make(view!!,exception.message!!,Snackbar.LENGTH_SHORT).show()
            }
            .addOnSuccessListener {
                Snackbar.make(view!!,"Update information Success",Snackbar.LENGTH_SHORT).show()

            }
    }

    fun updateToken(context: Context, token: String) {
        val tokenModel = TokenModel(token)


        FirebaseDatabase.getInstance().getReference(Common.TOKEN_REFERENCE)
            .child(FirebaseAuth.getInstance().currentUser!!.uid)
            .setValue(tokenModel)
            .addOnFailureListener { exception ->

            }.addOnSuccessListener {

            }
    }
}