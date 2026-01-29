package pk.gop.pulse.katchiAbadi.data.local

import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import pk.gop.pulse.katchiAbadi.domain.model.SurveyImage
import pk.gop.pulse.katchiAbadi.databinding.ItemSurveyImageBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SurveyImageAdapter(
    private val onRemoveClick: (SurveyImage) -> Unit
) : RecyclerView.Adapter<SurveyImageAdapter.ImageViewHolder>() {

    private val images = mutableListOf<SurveyImage>()

    fun submitList(newImages: List<SurveyImage>) {
        images.clear()
        images.addAll(newImages)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val binding = ItemSurveyImageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ImageViewHolder(binding)
    }

    override fun getItemCount(): Int = images.size

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        holder.bind(images[position])
    }

    inner class ImageViewHolder(private val binding: ItemSurveyImageBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(image: SurveyImage) {
            // Set image
            binding.image.setImageURI(Uri.parse(image.uri))
            binding.tvImageType.text = image.type

            // Display timestamp
            if (image.timestamp != null && image.timestamp > 0) {
                val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
                val date = Date(image.timestamp)
                val dateTimeText = "${dateFormat.format(date)} ${timeFormat.format(date)}"
                binding.tvDateTime.text = dateTimeText
                binding.tvDateTime.visibility = View.VISIBLE
            } else {
                binding.tvDateTime.text = "No date/time"
                binding.tvDateTime.visibility = View.VISIBLE
            }

            // ✅ Display direction (for bottom layout)
            if (image.bearing != null) {
                val direction = getDirectionFromBearing(image.bearing)
                val directionText = "$direction (${image.bearing.toInt()}°)"
                binding.tvDirection.text = directionText
                binding.layoutDirection.visibility = View.VISIBLE  // Show the container
                Log.d("SurveyImageAdapter", "  Showing direction: $directionText")
            } else {
                binding.layoutDirection.visibility = View.GONE  // Hide the container
                Log.d("SurveyImageAdapter", "  No bearing available")
            }

            // Display location
            if (image.latitude != null && image.longitude != null) {
                val locationText = "Lat: %.6f\nLng: %.6f".format(image.latitude, image.longitude)
                binding.tvLocation.text = locationText
                binding.tvLocation.visibility = View.VISIBLE
            } else {
                binding.tvLocation.text = "Location not available"
                binding.tvLocation.visibility = View.VISIBLE
            }

            binding.btnRemove.setOnClickListener {
                onRemoveClick(image)
            }
        }

        private fun getDirectionFromBearing(bearing: Float): String {
            return when {
                bearing >= 337.5 || bearing < 22.5 -> "North ↑"
                bearing >= 22.5 && bearing < 67.5 -> "NE ↗"
                bearing >= 67.5 && bearing < 112.5 -> "East →"
                bearing >= 112.5 && bearing < 157.5 -> "SE ↘"
                bearing >= 157.5 && bearing < 202.5 -> "South ↓"
                bearing >= 202.5 && bearing < 247.5 -> "SW ↙"
                bearing >= 247.5 && bearing < 292.5 -> "West ←"
                bearing >= 292.5 && bearing < 337.5 -> "NW ↖"
                else -> "?"
            }
        }
    }
}