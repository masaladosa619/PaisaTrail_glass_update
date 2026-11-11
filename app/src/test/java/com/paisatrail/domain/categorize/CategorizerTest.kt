package com.paisatrail.domain.categorize

import com.paisatrail.domain.model.Category
import org.junit.Assert.assertEquals
import org.junit.Test

class CategorizerTest {

    @Test
    fun `well-known merchants land in the expected category`() {
        assertEquals(Category.FOOD, Categorizer.categorize("Swiggy"))
        assertEquals(Category.FOOD, Categorizer.categorize("Annapurna Kitchen"))
        assertEquals(Category.GROCERY, Categorizer.categorize("City Hypermarket"))
        assertEquals(Category.SHOPPING, Categorizer.categorize("Flipkart Payments"))
        assertEquals(Category.TRAVEL, Categorizer.categorize("IRCTC"))
        assertEquals(Category.RENT, Categorizer.categorize("House Rent July"))
        assertEquals(Category.INVESTMENTS, Categorizer.categorize("Groww"))
        assertEquals(Category.HEALTH, Categorizer.categorize("City Pharmacy"))
        assertEquals(Category.EMI_BILL, Categorizer.categorize("MSEDCL Electricity"))
        assertEquals(Category.SUBSCRIPTIONS, Categorizer.categorize("Netflix"))
    }

    @Test
    fun `specific rules beat broader ones`() {
        assertEquals(Category.GROCERY, Categorizer.categorize("Swiggy Instamart"))
        assertEquals(Category.SUBSCRIPTIONS, Categorizer.categorize("Amazon Prime"))
        assertEquals(Category.FOOD, Categorizer.categorize("Uber Eats"))
        assertEquals(Category.GROCERY, Categorizer.categorize("JioMart"))
    }

    @Test
    fun `keywords match whole words only`() {
        assertEquals(Category.OTHERS, Categorizer.categorize("Smartech Solutions"))
        assertEquals(Category.OTHERS, Categorizer.categorize("Parental Advisory"))
        assertEquals(Category.OTHERS, Categorizer.categorize("Credit Suisse"))
    }

    @Test
    fun `unknown merchants fall back to the description then to Others`() {
        assertEquals(Category.TRAVEL, Categorizer.categorize("Ramesh", "UPI/DR/123/RAMESH/petrol pump"))
        assertEquals(Category.OTHERS, Categorizer.categorize("Ramesh"))
        assertEquals(Category.OTHERS, Categorizer.categorize(""))
    }

    @Test
    fun `user overrides always win and ignore case and punctuation`() {
        val overrides = mapOf(Categorizer.merchantKey("Swiggy") to Category.GROCERY)

        assertEquals(Category.GROCERY, Categorizer.categorize("SWIGGY", overrides = overrides))
        assertEquals(Category.GROCERY, Categorizer.categorize("  swiggy. ", overrides = overrides))
        assertEquals("sharma kirana store", Categorizer.merchantKey("Sharma  Kirana-Store!"))
    }
}
