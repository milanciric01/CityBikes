package elfak.mosis.citybikes

import BikeListAdapter
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ImageButton
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale



class MainActivity : AppCompatActivity(), BikeListAdapter.OnBikeClickListener {

    private lateinit var btnMap: ImageButton
    private lateinit var addBike: ImageButton
    private lateinit var filterBtn: ImageButton
    private lateinit var sortBtn: ImageButton
    private lateinit var profileBtn: ImageButton
    private lateinit var databaseReference: DatabaseReference
    private lateinit var bikeListRecyclerView: RecyclerView
    private lateinit var bikeListAdapter: BikeListAdapter
    private lateinit var storage: FirebaseStorage
    private lateinit var storageReference: StorageReference
    private lateinit var bikeList: MutableList<Bike>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)




        storage = FirebaseStorage.getInstance()
        storageReference = storage.reference.child("brands_models")
        databaseReference = FirebaseDatabase.getInstance().reference

        bikeList = mutableListOf()

        initView()


        addBike = findViewById(R.id.btnAddBike)
        btnMap = findViewById(R.id.btnMap)
        filterBtn = findViewById(R.id.btnFilterMain)
        sortBtn = findViewById(R.id.sortBtn)
        profileBtn = findViewById(R.id.btnProfile)

        btnMap.setOnClickListener{
            val intentMap = Intent(this, MapActivity::class.java)
            intentMap.putExtra("bikeList", ArrayList(bikeList))
            startActivity(intentMap)
        }
        addBike.setOnClickListener{
            val intentAddBike = Intent(this, AddBikeActivity::class.java)
            startActivity(intentAddBike)
        }
        profileBtn.setOnClickListener{
            val intentProfile = Intent(this, ProfileActivity::class.java)
            startActivity(intentProfile)
        }
        sortBtn.setOnClickListener {
            showSortDialog()
        }
        filterBtn.setOnClickListener{
            showFilterDialog()
        }

    }
    @SuppressLint("NotifyDataSetChanged")
    private fun showFilterDialog() {
        BikesUtils.showFilterDialog(this, bikeList) { filteredBikes ->
            bikeListAdapter.setBikes(filteredBikes)
            bikeListAdapter.notifyDataSetChanged()
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun showSortDialog() {
        val options = arrayOf("Rating Ascending", "Rating Descending", "Date added: Older", "Date added: Newer")

        val builder = AlertDialog.Builder(this)
        builder.setTitle("Sort Options")
        builder.setItems(options) { dialog, which ->
            when (which) {
                0 -> {
                    bikeList.sortBy { it.rating.toFloat() }
                    bikeListAdapter.notifyDataSetChanged()
                }
                1 -> {
                    bikeList.sortByDescending { it.rating.toFloat() }
                    bikeListAdapter.notifyDataSetChanged()

                }
                2 -> {
                    Log.d("pre", "$bikeList")
                    bikeList.sortBy { formatDate(it.dateAdded.toLong()) }
                    Log.d("posle", "$bikeList")
                    bikeListAdapter.notifyDataSetChanged()
                }
                3 -> {
                    bikeList.sortByDescending { formatDate(it.dateAdded.toLong()) }
                    bikeListAdapter.notifyDataSetChanged()
                }
            }
            dialog.dismiss()
        }
        builder.create().show()
    }
    private fun formatDate(dateInMillis: Long): String {
        val dateFormat = SimpleDateFormat("dd MMM yyyy HH:mm:ss", Locale.getDefault())
        val date = Date(dateInMillis)
        return dateFormat.format(date)
    }
    override fun onResume() {
        super.onResume()
        retrieveAllBikes()
    }


    private fun initView() {
        bikeListRecyclerView = findViewById(R.id.bikeListRecyclerView)
        bikeListRecyclerView.layoutManager = GridLayoutManager(this, 2)
        bikeListAdapter = BikeListAdapter(emptyList())
        bikeListRecyclerView.adapter = bikeListAdapter
    }

    private fun retrieveAllBikes() {
        BikesUtils.retrieveAllBikes { bikes ->
            bikeList = ArrayList(bikes)
            Log.d("Bike List:","$bikeList")
            bikeListAdapter.setOnBikeClickListener(this@MainActivity)
            bikeListAdapter.setBikes(bikeList)
        }
    }
    override fun onBikeClick(bike: Bike) {
        showBikeDialog(bike)
    }

    private fun showBikeDialog(bike: Bike) {
        BikesUtils.showBikeDialog(this, bike)
    }



}