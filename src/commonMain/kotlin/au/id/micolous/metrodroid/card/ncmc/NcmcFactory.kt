package au.id.micolous.metrodroid.card.ncmc

import au.id.micolous.metrodroid.card.TagReaderFeedbackInterface
import au.id.micolous.metrodroid.card.iso7816.ISO7816Application
import au.id.micolous.metrodroid.card.iso7816.ISO7816ApplicationFactory
import au.id.micolous.metrodroid.card.iso7816.ISO7816ApplicationMutableCapsule
import au.id.micolous.metrodroid.card.iso7816.ISO7816Protocol
import au.id.micolous.metrodroid.card.iso7816.ISO7816TLV
import au.id.micolous.metrodroid.transit.ncmc.NcmcEntry
import au.id.micolous.metrodroid.transit.ncmc.NcmcTransaction
import au.id.micolous.metrodroid.multi.Log
import au.id.micolous.metrodroid.util.ImmutableByteArray
import kotlinx.serialization.KSerializer
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.random.Random

class NcmcFactory : ISO7816ApplicationFactory {
    private data class NcmcMetadata(
        val pan: String? = null,
        val effectiveDate: String? = null,
        val expiryDate: String? = null,
        val version: String? = null,
        val cardLanguage: String? = null,
        val cardType: String? = null
    )

    private data class Sfi16Metadata(
        val atc: Long? = null
    )

    data class Sfi16Result(
        val transactions: List<NcmcTransaction>,
        val pendingEntry: NcmcPendingEntry?
    )

    companion object {
        private const val TAG = "NcmcFactory"
        private const val CSA_LENGTH = 96

        private val NCMC_AID =
            ImmutableByteArray.fromHex("A0000005241010")
    }

    override val applicationNames:
        Collection<ImmutableByteArray>
        get() = listOf(NCMC_AID)

    /*
     * Do not stop processing other registered application factories.
     */
    override val stopAfterFirstApp: Boolean
        get() = false

    override val fixedAppIds: Boolean
        get() = true

    override val typeMap:
        Map<String, KSerializer<out ISO7816Application>>
        get() = mapOf(
            NcmcApplication.TYPE to NcmcApplication.serializer()
        )

    override fun dumpTag(
        protocol: ISO7816Protocol,
        capsule: ISO7816ApplicationMutableCapsule,
        feedbackInterface: TagReaderFeedbackInterface,
        presentAids: List<ImmutableByteArray?>
    ): List<ISO7816Application>? {

        Log.d(TAG, "Starting NCMC application processing")

        /*
         * Step 1:
         * Send GPO using the captured PDOL data.
         *
         * NCMC_GPO_DATA already contains:
         *
         * 83 <length> <PDOL data>
         */
        val gpoResponse = try {
            protocol.sendRequest(
                ISO7816Protocol.CLASS_80,
                0xA8.toByte(),
                0x00,
                0x00,
                0x00,
                buildNcmcGpoData()
            )
        } catch (e: Exception) {
            Log.d(TAG, "NCMC GPO failed", e)
            null
        }

        if (gpoResponse == null) {
            return emptyList()
        }

        Log.d(TAG, "GPO response: $gpoResponse")

        val df33 = ISO7816TLV.findBERTLV(
            gpoResponse,
            "DF33",
            false
        )

        Log.d(TAG, "DF33 value: ${df33 ?: "NOT FOUND"}")
        
        val balancePaise = df33?.let {
            NcmcParser.parseDf33Balance(it)
        }

        val ncmcMetadata = df33?.let {
            parseNcmcMetadataFromDf33(it)
        } ?: NcmcMetadata()


        val sfi16Metadata = findSfi16Metadata(protocol)

        Log.d(TAG, "Parsed balancePaise: $balancePaise")

        /*
         * Step 2:
         * Dump SFI files and records.
         *
         * The existing capsule implementation reads SFI 1..31.
         * This also allows us to inspect records for PAN tags 5A/57.
         */
        feedbackInterface.updateProgressBar(0, 32)

        capsule.dumpAllSfis(
            protocol = protocol,
            feedbackInterface = feedbackInterface,
            start = 0,
            total = 32
        )

        /*
         * Step 3:
         * Search all dumped SFI records for the PAN.
         */
        val pan = findPan(protocol)

        Log.d(TAG, "Extracted NCMC PAN: ${pan ?: "not found"}")

        val effectiveDate = findDate(
            protocol = protocol,
            tag = "5F25"
        )

        val expiryDate = findDate(
            protocol = protocol,
            tag = "5F24"
        )

        Log.d(
            TAG,
            "Extracted NCMC effective date: ${effectiveDate ?: "not found"}"
        )

        Log.d(
            TAG,
            "Extracted NCMC expiry date: ${expiryDate ?: "not found"}"
        )

        val df33Transactions: List<NcmcTransaction> =
            df33?.let {
                NcmcParser.parseHistory(it, effectiveDate)
            } ?: emptyList()

        val sfi16Result = findSfi16Transactions(
            protocol = protocol,
            effectiveDate = effectiveDate
        )

        val sfi16Transactions = sfi16Result.transactions

        val transactionMap =
            linkedMapOf<Triple<Int, Int, Int>, NcmcTransaction>()

        fun transactionKey(
            transaction: NcmcTransaction
        ): Triple<Int, Int, Int> {
            return Triple(
                transaction.transactionSequence,
                transaction.minutesElapsed,
                transaction.terminalId
            )
        }

        // Add DF33 transactions first.
        for (transaction in df33Transactions) {
            transactionMap[transactionKey(transaction)] = transaction
        }

        // Add SFI16 transactions second.
        // SFI16 entries with the same key overwrite the DF33 version.
        for (transaction in sfi16Transactions) {
            transactionMap[transactionKey(transaction)] = transaction
        }

        val transactions = transactionMap.values.toList()

        Log.d(
            TAG,
            "Parsed NCMC transactions: ${transactions.size}"
        )

        return listOf(
            NcmcApplication(
                generic = capsule.freeze(),
                pan = pan,
                effectiveDate = effectiveDate,
                balancePaise = balancePaise,
                transactions = transactions,
                ncmcVersion = ncmcMetadata.version,
                cardLanguage = ncmcMetadata.cardLanguage,
                expiryDate = expiryDate,
                cardAtc = sfi16Metadata.atc,
                pendingEntry = sfi16Result.pendingEntry
            )
        )
    }

    private fun buildNcmcGpoData(): ImmutableByteArray {
        val now = Clock.System.now()
            .toLocalDateTime(TimeZone.currentSystemDefault())

        fun bcd(value: Int): String =
            value.toString().padStart(2, '0')

        val dateBcd =
            bcd(now.year % 100) +
            bcd(now.month.ordinal + 1) +
            bcd(now.dayOfMonth)

        val timeBcd =
            bcd(now.hour) +
            bcd(now.minute) +
            bcd(now.second)

        val randomBytes = Random.nextBytes(4)

        val randomHex = randomBytes
            .joinToString("") {
                (it.toInt() and 0xFF)
                    .toString(16)
                    .uppercase()
                    .padStart(2, '0')
            }

        return ImmutableByteArray.fromHex(
            "8325" +
            "FF80F00001" +       // 9F40 (5)
            "0040000000" +       // DF3A (5)
            "000848" +           // 9F33 (3)
            "0002" +             // 9F09 (2)
            "0743" +             // 9F15 (2)
            dateBcd +            // 9A (3)
            timeBcd +            // 9F21 (3)
            randomHex +          // 9F37 (4)
            "FF01" +             // DF16 (2)
            "3132333435363738"   // 9F1C (8)
        )
    }

    private fun findPan(
        protocol: ISO7816Protocol
    ): String? {

        for (sfi in 1..31) {
            for (recordNumber in 1..255) {

                val record = try {
                    protocol.readRecord(
                        sfi = sfi,
                        recordNumber = recordNumber.toByte(),
                        length = 0
                    )
                } catch (_: Exception) {
                    null
                }

                if (record == null || record.isEmpty()) {
                    continue
                }

                val pan = NcmcParser.extractPan(record)

                if (!pan.isNullOrBlank()) {
                    return pan
                }
            }
        }

        return null
    }

    private fun findDate(
        protocol: ISO7816Protocol,
        tag: String
    ): String? {

        for (sfi in 1..31) {
            for (recordNumber in 1..255) {

                val record = try {
                    protocol.readRecord(
                        sfi = sfi,
                        recordNumber = recordNumber.toByte(),
                        length = 0
                    )
                } catch (_: Exception) {
                    null
                }

                if (record == null || record.isEmpty()) {
                    continue
                }

                val date = NcmcParser.extractDate(
                    record = record,
                    tag = tag
                )

                if (date != null &&
                    NcmcParser.isValidEffectiveDate(date)
                ) {
                    return date
                }
            }
        }

        return null
    }

    private fun decodeCardLanguage(
        languageCode: Int
    ): String? {
        return when (languageCode) {
            0 -> "English"
            1 -> "Hindi"
            2 -> "Bengali"
            3 -> "Marathi"
            4 -> "Telugu"
            5 -> "Tamil"
            6 -> "Gujarati"
            7 -> "Urdu"
            8 -> "Kannada"
            9 -> "Odia"
            10 -> "Malayalam"
            11 -> "Punjabi"
            12 -> "Sanskrit"
            13 -> "Assamese"
            14 -> "Maithili"
            15 -> "Santali"
            16 -> "Kashmiri"
            17 -> "Nepali"
            18 -> "Sindhi"
            19 -> "Dogri"
            20 -> "Konkani"
            21 -> "Manipuri"
            22 -> "Bodo"
            else -> null
        }
    }

    private fun parseNcmcMetadataFromDf33(
        df33: ImmutableByteArray
    ): NcmcMetadata {

        val csaOffset = 32

        if (df33.size < csaOffset + 2) {
            Log.d(TAG, "DF33 too short for CSA general data")
            return NcmcMetadata()
        }

        val versionByte =
            df33[csaOffset].toInt() and 0xFF

        val major = (versionByte ushr 4) and 0x0F
        val minor = versionByte and 0x0F

        val version =
            if (major <= 9 && minor <= 9) {
                "$major.$minor"
            } else {
                null
            }

        val languageByte =
            df33[csaOffset + 1].toInt() and 0xFF

        val languageCode = languageByte ushr 3

        val language = decodeCardLanguage(languageCode)

        Log.d(TAG, "NCMC version: ${version ?: "not found"}")
        Log.d(TAG, "NCMC language: ${language ?: "RFU"}")

        return NcmcMetadata(
            version = version,
            cardLanguage = language
        )
    }

    private fun findSfi16Metadata(
        protocol: ISO7816Protocol
    ): Sfi16Metadata {

        val record = try {
            protocol.readRecord(
                sfi = 16,
                recordNumber = 1,
                length = 0
            )
        } catch (e: Exception) {
            Log.d(TAG, "Failed to read SFI16 record 1", e)
            return Sfi16Metadata()
        } ?: run {
            Log.d(TAG, "SFI16 record 1 not found")
            return Sfi16Metadata()
        }

        if (record.size < 16) {
            Log.d(TAG, "SFI16 record 1 is too short: ${record.size}")
            return Sfi16Metadata()
        }

        val atc =
            ((record[14].toInt() and 0xFF) shl 8) or
            (record[15].toInt() and 0xFF)

        Log.d(TAG, "SFI16 ATC: $atc")

        return Sfi16Metadata(
            atc = atc.toLong()
        )
    }

    private fun findSfi16Transactions(
        protocol: ISO7816Protocol,
        effectiveDate: String?
    ): Sfi16Result {
        val allTransactions = mutableListOf<NcmcTransaction>()
        val validationData = mutableListOf<NcmcParser.ValidationData>()

        for (recordNumber in 1..255) {
            val record = try {
                protocol.readRecord(
                    sfi = 16,
                    recordNumber = recordNumber.toByte(),
                    length = 0
                )
            } catch (_: Exception) {
                null
            } ?: continue

            if (record.size < CSA_LENGTH) {
                continue
            }

            val csa = extractLastCsa(record)
                ?: continue

            Log.d(
                "NCMC",
                "SFI16 record=$recordNumber, CSA=${csa.toHexString()}"
            )

            Log.d(
                "NCMC",
                "potential=${isPotentialTransactionLog(csa)}"
            )

            // Parse validation data independently of transaction history.
            val validation = NcmcParser.parseValidationData(csa)

            Log.d(
                "NCMC",
                "record=$recordNumber, validation=$validation"
            )

            if (validation != null) {
                validationData += validation
            }

            // Only parse transaction history for potential transaction logs.
            if (isPotentialTransactionLog(csa)) {
                allTransactions += NcmcParser.parseCsaHistory(
                    csa = csa,
                    effectiveDate = effectiveDate
                )
            }
        }

        Log.d(
            "NCMC",
            "allTransactions=${allTransactions.size}, " +
                "validationData=${validationData.size}"
        )

        return attachEntryData(
            transactions = allTransactions,
            validations = validationData
        )
    }

    private fun attachEntryData(
        transactions: List<NcmcTransaction>,
        validations: List<NcmcParser.ValidationData>
    ): Sfi16Result {
        val entries = validations
            .filter { it.statusCode == 1 }
            .sortedBy { it.minutesElapsed }
            .toMutableList()

        val exits = transactions
            .filter { it.statusCode == 0 }
            .sortedBy { it.minutesElapsed }

        val usedEntryIndexes = mutableSetOf<Int>()
        val transactionMap = transactions.associateBy {
            Triple(
                it.transactionSequence,
                it.minutesElapsed,
                it.terminalId
            )
        }.toMutableMap()

        for (exit in exits) {
            val matchingEntryIndex = entries.indices
                .filter { index ->
                    index !in usedEntryIndexes &&
                        entries[index].minutesElapsed <= exit.minutesElapsed
                }
                .maxByOrNull { index ->
                    entries[index].minutesElapsed
                }

            if (matchingEntryIndex == null) {
                continue
            }

            val entry = entries[matchingEntryIndex]
            usedEntryIndexes += matchingEntryIndex

            val key = Triple(
                exit.transactionSequence,
                exit.minutesElapsed,
                exit.terminalId
            )

            transactionMap[key] = exit.copy(
                entry = NcmcEntry(
                    acquirerId = entry.acquirerId,
                    operatorId = entry.operatorId,
                    terminalId = entry.terminalId,
                    minutesElapsed = entry.minutesElapsed
                )
            )
        }

        val pendingEntry = entries
            .indices
            .firstOrNull { it !in usedEntryIndexes }
            ?.let { index ->
                val entry = entries[index]

                NcmcPendingEntry(
                    acquirerId = entry.acquirerId,
                    operatorId = entry.operatorId,
                    terminalId = entry.terminalId,
                    minutesElapsed = entry.minutesElapsed
                )
            }

        Log.d(
            "NCMC",
            "Entry count=${entries.size}, " +
                "used=${usedEntryIndexes.size}, " +
                "pending=$pendingEntry"
        )

        return Sfi16Result(
            transactions = transactionMap.values.toList(),
            pendingEntry = pendingEntry
        )
    }

    private fun extractLastCsa(
        record: ImmutableByteArray
    ): ImmutableByteArray? {
        if (record.size < CSA_LENGTH) {
            return null
        }

        val hex = record.toHexString()

        val csaHex = hex.takeLast(CSA_LENGTH * 2)

        return ImmutableByteArray.fromHex(csaHex)
    }

    private fun isPotentialTransactionLog(
        csa: ImmutableByteArray
    ): Boolean {
        if (csa.size < CSA_LENGTH) {
            return false
        }

        val hex = csa.toHexString().uppercase()

        // Reject empty or version-only-looking data.
        if (hex.all { it == '0' }) {
            return false
        }

        val nonZeroBytes = (0 until csa.size)
            .count { csa[it].toInt() != 0 }

        return nonZeroBytes > 4
    }
}