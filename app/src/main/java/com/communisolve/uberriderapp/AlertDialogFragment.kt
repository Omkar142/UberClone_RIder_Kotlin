package com.communisolve.uberriderapp

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.DialogFragment
import com.communisolve.uberriderapp.Common.Common
import com.communisolve.uberriderapp.Event.UserRegistrationBus
import com.communisolve.uberriderapp.Model.RiderModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import org.greenrobot.eventbus.EventBus


class AlertDialogFragment : DialogFragment() {


    lateinit var button_continue: Button
    lateinit var first_name: TextView
    lateinit var last_name: TextView
    lateinit var edit_email_address: TextView
    lateinit var edit_phone: TextView

    lateinit var database: FirebaseDatabase
    lateinit var driverInfoRef: DatabaseReference


//    override fun onStart() {
//        super.onStart()
//        val dialog: Dialog? = dialog
//        if (dialog != null) {
//            val width = ViewGroup.LayoutParams.MATCH_PARENT
//            val height = ViewGroup.LayoutParams.WRAP_CONTENT
//            dialog.getWindow()!!.setLayout(width, height)
//        }
//    }

    override fun onDestroy() {
        super.onDestroy()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment

        val view = inflater.inflate(R.layout.fragment_alert_dialog, container, false)
        getDialog()!!.getWindow()!!.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT));

        database = FirebaseDatabase.getInstance()
        driverInfoRef = database.getReference(Common.RIDER_INFO_REFERENCE)


        button_continue = view.findViewById(R.id.button_continue)
        first_name = view.findViewById(R.id.first_name)
        last_name = view.findViewById(R.id.last_name)
        edit_phone = view.findViewById(R.id.edit_phone)


        button_continue.setOnClickListener {
            if (TextUtils.isEmpty(first_name.text.toString().trim())) {
                first_name.setError("Enter First Name")
                return@setOnClickListener
            }
            if (TextUtils.isEmpty(last_name.text.toString().trim())) {
                last_name.setError("Enter Last Name")
                return@setOnClickListener
            }
            if (TextUtils.isEmpty(edit_phone.text.toString().trim())) {
                edit_phone.setError("Enter Phone Number")
                return@setOnClickListener
            }

            val model = RiderModel(
                first_name.text.toString().trim(),
                last_name.text.toString().trim(),
                edit_phone.text.toString().trim(),
                ""
            )
            driverInfoRef.child(FirebaseAuth.getInstance().currentUser!!.uid)
                .setValue(model)
                .addOnFailureListener {
                    EventBus.getDefault()
                        .postSticky(UserRegistrationBus(false, it.message.toString()))
                }.addOnCompleteListener {
                    if (it.isSuccessful) {
                        Common.currentUser = model
                        EventBus.getDefault()
                            .postSticky(UserRegistrationBus(true, "Success"))
                    }
                }

        }


        dialog!!.setTitle("title")
        return view
    }

    companion object {
        fun newInstance(title: String?): AlertDialogFragment? {
            val frag = AlertDialogFragment()
            val args = Bundle()
            args.putString("title", title)
            frag.setArguments(args)
            return frag
        }
    }


}