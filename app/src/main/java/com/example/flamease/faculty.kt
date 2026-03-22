package com.example.flamease

import adapter.RequestAdapter
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class faculty : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private lateinit var rvRequests: RecyclerView
    private lateinit var etSearchRequests: EditText
    private lateinit var adapter: RequestAdapter

    private val requestList = arrayListOf<RequestData>()
    private val masterRequestList = arrayListOf<RequestData>()
    private val CHANNEL_ID = "flamease_notifications"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        enableEdgeToEdge()
        setContentView(R.layout.activity_faculty)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        etSearchRequests = findViewById(R.id.etSearchRequests)
        rvRequests = findViewById(R.id.rvRecentRequests)
        rvRequests.layoutManager = LinearLayoutManager(this)

        adapter = RequestAdapter(requestList)
        rvRequests.adapter = adapter

        createNotificationChannel()
        checkNotificationPermission()
        setupNavigation()

        val auth = FirebaseAuth.getInstance()
        val userId = auth.currentUser?.uid // ✅ Declared only ONCE

        if (userId != null) {

            // 🔴 Real-time suspension listener
            db.collection("users").document(userId)
                .addSnapshotListener { snapshot, _ ->
                    if (snapshot != null && snapshot.exists()) {
                        val status = snapshot.getString("status")
                        if (status == "suspended") {
                            Toast.makeText(this, "Your account is suspended.", Toast.LENGTH_LONG).show()

                            // ✅ Clear SharedPreferences so auto-login is blocked
                            getSharedPreferences("FlameEasePrefs", MODE_PRIVATE)
                                .edit().clear().apply()

                            auth.signOut()

                            val intent = Intent(this, Login::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            startActivity(intent)
                        }
                    }
                }

            // ✅ Fetch profile and requests
            db.collection("users").document(userId).get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        val fName = document.getString("firstName") ?: ""
                        val lName = document.getString("lastName") ?: ""
                        findViewById<TextView>(R.id.txtUserName).text = "$fName $lName".uppercase()

                        val myIdNumber = document.getString("idNumber") ?: ""
                        startFetchingRequests(myIdNumber)
                    }
                }
        }

        etSearchRequests.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterRequests(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun filterRequests(text: String) {
        val filteredList = arrayListOf<RequestData>()
        for (item in masterRequestList) {
            if (item.room.lowercase().contains(text.lowercase()) ||
                item.building.lowercase().contains(text.lowercase())) {
                filteredList.add(item)
            }
        }
        adapter.updateList(filteredList)
    }

    private fun startFetchingRequests(studentId: String) {
        db.collection("room_requests")
            .whereEqualTo("userId", studentId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(4)
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null) return@addSnapshotListener

                for (dc in snapshot.documentChanges) {
                    if (dc.type == DocumentChange.Type.MODIFIED) {
                        val status = dc.document.getString("status")
                        val room = dc.document.getString("room")?.uppercase()
                        if (status?.lowercase() == "approved") {
                            sendNotification(room ?: "a room")
                        }
                    }
                }

                masterRequestList.clear()
                for (doc in snapshot.documents) {
                    val req = doc.toObject(RequestData::class.java)
                    if (req != null) masterRequestList.add(req)
                }

                filterRequests(etSearchRequests.text.toString())
            }
    }

    private fun setupNavigation() {
        findViewById<TextView>(R.id.btnViewAll).setOnClickListener { startActivity(Intent(this, request::class.java)) }
        findViewById<Button>(R.id.btnBuildings).setOnClickListener { startActivity(Intent(this, Buildings::class.java)) }

        findViewById<LinearLayout>(R.id.btnRequest).setOnClickListener {
            val intent = Intent(this, request::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        findViewById<LinearLayout>(R.id.notification).setOnClickListener {
            val intent = Intent(this, notifications::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        findViewById<LinearLayout>(R.id.settings).setOnClickListener {
            val intent = Intent(this, Settings::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Room Updates", NotificationManager.IMPORTANCE_HIGH)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun sendNotification(roomName: String) {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.approved)
            .setContentTitle("Request Approved!")
            .setContentText("Your request for $roomName was approved.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            NotificationManagerCompat.from(this).notify(System.currentTimeMillis().toInt(), builder.build())
        }
    }
}