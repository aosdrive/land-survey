package pk.gop.pulse.katchiAbadi.data.local

import android.content.Context
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
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

        // --- Mobile Number: PK format 923XXXXXXXXX (12 digits, starts with 92) ---
        binding.etMobile.inputType = InputType.TYPE_CLASS_NUMBER
        binding.etMobile.filters = arrayOf(InputFilter.LengthFilter(12))

        var isMobileFormatting = false
        binding.etMobile.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (isMobileFormatting || s == null) return
                isMobileFormatting = true

                var digits = s.toString().filter { it.isDigit() }

                if (digits.startsWith("0")) {
                    digits = "92" + digits.drop(1)
                }
                if (!digits.startsWith("92")) {
                    digits = "92" + digits
                }

                digits = digits.take(12)

                if (digits != s.toString()) {
                    binding.etMobile.setText(digits)
                    binding.etMobile.setSelection(digits.length)
                }

                binding.etMobile.error = when {
                    digits.length < 12 -> "Enter full number e.g. 923224345678"
                    !digits.startsWith("92") -> "Must start with 92"
                    else -> null
                }

                isMobileFormatting = false
            }
        })

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
        // ⚠️ NOTE: etGrowerCode ko is list se NIKAAL diya hai — woh hamesha readonly rahega.
        listOf(
            binding.etFirstName, binding.etLastName, binding.etMobile, binding.etNic, binding.etAddress,
            binding.etExtra1, binding.etExtra2,
            binding.etMauzaId, binding.etMauzaName,
            binding.spinnerRelation, binding.spinnerGender, binding.spinnerOwnership
        ).forEach { it.isEnabled = editable }

        // ✅ Grower Code HAMESHA readonly — value dikhao lekin edit na hone do
        binding.etGrowerCode.isEnabled = false
        binding.etGrowerCode.isFocusable = false
        binding.etGrowerCode.isFocusableInTouchMode = false
        binding.etGrowerCode.isCursorVisible = false
        binding.etGrowerCode.keyListener = null   // pakka readonly (typing block)

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
        // Format: 11-01-00001 (2 digits - 2 digits - 5 digits = 9 digits, 11 chars)
        val growerCodePattern = Regex("""^\d{2}-\d{2}-\d{5}$""")

        // "11-01-00001" = 11 chars
        binding.etGrowerCode.filters = arrayOf(InputFilter.LengthFilter(11))

        var isFormatting = false
        binding.etGrowerCode.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (isFormatting || s == null) return
                isFormatting = true

                // 9 digits total (2+2+5)
                val digits = s.toString().filter { it.isDigit() }.take(9)

                // Rebuild with dashes: 11-01-00001 — dash 2 aur 4 ke baad
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

                when {
                    formatted.isEmpty() -> binding.etGrowerCode.error = null
                    formatted.length < 11 -> binding.etGrowerCode.error = null // abhi type kar raha hai
                    !growerCodePattern.matches(formatted) ->
                        binding.etGrowerCode.error = "Format: 11-01-00001"
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

                growerCode = binding.etGrowerCode?.text?.toString()?.trim().orEmpty(),
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