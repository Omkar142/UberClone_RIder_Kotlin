package com.communisolve.uberriderapp.callback

import com.communisolve.uberriderapp.Model.DriverGeoModel

interface IFirebaseDriverInfoListner {
    fun onDriverInfoLoadSuccess(driverGeoModel: DriverGeoModel?)
}