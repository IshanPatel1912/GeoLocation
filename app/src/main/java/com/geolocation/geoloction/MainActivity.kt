package com.geolocation.geoloction

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                GeoTagApp()
            }
        }
    }
}

@Composable
fun GeoTagApp() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var stampedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isProcessing by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent() // Changed back to GetContent
    ) { uri ->
        uri?.let {
            selectedImageUri = it
            stampedBitmap = null
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // Once the user clicks "Allow", proceed to generate the tag
        selectedImageUri?.let { uri ->
            isProcessing = true
            coroutineScope.launch {
                val meta = ExifHelper.extractMetadata(context, uri)
                stampedBitmap = OverlayGenerator.generateStampedImage(context, uri, meta)
                isProcessing = false
            }
        }
    }
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("GeoTag Photo Stamp", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(16.dp))

            // Image Preview Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (stampedBitmap != null) {
                    Image(
                        bitmap = stampedBitmap!!.asImageBitmap(),
                        contentDescription = "Stamped Image",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                } else if (selectedImageUri != null) {
                    AsyncImage(
                        model = selectedImageUri,
                        contentDescription = "Selected Image",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Text("No image selected", style = MaterialTheme.typography.bodyLarge)
                }

                if (isProcessing) {
                    CircularProgressIndicator()
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(onClick = {
                    photoPickerLauncher.launch("image/*") // Removed the arrayOf()
                }) {
                    Text("Pick Photo")
                }

                Button(
                    onClick = {
                        // Ask for location un-redaction permission on Android 10+
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                            permissionLauncher.launch(android.Manifest.permission.ACCESS_MEDIA_LOCATION)
                        } else {
                            // Older Androids don't need the runtime permission, just process it
                            selectedImageUri?.let { uri ->
                                isProcessing = true
                                coroutineScope.launch {
                                    val meta = ExifHelper.extractMetadata(context, uri)
                                    stampedBitmap = OverlayGenerator.generateStampedImage(context, uri, meta)
                                    isProcessing = false
                                }
                            }
                        }
                    },
                    enabled = selectedImageUri != null && !isProcessing && stampedBitmap == null
                ) {
                    Text("Generate Tag")
                }}

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = {
                        stampedBitmap?.let { bmp ->
                            coroutineScope.launch {
                                saveBitmapToGallery(context, bmp)
                            }
                        }
                    },
                    enabled = stampedBitmap != null
                ) {
                    Text("Save to Gallery")
                }

                Button(
                    onClick = {
                        stampedBitmap?.let { bmp ->
                            coroutineScope.launch {
                                shareImage(context, bmp)
                            }
                        }
                    },
                    enabled = stampedBitmap != null
                ) {
                    Text("Share")
                }
            }
        }
    }
}

// Utility: Save to Gallery using MediaStore
fun saveBitmapToGallery(context: android.content.Context, bitmap: Bitmap): Uri? {
    val filename = "GeoTag_${System.currentTimeMillis()}.jpg"
    val contentValues = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, filename)
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES)
    }

    val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
    uri?.let {
        context.contentResolver.openOutputStream(it).use { outStream ->
            if (outStream != null) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outStream)
                Toast.makeText(context, "Saved to Gallery!", Toast.LENGTH_SHORT).show()
            }
        }
    }
    return uri
}

// Utility: Share Image
fun shareImage(context: android.content.Context, bitmap: Bitmap) {
    val uri = saveBitmapToGallery(context, bitmap) // Save first to get a URI to share
    uri?.let {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, it)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Stamped Photo"))
    }
}