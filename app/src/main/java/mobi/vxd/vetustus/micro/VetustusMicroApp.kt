package mobi.vxd.vetustus.micro

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import mobi.vxd.vetustus.micro.ads.AppOpenAdManager
import mobi.vxd.vetustus.micro.data.AppDatabase
import mobi.vxd.vetustus.micro.data.DownloadRepository
import mobi.vxd.vetustus.micro.data.LibraryRepository
import mobi.vxd.vetustus.micro.data.SettingsRepository
import mobi.vxd.vetustus.micro.network.NetworkDirectory
import mobi.vxd.vetustus.micro.network.XdccSearchClient
import mobi.vxd.vetustus.micro.registration.FirstRunRegistration
import mobi.vxd.vetustus.micro.storage.MediaArchiveManager
import mobi.vxd.vetustus.micro.storage.MediaStorePublisher

class VetustusMicroApp :
    Application(),
    Application.ActivityLifecycleCallbacks,
    DefaultLifecycleObserver {

    val container: AppContainer by lazy { AppContainer(this) }
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var currentActivity: Activity? = null

    override fun onCreate() {
        super<Application>.onCreate()
        registerActivityLifecycleCallbacks(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)

        container.database.recoverInterruptedTransfers()
        container.downloads.refresh()
        appScope.launch { container.registration.registerIfNeeded() }
    }

    override fun onStart(owner: LifecycleOwner) {
        currentActivity?.let(container.ads::onAppForeground)
    }

    override fun onStop(owner: LifecycleOwner) {
        container.ads.onAppBackground()
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        currentActivity = activity
        container.ads.updateCurrentActivity(activity)
        container.ads.initializeConsent(activity)
    }

    override fun onActivityStarted(activity: Activity) {
        if (!container.ads.isShowingAd) {
            currentActivity = activity
            container.ads.updateCurrentActivity(activity)
        }
    }

    override fun onActivityResumed(activity: Activity) {
        if (!container.ads.isShowingAd) {
            currentActivity = activity
            container.ads.updateCurrentActivity(activity)
        }
    }

    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    override fun onActivityDestroyed(activity: Activity) {
        if (currentActivity === activity) currentActivity = null
    }
}

class AppContainer(application: Application) {
    val database = AppDatabase(application)
    val downloads = DownloadRepository(application, database)
    val library = LibraryRepository(database)
    val settings = SettingsRepository(application)
    val networkDirectory = NetworkDirectory(application)
    val searchClient = XdccSearchClient()
    val publisher = MediaStorePublisher(application)
    val archiveManager = MediaArchiveManager(application, publisher, library)
    val registration = FirstRunRegistration(application)
    val ads = AppOpenAdManager(application)
}
