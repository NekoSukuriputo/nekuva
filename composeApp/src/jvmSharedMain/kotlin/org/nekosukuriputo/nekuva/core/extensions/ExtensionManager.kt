package org.nekosukuriputo.nekuva.core.extensions

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import org.nekosukuriputo.nekuva.core.prefs.AppSettings
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

// Where the published catalog + per-platform artifacts live (GitHub Release "latest" assets of the exts repo).
private const val RELEASE_BASE = "https://github.com/NekoSukuriputo/nekuva-exts/releases/latest/download/"
private const val INSTALLED_JAR = "extension.jar"

@Serializable
private data class ExtIndex(
    val abiVersion: Int = 0,
    val version: String = "",
    val artifacts: Map<String, ExtArtifact> = emptyMap(),
)

@Serializable
private data class ExtArtifact(val file: String = "", val sha256: String = "")

/** UI state of the extension updater. */
sealed interface ExtState {
    data object Idle : ExtState
    data object Working : ExtState
    data class Installed(val version: String, val sourceCount: Int) : ExtState
    data object UpToDate : ExtState
    data class Error(val message: String) : ExtState
}

/**
 * Downloads / imports a nekuva-exts extension bundle at runtime and loads it via [loadExtension], so new
 * sources can ship without rebuilding the app. The loaded bundle is exposed via [loaded] for the (future)
 * runtime source registry; for now this powers the Settings → About "Update extensions" UI.
 *
 * Desktop works today (URLClassLoader). Android no-ops until the dexed artifact + DexClassLoader land.
 */
class ExtensionManager(
    private val httpClient: OkHttpClient,
    private val settings: AppSettings,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val _state = MutableStateFlow<ExtState>(ExtState.Idle)
    val state: StateFlow<ExtState> = _state.asStateFlow()
    private val loadedOnce = AtomicBoolean(false)

    @Volatile
    var loaded: LoadedExtension? = null
        private set

    /** Load a previously-installed bundle (once, best-effort) — e.g. when the settings screen opens. */
    fun loadInstalled() {
        if (!loadedOnce.compareAndSet(false, true)) return
        val jar = File(extensionsDir(), INSTALLED_JAR)
        if (!jar.isFile) return
        val ext = loadExtension(jar.absolutePath) ?: return
        loaded = ext
        _state.value = ExtState.Installed(
            settings.installedExtensionVersion ?: ext.abiVersion.toString(),
            ext.sources.size,
        )
    }

    /** Import a local plugin jar (Desktop file picker). Returns true on success. */
    suspend fun installFromFile(path: String): Boolean = withContext(Dispatchers.IO) {
        _state.value = ExtState.Working
        runCatching {
            val src = File(path)
            require(src.isFile) { "File not found" }
            // Validate by loading from the picked file first, so we never overwrite a good install with a bad jar.
            val ext = loadExtension(src.absolutePath) ?: error("Incompatible or invalid extension bundle")
            src.copyTo(File(extensionsDir(), INSTALLED_JAR), overwrite = true)
            loaded = ext
            settings.installedExtensionVersion = "imported"
            _state.value = ExtState.Installed("imported", ext.sources.size)
            true
        }.getOrElse {
            _state.value = ExtState.Error(it.message ?: "Import failed")
            false
        }
    }

    /** Check the published catalog and download/install if newer (or not yet installed). */
    suspend fun checkAndUpdate(): Boolean = withContext(Dispatchers.IO) {
        _state.value = ExtState.Working
        runCatching {
            val index = json.decodeFromString<ExtIndex>(httpGet(RELEASE_BASE + "index.json"))
            if (index.abiVersion != HOST_EXT_ABI_VERSION) error("This extension needs a newer app version")
            val artifact = index.artifacts[extensionPlatformKey] ?: error("No build for this platform yet")
            if (index.version == settings.installedExtensionVersion && loaded != null) {
                _state.value = ExtState.UpToDate
                return@withContext true
            }
            val bytes = httpGetBytes(RELEASE_BASE + artifact.file)
            if (artifact.sha256.isNotBlank()) {
                val sha = MessageDigest.getInstance("SHA-256").digest(bytes)
                    .joinToString("") { "%02x".format(it) }
                require(sha.equals(artifact.sha256, ignoreCase = true)) { "Checksum mismatch" }
            }
            val target = File(extensionsDir(), INSTALLED_JAR)
            target.writeBytes(bytes)
            val ext = loadExtension(target.absolutePath) ?: error("Failed to load downloaded extension")
            loaded = ext
            settings.installedExtensionVersion = index.version
            _state.value = ExtState.Installed(index.version, ext.sources.size)
            true
        }.getOrElse {
            _state.value = ExtState.Error(it.message ?: "Update failed")
            false
        }
    }

    private fun httpGet(url: String): String =
        httpClient.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code}")
            resp.body?.string() ?: error("Empty response")
        }

    private fun httpGetBytes(url: String): ByteArray =
        httpClient.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code}")
            resp.body?.bytes() ?: error("Empty response")
        }
}
