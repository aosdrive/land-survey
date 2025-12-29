package pk.gop.pulse.katchiAbadi.ui.activities

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.textfield.TextInputEditText
import pk.gop.pulse.katchiAbadi.R
import pk.gop.pulse.katchiAbadi.databinding.ActivityDroneSprayBinding

class DroneSprayActivity : AppCompatActivity() {

    private lateinit var binding:ActivityDroneSprayBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding=ActivityDroneSprayBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Setup ActionBar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.title = "Drone Spray Survey"

        // Setup spray section
        setupSpraySection()
    }

    private fun setupSpraySection() {
        // Setup Spray Provider Spinner
        val sprayProviders = arrayOf(
            "Select Provider",
            "Owner",
            "Zaraat Dost"
        )

        val providerAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            sprayProviders
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        binding.spinnerSprayProvider.adapter = providerAdapter

        // Setup Measurement Unit Spinner
        val measurementUnits = arrayOf(
            "Select Unit",
            "Bottles",
            "Gallons",
            "Liters",
            "Kilograms",
            "Grams",
            "Milliliters",
            "Packets"
        )

        val unitAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            measurementUnits
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        binding.spinnerMeasurementUnit.adapter = unitAdapter

        // Handle Spray Provider Selection
        binding.spinnerSprayProvider.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                when (position) {
                    0 -> {
                        // "Select Provider" - Hide details
                        binding.layoutZaraatDostDetails.visibility = View.GONE
                        clearZaraatDostFields()
                    }
                    1 -> {
                        // "Owner" - Hide details
                        binding.layoutZaraatDostDetails.visibility = View.GONE
                        clearZaraatDostFields()
                    }
                    2 -> {
                        // "Zaraat Dost" - Show details
                        binding.layoutZaraatDostDetails.visibility = View.VISIBLE
                    }
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                binding.layoutZaraatDostDetails.visibility = View.GONE
            }
        }
    }

    private fun clearZaraatDostFields() {
        binding.etSprayName.text?.clear()
        binding.etSprayQuantity.text?.clear()
        binding.etSprayNotes.text?.clear()
        binding.spinnerMeasurementUnit.setSelection(0)
    }

    private fun validateSpraySection(): Boolean {
        val selectedProvider = binding.spinnerSprayProvider.selectedItemPosition

        // If no provider selected
        if (selectedProvider == 0) {
            Toast.makeText(this, "Please select spray provider", Toast.LENGTH_SHORT).show()
            return false
        }

        // If Zaraat Dost is selected, validate required fields
        if (selectedProvider == 2) { // Zaraat Dost
            val sprayName = binding.etSprayName.text.toString().trim()
            val sprayQuantity = binding.etSprayQuantity.text.toString().trim()
            val measurementUnit = binding.spinnerMeasurementUnit.selectedItemPosition

            when {
                sprayName.isEmpty() -> {
                    binding.etSprayName.error = "Spray name is required"
                    binding.etSprayName.requestFocus()
                    return false
                }
                sprayQuantity.isEmpty() -> {
                    binding.etSprayQuantity.error = "Quantity is required"
                    binding.etSprayQuantity.requestFocus()
                    return false
                }
                measurementUnit == 0 -> {
                    Toast.makeText(this, "Please select measurement unit", Toast.LENGTH_SHORT).show()
                    return false
                }
            }
        }

        return true
    }

    private fun getSprayData(): SprayData {
        val provider = when (binding.spinnerSprayProvider.selectedItemPosition) {
            1 -> "Owner"
            2 -> "Zaraat Dost"
            else -> ""
        }

        return if (provider == "Zaraat Dost") {
            SprayData(
                provider = provider,
                sprayName = binding.etSprayName.text.toString().trim(),
                quantity = binding.etSprayQuantity.text.toString().trim(),
                measurementUnit = binding.spinnerMeasurementUnit.selectedItem.toString(),
                notes = binding.etSprayNotes.text.toString().trim()
            )
        } else {
            SprayData(
                provider = provider,
                sprayName = "",
                quantity = "",
                measurementUnit = "",
                notes = ""
            )
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}

// Data class to hold spray information
data class SprayData(
    val provider: String,
    val sprayName: String,
    val quantity: String,
    val measurementUnit: String,
    val notes: String
)




