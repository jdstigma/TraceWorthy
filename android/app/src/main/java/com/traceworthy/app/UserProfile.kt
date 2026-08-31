package com.traceworthy.app

import android.content.Context

/**
 * The kind of harassment being documented. This changes the wording of the
 * generated documents and which evidence they emphasize:
 *  - [Silent] leans on the pattern itself (volume, spoofing, silent/short calls).
 *  - [Aggressive] leans on the content of the calls — threats, abuse — evidenced
 *    by the incident timeline built from the user's dated notes.
 */
enum class HarassmentType(val label: String, val shortLabel: String) {
    Unspecified("Not sure yet / prefer not to say", "Unsure"),
    Silent("Silent / hang-up calls", "Silent"),
    Aggressive("Aggressive / threatening", "Aggressive"),
    Both("Both", "Both");

    val includesAggressive: Boolean get() = this == Aggressive || this == Both
    val includesSilent: Boolean get() = this == Silent || this == Both
}

/**
 * The details you enter once on the "My info" screen. Every generated document
 * (FCC complaint, police cover note, carrier script) pulls from here so you
 * never retype your name, number, or case numbers. Stored locally in
 * SharedPreferences — nothing leaves the device.
 */
data class UserProfile(
    val fullName: String = "",
    val phone: String = "",          // where police / FCC / the carrier should reach you
    val affectedNumber: String = "", // the line actually receiving the harassing calls
    val email: String = "",
    val addressCity: String = "",
    val state: String = "",          // two-letter USPS code, e.g. "CA"
    val carrier: String = "",        // e.g. "Verizon", "AT&T", "T-Mobile"
    val harassmentType: HarassmentType = HarassmentType.Unspecified,
    val fccComplaintNumber: String = "",
    val policeCaseNumber: String = "",
    val carrierCaseNumber: String = "",
) {
    /** The harassed number — falls back to the contact number when not set separately. */
    val affectedLine: String
        get() = affectedNumber.ifBlank { phone }

    /** True once the minimum needed to fill a document is present. */
    val isReadyForDocuments: Boolean
        get() = fullName.isNotBlank() && phone.isNotBlank()
}

object ProfileStore {

    private const val PREFS = "user_profile"

    fun load(context: Context): UserProfile {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return UserProfile(
            fullName = p.getString("fullName", "") ?: "",
            phone = p.getString("phone", "") ?: "",
            affectedNumber = p.getString("affectedNumber", "") ?: "",
            email = p.getString("email", "") ?: "",
            addressCity = p.getString("addressCity", "") ?: "",
            state = p.getString("state", "") ?: "",
            carrier = p.getString("carrier", "") ?: "",
            harassmentType = runCatching {
                HarassmentType.valueOf(p.getString("harassmentType", null) ?: "Unspecified")
            }.getOrDefault(HarassmentType.Unspecified),
            fccComplaintNumber = p.getString("fccComplaintNumber", "") ?: "",
            policeCaseNumber = p.getString("policeCaseNumber", "") ?: "",
            carrierCaseNumber = p.getString("carrierCaseNumber", "") ?: "",
        )
    }

    fun save(context: Context, profile: UserProfile) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            putString("fullName", profile.fullName)
            putString("phone", profile.phone)
            putString("affectedNumber", profile.affectedNumber)
            putString("email", profile.email)
            putString("addressCity", profile.addressCity)
            putString("state", profile.state)
            putString("carrier", profile.carrier)
            putString("harassmentType", profile.harassmentType.name)
            putString("fccComplaintNumber", profile.fccComplaintNumber)
            putString("policeCaseNumber", profile.policeCaseNumber)
            putString("carrierCaseNumber", profile.carrierCaseNumber)
        }.apply()
    }
}
