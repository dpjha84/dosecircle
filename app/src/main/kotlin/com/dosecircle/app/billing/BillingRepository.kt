package com.dosecircle.app.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.dosecircle.app.AppContainer
import com.dosecircle.core.PlanTier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant
import java.time.Period
import java.time.format.DateTimeParseException
import kotlin.coroutines.resume

/** Create this subscription in Play Console with base plans "monthly" (P1M) and "annual" (P1Y), plus a 7-day free-trial offer on annual. */
const val PRO_PRODUCT_ID = "dosecircle_pro"

data class ProOffer(
    val basePlanId: String,
    val offerToken: String,
    val price: String,
    val periodLabel: String,
    val trialLabel: String?,
    val isAnnual: Boolean,
)

sealed interface BillingEvent {
    data object Purchased : BillingEvent
    data object Pending : BillingEvent
    data object Cancelled : BillingEvent
    data class Failed(val message: String) : BillingEvent
}

class BillingRepository(
    context: Context,
    private val scope: CoroutineScope,
    private val container: AppContainer,
) : PurchasesUpdatedListener {

    private val client: BillingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    private val connectLock = Mutex()
    private var details: ProductDetails? = null

    private val _offers = MutableStateFlow<List<ProOffer>>(emptyList())
    val offers: StateFlow<List<ProOffer>> = _offers.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _events = MutableSharedFlow<BillingEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<BillingEvent> = _events.asSharedFlow()

    private suspend fun connected(): Boolean = connectLock.withLock {
        if (client.isReady) return@withLock true
        suspendCancellableCoroutine { cont ->
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (cont.isActive) cont.resume(result.responseCode == BillingClient.BillingResponseCode.OK)
                }

                override fun onBillingServiceDisconnected() = Unit // the next call reconnects
            })
        }
    }

    suspend fun loadOffers() {
        _loading.value = true
        try {
            if (!connected()) return
            val params = QueryProductDetailsParams.newBuilder()
                .setProductList(
                    listOf(
                        QueryProductDetailsParams.Product.newBuilder()
                            .setProductId(PRO_PRODUCT_ID)
                            .setProductType(BillingClient.ProductType.SUBS)
                            .build(),
                    ),
                )
                .build()
            val result = client.queryProductDetails(params)
            val product = result.productDetailsList?.firstOrNull() ?: return
            details = product
            _offers.value = product.subscriptionOfferDetails.orEmpty()
                .groupBy { it.basePlanId }
                .map { (plan, offers) ->
                    val chosen = offers.firstOrNull { o -> o.pricingPhases.pricingPhaseList.any { it.priceAmountMicros == 0L } }
                        ?: offers.firstOrNull { it.offerId == null }
                        ?: offers.first()
                    val phases = chosen.pricingPhases.pricingPhaseList
                    val recurring = phases.last()
                    val trial = phases.firstOrNull { it.priceAmountMicros == 0L && phases.size > 1 }
                    ProOffer(
                        basePlanId = plan,
                        offerToken = chosen.offerToken,
                        price = recurring.formattedPrice,
                        periodLabel = periodLabel(recurring.billingPeriod),
                        trialLabel = trial?.let { trialLabel(it.billingPeriod) },
                        isAnnual = recurring.billingPeriod == "P1Y",
                    )
                }
                .sortedByDescending { it.isAnnual }
        } finally {
            _loading.value = false
        }
    }

    fun purchase(activity: Activity, offer: ProOffer) {
        val product = details
        if (product == null) {
            _events.tryEmit(BillingEvent.Failed("Subscriptions aren't available right now. Check your connection and try again."))
            return
        }
        val flow = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(product)
                        .setOfferToken(offer.offerToken)
                        .build(),
                ),
            )
            .build()
        val result = client.launchBillingFlow(activity, flow)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            _events.tryEmit(BillingEvent.Failed("Couldn't start the purchase. Please try again."))
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> scope.launch { handle(purchases.orEmpty()) }
            BillingClient.BillingResponseCode.USER_CANCELED -> _events.tryEmit(BillingEvent.Cancelled)
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> scope.launch { refreshEntitlement(); _events.emit(BillingEvent.Purchased) }
            else -> _events.tryEmit(BillingEvent.Failed("The purchase didn't go through. You haven't been charged."))
        }
    }

    private suspend fun handle(purchases: List<Purchase>) {
        var granted = false
        for (p in purchases.filter { PRO_PRODUCT_ID in it.products }) {
            when (p.purchaseState) {
                Purchase.PurchaseState.PURCHASED -> {
                    if (!p.isAcknowledged) {
                        client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.purchaseToken).build())
                    }
                    granted = true
                }
                Purchase.PurchaseState.PENDING -> _events.emit(BillingEvent.Pending)
                else -> Unit
            }
        }
        if (granted) {
            setPro(true)
            _events.emit(BillingEvent.Purchased)
        }
    }

    /** Asks Google Play what this account owns. Offline or failed checks never take Pro away. */
    suspend fun refreshEntitlement(): Boolean {
        if (!connected()) return container.tier() == PlanTier.PRO
        val result = client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build())
        if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) return container.tier() == PlanTier.PRO
        val active = result.purchasesList.any { it.purchaseState == Purchase.PurchaseState.PURCHASED && PRO_PRODUCT_ID in it.products }
        for (p in result.purchasesList.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED && !it.isAcknowledged }) {
            client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.purchaseToken).build())
        }
        setPro(active)
        return active
    }

    /** Pro is cached for a week so a phone without signal does not lose features mid-trip. Each successful check renews it. */
    suspend fun setPro(active: Boolean) {
        container.updateSettings {
            if (active) it.copy(tier = PlanTier.PRO, proExpires = Instant.now().plus(Duration.ofDays(GRACE_DAYS)))
            else it.copy(tier = PlanTier.FREE, proExpires = null)
        }
        container.reschedule()
    }

    private fun periodLabel(iso: String) = when (iso) {
        "P1M" -> "month"
        "P1Y" -> "year"
        "P1W" -> "week"
        else -> "period"
    }

    private fun trialLabel(iso: String): String = try {
        val p = Period.parse(iso)
        val days = p.years * 365 + p.months * 30 + p.days
        "$days-day free trial"
    } catch (e: DateTimeParseException) {
        "Free trial"
    }

    private companion object {
        const val GRACE_DAYS = 7L
    }
}
