package pk.gop.pulse.katchiAbadi.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONObject
import pk.gop.pulse.katchiAbadi.common.Utility
import pk.gop.pulse.katchiAbadi.common.ViewRecordClickListener
import pk.gop.pulse.katchiAbadi.data.remote.post.Floors
import pk.gop.pulse.katchiAbadi.data.remote.post.Partition
import pk.gop.pulse.katchiAbadi.databinding.CardViewRecordBinding
import pk.gop.pulse.katchiAbadi.domain.model.SurveyFormEntity


class ViewRecordAdapter(private val listener: ViewRecordClickListener) :
    ListAdapter<SurveyFormEntity, ViewRecordAdapter.SurveyViewHolder>(SurveyDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SurveyViewHolder {
        val binding =
            CardViewRecordBinding.inflate(
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

    inner class SurveyViewHolder(private val binding: CardViewRecordBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(survey: SurveyFormEntity) {
            binding.apply {
                if (survey.parcelOperation == "Split") {
                    layoutPageHeader.visibility = View.VISIBLE
                    tvSubParcel.text = survey.subParcelId.toString()
                } else {
                    layoutPageHeader.visibility = View.GONE
                }

                tvSurveyDateTime.text = Utility.convertStringToDate(survey.mobileTimestamp)

                if(survey.isRevisit == 1 && survey.newStatusId in mutableListOf(3,11)){

                    tvPropertyDetailsHeader.visibility = View.GONE
                    tvSurveyNoCaption.visibility = View.GONE
                    tvSurveyNo.visibility = View.GONE
                    tvAreaCaption.visibility = View.GONE
                    tvArea.visibility = View.GONE
                    tvInterviewStatusCaption.visibility = View.GONE
                    tvInterviewStatus.visibility = View.GONE

                    tvOwnerDetailsDivider.visibility = View.GONE
                    tvOwnerDetailsHeader.visibility = View.GONE
                    tvOccupancyDivider.visibility = View.GONE
                    tvOccupancyHeader.visibility = View.GONE

                    tvOwnerNameCaption.visibility = View.GONE
                    tvFatherHusbandNameCaption.visibility = View.GONE
                    tvGenderCaption.visibility = View.GONE
                    tvCnicCaption.visibility = View.GONE
                    tvMobileCaption.visibility = View.GONE
                    tvRespondentStatusCaption.visibility = View.GONE

                    tvOwnerName.visibility = View.GONE
                    tvFatherHusbandName.visibility = View.GONE
                    tvGender.visibility = View.GONE
                    tvCnic.visibility = View.GONE
                    tvMobile.visibility = View.GONE
                    tvRespondentStatus.visibility = View.GONE
                }else{
                    tvSurveyNo.text = survey.propertyNumber
                    tvArea.text = survey.area
                    tvInterviewStatus.text = survey.interviewStatus

                    when(survey.interviewStatus){
                        "Respondent Present" -> {

                            tvOwnerDetailsDivider.visibility = View.VISIBLE
                            tvOwnerDetailsHeader.visibility = View.VISIBLE
                            tvOccupancyDivider.visibility = View.VISIBLE
                            tvOccupancyHeader.visibility = View.VISIBLE

                            tvOwnerNameCaption.visibility = View.VISIBLE
                            tvFatherHusbandNameCaption.visibility = View.VISIBLE
                            tvGenderCaption.visibility = View.VISIBLE
                            tvCnicCaption.visibility = View.VISIBLE
                            tvMobileCaption.visibility = View.VISIBLE
                            tvRespondentStatusCaption.visibility = View.VISIBLE

                            tvOwnerName.visibility = View.VISIBLE
                            tvFatherHusbandName.visibility = View.VISIBLE
                            tvGender.visibility = View.VISIBLE
                            tvCnic.visibility = View.VISIBLE
                            tvMobile.visibility = View.VISIBLE
                            tvRespondentStatusCaption.visibility = View.VISIBLE

                            tvOwnerName.text = survey.name
                            tvFatherHusbandName.text = survey.fatherName
                            tvGender.text = survey.gender
                            tvCnic.text = survey.cnic
                            tvMobile.text = survey.mobile
                            tvRespondentStatus.text = when (survey.mobileSource) {
                                "Other" -> "${survey.mobileSource} (${survey.mobileOtherSource})"
                                else -> survey.mobileSource
                            }
                        }

                        else -> {

                            tvOwnerDetailsDivider.visibility = View.GONE
                            tvOwnerDetailsHeader.visibility = View.GONE
                            tvOccupancyDivider.visibility = View.GONE
                            tvOccupancyHeader.visibility = View.GONE

                            tvOwnerNameCaption.visibility = View.GONE
                            tvFatherHusbandNameCaption.visibility = View.GONE
                            tvGenderCaption.visibility = View.GONE
                            tvCnicCaption.visibility = View.GONE
                            tvMobileCaption.visibility = View.GONE
                            tvRespondentStatusCaption.visibility = View.GONE

                            tvOwnerName.visibility = View.GONE
                            tvFatherHusbandName.visibility = View.GONE
                            tvGender.visibility = View.GONE
                            tvCnic.visibility = View.GONE
                            tvMobile.visibility = View.GONE
                            tvRespondentStatus.visibility = View.GONE

                        }
                    }

                    // Create Floors
                    val floorsList = ArrayList<Floors>()
                    if (survey.floorsList != "") {
                        val jsonObject1 = JSONObject(survey.floorsList)
                        val jsonArray1 = jsonObject1.getJSONArray("floors")

                        for (i in 0 until jsonArray1.length()) {

                            val floors = Floors()

                            val jsonObjectFloor = jsonArray1.getJSONObject(i)
                            val floorNumber = jsonObjectFloor.getInt("floor_number")
                            val partitions = jsonObjectFloor.getJSONArray("partitions")

                            val partitionList = ArrayList<Partition>()

                            for (j in 0 until partitions.length()) {
                                val partitionObject = partitions.getJSONObject(j)

                                val partition = Partition(
                                    PartitionNumber = partitionObject.getInt("partition_number"),
                                    Landuse = partitionObject.getString("landuse"),
                                    CommercialActivity = partitionObject.getString("commercial_activity"),
                                    Occupancy = partitionObject.getString("occupancy"),
                                    TenantName = partitionObject.getString("tenant_name"),
                                    TenantFatherName = partitionObject.getString("tenant_father_name"),
                                    TenantCnic = partitionObject.getString("tenant_cnic"),
                                    TenantMobile = partitionObject.getString("tenant_mobile"),
                                )

                                partitionList.add(partition)
                            }
                            floors.FloorNumber = floorNumber
                            floors.Partitions = partitionList

                            floorsList.add(floors)
                        }
                    }

                    val floorAdapter = FloorAdapter()
                    recyclerViewFloors.layoutManager = LinearLayoutManager(itemView.context)
                    recyclerViewFloors.adapter = floorAdapter
                    floorAdapter.submitList(floorsList)
                }

                tvRemarks.text = when (survey.remarks) {
                    "" -> "Nil"
                    else -> survey.remarks
                }

                btnUploadRecord.setOnClickListener {
                    val position = adapterPosition
                    if (position != RecyclerView.NO_POSITION) {
                        val item = getItem(position)
                        listener.onViewImagesClicked(item)
                    }
                }
            }
        }
    }

    private class SurveyDiffCallback : DiffUtil.ItemCallback<SurveyFormEntity>() {
        override fun areItemsTheSame(
            oldItem: SurveyFormEntity,
            newItem: SurveyFormEntity
        ): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(
            oldItem: SurveyFormEntity,
            newItem: SurveyFormEntity
        ): Boolean {
            return oldItem == newItem
        }
    }
}
