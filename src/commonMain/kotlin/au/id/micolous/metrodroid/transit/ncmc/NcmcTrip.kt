package au.id.micolous.metrodroid.transit.ncmc

import au.id.micolous.metrodroid.multi.FormattedString
import au.id.micolous.metrodroid.multi.Parcelize
import au.id.micolous.metrodroid.time.TimestampFull
import au.id.micolous.metrodroid.transit.Station
import au.id.micolous.metrodroid.transit.TransitCurrency
import au.id.micolous.metrodroid.transit.TransitCurrencyBase
import au.id.micolous.metrodroid.transit.TransitData
import au.id.micolous.metrodroid.transit.Trip

@Parcelize
class NcmcTrip(
    private val transaction: NcmcTransaction,
    override val startTimestamp: TimestampFull?
) : Trip() {

    override val fare: TransitCurrencyBase?
        get() = TransitCurrency.INR(transaction.amountPaise)

    override val mode: Mode
        get() = NcmcOperator.resolve(
            acquirerId = transaction.acquirerId,
            operatorId = transaction.operatorId
        ).mode

    override fun getAgencyName(isShort: Boolean): FormattedString? {
        return FormattedString(
            NcmcOperator.resolve(
                acquirerId = transaction.acquirerId,
                operatorId = transaction.operatorId
            ).displayName
        )
    }

    override val startStation: Station
    get() = Station.nameOnly(
        NcmcDisplay.terminalName(
            terminalId = transaction.terminalId,
            statusCode = transaction.statusCode
        )
    )

    override val machineID: String?
        get() = null

    override fun getRawFields(
        level: TransitData.RawLevel
    ): String? {
        if (level != TransitData.RawLevel.ALL) {
            return null
        }

        fun terminalString(
            acquirerId: Int,
            operatorId: Int,
            terminalId: Int
        ): String {
            return buildString {
                append("Acq=0x")
                append(
                    acquirerId.toString(16)
                        .uppercase()
                        .padStart(2, '0')
                )

                append(", Op=0x")
                append(
                    operatorId.toString(16)
                        .uppercase()
                        .padStart(4, '0')
                )

                append(", Term=0x")
                append(
                    terminalId.toString(16)
                        .uppercase()
                        .padStart(6, '0')
                )
            }
        }

        return buildString {
            append("sequence=")
            append(transaction.transactionSequence)

            append(", status=")
            append(transaction.statusCode)
            append(" (")
            append(transaction.status.name)
            append(")")

            append(", balance=")
            append(transaction.balancePaise)
            append(" paise")

            append(", minutesElapsed=")
            append(transaction.minutesElapsed)

            append(", terminal=")
            append(
                terminalString(
                    transaction.acquirerId,
                    transaction.operatorId,
                    transaction.terminalId
                )
            )

            if (transaction.entry != null) {
                append(", entryTerminal=")
                append(
                    terminalString(
                        transaction.entry.acquirerId,
                        transaction.entry.operatorId,
                        transaction.entry.terminalId
                    )
                )

                append(", entryMinutesElapsed=")
                append(transaction.entry.minutesElapsed)
            }

            append(", rfu=0x")
            append(
                transaction.rfu.toString(16)
                    .uppercase()
                    .padStart(2, '0')
            )
        }
    }

    companion object {
        fun fromTransaction(
            transaction: NcmcTransaction,
            timestamp: TimestampFull?
        ): NcmcTrip {
            return NcmcTrip(
                transaction = transaction,
                startTimestamp = timestamp
            )
        }
    }
}