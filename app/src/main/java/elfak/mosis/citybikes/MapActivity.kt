package elfak.mosis.citybikes

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import android.os.Looper
import android.util.Log
import android.widget.EditText
import android.widget.Filter
import android.widget.ImageButton
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.google.android.gms.maps.model.CameraPosition
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
class MapActivity : AppCompatActivity(),OnMapReadyCallback {

    private lateinit var filter: ImageButton
    private lateinit var mapView: MapView
    private lateinit var googleMap: GoogleMap
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var storage: FirebaseStorage
    private lateinit var storageReference: StorageReference
    private lateinit var radius: EditText
    private lateinit var radiusBtn: ImageButton
    private lateinit var bikeList: ArrayList<Bike>

    private val MAPVIEW_BUNDLE_KEY = "MapViewBundleKey"
    private val CAMERA_POSITION_KEY = "CameraPositionKey"
    private var mapViewBundle: Bundle? = null
    private var savedCameraPosition: CameraPosition? = null

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)




        bikeList = intent.getSerializableExtra("bikeList") as? ArrayList<Bike> ?: ArrayList()
        Log.d("MapActivity", "Bike List: $bikeList")

        radius = findViewById(R.id.edtDistance)
        radiusBtn = findViewById(R.id.btnDistance)
        filter = findViewById(R.id.filterBtn)
        mapView = findViewById(R.id.mapView)
        mapView.onCreate(savedInstanceState?.getBundle(MAPVIEW_BUNDLE_KEY))
        mapView.getMapAsync(this)

        storage = FirebaseStorage.getInstance()
        storageReference = storage.reference.child("brands_models")
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createLocationRequest()
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val lastLocation = locationResult.lastLocation

                //
               // val desiredLocation = LatLng(43.318060301175485, 21.89265079284189)
               // googleMap?.addMarker(MarkerOptions().position(desiredLocation).title("Desired Location"))


                 val yourLocation: LatLng? = lastLocation?.let {
                    LatLng(it.latitude, it.longitude)
                }
                if (yourLocation != null) {

                    //val yourLocation = LatLng(lastLocation.latitude, lastLocation.longitude)
                    googleMap.addMarker(MarkerOptions().position(yourLocation).title("Your Location"))
                    if (savedCameraPosition == null && googleMap.cameraPosition == null) {
                        val cameraPosition = CameraPosition.Builder()
                            .target(yourLocation)
                            .zoom(15f)
                            .build()

                        googleMap.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
                    } }
            }
        }
        requestLocationUpdates()

        filter.setOnClickListener {
            showFilterDialog()
        }

        radiusBtn.setOnClickListener{
            filterByRadius()
        }

        // mapView.getMapAsync(this)

    }
    @SuppressLint("MissingPermission")
    private fun filterByRadius() {
        val radius = radius.text.toString().toFloatOrNull()

        if (radius != null) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    val userLocation = LatLng(location.latitude, location.longitude)
                    val filteredBikes = bikeList.filter { bike ->
                        val bikeLocation = LatLng(bike.latitude, bike.longitude)
                        val distance = BikesUtils.calculateDistance(userLocation, bikeLocation) / 1000.0f
                        distance <= radius
                    }

                    googleMap.clear()
                    addMarkersToMap(filteredBikes as ArrayList<Bike>)

                } else {
                    Toast.makeText(this, "Unable to retrieve user location.", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            Toast.makeText(this, "Invalid radius value.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun addMarkersToMap(bikeList: ArrayList<Bike>) {

        for (bike in bikeList) {
            val latitude = bike.latitude
            val longitude = bike.longitude
            val bikeLocation = LatLng(latitude, longitude)

            val markerOptions = MarkerOptions()
                .position(bikeLocation)
                .title(bike.brand + " " + bike.model + " (" + bike.year + ")")

            val snippet = "Rating: ${bike.rating} / 5 (${bike.numOfRatings})"
            markerOptions.snippet(snippet)

            val marker = googleMap.addMarker(markerOptions)
            marker?.tag = bike
            marker?.showInfoWindow()
        }

        googleMap.setOnMarkerClickListener { clickedMarker ->
            val bike = clickedMarker.tag as? Bike
            if (bike != null) {
                BikesUtils.showBikeDialog(this, bike)
                true
            } else {
                false
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView?.onSaveInstanceState(outState)
        outState.putParcelable(CAMERA_POSITION_KEY, savedCameraPosition)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        savedCameraPosition = savedInstanceState.getParcelable(CAMERA_POSITION_KEY)
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()



        savedCameraPosition?.let { position ->
            googleMap.moveCamera(CameraUpdateFactory.newCameraPosition(position))
            savedCameraPosition = null

            //
            if (::googleMap.isInitialized && savedCameraPosition != null) {
                googleMap.moveCamera(CameraUpdateFactory.newCameraPosition(savedCameraPosition!!))
                savedCameraPosition = null
            }
            //
        }
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()

        //
        if (::googleMap.isInitialized) {
            savedCameraPosition = googleMap.cameraPosition
        }
        //

        savedCameraPosition = googleMap.cameraPosition
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    private fun createLocationRequest() {
        locationRequest = LocationRequest.create().apply {
            interval = 5000
            fastestInterval = 1500
            priority = LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY
        }
    }

    private fun requestLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        } else {
            showLocationSettingsDialog()
        }
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun showLocationSettingsDialog() {
        AlertDialog.Builder(this)
            .setTitle("Location Permission Required")
            .setMessage("Please grant location permission to use this feature.")
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    LOCATION_PERMISSION_REQUEST_CODE
                )
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun showFilterDialog(){
        BikesUtils.showFilterDialog(this, bikeList) { filteredBikeList ->
            bikeList.clear()
            bikeList.addAll(filteredBikeList as ArrayList<Bike>)
            googleMap?.clear()
            addMarkersToMap(bikeList)
        }
    }

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            googleMap.isMyLocationEnabled = true
        }
        addMarkersToMap(bikeList)
    }
}