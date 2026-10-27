package com.tinymotors.game.monetize

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Optional purchases through Google Play. Product ids must be created in Play Console (see README). */
object Products {
    const val COINS_SMALL = "coins_500"
    const val COINS_MEDIUM = "coins_1500"
    const val COINS_LARGE = "coins_4000"
    const val REMOVE_ADS = "remove_ads"

    val COIN_PACKS = linkedMapOf(COINS_SMALL to 500, COINS_MEDIUM to 1500, COINS_LARGE to 4000)
    val ALL = COIN_PACKS.keys + REMOVE_ADS
}

/**
 * Google Play Billing. "Remove ads" is a one-time purchase that is acknowledged and restored on
 * every start. Coin packs are consumable: [onCoins] gets the purchase token and the coins, adds and
 * saves them (remembering the token), then calls the `consume` callback; only then is the purchase
 * consumed at Google Play. If consuming fails, the purchase comes back on the next start, and the
 * remembered token stops the coins from being added twice.
 * All callbacks are delivered on the main thread.
 */
class BillingManager(
    context: Context,
    private val onCoins: (purchaseToken: String, coins: Int, consume: () -> Unit) -> Unit,
    private val onAdsRemoved: () -> Unit,
) : PurchasesUpdatedListener {

    private val main = Handler(Looper.getMainLooper())
    private val details = HashMap<String, ProductDetails>()
    private val handledTokens = HashSet<String>()
    private val client = BillingClient.newBuilder(context.applicationContext)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    private val _prices = MutableStateFlow<Map<String, String>>(emptyMap())
    /** Formatted local prices by product id; empty until Google Play answers. */
    val prices: StateFlow<Map<String, String>> = _prices.asStateFlow()

    fun start() {
        if (client.isReady) {
            refresh()
            return
        }
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) refresh()
            }

            override fun onBillingServiceDisconnected() = Unit
        })
    }

    fun end() {
        client.endConnection()
    }

    private fun refresh() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                Products.ALL.map {
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(it)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                },
            ).build()
        client.queryProductDetailsAsync(params) { result, found ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryProductDetailsAsync
            val map = HashMap<String, String>()
            for (d in found.productDetailsList) {
                val price = d.oneTimePurchaseOfferDetails?.formattedPrice ?: continue
                map[d.productId] = price
                synchronized(details) { details[d.productId] = d }
            }
            main.post { _prices.value = map }
        }
        // Restore "Remove ads" and finish any purchase that completed while the app was closed.
        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build(),
        ) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) purchases.forEach { handle(it) }
        }
    }

    /** Opens Google Play's purchase sheet. Returns false when the product is not available. */
    fun buy(activity: Activity, productId: String): Boolean {
        val d = synchronized(details) { details[productId] } ?: return false
        val params = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(d)
        d.oneTimePurchaseOfferDetails?.offerToken?.let { params.setOfferToken(it) }
        val flow = BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(params.build())).build()
        return client.launchBillingFlow(activity, flow).responseCode == BillingClient.BillingResponseCode.OK
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK) purchases?.forEach { handle(it) }
    }

    private fun handle(p: Purchase) {
        if (p.purchaseState != Purchase.PurchaseState.PURCHASED) return // pending: wait for the update
        for (product in p.products) {
            if (product == Products.REMOVE_ADS) {
                main.post { onAdsRemoved() }
                if (!p.isAcknowledged) {
                    client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.purchaseToken).build()) { }
                }
            }
            val coins = Products.COIN_PACKS[product] ?: continue
            val token = p.purchaseToken
            synchronized(handledTokens) { if (!handledTokens.add(token)) return }
            main.post { onCoins(token, coins * p.quantity.coerceAtLeast(1)) { consume(token) } }
        }
    }

    private fun consume(token: String) {
        client.consumeAsync(ConsumeParams.newBuilder().setPurchaseToken(token).build()) { r, _ ->
            // Not consumed (for example offline): try again on the next start; the coins are
            // already saved and the token is remembered, so they are not added again.
            if (r.responseCode != BillingClient.BillingResponseCode.OK) {
                synchronized(handledTokens) { handledTokens.remove(token) }
            }
        }
    }
}
