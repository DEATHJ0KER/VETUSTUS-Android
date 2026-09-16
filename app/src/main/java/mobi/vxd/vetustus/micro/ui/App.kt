package mobi.vxd.vetustus.micro.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import mobi.vxd.vetustus.micro.AppContainer
import mobi.vxd.vetustus.micro.BuildConfig
import mobi.vxd.vetustus.micro.R
import mobi.vxd.vetustus.micro.data.LibraryItem
import mobi.vxd.vetustus.micro.ui.screens.DownloadsScreen
import mobi.vxd.vetustus.micro.ui.screens.LibraryScreen
import mobi.vxd.vetustus.micro.ui.screens.PlayerScreen
import mobi.vxd.vetustus.micro.ui.screens.SearchScreen
import mobi.vxd.vetustus.micro.ui.screens.SettingsScreen

private enum class MainTab(@StringRes val labelRes: Int, val icon: ImageVector) {
    SEARCH(R.string.tab_search, Icons.Default.Search),
    DOWNLOADS(R.string.tab_downloads, Icons.Default.Download),
    LIBRARY(R.string.tab_library, Icons.Default.VideoLibrary),
    SETTINGS(R.string.tab_settings, Icons.Default.Settings),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VetustusMicroRoot(container: AppContainer) {
    var selectedTab by remember { mutableStateOf(MainTab.SEARCH) }
    var playingItem by remember { mutableStateOf<LibraryItem?>(null) }

    playingItem?.let { item ->
        BackHandler { playingItem = null }
        PlayerScreen(item = item, onBack = { playingItem = null })
        return
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(stringResource(R.string.app_name), fontWeight = FontWeight.Bold)
                            Text(
                                BuildConfig.VERSION_NAME.uppercase(),
                                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.background,
                    ),
                )
                HorizontalDivider()
            }
        },
        bottomBar = {
            NavigationBar {
                MainTab.entries.forEach { tab ->
                    val label = stringResource(tab.labelRes)
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Icon(tab.icon, contentDescription = label) },
                        label = { Text(label) },
                    )
                }
            }
        },
    ) { padding ->
        when (selectedTab) {
            MainTab.SEARCH -> SearchScreen(
                searchClient = container.searchClient,
                networkDirectory = container.networkDirectory,
                onOpenDownloads = { selectedTab = MainTab.DOWNLOADS },
                modifier = Modifier.padding(padding),
            )
            MainTab.DOWNLOADS -> DownloadsScreen(
                repository = container.downloads,
                library = container.library,
                onPlay = { playingItem = it },
                modifier = Modifier.padding(padding),
            )
            MainTab.LIBRARY -> LibraryScreen(
                repository = container.library,
                publisher = container.publisher,
                onPlay = { playingItem = it },
                modifier = Modifier.padding(padding),
            )
            MainTab.SETTINGS -> SettingsScreen(
                repository = container.settings,
                ads = container.ads,
                modifier = Modifier.padding(padding),
            )
        }
    }
}
