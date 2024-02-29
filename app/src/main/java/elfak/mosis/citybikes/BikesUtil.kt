package elfak.mosis.citybikes

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.location.Location
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.bumptech.glide.Glide
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.GenericTypeIndicator
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import java.nio.charset.Charset

object BikesUtils {

    private val databaseReference: DatabaseReference = FirebaseDatabase.getInstance().reference
    private val storage: FirebaseStorage = FirebaseStorage.getInstance()
    private val storageReference: StorageReference = storage.reference.child("brands_models")

    fun changeBikeStatus(bike: Bike, rented: Boolean) {
        val userId = bike.userId
        val bikeRef = databaseReference.child("users").child(userId).child("bikes")

        bikeRef.orderByChild("brand").equalTo(bike.brand)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(dataSnapshot: DataSnapshot) {
                    for (childSnapshot in dataSnapshot.children) {
                        val bikeSnapshot = childSnapshot.getValue(Bike::class.java)
                        if (bikeSnapshot != null && bikeSnapshot.model == bike.model) {
                            val bikeId = childSnapshot.key

                            bikeRef.child(bikeId!!).child("rented").setValue(rented)
                                .addOnSuccessListener {
                                }
                                .addOnFailureListener { exception ->
                                    Log.e("Exception:", exception.toString())
                                }

                            break
                        }
                    }
                }

                override fun onCancelled(databaseError: DatabaseError) {
                    Log.e("DB error: ", databaseError.toString())
                }
            })
    }

    fun rateAndUpdateBike(bike: Bike, rating: Float, location: Location) {
        val userId = bike.userId
        val bikeRef = databaseReference.child("users").child(userId).child("bikes")

        bikeRef.orderByChild("brand").equalTo(bike.brand)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(dataSnapshot: DataSnapshot) {
                    for (childSnapshot in dataSnapshot.children) {
                        val bikeSnapshot = childSnapshot.getValue(Bike::class.java)
                        if (bikeSnapshot != null && bikeSnapshot.model == bike.model) {
                            val bikeId = childSnapshot.key

                            val currentRating = bikeSnapshot.rating.toDouble()
                            val numOfRatings = bikeSnapshot.numOfRatings.toInt()

                            val newNumOfRatings = numOfRatings + 1
                            val newRating = ((currentRating * numOfRatings) + rating) / newNumOfRatings

                            bikeSnapshot.latitude = location.latitude
                            bikeSnapshot.longitude = location.longitude

                            bikeRef.child(bikeId!!).child("rating").setValue(newRating.toString())
                                .addOnSuccessListener {
                                    bikeRef.child(bikeId).child("numOfRatings").setValue(newNumOfRatings.toString())
                                        .addOnSuccessListener {
                                            bikeRef.child(bikeId).child("latitude").setValue(location.latitude)
                                                .addOnSuccessListener {
                                                    bikeRef.child(bikeId).child("longitude").setValue(location.longitude)
                                                        .addOnSuccessListener {
                                                            println("Bike rating, numOfRatings, and location updated successfully.")
                                                        }
                                                        .addOnFailureListener { e ->
                                                            println("Error updating bike location: $e")
                                                        }
                                                }
                                                .addOnFailureListener { e ->
                                                    println("Error updating bike location: $e")
                                                }
                                        }
                                        .addOnFailureListener { e ->
                                            println("Error updating numOfRatings: $e")
                                        }
                                }
                                .addOnFailureListener { e ->
                                    println("Error updating rating: $e")
                                }

                            break
                        }
                    }
                }

                override fun onCancelled(databaseError: DatabaseError) {
                    Log.e("DB error:", databaseError.toString())
                }
            })
    }

    fun retrieveAllBikes(callback: (List<Bike>) -> Unit) {
        val bikeList = mutableListOf<Bike>()

        val usersRef = databaseReference.child("users")

        usersRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {


                for (userSnapshot in dataSnapshot.children) {
                    val userId = userSnapshot.key

                    if (userId != null) {
                        val bikesRef = databaseReference.child("users").child(userId).child("bikes")

                        bikesRef.addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(bikesSnapshot: DataSnapshot) {
                                for (bikeSnapshot in bikesSnapshot.children) {
                                    val bikeMap = bikeSnapshot.getValue(object : GenericTypeIndicator<HashMap<String, Any>>() {})
                                    if (bikeMap != null) {
                                        val rented = bikeMap["rented"] as? Boolean ?: false

                                        if (!rented) {
                                            val bikeObject = Bike(
                                                userId,
                                                bikeMap["brand"] as? String ?: "",
                                                bikeMap["model"] as? String ?: "",
                                                bikeMap["style"] as? String ?: "",
                                                bikeMap["category"] as? String ?: "",
                                                bikeMap["year"] as? String ?: "0",
                                                bikeMap["transmission"] as? String ?: "",
                                                bikeMap["latitude"] as? Double ?: 0.0,
                                                bikeMap["longitude"] as? Double ?: 0.0,
                                                bikeMap["bikeImage"] as? String ?: "",
                                                bikeMap["rented"] as? Boolean ?: false,
                                                bikeMap["openKey"] as? String ?: "",
                                                bikeMap["rating"] as? String ?: "0",
                                                bikeMap["numOfRatings"] as? String ?: "0",
                                                bikeMap["dateAdded"] as? String ?: "0"
                                            )

                                            bikeList.add(bikeObject)
                                        }
                                    }
                                }

                                callback(bikeList)
                            }

                            override fun onCancelled(databaseError: DatabaseError) {
                                Log.e("Database Error", databaseError.toString())
                            }
                        })
                    }
                }
            }

            override fun onCancelled(databaseError: DatabaseError) {
                Log.e("Database Error", databaseError.toString())
            }
        })
    }

    private fun retrieveBrands(callback: (List<String>) -> Unit) {
        val brandsRef = storageReference.child("brands.txt")
        brandsRef.getBytes(Long.MAX_VALUE).addOnSuccessListener { bytes ->
            val brandsText = String(bytes, Charset.forName("UTF-8"))

            val brandList = brandsText.split("\n")
            val brandListWithNull = listOf("All brands") + brandList
            callback(brandListWithNull)
        }.addOnFailureListener { exception ->
            Log.e("Exception", exception.toString())
        }
    }

    private fun retrieveBrandsDialog(dialog: Dialog, spinner: Spinner) {
        retrieveBrands { brandList->

            val brandAdapter = ArrayAdapter(
                dialog.context, android.R.layout.simple_spinner_item, brandList
            )
            brandAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinner.adapter = brandAdapter
        }
    }

    private fun retrieveModels(brand: String, callback: (List<String>) -> Unit) {
        val lowercaseBrand = brand.trim().lowercase()
        val brandModelsRef = storageReference.child("$lowercaseBrand.txt")
        brandModelsRef.getBytes(Long.MAX_VALUE).addOnSuccessListener { bytes ->
            val modelsText = String(bytes, Charset.forName("UTF-8"))
            val modelList = modelsText.split("\n")
            val modelListWithNull = listOf("All models") + modelList
            callback(modelListWithNull)

        }.addOnFailureListener { exception ->
            Log.e("Exception", exception.toString())
        }
    }

    fun retrieveModelsDialog(dialog: Dialog, brand: String, spinner: Spinner) {

        retrieveModels(brand) { modelListWithNull->
            val modelAdapter = ArrayAdapter(
                dialog.context, android.R.layout.simple_spinner_item, modelListWithNull
            )
            modelAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinner.adapter = modelAdapter
        }

    }

    fun showBikeDialog(activity: Activity, bike: Bike) {
        val dialog = Dialog(activity)
        dialog.setContentView(R.layout.bike_dialog)

        val dialogBikePic: ImageView = dialog.findViewById(R.id.dialogBikePic)
        val textViewBikeMake: TextView = dialog.findViewById(R.id.cdBrand)
        val textViewBikeModel: TextView = dialog.findViewById(R.id.cdModel)
        val textViewBikeYear: TextView = dialog.findViewById(R.id.cdYear)
        val textViewBikeCategory: TextView = dialog.findViewById(R.id.cdCategory)
        val textViewBikeStyle: TextView = dialog.findViewById(R.id.cdStyle)
        val textViewBikeTransmission: TextView = dialog.findViewById(R.id.cdTransmission)
        val textViewBikeRating: TextView = dialog.findViewById(R.id.cdRating)
        val rentButton: Button = dialog.findViewById(R.id.btnRentBike)

        val ratingText = String.format("(%s) %.1f/5", bike.numOfRatings, bike.rating.toFloat())
        Glide.with(dialog.context)
            .load(bike.bikeImage)
            .placeholder(R.drawable.baseline_directions_bike_24)
            .into(dialogBikePic)

        textViewBikeMake.text = bike.brand
        textViewBikeModel.text = bike.model
        textViewBikeYear.text = bike.year
        textViewBikeCategory.text = bike.category
        textViewBikeStyle.text = bike.style
        textViewBikeTransmission.text = bike.transmission
        textViewBikeRating.text = ratingText

        rentButton.setOnClickListener{
            val intentRentBike = Intent(activity, BikeRentActivity::class.java)
            intentRentBike.putExtra("bike", bike)
            activity.startActivity(intentRentBike)
            activity.finish()
        }

        dialog.show()
    }

    fun showFilterDialog(context: Context, bikeList: MutableList<Bike>, callback: (MutableList<Bike>) -> Unit) {
        val dialog = Dialog(context)
        dialog.setContentView(R.layout.filter_dialog)

        var selectedBrand: String? = null
        var selectedModel: String? = null
        var selectedCategory: String? = null
        var selectedBikeStyle: String? = null
        var selectedTransmissionType: String? = null
        var selectedYearFrom: Number? = null
        var selectedYearTo: Number? = null

        val applyButton: Button = dialog.findViewById(R.id.applyButton)
        val cancelButton: Button = dialog.findViewById(R.id.resetBtn)
        val categorySpinner: Spinner = dialog.findViewById(R.id.categorySpinner1)
        val bikeStyleSpinner: Spinner = dialog.findViewById(R.id.bikeStyleSpinner1)
        val transmissionSpinner: Spinner = dialog.findViewById(R.id.transmissionSpinner1)
        val brandSpinner: Spinner = dialog.findViewById(R.id.brandSpinner1)
        val modelSpinner: Spinner = dialog.findViewById(R.id.modelSpinner1)
        val yearFrom : EditText = dialog.findViewById(R.id.yearFromDP)
        val yearTo : EditText = dialog.findViewById(R.id.yearToDP)

        retrieveBrandsDialog(dialog, brandSpinner)

        val categoryListWithNull = listOf("All categories") + categories
        val categoryAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, categoryListWithNull)
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        categorySpinner.adapter = categoryAdapter

        val bikeStyleListWithNull = listOf("All bike types") + bikeStyle
        val bikeStyleAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, bikeStyleListWithNull)
        bikeStyleAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        bikeStyleSpinner.adapter = bikeStyleAdapter

        val transmissionTypesWithNull = listOf("All transmission types", "Manual transmission", "Single Speed")
        val transmissionTypeAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, transmissionTypesWithNull)
        transmissionTypeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        transmissionSpinner.adapter = transmissionTypeAdapter

        brandSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val brand = if (position == 0) null else parent?.getItemAtPosition(position) as String
                selectedBrand = brand
                selectedBrand?.let { retrieveModelsDialog(dialog, it, modelSpinner) }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                selectedBrand = null
            }
        }

        categorySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedCategory = if (position == 0) null else parent?.getItemAtPosition(position) as String
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {
                selectedCategory = null
            }
        }

        bikeStyleSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedBikeStyle = if (position == 0) null else parent?.getItemAtPosition(position) as String
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {
                selectedBikeStyle = null
            }
        }

        transmissionSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedTransmissionType = if (position == 0) null else parent?.getItemAtPosition(position) as String
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {
                selectedTransmissionType = null
            }
        }

        applyButton.setOnClickListener {
            selectedYearFrom = yearFrom.text.toString().toIntOrNull()
            selectedYearTo = yearTo.text.toString().toIntOrNull()

            Toast.makeText(context, "Filter applied", Toast.LENGTH_SHORT).show()
            dialog.dismiss()

            filter(context, bikeList, selectedBrand, selectedModel, selectedCategory, selectedBikeStyle, selectedTransmissionType, selectedYearFrom, selectedYearTo) { filteredBikes->
                callback(filteredBikes)
            }
        }

        cancelButton.setOnClickListener {
            dialog.dismiss()
            retrieveAllBikes { newBikeList->
                callback(newBikeList as MutableList<Bike>)
            }
        }

        dialog.show()
    }

    private fun filter(context: Context, bikeList: MutableList<Bike>, selectedBrand: String?, selectedModel: String?, selectedCategory: String?, selectedBikeStyle: String?, selectedTransmissionType: String?, selectedYearFrom: Number?, selectedYearTo: Number?, callback: (MutableList<Bike>)-> Unit) {
        val filteredBikes = mutableListOf<Bike>()

        for (bike in bikeList) {
            val matchesBrand = selectedBrand == null || bike.brand == selectedBrand
            val matchesModel = selectedModel == null || bike.model == selectedModel
            val matchesCategory = selectedCategory == null || bike.category == selectedCategory
            val matchesBikeStyle = selectedBikeStyle == null || bike.style == selectedBikeStyle
            val matchesTransmissionType = selectedTransmissionType == null || bike.transmission == selectedTransmissionType
            val matchesYearFrom = selectedYearFrom == null || bike.year.toInt() >= selectedYearFrom.toInt()
            val matchesYearTo = selectedYearTo == null || bike.year.toInt() <= selectedYearTo.toInt()

            if (matchesBrand && matchesModel && matchesCategory && matchesBikeStyle && matchesTransmissionType && matchesYearFrom && matchesYearTo) {
                filteredBikes.add(bike)
            }
        }

        if (filteredBikes.isEmpty()) {
            val alertDialog = AlertDialog.Builder(context)
                .setTitle("No Bikes Found")
                .setMessage("No bikes match the selected filter criteria.")
                .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                .create()

            alertDialog.show()
        } else {
            callback(filteredBikes)
        }
    }

    fun calculateDistance(location1: LatLng, location2: LatLng): Float {
        val results = FloatArray(1)
        Location.distanceBetween(
            location1.latitude, location1.longitude,
            location2.latitude, location2.longitude,
            results
        )
        return results[0]
    }

}
