package elfak.mosis.citybikes

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import java.io.ByteArrayOutputStream
class RegisterActivity : AppCompatActivity() {
    private lateinit var email: EditText
    private lateinit var password: EditText
    private lateinit var firstName: EditText
    private lateinit var lastName: EditText
    private lateinit var profilePic: ImageView
    private lateinit var phoneNumber: EditText
    private lateinit var register: Button
    private lateinit var auth: FirebaseAuth

    private val takePictureLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val imageBitmap = result.data?.extras?.get("data") as Bitmap
                profilePic.setImageBitmap(imageBitmap)
            }
        }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        email = findViewById(R.id.email2)
        password = findViewById(R.id.password1)
        firstName = findViewById(R.id.fname)
        lastName = findViewById(R.id.lname)
        profilePic = findViewById(R.id.profilePic)
        phoneNumber = findViewById(R.id.phoneNumber)
        register = findViewById(R.id.registerBtn2)
        auth = FirebaseAuth.getInstance()

        profilePic.setOnClickListener{
            val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            takePictureLauncher.launch(cameraIntent)
        }

        register.setOnClickListener{
            val email_text = email.text.toString()
            val password_text = password.text.toString()
            val fname_text = firstName.text.toString()
            val lname_text = lastName.text.toString()
            val phone = phoneNumber.text.toString()
            val profilePicDrawable = profilePic.drawable as BitmapDrawable?
            val profileBitmap = profilePicDrawable?.bitmap

            if(email_text.isBlank() || password_text.isBlank() || fname_text.isBlank()
                || lname_text.isBlank() || phone.isBlank()) {
                Toast.makeText(this, "Empty credentials!", Toast.LENGTH_SHORT).show()
            }
            else if(profileBitmap == null) {
                Toast.makeText(this, "Please provide a picture!", Toast.LENGTH_SHORT).show()
            }
            else if(password_text.length < 6 ) {
                Toast.makeText(this, "Password must have minimum of 6 characters!", Toast.LENGTH_SHORT).show()
            }
            else if(!isValidEmail(email_text)) {
                Toast.makeText(this, "Please enter a valid email!", Toast.LENGTH_SHORT).show()
            }
            else if(!isValidPhone(phone)) {
                Toast.makeText(this, "Please enter a valid phone number!", Toast.LENGTH_SHORT).show()
            }
            else {
                registerUser(email_text, password_text, fname_text, lname_text, phone, profileBitmap)
            }
        }
    }

    private fun registerUser(emailText: String, passwordText: String, fnameText: String,
                             lnameText: String, phone: String, profileBitmap: Bitmap) {

        auth.createUserWithEmailAndPassword(emailText, passwordText)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    val userId = user?.uid

                    if (userId != null) {
                        val database = FirebaseDatabase.getInstance()
                        val usersRef = database.reference.child("users")

                        val userData = HashMap<String, Any>()
                        userData["email"] = emailText
                        userData["phone"] = phone
                        userData["firstName"] = fnameText
                        userData["lastName"] = lnameText
                        userData["score"] = 0

                        val storageRef = FirebaseStorage.getInstance().reference
                        val profilePicRef = storageRef.child("profile_pictures/$userId.jpg")
                        Log.d("TAG", "User ID: $userId")
                        val baos = ByteArrayOutputStream()
                        profileBitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos)
                        val data = baos.toByteArray()

                        val uploadTask = profilePicRef.putBytes(data)
                        uploadTask.addOnSuccessListener { taskSnapshot ->
                            // Get the download URL of the uploaded profile picture
                            val downloadUrlTask = taskSnapshot.storage.downloadUrl
                            downloadUrlTask.addOnSuccessListener { uri ->
                                val profilePicUrl = uri.toString()
                                userData["profilePicUrl"] = profilePicUrl

                                usersRef.child(userId).setValue(userData)
                                    .addOnSuccessListener {
                                        Toast.makeText(this, "Registration successful", Toast.LENGTH_SHORT).show()
                                    }
                                    .addOnFailureListener { exception ->
                                        Toast.makeText(this, "Failed to store user data", Toast.LENGTH_SHORT).show()
                                    }
                            }
                        }.addOnFailureListener { exception ->
                            Toast.makeText(this,"Failed to upload profile picture", Toast.LENGTH_SHORT).show()
                        }
                    }
                    val intent = Intent(this, MainActivity::class.java)
                    startActivity(intent)
                    finish()

                } else {
                    val exception = task.exception
                    Toast.makeText(this,"Failed to create user account: ${exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }


    private fun isValidPhone(phone: String): Boolean {
        val phoneNumberRegex = Regex("^\\+[1-9]\\d{1,14}\$")
        return phone.matches(phoneNumberRegex)
    }

    private fun isValidEmail(email: String): Boolean {
        val emailRegex = Regex("^\\w+([.-]?\\w+)*@\\w+([.-]?\\w+)*(\\.\\w{2,3})+\$")
        return email.matches(emailRegex)
    }

}