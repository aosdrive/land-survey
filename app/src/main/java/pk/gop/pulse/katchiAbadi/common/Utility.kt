package pk.gop.pulse.katchiAbadi.common

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.esri.arcgisruntime.geometry.GeometryEngine
import com.esri.arcgisruntime.geometry.Point
import com.esri.arcgisruntime.geometry.PointCollection
import com.esri.arcgisruntime.geometry.Polygon
import com.esri.arcgisruntime.geometry.SpatialReference
import pk.gop.pulse.katchiAbadi.R
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.system.exitProcess


class Utility {

    companion object {

        fun distance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val theta = Math.toRadians(lon1 - lon2)
            var dist = Math.sin(Math.toRadians(lat1)) * Math.sin(Math.toRadians(lat2)) +
                    Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.cos(theta)
            dist = Math.acos(dist)
            dist = Math.toDegrees(dist)
            dist *= 60.0 * 1.1515
            dist *= 1.609344
            return dist * 1000
        }

        private lateinit var dialog: AlertDialog

        fun showProgressAlertDialog(mAct: Context, message: String) {

            val builder = AlertDialog.Builder(mAct)
            val inflater = LayoutInflater.from(mAct)
            val dialogView: View =
                inflater.inflate(R.layout.progress_indeterminate_layout, null)
            val tvMessage = dialogView.findViewById<TextView>(R.id.tv_message)
            tvMessage.text = message

            builder.setView(dialogView)
            builder.setCancelable(false) // Prevent dismissing the dialog by tapping outside

            // Create the dialog and set its style
            dialog = builder.create()

            // Show the dialog
            dialog.show()
        }

        fun dismissProgressAlertDialog() {
            try {
                if (dialog.isShowing)
                    dialog.dismiss()
            } catch (e: Exception) {

            }
        }

        fun dialog(activity: Context?, message: String?, title: String?) {
            val alertDialog = AlertDialog.Builder(activity)
            alertDialog.setMessage(message)
            alertDialog.setTitle(title)
            alertDialog.setCancelable(false)
            alertDialog.setPositiveButton("OK", null)
            alertDialog.create()
            alertDialog.show()
        }

        fun exitApplication(title: String?, message: String?, activity: Activity) {
            try {
                val builder = AlertDialog.Builder(activity)
                if (title != null) {
                    builder.setTitle(title)
                }
                builder.setMessage(message)
                builder.setPositiveButton(
                    "OK"
                ) { _, _ -> killApplicationProcess(activity) }
                builder.create()
                builder.show()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        private fun killApplicationProcess(activity: Activity) {
            try {
                activity.finishAffinity()
                Process.killProcess(Process.myPid())
                exitProcess(0)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun checkGPS(activity: Activity): Boolean {
            val manager: LocationManager =
                activity.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            return manager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        }

        fun buildAlertMessageNoGps(activity: Activity) {
            val builder = AlertDialog.Builder(activity)
            builder.setTitle("Alert!")
            builder.setMessage(activity.resources.getString(pk.gop.pulse.katchiAbadi.R.string.app_name) + " wants you to enable the GPS Sensor!")
                .setCancelable(false)
                .setPositiveButton(
                    "Enable"
                ) { _, _ -> activity.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
                .setNegativeButton(
                    "Cancel"
                ) { dialog, _ ->
                    dialog.cancel()
                    activity.finish()
                }
            val alert = builder.create()
            alert.show()
        }

        fun mockLocationCheck(activity: Activity) {
            if (isMockSettingsON(activity) && areThereMockPermissionApps(activity)) {
                exitApplication(
                    "Warning!",
                    "Please disable mock/fake location. The application will exit now.",
                    activity
                )
                return
            }
        }

        fun mockLocationSettingCheck(activity: Activity): Boolean {
            return isMockSettingsON(activity) && areThereMockPermissionApps(activity)
        }

        private fun isMockSettingsON(activity: Activity): Boolean {
            // returns true if mock location enabled, false if not enabled.
            return Settings.Secure.getString(
                activity.contentResolver,
                Settings.Secure.ALLOW_MOCK_LOCATION
            ) != "0"
        }

        private fun areThereMockPermissionApps(activity: Activity): Boolean {
            var count = 0
            val pm: PackageManager = activity.packageManager
            val packages: List<ApplicationInfo> =
                pm.getInstalledApplications(PackageManager.GET_META_DATA)
            for (applicationInfo in packages) {
                try {
                    val packageInfo: PackageInfo = pm.getPackageInfo(
                        applicationInfo.packageName,
                        PackageManager.GET_PERMISSIONS
                    )
                    // Get Permissions
                    val requestedPermissions = packageInfo.requestedPermissions
                    if (requestedPermissions != null) {
                        for (i in requestedPermissions.indices) {
                            if ((requestedPermissions[i]
                                        == "android.permission.ACCESS_MOCK_LOCATION") && applicationInfo.packageName != activity.packageName
                            ) {
                                count++
                            }
                        }
                    }
                } catch (e: PackageManager.NameNotFoundException) {
                    //		        Log.e("Got exception " + e.getMessage());
                    Toast.makeText(activity, "Error is" + e.message, Toast.LENGTH_LONG).show()
                }
            }
            return count > 0
        }

        fun closeKeyBoard(activity: Activity?) {
            try {
                if (activity != null) {
                    val view = activity.currentFocus
                    if (view != null) {
                        val inputMethodManager =
                            activity.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
                        inputMethodManager.hideSoftInputFromWindow(
                            activity.currentFocus!!.windowToken,
                            0
                        )
                    } else {
                        Log.w("Utility", "No view is currently in focus")
                    }
                } else {
                    Log.e("Utility", "Activity is null")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun checkInternetConnection(context: Context): Boolean {
            val connectivityManager =
                context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork
            val networkCapabilities = connectivityManager.getNetworkCapabilities(network)
            return networkCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        }

        fun hasSingleDigit(numberString: String): Boolean {
            // Check if the string is "0000000000000", return false
//            if (numberString == "0000000000000") {
//                return false
//            }

            // Check if all characters in the string are the same
            return numberString.all { it == numberString[0] } || (numberString.substring(0, 5)
                .toSet().size == 1)
        }

        fun isMinLength(editText: EditText, minLength: Int): Boolean {
            return editText.text.toString().length >= minLength
        }

        fun isMinLengthAndNoDuplicates(editText: EditText, minLength: Int): Boolean {
            val inputText = editText.text.toString().lowercase()

            // Check minimum length
            if (inputText.length < minLength) {
                return false
            }

            // Check for different characters
            val firstChar = inputText[0]
            for (char in inputText) {
                if (char != firstChar) {
                    // Different character found
                    return true
                }
            }

            // All characters are the same
            return false
        }


//        fun isEmulator(): Boolean {
//            return Build.FINGERPRINT.startsWith("generic") ||
//                    Build.FINGERPRINT.startsWith("unknown") ||
//                    Build.MODEL.contains("google_sdk") ||
//                    Build.MODEL.contains("Emulator") ||
//                    Build.MODEL.contains("Pixel") ||
//                    Build.MODEL.contains("Android SDK built for x86") ||
//                    Build.MANUFACTURER.contains("Genymotion") ||
//                    Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic") ||
//                    "google_sdk" == Build.PRODUCT
//        }

//        fun isEmulator(): Boolean {
//            return Build.PRODUCT.contains("API")
//        }

        fun isEmulator(): Boolean {
            return Build.FINGERPRINT.startsWith("generic") ||
                    Build.FINGERPRINT.contains("generic_x86") ||
                    Build.FINGERPRINT.contains("vbox") ||
                    Build.FINGERPRINT.contains("emu")
        }

        fun convertStringToDate(inputDate: String): String {
            try {
                val date: Date = Constants.dateFormat.parse(inputDate) ?: Date()

                return Constants.dateFormatPresentable.format(date)
            } catch (e: ParseException) {
                e.printStackTrace()
                // Handle the ParseException if necessary
                return ""
            }
        }

        fun convertStringToDateOnly(inputDate: String): String {
            try {
                val date: Date = Constants.newDateFormat.parse(inputDate) ?: Date()

                return Constants.newDateFormatPresentable.format(date)
            } catch (e: ParseException) {
                e.printStackTrace()
                // Handle the ParseException if necessary
                return ""
            }
        }

        fun writeJsonToFile(context: Context, fileName: String, jsonString: String) {
            val cacheDir = File(context.cacheDir, "cachefiles")

            // Create the cache directory if it doesn't exist
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }

            // Create the file
            val file = File(cacheDir, fileName)

            try {
                // Write the JSON string to the file
                FileWriter(file).use { writer ->
                    writer.write(jsonString)
                }
                // Optionally, you may want to notify that the file has been written successfully
            } catch (e: IOException) {
                // Handle IOException (e.g., log an error)
                e.printStackTrace()
            }
        }

        fun convertFloorNumber(priority: Int): String {
            return when (priority) {
                1 -> "Ground Floor"
                2 -> "1st Floor"
                3 -> "2nd Floor"
                4 -> "3rd Floor"
                else -> "${priority - 1}th Floor"
            }
        }

        fun convertGpsTimeToString(gpsTime: Long): String {
            return Constants.dateFormat.format(Date(gpsTime))
        }

        fun checkTimeZone(context: Context): Boolean {
            return if (TimeZone.getDefault().id != "Asia/Karachi") {
                Toast.makeText(
                    context,
                    "Your device's time zone is not set to Pakistan Standard Time (PST). Please update your time zone to avoid issues.",
                    Toast.LENGTH_SHORT
                ).show()
                false
            } else {
                true
            }
        }

        fun areDatesSame(
            currentLocation: String,
            gpsTimestamp: String,
            currentMobileTimestamp: String
        ): Boolean {
            val dateFormat =
                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) // Extract date part only
            return try {
                // Parse the gpsTimestamp and currentMobileTimestamp
                val date2 =
                    dateFormat.parse(gpsTimestamp.split('T')[0]) // Extract date from gpsTimestamp
                val date3 =
                    dateFormat.parse(currentMobileTimestamp.split('T')[0]) // Extract date from currentMobileTimestamp

                if (currentLocation.isEmpty()) {
                    // Compare only gpsTimestamp and currentMobileTimestamp
                    date2 == date3
                } else {
                    // Parse currentLocation and compare all three
                    val date1 = dateFormat.parse(currentLocation.split('T')[0])
                    date1 == date2 && date2 == date3
                }
            } catch (e: Exception) {
                false // Return false if parsing fails
            }
        }

        fun convertToKanalMarlaSqFeet(areaInSqFeet: Long, sqFeetPerMarla: Int): String {
            // Constants
            val marlasPerKanal = 20

            // Calculate total Marlas
            val totalMarlas = areaInSqFeet / sqFeetPerMarla

            // Calculate remaining square feet after converting to Marlas
            val remainingSqFeet = areaInSqFeet % sqFeetPerMarla

            // Calculate Kanals from Marlas
            val kanals = totalMarlas / marlasPerKanal

            // Calculate remaining Marlas after converting to Kanals
            val remainingMarlas = totalMarlas % marlasPerKanal

            // Return the result in the format "X Kanals Y Marlas Z Square Feet"
            return "$kanals-$remainingMarlas-$remainingSqFeet"
        }

        fun simplifyPolygon(polygon: Polygon): Polygon {
            return GeometryEngine.simplify(polygon) as Polygon
        }

        // Updated getMultiPolygonFromString method
        fun getMultiPolygonFromString(
            strMultiPolyGeom: String,
            wgs84: SpatialReference
        ): List<Polygon> {
            val cleanedGeom = strMultiPolyGeom
                .removePrefix("MULTIPOLYGON (((")
                .removeSuffix(")))")
                .trim()

            val polygonStrings = cleanedGeom.split(")), ((")
            val polygons = mutableListOf<Polygon>()

            for (polygonString in polygonStrings) {
                val points = PointCollection(wgs84)
                val coordinates = polygonString.split(",")

                for (coordinate in coordinates) {
                    val trimmed = coordinate.trim()
                    val pointSplit = trimmed.split(" ").filter { it.isNotEmpty() }

                    if (pointSplit.size == 2) {
                        points.add(
                            Point(
                                pointSplit[0].toDouble(),
                                pointSplit[1].toDouble()
                            )
                        )
                    }
                }

                polygons.add(Polygon(points))
            }

            return polygons
        }

        fun getPolygonFromString(strPolyGeom: String, wgs84: SpatialReference): Polygon {

            val cleanedGeom = strPolyGeom.removePrefix("POLYGON ((").removeSuffix("))")

            Log.d("TAG", "Cleaned Geom: $cleanedGeom")

            val geomParts =
                cleanedGeom.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            val points = PointCollection(wgs84)
            for (geomPart in geomParts) {
                val trimmed = geomPart.trim()
                val pointSplit =
                    trimmed.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                points.add(
                    Point(
                        pointSplit[0].replace("(", "").trim { it <= ' ' }.toDouble(),
                        pointSplit[1].replace(")", "").trim { it <= ' ' }.toDouble()
                    )
                )
            }
            return Polygon(points)
        }

        fun getPolyFromString(strPolyGeom: String, wgs84: SpatialReference): Polygon {
            val geomParts =
                strPolyGeom.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            val points = PointCollection(wgs84)
            for (geomPart in geomParts) {
                val trimmed = geomPart.trim()
                val pointSplit =
                    trimmed.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                points.add(
                    Point(
                        pointSplit[0].replace("(", "").trim { it <= ' ' }.toDouble(),
                        pointSplit[1].replace(")", "").trim { it <= ' ' }.toDouble()
                    )
                )
            }
            return Polygon(points)
        }
    }
}