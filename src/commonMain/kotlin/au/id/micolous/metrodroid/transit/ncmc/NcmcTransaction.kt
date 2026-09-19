package au.id.micolous.metrodroid.transit.ncmc

import au.id.micolous.metrodroid.multi.Parcelize
import au.id.micolous.metrodroid.multi.Parcelable
import kotlinx.serialization.Serializable

@Parcelize
@Serializable
data class NcmcEntry(
    val acquirerId: Int,
    val operatorId: Int,
    val terminalId: Int,
    val minutesElapsed: Int
) : Parcelable

@Parcelize
@Serializable
data class NcmcTransaction(
    val acquirerId: Int,
    val operatorId: Int,
    val terminalId: Int,
    val minutesElapsed: Int,
    val transactionSequence: Int,
    val amountUnits: Int,
    val balanceUnits: Int,
    val statusCode: Int,
    val rfu: Int,
    val entry: NcmcEntry? = null
) : Parcelable {
    val amountPaise: Int
        get() = amountUnits * 10

    val balancePaise: Int
        get() = balanceUnits * 10

    val status: NcmcTransactionStatus
        get() = NcmcTransactionStatus.fromCode(statusCode)
}