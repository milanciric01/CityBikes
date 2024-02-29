package elfak.mosis.citybikes

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import BikeListAdapter
import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.Typeface
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.GenericTypeIndicator
import com.google.firebase.database.ValueEventListener

class ProfileActivity : AppCompatActivity() {
    private lateinit var profilePicture: ImageView
    private lateinit var fullName: TextView
    private lateinit var score: TextView
    private lateinit var email: TextView
    private lateinit var bikesRecyclerView: RecyclerView
    private lateinit var bikeListAdapter: BikeListAdapter
    private lateinit var leaderboardBtn: ImageButton

    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference
    private var currentUser: FirebaseUser? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        profilePicture = findViewById(R.id.profilePic)
        score = findViewById(R.id.tvScore)
        email = findViewById(R.id.tvEMail)
        fullName = findViewById(R.id.textViewImePrezime)
        leaderboardBtn = findViewById(R.id.btnLeaderboard)

        bikesRecyclerView = findViewById(R.id.bikeListRecyclerView1)
        bikesRecyclerView.layoutManager = LinearLayoutManager(this)

        bikeListAdapter = BikeListAdapter(emptyList())
        bikesRecyclerView.adapter = bikeListAdapter
        bikesRecyclerView.layoutManager = GridLayoutManager(this, 2)

        initView()
        retrieveAllUserBikes()
        leaderboardBtn.setOnClickListener {
            showLeaderboardDialog(currentUser)
        }
    }


    private fun initView() {
        auth = FirebaseAuth.getInstance()
        currentUser = auth.currentUser
        database = FirebaseDatabase.getInstance().reference.child("users").child(currentUser?.uid ?: "")

        database.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                if (dataSnapshot.exists()) {
                    val userData = dataSnapshot.value as? Map<String, Any>
                    userData?.let {
                        val profilePicUrl = userData["profilePicUrl"] as? String
                        val firstName = userData["firstName"] as? String
                        val lastName = userData["lastName"] as? String
                        val umail = userData["email"] as? String
                        val uscore = userData["score"] as? Number

                        profilePicUrl?.let {
                            Glide.with(this@ProfileActivity)
                                .load(it)
                                .into(profilePicture)
                        }

                        val fullNameText = "$firstName $lastName"
                        fullName.text = fullNameText
                        email.text = umail
                        score.text = "Score: " + uscore.toString()
                    }
                }
            }

            override fun onCancelled(databaseError: DatabaseError) {
                Log.e(TAG, "Database Error: ${databaseError.message}")
            }
        })
    }

    private fun retrieveAllUserBikes() {
        val currentUser = FirebaseAuth.getInstance().currentUser
        val userId = currentUser?.uid

        if (userId != null) {
            val usersRef = FirebaseDatabase.getInstance().getReference("users")
            val bikesRef = usersRef.child(userId).child("bikes")

            bikesRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(bikesSnapshot: DataSnapshot) {
                    val bikeList = mutableListOf<Bike>()

                    for (bikeSnapshot in bikesSnapshot.children) {
                        val bikeMap = bikeSnapshot.getValue(object : GenericTypeIndicator<HashMap<String, Any>>() {})
                        if (bikeMap != null) {

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
                                bikeMap["openKey"] as? String ?: ""
                            )

                            bikeList.add(bikeObject)
                        }
                    }
                    bikeListAdapter.setBikes(bikeList)
                }

                override fun onCancelled(databaseError: DatabaseError) {
                    Log.e(TAG, "Database Error: ${databaseError.message}")
                }
            })
        }
    }

    companion object {
        private const val TAG = "ProfileActivity"
    }

    private fun showLeaderboardDialog(currentUser: FirebaseUser?) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.leaderboard_dialog, null)
        val tableLayout = dialogView.findViewById<TableLayout>(R.id.leaderboardTable)

        val leaderboardData = ArrayList<Pair<String, Int>>()

        val usersRef = FirebaseDatabase.getInstance().reference.child("users")

        usersRef.orderByChild("score").addListenerForSingleValueEvent(object : ValueEventListener {
            @SuppressLint("SetTextI18n")
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                leaderboardData.clear()

                for (userSnapshot in dataSnapshot.children) {
                    val firstName = userSnapshot.child("firstName").value as String
                    val lastName = userSnapshot.child("lastName").value as String
                    val score = userSnapshot.child("score").value as Long

                    val fullName = "$firstName $lastName"
                    leaderboardData.add(Pair(fullName, score.toInt()))
                }

                leaderboardData.sortByDescending { it.second }

                for (i in leaderboardData.indices) {
                    val entry = leaderboardData[i]
                    val row = TableRow(this@ProfileActivity)

                    val rankTextView = TextView(this@ProfileActivity)
                    val scoreTextView = TextView(this@ProfileActivity)
                    val nameTextView = TextView(this@ProfileActivity)

                    rankTextView.text = (i + 1).toString()
                    scoreTextView.text = entry.second.toString()
                    nameTextView.text = entry.first

                    rankTextView.layoutParams = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1f)
                    scoreTextView.layoutParams = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1f)
                    nameTextView.layoutParams = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1f)

                    rankTextView.gravity = Gravity.CENTER
                    scoreTextView.gravity = Gravity.CENTER
                    nameTextView.gravity = Gravity.CENTER

                    if ((currentUser != null) && (entry.first == currentUser.uid)) {
                        rankTextView.setTextColor(Color.RED)
                        scoreTextView.setTextColor(Color.RED)
                        nameTextView.setTextColor(Color.RED)

                        rankTextView.setTypeface(null, Typeface.BOLD)
                        scoreTextView.setTypeface(null, Typeface.BOLD)
                        nameTextView.setTypeface(null, Typeface.BOLD)
                    }
                    row.addView(rankTextView)
                    row.addView(scoreTextView)
                    row.addView(nameTextView)

                    tableLayout.addView(row)
                }
            }

            override fun onCancelled(databaseError: DatabaseError) {
                Log.e("Database error:", databaseError.toString())
            }
        })

        val dialogBuilder = AlertDialog.Builder(this)
            .setView(dialogView)
            .setTitle("Leaderboard")
            .setPositiveButton("Close", null)

        val dialog = dialogBuilder.create()

        dialog.setOnShowListener {
            val closeButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val closeButtonLayoutParams = closeButton.layoutParams as LinearLayout.LayoutParams
            closeButtonLayoutParams.gravity = Gravity.CENTER
            closeButton.layoutParams = closeButtonLayoutParams
        }

        dialog.show()
    }
}
