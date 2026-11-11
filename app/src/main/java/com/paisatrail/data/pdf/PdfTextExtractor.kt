package com.paisatrail.data.pdf

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface PdfTextResult {
    data class Success(val text: String, val pages: Int) : PdfTextResult
    /** The PDF is encrypted; ask the user for the password (banks usually protect statements). */
    data object PasswordRequired : PdfTextResult
    data class Failure(val message: String) : PdfTextResult
}

@Singleton
class PdfTextExtractor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /** Reads every page as position-sorted text. Always runs on the IO dispatcher. */
    suspend fun extract(uri: Uri, password: String? = null): PdfTextResult = withContext(Dispatchers.IO) {
        try {
            PDFBoxResourceLoader.init(context)
            val input = context.contentResolver.openInputStream(uri)
                ?: return@withContext PdfTextResult.Failure("Couldn't open that file.")
            input.use { stream ->
                val document = if (password.isNullOrEmpty()) PDDocument.load(stream) else PDDocument.load(stream, password)
                document.use { doc ->
                    val stripper = PDFTextStripper()
                    stripper.sortByPosition = true
                    PdfTextResult.Success(stripper.getText(doc), doc.numberOfPages)
                }
            }
        } catch (e: InvalidPasswordException) {
            PdfTextResult.PasswordRequired
        } catch (e: OutOfMemoryError) {
            PdfTextResult.Failure("This PDF is too large to read on this device.")
        } catch (e: Exception) {
            PdfTextResult.Failure(e.message ?: "This file doesn't look like a readable PDF.")
        }
    }
}
