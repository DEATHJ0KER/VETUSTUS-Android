package mobi.vxd.vetustus.micro.network

import android.content.Context
import org.json.JSONObject

data class IrcNetwork(
    val id: String,
    val name: String,
    val aliases: List<String>,
    val address: String,
    val port: Int,
    val tls: Boolean,
)

class NetworkDirectory(context: Context) {
    private val networks: List<IrcNetwork> = context.assets.open("network-directory.json")
        .bufferedReader(Charsets.UTF_8)
        .use { reader -> parse(reader.readText()) }

    fun resolve(label: String): IrcNetwork? {
        val normalized = normalize(label)
        if (normalized.isBlank()) return null
        val exact = networks.firstOrNull { network ->
            normalize(network.id) == normalized ||
                normalize(network.name) == normalized ||
                network.aliases.any { normalize(it) == normalized }
        }
        if (exact != null) return exact
        val fuzzy = networks.firstOrNull { network ->
            normalized.contains(normalize(network.id)) || normalize(network.id).contains(normalized)
        }
        if (fuzzy != null) return fuzzy

        // La rete arriva da un indice HTML esterno: mai trasformare una label
        // sconosciuta in un hostname arbitrario. Il catalogo locale resta la
        // allow-list autorevole per le connessioni IRC.
        return null
    }

    fun supports(label: String): Boolean = resolve(label) != null

    internal fun parse(source: String): List<IrcNetwork> {
        val array = JSONObject(source).getJSONArray("networks")
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                val aliasesArray = item.optJSONArray("aliases")
                val aliases = buildList {
                    if (aliasesArray != null) {
                        for (aliasIndex in 0 until aliasesArray.length()) add(aliasesArray.getString(aliasIndex))
                    }
                }
                val address = item.optString("address").trim()
                val port = item.optInt("port", 6697)
                if (address.isBlank() || port !in 1..65535) continue
                add(
                    IrcNetwork(
                        id = item.optString("id", address),
                        name = item.optString("name", address),
                        aliases = aliases,
                        address = address,
                        port = port,
                        tls = item.optString("tls_mode", "ssl").equals("ssl", ignoreCase = true),
                    ),
                )
            }
        }
    }

    private fun normalize(value: String): String = value.lowercase().replace(Regex("[^a-z0-9]"), "")

}
