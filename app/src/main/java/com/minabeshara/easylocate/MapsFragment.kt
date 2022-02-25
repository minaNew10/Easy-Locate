package com.minabeshara.easylocate

import android.Manifest
import android.content.DialogInterface
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.net.PlacesClient
import com.loopj.android.http.AsyncHttpClient
import com.loopj.android.http.JsonHttpResponseHandler
import cz.msebera.android.httpclient.Header
import org.json.JSONObject


class MapsFragment : Fragment(), OnMapReadyCallback {
    private var map: GoogleMap? = null

    private var cameraPosition: CameraPosition? = null

    private val defaultLocation = LatLng(-33.8523341, 151.2106085)

    private var locationPermissionGranted = false
    private var lastKnownLocation: Location? = null

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
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment?
        mapFragment?.getMapAsync(this)
        Places.initialize(context, getString(R.string.google_maps_key))
        placesClient = Places.createClient(context)

        // Construct a FusedLocationProviderClient.
        context?.let {
            fusedLocationProviderClient =
                LocationServices.getFusedLocationProviderClient(
                    it
                )
        }
        val restaurants = view.findViewById<Button>(R.id.restaurants_buttons)
        restaurants.setOnClickListener {
            findNearByRestaurants()
        }
        populateDummyData()
    }

    private fun populateDummyData() {
        likelyPlaceNames.add("Grand Kadri Hotel By Cristal Lebanon")
        likelyPlaceLatLngs.add(LatLng(33.85148430277257,35.895525763213946))

        likelyPlaceNames.add("Germanos - Pastry")
        likelyPlaceLatLngs.add(LatLng(33.85217073479985,35.89477838111461))

        likelyPlaceNames.add("Malak el Tawook")
        likelyPlaceLatLngs.add(LatLng(33.85334017189446,35.89438946093824))

        likelyPlaceNames.add("Z Burger House")
        likelyPlaceLatLngs.add(LatLng(33.85454300475094,35.894561122304474))

        likelyPlaceNames.add("College Oriental")
        likelyPlaceLatLngs.add(LatLng(33.85129821373707,35.89446263654391))

        likelyPlaceNames.add("VERO MODA")
        likelyPlaceLatLngs.add(LatLng(33.85048738635312,35.89664059012788))

    }

    private fun findNearByRestaurants() {
        val client = AsyncHttpClient()
        val url =
            "https://maps.googleapis.com/maps/api/place/nearbysearch/json?location=${lastKnownLocation?.latitude},${lastKnownLocation?.longitude}&radius=10000&type=restaurant&keyword=cousins&key=${
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
                Toast.makeText(
                    activity, "Request Succeeded",
                    Toast.LENGTH_SHORT
                ).show()
                try {


                } catch (e: Exception) {
                    Log.e("Bitcoin", e.toString())
                }
            }

            override fun onFailure(
                statusCode: Int, headers: Array<Header?>?, e: Throwable,
                response: JSONObject
            ) {

                // called when response HTTP status is "4XX" (eg. 401, 403, 404)
                Log.d("Bitcoin", "Request fail! Status code: $statusCode")
                Log.d("Bitcoin", "Fail response: $response")
                Log.e("ERROR", e.toString())
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

            // below line is use to add marker to each location of our array list.
            map?.addMarker(
                MarkerOptions().position(restaurantsLatLngs.get(i)).title(
                    restaurantsNames[i]
                )
            )

            // below lin is use to zoom our camera on map.
            map?.animateCamera(CameraUpdateFactory.zoomTo(18.0f))

            // below line is use to move our camera to the specific location.
            map?.moveCamera(CameraUpdateFactory.newLatLng(restaurantsLatLngs.get(i)))
        }
    }

    private fun openPlacesDialog() {
        val listener =
            DialogInterface.OnClickListener { dialog, which -> // The "which" argument contains the position of the selected item.
                val markerLatLng = likelyPlaceLatLngs[which] ?: return@OnClickListener

                // Add a marker for the selected place, with an info window
                // showing information about that place.
                map?.addMarker(
                    MarkerOptions()
                        .title(likelyPlaceNames[which])
                        .position(markerLatLng)
                )

                // Position the map's camera at the location of the marker.
                map?.moveCamera(
                    CameraUpdateFactory.newLatLngZoom(
                        markerLatLng,
                        DEFAULT_ZOOM.toFloat()
                    )
                )
            }

        // Display the dialog.
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

                // If request is cancelled, the result arrays are empty.
                if (grantResults.isNotEmpty() &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED
                ) {
                    locationPermissionGranted = true
                }
            }
            else -> super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
        updateLocationUI()
    }

    private fun getLocationPermission() {
        /*
         * Request location permission, so that we can get the location of the
         * device. The result of the permission request is handled by a callback,
         * onRequestPermissionsResult.
         */
        context?.applicationContext?.let {
            if (ContextCompat.checkSelfPermission(
                    it,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
                == PackageManager.PERMISSION_GRANTED
            ) {
                locationPermissionGranted = true
            } else {
                activity?.let { activity ->
                    ActivityCompat.requestPermissions(
                        activity, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                        PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION
                    )
                }
            }
        }

    }

    override fun onMapReady(googleMap: GoogleMap) {
        this.map = googleMap

        this.map?.setInfoWindowAdapter(object : GoogleMap.InfoWindowAdapter {
            // Return null here, so that getInfoContents() is called next.
            override fun getInfoWindow(arg0: Marker): View? {
                return null
            }

            override fun getInfoContents(marker: Marker): View {
                // Inflate the layouts for the info window, title and snippet.
                val infoWindow = layoutInflater.inflate(R.layout.custom_info_content,
                      null)
                val title = infoWindow.findViewById<TextView>(R.id.title)
                title.text = marker.title
                val snippet = infoWindow.findViewById<TextView>(R.id.snippet)
                snippet.text = marker.snippet

                return infoWindow
            }
        })
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
                lastKnownLocation = null
                getLocationPermission()
            }
        } catch (e: SecurityException) {
            Log.e("Exception: %s", e.message, e)
        }
    }

    private fun getDeviceLocation() {
        Log.i(TAG, "getDeviceLocation: ")
        try {
            if (locationPermissionGranted) {
                Log.i(TAG, "getDeviceLocation: location permission granted")

                val locationResult = fusedLocationProviderClient.lastLocation
                Log.i(TAG, "getDeviceLocation: location result $locationResult ")
                activity?.let {
                    locationResult.addOnCompleteListener(it) { task ->
                        if (task.isSuccessful) {
                            Log.i(TAG, "getDeviceLocation: task successful")
                            // Set the map's camera position to the current location of the device.
                            lastKnownLocation = task.result
                            if (lastKnownLocation != null) {
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
                            Log.i(TAG, "getDeviceLocation: location is null")
                            Log.d(TAG, "Current location is null. Using defaults.")
                            Log.e(TAG, "Exception: %s", task.exception)
                            map?.moveCamera(
                                CameraUpdateFactory
                                    .newLatLngZoom(defaultLocation, DEFAULT_ZOOM.toFloat())
                            )
                            map?.uiSettings?.isMyLocationButtonEnabled = false
                        }
                    }
                } ?: Log.i(TAG, "getDeviceLocation activity null")
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

        // Keys for storing activity state.
        private const val KEY_CAMERA_POSITION = "camera_position"

        private const val KEY_LOCATION = "location"

        // Used for selecting the current place.
        private const val M_MAX_ENTRIES = 5
    }
}