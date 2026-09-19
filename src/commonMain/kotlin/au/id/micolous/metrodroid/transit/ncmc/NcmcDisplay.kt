package au.id.micolous.metrodroid.transit.ncmc

object NcmcDisplay {

    fun eventType(statusCode: Int): String {
        return when (statusCode and 0x0F) {
            0b0000 -> "Exit"
            0b0001 -> "Entry"
            0b0010 -> "Penalty"
            0b0011 -> "Ticket"
            else -> "RFU"
        }
    }

    fun terminalName(terminalId: Int, statusCode: Int): String {
        val terminal = "0x" +
            terminalId.toString(16)
                .uppercase()
                .padStart(6, '0')

        return "Terminal $terminal (${eventType(statusCode)})"
    }
}