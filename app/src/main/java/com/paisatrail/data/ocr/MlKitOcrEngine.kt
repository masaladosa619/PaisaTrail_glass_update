package com.paisatrail.data.ocr

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

@Singleton
class MlKitOcrEngine @Inject constructor(
    @ApplicationContext private val context: Context
) : OcrEngine {

    private class Piece(val text: String, val top: Int, val bottom: Int, val left: Int) {
        val centerY: Int get() = (top + bottom) / 2
    }

    override suspend fun recognize(uri: Uri): List<String> = withContext(Dispatchers.Default) {
        val image = InputImage.fromFilePath(context, uri)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val result = suspendCancellableCoroutine<Text> { continuation ->
                recognizer.process(image)
                    .addOnSuccessListener { text -> continuation.resume(text) }
                    .addOnFailureListener { error -> continuation.resumeWithException(error) }
            }
            toRows(result)
        } finally {
            recognizer.close()
        }
    }

    /**
     * ML Kit returns blocks in an arbitrary order. Payment screens put a label on the left and a
     * value on the right of the same row, so pieces are regrouped into visual rows first.
     */
    private fun toRows(result: Text): List<String> {
        val pieces = result.textBlocks.flatMap { it.lines }.mapNotNull { line ->
            val box = line.boundingBox ?: return@mapNotNull null
            Piece(line.text, box.top, box.bottom, box.left)
        }.sortedBy { it.top }

        val rows = ArrayList<MutableList<Piece>>()
        for (piece in pieces) {
            val row = rows.lastOrNull()
            val anchor = row?.firstOrNull()
            if (row != null && anchor != null && piece.centerY >= anchor.top && piece.centerY <= anchor.bottom) {
                row.add(piece)
            } else {
                rows.add(mutableListOf(piece))
            }
        }
        return rows.map { row -> row.sortedBy { it.left }.joinToString(" ") { it.text } }
    }
}
