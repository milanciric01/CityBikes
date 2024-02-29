package elfak.mosis.citybikes

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.util.*

class AddBikeActivity : AppCompatActivity() {

    private lateinit var bikePicture: ImageView
    private lateinit var brandSpinner: Spinner
    private lateinit var modelSpinner: Spinner
    private lateinit var categorySpinner: Spinner
    private lateinit var styleTypeSpinner: Spinner
    private lateinit var radioTransmission: RadioGroup
    private lateinit var yearPicker: EditText
    private lateinit var saveBtn: Button

    private lateinit var category: String
    private lateinit var style: String
    private lateinit var brand: String
    private lateinit var model: String
    private lateinit var transmission: String

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var databaseReference: DatabaseReference
    private lateinit var storage: FirebaseStorage
    private lateinit var storageReference: StorageReference
    private lateinit var imageReference: StorageReference
    private lateinit var auth: FirebaseAuth

    private val takePictureLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val imageBitmap = result.data?.extras?.get("data") as Bitmap
                bikePicture.setImageBitmap(imageBitmap)
            }
        }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_bike)

        val bikeStyle = bikeStyle
        val categories = categories

        auth = FirebaseAuth.getInstance()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        storage = FirebaseStorage.getInstance()
        storageReference = storage.reference.child("brands_models")
        imageReference = storage.reference
        databaseReference = FirebaseDatabase.getInstance().reference

        brandSpinner = findViewById(R.id.brandSpinner)
        modelSpinner = findViewById(R.id.modelSpinner)
        categorySpinner = findViewById(R.id.categorySpineer)
        styleTypeSpinner = findViewById(R.id.styleSpinner)
        bikePicture = findViewById(R.id.bikeImage)
        yearPicker = findViewById(R.id.yearDP)
        saveBtn = findViewById(R.id.btnSave)

        bikePicture.setOnClickListener {
            val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            takePictureLauncher.launch(takePictureIntent)
        }
        retrieveBrands()

        brandSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                brand = parent?.getItemAtPosition(position).toString()
                retrieveModels(brand)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {

            }
        }
        modelSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                model = parent?.getItemAtPosition(position).toString()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {
            }
        }

        val categoryAdapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item, categories
        )
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        categorySpinner.adapter = categoryAdapter


        val styleTypeAdapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item, bikeStyle
        )
        styleTypeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        styleTypeSpinner.adapter = styleTypeAdapter

        categorySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                category = parent.getItemAtPosition(position) as String
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                Toast.makeText(applicationContext, "Please select bike category!", Toast.LENGTH_SHORT).show()
            }
        }
        styleTypeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                style = parent.getItemAtPosition(position) as String
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                Toast.makeText(applicationContext, "Please select bike category!", Toast.LENGTH_SHORT).show()
            }
        }

        radioTransmission = findViewById(R.id.radioTransmission)

        saveBtn.setOnClickListener {
            Log.d("AddBikeActivity", "Save button clicked")
            val year = yearPicker.text.toString()
            val maxYear = Calendar.getInstance().get(Calendar.YEAR)


           // Log.d("AddBikeActivity", "")
            val bikePicDrawable = bikePicture.drawable
            val bikeBitmap = if (bikePicDrawable is BitmapDrawable) {
                bikePicDrawable.bitmap
            } else {

                null
            }

            Log.d("AddBikeActivity", "")
            if (year.toIntOrNull() == null) {
                Toast.makeText(this, "Please add year of production!", Toast.LENGTH_SHORT).show()
            } else if (year.toInt() > maxYear || year.toInt() < 1900) {
                Toast.makeText(this, "Please enter a valid year of production!", Toast.LENGTH_SHORT).show()
            } else if (transmission.isNullOrBlank()) {
                Toast.makeText(this, "Please select the transmission type!", Toast.LENGTH_SHORT).show()
            } else
                addBike(brand, model, style, category, year, transmission, bikeBitmap)
        }
    }

    @SuppressLint("MissingPermission")
    private fun addBike(brand: String, model: String, style: String, category: String, year: String, transmission: String, bikeBitmap: Bitmap?) {
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                if (location != null) {
                    val user = auth.currentUser
                    val userId = user?.uid

                    if (userId != null) {
                        val bike = Bike(userId, brand, model, style, category, year, transmission, location.latitude, location.longitude,"", false, "", "0", "0")

                        val bikeImageRef = imageReference.child("bike_images").child("$userId-${System.currentTimeMillis()}.jpg")
                        bikeBitmap?.let { bitmap ->
                            val baos = ByteArrayOutputStream()
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos)
                            val bikeImageData = baos.toByteArray()

                            bikeImageRef.putBytes(bikeImageData)
                                .addOnSuccessListener {
                                    bikeImageRef.downloadUrl
                                        .addOnSuccessListener { uri ->
                                            val imageUrl = uri.toString()
                                            bike.bikeImage = imageUrl
                                            bike.dateAdded = System.currentTimeMillis().toString()

                                            val bikesRef = databaseReference.child("users").child(userId).child("bikes")
                                            bikesRef.push().setValue(bike)
                                                .addOnSuccessListener {
                                                    val userRef = databaseReference.child("users").child(userId)
                                                    userRef.child("score").get().addOnSuccessListener { dataSnapshot ->
                                                        val currentScore = dataSnapshot.value as? Long ?: 0
                                                        userRef.child("score").setValue(currentScore + 50)
                                                            .addOnSuccessListener {
                                                                Toast.makeText(this, "Bike added successfully", Toast.LENGTH_SHORT).show()
                                                                finish()
                                                            }
                                                            .addOnFailureListener {
                                                                Toast.makeText(this, "Failed to add bike", Toast.LENGTH_SHORT).show()
                                                            }
                                                    }.addOnFailureListener {
                                                        Toast.makeText(this, "Failed to retrieve user score", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                                .addOnFailureListener {
                                                    Toast.makeText(this, "Failed to add bike", Toast.LENGTH_SHORT).show()
                                                }
                                        }
                                        .addOnFailureListener {
                                            Toast.makeText(this, "Failed to get image URL", Toast.LENGTH_SHORT).show()
                                        }
                                }
                                .addOnFailureListener {
                                    Toast.makeText(this, "Failed to upload bike image", Toast.LENGTH_SHORT).show()
                                }
                        }
                    }
                } else {
                    Log.e("Location Error", "Location not available!")
                }
            }
            .addOnFailureListener { exception ->
                Log.e("Exception", exception.toString())
            }
    }

    fun checkButton(view: View) {
        if (view is RadioButton) {
            val checked = view.isChecked
            if (checked) {
                transmission = view.text.toString()
            }
        }
    }

    private fun retrieveBrands() {
        val brandsRef = storageReference.child("brands.txt")
        brandsRef.getBytes(Long.MAX_VALUE).addOnSuccessListener { bytes ->
            val brandsText = String(bytes, Charset.forName("UTF-8"))

            val brandList = brandsText.split("\n")
            val brandAdapter = ArrayAdapter(
                this@AddBikeActivity, android.R.layout.simple_spinner_item, brandList
            )
            brandAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            brandSpinner.adapter = brandAdapter
        }.addOnFailureListener { exception ->
            Log.e("Exception", exception.toString())
        }
    }

    private fun retrieveModels(brand: String) {
        val lowercaseBrand = brand.trim().lowercase()
        val brandModelsRef = storageReference.child("$lowercaseBrand.txt")
        brandModelsRef.getBytes(Long.MAX_VALUE).addOnSuccessListener { bytes ->
            val modelsText = String(bytes, Charset.forName("UTF-8"))

            val modelList = modelsText.split("\n")
            val modelAdapter = ArrayAdapter(
                this@AddBikeActivity, android.R.layout.simple_spinner_item, modelList
            )
            modelAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            modelSpinner.adapter = modelAdapter
        }.addOnFailureListener { exception ->
            Log.e("Exception", exception.toString())
        }
    }
}