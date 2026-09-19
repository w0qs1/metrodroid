package au.id.micolous.metrodroid.transit.ncmc

import au.id.micolous.metrodroid.transit.Trip

enum class NcmcOperator(
    val displayName: String,
    val mode: Trip.Mode
) {
    CHENNAI_METRO(
        displayName = "Chennai Metro",
        mode = Trip.Mode.METRO
    ),

    HYDERABAD_METRO(
        displayName = "Hyderabad Metro",
        mode = Trip.Mode.METRO
    ),

    BENGALURU_METRO(
        displayName = "Bengaluru Metro",
        mode = Trip.Mode.METRO
    ),

    MTC_CHENNAI(
        displayName = "MTC Chennai",
        mode = Trip.Mode.BUS
    ),

    UNKNOWN(
        displayName = "Unknown",
        mode = Trip.Mode.OTHER
    );

    companion object {
        fun resolve(
            acquirerId: Int,
            operatorId: Int
        ): NcmcOperator {
            return when {
                acquirerId == 0x0B &&
                    operatorId == 0x177D ->
                    CHENNAI_METRO

                acquirerId == 0x04 &&
                    operatorId == 0x3630 ->
                    HYDERABAD_METRO

                acquirerId == 0x02 &&
                    operatorId == 0x0001 ->
                    BENGALURU_METRO

                acquirerId == 0x01 &&
                    operatorId == 0x0001 ->
                    MTC_CHENNAI

                else -> UNKNOWN
            }
        }
    }
}