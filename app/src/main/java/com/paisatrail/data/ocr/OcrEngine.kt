package com.paisatrail.data.ocr

import android.net.Uri

/**
 * On-device text recognition. The rest of the app only knows this interface.
 *
 * The default binding is [MlKitOcrEngine] (Google ML Kit, bundled model, works offline).
 * For an F-Droid build with no Google libraries, write a `TesseractOcrEngine : OcrEngine`
 * (for example with the tesseract4android library and an `eng.traineddata` asset), return one
 * string per visual text row, and change the single `@Binds` line in di/AppModule.kt.
 */
interface OcrEngine {
    /** Returns the recognised text rows, top to bottom. Throws if the image cannot be read. */
    suspend fun recognize(uri: Uri): List<String>
}
