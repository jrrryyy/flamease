package adapter

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.flamease.R
import model.Building

class BuildingAdapter(
    private val buildingList: ArrayList<Building>,
    private val onItemClick: (Building) -> Unit
) : RecyclerView.Adapter<BuildingAdapter.BuildingViewHolder>() {

    class BuildingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imgBuilding: ImageView = itemView.findViewById(R.id.imgBuilding)
        val tvBuildingName: TextView = itemView.findViewById(R.id.tvBuildingName)

    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BuildingViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_building, parent, false)
        return BuildingViewHolder(itemView)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: BuildingViewHolder, position: Int) {
        val currentBuilding = buildingList[position]

        holder.tvBuildingName.text = currentBuilding.name

        // Handle clicks to navigate to specific rooms
        holder.itemView.setOnClickListener {
            onItemClick(currentBuilding)
        }
    }

    // In BuildingAdapter.kt
    @SuppressLint("NotifyDataSetChanged")
    fun updateList(newList: List<Building>) {
        this.buildingList.clear()
        this.buildingList.addAll(newList)
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int {
        return buildingList.size
    }
}