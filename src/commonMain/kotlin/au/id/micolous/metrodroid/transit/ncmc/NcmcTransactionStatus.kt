package au.id.micolous.metrodroid.transit.ncmc

enum class NcmcTransactionStatus(val code: Int) {
    EXIT(0),
    ENTRY(1),
    PENALTY_APPLY(2),
    ONE_TAP_TICKET(3),
    UNKNOWN(-1);

    companion object {
        fun fromCode(code: Int): NcmcTransactionStatus =
            entries.find { it.code == code } ?: UNKNOWN
    }
}