package au.id.micolous.metrodroid.transit.ncmc

import au.id.micolous.metrodroid.multi.Parcelize
import au.id.micolous.metrodroid.card.ncmc.NcmcParser
import au.id.micolous.metrodroid.card.ncmc.NcmcPendingEntry
import au.id.micolous.metrodroid.transit.TransitBalance
import au.id.micolous.metrodroid.transit.TransitCurrency
import au.id.micolous.metrodroid.transit.TransitData
import au.id.micolous.metrodroid.transit.Trip
import au.id.micolous.metrodroid.ui.ListItem

@Parcelize
class NcmcTransitData(
    private val pan: String?,
    private val balancePaise: Long?,
    private val effectiveDate: String? = null,
    private val transactions: List<NcmcTransaction>,
    private val pendingEntry: NcmcPendingEntry? = null
) : TransitData() {

    override val serialNumber: String?
        get() = pan

    override val balance: TransitBalance?
    get() {
        val paise = balancePaise ?: return null

        if (paise < 0 || paise > Int.MAX_VALUE) {
            return null
        }

        return TransitCurrency.INR(paise.toInt())
    }

    override val cardName: String
        get() = "RuPay NCMC"

    override val info: List<ListItem>?
        get() = null

    override val trips: List<Trip>
    get() {
        val transactionTrips = transactions.flatMap { transaction ->
            val exitTrip = NcmcTrip.fromTransaction(
                transaction = transaction,
                timestamp = NcmcParser.parseTimestamp(
                    effectiveDate = effectiveDate,
                    minutesElapsed = transaction.minutesElapsed
                )
            )

            val entryTrip = transaction.entry?.let { entry ->
                NcmcEntryTrip(
                    entry = entry,
                    startTimestamp = NcmcParser.parseTimestamp(
                        effectiveDate = effectiveDate,
                        minutesElapsed = entry.minutesElapsed
                    )
                )
            }

            listOfNotNull(entryTrip, exitTrip)
        }

        val pendingEntryTrip = pendingEntry?.let { entry ->
            NcmcPendingEntryTrip(
                entry = entry,
                startTimestamp = NcmcParser.parseTimestamp(
                    effectiveDate = effectiveDate,
                    minutesElapsed = entry.minutesElapsed
                )
            )
        }

        return (transactionTrips + listOfNotNull(pendingEntryTrip))
            .sortedWith(Trip.Comparator())
    }
}