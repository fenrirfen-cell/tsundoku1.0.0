package eu.kanade.presentation.webview

import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.os.Message
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.stack.mutableStateStackOf
import com.kevinnzou.web.AccompanistWebChromeClient
import com.kevinnzou.web.AccompanistWebViewClient
import com.kevinnzou.web.LoadingState
import com.kevinnzou.web.WebContent
import com.kevinnzou.web.WebView
import com.kevinnzou.web.WebViewNavigator
import com.kevinnzou.web.WebViewState
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.components.WarningBanner
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.util.system.getHtml
import eu.kanade.tachiyomi.util.system.setDefaultSettings
import eu.kanade.tachiyomi.util.system.setUserAgent
import kotlinx.coroutines.launch
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource

class WebViewWindow(webContent: WebContent, val navigator: WebViewNavigator) {
    var state by mutableStateOf(WebViewState(webContent))
    var popupMessage: Message? = null
        private set
    var webView: WebView? = null

    constructor(popupMessage: Message, navigator: WebViewNavigator) : this(WebContent.NavigatorOnly, navigator) {
        this.popupMessage = popupMessage
    }
}

@Composable
fun WebViewScreenContent(
    onNavigateUp: () -> Unit,
    initialTitle: String?,
    url: String,
    defaultUserAgentProvider: () -> String,
    onShare: (String) -> Unit,
    onOpenInBrowser: (String) -> Unit,
    onClearCookies: (String) -> Unit,
    headers: Map<String, String> = emptyMap(),
    onUrlChange: (String) -> Unit = {},
    // Fenrir: when set, a download started on the page is handed over (url, page it came from)
    onDownload: ((url: String, pageUrl: String?) -> Unit)? = null,
    // Fenrir: offer ad blocking (Browse in Import a book); on/off is remembered
    adBlockAvailable: Boolean = false,
) {
    val fenrirContext = androidx.compose.ui.platform.LocalContext.current
    var blockAds by remember { mutableStateOf(adBlockAvailable && FenrirAdBlock.isEnabled(fenrirContext)) }
    val blockAdsNow = androidx.compose.runtime.rememberUpdatedState(blockAds)
    // Fenrir (Browse): books gathered so far, the download in progress, and the page's book list
    val importMode = onDownload != null
    val imports = eu.kanade.presentation.library.components.FenrirBrowserImports
    val collected by imports.collected.collectAsState()
    val importProgress by imports.progress.collectAsState()
    var pageBooks by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val windowStack = remember {
        mutableStateStackOf(
            WebViewWindow(
                WebContent.Url(url = url, additionalHttpHeaders = headers),
                WebViewNavigator(coroutineScope),
            ),
        )
    }

    val currentWindow = windowStack.lastItemOrNull!!
    val navigator = currentWindow.navigator

    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()

    var currentUrl by remember { mutableStateOf(url) }
    var showCloudflareHelp by remember { mutableStateOf(false) }
    var isActive by remember { mutableStateOf(true) }

    DisposableEffect(Unit) {
        onDispose { isActive = false }
    }

    val webClient = remember {
        object : AccompanistWebViewClient() {
            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                url?.let {
                    currentUrl = it
                    onUrlChange(it)
                }
            }

            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)
                scope.launch {
                    val html = view.getHtml()
                    showCloudflareHelp = "window._cf_chl_opt" in html || "Ray ID is" in html
                }
            }

            override fun doUpdateVisitedHistory(
                view: WebView,
                url: String?,
                isReload: Boolean,
            ) {
                super.doUpdateVisitedHistory(view, url, isReload)
                url?.let {
                    currentUrl = it
                    onUrlChange(it)
                }
            }

            // Fenrir: requests to ad networks get an empty answer while ad blocking is on
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?,
            ): android.webkit.WebResourceResponse? {
                if (blockAdsNow.value && FenrirAdBlock.isAdHost(request?.url?.host)) return FenrirAdBlock.blocked()
                return super.shouldInterceptRequest(view, request)
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?,
            ): Boolean {
                val url = request?.url?.toString() ?: return false

                // Fenrir: with ad blocking on, an ad site, or a jump to another site the page made by
                // itself (not a tap, not the server's own redirect), is stopped
                val req = request ?: return false
                if (blockAdsNow.value && req.isForMainFrame) {
                    if (FenrirAdBlock.isAdHost(req.url?.host)) return true
                    if (!req.hasGesture() && !req.isRedirect && !FenrirAdBlock.sameSite(url, view?.url)) return true
                }

                // Ignore intents urls
                if (url.startsWith("intent://")) return true

                // Only open valid web urls
                if (url.startsWith("http") || url.startsWith("https")) {
                    if (url != view?.url) {
                        view?.loadUrl(url, headers)
                        return true
                    }
                }

                return false
            }
        }
    }

    val webChromeClient = remember {
        object : AccompanistWebChromeClient() {
            override fun onCreateWindow(
                view: WebView,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message,
            ): Boolean {
                // Fenrir: with ad blocking on, no pop-up / new-tab windows. A tapped new-tab link still
                // works when it's on the same site or looks like a download: it opens in this page.
                // Anything else (pop-under ads) is dropped.
                if (blockAdsNow.value) {
                    if (!isUserGesture) return false
                    val catcher = WebView(view.context)
                    catcher.webViewClient = object : android.webkit.WebViewClient() {
                        override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                            val target = r.url.toString()
                            if (FenrirAdBlock.looksWanted(target, view.url)) view.loadUrl(target)
                            v.post { v.destroy() }
                            return true
                        }
                    }
                    (resultMsg.obj as? WebView.WebViewTransport)?.webView = catcher
                    resultMsg.sendToTarget()
                    return true
                }
                // if it wasn't initiated by a user gesture, we should ignore it like a normal browser would
                if (isUserGesture) {
                    windowStack.push(WebViewWindow(resultMsg, WebViewNavigator(coroutineScope)))
                    return true
                }
                return false
            }

            override fun onJsAlert(view: WebView, url: String?, message: String?, result: JsResult): Boolean {
                if (!isActive) {
                    result.confirm()
                    return true
                }
                return super.onJsAlert(view, url, message, result)
            }

            override fun onJsConfirm(view: WebView, url: String?, message: String?, result: JsResult): Boolean {
                if (!isActive) {
                    result.cancel()
                    return true
                }
                return super.onJsConfirm(view, url, message, result)
            }

            override fun onJsPrompt(
                view: WebView,
                url: String?,
                message: String?,
                defaultValue: String?,
                result: JsPromptResult,
            ): Boolean {
                if (!isActive) {
                    result.cancel()
                    return true
                }
                return super.onJsPrompt(view, url, message, defaultValue, result)
            }
        }
    }

    fun initializePopup(webView: WebView, message: Message): WebView {
        val transport = message.obj as WebView.WebViewTransport
        transport.webView = webView
        message.sendToTarget()
        return webView
    }

    val popState = remember<() -> Unit> {
        {
            if (windowStack.size == 1) {
                onNavigateUp()
            } else {
                windowStack.pop()
            }
        }
    }

    BackHandler(windowStack.size > 1, popState)

    Scaffold(
        topBar = {
            Box {
                Column {
                    AppBar(
                        title = currentWindow.state.pageTitle ?: initialTitle,
                        subtitle = currentUrl,
                        navigateUp = onNavigateUp,
                        navigationIcon = Icons.Outlined.Close,
                        actions = {
                            AppBarActions(
                                listOf(
                                    AppBar.Action(
                                        title = stringResource(MR.strings.action_webview_back),
                                        icon = Icons.AutoMirrored.Outlined.ArrowBack,
                                        onClick = {
                                            if (navigator.canGoBack) {
                                                navigator.navigateBack()
                                            }
                                        },
                                        enabled = navigator.canGoBack,
                                    ),
                                    AppBar.Action(
                                        title = stringResource(MR.strings.action_webview_forward),
                                        icon = Icons.AutoMirrored.Outlined.ArrowForward,
                                        onClick = {
                                            if (navigator.canGoForward) {
                                                navigator.navigateForward()
                                            }
                                        },
                                        enabled = navigator.canGoForward,
                                    ),
                                    AppBar.OverflowAction(
                                        title = stringResource(MR.strings.action_webview_refresh),
                                        onClick = { navigator.reload() },
                                    ),
                                    AppBar.OverflowAction(
                                        title = stringResource(MR.strings.action_share),
                                        onClick = { onShare(currentUrl) },
                                    ),
                                    AppBar.OverflowAction(
                                        title = stringResource(MR.strings.action_open_in_browser),
                                        onClick = { onOpenInBrowser(currentUrl) },
                                    ),
                                    AppBar.OverflowAction(
                                        title = stringResource(MR.strings.pref_clear_cookies),
                                        onClick = { onClearCookies(currentUrl) },
                                    ),
                                ).toMutableList().apply {
                                    if (importMode) {
                                        add(
                                            0,
                                            AppBar.OverflowAction(
                                                title = "Get all books on this page",
                                                onClick = {
                                                    scope.launch {
                                                        val raw = currentWindow.webView?.let { wv ->
                                                            runCatching { wv.getHtml() }.getOrNull()
                                                        }.orEmpty()
                                                        // the page's HTML comes back as a JavaScript string
                                                        val html = runCatching { org.json.JSONTokener(raw).nextValue() as? String }.getOrNull() ?: raw
                                                        val found = imports.pageBooks(currentUrl, html)
                                                        if (found.isEmpty()) {
                                                            android.widget.Toast.makeText(fenrirContext, "No book downloads found on this page.", android.widget.Toast.LENGTH_LONG).show()
                                                        } else {
                                                            pageBooks = found
                                                        }
                                                    }
                                                },
                                            ),
                                        )
                                    }
                                    if (adBlockAvailable) {
                                        add(
                                            AppBar.OverflowAction(
                                                title = if (blockAds) "Block ads: on (tap to turn off)" else "Block ads: off (tap to turn on)",
                                                onClick = {
                                                    blockAds = !blockAds
                                                    FenrirAdBlock.setEnabled(fenrirContext, blockAds)
                                                    navigator.reload()
                                                },
                                            ),
                                        )
                                    }
                                    if (windowStack.size > 1) {
                                        add(
                                            0,
                                            AppBar.Action(
                                                title = stringResource(MR.strings.action_webview_close_tab),
                                                icon = ImageVector.vectorResource(R.drawable.ic_tab_close_24px),
                                                onClick = popState,
                                            ),
                                        )
                                    }
                                },
                            )
                        },
                    )

                    if (showCloudflareHelp) {
                        Surface(
                            modifier = Modifier.padding(8.dp),
                        ) {
                            WarningBanner(
                                textRes = MR.strings.information_cloudflare_help,
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.small)
                                    .clickable {
                                        uriHandler.openUri(
                                            "https://tsundoku-otaku.github.io/docs/guides/troubleshooting/#cloudflare",
                                        )
                                    },
                            )
                        }
                    }
                }
                when (val loadingState = currentWindow.state.loadingState) {
                    is LoadingState.Initializing -> LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter),
                    )
                    is LoadingState.Loading -> LinearProgressIndicator(
                        progress = { loadingState.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter),
                    )
                    else -> {}
                }
            }
        },
    ) { contentPadding ->
        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
        // We need to key the WebView composable to the window object since simply updating the WebView composable will
        // not cause it to re-invoke the WebView factory and render the new current window's WebView. This lets us
        // completely reset the WebView composable when the current window switches.
        key(currentWindow) {
            WebView(
                state = currentWindow.state,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                navigator = navigator,
                onCreated = { webView ->
                    webView.setDefaultSettings()

                    // Debug mode (chrome://inspect/#devices)
                    if (BuildConfig.DEBUG &&
                        0 != webView.context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE
                    ) {
                        WebView.setWebContentsDebuggingEnabled(true)
                    }

                    webView.setUserAgent(headers["user-agent"] ?: defaultUserAgentProvider())
                    if (onDownload != null) {
                        webView.setDownloadListener { downloadUrl, _, _, _, _ ->
                            onDownload(downloadUrl, webView.url)
                        }
                    }
                },
                onDispose = { webView ->
                    val window = windowStack.items.find { it.webView == webView }
                    if (window == null) {
                        // If we couldn't find any window on the stack that owns this WebView, it means that we can
                        // safely dispose of it because the window containing it has been closed.
                        webView.destroy()
                    } else {
                        // The composable is being disposed but the WebView object is not.
                        // When the WebView element is recomposed, we will want the WebView to resume from its state
                        // before it was unmounted, we won't want it to reset back to its original target.
                        window.state.content = WebContent.NavigatorOnly
                    }
                },
                client = webClient,
                chromeClient = webChromeClient,
                factory = { context ->
                    currentWindow.webView
                        ?: WebView(context).also { webView ->
                            currentWindow.webView = webView
                            currentWindow.popupMessage?.let {
                                initializePopup(webView, it)
                            }
                        }
                },
            )
        }
        // Fenrir (Browse): what's been gathered, and the import button
        if (importMode && (collected.isNotEmpty() || importProgress != null)) {
            androidx.compose.material3.Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(contentPadding)
                    .padding(12.dp)
                    .fillMaxWidth(),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
                color = androidx.compose.ui.graphics.Color(0xFF1E1E24),
                shadowElevation = 6.dp,
            ) {
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                        Text(
                            text = when (collected.size) {
                                0 -> "No books yet"
                                1 -> "1 book ready"
                                else -> "${collected.size} books ready"
                            },
                            color = androidx.compose.ui.graphics.Color.White,
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = importProgress ?: collected.lastOrNull()?.first.orEmpty(),
                            color = androidx.compose.ui.graphics.Color(0xFF9A9AA6),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                        )
                    }
                    if (collected.isNotEmpty()) {
                        androidx.compose.material3.Button(
                            onClick = onNavigateUp, // leaving the browser imports them all
                            enabled = importProgress == null,
                        ) { Text(if (collected.size == 1) "Import" else "Import ${collected.size}") }
                    }
                }
            }
        }
        }
        // Fenrir (Browse): pick which of the page's books to get
        pageBooks?.let { found ->
            val picked = androidx.compose.runtime.remember(found) { androidx.compose.runtime.mutableStateListOf<String>().apply { addAll(found.map { it.second }) } }
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { pageBooks = null },
                title = { Text("${found.size} books on this page") },
                text = {
                    androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 420.dp)) {
                        items(found.size) { i ->
                            val (name, link) = found[i]
                            androidx.compose.foundation.layout.Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().clickable {
                                    if (link in picked) picked.remove(link) else picked.add(link)
                                },
                            ) {
                                androidx.compose.material3.Checkbox(checked = link in picked, onCheckedChange = null)
                                Text(name, maxLines = 2, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(
                        enabled = picked.isNotEmpty() && importProgress == null,
                        onClick = {
                            val links = found.map { it.second }.filter { it in picked }
                            val from = currentUrl
                            pageBooks = null
                            scope.launch {
                                var failed = 0
                                links.forEachIndexed { i, link ->
                                    imports.progress.value = "Downloading ${i + 1} of ${links.size}…"
                                    val before = imports.collected.value.size
                                    imports.collect(fenrirContext, link, from)
                                    if (imports.collected.value.size == before) failed++
                                }
                                imports.progress.value = null
                                if (failed > 0) {
                                    android.widget.Toast.makeText(fenrirContext, "$failed of ${links.size} couldn't be downloaded.", android.widget.Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                    ) { Text("Get ${picked.size}") }
                },
                dismissButton = { androidx.compose.material3.TextButton(onClick = { pageBooks = null }) { Text("Cancel") } },
            )
        }
    }
}
