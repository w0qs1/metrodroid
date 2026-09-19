package au.id.micolous.metrodroid.card.ncmc

import au.id.micolous.metrodroid.transit.CardTransitFactory
import au.id.micolous.metrodroid.transit.ncmc.NCMCTransitFactory

object NCMCRegistry {
    val allFactories: List<CardTransitFactory<*>> = listOf(
        NCMCTransitFactory
    )
}