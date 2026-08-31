package com.traceworthy.app

import android.content.Context

/**
 * Your identity — the parts that are the same no matter which case you're working.
 * Entered once on the "My info" screen; every generated document pulls from here.
 * Case-specific details (the affected number, carrier, harassment type, and the
 * FCC / police / carrier case numbers) live on the [Case], not here.
 *
 * Stored locally in SharedPreferences — nothing leaves the device.
 */
data class MyInfo(
    val fullName: String = "",
    val phone: String = "",          // where police / FCC / the carrier should reach you
    val email: String = "",
    val addressCity: String = "",
    val state: String = "",          // two-letter USPS code, e.g. "CA"
) {
    /** True once the minimum a document needs is present. */
    val isReadyForDocuments: Boolean
        get() = fullName.isNotBlank() && phone.isNotBlank()
}

object MyInfoStore {

    // Kept as "user_profile" so existing installs migrate transparently.
    private const val PREFS = "user_profile"

    fun load(context: Context): MyInfo {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return MyInfo(
            fullName = p.getString("fullName", "") ?: "",
            phone = p.getString("phone", "") ?: "",
            email = p.getString("email", "") ?: "",
            addressCity = p.getString("addressCity", "") ?: "",
            state = p.getString("state", "") ?: "",
        )
    }

    fun save(context: Context, info: MyInfo) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            putString("fullName", info.fullName)
            putString("phone", info.phone)
            putString("email", info.email)
            putString("addressCity", info.addressCity)
            putString("state", info.state)
        }.apply()
    }
}
