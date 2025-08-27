// OwnerSelectionDialog.kt
//package com.example.app.ui.survey


import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Window
import android.widget.ArrayAdapter
import android.widget.ListView
import pk.gop.pulse.katchiAbadi.databinding.DialogSelectOwnerBinding
import pk.gop.pulse.katchiAbadi.domain.model.SurveyPersonEntity
//
//class OwnerSelectionDialog(
//    context: Context,
//    private val owners: List<SurveyPersonEntity>,
//    private val onOwnerSelected: (SurveyPersonEntity) -> Unit
//) : Dialog(context) {
//
//    private lateinit var binding: DialogSelectOwnerBinding
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//
//        requestWindowFeature(Window.FEATURE_NO_TITLE)
//        binding = DialogSelectOwnerBinding.inflate(LayoutInflater.from(context))
//        setContentView(binding.root)
//
//        val ownerDescriptions = owners.map {
//            "${it.firstName} ${it.lastName} - ${it.nic} (${it.ownershipType})"
//        }
//
//        val adapter = ArrayAdapter(context, android.R.layout.simple_list_item_1, ownerDescriptions)
//        binding.listOwners.adapter = adapter
//
//        binding.listOwners.setOnItemClickListener { _, _, position, _ ->
//            onOwnerSelected(owners[position])
//            dismiss()
//        }
//
//        binding.btnCancel.setOnClickListener {
//            dismiss()
//        }
//    }
//}

class OwnerSelectionDialog(
    context: Context,
    private val owners: List<SurveyPersonEntity>,
    private val onOwnerSelected: (SurveyPersonEntity) -> Unit
) : Dialog(context) {

    private lateinit var binding: DialogSelectOwnerBinding
    private lateinit var adapter: ArrayAdapter<String>
    private var filteredOwners: MutableList<SurveyPersonEntity> = owners.toMutableList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestWindowFeature(Window.FEATURE_NO_TITLE)
        binding = DialogSelectOwnerBinding.inflate(LayoutInflater.from(context))
        setContentView(binding.root)

        adapter = ArrayAdapter(
            context,
            android.R.layout.simple_list_item_1,
            filteredOwners.map {
                "${it.firstName} ${it.lastName} - ${it.nic} - GC - ${it.growerCode} (${it.ownershipType})"
            }
        )

        binding.listOwners.adapter = adapter

        binding.searchOwner.setOnQueryTextListener(object : android.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false

            override fun onQueryTextChange(newText: String?): Boolean {
                filterOwners(newText ?: "")
                return true
            }
        })

        binding.listOwners.setOnItemClickListener { _, _, position, _ ->
            onOwnerSelected(filteredOwners[position])
            dismiss()
        }

        binding.btnCancel.setOnClickListener {
            dismiss()
        }
    }

    private fun filterOwners(query: String) {
        filteredOwners = owners.filter {
            val fullName = "${it.firstName} ${it.lastName}".lowercase()
            val nic = it.nic.lowercase()
            val ownership = it.ownershipType.lowercase()
            val grcode = it.growerCode.lowercase()
            fullName.contains(query.lowercase()) ||
                    nic.contains(query.lowercase()) ||grcode.contains(query.lowercase()) ||
                    ownership.contains(query.lowercase())
        }.toMutableList()

        adapter.clear()
        adapter.addAll(
            filteredOwners.map {
                "${it.firstName} ${it.lastName} - ${it.nic}-GC- ${it.growerCode} (${it.ownershipType})"
            }
        )
        adapter.notifyDataSetChanged()
    }
}
