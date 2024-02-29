import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import elfak.mosis.citybikes.Bike
import elfak.mosis.citybikes.R
import elfak.mosis.citybikes.databinding.ItemBikeBinding

class BikeListAdapter(private var bikes: List<Bike>) : RecyclerView.Adapter<BikeListAdapter.BikeViewHolder>() {

    private var onBikeClickListener: OnBikeClickListener? = null

    interface OnBikeClickListener {
        fun onBikeClick(bike: Bike)
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BikeViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemBikeBinding.inflate(inflater, parent, false)
        return BikeViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BikeViewHolder, position: Int) {
        val bike = bikes[position]
        holder.bind(bike)
    }

    override fun getItemCount(): Int {
        return bikes.size
    }

    fun setBikes(bikes: List<Bike>) {
        this.bikes = bikes
        notifyDataSetChanged()
    }

    fun setOnBikeClickListener(listener: OnBikeClickListener) {
        onBikeClickListener = listener
    }

    inner class BikeViewHolder(private val binding: ItemBikeBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(bike: Bike) {
            binding.textViewBikeMakeModel.text = "${bike.brand} ${bike.model}"

            Glide.with(itemView)
                .load(bike.bikeImage)
                .placeholder(R.drawable.baseline_directions_bike_24)
                .into(binding.imageViewBike)

            itemView.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val bike = bikes[position]
                    onBikeClickListener?.onBikeClick(bike)
                }
            }
        }
    }

}
