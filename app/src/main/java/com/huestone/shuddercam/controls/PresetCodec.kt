package com.huestone.shuddercam.controls

/**
 * Stable text serialization for user-saved presets. Pure Kotlin, so the format
 * is unit-tested without a device.
 *
 * FORMAT — one record per preset:
 *
 *     id|displayName|description|ENTRY;ENTRY;...
 *     ENTRY := CONTROL_ID=T:raw       T ∈ { I Int, L Long, F Float, B Bool, S String, R IntRange }
 *     IntRange raw := lower~upper
 *
 * Chosen over JSON because it needs no dependency and no reflection, and over
 * an ordinal-indexed binary format because ControlId names survive reordering
 * of the enum. Unknown control ids and malformed entries are skipped rather
 * than failing the whole record, so a preset written by a newer build still
 * loads what this build understands.
 */
object PresetCodec {

    private const val FIELD = '|'
    private const val ENTRY = ';'
    private const val ASSIGN = '='
    private const val TYPE = ':'
    private const val RANGE = '~'

    /** Longest accepted preset name. */
    const val MAX_NAME_LENGTH = 24

    /**
     * Strips the delimiter that would corrupt a record and trims to length.
     */
    fun sanitizeName(raw: String): String =
        raw.replace(FIELD.toString(), " ").trim().take(MAX_NAME_LENGTH)

    /**
     * Turns a display name into a stable id, e.g. "Blue Hour 2" -> "user_blue_hour_2".
     */
    fun idForName(name: String): String {
        val slug = sanitizeName(name)
            .lowercase()
            .map { if (it.isLetterOrDigit()) it else '_' }
            .joinToString("")
            .replace(Regex("_+"), "_") // runs of punctuation collapse to one
            .trim('_')
        return "user_" + slug.ifEmpty { "preset" }
    }

    fun encode(preset: Preset): String {
        val entries = preset.values.entries
            .sortedBy { it.key.name } // deterministic output
            .mapNotNull { (id, value) -> encodeValue(value)?.let { "${id.name}$ASSIGN$it" } }
            .joinToString(ENTRY.toString())
        return listOf(
            preset.id,
            preset.displayName,
            preset.description,
            entries
        ).joinToString(FIELD.toString())
    }

    /**
     * Returns null when the record is too damaged to name a preset at all.
     */
    fun decode(record: String): Preset? {
        val fields = record.split(FIELD, limit = 4)
        if (fields.size < 4) return null

        val id = fields[0]
        val name = fields[1]
        if (id.isBlank() || name.isBlank()) return null

        val values = fields[3]
            .split(ENTRY)
            .filter { it.isNotBlank() }
            .mapNotNull { decodeEntry(it) }
            .toMap()

        return Preset(
            id = id,
            displayName = name,
            description = fields[2],
            values = values
        )
    }

    private fun encodeValue(value: ControlValue): String? = when (value) {
        is ControlValue.IntValue -> "I$TYPE${value.value}"
        is ControlValue.LongValue -> "L$TYPE${value.value}"
        is ControlValue.FloatValue -> "F$TYPE${value.value}"
        is ControlValue.BoolValue -> "B$TYPE${value.value}"
        is ControlValue.StringValue -> "S$TYPE${value.value}"
        is ControlValue.IntRangeValue -> "R$TYPE${value.lower}$RANGE${value.upper}"
    }

    private fun decodeEntry(entry: String): Pair<ControlId, ControlValue>? {
        val assignAt = entry.indexOf(ASSIGN)
        if (assignAt <= 0) return null

        val id = ControlId.entries.firstOrNull { it.name == entry.substring(0, assignAt) }
            ?: return null // written by a build that knows a control this one does not

        val payload = entry.substring(assignAt + 1)
        val typeAt = payload.indexOf(TYPE)
        if (typeAt != 1) return null

        val raw = payload.substring(typeAt + 1)
        val value = when (payload[0]) {
            'I' -> raw.toIntOrNull()?.let { ControlValue.IntValue(it) }
            'L' -> raw.toLongOrNull()?.let { ControlValue.LongValue(it) }
            'F' -> raw.toFloatOrNull()?.let { ControlValue.FloatValue(it) }
            'B' -> raw.toBooleanStrictOrNull()?.let { ControlValue.BoolValue(it) }
            'S' -> ControlValue.StringValue(raw)
            'R' -> {
                val parts = raw.split(RANGE)
                val lower = parts.getOrNull(0)?.toIntOrNull()
                val upper = parts.getOrNull(1)?.toIntOrNull()
                if (lower != null && upper != null) {
                    ControlValue.IntRangeValue(lower, upper)
                } else {
                    null
                }
            }
            else -> null
        } ?: return null

        return id to value
    }
}
