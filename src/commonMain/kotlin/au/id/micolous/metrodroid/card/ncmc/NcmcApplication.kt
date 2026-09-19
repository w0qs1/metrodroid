package au.id.micolous.metrodroid.card.ncmc

import au.id.micolous.metrodroid.card.iso7816.ISO7816Application
import au.id.micolous.metrodroid.card.iso7816.ISO7816ApplicationCapsule
import au.id.micolous.metrodroid.card.iso7816.ISO7816Card
import au.id.micolous.metrodroid.transit.TransitData
import au.id.micolous.metrodroid.transit.TransitIdentity
import au.id.micolous.metrodroid.transit.ncmc.NcmcTransitData
import au.id.micolous.metrodroid.transit.ncmc.NcmcTransaction
import au.id.micolous.metrodroid.multi.R
import au.id.micolous.metrodroid.multi.Parcelize
import au.id.micolous.metrodroid.multi.Parcelable
import au.id.micolous.metrodroid.ui.HeaderListItem
import au.id.micolous.metrodroid.ui.ListItem
import au.id.micolous.metrodroid.ui.ListItemInterface
import kotlinx.serialization.Serializable

@Parcelize
@Serializable
data class NcmcPendingEntry(
    val acquirerId: Int,
    val operatorId: Int,
    val terminalId: Int,
    val minutesElapsed: Int
) : Parcelable

@Serializable
data class NcmcApplication(
    override val generic: ISO7816ApplicationCapsule,
    val pan: String? = null,
    val balancePaise: Long? = null,
    val effectiveDate: String? = null,
    val transactions: List<NcmcTransaction> = emptyList(),
    val ncmcVersion: String? = null,
    val cardLanguage: String? = null,
    val expiryDate: String? = null,
    val cardAtc: Long? = null,
    val pendingEntry: NcmcPendingEntry? = null
) : ISO7816Application() {

    override val type: String
        get() = TYPE

    private fun formatNcmcDate(date: String?): String {
        if (date == null || date.length != 6) {
            return "Unknown"
        }

        val year = "20${date.substring(0, 2)}"
        val month = date.substring(2, 4)
        val day = date.substring(4, 6)

        return "$day/$month/$year"
    }

    override val manufacturingInfo: List<ListItemInterface>?
    get() = listOf(
        HeaderListItem(R.string.ncmc_card_info),

        ListItem(
            R.string.ncmc_pan,
            pan ?: "Unknown"
        ),

        ListItem(
            R.string.ncmc_version,
            ncmcVersion ?: "Unknown"
        ),

        ListItem(
            R.string.ncmc_language,
            cardLanguage ?: "Unknown"
        ),

        ListItem(
            R.string.ncmc_effective_date,
            formatNcmcDate(effectiveDate)
        ),

        ListItem(
            R.string.expiry_date,
            formatNcmcDate(expiryDate)
        ),

        ListItem(
            R.string.ncmc_atc,
            cardAtc?.toString() ?: "Unknown"
        )
    )

    override fun parseTransitIdentity(
        card: ISO7816Card
    ): TransitIdentity {
        return TransitIdentity(
            name = "RuPay NCMC",
            serialNumber = pan
        )
    }

    override fun parseTransitData(card: ISO7816Card): TransitData {
        return NcmcTransitData(
            pan = pan,
            balancePaise = balancePaise,
            effectiveDate = effectiveDate,
            transactions = transactions,
            pendingEntry = pendingEntry
        )
    }

    companion object {
        const val TYPE = "ncmc"
    }
}