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
        binding.etReligion.setText(data?.religion.orEmpty())

        // New fields
        binding.etGrowerCode.setText(data?.growerCode.orEmpty())
        binding.etExtra1.setText(data?.extra1.orEmpty())
        binding.etExtra2.setText(data?.extra2.orEmpty())
        binding.etMauzaId.setText(data?.mauzaId?.toString().orEmpty())
        binding.etMauzaName.setText(data?.mauzaName.orEmpty())
        binding.etAddress.setText(data?.address.orEmpty())
        binding.etAddress.isEnabled = editable

        // --- Grower Code: strict 12-34-56789 format with auto-dashes ---
        setupGrowerCodeFormatting(binding)

        // --- CNIC: XXXXX-XXXXXXX-X (5-7-1) with auto-dashes ---
        setupCnicFormatting(binding)

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
            binding.etFirstName, binding.etLastName, binding.etMobile, binding.etNic, binding.etAddress,
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
        // Strict format: 12-34-56789 (2 digits - 2 digits - 5 digits)
        val growerCodePattern = Regex("""^\d{2}-\d{2}-\d{5}$""")

        // Max length: "12-34-56789" = 11 chars
        binding.etGrowerCode.filters = arrayOf(InputFilter.LengthFilter(11))

        var isFormatting = false
        binding.etGrowerCode.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (isFormatting || s == null) return
                isFormatting = true

                // Strip everything except digits
                val digits = s.toString().filter { it.isDigit() }.take(9)

                // Rebuild with dashes: 12-34-56789
                val formatted = buildString {
                    for (i in digits.indices) {
                        if (i == 2 || i == 4) append('-')
                        append(digits[i])
                    }
                }

                if (formatted != s.toString()) {
                    binding.etGrowerCode.setText(formatted)
                    binding.etGrowerCode.setSelection(formatted.length)
                }

                // Validate
                when {
                    formatted.isEmpty() -> binding.etGrowerCode.error = null
                    formatted.length < 11 -> binding.etGrowerCode.error = null // still typing
                    !growerCodePattern.matches(formatted) ->
                        binding.etGrowerCode.error = "Format: 12-34-56789"
                    else -> binding.etGrowerCode.error = null
                }

                isFormatting = false
            }
        })
    }

    private fun setupCnicFormatting(binding: ItemPersonEntryBinding) {
        // CNIC format: XXXXX-XXXXXXX-X (5-7-1 = 13 digits, total 15 chars with dashes)
        val cnicPattern = Regex("""^\d{5}-\d{7}-\d{1}$""")

        binding.etNic.filters = arrayOf(InputFilter.LengthFilter(15))

        var isFormatting = false
        binding.etNic.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (isFormatting || s == null) return
                isFormatting = true

                // Strip everything except digits, max 13
                val digits = s.toString().filter { it.isDigit() }.take(13)

                // Rebuild with dashes: 12345-1234567-1
                val formatted = buildString {
                    for (i in digits.indices) {
                        if (i == 5 || i == 12) append('-')
                        append(digits[i])
                    }
                }

                if (formatted != s.toString()) {
                    binding.etNic.setText(formatted)
                    binding.etNic.setSelection(formatted.length)
                }

                // Validate
                when {
                    formatted.isEmpty() -> binding.etNic.error = null
                    formatted.length < 15 -> binding.etNic.error = null // still typing
                    !cnicPattern.matches(formatted) ->
                        binding.etNic.error = "Format: 12345-1234567-1"
                    else -> binding.etNic.error = null
                }

                isFormatting = false
            }
        })
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

                extra1 = binding.etExtra1?.text?.toString()?.trim().orEmpty(),
                extra2 = binding.etExtra2?.text?.toString()?.trim().orEmpty(),

                mauzaId = binding.etMauzaId?.text?.toString()?.toLongOrNull() ?: 0L,
                mauzaName = binding.etMauzaName?.text?.toString()?.trim().orEmpty(),
                address = binding.etAddress?.text?.toString()?.trim().orEmpty(),
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

                extra1 = binding.etExtra1?.text?.toString()?.trim().orEmpty(),
                extra2 = binding.etExtra2?.text?.toString()?.trim().orEmpty(),

                mauzaId = currentMauzaId,
                mauzaName = binding.etMauzaName?.text?.toString()?.trim().orEmpty(),
                address = binding.etAddress?.text?.toString()?.trim().orEmpty(),
            )
        }
    }
}