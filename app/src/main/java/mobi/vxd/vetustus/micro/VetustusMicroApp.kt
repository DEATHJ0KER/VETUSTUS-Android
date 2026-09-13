package mobi.vxd.vetustus.micro

import android.app.Application
import mobi.vxd.vetustus.micro.data.AppDatabase
import mobi.vxd.vetustus.micro.data.DownloadRepository
import mobi.vxd.vetustus.micro.data.LibraryRepository
import mobi.vxd.vetustus.micro.data.SettingsRepository
import mobi.vxd.vetustus.micro.network.NetworkDirectory
import mobi.vxd.vetustus.micro.network.XdccSearchClient
import mobi.vxd.vetustus.micro.storage.MediaArchiveManager
import mobi.vxd.vetustus.micro.storage.MediaStorePublisher

class VetustusMicroApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        container.database.recoverInterruptedTransfers()
        container.downloads.refresh()
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
}
