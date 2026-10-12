package com.meta.wearable.dat.externalsampleapps.displayaccess.house

/** Whether a property is on the market. */
sealed interface ListingStatus {
  data class ForSale(val askingPrice: Long?, val source: String) : ListingStatus
  data object NotListed : ListingStatus
  data object Unknown : ListingStatus
}

/**
 * Source of for-sale status. BC Assessment doesn't publish listings, and MLS data (REALTOR.ca,
 * CREA DDF) needs a licensed feed, so the default reports Unknown. Plug a licensed feed in here.
 */
fun interface ListingProvider {
  /** Blocking; called on Dispatchers.IO. */
  fun statusFor(site: AddressSite, assessment: PropertyAssessment): ListingStatus

  companion object {
    val NONE = ListingProvider { _, _ -> ListingStatus.Unknown }
  }
}
