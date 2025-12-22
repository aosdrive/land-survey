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

class DroneSprayActivity : AppCompatActivity() {

    private lateinit var spinnerSprayProvider: Spinner
    private lateinit var spinnerMeasurementUnit: Spinner
    private lateinit var layoutZaraatDostDetails: LinearLayout
    private lateinit var etSprayName: TextInputEditText
    private lateinit var etSprayQuantity: TextInputEditText
    private lateinit var etSprayNotes: TextInputEditText
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_drone_spray)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Setup ActionBar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.title = "Drone Spray Survey"


        // Initialize views
        initializeViews()

        // Setup spray section
        setupSpraySection()
    }

    private fun initializeViews() {
        spinnerSprayProvider = findViewById(R.id.spinnerSprayProvider)
        spinnerMeasurementUnit = findViewById(R.id.spinnerMeasurementUnit)
        layoutZaraatDostDetails = findViewById(R.id.layoutZaraatDostDetails)
        etSprayName = findViewById(R.id.etSprayName)
        etSprayQuantity = findViewById(R.id.etSprayQuantity)
        etSprayNotes = findViewById(R.id.etSprayNotes)
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

        spinnerSprayProvider.adapter = providerAdapter

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

        spinnerMeasurementUnit.adapter = unitAdapter

        // Handle Spray Provider Selection
        spinnerSprayProvider.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                when (position) {
                    0 -> {
                        // "Select Provider" - Hide details
                        layoutZaraatDostDetails.visibility = View.GONE
                        clearZaraatDostFields()
                    }
                    1 -> {
                        // "Owner" - Hide details
                        layoutZaraatDostDetails.visibility = View.GONE
                        clearZaraatDostFields()
                    }
                    2 -> {
                        // "Zaraat Dost" - Show details
                        layoutZaraatDostDetails.visibility = View.VISIBLE
                    }
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                layoutZaraatDostDetails.visibility = View.GONE
            }
        }
    }

    private fun clearZaraatDostFields() {
        etSprayName.text?.clear()
        etSprayQuantity.text?.clear()
        etSprayNotes.text?.clear()
        spinnerMeasurementUnit.setSelection(0)
    }

    private fun validateSpraySection(): Boolean {
        val selectedProvider = spinnerSprayProvider.selectedItemPosition

        // If no provider selected
        if (selectedProvider == 0) {
            Toast.makeText(this, "Please select spray provider", Toast.LENGTH_SHORT).show()
            return false
        }

        // If Zaraat Dost is selected, validate required fields
        if (selectedProvider == 2) { // Zaraat Dost
            val sprayName = etSprayName.text.toString().trim()
            val sprayQuantity = etSprayQuantity.text.toString().trim()
            val measurementUnit = spinnerMeasurementUnit.selectedItemPosition

            when {
                sprayName.isEmpty() -> {
                    etSprayName.error = "Spray name is required"
                    etSprayName.requestFocus()
                    return false
                }
                sprayQuantity.isEmpty() -> {
                    etSprayQuantity.error = "Quantity is required"
                    etSprayQuantity.requestFocus()
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
        val provider = when (spinnerSprayProvider.selectedItemPosition) {
            1 -> "Owner"
            2 -> "Zaraat Dost"
            else -> ""
        }

        return if (provider == "Zaraat Dost") {
            SprayData(
                provider = provider,
                sprayName = etSprayName.text.toString().trim(),
                quantity = etSprayQuantity.text.toString().trim(),
                measurementUnit = spinnerMeasurementUnit.selectedItem.toString(),
                notes = etSprayNotes.text.toString().trim()
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




