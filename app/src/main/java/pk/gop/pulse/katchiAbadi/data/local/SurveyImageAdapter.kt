package pk.gop.pulse.katchiAbadi.data.local


import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import pk.gop.pulse.katchiAbadi.domain.model.SurveyImage
import pk.gop.pulse.katchiAbadi.databinding.ItemSurveyImageBinding


//class SurveyImageAdapter : RecyclerView.Adapter<SurveyImageAdapter.ImageViewHolder>() {

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
                binding.image.setImageURI(Uri.parse(image.uri))
                binding.tvImageType.text = image.type

                binding.btnRemove.setOnClickListener {
                    onRemoveClick(image)
                }
            }
        }

    }
