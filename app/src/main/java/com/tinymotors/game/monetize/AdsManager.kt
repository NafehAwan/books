package com.tinymotors.game.monetize

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.tinymotors.game.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Google AdMob, set up for a children's app: every request is tagged child-directed and
 * under the age of consent, and only G-rated ads may show. No advertising ID is used
 * (the AD_ID permission is removed in the manifest). Ad sound is always muted.
 * Category blocking (for example finance with interest, astrology, music downloads) is set in
 * the AdMob console; see "Ad content filters" in README.md.
 */
class AdsManager(context: Context) {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var started = false
    private var rewarded: RewardedAd? = null
    private var loading = false
    private var retries = 0

    private val _rewardedReady = MutableStateFlow(false)
    val rewardedReady: StateFlow<Boolean> = _rewardedReady.asStateFlow()

    private val _started = MutableStateFlow(false)
    /** True once the SDK is set up; banners wait for this. */
    val ready: StateFlow<Boolean> = _started.asStateFlow()

    val bannerUnitId: String get() = BuildConfig.BANNER_AD_UNIT

    fun start() {
        if (started) return
        started = true
        MobileAds.setRequestConfiguration(
            RequestConfiguration.Builder()
                .setTagForChildDirectedTreatment(RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_TRUE)
                .setTagForUnderAgeOfConsent(RequestConfiguration.TAG_FOR_UNDER_AGE_OF_CONSENT_TRUE)
                .setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_G)
                .build(),
        )
        // Initialising off the main thread keeps the first frames smooth.
        Thread {
            MobileAds.initialize(app) {
                main.post {
                    // Ads never play sound (no music or voices in ads).
                    MobileAds.setAppMuted(true)
                    MobileAds.setAppVolume(0f)
                    _started.value = true
                    loadRewarded()
                }
            }
        }.start()
    }

    /**
     * Tries again to load a reward video if none is ready and none is loading, for example after
     * the retries ran out while the phone was offline. Called when the app comes back to the front
     * and when the Store opens.
     */
    fun retryRewardedIfIdle() {
        if (!_started.value || loading || rewarded != null) return
        retries = 0
        loadRewarded()
    }

    private fun loadRewarded() {
        if (loading || rewarded != null) return
        loading = true
        RewardedAd.load(app, BuildConfig.REWARDED_AD_UNIT, AdRequest.Builder().build(), object : RewardedAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedAd) {
                loading = false
                retries = 0
                rewarded = ad
                _rewardedReady.value = true
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                loading = false
                _rewardedReady.value = false
                if (retries < MAX_RETRIES) {
                    retries++
                    main.postDelayed({ loadRewarded() }, RETRY_MS * retries)
                }
            }
        })
    }

    /** Shows a reward video. [onReward] runs only if the video was watched to the end. */
    fun showRewarded(activity: Activity, onReward: () -> Unit, onClosed: () -> Unit) {
        val ad = rewarded
        if (ad == null) {
            loadRewarded()
            onClosed()
            return
        }
        rewarded = null
        _rewardedReady.value = false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                onClosed()
                loadRewarded()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                onClosed()
                loadRewarded()
            }
        }
        ad.show(activity) { onReward() }
    }

    private companion object {
        const val MAX_RETRIES = 4
        const val RETRY_MS = 20_000L
    }
}
