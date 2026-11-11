package com.paisatrail.domain.parse

import com.paisatrail.domain.model.TxnType
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatementParserTest {

    @Test
    fun `amount plus balance rows use the running balance to tell debit from credit`() {
        val text = """
            Statement of Account
            Date Narration Amount Balance
            Opening Balance 50,000.00
            01/07/2026 UPI/DR/618212345678/SWIGGY/UTIB/swiggy@axisbank/Payment 250.00 49,750.00
            02/07/2026 NEFT-HDFC0000123-ACME TECHNOLOGIES PVT LTD-JUL PAYOUT 80,000.00 1,29,750.00
            03/07/2026 POS 416021XXXXXX1234 RELIANCE FRESH 1,240.50 1,28,509.50
            Closing Balance 1,28,509.50
        """.trimIndent()

        val result = StatementParser.parse(text)

        assertEquals(3, result.rows.size)
        assertEquals(0, result.skippedLines)

        val swiggy = result.rows[0]
        assertEquals(LocalDate.of(2026, 7, 1), swiggy.date)
        assertEquals(25_000L, swiggy.amountPaise)
        assertEquals(TxnType.EXPENSE, swiggy.type)
        assertEquals("Swiggy", swiggy.merchant)
        assertEquals(4_975_000L, swiggy.balancePaise)

        val salary = result.rows[1]
        assertEquals(8_000_000L, salary.amountPaise)
        assertEquals(TxnType.INCOME, salary.type)
        assertEquals("Acme Technologies Pvt Ltd", salary.merchant)

        val grocery = result.rows[2]
        assertEquals(124_050L, grocery.amountPaise)
        assertEquals(TxnType.EXPENSE, grocery.type)
        assertEquals("Reliance Fresh", grocery.merchant)
    }

    @Test
    fun `withdrawal deposit balance columns`() {
        val text = """
            05-Jul-2026 05-Jul-2026 ATM WDL 1234 MG ROAD PUNE 2,000.00 0.00 10,500.00
            06-Jul-2026 06-Jul-2026 BY TRANSFER RENT RECEIVED 0.00 15,000.00 25,500.00
        """.trimIndent()

        val rows = StatementParser.parse(text).rows

        assertEquals(2, rows.size)
        assertEquals(TxnType.EXPENSE, rows[0].type)
        assertEquals(200_000L, rows[0].amountPaise)
        assertEquals(LocalDate.of(2026, 7, 5), rows[0].date)
        assertEquals(TxnType.INCOME, rows[1].type)
        assertEquals(1_500_000L, rows[1].amountPaise)
        assertEquals(2_550_000L, rows[1].balancePaise)
    }

    @Test
    fun `explicit Dr and Cr markers win`() {
        val text = """
            10/07/26 CARD PURCHASE BOOK HOUSE 499.00 Dr 9,501.00 Cr
            11/07/26 CASH DEPOSIT SELF 1,000.00 Cr 10,501.00 Cr
        """.trimIndent()

        val rows = StatementParser.parse(text).rows

        assertEquals(TxnType.EXPENSE, rows[0].type)
        assertEquals(49_900L, rows[0].amountPaise)
        assertEquals(TxnType.INCOME, rows[1].type)
        assertEquals(100_000L, rows[1].amountPaise)
    }

    @Test
    fun `wrapped descriptions are folded into the row above`() {
        val text = """
            12/07/2026 UPI/DR/619312345678/
            CITY PHARMACY/SBIN/citypharma@sbi 310.00 4,690.00
            13/07/2026 UPI/DR/619412345678/GREEN CABS 180.00 4,510.00
            Page 1 of 2
        """.trimIndent()

        val result = StatementParser.parse(text)

        assertEquals(2, result.rows.size)
        assertEquals(31_000L, result.rows[0].amountPaise)
        assertEquals("City Pharmacy", result.rows[0].merchant)
        assertEquals(18_000L, result.rows[1].amountPaise)
        assertEquals("Green Cabs", result.rows[1].merchant)
    }

    @Test
    fun `newest-first statements are reordered before reading balances`() {
        val text = """
            03/08/2026 UPI/DR/1/TEA STALL 50.00 950.00
            02/08/2026 IMPS/9988/FROM RAHUL 500.00 1,000.00
            01/08/2026 UPI/DR/2/BOOK SHOP 500.00 500.00
        """.trimIndent()

        val rows = StatementParser.parse(text).rows

        assertEquals(LocalDate.of(2026, 8, 1), rows.first().date)
        assertEquals(TxnType.INCOME, rows[1].type)
        assertEquals(TxnType.EXPENSE, rows[2].type)
    }

    @Test
    fun `rows without an amount are counted as skipped and junk text yields nothing`() {
        val result = StatementParser.parse("14/07/2026 CHEQUE RETURNED\nThis is not a statement at all.")
        assertEquals(0, result.rows.size)
        assertEquals(1, result.skippedLines)

        assertTrue(StatementParser.parse("").rows.isEmpty())
    }

    @Test
    fun `span and fingerprint`() {
        val rows = StatementParser.parse(
            "01/07/2026 UPI/DR/1/TEA STALL 50.00 950.00\n31/07/2026 UPI/DR/2/TEA STALL 50.00 900.00"
        ).rows

        assertEquals(31L, StatementParser.spanDays(rows))
        assertEquals(0L, StatementParser.spanDays(emptyList()))
        assertEquals(StatementParser.fingerprint(rows[0]), StatementParser.fingerprint(rows[0].copy()))
        assertNotEquals(StatementParser.fingerprint(rows[0]), StatementParser.fingerprint(rows[1]))
    }

    @Test
    fun `merchant extraction from common narrations`() {
        assertEquals("Swiggy", StatementParser.extractMerchant("UPI-SWIGGY-SWIGGY8@YBL-YESB0000001-412345678901-PAYMENT"))
        assertEquals("Amazon Pay India", StatementParser.extractMerchant("POS 416021XXXXXX1234 AMAZON PAY INDIA"))
        assertEquals("Sharma Kirana Store", StatementParser.extractMerchant("UPI/DR/123456789012/Sharma Kirana Store/PUNB/sharma@pnb"))
    }
}
