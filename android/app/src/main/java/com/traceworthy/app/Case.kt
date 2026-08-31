package com.traceworthy.app

import org.json.JSONObject
import java.util.UUID

/**
 * The kind of harassment being documented. Changes the wording of the generated
 * documents and which evidence they emphasize:
 *  - [Silent] leans on the pattern itself (volume, spoofing, silent/short calls).
 *  - [Aggressive] leans on the content of the calls — threats, abuse — evidenced
 *    by the incident timeline built from the user's dated notes.
 */
enum class HarassmentType(val label: String, val shortLabel: String) {
    Silent("Silent / hang-up calls", "Silent"),
    Aggressive("Aggressive / threatening", "Aggressive"),
    Both("Both", "Both");

    val includesAggressive: Boolean get() = this == Aggressive || this == Both
    val includesSilent: Boolean get() = this == Silent || this == Both

    companion object {
        fun fromName(name: String?): HarassmentType =
            entries.firstOrNull { it.name == name } ?: Silent
    }
}

/** How far along a storyboard stage is. [status] on a stage is normally derived; the
 *  user can pin it with an override stored on the [Case]. */
enum class StageStatus { NotStarted, InProgress, Done }

/**
 * One investigation the user is building evidence for. A user can have several at
 * once (a scam call that led to identity theft, say) — each is its own [CaseType]
 * with its own documents, storyboard, and filings.
 *
 * Type-specific scalars live in [typeData] (a keyed bag) so adding a pack never
 * grows this class; structured, repeated evidence lives in typed lists
 * ([fraudItems] for identity theft). Typed accessors below keep call sites clean.
 */
data class Case(
    val id: String = UUID.randomUUID().toString(),
    val type: CaseType = CaseType.PhoneHarassment,
    val title: String = type.displayName,
    val createdAtMillis: Long = System.currentTimeMillis(),

    /** Type-specific scalar fields. Phone: affectedNumber, carrier, harassmentType. */
    val typeData: Map<String, String> = emptyMap(),
    /** Identity-theft evidence rows (empty for other types). */
    val fraudItems: List<FraudItem> = emptyList(),

    /** Confirmation / reference numbers, keyed by filing (e.g. "carrier", "fcc", "police"). */
    val filingNumbers: Map<String, String> = emptyMap(),
    /** User-pinned stage statuses, keyed by stage id. Absent = use the derived status. */
    val stageOverrides: Map<String, StageStatus> = emptyMap(),
) {
    // -- typeData accessors -------------------------------------------------
    fun td(key: String): String = typeData[key].orEmpty()

    fun withTypeData(key: String, value: String): Case =
        copy(typeData = typeData.toMutableMap().apply {
            if (value.isBlank()) remove(key) else put(key, value.trim())
        })

    // -- phone-harassment pack --
    val affectedNumber: String get() = td("affectedNumber")
    val carrier: String get() = td("carrier")
    val harassmentType: HarassmentType get() = HarassmentType.fromName(typeData["harassmentType"])

    fun withHarassmentType(t: HarassmentType): Case =
        copy(typeData = typeData.toMutableMap().apply { put("harassmentType", t.name) })

    /** The harassed number, falling back to the contact number when not set separately. */
    fun affectedLine(myInfo: MyInfo): String = affectedNumber.ifBlank { myInfo.phone }

    // -- filings ----------------------------------------------------------
    fun filing(key: String): String = filingNumbers[key].orEmpty()

    fun withFiling(key: String, value: String): Case =
        copy(filingNumbers = filingNumbers.toMutableMap().apply {
            if (value.isBlank()) remove(key) else put(key, value.trim())
        })

    fun withFraudItems(items: List<FraudItem>): Case = copy(fraudItems = items)

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("type", type.name)
        put("title", title)
        put("createdAtMillis", createdAtMillis)
        put("typeData", JSONObject(typeData))
        put("fraudItems", FraudItem.listToJson(fraudItems))
        put("filingNumbers", JSONObject(filingNumbers))
        put("stageOverrides", JSONObject(stageOverrides.mapValues { it.value.name }))
    }

    companion object {
        fun fromJson(o: JSONObject): Case {
            // Tolerate the M1 shape where phone scalars were top-level fields.
            val td = o.optJSONObject("typeData").toStringMap().toMutableMap()
            listOf("affectedNumber", "carrier", "harassmentType").forEach { k ->
                if (!td.containsKey(k)) o.optString(k).takeIf { it.isNotBlank() }?.let { td[k] = it }
            }
            return Case(
                id = o.optString("id", UUID.randomUUID().toString()),
                type = runCatching { CaseType.valueOf(o.optString("type")) }.getOrDefault(CaseType.PhoneHarassment),
                title = o.optString("title"),
                createdAtMillis = o.optLong("createdAtMillis", System.currentTimeMillis()),
                typeData = td,
                fraudItems = FraudItem.listFromJson(o.optJSONArray("fraudItems")),
                filingNumbers = o.optJSONObject("filingNumbers").toStringMap(),
                stageOverrides = o.optJSONObject("stageOverrides").toStringMap()
                    .mapValues { runCatching { StageStatus.valueOf(it.value) }.getOrDefault(StageStatus.NotStarted) },
            )
        }

        private fun JSONObject?.toStringMap(): Map<String, String> {
            if (this == null) return emptyMap()
            return keys().asSequence().associateWith { getString(it) }
        }
    }
}
