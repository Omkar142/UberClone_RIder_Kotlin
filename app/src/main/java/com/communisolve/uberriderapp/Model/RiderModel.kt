package com.communisolve.uberriderapp.Model

class RiderModel {
    var firstName: String = ""
    var lastName: String = ""
    var phoneNumber = ""
    var avatar: String = ""

    constructor()
    constructor(firstName: String, lastName: String, phoneNumber: String, avatar: String) {
        this.firstName = firstName
        this.lastName = lastName
        this.phoneNumber = phoneNumber
        this.avatar = avatar
    }
}
