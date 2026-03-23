package com.example.flamease

import adapter.RoomAdapter
import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore

class Rooms : AppCompatActivity() {

    private lateinit var roomsRecyclerView: RecyclerView
    private val db = FirebaseFirestore.getInstance()
    // Corrected: Uses RoomData instead of a Map to fix the adapter error
    private val roomList = arrayListOf<RoomData>()

    override fun onResume() {
        super.onResume()
        // ✅ Update badge count whenever user returns to this activity
        BadgeManager.updateBadgeCount(this, R.id.tvNotifBadge)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.rooms)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        findViewById<LinearLayout>(R.id.btnHome).setOnClickListener {
            val intent = Intent(this, faculty::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
            finish()
        }
        findViewById<LinearLayout>(R.id.btnRequest).setOnClickListener {
            val intent = Intent(this, request::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        }
        findViewById<LinearLayout>(R.id.btnSettings).setOnClickListener {
            val intent = Intent(this, Settings::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        }
        findViewById<LinearLayout>(R.id.notification).setOnClickListener {
            val intent = Intent(this, notifications::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        }

        val buildingId = intent.getStringExtra("BUILDING_ID") ?: ""
        val buildingName = intent.getStringExtra("BUILDING_NAME") ?: "Building"

        findViewById<TextView>(R.id.tvBuildingHeaderName).text = buildingName

        roomsRecyclerView = findViewById(R.id.roomsRecyclerView)
        roomsRecyclerView.layoutManager = LinearLayoutManager(this)

        if (buildingId.isNotEmpty()) {
            fetchRooms(buildingId)
        }

        findViewById<TextView>(R.id.btnBack).setOnClickListener { finish() }
    }

    private fun fetchRooms(buildingId: String) {
        // Retrieve the building name from the intent so we can pass it forward
        val buildingName = intent.getStringExtra("BUILDING_NAME") ?: "unknown"

        db.collection("buildings").document(buildingId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    roomList.clear()
                    val roomsArray = snapshot.get("rooms") as? List<Map<String, Any>>

                    roomsArray?.forEach { roomMap ->
                        val roomCode = roomMap["code"] as? String ?: "Unknown Room"
                        roomList.add(RoomData(code = roomCode, status = "Available"))
                    }

                    // FIX: Pass the building info to the adapter here
                    roomsRecyclerView.adapter = RoomAdapter(roomList, buildingId, buildingName)
                }
            }
    }

    override fun onBackPressed() {
        val intent = Intent(this, faculty::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        startActivity(intent)
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
    }
}