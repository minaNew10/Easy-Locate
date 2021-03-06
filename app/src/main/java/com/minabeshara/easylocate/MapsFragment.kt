package com.minabeshara.easylocate

import android.Manifest
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.loopj.android.http.AsyncHttpClient
import com.loopj.android.http.JsonHttpResponseHandler
import cz.msebera.android.httpclient.Header
import org.json.JSONObject
import java.io.IOException


class MapsFragment : Fragment(), OnMapReadyCallback {
    private var map: GoogleMap? = null

    private var cameraPosition: CameraPosition? = null

    private val defaultLocation = LatLng(-33.8523341, 151.2106085)

    private var locationPermissionGranted = false
    private var currentLocation: Location? = null

    private lateinit var placesClient: PlacesClient
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private var restaurantsLatLngs: ArrayList<LatLng> = arrayListOf()
    private var restaurantsNames: ArrayList<String?> = arrayListOf()

    private var likelyPlaceNames: ArrayList<String?> = arrayListOf()
    private var likelyPlaceLatLngs: ArrayList<LatLng?> = arrayListOf()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        return inflater.inflate(R.layout.fragment_maps, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        populateDummyData()
        initVars()
        initViews(view)
    }

    private fun initVars() {
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment?
        mapFragment?.getMapAsync(this)
        Places.initialize(context, getString(R.string.google_maps_key))
        placesClient = Places.createClient(context)

        context?.let {
            fusedLocationProviderClient =
                LocationServices.getFusedLocationProviderClient(
                    it
                )
        }
    }

    private fun initViews(view: View) {
        val restaurants = view.findViewById<Button>(R.id.btn_restaurants)
        val places = view.findViewById<Button>(R.id.btn_places)
        val fab = view.findViewById<FloatingActionButton>(R.id.floatingActionButton)
        restaurants.setOnClickListener {
            findNearByRestaurants()
        }
        places.setOnClickListener {
            openPlacesDialog()
        }
        fab.setOnClickListener {
            shareLocation()
        }
        val searchView = view.findViewById<SearchView>(R.id.idSearchView);
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                val location = searchView.query.toString()

                var addressList: List<Address>? = null

                if (location != null || location == "") {
                    val geocoder = Geocoder(context)
                    try {
                        addressList = geocoder.getFromLocationName(location, 1)
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                    var address: Address? = null
                    addressList?.let { list ->
                        if (list?.size > 0)
                            address = list[0]

                    }

                    var latLng: LatLng? = null
                    address?.let {
                        latLng = LatLng(it.latitude, it.longitude)
                    }


                    latLng?.let {
                        map?.addMarker(MarkerOptions().position(it).title(location))
                        map?.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 10f))
                    } ?: Toast.makeText(activity, "Not found", Toast.LENGTH_LONG).show()

                }
                return false
            }

            override fun onQueryTextChange(newText: String): Boolean {
                return false
            }
        })
    }

    private fun populateDummyData() {
        likelyPlaceNames.add("Grand Kadri Hotel By Cristal Lebanon")
        likelyPlaceLatLngs.add(LatLng(33.85148430277257, 35.895525763213946))

        likelyPlaceNames.add("Germanos - Pastry")
        likelyPlaceLatLngs.add(LatLng(33.85217073479985, 35.89477838111461))

        likelyPlaceNames.add("Malak el Tawook")
        likelyPlaceLatLngs.add(LatLng(33.85334017189446, 35.89438946093824))

        likelyPlaceNames.add("Z Burger House")
        likelyPlaceLatLngs.add(LatLng(33.85454300475094, 35.894561122304474))

        likelyPlaceNames.add("College Oriental")
        likelyPlaceLatLngs.add(LatLng(33.85129821373707, 35.89446263654391))

        likelyPlaceNames.add("VERO MODA")
        likelyPlaceLatLngs.add(LatLng(33.85048738635312, 35.89664059012788))

    }

    private fun findNearByRestaurants() {
        val client = AsyncHttpClient()
        val url =
            "https://maps.googleapis.com/maps/api/place/nearbysearch/json?location=${currentLocation?.latitude},${currentLocation?.longitude}&radius=10000&type=restaurant&keyword=cousins&key=${
                getString(
                    R.string.google_maps_key
                )
            }"
        client[url, object : JsonHttpResponseHandler() {
            override fun onSuccess(
                statusCode: Int, headers: Array<Header?>?,
                response: JSONObject
            ) {

                extractRestaurantNamesAndLocation(response)
                Log.d("MapsFragment", "JSON: $response")

            }

            override fun onFailure(
                statusCode: Int, headers: Array<Header?>?, e: Throwable,
                response: JSONObject
            ) {
                Toast.makeText(
                    activity, "Request Failed",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }]
    }

    private fun extractRestaurantNamesAndLocation(response: JSONObject) {
        val restaurantsArray = response.getJSONArray("results")
        for (i in 0 until restaurantsArray.length()) {
            val restJSONObject = restaurantsArray.getJSONObject(i)
            val name = restJSONObject.getString("name")
            restaurantsNames.add(name)
            val geometry = restJSONObject.getJSONObject("geometry")
            val location = geometry.getJSONObject("location")
            val latLng = LatLng(location.getDouble("lat"), location.getDouble("lng"))
            restaurantsLatLngs.add(latLng)
            Log.i(
                TAG,
                "extractRestaurantNamesAndLocation: $name lat ${latLng.latitude} lng ${latLng.longitude}"
            )
        }
        viewRestaurantsOnMap()
    }

    private fun viewRestaurantsOnMap() {
        for (i in 0 until restaurantsLatLngs.size) {

            map?.addMarker(
                MarkerOptions().position(restaurantsLatLngs.get(i)).title(
                    restaurantsNames[i]
                )
            )

            map?.animateCamera(CameraUpdateFactory.zoomTo(18.0f))

            map?.moveCamera(CameraUpdateFactory.newLatLng(restaurantsLatLngs.get(i)))
        }
    }

    private fun requestDirection(destination: LatLng) {
        val client = AsyncHttpClient()
        val url =
            "https://maps.googleapis.com/maps/api/directions/json?origin=${currentLocation?.latitude},${currentLocation?.longitude}&destination=${destination.latitude},${destination.longitude}&key=${
                getString(
                    R.string.google_maps_key
                )
            }"
        client[url, object : JsonHttpResponseHandler() {
            override fun onSuccess(
                statusCode: Int, headers: Array<Header?>?,
                response: JSONObject
            ) {
                Log.d(TAG, "onSuccess: $response")
                parseDirectionResponse(response)
            }

            override fun onFailure(
                statusCode: Int, headers: Array<Header?>?, e: Throwable,
                response: JSONObject
            ) {
                Toast.makeText(
                    activity, "Request Failed",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }]
    }

    private fun parseDirectionResponse(response: JSONObject) {
        val directionHelper = DirectionHelper()
        var routes = directionHelper.parse(response)

        var points: ArrayList<LatLng?>
        var lineOptions: PolylineOptions? = null
        for (i in routes.indices) {
            points = ArrayList()
            lineOptions = PolylineOptions()

            val path = routes[i]

            for (j in path.indices) {
                val point = path[j]
                val lat = point["lat"]!!.toDouble()
                val lng = point["lng"]!!.toDouble()
                val position = LatLng(lat, lng)
                points.add(position)
            }

            lineOptions.addAll(points)
            lineOptions.width(10f)
            lineOptions.color(Color.BLUE)
            Log.e(TAG, "PolylineOptions Decoded")
        }
        map?.addPolyline(lineOptions)
    }

    private fun shareLocation() {
        currentLocation?.let {
            val latitude: Double = it.latitude
            val longitude: Double = it.longitude

            val uri = "http://maps.google.com/maps?location=$latitude,$longitude"

            val sharingIntent = Intent(Intent.ACTION_SEND)
            sharingIntent.type = "text/plain"
            val shareSub = getString(R.string.share_subject)
            sharingIntent.putExtra(Intent.EXTRA_SUBJECT, shareSub)
            sharingIntent.putExtra(Intent.EXTRA_TEXT, uri)
            startActivity(Intent.createChooser(sharingIntent, "Share via"))
        }
    }

    private fun openPlacesDialog() {
        val listener =
            DialogInterface.OnClickListener { dialog, which -> // The "which" argument contains the position of the selected item.
                val markerLatLng = likelyPlaceLatLngs[which] ?: return@OnClickListener

                map?.addMarker(
                    MarkerOptions()
                        .title(likelyPlaceNames[which])
                        .position(markerLatLng)
                )

                map?.moveCamera(
                    CameraUpdateFactory.newLatLngZoom(
                        markerLatLng,
                        DEFAULT_ZOOM.toFloat()
                    )
                )
            }

        context?.let {
            AlertDialog.Builder(it)
                .setTitle("Pick place")
                .setItems(likelyPlaceNames.toTypedArray(), listener)
                .show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        locationPermissionGranted = false
        when (requestCode) {
            PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION -> {
                if (grantResults.isNotEmpty() &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED
                ) {
                    locationPermissionGranted = true
                    Log.i(TAG, "onRequestPermissionsResult: location permission granted ")
                    getDeviceLocation()
                }
            }
            else -> super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
        updateLocationUI()
    }

    private fun getLocationPermission() {
        context?.applicationContext?.let {
            if (ContextCompat.checkSelfPermission(
                    it,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
                == PackageManager.PERMISSION_GRANTED
            ) {
                locationPermissionGranted = true
            } else {
                    requestPermissions(
                         arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                        PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION
                    )
            }
        }

    }

    override fun onMapReady(googleMap: GoogleMap) {
        this.map = googleMap

        this.map?.setInfoWindowAdapter(object : GoogleMap.InfoWindowAdapter {
            override fun getInfoWindow(arg0: Marker): View? {
                return null
            }

            override fun getInfoContents(marker: Marker): View {
                val infoWindow = layoutInflater.inflate(
                    R.layout.custom_info_content,
                    null
                )
                val title = infoWindow.findViewById<TextView>(R.id.title)
                title.text = marker.title
                val snippet = infoWindow.findViewById<TextView>(R.id.snippet)
                snippet.text = marker.snippet

                return infoWindow
            }
        })
        this.map?.let { map ->
            map.setOnInfoWindowClickListener {
                requestDirection(LatLng(it.position.latitude, it.position.longitude))
            }
        }

        getLocationPermission()
        updateLocationUI()
        getDeviceLocation()


    }

    private fun updateLocationUI() {
        if (map == null) {
            return
        }
        try {
            if (locationPermissionGranted) {
                map?.isMyLocationEnabled = true
                map?.uiSettings?.isMyLocationButtonEnabled = true
            } else {
                map?.isMyLocationEnabled = false
                map?.uiSettings?.isMyLocationButtonEnabled = false
                currentLocation = null
                getLocationPermission()
            }
        } catch (e: SecurityException) {
            Log.e("Exception: %s", e.message, e)
        }
    }

    private fun getDeviceLocation() {
        try {
            if (locationPermissionGranted) {
                Log.i(TAG, "getDeviceLocation: location permission granted")

                val locationResult = fusedLocationProviderClient.lastLocation
                Log.i(TAG, "getDeviceLocation: location result $locationResult ")
                activity?.let {
                    locationResult.addOnCompleteListener(it) { task ->
                        if (task.isSuccessful) {
                            Log.i(TAG, "getDeviceLocation: task successful")
                            currentLocation = task.result
                            if (currentLocation != null) {
                                Log.i(TAG, "getDeviceLocation: Location is not null")
                                val lastKnownLocationLatLang =
                                    LatLng(task.result.latitude, task.result.longitude)
                                map?.isMyLocationEnabled
                                Log.i(TAG, "getDeviceLocation: ${map?.isMyLocationEnabled}")
                                map?.addMarker(
                                    MarkerOptions().position(lastKnownLocationLatLang)
                                        .snippet("snippet data")
                                        .title("My Location")

                                )

                                map?.moveCamera(
                                    CameraUpdateFactory.newLatLngZoom(
                                        lastKnownLocationLatLang, DEFAULT_ZOOM.toFloat()
                                    )
                                )

                            }
                        } else {
                            Log.e(TAG, "Exception: %s", task.exception)
                            map?.moveCamera(
                                CameraUpdateFactory
                                    .newLatLngZoom(defaultLocation, DEFAULT_ZOOM.toFloat())
                            )
                            map?.uiSettings?.isMyLocationButtonEnabled = false
                        }
                    }
                }
            } else {
                Log.i(TAG, "getDeviceLocation: not granted")
            }
        } catch (e: SecurityException) {
            Log.e("Exception: %s", e.message, e)
        }
    }

    companion object {
        private val TAG = MapsFragment::class.java.simpleName
        private const val DEFAULT_ZOOM = 15
        private const val PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 1
    }
}