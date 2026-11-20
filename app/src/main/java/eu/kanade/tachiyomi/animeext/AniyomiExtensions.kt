package eu.kanade.tachiyomi.animeext

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.pm.PackageInfoCompat
import dalvik.system.PathClassLoader
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.AnimeSourceFactory
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.animesource.online.ParsedAnimeHttpSource
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.util.lang.Hash
import eu.kanade.tachiyomi.util.system.ChildFirstPathClassLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.Request
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File

/**
 * Fenrir Read: runs Aniyomi anime extensions (APK plugins built against Aniyomi's extension
 * library). Extensions come from repositories the user adds; they are downloaded into the app's
 * private folder and loaded from there (no system install needed). Extensions installed on the
 * phone some other way are found too, but only load after the user trusts them.
 */
object AniyomiExtensions {
    private const val TAG = "FenrirAniyomi"
    private const val FEATURE = "tachiyomi.animeextension"
    private const val META_CLASS = "tachiyomi.animeextension.class"
    private const val META_NSFW = "tachiyomi.animeextension.nsfw"
    private const val META_NAME = "aniyomix.name"
    private const val META_LIB = "aniyomix.extensionLib"
    private const val META_CONTENT_WARNING = "aniyomix.contentWarning"
    private val SUPPORTED_LIB_VERSIONS = 12.0..17.0
    private const val PREFS = "fenrir_aniyomi"
    private const val KEY_REPOS = "repos"
    private const val KEY_TRUSTED = "trusted"
    private const val KEY_SHOW_NSFW = "show_nsfw"

    @Suppress("DEPRECATION")
    private val PACKAGE_FLAGS = PackageManager.GET_CONFIGURATIONS or
        PackageManager.GET_META_DATA or
        PackageManager.GET_SIGNATURES or
        (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else 0)

    data class Installed(
        val pkg: String,
        val name: String,
        val versionName: String,
        val versionCode: Long,
        val lang: String,
        val nsfw: Boolean,
        val isPrivate: Boolean,
        val sources: List<AnimeSource>,
    )

    data class Untrusted(
        val pkg: String,
        val name: String,
        val versionName: String,
        val versionCode: Long,
        val signature: String,
    )

    data class Available(
        val pkg: String,
        val name: String,
        val versionName: String,
        val versionCode: Long,
        val lang: String,
        val nsfw: Boolean,
        val apkUrl: String,
        val iconUrl: String,
        val repo: String,
    )

    data class State(
        val repos: List<String> = emptyList(),
        val installed: List<Installed> = emptyList(),
        val untrusted: List<Untrusted> = emptyList(),
        val available: List<Available> = emptyList(),
        val showNsfw: Boolean = false,
        val busy: Boolean = false,
        val message: String? = null,
    )

    /** One playable link found in an extension. */
    data class FoundVideo(
        val sourceName: String,
        val matchedTitle: String,
        val episodeName: String?,
        val quality: String,
        val url: String,
        val headers: Map<String, String>,
        val subtitles: List<Pair<String, String>>,
    )

    /** A search result kept so the user can open its episodes. */
    data class Hit(val key: String, val sourceName: String, val title: String, val poster: String?)

    data class Episode(val key: String, val name: String, val number: Float)

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val loadMutex = Mutex()
    private lateinit var app: Application
    private val json = Json { ignoreUnknownKeys = true }
    private val hits = HashMap<String, Pair<AnimeSource, SAnime>>()
    private val episodes = HashMap<String, Pair<AnimeSource, SEpisode>>()

    val isInitialized: Boolean get() = ::app.isInitialized

    fun init(application: Application) {
        if (isInitialized) return
        app = application
        _state.update { it.copy(repos = repos(), showNsfw = prefs().getBoolean(KEY_SHOW_NSFW, false)) }
        scope.launch {
            reload()
            refreshAvailable()
        }
    }

    private fun prefs() = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun repos(): List<String> = prefs().getStringSet(KEY_REPOS, emptySet()).orEmpty().sorted()
    private fun trusted(): Set<String> = prefs().getStringSet(KEY_TRUSTED, emptySet()).orEmpty()
    private fun privateDir() = File(app.filesDir, "anime_exts").apply { mkdirs() }
    private val network: NetworkHelper by lazy { Injekt.get() }

    // ------------------------------------------------------------------ repositories

    fun addRepo(url: String) {
        val clean = normalizeRepo(url) ?: run {
            _state.update { it.copy(message = "That doesn't look like a repository link.") }
            return
        }
        prefs().edit().putStringSet(KEY_REPOS, (repos() + clean).toSet()).apply()
        _state.update { it.copy(repos = repos(), message = null) }
        scope.launch { refreshAvailable() }
    }

    fun removeRepo(url: String) {
        prefs().edit().putStringSet(KEY_REPOS, (repos() - url).toSet()).apply()
        _state.update { s -> s.copy(repos = repos(), available = s.available.filter { it.repo != url }) }
    }

    fun setShowNsfw(show: Boolean) {
        prefs().edit().putBoolean(KEY_SHOW_NSFW, show).apply()
        _state.update { it.copy(showNsfw = show) }
        scope.launch { reload(); refreshAvailable() }
    }

    fun refresh() {
        scope.launch { reload(); refreshAvailable() }
    }

    /** Accepts the repo's base URL or its index.min.json / index.json link. */
    private fun normalizeRepo(url: String): String? {
        val trimmed = url.trim().removeSuffix("/")
        if (!trimmed.startsWith("http")) return null
        return trimmed.removeSuffix("/index.min.json").removeSuffix("/index.json")
    }

    private suspend fun refreshAvailable() {
        val repos = repos()
        if (repos.isEmpty()) {
            _state.update { it.copy(available = emptyList()) }
            return
        }
        _state.update { it.copy(busy = true) }
        val showNsfw = prefs().getBoolean(KEY_SHOW_NSFW, false)
        val failed = mutableListOf<String>()
        val all = coroutineScope {
            repos.map { repo ->
                async {
                    runCatching { fetchIndex(repo) }
                        .onFailure { failed += repo; Log.w(TAG, "Repo failed: $repo", it) }
                        .getOrDefault(emptyList())
                }
            }.awaitAll().flatten()
        }
        _state.update { s ->
            s.copy(
                available = all.filter { showNsfw || !it.nsfw }.distinctBy { it.pkg }.sortedBy { it.name.lowercase() },
                busy = false,
                message = if (failed.isNotEmpty()) "Couldn't load ${failed.size} repository(s). Check the link." else s.message,
            )
        }
    }

    private suspend fun fetchIndex(repo: String): List<Available> = withContext(Dispatchers.IO) {
        val body = network.client.newCall(Request.Builder().url("$repo/index.min.json").build()).execute().use {
            if (!it.isSuccessful) error("HTTP ${it.code}")
            it.body.string()
        }
        val array = json.parseToJsonElement(body) as JsonArray
        array.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            fun str(key: String) = obj[key]?.jsonPrimitive?.contentOrNull
            val pkg = str("pkg") ?: return@mapNotNull null
            val apk = str("apk") ?: return@mapNotNull null
            Available(
                pkg = pkg,
                name = (str("name") ?: pkg).removePrefix("Aniyomi: "),
                versionName = str("version").orEmpty(),
                versionCode = obj["code"]?.jsonPrimitive?.longOrNull ?: 0L,
                lang = str("lang").orEmpty(),
                nsfw = (obj["nsfw"]?.jsonPrimitive?.intOrNull ?: 0) == 1,
                apkUrl = "$repo/apk/$apk",
                iconUrl = "$repo/icon/$pkg.png",
                repo = repo,
            )
        }
    }

    // ------------------------------------------------------------------ install / remove / trust

    fun install(pkg: String) {
        val item = _state.value.available.firstOrNull { it.pkg == pkg } ?: return
        scope.launch {
            _state.update { it.copy(busy = true, message = "Installing ${item.name}…") }
            val result = runCatching { downloadAndInstall(item) }
            _state.update {
                it.copy(
                    busy = false,
                    message = result.exceptionOrNull()?.let { e -> "Couldn't install ${item.name}: ${e.message}" },
                )
            }
            reload()
        }
    }

    private suspend fun downloadAndInstall(item: Available) = withContext(Dispatchers.IO) {
        val temp = File(app.cacheDir, "anime_ext_${item.pkg}.apk")
        network.client.newCall(Request.Builder().url(item.apkUrl).build()).execute().use { response ->
            if (!response.isSuccessful) error("download failed (HTTP ${response.code})")
            temp.outputStream().use { out -> response.body.byteStream().copyTo(out) }
        }
        val info = app.packageManager.getPackageArchiveInfo(temp.absolutePath, PACKAGE_FLAGS)
            ?: error("not a valid extension file")
        if (!isExtension(info)) error("not an Aniyomi extension")
        val signature = signatures(info)?.lastOrNull() ?: error("not signed")
        val target = File(privateDir(), "${info.packageName}.ext")
        target.setWritable(true)
        target.delete()
        temp.copyTo(target, overwrite = true)
        // Android 14+ refuses to load code from files that can still be written.
        target.setReadOnly()
        temp.delete()
        // Installed from the user's own repository: trust this signature.
        trust(info.packageName, signature, reload = false)
    }

    fun uninstall(pkg: String) {
        val file = File(privateDir(), "$pkg.ext")
        if (file.exists()) {
            file.setWritable(true)
            file.delete()
            scope.launch { reload() }
        } else {
            val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { app.startActivity(intent) }
        }
    }

    fun trust(pkg: String) {
        val item = _state.value.untrusted.firstOrNull { it.pkg == pkg } ?: return
        trust(pkg, item.signature, reload = true)
    }

    private fun trust(pkg: String, signature: String, reload: Boolean) {
        prefs().edit().putStringSet(KEY_TRUSTED, trusted() + "$pkg:$signature").apply()
        if (reload) scope.launch { reload() }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }

    // ------------------------------------------------------------------ loading

    private suspend fun reload() = loadMutex.withLock {
        val pm = app.packageManager
        val showNsfw = prefs().getBoolean(KEY_SHOW_NSFW, false)
        val trusted = trusted()
        val privatePkgs = privateDir().listFiles().orEmpty()
            .filter { it.isFile && it.extension == "ext" }
            .mapNotNull { file ->
                pm.getPackageArchiveInfo(file.absolutePath, PACKAGE_FLAGS)?.also {
                    it.applicationInfo?.fixPaths(file.absolutePath)
                }
            }
            .filter(::isExtension)
        @Suppress("DEPRECATION")
        val sharedPkgs = runCatching { pm.getInstalledPackages(PACKAGE_FLAGS) }.getOrDefault(emptyList())
            .filter(::isExtension)
            .filter { shared -> privatePkgs.none { it.packageName == shared.packageName } }

        val installed = mutableListOf<Installed>()
        val untrusted = mutableListOf<Untrusted>()
        (privatePkgs.map { it to true } + sharedPkgs.map { it to false }).forEach { (info, isPrivate) ->
            runCatching { load(info, isPrivate, trusted, showNsfw) }
                .onFailure { Log.w(TAG, "Failed to load ${info.packageName}", it) }
                .getOrNull()
                ?.let { result ->
                    when (result) {
                        is Installed -> installed += result
                        is Untrusted -> untrusted += result
                    }
                }
        }
        _state.update { it.copy(installed = installed.sortedBy { e -> e.name.lowercase() }, untrusted = untrusted) }
    }

    private fun load(info: PackageInfo, isPrivate: Boolean, trusted: Set<String>, showNsfw: Boolean): Any? {
        val pm = app.packageManager
        val appInfo = info.applicationInfo ?: return null
        val meta = appInfo.metaData ?: return null
        val name = meta.getString(META_NAME)
            ?: pm.getApplicationLabel(appInfo).toString().substringAfter("Aniyomi: ")
        val versionName = info.versionName ?: return null
        val versionCode = PackageInfoCompat.getLongVersionCode(info)
        val libVersion = meta.getInt(META_LIB).takeUnless { it == 0 }?.toDouble()
            ?: versionName.substringBeforeLast('.').toDoubleOrNull()
        if (libVersion == null || libVersion !in SUPPORTED_LIB_VERSIONS) {
            Log.w(TAG, "${info.packageName}: unsupported lib version $libVersion")
            return null
        }
        val signature = signatures(info)?.lastOrNull() ?: return null
        if ("${info.packageName}:$signature" !in trusted) {
            return Untrusted(info.packageName, name, versionName, versionCode, signature)
        }
        val nsfw = meta.getInt(META_CONTENT_WARNING) > 0 || meta.getInt(META_NSFW) == 1
        if (nsfw && !showNsfw) return null

        val classLoader = ChildFirstPathClassLoader(appInfo.sourceDir, null, app.classLoader)
        val classNames = meta.getString(META_CLASS).orEmpty().split(";").map { raw ->
            val trimmed = raw.trim()
            if (trimmed.startsWith(".")) info.packageName + trimmed else trimmed
        }.filter { it.isNotBlank() }
        val sources = classNames.flatMap { className ->
            val instance = try {
                Class.forName(className, false, classLoader).getDeclaredConstructor().newInstance()
            } catch (e: LinkageError) {
                val fallback = PathClassLoader(appInfo.sourceDir, null, app.classLoader)
                Class.forName(className, false, fallback).getDeclaredConstructor().newInstance()
            }
            when (instance) {
                is AnimeSource -> listOf(instance)
                is AnimeSourceFactory -> instance.createSources()
                else -> emptyList()
            }
        }
        val langs = sources.map { it.lang }.toSet()
        return Installed(
            pkg = info.packageName,
            name = name,
            versionName = versionName,
            versionCode = versionCode,
            lang = if (langs.size == 1) langs.first() else "all",
            nsfw = nsfw,
            isPrivate = isPrivate,
            sources = sources,
        )
    }

    private fun isExtension(info: PackageInfo): Boolean =
        info.reqFeatures.orEmpty().any { it.name == FEATURE }

    @Suppress("DEPRECATION")
    private fun signatures(info: PackageInfo): List<String>? {
        val raw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signing = info.signingInfo ?: return null
            if (signing.hasMultipleSigners()) signing.apkContentsSigners else signing.signingCertificateHistory
        } else {
            info.signatures
        }
        return raw?.map { Hash.sha256(it.toByteArray()) }
    }

    private fun ApplicationInfo.fixPaths(path: String) {
        if (sourceDir == null) sourceDir = path
        if (publicSourceDir == null) publicSourceDir = path
    }

    private fun allSources(): List<AnimeCatalogueSource> =
        _state.value.installed.flatMap { it.sources }.filterIsInstance<AnimeCatalogueSource>()

    val hasSources: Boolean get() = allSources().isNotEmpty()

    // ------------------------------------------------------------------ finding streams

    /**
     * Looks the show up by name in every installed extension, picks the matching episode and
     * calls [onFound] with each extension's links as they come in.
     */
    suspend fun findStreams(
        title: String,
        altTitles: List<String>,
        isMovie: Boolean,
        season: Int?,
        episode: Int?,
        onFound: suspend (sourceName: String, videos: List<FoundVideo>) -> Unit,
    ) = coroutineScope {
        val permits = Semaphore(4)
        allSources().map { source ->
            async(Dispatchers.IO) {
                permits.withPermit {
                    val videos = withTimeoutOrNull(30_000) {
                        runCatching { findInSource(source, title, altTitles, isMovie, season, episode) }
                            .onFailure { Log.w(TAG, "${source.name}: ${it.message}") }
                            .getOrNull()
                    }.orEmpty()
                    onFound(source.name, videos)
                }
            }
        }.awaitAll()
    }

    private suspend fun findInSource(
        source: AnimeCatalogueSource,
        title: String,
        altTitles: List<String>,
        isMovie: Boolean,
        season: Int?,
        episode: Int?,
    ): List<FoundVideo> {
        val queries = buildList {
            if (!isMovie && season != null && season > 1) {
                add("$title Season $season")
                add("$title $season")
            }
            add(title)
            addAll(altTitles)
        }.distinct()
        var best: SAnime? = null
        for (query in queries) {
            val results = source.getSearchAnime(1, query, source.getFilterList()).animes
            best = bestMatch(results, query)
            if (best != null) break
        }
        val anime = best ?: return emptyList()
        val list = episodesOf(source, anime)
        val target = when {
            list.isEmpty() -> return emptyList()
            isMovie || list.size == 1 -> list.first()
            episode != null -> list.firstOrNull { it.episode_number == episode.toFloat() }
                ?: list.sortedBy { it.episode_number }.getOrNull(episode - 1)
            else -> list.first()
        } ?: return emptyList()
        return videosOf(source, target).map { it.copy(matchedTitle = anime.title) }
    }

    private fun normalize(text: String): String =
        text.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()

    private fun bestMatch(results: List<SAnime>, query: String): SAnime? {
        val q = normalize(query)
        if (q.isEmpty()) return null
        return results
            .map { it to normalize(it.title) }
            .mapNotNull { (anime, name) ->
                val score = when {
                    name == q -> 100
                    name.startsWith(q) && (name.length - q.length) <= 12 -> 80
                    q.startsWith(name) && name.length >= 4 -> 60
                    name.contains(q) && q.length >= 5 -> 50
                    else -> 0
                }
                if (score > 0) anime to score else null
            }
            .maxByOrNull { it.second }
            ?.first
    }

    @Suppress("DEPRECATION")
    private suspend fun episodesOf(source: AnimeSource, anime: SAnime): List<SEpisode> =
        try {
            source.getEpisodeList(anime)
        } catch (_: UnsupportedOperationException) {
            source.getAnimeEpisodeUpdate(anime, emptyList(), fetchDetails = false, fetchEpisodes = true).episodes
        }

    /** True when the extension uses the newer hoster API (ext lib 16+). */
    private fun usesHosters(source: AnimeHttpSource): Boolean {
        var current: Class<*>? = source.javaClass
        while (current != null) {
            if (current == ParsedAnimeHttpSource::class.java || current == AnimeHttpSource::class.java) return false
            if (current.declaredMethods.any { it.name in setOf("getHosterList", "hosterListRequest", "hosterListParse") }) {
                return true
            }
            current = current.superclass
        }
        return false
    }

    @Suppress("DEPRECATION")
    private suspend fun videosOf(source: AnimeSource, episode: SEpisode): List<FoundVideo> {
        val http = source as? AnimeHttpSource
        val raw: List<Video> = if (http != null && usesHosters(http)) {
            http.getHosterList(episode).flatMap { hoster ->
                runCatching { hoster.videoList ?: http.getVideoList(hoster) }.getOrDefault(emptyList())
            }
        } else {
            source.getVideoList(episode)
        }
        val sorted = if (http != null) with(http) { raw.sortVideos() } else raw
        return sorted.mapNotNull { video ->
            val ready = if (!video.initialized && video.videoUrl.isBlank() && http != null) {
                runCatching { http.resolveVideo(video) }.getOrNull()
            } else {
                video
            } ?: return@mapNotNull null
            if (ready.videoUrl.isBlank() || ready.videoUrl == "null" || ready.usesHttpServer()) return@mapNotNull null
            val headers = (ready.headers ?: http?.headers)?.let { h -> h.names().associateWith { h[it].orEmpty() } }.orEmpty()
            FoundVideo(
                sourceName = source.name,
                matchedTitle = "",
                episodeName = episode.name,
                quality = ready.videoTitle.ifBlank { "Video" },
                url = ready.videoUrl,
                headers = headers,
                subtitles = ready.subtitleTracks.map { it.url to it.lang },
            )
        }
    }

    // ------------------------------------------------------------------ browsing

    suspend fun search(query: String): List<Hit> = coroutineScope {
        allSources().map { source ->
            async(Dispatchers.IO) {
                withTimeoutOrNull(20_000) {
                    runCatching { source.getSearchAnime(1, query, source.getFilterList()).animes }.getOrNull()
                }.orEmpty().take(12).map { anime ->
                    val key = "${source.id}|${anime.url}"
                    synchronized(hits) { hits[key] = source to anime }
                    Hit(key, source.name, anime.title, anime.thumbnail_url)
                }
            }
        }.awaitAll().flatten()
    }

    suspend fun episodes(hitKey: String): List<Episode> = withContext(Dispatchers.IO) {
        val (source, anime) = synchronized(hits) { hits[hitKey] } ?: return@withContext emptyList()
        episodesOf(source, anime).sortedBy { it.episode_number }.map { ep ->
            val key = "${source.id}|${ep.url}"
            synchronized(episodes) { episodes[key] = source to ep }
            Episode(key, ep.name, ep.episode_number)
        }
    }

    suspend fun videos(episodeKey: String): List<FoundVideo> = withContext(Dispatchers.IO) {
        val (source, ep) = synchronized(episodes) { episodes[episodeKey] } ?: return@withContext emptyList()
        videosOf(source, ep)
    }
}
