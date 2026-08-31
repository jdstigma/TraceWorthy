package com.traceworthy.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * The user's cases, stored as one JSON file in the app's private files dir.
 * Hand-rolled with org.json — no database, no new dependency, same spirit as the
 * SharedPreferences stores. The active-case id lives in the "settings" prefs.
 */
object CaseStore {

    private const val FILE = "cases.json"
    private const val PREFS = "settings"
    private const val KEY_ACTIVE = "active_case_id"

    private fun file(context: Context) = File(context.filesDir, FILE)

    fun all(context: Context): List<Case> {
        val f = file(context)
        if (!f.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).map { Case.fromJson(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    fun save(context: Context, cases: List<Case>) {
        val arr = JSONArray()
        cases.forEach { arr.put(it.toJson()) }
        file(context).writeText(arr.toString())
    }

    fun upsert(context: Context, case: Case): List<Case> {
        val updated = all(context).toMutableList()
        val i = updated.indexOfFirst { it.id == case.id }
        if (i >= 0) updated[i] = case else updated.add(case)
        save(context, updated)
        return updated
    }

    fun delete(context: Context, id: String): List<Case> {
        val updated = all(context).filterNot { it.id == id }
        save(context, updated)
        if (activeCaseId(context) == id) setActiveCaseId(context, updated.firstOrNull()?.id)
        return updated
    }

    fun activeCaseId(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ACTIVE, null)

    fun setActiveCaseId(context: Context, id: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            if (id == null) remove(KEY_ACTIVE) else putString(KEY_ACTIVE, id)
        }.apply()
    }

    /** The active case, or the first one, or null if there are none. */
    fun activeCase(context: Context): Case? {
        val cases = all(context)
        return cases.firstOrNull { it.id == activeCaseId(context) } ?: cases.firstOrNull()
    }

    fun canCreate(context: Context, type: CaseType): Boolean =
        all(context).count { it.type == type } < type.maxInstances

    /**
     * One-time migration from the pre-v2 single "profile" model. If there's no
     * cases file yet, fold the old `user_profile` prefs into one phone-harassment
     * case plus the shared [MyInfo]. Safe to call on every launch.
     */
    fun ensureMigrated(context: Context) {
        if (file(context).exists()) return
        val p = context.getSharedPreferences("user_profile", Context.MODE_PRIVATE)

        val case = Case(
            type = CaseType.PhoneHarassment,
            title = CaseType.PhoneHarassment.displayName,
            affectedNumber = p.getString("affectedNumber", "") ?: "",
            carrier = p.getString("carrier", "") ?: "",
            harassmentType = HarassmentType.fromName(p.getString("harassmentType", null)),
            filingNumbers = buildMap {
                p.getString("fccComplaintNumber", "")?.takeIf { it.isNotBlank() }?.let { put("fcc", it) }
                p.getString("policeCaseNumber", "")?.takeIf { it.isNotBlank() }?.let { put("police", it) }
                p.getString("carrierCaseNumber", "")?.takeIf { it.isNotBlank() }?.let { put("carrier", it) }
            },
        )
        save(context, listOf(case))
        setActiveCaseId(context, case.id)

        // Drop the now-migrated case-specific keys from the profile prefs; MyInfoStore
        // keeps reading the shared ones from the same file.
        p.edit().apply {
            listOf("affectedNumber", "carrier", "harassmentType",
                "fccComplaintNumber", "policeCaseNumber", "carrierCaseNumber").forEach { remove(it) }
        }.apply()
    }

    /** Guarantee at least one case exists (used after the last one is deleted). */
    fun ensureAtLeastOne(context: Context): Case {
        all(context).firstOrNull()?.let { return it }
        val c = Case(type = CaseType.PhoneHarassment)
        save(context, listOf(c))
        setActiveCaseId(context, c.id)
        return c
    }
}
