package au.id.micolous.metrodroid.transit.ncmc

import au.id.micolous.metrodroid.transit.Trip

enum class NcmcOperator(
    val acquirerId: Int,
    val operatorId: Int,
    val displayName: String,
    val mode: Trip.Mode
) {
    CHENNAI_METRO(
        acquirerId = 0x0B,
        operatorId = 0x177D,
        displayName = "Chennai Metro",
        mode = Trip.Mode.METRO
    ),


    BENGALURU_METRO(
        acquirerId = 0x02,
        operatorId = 0x0001,
        displayName = "Bengaluru Metro",
        mode = Trip.Mode.METRO
    ),

    MTC_CHENNAI(
        acquirerId = 0x01,
        operatorId = 0x0001,
        displayName = "MTC Chennai",
        mode = Trip.Mode.BUS
    ),

    HYDERABAD_METRO(
        acquirerId = 0x04,
        operatorId = 0x3630,
        displayName = "Hyderabad Metro",
        mode = Trip.Mode.METRO
    ),
    
    UNKNOWN(
        acquirerId = -1,
        operatorId = -1,
        displayName = "Unknown",
        mode = Trip.Mode.OTHER
    );

    companion object {
        fun resolve(
            acquirerId: Int,
            operatorId: Int
        ): NcmcOperator {
            return entries.firstOrNull {
                it.acquirerId == acquirerId &&
                    it.operatorId == operatorId
            } ?: UNKNOWN
        }
    }
}