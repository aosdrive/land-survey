package pk.gop.pulse.katchiAbadi.data.local

import android.content.Context
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import androidx.core.widget.doAfterTextChanged
import pk.gop.pulse.katchiAbadi.databinding.ItemPersonEntryBinding
import pk.gop.pulse.katchiAbadi.domain.model.SurveyPersonEntity

class PersonEntryHelper(
    private val context: Context,
    private val container: LinearLayout
) {

    private val personViews = mutableListOf<ItemPersonEntryBinding>()

    private val genderOptions = listOf("Male", "Female", "Other")
    private val ownershipOptions = listOf("Owner", "Leasee", "Other")
    private val relationOptions = listOf("Father", "Husband", "Other")

    fun addPersonView(data: SurveyPersonEntity?, editable: Boolean) {
        val binding = ItemPersonEntryBinding.inflate(LayoutInflater.from(context), container, false)

        // Set existing values
        binding.etFirstName.setText(data?.firstName.orEmpty())
        binding.etLastName.setText(data?.lastName.orEmpty())
        binding.etMobile.setText(data?.mobile.orEmpty())
        binding.etNic.setText(data?.nic.orEmpty())
//        binding.etPersonArea.setText(data?.personArea.orEmpty())
        binding.etReligion.setText(data?.religion.orEmpty())

        // New fields
        binding.etGrowerCode.setText(data?.growerCode.orEmpty())
        binding.etExtra1.setText(data?.extra1.orEmpty())
        binding.etExtra2.setText(data?.extra2.orEmpty())
        binding.etMauzaId.setText(data?.mauzaId?.toString().orEmpty())
        binding.etMauzaName.setText(data?.mauzaName.orEmpty())

        // --- Grower Code Format with Auto-Formatting ---
        setupGrowerCodeFormatting(binding)

        // --- CNIC Validation: 13 digits only ---
        binding.etNic.filters = arrayOf(InputFilter.LengthFilter(13))
        binding.etNic.doAfterTextChanged { text ->
            if (text?.length != 13 || !text.all { it.isDigit() }) {
                binding.etNic.error = "Enter valid 13-digit CNIC"
            } else {
                binding.etNic.error = null
            }
        }

        // --- Mobile Number Validation: 11 digits only ---
        binding.etMobile.filters = arrayOf(InputFilter.LengthFilter(11))
        binding.etMobile.doAfterTextChanged { text ->
            if (text?.length != 11 || !text.all { it.isDigit() }) {
                binding.etMobile.error = "Enter valid 11-digit mobile number"
            } else {
                binding.etMobile.error = null
            }
        }

        // Spinners setup
        val relationAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, relationOptions)
        binding.spinnerRelation.adapter = relationAdapter
        binding.spinnerRelation.setSelection(relationOptions.indexOf(data?.relation).takeIf { it >= 0 } ?: 0)

        val genderAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, genderOptions)
        binding.spinnerGender.adapter = genderAdapter
        binding.spinnerGender.setSelection(genderOptions.indexOf(data?.gender).takeIf { it >= 0 } ?: 0)

        val ownershipAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, ownershipOptions)
        binding.spinnerOwnership.adapter = ownershipAdapter
        binding.spinnerOwnership.setSelection(ownershipOptions.indexOf(data?.ownershipType).takeIf { it >= 0 } ?: 0)

        // Enable/disable all fields
        listOf(
            binding.etFirstName, binding.etLastName, binding.etMobile, binding.etNic,
            binding.etGrowerCode, binding.etExtra1, binding.etExtra2,
            binding.etMauzaId, binding.etMauzaName,
            binding.spinnerRelation, binding.spinnerGender, binding.spinnerOwnership
        ).forEach { it.isEnabled = editable }

        // Remove button
        binding.btnRemove.isEnabled = editable
        binding.btnRemove.setOnClickListener {
            container.removeView(binding.root)
            personViews.remove(binding)
        }

        // Add view to container
        container.addView(binding.root)
        personViews.add(binding)
    }

    private fun setupGrowerCodeFormatting(binding: ItemPersonEntryBinding) {
        // Pattern with dashes: Only numbers allowed
        val growerCodePattern = Regex("""^\d{2}-(\d{2}-\d{5}|\d{5})$""")

        binding.etGrowerCode.filters = arrayOf(
            InputFilter.LengthFilter(11), // Maximum: 12-34-56789
            InputFilter { source, start, end, dest, dstart, dend ->
                // Allow ONLY numbers and dashes
                val filtered = source.subSequence(start, end).filter {
                    it.isDigit() || it == '-'
                }
                if (filtered.length != end - start) {
                    filtered
                } else {
                    null
                }
            }
        )

        binding.etGrowerCode.doAfterTextChanged { text ->
            val input = text?.toString()?.trim().orEmpty()

            when {
                input.isEmpty() -> binding.etGrowerCode.error = null
                input.length < 8 -> binding.etGrowerCode.error = null // Still typing
                !growerCodePattern.matches(input) -> {
                    binding.etGrowerCode.error = "Format: 12-34-56789 or 12-56789"
                }
                else -> binding.etGrowerCode.error = null
            }
        }
    }


    fun getAllPersons(): List<SurveyPersonEntity> {
        return personViews.map { binding ->
            SurveyPersonEntity(
                surveyId = binding.etSurveyId?.text?.toString()?.toLongOrNull() ?: 0L,
                ownershipType = binding.spinnerOwnership.selectedItem?.toString().orEmpty(),
                personId = binding.etPersonId.text.toString().toLongOrNull() ?: 0L,

                firstName = binding.etFirstName.text.toString().trim(),
                lastName = binding.etLastName.text.toString().trim(),
                gender = binding.spinnerGender.selectedItem?.toString().orEmpty(),

                relation = binding.spinnerRelation.selectedItem?.toString().orEmpty(),
                religion = binding.etReligion.text.toString().trim(),
                mobile = binding.etMobile.text.toString().trim(),
                nic = binding.etNic.text.toString().trim(),

                growerCode = binding.etGrowerCode?.text?.toString()?.trim()?.uppercase().orEmpty(),
//                personArea = binding.etPersonArea.text.toString().trim(),

                extra1 = binding.etExtra1?.text?.toString()?.trim().orEmpty(),
                extra2 = binding.etExtra2?.text?.toString()?.trim().orEmpty(),

                mauzaId = binding.etMauzaId?.text?.toString()?.toLongOrNull() ?: 0L,
                mauzaName = binding.etMauzaName?.text?.toString()?.trim().orEmpty()
            )
        }
    }

    fun getPersonsByMauzaId(mauzaId: Long): List<SurveyPersonEntity> {
        return personViews.mapNotNull { binding ->
            val currentMauzaId = binding.etMauzaId?.text?.toString()?.toLongOrNull() ?: return@mapNotNull null
            if (currentMauzaId != mauzaId) return@mapNotNull null

            SurveyPersonEntity(
                surveyId = binding.etSurveyId?.text?.toString()?.toLongOrNull() ?: 0L,
                ownershipType = binding.spinnerOwnership.selectedItem?.toString().orEmpty(),
                personId = binding.etPersonId?.text?.toString()?.toLongOrNull() ?: 0L,

                firstName = binding.etFirstName.text.toString().trim(),
                lastName = binding.etLastName.text.toString().trim(),
                gender = binding.spinnerGender.selectedItem?.toString().orEmpty(),

                relation = binding.spinnerRelation.selectedItem?.toString().orEmpty(),
                religion = binding.etReligion.text.toString().trim(),
                mobile = binding.etMobile.text.toString().trim(),
                nic = binding.etNic.text.toString().trim(),

                growerCode = binding.etGrowerCode?.text?.toString()?.trim().orEmpty(),
//                personArea = binding.etPersonArea.text.toString().trim(),

                extra1 = binding.etExtra1?.text?.toString()?.trim().orEmpty(),
                extra2 = binding.etExtra2?.text?.toString()?.trim().orEmpty(),

                mauzaId = currentMauzaId,
                mauzaName = binding.etMauzaName?.text?.toString()?.trim().orEmpty()
            )
        }
    }
}