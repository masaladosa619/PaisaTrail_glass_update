package com.paisatrail.domain.parse

import com.paisatrail.domain.model.TxnType
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpiScreenshotParserTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private val today = LocalDate.of(2026, 11, 10)

    private fun millis(y: Int, mo: Int, d: Int, h: Int, mi: Int): Long =
        LocalDateTime.of(y, mo, d, h, mi).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `label-above-value layout with amount on the merchant row`() {
        val lines = listOf(
            "Transaction Successful",
            "04 October 2026 at 3:18 PM",
            "Paid to",
            "CHAI POINT INDIRANAGAR ₹749",
            "chaipoint.blr@hdfcbank",
            "Payment Details",
            "Message",
            "evening snacks",
            "Wallet Transaction ID",
            "T2610041518220795AB7527",
            "Debited from",
            "XXXXXXXXXX55 ₹749",
            "UTR: 610412345678"
        )
        val parsed = UpiScreenshotParser.parse(lines, zone, today)

        assertEquals(74_900L, parsed.amountPaise)
        assertEquals("CHAI POINT INDIRANAGAR", parsed.merchant)
        assertEquals(millis(2026, 10, 4, 15, 18), parsed.timestamp)
        assertEquals("610412345678", parsed.txnId)
        assertEquals("XXXXXXXXXX55", parsed.paidFrom)
        assertEquals(TxnType.EXPENSE, parsed.type)
    }

    @Test
    fun `inline label layout with grouped amount`() {
        val lines = listOf(
            "₹1,850.00",
            "Paid to Annapurna Kitchen",
            "Completed",
            "21 Oct 2026, 1:53 pm",
            "UPI transaction ID",
            "629412345678",
            "To: ANNAPURNA KITCHEN",
            "From: RAVI K (State Bank 4321)",
            "Bank transaction ID",
            "CICAgJDx1abcDE"
        )
        val parsed = UpiScreenshotParser.parse(lines, zone, today)

        assertEquals(185_000L, parsed.amountPaise)
        assertEquals("Annapurna Kitchen", parsed.merchant)
        assertEquals(millis(2026, 10, 21, 13, 53), parsed.timestamp)
        assertEquals("629412345678", parsed.txnId)
        assertEquals("RAVI K (State Bank 4321)", parsed.paidFrom)
    }

    @Test
    fun `money received is logged as income not expense`() {
        val lines = listOf(
            "Payment received",
            "Rs. 500",
            "Received from",
            "Meera Joshi",
            "09/11/2026 18:05",
            "UPI Ref No: 631312345678"
        )
        val parsed = UpiScreenshotParser.parse(lines, zone, today)

        assertEquals(TxnType.INCOME, parsed.type)
        assertEquals(50_000L, parsed.amountPaise)
        assertEquals("Meera Joshi", parsed.merchant)
        assertEquals(millis(2026, 11, 9, 18, 5), parsed.timestamp)
        assertEquals("631312345678", parsed.txnId)
        assertNull(parsed.paidFrom)
    }

    @Test
    fun `falls back to a bare number when OCR drops the rupee sign`() {
        val lines = listOf(
            "10:17",
            "Payment Successful",
            "2,499.00",
            "Sent to",
            "Metro Mobiles",
            "Nov 8, 2026 9:41 AM",
            "Transaction ID: MM2611080941XZ"
        )
        val parsed = UpiScreenshotParser.parse(lines, zone, today)

        assertEquals(249_900L, parsed.amountPaise)
        assertEquals("Metro Mobiles", parsed.merchant)
        assertEquals(millis(2026, 11, 8, 9, 41), parsed.timestamp)
        assertEquals("MM2611080941XZ", parsed.txnId)
    }

    @Test
    fun `uses the UPI handle when there is no payee label`() {
        val lines = listOf("₹120", "freshjuice@okaxis", "Success")
        val parsed = UpiScreenshotParser.parse(lines, zone, today)

        assertEquals(12_000L, parsed.amountPaise)
        assertEquals("freshjuice", parsed.merchant)
        assertNull(parsed.timestamp)
        assertNull(parsed.txnId)
    }

    @Test
    fun `unreadable screenshot yields nulls instead of guesses`() {
        val parsed = UpiScreenshotParser.parse(listOf("Hello", "nothing useful here"), zone, today)

        assertNull(parsed.amountPaise)
        assertNull(parsed.merchant)
        assertNull(parsed.timestamp)
        assertNull(parsed.txnId)
        assertEquals(TxnType.EXPENSE, parsed.type)
    }
}
