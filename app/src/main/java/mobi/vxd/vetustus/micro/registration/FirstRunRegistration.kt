package mobi.vxd.vetustus.micro.registration

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mobi.vxd.vetustus.micro.BuildConfig
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class FirstRunRegistration(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    suspend fun registerIfNeeded() = withContext(Dispatchers.IO) {
        if (preferences.getBoolean(KEY_REGISTERED, false)) return@withContext

        val now = System.currentTimeMillis()
        val lastAttempt = preferences.getLong(KEY_LAST_ATTEMPT, 0L)
        if (lastAttempt > 0L && now - lastAttempt < RETRY_INTERVAL_MS) return@withContext

        val installId = preferences.getString(KEY_INSTALL_ID, null) ?: UUID.randomUUID().toString().also {
            preferences.edit().putString(KEY_INSTALL_ID, it).apply()
        }
        preferences.edit().putLong(KEY_LAST_ATTEMPT, now).apply()

        val payload = JSONObject().apply {
            put("product", PRODUCT)
            put("install_id", installId)
            put("version", BuildConfig.VERSION_NAME)
            put("platform", PLATFORM)
        }.toString()

        var connection: HttpURLConnection? = null
        try {
            connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                doOutput = true
                useCaches = false
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "VETUSTUS-Micro/${BuildConfig.VERSION_NAME}")
            }

            connection.outputStream.use { output ->
                output.write(payload.toByteArray(Charsets.UTF_8))
            }

            val code = connection.responseCode
            if (code !in 200..299) return@withContext

            val response = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val ok = runCatching { JSONObject(response).optBoolean("ok", false) }.getOrDefault(false)
            if (ok) {
                preferences.edit()
                    .putBoolean(KEY_REGISTERED, true)
                    .putLong(KEY_REGISTERED_AT, System.currentTimeMillis())
                    .apply()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Registration is best-effort only. App startup and XDCC functions must never depend on it.
        } finally {
            connection?.disconnect()
        }
    }

    fun isRegistered(): Boolean = preferences.getBoolean(KEY_REGISTERED, false)

    companion object {
        const val ENDPOINT = "https://vxd.mobi/vetustus/api/register-micro.php"
        const val PRODUCT = "VETUSTUS-MICRO"
        const val PLATFORM = "android"

        private const val PREFS_NAME = "vetustus_micro_registration"
        private const val KEY_INSTALL_ID = "install_id"
        private const val KEY_REGISTERED = "registered"
        private const val KEY_REGISTERED_AT = "registered_at"
        private const val KEY_LAST_ATTEMPT = "last_attempt"
        private const val RETRY_INTERVAL_MS = 60L * 60L * 1000L
        private const val CONNECT_TIMEOUT_MS = 6_000
        private const val READ_TIMEOUT_MS = 6_000
    }
}
