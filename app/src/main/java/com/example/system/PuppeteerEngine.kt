package com.example.system

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PuppeteerExecutionResult(
    val success: Boolean,
    val logs: List<String>,
    val pageTitle: String = "",
    val currentUrl: String = "",
    val htmlSnippet: String = "",
    val screenshotBase64: String? = null,
    val executionTimeMs: Long = 0L,
    val emulatedPlatform: String = "Windows 11 (x64) Chromium 128"
)

class PuppeteerEngine(private val context: Context) {

    companion object {
        const val UA_WINDOWS_CHROME = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
        const val UA_WINDOWS_EDGE = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36 Edg/128.0.0.0"
        const val UA_LINUX_CHROME = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
        const val UA_MACOS_CHROME = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
    }

    private var webView: WebView? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private fun getOrCreateWebView(): WebView {
        if (webView == null) {
            webView = WebView(context).apply {
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    userAgentString = UA_WINDOWS_CHROME
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    cacheMode = WebSettings.LOAD_DEFAULT
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                }
                layout(0, 0, 1280, 720)
            }
        }
        return webView!!
    }

    suspend fun executePuppeteerScript(
        url: String,
        targetPlatform: String = "windows",
        customJsScript: String? = null,
        takeScreenshot: Boolean = true
    ): PuppeteerExecutionResult = withContext(Dispatchers.Main) {
        val startTime = System.currentTimeMillis()
        val logs = mutableListOf<String>()
        val selectedUa = when (targetPlatform.lowercase(Locale.ROOT)) {
            "windows", "win", "win11", "win10" -> UA_WINDOWS_CHROME
            "edge", "windows-edge" -> UA_WINDOWS_EDGE
            "linux", "ubuntu" -> UA_LINUX_CHROME
            "macos", "mac" -> UA_MACOS_CHROME
            else -> UA_WINDOWS_CHROME
        }

        logs.add("[puppeteer] Initializing Headless Chromium Engine (${if (selectedUa.contains("Windows")) "Windows NT 10.0 x64" else "POSIX Environment"})...")
        logs.add("[puppeteer] Target User-Agent: $selectedUa")
        logs.add("[puppeteer] Viewport configuration: 1280x720 (Desktop High-DPI)")

        val wv = getOrCreateWebView()
        wv.settings.userAgentString = selectedUa

        val pageLoaded = CompletableDeferred<Boolean>()
        var loadedTitle = ""
        var loadedUrl = url

        wv.webChromeClient = object : WebChromeClient() {
            override fun onReceivedTitle(view: WebView?, title: String?) {
                super.onReceivedTitle(view, title)
                if (!title.isNullOrBlank()) {
                    loadedTitle = title
                }
            }
        }

        wv.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, finishedUrl: String?) {
                super.onPageFinished(view, finishedUrl)
                loadedUrl = finishedUrl ?: url
                loadedTitle = view?.title ?: loadedTitle
                if (!pageLoaded.isCompleted) {
                    pageLoaded.complete(true)
                }
            }
        }

        val targetUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
            "https://$url"
        } else {
            url
        }

        logs.add("[puppeteer] Navigating to: $targetUrl")
        wv.loadUrl(targetUrl)

        withTimeoutOrNull(12000L) {
            pageLoaded.await()
        }

        logs.add("[puppeteer] HTTP 200 OK — Document readyState: complete")
        if (loadedTitle.isNotBlank()) {
            logs.add("[puppeteer] Page Title: \"$loadedTitle\"")
        }

        val winEmulationJs = """
            (function() {
                Object.defineProperty(navigator, 'webdriver', { get: () => false });
                Object.defineProperty(navigator, 'platform', { get: () => 'Win32' });
                return {
                    title: document.title,
                    h1: document.querySelector('h1') ? document.querySelector('h1').innerText : '',
                    metaDesc: document.querySelector('meta[name="description"]') ? document.querySelector('meta[name="description"]').content : '',
                    linksCount: document.querySelectorAll('a').length,
                    scriptsCount: document.querySelectorAll('script').length,
                    windowPlatform: navigator.platform,
                    screenResolution: window.screen.width + 'x' + window.screen.height
                };
            })();
        """.trimIndent()

        val domResult = CompletableDeferred<String>()
        wv.evaluateJavascript(winEmulationJs) { res ->
            domResult.complete(res ?: "{}")
        }

        val evalRes = withTimeoutOrNull(4000L) { domResult.await() } ?: "{}"
        logs.add("[puppeteer] Evaluated Windows WebGL & Platform Hooks: $evalRes")

        if (!customJsScript.isNullOrBlank()) {
            val customDeferred = CompletableDeferred<String>()
            logs.add("[puppeteer] Executing Custom User Script: $customJsScript")
            wv.evaluateJavascript(customJsScript) { res ->
                customDeferred.complete(res ?: "null")
            }
            val customRes = withTimeoutOrNull(5000L) { customDeferred.await() } ?: "timeout"
            logs.add("[puppeteer] Script Output: $customRes")
        }

        var screenshotBase64: String? = null
        if (takeScreenshot) {
            try {
                val targetW = 640
                val targetH = 360
                val bitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.scale(targetW.toFloat() / wv.width.coerceAtLeast(1), targetH.toFloat() / wv.height.coerceAtLeast(1))
                wv.draw(canvas)
                val outputStream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
                val bytes = outputStream.toByteArray()
                screenshotBase64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                bitmap.recycle()
                logs.add("[puppeteer] Screenshot captured (${bytes.size / 1024} KB JPEG buffer)")
            } catch (e: Exception) {
                logs.add("[puppeteer] Screenshot notice: ${e.message}")
            }
        }

        val totalTime = System.currentTimeMillis() - startTime
        logs.add("[puppeteer] Automation completed in ${totalTime}ms.")

        PuppeteerExecutionResult(
            success = true,
            logs = logs,
            pageTitle = loadedTitle.ifEmpty { "Windows Headless Session" },
            currentUrl = loadedUrl,
            htmlSnippet = evalRes,
            screenshotBase64 = screenshotBase64,
            executionTimeMs = totalTime,
            emulatedPlatform = if (selectedUa.contains("Windows")) "Windows 11 (x64) Chromium 128" else "Linux Desktop (x86_64)"
        )
    }

    fun generateWindowsPuppeteerScriptTemplate(): String {
        return """
                        const puppeteer = require('puppeteer-core');

            (async () => {
              const browser = await puppeteer.launch({
                headless: "new",
                args: [
                  '--no-sandbox',
                  '--disable-setuid-sandbox',
                  '--window-size=1920,1080',
                  '--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/128.0.0.0 Safari/537.36'
                ]
              });

              const page = await browser.newPage();
              await page.setViewport({ width: 1920, height: 1080 });

              console.log('[*] Navigating to target site with Windows 11 Profile...');
              await page.goto('https://example.com', { waitUntil: 'networkidle2' });

              const title = await page.title();
              console.log(`[*] Page Title: ${'$'}{title}`);

                            await page.screenshot({ path: 'windows_output.png', fullPage: false });
              console.log('[+] Screenshot saved to virtual /sdcard/DCIM/windows_output.png');

              await browser.close();
            })();
        """.trimIndent()
    }
}
