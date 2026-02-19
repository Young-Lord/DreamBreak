package moe.lyniko.dreambreak.core

val DEFAULT_POSTPONE_DURATIONS_SECONDS = listOf(60, 300, 600, 1800)
const val DEFAULT_POSTPONE_DURATION_SECONDS = 60

fun normalizePostponeDurationInput(rawInput: String): String {
    val values = rawInput
        .replace('，', ',')
        .filterNot { it.isWhitespace() }
        .split(',')
        .mapNotNull { token -> token.toIntOrNull()?.takeIf { it > 0 } }
        .distinct()
        .sorted()

    return values.joinToString(",")
}

fun parsePostponeDurations(
    rawInput: String?,
    fallback: List<Int> = DEFAULT_POSTPONE_DURATIONS_SECONDS,
): List<Int> {
    val normalizedInput = normalizePostponeDurationInput(rawInput.orEmpty())
    if (normalizedInput.isNotBlank()) {
        return normalizedInput.split(',').map { it.toInt() }
    }

    val normalizedFallback = fallback
        .filter { it > 0 }
        .distinct()
        .sorted()

    return if (normalizedFallback.isNotEmpty()) {
        normalizedFallback
    } else {
        DEFAULT_POSTPONE_DURATIONS_SECONDS
    }
}

fun parsePostponeDurationsOrEmpty(rawInput: String): List<Int> {
    val normalizedInput = normalizePostponeDurationInput(rawInput)
    if (normalizedInput.isBlank()) {
        return emptyList()
    }

    return normalizedInput.split(',').map { it.toInt() }
}

fun formatPostponeDurations(values: List<Int>): String {
    return parsePostponeDurations(
        rawInput = values.joinToString(","),
        fallback = DEFAULT_POSTPONE_DURATIONS_SECONDS,
    ).joinToString(", ")
}
