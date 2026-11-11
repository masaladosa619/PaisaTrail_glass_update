package com.paisatrail.domain.util

import java.net.URLEncoder
import java.util.Locale

/** Builds standard UPI deep links (also used as the QR payload). */
object UpiLink {
    private val VPA = Regex("^[A-Za-z0-9._\\-]{2,}@[A-Za-z][A-Za-z0-9]{1,}$")

    fun isValidVpa(vpa: String): Boolean = VPA.matches(vpa.trim())

    fun build(payeeVpa: String, payeeName: String, amountPaise: Long, note: String = ""): String {
        val sb = StringBuilder("upi://pay?pa=").append(encode(payeeVpa.trim()))
        if (payeeName.isNotBlank()) sb.append("&pn=").append(encode(payeeName.trim()))
        if (amountPaise > 0) {
            sb.append("&am=").append(String.format(Locale.US, "%.2f", amountPaise / 100.0))
        }
        sb.append("&cu=INR")
        if (note.isNotBlank()) sb.append("&tn=").append(encode(note.trim()))
        return sb.toString()
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20").replace("%40", "@")
}
