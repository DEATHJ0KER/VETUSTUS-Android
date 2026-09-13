package mobi.vxd.vetustus.micro.ui.screens

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mobi.vxd.vetustus.micro.BuildConfig
import mobi.vxd.vetustus.micro.R
import mobi.vxd.vetustus.micro.data.SettingsRepository

private data class LanguageOption(val tag: String, val label: String)

private val languageOptions = listOf(
    LanguageOption("en", "English"),
    LanguageOption("it", "Italiano"),
    LanguageOption("de", "Deutsch"),
    LanguageOption("es", "Español"),
    LanguageOption("fr", "Français"),
    LanguageOption("pt", "Português"),
    LanguageOption("nl", "Nederlands"),
    LanguageOption("pl", "Polski"),
    LanguageOption("ru", "Русский"),
    LanguageOption("uk", "Українська"),
    LanguageOption("tr", "Türkçe"),
    LanguageOption("ja", "日本語"),
    LanguageOption("ko", "한국어"),
    LanguageOption("zh", "中文"),
)

@Composable
fun SettingsScreen(repository: SettingsRepository, modifier: Modifier = Modifier) {
    val settings by repository.settings.collectAsStateWithLifecycle()
    var nick by remember(settings.nick) { mutableStateOf(settings.nick) }
    val currentTag = AppCompatDelegate.getApplicationLocales().toLanguageTags().substringBefore(',')

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SettingsCard(Icons.Default.Translate, stringResource(R.string.settings_language)) {
            Text(
                stringResource(R.string.settings_language_detail),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = currentTag.isBlank(),
                    onClick = { AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList()) },
                    label = { Text(stringResource(R.string.language_system)) },
                )
                languageOptions.forEach { option ->
                    FilterChip(
                        selected = currentTag.equals(option.tag, ignoreCase = true),
                        onClick = {
                            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(option.tag))
                        },
                        label = { Text(option.label) },
                    )
                }
            }
        }

        SettingsCard(Icons.Default.SettingsEthernet, stringResource(R.string.settings_irc_identity)) {
            Text(
                stringResource(R.string.settings_irc_detail),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = nick,
                onValueChange = { nick = it.take(24) },
                label = { Text(stringResource(R.string.nickname)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { repository.setNick(nick) },
                enabled = nick.trim().length >= 3 && nick != settings.nick,
            ) { Text(stringResource(R.string.save_nickname)) }
        }

        SettingsCard(Icons.Default.Archive, stringResource(R.string.settings_archives)) {
            SwitchSetting(
                title = stringResource(R.string.auto_extract_title),
                detail = stringResource(R.string.auto_extract_detail),
                checked = settings.autoExtractArchives,
                onChecked = repository::setAutoExtractArchives,
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SwitchSetting(
                title = stringResource(R.string.delete_archive_title),
                detail = stringResource(R.string.delete_archive_detail),
                checked = settings.deleteArchiveAfterExtract,
                enabled = settings.autoExtractArchives,
                onChecked = repository::setDeleteArchiveAfterExtract,
            )
        }

        SettingsCard(Icons.Default.Security, stringResource(R.string.settings_dcc_security)) {
            SwitchSetting(
                title = stringResource(R.string.allow_local_title),
                detail = stringResource(R.string.allow_local_detail),
                checked = settings.allowPrivateDccHosts,
                onChecked = repository::setAllowPrivateDccHosts,
            )
        }

        SettingsCard(Icons.Default.Folder, stringResource(R.string.settings_storage)) {
            Text(
                stringResource(R.string.public_destination),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
            Text("Download/VETUSTUS Micro", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.storage_detail),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SettingsCard(Icons.Default.Info, "VETUSTUS Micro ${BuildConfig.VERSION_NAME}") {
            Text(stringResource(R.string.settings_search_line), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.settings_player_line), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.settings_archives_line), style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.settings_codec_detail),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text("VETUSTUS Script by VxD aka DEATHJ0KER", color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun SettingsCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(title, modifier = Modifier.padding(start = 8.dp), fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun SwitchSetting(
    title: String,
    detail: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChecked: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChecked, enabled = enabled)
    }
}
