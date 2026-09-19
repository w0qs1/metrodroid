package au.id.micolous.metrodroid.transit.ncmc

import au.id.micolous.metrodroid.card.ncmc.NcmcPendingEntry
import au.id.micolous.metrodroid.multi.FormattedString
import au.id.micolous.metrodroid.multi.Parcelize
import au.id.micolous.metrodroid.time.TimestampFull
import au.id.micolous.metrodroid.transit.Station
import au.id.micolous.metrodroid.transit.TransitCurrencyBase
import au.id.micolous.metrodroid.transit.TransitData
import au.id.micolous.metrodroid.transit.Trip

@Parcelize
class NcmcPendingEntryTrip(
    private val entry: NcmcPendingEntry,
    override val startTimestamp: TimestampFull?
) : Trip() {

    override val fare: TransitCurrencyBase?
        get() = null

    override val mode: Mode
    get() = NcmcOperator.resolve(
        acquirerId = entry.acquirerId,
        operatorId = entry.operatorId
    ).mode

    override fun getAgencyName(isShort: Boolean): FormattedString? {
        return FormattedString(
            NcmcOperator.resolve(
                acquirerId = entry.acquirerId,
                operatorId = entry.operatorId
            ).displayName
        )
    }

    override val startStation: Station
    get() = Station.nameOnly(
        NcmcDisplay.terminalName(
            terminalId = entry.terminalId,
            statusCode = 0b0001
        )
    )

    override val machineID: String?
        get() = null

    override fun getRawFields(
        level: TransitData.RawLevel
    ): String? {
        return buildString {
            append("status=Pending entry")
            append(", minutesElapsed=")
            append(entry.minutesElapsed)

            append(", terminal=0x")
            append(entry.terminalId.toString(16).uppercase().padStart(6, '0'))
        }
    }
}