package com.communisolve.uberriderapp.ui.home

import android.Manifest
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.content.res.Resources
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.communisolve.uberriderapp.Common.Common
import com.communisolve.uberriderapp.Model.AnimationModel
import com.communisolve.uberriderapp.Model.DriverGeoModel
import com.communisolve.uberriderapp.Model.DriverInfoModel
import com.communisolve.uberriderapp.Model.GeoQueryModel
import com.communisolve.uberriderapp.R
import com.communisolve.uberriderapp.Utils.snackbar
import com.communisolve.uberriderapp.Utils.toast
import com.communisolve.uberriderapp.callback.IFirebaseDriverInfoListner
import com.communisolve.uberriderapp.callback.IFirebaseFailedListner
import com.communisolve.uberriderapp.remote.IGoogleAPI
import com.communisolve.uberriderapp.remote.RetrofitClient
import com.firebase.geofire.GeoFire
import com.firebase.geofire.GeoLocation
import com.firebase.geofire.GeoQueryEventListener
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
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
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import org.json.JSONObject
import java.io.IOException
import java.lang.StringBuilder
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.ln

class HomeFragment : Fragment(), OnMapReadyCallback, IFirebaseDriverInfoListner,
    IFirebaseFailedListner {

    companion object {
        private const val TAG = "HomeFragment"
        private const val MAPVIEW_BUNDLE_KEY = "MapViewBundleKey"
    }

    private var end: LatLng?=null
    private var start: LatLng?=null
    private lateinit var homeViewModel: HomeViewModel
    private lateinit var mMap: GoogleMap
    lateinit var mapFragment: SupportMapFragment

    //Location
    lateinit var locationRequest: LocationRequest
    lateinit var locationCallback: LocationCallback
    lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    //Load Driver
    var distance = 1.0
    var LIMIT_RANGE = 10.0
    var previousLocation: Location? = null
    var currentLocation: Location? = null

    var firstTime = true

    //listner
    lateinit var iFirebaseDriverInfoListner: IFirebaseDriverInfoListner
    lateinit var iFirebaseFailedListner: IFirebaseFailedListner

    var cityName = ""

    val compositeDisposable = CompositeDisposable()
    lateinit var iGoogleAPI: IGoogleAPI

    //Moving Marker
    var polylineList: ArrayList<LatLng?>? = null
    var handler: Handler? = null
    var index: Int = 0
    var next: Int = 0
    var v: Float = 0.0f
    var lat: Double = 0.0
    var lng: Double = 0.0


    override fun onStop() {
        compositeDisposable.clear()
        super.onStop()
    }

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

        iGoogleAPI = RetrofitClient.instance!!.create(IGoogleAPI::class.java)
        iFirebaseDriverInfoListner = this
        iFirebaseFailedListner = this
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
//            requireContext().snackbar(requireView(), "Permission Denied")
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
                var addressList: List<Address> = ArrayList()

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
        }


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
                    if (!snapshot.hasChildren()) {
                        if (Common.markerList.get(driverGeoModel!!.key!!) != null) {
                            Common.markerList.get(driverGeoModel.key!!)!!
                                .remove() // remove marker from map
                            Common.markerList.remove(driverGeoModel.key!!) //remove marker information
                            Common.driversSubscribe.remove(driverGeoModel.key!!)
                            driverlocation.removeEventListener(this)
                        }
                    } else {
                        if (Common.markerList.get(driverGeoModel!!.key!!) != null) {
                            val geoQueryModel = snapshot.getValue(GeoQueryModel::class.java)
                            val animationModel = AnimationModel(false, geoQueryModel!!)

                            if (Common.driversSubscribe.get(driverGeoModel.key) != null) {
                                val marker = Common.markerList.get(driverGeoModel!!.key!!)
                                val oldPosition = Common.driversSubscribe.get(driverGeoModel.key)

                                val from = StringBuilder()
                                    .append(oldPosition!!.geoQueryModel.l!!.get(0))
                                    .append(",")
                                    .append(oldPosition.geoQueryModel.l!!.get(1)).toString()

                                val to = StringBuilder()
                                    .append(animationModel!!.geoQueryModel.l!!.get(0))
                                    .append(",")
                                    .append(animationModel.geoQueryModel.l!!.get(1)).toString()

                                moverMarkerAnimation(
                                    driverGeoModel.key,
                                    animationModel,
                                    marker,
                                    from,
                                    to
                                )
                            } else {
                                Common.driversSubscribe.put(
                                    driverGeoModel!!.key!!,
                                    animationModel
                                ) // First location init

                            }

                        }
                    }

                }

            })
        }


    }

    private fun moverMarkerAnimation(
        key: String?,
        newData: AnimationModel,
        marker: Marker?,
        from: String,
        to: String
    ) {


        if (!newData.isRun) {
            //RestAPI
            compositeDisposable.add(
                iGoogleAPI.getDirection(
                    "driving",
                    "less_driving", from, to, getString(R.string.google_api_key)
                )
                !!.subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({ returnResult ->
                        Log.d("API_RETURN", returnResult)

                        try {
                            val jsonObject = JSONObject(returnResult)
                            val jsonArray = jsonObject.getJSONArray("routes")

                            for (i in 0 until jsonArray.length()) {
                                val route = jsonArray.getJSONObject(i)
                                val poly = route.getJSONObject("overview_polyline")
                                val polyline = poly.getString("points")
                                polylineList = Common.decodePoly(polyline)
                            }

                            //Moving
                            handler = Handler()
                            index = -1
                            next =1
                            val runnable = object:Runnable{
                                override fun run() {

                                    if (polylineList!!.size > 1){
                                        if (index < polylineList!!.size -2){
                                            index++
                                            next = index+1
                                            start = polylineList!![index]
                                            end = polylineList!![next]
                                        }
                                    }
                                    val valueAnimator = ValueAnimator.ofInt(0,1)
                                    valueAnimator.duration = 3000
                                    valueAnimator.interpolator = LinearInterpolator()

                                    valueAnimator.addUpdateListener { value->
                                        v = value.animatedFraction
                                        lat = v*end!!.latitude + (1-v) * start!!.latitude
                                        lng = v*end!!.longitude + (1-v)*start!!.longitude
                                        val newPos = LatLng(lat,lng)

                                        marker!!.position = newPos
                                        marker.setAnchor(0.5f,0.5f)
                                        marker.rotation  = Common.getBearing(start!!,newPos)
                                    }

                                    valueAnimator.start()

                                    if (index < polylineList!!.size -2)
                                        handler!!.postDelayed(this,1500)
                                    else if(index < polylineList!!.size -1){
                                        newData.isRun = false
                                        Common.driversSubscribe.put(key!!,newData)
                                    }
                                }

                            }

                            handler!!.postDelayed(runnable,1500)

                        } catch (e: java.lang.Exception) {
                            requireContext().snackbar(requireView(), e.message!!)
                        }
                    }, {

                    })
            )
        }
    }

    override fun onFirebaseFailed(message: String) {

        requireContext().toast(message)
    }
}