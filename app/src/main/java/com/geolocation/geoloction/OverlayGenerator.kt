package com.geolocation.geoloction

import android.content.Context
import android.graphics.*
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object OverlayGenerator {
    suspend fun generateStampedImage(
        context: Context,
        imageUri: Uri,
        metadata: PhotoMetadata
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            // 1. Load Original Image
            val inputStream = context.contentResolver.openInputStream(imageUri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close() ?: return@withContext null

            val mutableBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(mutableBitmap)
            val width = mutableBitmap.width
            val height = mutableBitmap.height

            // 2. Setup Initial Margins and Paints
            val margin = width * 0.03f // 3% margin around everything
            val qrBoxSize = (width * 0.18f).coerceAtLeast(150f) // Keep QR proportional

            // Text has to fit between the left margin and the QR code on the right
            val textMaxWidth = (width - (margin * 3) - qrBoxSize).toInt()

            val titlePaint = TextPaint().apply {
                color = Color.WHITE
                textSize = (width * 0.035f).coerceAtLeast(40f) // Scale text to image width
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }

            val bodyPaint = TextPaint().apply {
                color = Color.WHITE
                textSize = (width * 0.025f).coerceAtLeast(28f)
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                isAntiAlias = true
            }

            // 3. PRE-MEASURE THE TEXT (This fixes the overflow)
            val titleLayout = StaticLayout.Builder.obtain(metadata.shortLocation, 0, metadata.shortLocation.length, titlePaint, textMaxWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).build()

            val addressLayout = StaticLayout.Builder.obtain(metadata.fullAddress, 0, metadata.fullAddress.length, bodyPaint, textMaxWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).build()

            val latLngText = if (metadata.latitude != null) {
                "Lat ${String.format("%.6f", metadata.latitude)}° Long ${String.format("%.6f", metadata.longitude)}°"
            } else "Coordinates Unavailable"
            val coordLayout = StaticLayout.Builder.obtain(latLngText, 0, latLngText.length, bodyPaint, textMaxWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).build()

            val dateLayout = StaticLayout.Builder.obtain(metadata.formattedDate, 0, metadata.formattedDate.length, bodyPaint, textMaxWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).build()

            // Calculate total text height + spacing between lines
            val lineSpacing = width * 0.01f
            val totalTextHeight = titleLayout.height + addressLayout.height + coordLayout.height + dateLayout.height + (lineSpacing * 3)

            // 4. Calculate Final Box Dimensions
            // The box needs to be tall enough to fit EITHER the text or the QR code (whichever is taller)
            val contentHeight = maxOf(totalTextHeight, qrBoxSize)
            val overlayHeight = contentHeight + (margin * 2)

            val rect = RectF(
                margin,
                height - overlayHeight - margin,
                width - margin,
                height - margin
            )

            // 5. Draw Background Box
            val bgPaint = Paint().apply {
                color = Color.parseColor("#99000000") // Dark semi-transparent
                isAntiAlias = true
            }
            val cornerRadius = width * 0.02f
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, bgPaint)

            // 6. Generate and Draw QR Code
            val qrRightMargin = rect.right - margin
            val qrLeftMargin = qrRightMargin - qrBoxSize

            // Center the QR code vertically inside the black box
            val qrTopMargin = rect.top + ((overlayHeight - qrBoxSize) / 2f)
            val qrRect = RectF(qrLeftMargin, qrTopMargin, qrRightMargin, qrTopMargin + qrBoxSize)

            val whitePaint = Paint().apply { color = Color.WHITE; isAntiAlias = true }
            canvas.drawRoundRect(qrRect, cornerRadius / 2, cornerRadius / 2, whitePaint)

            val mapsUrl = "https://maps.google.com/?q=${metadata.latitude},${metadata.longitude}"
            val qrBitmap = generateQrCode(mapsUrl, qrBoxSize.toInt())
            if (qrBitmap != null) {
                val qrPadding = qrBoxSize * 0.05f
                val qrInnerRect = RectF(
                    qrRect.left + qrPadding, qrRect.top + qrPadding,
                    qrRect.right - qrPadding, qrRect.bottom - qrPadding
                )
                canvas.drawBitmap(qrBitmap, null, qrInnerRect, null)
            }

            // 7. Draw the Text
            canvas.save()
            // Start drawing text from top-left of the black box
            canvas.translate(rect.left + margin, rect.top + margin)

            titleLayout.draw(canvas)
            canvas.translate(0f, titleLayout.height + lineSpacing)

            addressLayout.draw(canvas)
            canvas.translate(0f, addressLayout.height + lineSpacing)

            coordLayout.draw(canvas)
            canvas.translate(0f, coordLayout.height + lineSpacing)

            dateLayout.draw(canvas)

            canvas.restore()

            mutableBitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun generateQrCode(content: String, size: Int): Bitmap? {
        return try {
            val bitMatrix = MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
            for (x in 0 until size) {
                for (y in 0 until size) {
                    bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE)
                }
            }
            bitmap
        } catch (e: Exception) { null }
    }
}