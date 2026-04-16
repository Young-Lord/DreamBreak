package moe.lyniko.dreambreak.data

enum class QsTileClickAction(val storageValue: Int) {
    NONE(0),
    OPEN_APP(1),
    OPEN_POSTPONE_PICKER(2),
    TOGGLE_ENABLED(3);

    companion object {
        fun fromStorage(value: Int?): QsTileClickAction {
            return entries.firstOrNull { it.storageValue == value } ?: TOGGLE_ENABLED
        }
    }
}
