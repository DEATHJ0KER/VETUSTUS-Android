package mobi.vxd.vetustus.micro.network

import android.content.Context
import org.json.JSONObject

data class IrcEndpoint(
    val address: String,
    val port: Int,
    val tls: Boolean,
)

data class IrcNetwork(
    val id: String,
    val name: String,
    val aliases: List<String>,
    val endpoints: List<IrcEndpoint>,
) {
    val address: String get() = endpoints.first().address
    val port: Int get() = endpoints.first().port
    val tls: Boolean get() = endpoints.first().tls
}

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

        // The network label comes from an external HTML index. Never turn an
        // unknown label into an arbitrary hostname. The bundled directory is
        // the authoritative connection allow-list.
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

                val tlsMode = item.optString("tls_mode", "ssl").lowercase()
                val primaryTls = tlsMode != "plain"
                val endpoints = buildList {
                    add(IrcEndpoint(address = address, port = port, tls = primaryTls))
                    val fallbackPort = when {
                        item.has("fallback_plain_port") -> item.optInt("fallback_plain_port", 0)
                        tlsMode == "auto" -> 6667
                        else -> 0
                    }
                    if (primaryTls && fallbackPort in 1..65535 && fallbackPort != port) {
                        add(
                            IrcEndpoint(
                                address = item.optString("fallback_address", address).trim().ifBlank { address },
                                port = fallbackPort,
                                tls = false,
                            ),
                        )
                    }
                }

                add(
                    IrcNetwork(
                        id = item.optString("id", address),
                        name = item.optString("name", address),
                        aliases = aliases,
                        endpoints = endpoints,
                    ),
                )
            }
        }
    }

    private fun normalize(value: String): String = value.lowercase().replace(Regex("[^a-z0-9]"), "")
}
