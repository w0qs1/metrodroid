package au.id.micolous.metrodroid.card.ncmc

import au.id.micolous.metrodroid.card.iso7816.ISO7816TLV
import au.id.micolous.metrodroid.util.ImmutableByteArray
import au.id.micolous.metrodroid.transit.ncmc.NcmcTransaction
import au.id.micolous.metrodroid.time.Epoch
import au.id.micolous.metrodroid.time.MetroTimeZone
import au.id.micolous.metrodroid.time.TimestampFull
import au.id.micolous.metrodroid.multi.Log

object NcmcParser {

    private val TZ = MetroTimeZone.INDIA

    private const val DF33_HEADER_LENGTH = 32
    private const val CSA_LENGTH = 96
    private const val HISTORY_START = 21
    private const val HISTORY_ENTRY_LENGTH = 17
    private const val HISTORY_COUNT = 4

    data class ValidationData(
        val acquirerId: Int,
        val operatorId: Int,
        val terminalId: Int,
        val minutesElapsed: Int,
        val fareUnits: Int,
        val routeNumber: Int,
        val serviceProviderData: Int,
        val statusCode: Int,
        val rfu: Int
    )

    /**
     * Attempts to extract PAN from a BER-TLV record.
     *
     * Priority:
     * 1. Tag 5A - Application PAN
     * 2. Tag 57 - Track 2 Equivalent Data
     */
    fun extractPan(data: ImmutableByteArray): String? {
        val panData = ISO7816TLV.findBERTLV(data, "5A", false)

        if (panData != null) {
            return decodePan(panData)
        }

        val track2Data = ISO7816TLV.findBERTLV(data, "57", false)

        if (track2Data != null) {
            return decodeTrack2(track2Data)
        }

        return null
    }

    /**
     * DF33 Service Related Data balance.
     *
     * Bytes 23..28 contain six BCD bytes.
     * Each digit represents 1 paise.
     */
    fun parseDf33Balance(
        df33Data: ImmutableByteArray
    ): Long? {
        Log.d("NcmcParser", "DF33 length: ${df33Data.size}")

        if (df33Data.size < 29) {
            Log.d("NcmcParser", "DF33 too short")
            return null
        }

        val hex = df33Data.toHexString()
        val balanceBcd = hex.substring(46, 58)

        Log.d("NcmcParser", "Balance BCD: $balanceBcd")

        if (balanceBcd.any { it !in '0'..'9' }) {
            Log.d("NcmcParser", "Invalid BCD")
            return null
        }

        val balance = balanceBcd.toLongOrNull()

        Log.d("NcmcParser", "Balance paise: $balance")

        return balance
    }

    private fun decodePan(data: ImmutableByteArray): String {
        return data.toHexString()
            .uppercase()
            .trimEnd('F')
            .chunked(4)
            .joinToString(" ")
    }

    private fun decodeTrack2(data: ImmutableByteArray): String? {
        val digits = data.toHexString().uppercase()

        val separator = digits.indexOf('D')
        if (separator < 0) {
            return null
        }

        return digits.substring(0, separator)
    }

    private fun isValidCsa(
        csa: ImmutableByteArray
    ): Boolean {
        if (csa.size < CSA_LENGTH) {
            return false
        }

        val hex = csa.toHexString().uppercase()

        // Reject completely empty CSA data.
        if (hex.all { it == '0' }) {
            return false
        }

        // Reject data that contains no meaningful history entries.
        val historyStart = HISTORY_START * 2
        val historyEnd =
            (HISTORY_START + HISTORY_COUNT * HISTORY_ENTRY_LENGTH) * 2

        val historyHex = hex.substring(historyStart, historyEnd)

        if (historyHex.all { it == '0' }) {
            return false
        }

        return true
    }

    fun extractEffectiveDate(
        record: ImmutableByteArray
    ): String? =
        extractDate(record, "5F25")

    fun extractDate(
        record: ImmutableByteArray,
        tag: String
    ): String? {
        val date = ISO7816TLV.findBERTLV(
            record,
            tag,
            false
        ) ?: return null

        if (date.size < 3) {
            return null
        }

        return date
            .toHexString()
            .uppercase()
            .take(6)
    }

    private fun logCsaBytes(
        csa: ImmutableByteArray
    ) {
        val hex = csa.toHexString().uppercase()

        Log.d(
            "NcmcParser",
            "CSA (${csa.size} bytes): $hex"
        )

        for (offset in csa.indices) {
            Log.d(
                "NcmcParser",
                "CSA[$offset] = ${
                    (csa[offset].toInt() and 0xFF)
                        .toString(16)
                        .uppercase()
                        .padStart(2, '0')
                }"
            )
        }
    }

    fun parseValidationData(
        csa: ImmutableByteArray
    ): ValidationData? {
        if (csa.size < HISTORY_START) {
            return null
        }

        // Validation data must begin with marker 0x31.
        if ((csa[0].toInt() and 0xFF) != 0x31) {
            return null
        }

        val acquirerId = csa[4].toInt() and 0xFF
        val operatorId = readUInt16(csa, 5)
        val terminalId = readUInt24(csa, 7)
        val minutesElapsed = readUInt24(csa, 10)

        val fareUnits = readUInt16(csa, 13)
        val routeNumber = readUInt16(csa, 15)
        val serviceProviderData = readUInt24(csa, 17)

        val statusByte = csa[20].toInt() and 0xFF
        val statusCode = statusByte ushr 4
        val rfu = statusByte and 0x0F

        return ValidationData(
            acquirerId = acquirerId,
            operatorId = operatorId,
            terminalId = terminalId,
            minutesElapsed = minutesElapsed,
            fareUnits = fareUnits,
            routeNumber = routeNumber,
            serviceProviderData = serviceProviderData,
            statusCode = statusCode,
            rfu = rfu
        )
    }

    fun parseCsaHistory(
        csa: ImmutableByteArray,
        effectiveDate: String?
    ): List<NcmcTransaction> {
        if (csa.size < CSA_LENGTH) {
            return emptyList()
        }

        // logCsaBytes(csa)

        val validation = parseValidationData(csa)

        Log.d(
            "NcmcParser",
            "Validation: $validation"
        )

        effectiveDate
            ?.let { parseEffectiveDate(it) }
            ?: return emptyList()

        if (!isValidCsa(csa)) {
            return emptyList()
        }

        val result = mutableListOf<NcmcTransaction>()

        for (index in 0 until HISTORY_COUNT) {
            val offset = HISTORY_START +
                    index * HISTORY_ENTRY_LENGTH

            val transaction = parseHistoryEntry(csa, offset)

            if (transaction != null) {
                result += transaction
            }
        }

        return result
    }

    fun parseHistory(
        df33: ImmutableByteArray,
        effectiveDate: String?
    ): List<NcmcTransaction> {
        if (df33.size < DF33_HEADER_LENGTH + CSA_LENGTH) {
            return emptyList()
        }

        val parsedDate = effectiveDate
            ?.let { parseEffectiveDate(it) }
            ?: return emptyList()

        val result = mutableListOf<NcmcTransaction>()

        for (index in 0 until HISTORY_COUNT) {
            val offset = DF33_HEADER_LENGTH +
                    HISTORY_START +
                    index * HISTORY_ENTRY_LENGTH

            val transaction = parseHistoryEntry(df33, offset)

            if (transaction != null) {
                result += transaction
            }
        }

        return result
    }

    private fun parseHistoryEntry(
        data: ImmutableByteArray,
        offset: Int
    ): NcmcTransaction? {
        if (offset + HISTORY_ENTRY_LENGTH > data.size) {
            return null
        }

        val acquirerId = data[offset].toInt() and 0xFF

        val operatorId = readUInt16(data, offset + 1)
        val terminalId = readUInt24(data, offset + 3)

        val minutesElapsed = readUInt24(data, offset + 6)
        val transactionSequence = readUInt16(data, offset + 9)
        val amountUnits = readUInt16(data, offset + 11)

        val balanceStatus = readUInt24(data, offset + 13)

        val balanceUnits = balanceStatus ushr 4
        val statusCode = balanceStatus and 0x0F

        val rfu = data[offset + 16].toInt() and 0xFF

        return NcmcTransaction(
            acquirerId = acquirerId,
            operatorId = operatorId,
            terminalId = terminalId,
            minutesElapsed = minutesElapsed,
            transactionSequence = transactionSequence,
            amountUnits = amountUnits,
            balanceUnits = balanceUnits,
            statusCode = statusCode,
            rfu = rfu
        )
    }

    fun parseTimestamp(
        effectiveDate: String?,
        minutesElapsed: Int
    ): TimestampFull? {
        val date = effectiveDate
            ?.let { parseEffectiveDate(it) }
            ?: return null

        return Epoch.local(
            year = date.first,
            tz = TZ
        ).dayMinute(
            d = date.second,
            m = minutesElapsed
        )
    }

    private fun parseEffectiveDate(
        date: String
    ): Pair<Int, Int>? {
        val value = date.trim()

        if (value.length != 6 || !value.all { it.isDigit() }) {
            return null
        }

        val year = 2000 + value.substring(0, 2).toInt()
        val month = value.substring(2, 4).toInt()
        val day = value.substring(4, 6).toInt()

        if (month !in 1..12 || day !in 1..31) {
            return null
        }

        // Day zero-based from January 1.
        val dayOfYear = daysBeforeMonth(year, month) + day - 1

        return year to dayOfYear
    }

    private fun daysBeforeMonth(
        year: Int,
        month: Int
    ): Int {
        val days = intArrayOf(
            31, 28, 31, 30, 31, 30,
            31, 31, 30, 31, 30, 31
        )

        val leapYear = year % 4 == 0 &&
                (year % 100 != 0 || year % 400 == 0)

        var result = 0

        for (index in 0 until month - 1) {
            result += days[index]

            if (index == 1 && leapYear) {
                result++
            }
        }

        return result
    }

    private fun readUInt16(
        data: ImmutableByteArray,
        offset: Int
    ): Int {
        return ((data[offset].toInt() and 0xFF) shl 8) or
                (data[offset + 1].toInt() and 0xFF)
    }

    private fun readUInt24(
        data: ImmutableByteArray,
        offset: Int
    ): Int {
        return ((data[offset].toInt() and 0xFF) shl 16) or
                ((data[offset + 1].toInt() and 0xFF) shl 8) or
                (data[offset + 2].toInt() and 0xFF)
    }

    fun isValidEffectiveDate(date: String): Boolean =
        parseEffectiveDate(date) != null
}