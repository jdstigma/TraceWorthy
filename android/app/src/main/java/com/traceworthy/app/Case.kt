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
 * with its own documents, storyboard, and filing numbers.
 *
 * While only the phone-harassment pack exists, the pack-specific fields
 * ([affectedNumber], [carrier], [harassmentType]) live directly on this class.
 * When a second pack lands, move type-specific data into a keyed bag.
 */
data class Case(
    val id: String = UUID.randomUUID().toString(),
    val type: CaseType = CaseType.PhoneHarassment,
    val title: String = type.displayName,
    val createdAtMillis: Long = System.currentTimeMillis(),

    // -- phone-harassment pack --
    val affectedNumber: String = "",   // the line receiving the harassing calls
    val carrier: String = "",
    val harassmentType: HarassmentType = HarassmentType.Silent,

    /** Confirmation / reference numbers, keyed by filing: "carrier", "fcc", "police". */
    val filingNumbers: Map<String, String> = emptyMap(),
    /** User-pinned stage statuses, keyed by stage id. Absent = use the derived status. */
    val stageOverrides: Map<String, StageStatus> = emptyMap(),
) {
    /** The harassed number, falling back to the contact number when not set separately. */
    fun affectedLine(myInfo: MyInfo): String = affectedNumber.ifBlank { myInfo.phone }

    fun filing(key: String): String = filingNumbers[key].orEmpty()

    fun withFiling(key: String, value: String): Case =
        copy(filingNumbers = filingNumbers.toMutableMap().apply {
            if (value.isBlank()) remove(key) else put(key, value.trim())
        })

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("type", type.name)
        put("title", title)
        put("createdAtMillis", createdAtMillis)
        put("affectedNumber", affectedNumber)
        put("carrier", carrier)
        put("harassmentType", harassmentType.name)
        put("filingNumbers", JSONObject(filingNumbers))
        put("stageOverrides", JSONObject(stageOverrides.mapValues { it.value.name }))
    }

    companion object {
        fun fromJson(o: JSONObject): Case = Case(
            id = o.optString("id", UUID.randomUUID().toString()),
            type = runCatching { CaseType.valueOf(o.optString("type")) }.getOrDefault(CaseType.PhoneHarassment),
            title = o.optString("title"),
            createdAtMillis = o.optLong("createdAtMillis", System.currentTimeMillis()),
            affectedNumber = o.optString("affectedNumber"),
            carrier = o.optString("carrier"),
            harassmentType = HarassmentType.fromName(o.optString("harassmentType")),
            filingNumbers = o.optJSONObject("filingNumbers").toStringMap(),
            stageOverrides = o.optJSONObject("stageOverrides").toStringMap()
                .mapValues { runCatching { StageStatus.valueOf(it.value) }.getOrDefault(StageStatus.NotStarted) },
        )

        private fun JSONObject?.toStringMap(): Map<String, String> {
            if (this == null) return emptyMap()
            return keys().asSequence().associateWith { getString(it) }
        }
    }
}
