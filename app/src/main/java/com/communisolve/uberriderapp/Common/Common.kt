package com.communisolve.uberriderapp.Common

import com.communisolve.uberriderapp.Model.RiderModel
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
}