package moe.lyniko.dreambreak.data

enum class AppListMode(val storageValue: Int) {
    /** Pause the timer while a selected app is in the foreground (original behavior). */
    WHITELIST(0),

    /** Only run the timer while a selected app is in the foreground; pause elsewhere. */
    BLACKLIST(1),
    ;

    companion object {
        fun fromStorage(value: Int?): AppListMode {
            return entries.firstOrNull { it.storageValue == value } ?: WHITELIST
        }
    }
}
