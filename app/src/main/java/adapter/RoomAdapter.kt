package adapter



import android.content.Intent

import android.view.LayoutInflater

import android.view.View

import android.view.ViewGroup

import android.widget.Button

import android.widget.TextView

import androidx.recyclerview.widget.RecyclerView

import com.example.flamease.R

import com.example.flamease.RoomData

import com.example.flamease.room_request

import java.util.Locale



class RoomAdapter(

    private val roomList: List<RoomData>,

    private val buildingId: String, // Add this

    private val buildingName: String ) :

    RecyclerView.Adapter<RoomAdapter.RoomViewHolder>() {



    class RoomViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        val tvRoomName: TextView = itemView.findViewById(R.id.tvRoomName)

        val tvRoomStatus: TextView = itemView.findViewById(R.id.tvRoomStatus)

// Ensure this ID matches the button in your item_room.xml

        val btnCheckAvailable: Button = itemView.findViewById(R.id.btnCheckAvailability)





    }



    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RoomViewHolder {

        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_room, parent, false)

        return RoomViewHolder(view)

    }



    override fun onBindViewHolder(holder: RoomViewHolder, position: Int) {

        val room = roomList[position]



// Auto-Capitalize everything

        holder.tvRoomName.text = room.code.uppercase(Locale.ROOT)

        holder.tvRoomStatus.text = room.status.uppercase(Locale.ROOT)



// HANDLE THE CLICK HERE

        holder.btnCheckAvailable.setOnClickListener {

            val context = holder.itemView.context

            val intent = Intent(context, room_request::class.java)



// Pass the room data to the next screen so it knows which room you clicked

            intent.putExtra("BUILDING_ID", buildingId)

            intent.putExtra("BUILDING_NAME", buildingName)

            intent.putExtra("ROOM_CODE", room.code.uppercase(Locale.ROOT))



            context.startActivity(intent)

        }

    }



    override fun getItemCount(): Int = roomList.size

}