package com.paisatrail.domain.categorize

import com.paisatrail.domain.model.Category

/**
 * Offline keyword categorizer. User corrections (merchant -> category overrides) always win.
 * Rules are checked in order, so more specific categories sit above broader ones.
 */
object Categorizer {

    private val RULES: List<Pair<Category, List<String>>> = listOf(
        Category.SUBSCRIPTIONS to listOf(
            "netflix", "spotify", "prime video", "amazon prime", "hotstar", "jiohotstar", "youtube premium",
            "youtube music", "apple media", "apple services", "apple.com/bill", "itunes", "icloud", "google one",
            "google play", "jiocinema", "sonyliv", "zee5", "chatgpt", "openai", "claude", "anthropic", "github",
            "notion", "linkedin premium", "audible", "kindle unlimited", "subscription", "membership", "gaana",
            "jiosaavn", "disney", "crunchyroll", "canva", "adobe", "microsoft 365", "dropbox"
        ),
        Category.GROCERY to listOf(
            "bigbasket", "blinkit", "zepto", "instamart", "dmart", "d mart", "grocery", "groceries", "supermarket",
            "hypermarket", "kirana", "provision", "provisions", "mart", "country delight", "milk", "dairy",
            "vegetables", "vegetable", "fruits", "reliance fresh", "more retail", "spencer", "spencers",
            "nature's basket", "jiomart", "general store", "departmental", "ratnadeep", "star bazaar", "licious"
        ),
        Category.INVESTMENTS to listOf(
            "groww", "zerodha", "upstox", "kite", "mutual fund", "mutual funds", "sip", "nps", "ppf", "smallcase",
            "kuvera", "indmoney", "paytm money", "angel one", "etmoney", "fixed deposit", "recurring deposit",
            "stocks", "demat", "nse", "bse", "icici direct", "hdfc securities", "5paisa", "dhan", "coin",
            "sovereign gold", "digital gold", "amc", "cams", "kfintech", "bse star"
        ),
        Category.HEALTH to listOf(
            "pharmacy", "pharma", "medical", "medicals", "medicine", "medicines", "hospital", "hospitals", "clinic",
            "apollo", "1mg", "pharmeasy", "netmeds", "diagnostic", "diagnostics", "pathology", "lab", "labs",
            "doctor", "dental", "dentist", "practo", "medplus", "health", "healthcare", "gym", "fitness",
            "cult fit", "cultfit", "chemist", "wellness", "physio", "eye care", "optical", "opticals"
        ),
        Category.RENT to listOf(
            "rent", "landlord", "nobroker", "housing society", "society maintenance", "maintenance", "pg",
            "hostel", "lease", "house owner", "flat owner"
        ),
        Category.EMI_BILL to listOf(
            "emi", "loan", "electricity", "mseb", "msedcl", "bescom", "tneb", "bses", "adani electricity",
            "water bill", "gas", "broadband", "fiber", "fibernet", "airtel", "jio", "vi", "vodafone", "bsnl",
            "recharge", "dth", "tata play", "bill", "bills", "billdesk", "insurance", "lic", "premium",
            "credit card", "cred", "bajaj finserv", "bajaj finance", "finance", "postpaid", "wifi",
            "interest payment", "interest", "municipal", "property tax", "tax", "fastag recharge", "challan"
        ),
        Category.FOOD to listOf(
            "swiggy", "zomato", "uber eats", "eatsure", "restaurant", "restaurants", "cafe", "coffee", "tea",
            "chai", "pizza", "dominos", "domino's", "mcdonald", "mcdonalds", "kfc", "burger", "biryani", "bakery",
            "bakers", "juice", "kitchen", "dhaba", "eatery", "food", "foods", "starbucks", "sweets", "mess",
            "canteen", "dine", "dining", "bar", "pub", "snacks", "tiffin", "tiffins", "caterers", "ice cream",
            "barbeque", "bbq", "subway", "haldiram", "haldirams", "chaayos", "udupi", "bhavan", "misal", "vada pav"
        ),
        Category.TRAVEL to listOf(
            "uber", "ola", "rapido", "irctc", "railway", "railways", "redbus", "makemytrip", "goibibo", "indigo",
            "air india", "vistara", "spicejet", "akasa", "metro", "petrol", "petroleum", "fuel", "hpcl", "bpcl",
            "iocl", "indian oil", "bharat petroleum", "hp pay", "fastag", "toll", "cab", "cabs", "taxi", "bus",
            "flight", "flights", "airlines", "airways", "yatra", "cleartrip", "ixigo", "parking", "oyo", "travels",
            "travel", "msrtc", "ksrtc", "namma yatri", "blusmart", "auto"
        ),
        Category.SHOPPING to listOf(
            "amazon", "flipkart", "myntra", "ajio", "meesho", "nykaa", "tata cliq", "croma", "reliance digital",
            "reliance trends", "decathlon", "ikea", "lifestyle", "pantaloons", "westside", "zara", "h&m", "zudio",
            "shoppers stop", "store", "stores", "mall", "fashion", "fashions", "electronics", "snapdeal", "shop",
            "shopping", "retail", "garments", "footwear", "bata", "lenskart", "titan", "jewellers", "mobiles"
        )
    )

    private val COMPILED: List<Pair<Category, List<Regex>>> = RULES.map { (category, keywords) ->
        category to keywords.map { Regex("(?<![a-z0-9])" + Regex.escape(it) + "(?![a-z0-9])") }
    }

    private val NON_ALNUM = Regex("[^a-z0-9]+")

    /** Normalised lookup key for a merchant name, used to remember user corrections. */
    fun merchantKey(merchant: String): String = merchant.lowercase().replace(NON_ALNUM, " ").trim()

    fun categorize(
        merchant: String,
        description: String = "",
        overrides: Map<String, Category> = emptyMap()
    ): Category {
        overrides[merchantKey(merchant)]?.let { return it }
        match(merchant)?.let { return it }
        if (description.isNotBlank()) match(description)?.let { return it }
        return Category.OTHERS
    }

    private fun match(text: String): Category? {
        val haystack = text.lowercase()
        for ((category, patterns) in COMPILED) {
            if (patterns.any { it.containsMatchIn(haystack) }) return category
        }
        return null
    }
}
