package pk.gop.pulse.katchiAbadi.data.local

import android.content.Context
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import com.google.android.material.bottomsheet.BottomSheetDialog
import pk.gop.pulse.katchiAbadi.R
import pk.gop.pulse.katchiAbadi.databinding.ItemPersonEntryBinding
import pk.gop.pulse.katchiAbadi.databinding.ItemPersonSummaryBinding
import pk.gop.pulse.katchiAbadi.domain.model.SurveyPersonEntity

class PersonEntryHelper(
    private val context: Context,
    private val container: LinearLayout
) {

    private data class PersonSlot(
        val form: ItemPersonEntryBinding,
        val summary: ItemPersonSummaryBinding
    )

    private val slots = mutableListOf<PersonSlot>()

    private val genderOptions = listOf("Male", "Female", "Other")
    private val ownershipOptions = listOf("Owner", "Leasee", "Other")
    private val relationOptions = listOf("Father", "Husband", "Other")

    // =====================================================================
    //  PUBLIC: pehle jaisa naam, magar ab bottom sheet kholta hai
    // =====================================================================
    fun addPersonView(data: SurveyPersonEntity?, editable: Boolean) {
        val form = buildForm(data, editable)
        val summary = ItemPersonSummaryBinding.inflate(
            LayoutInflater.from(context), container, false
        )

        val slot = PersonSlot(form, summary)

        summary.btnEditPerson.setOnClickListener { showSheet(slot, editable) }
        summary.llPersonRow.setOnClickListener { showSheet(slot, editable) }
        summary.btnDeletePerson.setOnClickListener { removeSlot(slot) }
        summary.btnDeletePerson.isEnabled = editable

        container.addView(summary.root)
        slots.add(slot)

        refreshSummary(slot)

        // Naya person: form foran khol dein taake surveyor bhar sake
        showSheet(slot, editable)
    }

    // =====================================================================
    //  BOTTOM SHEET
    // =====================================================================
    private fun showSheet(slot: PersonSlot, editable: Boolean) {
        val sheet = BottomSheetDialog(context, R.style.ZdBottomSheet)

        // Form view kisi purane parent se juda ho sakta hai - pehle alag karein
        (slot.form.root.parent as? android.view.ViewGroup)?.removeView(slot.form.root)
        sheet.setContentView(slot.form.root)

        // Sheet poori khuli aaye, aadhi nahi
        sheet.behavior.skipCollapsed = true
        sheet.behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED

        slot.form.btnSavePerson.setOnClickListener {
            if (validate(slot.form)) {
                refreshSummary(slot)
                sheet.dismiss()
            }
        }

        slot.form.btnRemove.isEnabled = editable
        slot.form.btnRemove.setOnClickListener {
            sheet.dismiss()
            removeSlot(slot)
        }

        // Sheet swipe se band ho to bhi summary update ho jaye
        sheet.setOnDismissListener { refreshSummary(slot) }

        sheet.show()
    }

    private fun validate(form: ItemPersonEntryBinding): Boolean {
        val first = form.etFirstName.text?.toString()?.trim().orEmpty()
        if (first.isEmpty()) {
            form.etFirstName.error = "First name required"
            form.etFirstName.requestFocus()
            return false
        }
        form.etFirstName.error = null
        return true
    }

    private fun removeSlot(slot: PersonSlot) {
        container.removeView(slot.summary.root)
        slots.remove(slot)
    }

    /** Summary row ko form ki mojooda values se bhar dein. */
    private fun refreshSummary(slot: PersonSlot) {
        val f = slot.form
        val s = slot.summary

        val first = f.etFirstName.text?.toString()?.trim().orEmpty()
        val last = f.etLastName.text?.toString()?.trim().orEmpty()
        val fullName = listOf(first, last).filter { it.isNotEmpty() }.joinToString(" ")

        s.tvPersonName.text = fullName.ifEmpty { "Unnamed person" }

        s.tvPersonInitials.text = buildString {
            first.firstOrNull()?.let { append(it.uppercaseChar()) }
            last.firstOrNull()?.let { append(it.uppercaseChar()) }
        }.ifEmpty { "?" }

        val ownership = f.spinnerOwnership.selectedItem?.toString().orEmpty()
        val cnic = f.etNic.text?.toString()?.trim().orEmpty()
        s.tvPersonMeta.text = listOf(ownership, cnic)
            .filter { it.isNotEmpty() }
            .joinToString(" · ")
            .ifEmpty { "Tap to add details" }

        val grower = f.etGrowerCode.text?.toString()?.trim().orEmpty()
        if (grower.isEmpty()) {
            s.llGrowerChip.visibility = android.view.View.GONE
        } else {
            s.llGrowerChip.visibility = android.view.View.VISIBLE
            s.tvPersonGrower.text = grower
        }
    }

    // =====================================================================
    //  FORM BUILD  (pehle wala poora logic, waise hi)
    // =====================================================================
    private fun buildForm(
        data: SurveyPersonEntity?,
        editable: Boolean
    ): ItemPersonEntryBinding {
        val binding = ItemPersonEntryBinding.inflate(LayoutInflater.from(context), null, false)

        binding.etFirstName.setText(data?.firstName.orEmpty())
        binding.etLastName.setText(data?.lastName.orEmpty())
        binding.etMobile.setText(data?.mobile.orEmpty())
        binding.etNic.setText(data?.nic.orEmpty())
        binding.etReligion.setText(data?.religion.orEmpty())

        binding.etGrowerCode.setText(data?.growerCode.orEmpty())
        binding.etExtra1.setText(data?.extra1.orEmpty())
        binding.etExtra2.setText(data?.extra2.orEmpty())
        binding.etMauzaId.setText(data?.mauzaId?.toString().orEmpty())
        binding.etMauzaName.setText(data?.mauzaName.orEmpty())
        binding.etAddress.setText(data?.address.orEmpty())
        binding.etAddress.isEnabled = editable

        setupGrowerCodeFormatting(binding)
        setupCnicFormatting(binding)

        // --- Mobile: PK format 923XXXXXXXXX ---
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
                    digits = "92$digits"
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

        // --- Spinners: ZD layouts (mauza dialog jaisi list) ---
        binding.spinnerRelation.adapter = zdAdapter(relationOptions)
        binding.spinnerRelation.setSelection(
            relationOptions.indexOf(data?.relation).takeIf { it >= 0 } ?: 0
        )

        binding.spinnerGender.adapter = zdAdapter(genderOptions)
        binding.spinnerGender.setSelection(
            genderOptions.indexOf(data?.gender).takeIf { it >= 0 } ?: 0
        )

        binding.spinnerOwnership.adapter = zdAdapter(ownershipOptions)
        binding.spinnerOwnership.setSelection(
            ownershipOptions.indexOf(data?.ownershipType).takeIf { it >= 0 } ?: 0
        )

        listOf(
            binding.etFirstName, binding.etLastName, binding.etMobile, binding.etNic,
            binding.etAddress, binding.etExtra1, binding.etExtra2,
            binding.etMauzaId, binding.etMauzaName,
            binding.spinnerRelation, binding.spinnerGender, binding.spinnerOwnership
        ).forEach { it.isEnabled = editable }

        // Grower Code hamesha readonly
        binding.etGrowerCode.isEnabled = false
        binding.etGrowerCode.isFocusable = false
        binding.etGrowerCode.isFocusableInTouchMode = false
        binding.etGrowerCode.isCursorVisible = false
        binding.etGrowerCode.keyListener = null

        return binding
    }

    private fun zdAdapter(items: List<String>): ArrayAdapter<String> {
        val adapter = ArrayAdapter(context, R.layout.spinner_item_drop_down, items)
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        return adapter
    }

    private fun setupGrowerCodeFormatting(binding: ItemPersonEntryBinding) {
        val growerCodePattern = Regex("""^\d{2}-\d{2}-\d{5}$""")
        binding.etGrowerCode.filters = arrayOf(InputFilter.LengthFilter(11))

        var isFormatting = false
        binding.etGrowerCode.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (isFormatting || s == null) return
                isFormatting = true

                val digits = s.toString().filter { it.isDigit() }.take(9)

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
                    formatted.length < 11 -> binding.etGrowerCode.error = null
                    !growerCodePattern.matches(formatted) ->
                        binding.etGrowerCode.error = "Format: 11-01-00001"

                    else -> binding.etGrowerCode.error = null
                }

                isFormatting = false
            }
        })
    }

    private fun setupCnicFormatting(binding: ItemPersonEntryBinding) {
        val cnicPattern = Regex("""^\d{5}-\d{7}-\d{1}$""")
        binding.etNic.filters = arrayOf(InputFilter.LengthFilter(15))

        var isFormatting = false
        binding.etNic.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (isFormatting || s == null) return
                isFormatting = true

                val digits = s.toString().filter { it.isDigit() }.take(13)

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

                when {
                    formatted.isEmpty() -> binding.etNic.error = null
                    formatted.length < 15 -> binding.etNic.error = null
                    !cnicPattern.matches(formatted) ->
                        binding.etNic.error = "Format: 12345-1234567-1"

                    else -> binding.etNic.error = null
                }

                isFormatting = false
            }
        })
    }

    // =====================================================================
    //  READ BACK  (pehle jaisa - forms se hi parhta hai)
    // =====================================================================
    fun getAllPersons(): List<SurveyPersonEntity> {
        return slots.map { slot -> toEntity(slot.form) }
    }

    fun getPersonsByMauzaId(mauzaId: Long): List<SurveyPersonEntity> {
        return slots.mapNotNull { slot ->
            val currentMauzaId = slot.form.etMauzaId.text?.toString()?.toLongOrNull()
                ?: return@mapNotNull null
            if (currentMauzaId != mauzaId) return@mapNotNull null
            toEntity(slot.form)
        }
    }

    private fun toEntity(b: ItemPersonEntryBinding) = SurveyPersonEntity(
        surveyId = b.etSurveyId.text?.toString()?.toLongOrNull() ?: 0L,
        ownershipType = b.spinnerOwnership.selectedItem?.toString().orEmpty(),
        personId = b.etPersonId.text?.toString()?.toLongOrNull() ?: 0L,

        firstName = b.etFirstName.text.toString().trim(),
        lastName = b.etLastName.text.toString().trim(),
        gender = b.spinnerGender.selectedItem?.toString().orEmpty(),

        relation = b.spinnerRelation.selectedItem?.toString().orEmpty(),
        religion = b.etReligion.text.toString().trim(),
        mobile = b.etMobile.text.toString().trim(),
        nic = b.etNic.text.toString().trim(),

        growerCode = b.etGrowerCode.text?.toString()?.trim().orEmpty(),
        extra1 = b.etExtra1.text?.toString()?.trim().orEmpty(),
        extra2 = b.etExtra2.text?.toString()?.trim().orEmpty(),

        mauzaId = b.etMauzaId.text?.toString()?.toLongOrNull() ?: 0L,
        mauzaName = b.etMauzaName.text?.toString()?.trim().orEmpty(),
        address = b.etAddress.text?.toString()?.trim().orEmpty()
    )
}