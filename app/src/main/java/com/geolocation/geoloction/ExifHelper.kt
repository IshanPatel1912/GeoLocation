package com.geolocation.geoloction

import android.content.Context
import android.location.Geocoder
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

data class PhotoMetadata(
    val latitude: Double?,
    val longitude: Double?,
    val formattedDate: String,
    val shortLocation: String,
    val fullAddress: String
)

object ExifHelper {
    private const val TAG = "ExifTracker"

    suspend fun extractMetadata(context: Context, uri: Uri): PhotoMetadata = withContext(Dispatchers.IO) {
        var lat: Double? = null
        var lng: Double? = null
        var rawDate: String? = null
        var shortLoc = "Unknown Location"
        var fullAddr = "Address unavailable"

        try {
            val originalUri = try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    android.provider.MediaStore.setRequireOriginal(uri)
                } else uri
            } catch (e: Exception) { uri }

            context.contentResolver.openFileDescriptor(originalUri, "r")?.use { pfd ->
                val exif = ExifInterface(pfd.fileDescriptor)
                rawDate = exif.getAttribute(ExifInterface.TAG_DATETIME)

                val latLong = exif.latLong
                if (latLong != null) {
                    lat = latLong[0]
                    lng = latLong[1]

                    // Get Address
                    val geocoder = Geocoder(context, Locale.getDefault())
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocation(lat, lng, 1)
                    if (!addresses.isNullOrEmpty()) {
                        val address = addresses[0]
                        val city = address.locality ?: address.subAdminArea ?: ""
                        val state = address.adminArea ?: ""
                        val country = address.countryName ?: ""

                        shortLoc = listOf(city, state, country).filter { it.isNotEmpty() }.joinToString(", ")
                        fullAddr = address.getAddressLine(0) ?: shortLoc
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error: ${e.message}")
        }

        PhotoMetadata(lat, lng, formatDate(rawDate), shortLoc, fullAddr)
    }

    private fun formatDate(rawDate: String?): String {
        if (rawDate == null) return "Unknown Date"
        return try {
            // EXIF format is typically "yyyy:MM:dd HH:mm:ss"
            val parser = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.getDefault())
            val formatter = SimpleDateFormat("EEEE, dd/MM/yyyy hh:mm a", Locale.getDefault())
            val date = parser.parse(rawDate)
            if (date != null) formatter.format(date) else rawDate
        } catch (e: Exception) {
            rawDate // Return raw if parsing fails
        }
    }
}