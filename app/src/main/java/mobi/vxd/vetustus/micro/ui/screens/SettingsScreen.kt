package mobi.vxd.vetustus.micro.ui.screens

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mobi.vxd.vetustus.micro.BuildConfig
import mobi.vxd.vetustus.micro.data.SettingsRepository

@Composable
fun SettingsScreen(repository: SettingsRepository, modifier: Modifier = Modifier) {
    val settings by repository.settings.collectAsStateWithLifecycle()
    var nick by remember(settings.nick) { mutableStateOf(settings.nick) }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SettingsCard(Icons.Default.SettingsEthernet, "Identità IRC invisibile") {
            Text(
                "Serve soltanto al motore XDCC. Chat, canali e status non vengono mostrati.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = nick,
                onValueChange = { nick = it.take(24) },
                label = { Text("Nickname") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { repository.setNick(nick) },
                enabled = nick.trim().length >= 3 && nick != settings.nick,
            ) { Text("Salva nickname") }
        }

        SettingsCard(Icons.Default.Archive, "Archivi") {
            SwitchSetting(
                title = "Estrai automaticamente gli ZIP",
                detail = "L'archivio viene controllato e i file finiscono in una sottocartella dedicata.",
                checked = settings.autoExtractZip,
                onChecked = repository::setAutoExtractZip,
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SwitchSetting(
                title = "Elimina lo ZIP dopo l'estrazione",
                detail = "Avviene solo se almeno un file è stato estratto correttamente.",
                checked = settings.deleteArchiveAfterExtract,
                enabled = settings.autoExtractZip,
                onChecked = repository::setDeleteArchiveAfterExtract,
            )
        }

        SettingsCard(Icons.Default.Security, "Sicurezza DCC") {
            SwitchSetting(
                title = "Consenti host DCC nella rete locale",
                detail = "Disattivato per impedire a un bot remoto di sondare LAN e loopback. Attivalo solo per un bot privato fidato.",
                checked = settings.allowPrivateDccHosts,
                onChecked = repository::setAllowPrivateDccHosts,
            )
        }

        SettingsCard(Icons.Default.Folder, "Memoria") {
            Text("Destinazione pubblica", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
            Text("Download/VETUSTUS Micro", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(
                "I file .part restano privati finché dimensione e scrittura non sono verificate. Pausa ed errori non lasciano copie corrotte nella cartella Download.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SettingsCard(Icons.Default.Info, "Prima Alpha") {
            Text("VETUSTUS Micro ${BuildConfig.VERSION_NAME}", fontWeight = FontWeight.Bold)
            Text("Ricerca: xdcc.eu · Download: IRC/DCC nativo", style = MaterialTheme.typography.bodySmall)
            Text("Player: Media3/ExoPlayer · MP4, MKV, MP3, FLAC", style = MaterialTheme.typography.bodySmall)
            Text("Archivi: ZIP · estrazione non ricorsiva", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            Text(
                "MKV è un contenitore: la riproduzione dipende anche dai codec presenti sul dispositivo. Android limita i servizi dataSync in background a 6 ore complessive; il file parziale resta riprendibile.",
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
private fun SettingsCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, content: @Composable ColumnScope.() -> Unit) {
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
