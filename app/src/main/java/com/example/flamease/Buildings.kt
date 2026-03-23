package com.example.flamease

import adapter.BuildingAdapter
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import model.Building

class Buildings : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var buildingRecyclerView: RecyclerView
    private lateinit var buildingList: ArrayList<Building>
    private lateinit var adapter: BuildingAdapter
    private lateinit var spinnerBuildings: Spinner
    private lateinit var spinnerAdapter: ArrayAdapter<String>

    override fun onResume() {
        super.onResume()
        // ✅ Update badge count whenever user returns to this activity
        BadgeManager.updateBadgeCount(this, R.id.tvNotifBadge)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_buildings)

        // UI Initialization
        buildingRecyclerView = findViewById(R.id.buildingsRecyclerView)
        spinnerBuildings = findViewById(R.id.spinnerBuildings)
        findViewById<TextView>(R.id.btnBack).setOnClickListener { finish() }

        buildingRecyclerView.layoutManager = LinearLayoutManager(this)
        buildingRecyclerView.setHasFixedSize(true)

        buildingList = arrayListOf()
        adapter = BuildingAdapter(arrayListOf()) { selectedBuilding ->
            val intent = Intent(this, Rooms::class.java)
            intent.putExtra("BUILDING_ID", selectedBuilding.id)
            intent.putExtra("BUILDING_NAME", selectedBuilding.name)
            startActivity(intent)
        }
        buildingRecyclerView.adapter = adapter

        db = FirebaseFirestore.getInstance()
        getBuildingData()

        // Bottom Navigation
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
        findViewById<TextView>(R.id.btnBack).setOnClickListener {
            finish()
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun getBuildingData() {
        db.collection("buildings").addSnapshotListener { snapshot, e ->
            if (e != null || snapshot == null) return@addSnapshotListener

            buildingList.clear()
            val spinnerItems = arrayListOf("All Buildings")

            for (doc in snapshot) {
                val rawName = doc.getString("name") ?: "Unknown"
                val roomCount = doc.get("roomCount")?.toString() ?: "0"

                // Formatting name
                val formattedName = rawName.split(" ").joinToString(" ") { word ->
                    word.lowercase().replaceFirstChar { it.uppercase() }
                } + " Building"

                buildingList.add(Building(doc.id, formattedName, roomCount))
                spinnerItems.add(formattedName)
            }

            adapter.updateList(ArrayList(buildingList))
            setupSpinner(spinnerItems)
        }
    }

    private fun setupSpinner(items: List<String>) {
        spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, items)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerBuildings.adapter = spinnerAdapter

        spinnerBuildings.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selected = items[position]
                if (selected == "All Buildings") {
                    adapter.updateList(buildingList)
                } else {
                    filterBuildings(selected)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun filterBuildings(query: String) {
        val tempFilteredList = arrayListOf<Building>()
        for (building in buildingList) {
            if (building.name?.lowercase()?.contains(query.lowercase()) == true) {
                tempFilteredList.add(building)
            }
        }
        adapter.updateList(tempFilteredList)
    }

    override fun onBackPressed() {
        val intent = Intent(this, faculty::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        startActivity(intent)
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
    }
}