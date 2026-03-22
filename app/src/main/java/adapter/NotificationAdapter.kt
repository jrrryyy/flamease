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
import androidx.appcompat.app.AppCompatActivity
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
    private val onMarkAsRead: (String) -> Unit,
    // ✅ Pass activity so dialogs don't crash when activity is finishing
    private val onShowStatusPopup: (RequestData) -> Unit
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

    override fun getItemViewType(position: Int) =
        if (displayList[position] is String) TYPE_HEADER else TYPE_ITEM

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_HEADER) {
            HeaderViewHolder(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_date_header, parent, false)
            )
        } else {
            NotifViewHolder(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_notification_row, parent, false)
            )
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

            val isUnread = data.notSeen == true
            val cardView = holder.itemView as? CardView

            // ✅ Always reset color first to avoid recycled view color bugs
            when {
                selectedItems.contains(id) -> cardView?.setCardBackgroundColor(Color.parseColor("#D1F2EB"))
                isUnread -> cardView?.setCardBackgroundColor(Color.parseColor("#F5F5F5"))
                else -> cardView?.setCardBackgroundColor(Color.WHITE)
            }

            holder.title.text = when (status) {
                "approved" -> "Request Approved!"
                "rejected" -> "Request Rejected"
                "expired" -> "Request Expired"
                "pending", "registrar_pending" -> "Request Pending"
                "accepted" -> "Request Accepted"
                else -> "Status Updated"
            }

            val buildingName = data.building.uppercase()
            val roomName = data.room.uppercase()
            val displayLocation = if (roomName.startsWith(buildingName)) roomName
            else "$buildingName $roomName"

            holder.message.text = "Your request for $displayLocation is $status."
            holder.time.text = getTimeAgo(data.createdAt?.toDate())

            val canFeedback = status in listOf("approved", "rejected", "expired")
            holder.btnFeedback.visibility = if (canFeedback) View.VISIBLE else View.GONE

            // ✅ Prevent feedback click from also triggering itemView click
            holder.btnFeedback.setOnClickListener { v ->
                v.tag = "feedback_clicked"
                onFeedbackClick(data)
            }

            holder.itemView.setOnClickListener {
                if (isSelectionMode) {
                    toggleSelection(id)
                } else {
                    if (isUnread) onMarkAsRead(id)
                    // ✅ Use activity callback for dialog — safe from crashes
                    if (status != "approved") onShowStatusPopup(data)
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
        if (selectedItems.contains(id)) selectedItems.remove(id)
        else selectedItems.add(id)
        if (selectedItems.isEmpty()) {
            isSelectionMode = false
            onDeleteModeChanged(false)
        }
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