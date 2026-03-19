package adapter

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.example.flamease.R
import com.example.flamease.RequestData
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotificationAdapter(
    private var rawList: List<RequestData>,
    private val onDeleteModeChanged: (Boolean) -> Unit,
    private val onItemClick: (RequestData) -> Unit,
    private val onFeedbackClick: (RequestData) -> Unit,
    private val onMarkAsRead: (String) -> Unit // NEW: Callback to mark as read
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val TYPE_HEADER = 0
    private val TYPE_ITEM = 1
    private val displayList = mutableListOf<Any>()
    private val selectedItems = HashSet<String>()
    private var isSelectionMode = false

    init { groupDataByDate() }

    @Suppress("NotifyDataSetChanged")
    fun updateData(newList: List<RequestData>) {
        this.rawList = newList
        groupDataByDate()
        notifyDataSetChanged()
    }

    // NEW: Get count of unread notifications
    fun getUnreadCount(): Int {
        return rawList.count { it.notSeen == true }
    }

    private fun groupDataByDate() {
        displayList.clear()
        if (rawList.isEmpty()) return
        val sdfHeader = SimpleDateFormat("MMMM d", Locale.getDefault())
        val todayStr = sdfHeader.format(Date())
        var lastDate = ""
        for (item in rawList) {
            val itemDate = item.createdAt?.toDate()?.let { sdfHeader.format(it) } ?: ""
            if (itemDate != lastDate) {
                displayList.add(if (itemDate == todayStr) "Today" else itemDate)
                lastDate = itemDate
            }
            displayList.add(item)
        }
    }

    override fun getItemViewType(position: Int) = if (displayList[position] is String) TYPE_HEADER else TYPE_ITEM

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_HEADER) {
            HeaderViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_date_header, parent, false))
        } else {
            NotifViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_notification_row, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = displayList[position]
        if (holder is HeaderViewHolder) {
            holder.tvHeader.text = item as String
        } else if (holder is NotifViewHolder) {
            val data = item as RequestData
            val id = data.requestId ?: ""
            val status = data.status.lowercase()

            // NEW: Handle background color based on notSeen status
            val isUnread = data.notSeen == true
            val cardView = holder.itemView as? CardView

            if (isUnread) {
                // Light grey background for unread
                cardView?.setCardBackgroundColor(Color.parseColor("#F5F5F5"))
            } else {
                // White background for read
                cardView?.setCardBackgroundColor(Color.WHITE)
            }

            // Selection mode background (green) takes priority
            if (selectedItems.contains(id)) {
                cardView?.setCardBackgroundColor(Color.parseColor("#D1F2EB"))
            }

            holder.title.text = when (status) {
                "approved" -> "Request Approved!"
                "rejected" -> "Request Rejected"
                "expired"  -> "Request Expired"
                "pending", "registrar_pending" -> "Request Pending"
                "accepted" -> "Request Accepted"
                else       -> "Status Updated"
            }

            // --- SMART CHECK TO PREVENT DOUBLE BUILDING NAME ---
            val buildingName = data.building.uppercase()
            val roomName = data.room.uppercase()

            val displayLocation = if (roomName.startsWith(buildingName)) {
                roomName // Room already includes building (e.g., "RS 101")
            } else {
                "$buildingName $roomName" // Combine them (e.g., "RS" + "101")
            }

            holder.message.text = "Your request for $displayLocation is $status."
            // ---------------------------------------------------

            // NEW: Show time ago
            holder.time.text = getTimeAgo(data.createdAt?.toDate())

            val canFeedback = status in listOf("approved", "rejected", "expired")
            holder.btnFeedback.visibility = if (canFeedback) View.VISIBLE else View.GONE

            holder.btnFeedback.setOnClickListener { onFeedbackClick(data) }

            holder.itemView.setOnClickListener {
                if (isSelectionMode) {
                    toggleSelection(id)
                } else {
                    // NEW: Mark as read when clicked
                    if (isUnread) {
                        onMarkAsRead(id)
                    }
                    if (status != "approved") showStatusPopup(holder.itemView.context, data)
                    onItemClick(data)
                }
            }

            holder.itemView.setOnLongClickListener {
                if (!isSelectionMode) {
                    isSelectionMode = true
                    onDeleteModeChanged(true)
                    toggleSelection(id)
                }
                true
            }
        }
    }

    // NEW: Helper function to format time ago
    private fun getTimeAgo(date: Date?): String {
        if (date == null) return ""

        val now = Date()
        val diffInMillis = now.time - date.time
        val diffInMinutes = diffInMillis / (1000 * 60)
        val diffInHours = diffInMillis / (1000 * 60 * 60)
        val diffInDays = diffInMillis / (1000 * 60 * 60 * 24)

        return when {
            diffInMinutes < 1 -> "Just now"
            diffInMinutes < 60 -> "$diffInMinutes min ago"
            diffInHours < 24 -> "$diffInHours hr ago"
            diffInDays == 1L -> "Yesterday"
            diffInDays < 7 -> "$diffInDays days ago"
            else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(date)
        }
    }

    private fun showStatusPopup(context: Context, request: RequestData) {
        val status = request.status?.lowercase() ?: "pending"

        // Apply the same smart check for the popup message
        val bldg = request.building.uppercase()
        val rm = request.room.uppercase()
        val loc = if (rm.startsWith(bldg)) rm else "$bldg $rm"

        val (title, message) = when (status) {
            "rejected" -> Pair("Request Rejected", "Your request for $loc was not accepted.")
            "accepted" -> Pair("Step 1 Complete!", "Accepted by GSD. Now send to Registrar.")
            "registrar_pending" -> Pair("Almost there!", "The Registrar is currently reviewing your request.")
            "expired" -> Pair("Request Expired", "This request for $loc has passed its time limit.")
            else -> Pair("Request Pending", "Waiting for approval.")
        }

        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_status_feedback, null)
        val alertDialog = AlertDialog.Builder(context).setView(dialogView).create()

        dialogView.findViewById<TextView>(R.id.tvFeedbackTitle).text = title
        dialogView.findViewById<TextView>(R.id.tvFeedbackMessage).text = message
        dialogView.findViewById<ImageView>(R.id.ivFeedbackIcon)?.visibility = View.GONE
        dialogView.findViewById<Button>(R.id.btnStatusClose).setOnClickListener { alertDialog.dismiss() }

        alertDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        alertDialog.show()
    }

    fun getSelectedIds(): List<String> = selectedItems.toList()

    @Suppress("NotifyDataSetChanged")
    fun selectAll() {
        val allIds = rawList.mapNotNull { it.requestId }
        if (selectedItems.size == allIds.size && allIds.isNotEmpty()) {
            selectedItems.clear()
            isSelectionMode = false
            onDeleteModeChanged(false)
        } else {
            isSelectionMode = true
            onDeleteModeChanged(true)
            selectedItems.addAll(allIds)
        }
        notifyDataSetChanged()
    }

    @Suppress("NotifyDataSetChanged")
    fun clearSelection() {
        selectedItems.clear()
        isSelectionMode = false
        onDeleteModeChanged(false)
        notifyDataSetChanged()
    }

    @Suppress("NotifyDataSetChanged")
    private fun toggleSelection(id: String) {
        if (selectedItems.contains(id)) selectedItems.remove(id) else selectedItems.add(id)
        if (selectedItems.isEmpty()) { isSelectionMode = false; onDeleteModeChanged(false) }
        notifyDataSetChanged()
    }

    override fun getItemCount() = displayList.size

    class HeaderViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val tvHeader: TextView = v.findViewById(R.id.tvDateHeader)
    }

    class NotifViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(R.id.tvNotifTitle)
        val message: TextView = v.findViewById(R.id.tvNotifMessage)
        val time: TextView = v.findViewById(R.id.tvNotifTime)
        val btnFeedback: TextView = v.findViewById(R.id.btnFeedback)
    }
}