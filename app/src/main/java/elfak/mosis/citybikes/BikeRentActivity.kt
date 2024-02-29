package elfak.mosis.citybikes

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.widget.Button
import android.widget.ImageView
import android.widget.RatingBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseError
class BikeRentActivity : AppCompatActivity(),OnMapReadyCallback {

    private lateinit var brandModelYear: TextView
    private lateinit var tvRating: TextView
    private lateinit var bikePic: ImageView
    private lateinit var openKey: TextView
    private lateinit var duration: TextView
    private lateinit var distanceTV: TextView
    private lateinit var stopBtn: Button
    private lateinit var mapView: MapView
    private lateinit var googleMap: GoogleMap
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var bike: Bike

    private val MAPVIEW_BUNDLE_KEY = "MapViewBundleKey"
    private val CAMERA_POSITION_KEY = "CameraPositionKey"
    private var savedCameraPosition: CameraPosition? = null
    private var startTime: Long = 0L
    private var previousLocation: Location? = null
    private var distanceTraveled: Float = 0F
    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bike_rent)

        mapView = findViewById(R.id.mapView2)
        mapView.onCreate(savedInstanceState?.getBundle(MAPVIEW_BUNDLE_KEY))
        mapView.getMapAsync(this@BikeRentActivity)

        brandModelYear = findViewById(R.id.tvBMY)
        tvRating = findViewById(R.id.tvRating)
        bikePic = findViewById(R.id.bikePicRent)
        openKey = findViewById(R.id.tvOpenKey)
        duration = findViewById(R.id.tvDuration)
        distanceTV = findViewById(R.id.tvDistance)
        stopBtn = findViewById(R.id.StopRentBtn)


        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createLocationRequest()
        locationCallback = object : LocationCallback() {
            @SuppressLint("SetTextI18n")
            override fun onLocationResult(locationResult: LocationResult) {

                val lastLocation = locationResult.lastLocation
                val yourLocation = LatLng(lastLocation!!.latitude, lastLocation.longitude)
                //googleMap.addMarker(MarkerOptions().position(yourLocation).title("Your Location"))
                if (savedCameraPosition == null && googleMap.cameraPosition == null) {
                    val cameraPosition = CameraPosition.Builder()
                        .target(yourLocation)
                        .zoom(15f)
                        .build()

                    googleMap.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
                }

                if (previousLocation != null) {
                    if (isUserNearBikeMarker(yourLocation)) {
                        val distance = lastLocation!!.distanceTo(previousLocation!!)
                        distanceTraveled += distance
                        distanceTV.text = String.format("%.2f km", distanceTraveled / 1000)
                    }
                    else
                    {
                        distanceTV.text = "Not Started"
                    }
                }
                previousLocation = lastLocation
            }
        }
        requestLocationUpdates()

        bike = (intent.getSerializableExtra("bike") as? Bike)!!

        openKey.text = "Open key: " + bike.openKey
        brandModelYear.text = bike.brand + "" + bike.model + " (" + bike.year + ")"
        tvRating.text = String.format("(%d) %.1f/5", bike.numOfRatings.toInt(), bike.rating.toFloat())

        Glide.with(this)
            .load(bike.bikeImage)
            .into(bikePic)

        stopBtn.setOnClickListener{
            stopRenting(bike)
            showRateDialog(bike)
        }

        startRenting()
    }


    private fun startRenting() {

        BikesUtils.changeBikeStatus(bike, true)
        startTime = System.currentTimeMillis()
        startTimer()
        startDistanceTracking()
    }

    private fun stopRenting(bike: Bike) {

        stopTimer()
        stopDistanceTracking()

        updatePoints()
        BikesUtils.changeBikeStatus(bike, false)
        showRateDialog(bike)
    }

    private fun startTimer() {
        val runnable = object : Runnable {
            override fun run() {
                val currentTime = System.currentTimeMillis()
                val elapsedTime = currentTime - startTime
                val seconds = (elapsedTime / 1000) % 60
                val minutes = (elapsedTime / (1000 * 60)) % 60
                val hours = (elapsedTime / (1000 * 60 * 60))

                duration.text = String.format("%02d:%02d:%02d", hours, minutes, seconds)
                duration.postDelayed(this, 1000)
            }
        }

        duration.post(runnable)
    }

    private fun stopTimer() {
        duration.removeCallbacks(null)
    }

    @SuppressLint("MissingPermission")
    private fun startDistanceTracking() {
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
    }

    private fun stopDistanceTracking() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun updatePoints() {

        val auth = FirebaseAuth.getInstance()

        val endTime = System.currentTimeMillis()
        val elapsedTimeInSeconds = (endTime - startTime) / 1000
        val elapsedTimeInHours = elapsedTimeInSeconds / 3600F

        val pointsPerKilometer = 1
        val pointsPerHour = 5

        val pointsFromDistance = (distanceTraveled * pointsPerKilometer).toInt()
        val pointsFromTime = (elapsedTimeInHours * pointsPerHour).toInt()

        val userPoints = pointsFromDistance + pointsFromTime

        val currentUser = auth.currentUser
        val userId = currentUser?.uid

        if (userId != null) {
            val database = FirebaseDatabase.getInstance()
            val usersRef = database.reference.child("users").child(userId)

            usersRef.child("score").addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val currentScore = snapshot.getValue(Int::class.java)
                    if (currentScore != null) {
                        val updatedScore = currentScore + userPoints

                        usersRef.child("score").setValue(updatedScore)
                            .addOnSuccessListener {

                            }
                            .addOnFailureListener { exception ->
                                Log.e("Exception:", "Failed to update user score: ${exception.message}")
                            }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("DB ERROR", "Failed to read user score: ${error.message}")
                }
            })
        }
    }

    @SuppressLint("MissingPermission")
    private fun showRateDialog(bike: Bike) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.rate_dialog, null)
        val ratingBar = dialogView.findViewById<RatingBar>(R.id.ratingBar)
        val btnRate = dialogView.findViewById<Button>(R.id.btnRate)

        val dialogBuilder = AlertDialog.Builder(this)
            .setView(dialogView)
            .setTitle("Please rate this bike")

        val alertDialog = dialogBuilder.create()

        btnRate.setOnClickListener {
            val rating = ratingBar.rating

            fusedLocationClient.lastLocation
                .addOnSuccessListener { location ->
                    if (location != null) {
                        BikesUtils.rateAndUpdateBike(bike, rating, location)
                    } else {
                        Log.e("Location Error", "Location not available!")
                    }
                }
                .addOnFailureListener { exception ->
                    Log.e("Exception", exception.toString())
                }

            alertDialog.dismiss()
            val intentMain = Intent(this, MainActivity::class.java)
            startActivity(intentMain)
            finish()
        }

        alertDialog.show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
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
        }
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()

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
            interval = 10000
            fastestInterval = 5000
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
        addBikeMarker()
    }

    private fun addBikeMarker() {

        val bikeLocation = LatLng(bike.latitude, bike.longitude)
        val markerOptions = MarkerOptions()
            .position(bikeLocation)
            .title("Starting point")

        val marker = googleMap.addMarker(markerOptions)
        marker?.tag = "bike"
        marker?.showInfoWindow()
    }

    private fun isUserNearBikeMarker(userLocation: LatLng): Boolean {
        val bikeLocation = LatLng(bike.latitude, bike.longitude)
        val distanceThreshold = 100.0

        val distance = BikesUtils.calculateDistance(userLocation, bikeLocation)

        return distance <= distanceThreshold
    }
}