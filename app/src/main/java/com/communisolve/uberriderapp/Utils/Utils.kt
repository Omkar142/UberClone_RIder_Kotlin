package com.communisolve.uberriderapp.Utils

import android.content.Context
import android.view.View
import android.widget.Toast
import com.google.android.material.snackbar.Snackbar

fun Context.toast(message: CharSequence) =
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

fun Context.snackbar(view: View, message: CharSequence) =
    Snackbar.make(view,message, Snackbar.LENGTH_SHORT).show()