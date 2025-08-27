package pk.gop.pulse.katchiAbadi.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import pk.gop.pulse.katchiAbadi.common.SurveyItemClickListener
import pk.gop.pulse.katchiAbadi.databinding.CardviewSurveyListBinding
import pk.gop.pulse.katchiAbadi.databinding.CardviewSurveyListTableBinding
import pk.gop.pulse.katchiAbadi.domain.model.OwnerInfo
import pk.gop.pulse.katchiAbadi.domain.model.SurveyEntity

class SurveyActivityAdapter() :
    ListAdapter<SurveyEntity, SurveyActivityAdapter.SurveyViewHolder>(SurveyDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SurveyViewHolder {
        val binding =
            CardviewSurveyListBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        return SurveyViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SurveyViewHolder, position: Int) {
        val survey = getItem(position)
        holder.bind(survey)
    }

    inner class SurveyViewHolder(private val binding: CardviewSurveyListBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(survey: SurveyEntity) {
            binding.apply {
                tvSurveyNo.text = "${survey.propertyNo}"
                tvArea.text = "${survey.area}"

                // List to store the results
                val resultList = mutableListOf<OwnerInfo>()

                // Split the strings by the delimiter ":$$:"
                val partsName = survey.name.split(":$$:")
                val partsFName = survey.fname.split(":$$:")
                val partsCnic = survey.cnic.split(":$$:")
                val partsRelation = survey.relation.split(":$$:")
                val partsGender = survey.gender.split(":$$:")

                // Check if all lists have exactly one item
                if (partsName.size == 1 && partsFName.size == 1) {
                    val record = OwnerInfo(
                        name = partsName[0],
                        fname = partsFName[0],
                        relation = partsRelation[0],
                        cnic = partsCnic[0],
                        gender = partsGender[0],

                        )
                    // Add the single record to the list
                    resultList.add(record)
                } else {
                    // Create a list of OwnerInfo objects
                    val records = partsName.indices.map { index ->
                        OwnerInfo(
                            name = partsName.getOrNull(index) ?: "",
                            fname = partsFName.getOrNull(index) ?: "",
                            relation = partsRelation.getOrNull(index) ?: "",
                            cnic = partsCnic.getOrNull(index) ?: "",
                            gender = partsGender.getOrNull(index) ?: ""
                        )
                    }

                    // Add all records to the result list
                    resultList.addAll(records)
                }

                // Output the results
                println(resultList)

                if (survey.isAttached) {
                    tvAttach.visibility = View.VISIBLE
                } else {
                    tvAttach.visibility = View.GONE
                }

                val ownersAdapter = OwnersAdapter()
                recyclerViewPartitions.layoutManager = LinearLayoutManager(itemView.context)
                recyclerViewPartitions.adapter = ownersAdapter
                ownersAdapter.submitList(resultList)

            }
        }
    }

    private class SurveyDiffCallback : DiffUtil.ItemCallback<SurveyEntity>() {
        override fun areItemsTheSame(oldItem: SurveyEntity, newItem: SurveyEntity): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: SurveyEntity, newItem: SurveyEntity): Boolean {
            return oldItem == newItem
        }
    }
}
