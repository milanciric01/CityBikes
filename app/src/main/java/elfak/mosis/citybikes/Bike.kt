package elfak.mosis.citybikes

import java.io.Serializable

data class Bike(
    val userId: String = "",
    val brand: String = "",
    val model: String = "",
    val style: String = "",
    val category: String = "",
    val year: String = "",
    val transmission: String = "",
    var latitude: Double = 0.0,
    var longitude: Double = 0.0,
    var bikeImage: String = "",
    val rented: Boolean = false,
    val openKey: String = "",
    val rating: String = "",
    val numOfRatings: String = "",
    var dateAdded: String = "0"
) : Serializable