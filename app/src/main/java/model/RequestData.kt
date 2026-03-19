package com.example.flamease

import com.google.firebase.Timestamp

data class RequestData(
    var requestId: String? = null,
    val studentName: String? = null,
    val building: String = "",
    val room: String = "",
    val status: String = "",
    val timeSlotIndex: String? = null,
    val userId: String? = null,
    val role: String? = null,
    val createdAt: com.google.firebase.Timestamp? = null,
    val bookingDate: com.google.firebase.Timestamp? = null,
    val block: String? = null,
    val description: String? = null,
    val permit: String? = null,
    var notSeen: Boolean? = true // NEW: Add this field
)