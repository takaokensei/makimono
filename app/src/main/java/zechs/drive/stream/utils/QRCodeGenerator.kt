package zechs.drive.stream.utils

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.util.EnumMap

object QRCodeGenerator {

    private const val TAG = "QRCodeGenerator"

    /**
     * Generates a high-quality Bitmap for a QR Code.
     *
     * @param content The text/URL content to encode.
     * @param sizePx The width and height of the resulting bitmap in pixels.
     * @param darkColor Color for the QR Code modules (foreground, default white).
     * @param lightColor Color for the background (default transparent).
     */
    fun generateBitmap(
        content: String,
        sizePx: Int = 512,
        darkColor: Int = Color.WHITE,
        lightColor: Int = Color.TRANSPARENT
    ): Bitmap? {
        if (content.isEmpty()) return null
        return try {
            val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
                put(EncodeHintType.CHARACTER_SET, "UTF-8")
                put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M)
                put(EncodeHintType.MARGIN, 1)
            }

            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
            val width = bitMatrix.width
            val height = bitMatrix.height

            val pixels = IntArray(width * height)
            for (y in 0 until height) {
                val offset = y * width
                for (x in 0 until width) {
                    pixels[offset + x] = if (bitMatrix.get(x, y)) darkColor else lightColor
                }
            }

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
            bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate QR code bitmap", e)
            null
        }
    }
}
