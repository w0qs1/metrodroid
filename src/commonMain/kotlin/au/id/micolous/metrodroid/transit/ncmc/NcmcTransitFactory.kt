package au.id.micolous.metrodroid.transit.ncmc

import au.id.micolous.metrodroid.card.ncmc.NcmcApplication
import au.id.micolous.metrodroid.card.ncmc.NcmcCardTransitFactory
import au.id.micolous.metrodroid.transit.TransitData
import au.id.micolous.metrodroid.transit.TransitIdentity

object NCMCTransitFactory : NcmcCardTransitFactory {

    override val allCards = listOf(
        NcmcTransitData.CARD_INFO
    )

    override fun parseTransitIdentity(
        card: NcmcApplication
    ): TransitIdentity? {
        return TransitIdentity(
            name = "RuPay NCMC",
            serialNumber = card.pan
        )
    }

    override fun parseTransitData(
        card: NcmcApplication
    ): TransitData? {
        return NcmcTransitData(
            pan = card.pan,
            balancePaise = card.balancePaise,
            effectiveDate = card.effectiveDate,
            transactions = card.transactions,
            pendingEntry = card.pendingEntry
        )
    }

    override fun check(card: NcmcApplication): Boolean {
        return card.pan != null ||
            card.balancePaise != null ||
            card.transactions.isNotEmpty() ||
            card.pendingEntry != null
    }
}