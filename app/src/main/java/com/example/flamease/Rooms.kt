package com.example.flamease

import adapter.RoomAdapter
import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore

class Rooms : AppCompatActivity() {

    private lateinit var roomsRecyclerView: RecyclerView
    private val db = FirebaseFirestore.getInstance()
    // Corrected: Uses RoomData instead of a Map to fix the adapter error
    private val roomList = arrayListOf<RoomData>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.rooms)

        findViewById<LinearLayout>(R.id.btnHome).setOnClickListener {
            startActivity(Intent(this, faculty::class.java))
            finish()
        }
        findViewById<LinearLayout>(R.id.btnRequest).setOnClickListener {
            startActivity(Intent(this, request::class.java))
        }
        findViewById<LinearLayout>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, Settings::class.java))
        }
        findViewById<LinearLayout>(R.id.notification).setOnClickListener {
            startActivity(Intent(this, notifications::class.java))
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
}