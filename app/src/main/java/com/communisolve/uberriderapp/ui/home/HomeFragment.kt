package com.communisolve.uberriderapp.ui.home

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.content.res.Resources
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.communisolve.uberriderapp.Common.Common
import com.communisolve.uberriderapp.Model.DriverGeoModel
import com.communisolve.uberriderapp.Model.DriverInfoModel
import com.communisolve.uberriderapp.Model.GeoQueryModel
import com.communisolve.uberriderapp.R
import com.communisolve.uberriderapp.Utils.snackbar
import com.communisolve.uberriderapp.Utils.toast
import com.communisolve.uberriderapp.callback.IFirebaseDriverInfoListner
import com.communisolve.uberriderapp.callback.IFirebaseFailedListner
import com.firebase.geofire.GeoFire
import com.firebase.geofire.GeoLocation
import com.firebase.geofire.GeoQueryEventListener
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.database.*
import com.karumi.dexter.Dexter
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionDeniedResponse
import com.karumi.dexter.listener.PermissionGrantedResponse
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.single.PermissionListener
import io.reactivex.Observable
import io.reactivex.Scheduler
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import java.io.IOException
import java.util.*
import kotlin.collections.ArrayList

class HomeFragment : Fragment(), OnMapReadyCallback, IFirebaseDriverInfoListner {

    companion object {
        private const val TAG = "HomeFragment"
        private const val MAPVIEW_BUNDLE_KEY = "MapViewBundleKey"
    }

    private lateinit var homeViewModel: HomeViewModel
    private lateinit var mMap: GoogleMap
    lateinit var mapFragment: SupportMapFragment

    //Location
    lateinit var locationRequest: LocationRequest
    lateinit var locationCallback: LocationCallback
    lateinit var fusedLocationProviderClient: FusedLocationProviderClient

    var distance = 1.0
    var LIMIT_RANGE = 10.0
    var previousLocation: Location? = null
    var currentLocation: Location? = null

    var firstTime = true

    //listner
    lateinit var iFirebaseDriverInfoListner: IFirebaseDriverInfoListner
    lateinit var iFirebaseFailedListner: IFirebaseFailedListner

    var cityName = ""

    override fun onDestroy() {
        fusedLocationProviderClient.removeLocationUpdates(locationCallback)
        super.onDestroy()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        homeViewModel =
            ViewModelProvider(this).get(HomeViewModel::class.java)
        val root = inflater.inflate(R.layout.fragment_home, container, false)
        init()

        mapFragment = childFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)


        return root
    }

    private fun init() {

        iFirebaseDriverInfoListner = this
        locationRequest = LocationRequest()
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
        locationRequest.setFastestInterval(3000)
        locationRequest.setSmallestDisplacement(10f)
        locationRequest.interval = 5000


        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult?) {
                super.onLocationResult(locationResult)
                val newPos = LatLng(
                    locationResult!!.lastLocation.latitude,
                    locationResult!!.lastLocation.longitude
                )
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(newPos, 18f))

                //If user has change location, calculate and load driver again
                if (firstTime) {
                    previousLocation = locationResult.lastLocation
                    currentLocation = locationResult.lastLocation

                    firstTime = false
                } else {
                    previousLocation = currentLocation
                    currentLocation = locationResult.lastLocation
                }

                if (previousLocation!!.distanceTo(currentLocation) / 1000 <= LIMIT_RANGE)
                    loadAvailableDrivers()


            }
        }

        fusedLocationProviderClient =
            LocationServices.getFusedLocationProviderClient(requireContext())
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            requireContext().snackbar(requireView(), "Permission Denied")
            return
        }
        fusedLocationProviderClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.myLooper()
        )

        loadAvailableDrivers()
    }

    private fun loadAvailableDrivers() {
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Snackbar.make(requireView(), "Location Permission Requires", Snackbar.LENGTH_SHORT)
                .show()

            return
        }
        fusedLocationProviderClient.lastLocation
            .addOnFailureListener { exception ->
                Snackbar.make(requireView(), exception.message!!, Snackbar.LENGTH_SHORT).show()
            }.addOnSuccessListener { location ->

                //load all drivers in city
                val geoCoder = Geocoder(requireContext(), Locale.getDefault())
                var addressList : List<Address> = ArrayList()

                try {
                    addressList = geoCoder.getFromLocation(location.latitude, location.longitude, 1)
                    cityName = addressList[0].locality

                    val driver_location_ref = FirebaseDatabase.getInstance()
                        .getReference(Common.DRIVERS_LOCATION_REFERENCE).child(cityName)

                    val gf = GeoFire(driver_location_ref)
                    val geoQuery = gf.queryAtLocation(
                        GeoLocation(location.latitude, location.longitude),
                        distance
                    )

                    geoQuery.removeAllListeners()

                    geoQuery.addGeoQueryEventListener(object : GeoQueryEventListener {
                        override fun onGeoQueryReady() {
                            if (distance <= LIMIT_RANGE) {
                                distance++
                                loadAvailableDrivers()
                            } else {
                                distance = 0.0
                                addDriverMarker()
                            }
                        }

                        override fun onKeyEntered(key: String?, location: GeoLocation?) {
                            Common.driversFound.add(DriverGeoModel(key!!, location!!))
                        }

                        override fun onKeyMoved(key: String?, location: GeoLocation?) {
                        }

                        override fun onKeyExited(key: String?) {
                        }

                        override fun onGeoQueryError(error: DatabaseError?) {
                            Snackbar.make(
                                requireView(),
                                error!!.message,
                                Snackbar.LENGTH_SHORT
                            ).show()
                        }

                    })

                    driver_location_ref.addChildEventListener(object : ChildEventListener {
                        override fun onCancelled(error: DatabaseError) {
                            Snackbar.make(
                                requireView(),
                                error!!.message,
                                Snackbar.LENGTH_SHORT
                            ).show()
                        }

                        override fun onChildMoved(
                            snapshot: DataSnapshot,
                            previousChildName: String?
                        ) {
                        }

                        override fun onChildChanged(
                            snapshot: DataSnapshot,
                            previousChildName: String?
                        ) {
                        }

                        override fun onChildAdded(
                            snapshot: DataSnapshot,
                            previousChildName: String?
                        ) {
                            val geoQueryModel = snapshot.getValue(GeoQueryModel::class.java)
                            val geoLocation =
                                GeoLocation(geoQueryModel!!.l!![0], geoQueryModel!!.l!![1])

                            val driverGeoModel = DriverGeoModel(snapshot.key, geoLocation)
                            val newDriverLocation = Location("")
                            newDriverLocation.latitude = geoLocation.latitude
                            newDriverLocation.longitude = geoLocation.longitude
                            val newDistance = location.distanceTo(newDriverLocation) / 1000 // in km

                            if (newDistance <= LIMIT_RANGE)
                                findDriverByKey(driverGeoModel)
                        }

                        override fun onChildRemoved(snapshot: DataSnapshot) {
                        }

                    })
                } catch (e: IOException) {
                    Snackbar.make(
                        requireView(),
                        "Location Permission Requires",
                        Snackbar.LENGTH_SHORT
                    ).show()

                }
            }

    }

    private fun addDriverMarker() {

        if (Common.driversFound.size > 0) {
            Observable.fromIterable(Common.driversFound)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { driverModel: DriverGeoModel? ->
                        findDriverByKey(driverModel)
                    }, { t ->
                        requireContext().snackbar(requireView(), t.message.toString())
                    }
                )
        } else {
            requireContext().snackbar(requireView(), "No Driver Found!")
        }

    }

    private fun findDriverByKey(driverModel: DriverGeoModel?) {

        FirebaseDatabase.getInstance().getReference(Common.DRIVER_INFO_REFERENCE)
            .child(driverModel!!.key!!)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onCancelled(error: DatabaseError) {
                    iFirebaseFailedListner.onFirebaseFailed(error.message)
                }

                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.hasChildren()) {
                        driverModel.driverInfoModel =
                            (snapshot.getValue(DriverInfoModel::class.java))
                        iFirebaseDriverInfoListner.onDriverInfoLoadSuccess(driverModel)
                    } else {
                        iFirebaseFailedListner.onFirebaseFailed("Key not found" + driverModel.key)

                    }
                }

            })
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        Dexter.withContext(requireContext())
            .withPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            .withListener(object : PermissionListener {
                @SuppressLint("MissingPermission")
                override fun onPermissionGranted(p0: PermissionGrantedResponse?) {
                    mMap.isMyLocationEnabled = true
                    mMap.uiSettings.isMyLocationButtonEnabled = true
                    mMap.setOnMyLocationButtonClickListener {

                        fusedLocationProviderClient.lastLocation.addOnFailureListener { e ->
                            Snackbar.make(requireView(), e.message!!, Snackbar.LENGTH_SHORT).show()

                        }.addOnSuccessListener { location ->
                            val userLatlng = LatLng(location.latitude, location.longitude)
                            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(userLatlng, 18f))
                        }
                        true
                    }
                    //Layout button
                    val locationButton =
                        (mapFragment.requireView().findViewById<View>("1".toInt()).parent as View)
                            .findViewById<View>("2".toInt())
                    val params = locationButton.layoutParams as RelativeLayout.LayoutParams
                    params.addRule(RelativeLayout.ALIGN_TOP, 0)
                    params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE)
                    params.bottomMargin = 250 // Move to see zoom control
                }

                override fun onPermissionRationaleShouldBeShown(
                    p0: PermissionRequest?,
                    p1: PermissionToken?
                ) {


                }

                override fun onPermissionDenied(p0: PermissionDeniedResponse?) {
                    Snackbar.make(
                        requireView(),
                        p0!!.permissionName + "needed for run app",
                        Snackbar.LENGTH_SHORT
                    ).show()
                }

            })
            .check()


        //Enable zoom
        mMap.uiSettings.isZoomControlsEnabled = true


        try {
            val success = googleMap.setMapStyle(
                MapStyleOptions.loadRawResourceStyle(
                    context,
                    R.raw.uber_maps_style
                )
            )
            if (!success)
                Log.e(TAG, "onMapReady: Style Parsing Error")
        } catch (e: Resources.NotFoundException) {
            Log.e(TAG, "onMapReady: " + e.message)
        }
    }

    override fun onDriverInfoLoadSuccess(driverGeoModel: DriverGeoModel?) {
        //requireContext().toast(driverGeoModel!!.driverInfoModel!!.firstName.toString())
        //if already have marker with key, doesn't est it again
        if (!Common.markerList.containsKey(driverGeoModel!!.key)) {
            Common.markerList.put(
                driverGeoModel.key!!,
                mMap.addMarker(
                    MarkerOptions()
                        .position(
                            LatLng(
                                driverGeoModel!!.geoLocation!!.latitude,
                                driverGeoModel.geoLocation!!.longitude
                            )
                        )
                        .flat(true)
                        .title(
                            Common.buildName(
                                driverGeoModel.driverInfoModel!!.firstName,
                                driverGeoModel.driverInfoModel!!.lastName
                            )
                        )
                        .snippet(driverGeoModel.driverInfoModel!!.phoneNumber)
                        .icon(BitmapDescriptorFactory.fromResource(R.drawable.car))
                )
            )


            if (!TextUtils.isEmpty(cityName)) {
                val driverlocation =
                    FirebaseDatabase.getInstance().getReference(Common.DRIVERS_LOCATION_REFERENCE)
                        .child(cityName)
                        .child(driverGeoModel!!.key!!)

                driverlocation.addValueEventListener(object : ValueEventListener {
                    override fun onCancelled(error: DatabaseError) {

                        requireContext().snackbar(requireView(), error.message)
                    }

                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (snapshot.hasChildren()) {
                            if (Common.markerList.get(driverGeoModel!!.key!!) !=null){
                                Common.markerList.get(driverGeoModel.key!!)!!.remove() // remove marker from map
                                Common.markerList.remove(driverGeoModel.key!!) //remove marker information

                                driverlocation.removeEventListener(this)
                            }
                        }

                    }

                })
            }
        }

    }
}