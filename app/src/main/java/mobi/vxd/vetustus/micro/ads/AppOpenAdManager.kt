package mobi.vxd.vetustus.micro.ads

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import mobi.vxd.vetustus.micro.BuildConfig

class AppOpenAdManager(private val application: Application) {
    private val preferences = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val consentInformation = UserMessagingPlatform.getConsentInformation(application)

    private val _privacyOptionsRequired = MutableStateFlow(false)
    val privacyOptionsRequired: StateFlow<Boolean> = _privacyOptionsRequired.asStateFlow()

    private var currentActivity: Activity? = null
    private var consentFlowStarted = false
    private var mobileAdsInitialized = false
    private var appOpenAd: AppOpenAd? = null
    private var isLoadingAd = false
    private var loadTimeMillis = 0L

    var isShowingAd: Boolean = false
        private set

    private var appInForeground = false
    private var foregroundStartedAt = 0L
    private var foregroundShowConsumed = false

    private val skipAdsForFirstEverSession: Boolean

    init {
        val launchedBefore = preferences.getBoolean(KEY_HAS_LAUNCHED, false)
        skipAdsForFirstEverSession = !launchedBefore && !BuildConfig.DEBUG
        if (!launchedBefore) {
            preferences.edit().putBoolean(KEY_HAS_LAUNCHED, true).apply()
        }
    }

    fun initializeConsent(activity: Activity) {
        currentActivity = activity
        if (consentFlowStarted) return
        consentFlowStarted = true

        val params = ConsentRequestParameters.Builder().build()
        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                refreshPrivacyOptionsRequirement()

                if (consentInformation.canRequestAds()) {
                    initializeMobileAdsIfNeeded()
                }

                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    if (formError != null) {
                        Log.w(TAG, "Consent form error: ${formError.message}")
                    }
                    refreshPrivacyOptionsRequirement()
                    if (consentInformation.canRequestAds()) {
                        initializeMobileAdsIfNeeded()
                    }
                }
            },
            { requestError ->
                Log.w(TAG, "Consent update error: ${requestError.message}")
                refreshPrivacyOptionsRequirement()
                if (consentInformation.canRequestAds()) {
                    initializeMobileAdsIfNeeded()
                }
            },
        )
    }

    fun updateCurrentActivity(activity: Activity) {
        currentActivity = activity
    }

    fun onAppForeground(activity: Activity) {
        currentActivity = activity
        appInForeground = true
        foregroundStartedAt = SystemClock.elapsedRealtime()
        foregroundShowConsumed = false

        if (consentInformation.canRequestAds()) {
            initializeMobileAdsIfNeeded()
        }
        showIfAvailableOrLoad()
    }

    fun onAppBackground() {
        appInForeground = false
    }

    fun showPrivacyOptions(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { formError ->
            if (formError != null) {
                Log.w(TAG, "Privacy options error: ${formError.message}")
            }
            refreshPrivacyOptionsRequirement()
            if (consentInformation.canRequestAds()) {
                initializeMobileAdsIfNeeded()
            }
        }
    }

    private fun refreshPrivacyOptionsRequirement() {
        _privacyOptionsRequired.value =
            consentInformation.privacyOptionsRequirementStatus ==
                ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
    }

    private fun initializeMobileAdsIfNeeded() {
        if (mobileAdsInitialized) {
            showIfAvailableOrLoad()
            return
        }

        mobileAdsInitialized = true
        MobileAds.initialize(application) {
            showIfAvailableOrLoad()
        }
    }

    private fun showIfAvailableOrLoad() {
        if (!mobileAdsInitialized) return

        if (canShowNow() && isAdAvailable()) {
            val activity = currentActivity ?: return
            showAd(activity)
        } else {
            loadAd()
        }
    }

    private fun loadAd() {
        if (!mobileAdsInitialized || isLoadingAd || isAdAvailable()) return

        isLoadingAd = true
        AppOpenAd.load(
            application,
            adUnitId,
            AdRequest.Builder().build(),
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    appOpenAd = ad
                    isLoadingAd = false
                    loadTimeMillis = System.currentTimeMillis()

                    if (canShowNow()) {
                        currentActivity?.let(::showAd)
                    }
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    isLoadingAd = false
                    appOpenAd = null
                    Log.d(TAG, "App-open load failed: ${error.message}")
                }
            },
        )
    }

    private fun showAd(activity: Activity) {
        if (!canShowNow() || !isAdAvailable()) return

        val ad = appOpenAd ?: return
        isShowingAd = true
        foregroundShowConsumed = true

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                appOpenAd = null
                isShowingAd = false
                loadAd()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.d(TAG, "App-open show failed: ${adError.message}")
                appOpenAd = null
                isShowingAd = false
                loadAd()
            }
        }

        ad.show(activity)
    }

    private fun canShowNow(): Boolean {
        if (!appInForeground || isShowingAd || foregroundShowConsumed) return false
        if (skipAdsForFirstEverSession) return false

        val activity = currentActivity ?: return false
        if (activity.isFinishing || activity.isDestroyed) return false

        // If a cold-start ad is not ready quickly, don't interrupt the user later.
        return SystemClock.elapsedRealtime() - foregroundStartedAt <= STARTUP_SHOW_WINDOW_MS
    }

    private fun isAdAvailable(): Boolean {
        val age = System.currentTimeMillis() - loadTimeMillis
        return appOpenAd != null && age in 0 until AD_EXPIRATION_MS
    }

    private val adUnitId: String
        get() = if (BuildConfig.DEBUG) TEST_APP_OPEN_AD_UNIT_ID else PRODUCTION_APP_OPEN_AD_UNIT_ID

    companion object {
        private const val TAG = "VETUSTUS-AdMob"
        private const val PREFS_NAME = "vetustus_ads"
        private const val KEY_HAS_LAUNCHED = "has_launched"
        private const val STARTUP_SHOW_WINDOW_MS = 5_000L
        private const val AD_EXPIRATION_MS = 4L * 60L * 60L * 1000L

        private const val TEST_APP_OPEN_AD_UNIT_ID = "ca-app-pub-3940256099942544/9257395921"
        private const val PRODUCTION_APP_OPEN_AD_UNIT_ID = "ca-app-pub-0237921387561801/4150635553"
    }
}
