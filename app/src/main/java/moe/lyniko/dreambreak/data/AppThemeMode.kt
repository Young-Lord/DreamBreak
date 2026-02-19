package moe.lyniko.dreambreak.data

enum class AppThemeMode(val storageValue: Int) {
    FOLLOW_SYSTEM(0),
    LIGHT(1),
    DARK(2);

    companion object {
        fun fromStorage(value: Int?): AppThemeMode {
            return entries.firstOrNull { it.storageValue == value } ?: FOLLOW_SYSTEM
        }
    }
}
