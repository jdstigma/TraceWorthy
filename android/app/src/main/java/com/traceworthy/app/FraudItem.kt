package com.traceworthy.app

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** What was opened or misused in the victim's name. */
enum class FraudKind(val label: String) {
    CreditCard("Credit card"),
    BankAccount("Bank / checking account"),
    Loan("Loan or credit line"),
    Ssn("SSN misuse"),
    Tax("Tax / IRS"),
    Medical("Medical or health insurance"),
    Utility("Utility / phone / internet"),
    Government("Government benefits"),
    Other("Other");

    companion object {
        fun fromName(n: String?) = entries.firstOrNull { it.name == n } ?: Other
    }
}

/** Where a fraudulent item is in the recovery process. */
enum class FraudStatus(val label: String) {
    Discovered("Discovered"),
    Reported("Reported"),
    Disputed("Disputed"),
    Resolved("Resolved");

    companion object {
        fun fromName(n: String?) = entries.firstOrNull { it.name == n } ?: Discovered
    }
}

/**
 * One fraudulent account, charge, or misuse in an identity-theft case. The list of
 * these on a [Case] is the evidence — it drives the dispute letters, the FTC report
 * companion, and the evidence summary.
 */
data class FraudItem(
    val id: String = UUID.randomUUID().toString(),
    val kind: FraudKind = FraudKind.CreditCard,
    val institution: String = "",     // "Chase", "Capital One", the hospital, the IRS…
    val accountRef: String = "",       // last 4 digits, or an account label
    val amount: String = "",           // free text: "$1,240.00", "unknown"
    val discoveredDate: String = "",   // free text date
    val status: FraudStatus = FraudStatus.Discovered,
    val note: String = "",
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("kind", kind.name)
        put("institution", institution)
        put("accountRef", accountRef)
        put("amount", amount)
        put("discoveredDate", discoveredDate)
        put("status", status.name)
        put("note", note)
    }

    companion object {
        fun fromJson(o: JSONObject) = FraudItem(
            id = o.optString("id", UUID.randomUUID().toString()),
            kind = FraudKind.fromName(o.optString("kind")),
            institution = o.optString("institution"),
            accountRef = o.optString("accountRef"),
            amount = o.optString("amount"),
            discoveredDate = o.optString("discoveredDate"),
            status = FraudStatus.fromName(o.optString("status")),
            note = o.optString("note"),
        )

        fun listToJson(items: List<FraudItem>): JSONArray =
            JSONArray().apply { items.forEach { put(it.toJson()) } }

        fun listFromJson(arr: JSONArray?): List<FraudItem> {
            if (arr == null) return emptyList()
            return (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
        }
    }
}
