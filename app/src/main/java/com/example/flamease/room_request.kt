package com.example.flamease

import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import java.util.Calendar

class room_request : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val slotTimeMap = mapOf(
        "1" to "7:30 AM - 9:00 AM",
        "2" to "9:00 AM - 10:30 AM",
        "3" to "10:30 AM - 12:00 PM",
        "4" to "12:00 PM - 1:30 PM",
        "5" to "1:30 PM - 3:00 PM",
        "6" to "3:00 PM - 4:30 PM",
        "7" to "4:30 PM - 6:00 PM"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_room_request)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val buildingId = intent.getStringExtra("BUILDING_ID") ?: ""
        val buildingName = intent.getStringExtra("BUILDING_NAME") ?: "unknown"
        val roomName = intent.getStringExtra("ROOM_CODE") ?: "UNKNOWN ROOM"

        findViewById<TextView>(R.id.tvHeaderRoomName).text = roomName

        val radioButtons = listOf(
            findViewById<RadioButton>(R.id.slot1),
            findViewById<RadioButton>(R.id.slot2),
            findViewById<RadioButton>(R.id.slot3),
            findViewById<RadioButton>(R.id.slot4),
            findViewById<RadioButton>(R.id.slot5),
            findViewById<RadioButton>(R.id.slot6),
            findViewById<RadioButton>(R.id.slot7)
        )

        // IMPORTANT: Disable past time slots first
        updateSlotsBasedOnTime(radioButtons)

        // Load availability from BOTH building settings AND today's bookings
        if (buildingId.isNotEmpty()) {
            loadRoomAvailability(buildingId, roomName, radioButtons)
        }

        // Handle slot selection - prevent selecting disabled slots
        radioButtons.forEach { button ->
            button.setOnClickListener {
                if (!button.isEnabled) {
                    button.isChecked = false
                    Toast.makeText(this, "This slot is not available", Toast.LENGTH_SHORT).show()
                } else {
                    radioButtons.forEach { it.isChecked = (it == button) }
                }
            }
        }

        findViewById<TextView>(R.id.btnBack).setOnClickListener { finish() }

        findViewById<Button>(R.id.btnRequest).setOnClickListener {
            val selectedButton = radioButtons.find { it.isChecked }

            when {
                selectedButton == null -> {
                    Toast.makeText(this, "Please select a time slot first", Toast.LENGTH_SHORT).show()
                }
                !selectedButton.isEnabled -> {
                    Toast.makeText(this, "This slot is unavailable", Toast.LENGTH_SHORT).show()
                    selectedButton.isChecked = false
                }
                else -> {
                    val timeSlotIndex = (radioButtons.indexOf(selectedButton) + 1).toString()
                    showDetailsPopup(buildingName, roomName, timeSlotIndex)
                }
            }
        }
    }

    /**
     * CRITICAL FIX: Check BOTH building availability AND today's bookings
     * Bookings are only checked for TODAY - tomorrow starts fresh (daily reset)
     */
    private fun loadRoomAvailability(buildingId: String, roomName: String, buttons: List<RadioButton>) {
        // Get building data (for "Unavailable" settings)
        db.collection("buildings").document(buildingId)
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null || !snapshot.exists()) {
                    // If no building data, just check bookings
                    checkTodaysBookings(roomName, buttons, null)
                    return@addSnapshotListener
                }

                val roomsArray = snapshot.get("rooms") as? List<Map<String, Any>>
                val specificRoom = roomsArray?.find {
                    it["code"].toString().equals(roomName, ignoreCase = true)
                }
                val availabilityMap = specificRoom?.get("availability") as? Map<String, String>

                // Now check today's bookings only
                checkTodaysBookings(roomName, buttons, availabilityMap)
            }
    }

    /**
     * KEY FIX: Only query bookings for TODAY.
     * Yesterday's bookings won't show up because bookingDate < todayStart
     * Tomorrow's bookings won't show up because bookingDate > todayEnd
     * This creates the "daily reset" behavior automatically!
     */
    private fun checkTodaysBookings(
        roomName: String,
        buttons: List<RadioButton>,
        buildingAvailability: Map<String, String>?
    ) {
        // Get today's date range (midnight to 23:59:59)
        val todayStart = getTodayStartTimestamp()
        val todayEnd = getTodayEndTimestamp()

        Log.d("RoomRequest", "Checking bookings for: $roomName between $todayStart and $todayEnd")

        // Normalize room name for consistent matching
        val normalizedRoomName = roomName.lowercase().trim()

        db.collection("room_requests")
            .whereEqualTo("room", normalizedRoomName)
            .whereIn("status", listOf("approved", "accepted", "registrar_pending"))
            // CRITICAL: Only get bookings for TODAY - this enables daily reset!
            .whereGreaterThanOrEqualTo("bookingDate", todayStart)
            .whereLessThanOrEqualTo("bookingDate", todayEnd)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    Log.e("RoomRequest", "Error loading bookings: $error")
                    // Still update UI with building availability even if bookings query fails
                    updateButtons(buttons, emptySet(), buildingAvailability)
                    return@addSnapshotListener
                }

                val occupiedSlots = mutableSetOf<String>()
                snapshot?.documents?.forEach { doc ->
                    val slot = doc.getString("timeSlotIndex")
                    val status = doc.getString("status")
                    val bookingDate = doc.getTimestamp("bookingDate")
                    Log.d("RoomRequest", "Found booking: slot=$slot, status=$status, date=$bookingDate")
                    slot?.let { occupiedSlots.add(it) }
                }

                Log.d("RoomRequest", "Today's occupied slots for $roomName: $occupiedSlots")
                updateButtons(buttons, occupiedSlots, buildingAvailability)
            }
    }

    private fun updateButtons(
        buttons: List<RadioButton>,
        occupiedSlots: Set<String>,
        buildingAvailability: Map<String, String>?
    ) {
        buttons.forEachIndexed { index, button ->
            val slotIndex = (index + 1).toString()
            val baseText = slotTimeMap[slotIndex] ?: button.text.toString().split(" (")[0]

            // 1. Priority: Check if the time has already passed (Closed)
            if (isSlotPast(index)) {
                setSlotDisabled(button, "Closed")
                return@forEachIndexed
            }

            // 2. Check Admin Settings (Unavailable)
            val buildingStatus = buildingAvailability?.get(slotIndex)
            val isBuildingUnavailable = !buildingStatus.isNullOrEmpty() &&
                    !buildingStatus.equals("available", ignoreCase = true)

            // 3. Check Live Bookings for TODAY only (Occupied)
            val isOccupied = occupiedSlots.contains(slotIndex)

            when {
                isBuildingUnavailable -> {
                    // Show only "Unavailable" or the specific reason (like a class block)
                    val message = if (buildingStatus!!.length > 2) buildingStatus else "Unavailable"
                    setSlotDisabled(button, message)
                }
                isOccupied -> {
                    setSlotDisabled(button, "Occupied")
                }
                else -> {
                    // Enable Slot
                    button.isEnabled = true
                    button.isClickable = true
                    button.alpha = 1.0f
                    button.setBackgroundResource(R.drawable.bg_radio_selector)
                    button.text = baseText
                }
            }
        }
    }

    private fun isSlotPast(index: Int): Boolean {
        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val currentMinute = calendar.get(Calendar.MINUTE)

        val slotEndTimes = listOf(
            Pair(8, 0), Pair(9, 30), Pair(11, 0), Pair(12, 30),
            Pair(14, 0), Pair(15, 30), Pair(17, 0)
        )

        val (endHour, endMinute) = slotEndTimes[index]
        return when {
            currentHour > endHour -> true
            currentHour == endHour -> currentMinute >= endMinute
            else -> false
        }
    }

    private fun setSlotDisabled(button: RadioButton, message: String) {
        button.isEnabled = false
        button.isClickable = false
        button.isChecked = false
        button.alpha = 0.5f
        val baseText = button.text.toString().split(" (")[0]
        button.text = "$baseText ($message)"
        button.setBackgroundResource(R.drawable.bg_radio_disabled)
    }

    private fun showDetailsPopup(building: String, room: String, slotIndex: String) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_request_details, null)
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setView(dialogView)

        val alertDialog = builder.create()
        alertDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val etBlock = dialogView.findViewById<EditText>(R.id.etBlockInfo)
        val etDesc = dialogView.findViewById<EditText>(R.id.etDescription)
        val btnSubmit = dialogView.findViewById<Button>(R.id.btnSubmitRequest)
        val btnCancel = dialogView.findViewById<TextView>(R.id.btnCancelRequest)

        btnSubmit.setOnClickListener {
            val block = etBlock.text.toString().trim()
            val description = etDesc.text.toString().trim()

            if (block.isEmpty() || description.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
            } else {
                btnSubmit.isEnabled = false
                btnSubmit.text = "Sending..."
                submitRequestWithAccountInfo(building, room, slotIndex, block, description, alertDialog)
            }
        }

        btnCancel.setOnClickListener { alertDialog.dismiss() }
        alertDialog.show()
    }

    /**
     * CRITICAL FIX: Save bookingDate as today's date (midnight timestamp)
     * This allows daily bookings - same room can be booked again tomorrow
     * Added notSeen field for notification tracking
     */
    private fun submitRequestWithAccountInfo(
        building: String, room: String, slotIndex: String,
        block: String, description: String, dialog: androidx.appcompat.app.AlertDialog
    ) {
        val currentUser = auth.currentUser ?: return
        val userId = currentUser.uid

        val cleanedBuilding = building.lowercase()
            .replace(" building", "")
            .trim()

        // Get today's date at midnight for bookingDate
        val bookingDate = getTodayStartTimestamp()

        db.collection("users").document(userId).get().addOnSuccessListener { document ->
            if (document.exists()) {
                val firstName = document.getString("firstName") ?: ""
                val lastName = document.getString("lastName") ?: ""
                val fullName = "$firstName $lastName"
                val studentId = document.getString("idNumber") ?: "N/A"
                val userRole = document.getString("role") ?: "Student"

                val requestData = hashMapOf(
                    "studentName" to fullName.uppercase(),
                    "building" to cleanedBuilding,
                    "room" to room.lowercase().trim(),
                    "status" to "pending",
                    "timeSlotIndex" to slotIndex,
                    "userId" to studentId,
                    "role" to userRole,
                    "createdAt" to com.google.firebase.Timestamp.now(), // When request was made
                    "bookingDate" to bookingDate, // CRITICAL: Which day this booking is for (enables daily reset)
                    "block" to block,
                    "description" to description,
                    "notSeen" to true  // ADDED: For tracking unread notifications
                )

                db.collection("room_requests").add(requestData)
                    .addOnSuccessListener {
                        dialog.dismiss()
                        Toast.makeText(this, "Request submitted successfully!", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                    .addOnFailureListener { e ->
                        val btnSubmit = dialog.findViewById<Button>(R.id.btnSubmitRequest)
                        btnSubmit?.isEnabled = true
                        btnSubmit?.text = "Submit Request"
                        Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
            }
        }
    }

    /**
     * Get today's date at midnight (00:00:00)
     * Used for bookingDate to enable daily reset
     */
    private fun getTodayStartTimestamp(): com.google.firebase.Timestamp {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return com.google.firebase.Timestamp(cal.time)
    }

    /**
     * Get today's date at 23:59:59
     * Used for querying today's bookings
     */
    private fun getTodayEndTimestamp(): com.google.firebase.Timestamp {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        return com.google.firebase.Timestamp(cal.time)
    }

    private fun updateSlotsBasedOnTime(radioButtons: List<RadioButton>) {
        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val currentMinute = calendar.get(Calendar.MINUTE)

        val slotEndTimes = listOf(
            Pair(9, 0), Pair(10, 30), Pair(12, 0), Pair(13, 30),
            Pair(15, 0), Pair(16, 30), Pair(18, 0)
        )

        radioButtons.forEachIndexed { index, button ->
            val (endHour, endMinute) = slotEndTimes[index]
            val isPast = when {
                currentHour > endHour -> true
                currentHour == endHour -> currentMinute >= endMinute
                else -> false
            }

            if (isPast) {
                setSlotDisabled(button, "Closed")
            }
        }
    }
}