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
import org.nekosukuriputo.nekuva.core.model.PluginMangaSource
import org.nekosukuriputo.nekuva.core.model.PluginSourceRegistry
import org.nekosukuriputo.nekuva.core.prefs.AppSettings
import org.nekosukuriputo.nekuva.parsers.model.ContentType
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

// Where the published catalog + per-platform artifacts live (GitHub Release "latest" assets of the exts repo).
private const val RELEASE_BASE = "https://github.com/NekoSukuriputo/nekuva-exts/releases/latest/download/"

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
 * sources can ship without rebuilding the app. The loaded bundle is exposed via [loaded] (and [generation])
 * for the repository factory's source override; it also powers the Settings → About "Update extensions" UI.
 *
 * Each install writes a NEW, uniquely-named jar and records it via [AppSettings.installedExtensionFile] —
 * we never overwrite the file the running class loader still holds open (which fails on Windows). Old jars
 * are cleaned up best-effort (the one currently mapped by the live class loader is skipped until next launch).
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

    // Whether a newer signed ext bundle is published than the one installed (drives the search-box ext-update
    // icon + the dot on About → "Update extensions"). Set by [checkForUpdate]; cleared once we're up to date.
    private val _updateAvailable = MutableStateFlow(false)
    val updateAvailable: StateFlow<Boolean> = _updateAvailable.asStateFlow()

    @Volatile
    var loaded: LoadedExtension? = null
        private set

    /** Bumped whenever the loaded bundle changes, so the repository factory can drop cached parsers. */
    @Volatile
    var generation: Int = 0
        private set

    /** Load a previously-installed bundle (once, best-effort) — called at startup. */
    fun loadInstalled() {
        if (!loadedOnce.compareAndSet(false, true)) return
        val jar = currentJar() ?: return
        val ext = loadExtension(jar.absolutePath) ?: return
        loaded = ext
        publish(ext)
        generation++
        _state.value = ExtState.Installed(settings.installedExtensionVersion ?: "imported", ext.sources.size)
    }

    /** Publish the bundle's sources to the in-memory registry the host reads (name resolution + Explore). */
    private fun publish(ext: LoadedExtension) {
        PluginSourceRegistry.set(
            ext.sources.map { s ->
                PluginMangaSource(
                    name = s.name,
                    title = s.title,
                    locale = s.locale,
                    contentType = runCatching { ContentType.valueOf(s.contentType) }.getOrDefault(ContentType.MANGA),
                    isBroken = s.isBroken,
                )
            },
        )
    }

    /** Import a local plugin jar (Desktop file picker). Returns true on success. */
    suspend fun installFromFile(path: String): Boolean = withContext(Dispatchers.IO) {
        _state.value = ExtState.Working
        runCatching {
            val src = File(path)
            require(src.isFile) { "File not found" }
            val target = newJarFile()
            src.copyTo(target, overwrite = false)
            activate(target, version = "imported")
        }.getOrElse {
            _state.value = ExtState.Error(it.message ?: "Import failed")
            false
        }
    }

    /**
     * Lightweight check (no download/install): is a NEWER signed bundle published for this platform than the
     * one currently installed? Drives the update indicator only — the actual install still goes via
     * [checkAndUpdate]. Returns false (and leaves the indicator off) when nothing is installed, the bundle was
     * locally imported, the ABI/platform doesn't match, or the signature is invalid.
     */
    suspend fun checkForUpdate(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val installed = settings.installedExtensionVersion
            // Only nag when a published (versioned) bundle is installed — not for built-in or locally imported.
            if (installed.isNullOrBlank() || installed == "imported") return@runCatching false
            val indexBytes = httpGetBytes(RELEASE_BASE + "index.json")
            val sig = runCatching { httpGet(RELEASE_BASE + "index.json.sig") }.getOrNull()
            if (!verifyExtensionSignature(indexBytes, sig)) return@runCatching false
            val index = json.decodeFromString<ExtIndex>(indexBytes.decodeToString())
            if (index.abiVersion != HOST_EXT_ABI_VERSION) return@runCatching false
            if (index.artifacts[extensionPlatformKey] == null) return@runCatching false
            index.version.isNotBlank() && index.version != installed
        }.getOrDefault(false).also { _updateAvailable.value = it }
    }

    /** Check the published catalog and download/install if newer (or not yet installed). */
    suspend fun checkAndUpdate(): Boolean = withContext(Dispatchers.IO) {
        _state.value = ExtState.Working
        runCatching {
            // Verify the catalog signature before trusting it (only the maintainer's signed bundles).
            // index.json's sha256 then gates the artifacts. Bootstrap: passes when no key is configured.
            val indexBytes = httpGetBytes(RELEASE_BASE + "index.json")
            val sig = runCatching { httpGet(RELEASE_BASE + "index.json.sig") }.getOrNull()
            if (!verifyExtensionSignature(indexBytes, sig)) error("Extension signature invalid — refusing to install")
            val index = json.decodeFromString<ExtIndex>(indexBytes.decodeToString())
            if (index.abiVersion != HOST_EXT_ABI_VERSION) error("This extension needs a newer app version")
            val artifact = index.artifacts[extensionPlatformKey] ?: error("No build for this platform yet")
            if (index.version == settings.installedExtensionVersion && loaded != null) {
                _updateAvailable.value = false
                _state.value = ExtState.UpToDate
                return@withContext true
            }
            val bytes = httpGetBytes(RELEASE_BASE + artifact.file)
            if (artifact.sha256.isNotBlank()) {
                val sha = MessageDigest.getInstance("SHA-256").digest(bytes)
                    .joinToString("") { "%02x".format(it) }
                require(sha.equals(artifact.sha256, ignoreCase = true)) { "Checksum mismatch" }
            }
            val target = newJarFile()
            target.writeBytes(bytes)
            activate(target, version = index.version)
        }.getOrElse {
            _state.value = ExtState.Error(it.message ?: "Update failed")
            false
        }
    }

    /** Load [jar], and only if that succeeds make it the active bundle + record it + clean up old jars. */
    private fun activate(jar: File, version: String): Boolean {
        val ext = loadExtension(jar.absolutePath)
        if (ext == null) {
            runCatching { jar.delete() } // bad/incompatible download — don't keep it
            // Include the captured reason (e.g. an R8-stripped class in the release build) so it's visible
            // in the "Update extensions" error without needing logcat.
            error(lastExtensionError?.let { "Bundle load failed — $it" } ?: "Incompatible or invalid extension bundle")
        }
        loaded = ext
        publish(ext)
        generation++
        settings.installedExtensionFile = jar.name
        settings.installedExtensionVersion = version
        _updateAvailable.value = false // just installed the latest → clear the update indicator
        _state.value = ExtState.Installed(version, ext.sources.size)
        cleanupOldJars(keep = jar.name)
        return true
    }

    private fun currentJar(): File? {
        val dir = extensionsDir()
        settings.installedExtensionFile?.let { name ->
            File(dir, name).takeIf { it.isFile }?.let { return it }
        }
        // Back-compat: a fixed-name jar from an earlier build.
        return File(dir, "extension.jar").takeIf { it.isFile }
    }

    private fun newJarFile(): File = File(extensionsDir(), "ext-${System.currentTimeMillis()}.jar")

    private fun cleanupOldJars(keep: String) {
        runCatching {
            extensionsDir().listFiles { f ->
                f.isFile && f.name != keep &&
                    (f.name.startsWith("ext-") && f.name.endsWith(".jar") || f.name == "extension.jar")
            }?.forEach { runCatching { it.delete() } } // a jar still open by the live loader just won't delete
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
