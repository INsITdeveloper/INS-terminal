package com.example.system

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentLinkedQueue

class HeadlessJsRuntimeEngine(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var webView: WebView? = null
    private val outputBuffer = ConcurrentLinkedQueue<String>()
    private var completionDeferred: CompletableDeferred<List<String>>? = null
    private var onLiveOutputListener: ((String) -> Unit)? = null

    init {
        mainHandler.post {
            try {
                webView = WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    settings.allowFileAccess = true
                    settings.allowContentAccess = true
                    addJavascriptInterface(AndroidJsBridge(), "AndroidBridge")
                    webViewClient = object : WebViewClient() {}
                }
            } catch (_: Exception) {}
        }
    }

    fun setOnLiveOutputListener(listener: ((String) -> Unit)?) {
        this.onLiveOutputListener = listener
    }

    inner class AndroidJsBridge {

        @JavascriptInterface
        fun consoleLog(msg: String) {
            outputBuffer.add(msg)
            onLiveOutputListener?.invoke(msg)
        }

        @JavascriptInterface
        fun consoleWarn(msg: String) {
            val formatted = "[WARN] $msg"
            outputBuffer.add(formatted)
            onLiveOutputListener?.invoke(formatted)
        }

        @JavascriptInterface
        fun consoleError(msg: String) {
            val formatted = "[ERROR] $msg"
            outputBuffer.add(formatted)
            onLiveOutputListener?.invoke(formatted)
        }

        @JavascriptInterface
        fun execSync(cmd: String): String {
            val shell = SystemShellEngine(context)
            val startTime = System.currentTimeMillis()
            val isRoot = shell.isDeviceRooted()

            val result = if (isRoot) {
                shell.runRootProcess(cmd, "/storage/emulated/0", startTime)
            } else {
                shell.runUniversalNonRootEngine(cmd, "/storage/emulated/0", startTime)
            }
            return result.output
        }

        @JavascriptInterface
        fun readFile(path: String): String {
            return try {
                val f = StorageAccessEngine.resolvePath(path, "/storage/emulated/0", context)
                if (f.exists() && f.isFile) f.readText() else ""
            } catch (e: Exception) {
                ""
            }
        }

        @JavascriptInterface
        fun writeFile(path: String, data: String): Boolean {
            return try {
                val f = StorageAccessEngine.resolvePath(path, "/storage/emulated/0", context)
                f.parentFile?.mkdirs()
                f.writeText(data)
                true
            } catch (_: Exception) {
                false
            }
        }

        @JavascriptInterface
        fun appendFile(path: String, data: String): Boolean {
            return try {
                val f = StorageAccessEngine.resolvePath(path, "/storage/emulated/0", context)
                f.parentFile?.mkdirs()
                f.appendText(data)
                true
            } catch (_: Exception) {
                false
            }
        }

        @JavascriptInterface
        fun fileExists(path: String): Boolean {
            return try {
                val f = StorageAccessEngine.resolvePath(path, "/storage/emulated/0", context)
                f.exists()
            } catch (_: Exception) {
                false
            }
        }

        @JavascriptInterface
        fun mkdir(path: String): Boolean {
            return try {
                val f = StorageAccessEngine.resolvePath(path, "/storage/emulated/0", context)
                f.mkdirs()
            } catch (_: Exception) {
                false
            }
        }

        @JavascriptInterface
        fun readdir(path: String): String {
            return try {
                val f = StorageAccessEngine.resolvePath(path, "/storage/emulated/0", context)
                val list = f.list() ?: emptyArray()
                JSONArray(list).toString()
            } catch (_: Exception) {
                "[]"
            }
        }

        @JavascriptInterface
        fun httpGet(urlStr: String): String {
            return try {
                val url = URL(urlStr)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.inputStream.bufferedReader().readText()
            } catch (e: Exception) {
                "[HTTP Error]: ${e.message}"
            }
        }

        @JavascriptInterface
        fun httpPost(urlStr: String, body: String): String {
            return try {
                val url = URL(urlStr)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.outputStream.bufferedWriter().use { it.write(body) }
                conn.inputStream.bufferedReader().readText()
            } catch (e: Exception) {
                "[HTTP Post Error]: ${e.message}"
            }
        }

        @JavascriptInterface
        fun getCwd(): String {
            return "/storage/emulated/0"
        }

        @JavascriptInterface
        fun onScriptComplete(exitCode: Int) {
            val lines = outputBuffer.toList()
            completionDeferred?.complete(lines)
        }
    }

    suspend fun executeScript(
        code: String,
        fileName: String = "script.js",
        currentDir: String = "/storage/emulated/0",
        timeoutMs: Long = 10000L
    ): List<String> = withContext(Dispatchers.IO) {
        outputBuffer.clear()
        val deferred = CompletableDeferred<List<String>>()
        completionDeferred = deferred

        val sanitizedCode = JSONObject.quote(code)
        val sanitizedDir = JSONObject.quote(currentDir)
        val sanitizedFile = JSONObject.quote(fileName)

        val bootstrapWrapper = """
            (function() {
                try {
                    const __dirname = $sanitizedDir;
                    const __filename = $sanitizedFile;

                    const fs = {
                        readFileSync: function(p, enc) { return AndroidBridge.readFile(p); },
                        writeFileSync: function(p, d) { return AndroidBridge.writeFile(p, String(d)); },
                        appendFileSync: function(p, d) { return AndroidBridge.appendFile(p, String(d)); },
                        existsSync: function(p) { return AndroidBridge.fileExists(p); },
                        mkdirSync: function(p) { return AndroidBridge.mkdir(p); },
                        readdirSync: function(p) {
                            try { return JSON.parse(AndroidBridge.readdir(p)); } catch(e) { return []; }
                        },
                        statSync: function(p) {
                            return {
                                isFile: function() { return AndroidBridge.fileExists(p); },
                                isDirectory: function() { return AndroidBridge.fileExists(p); },
                                size: (AndroidBridge.readFile(p) || '').length
                            };
                        }
                    };

                    const child_process = {
                        execSync: function(cmd) { return AndroidBridge.execSync(cmd); },
                        exec: function(cmd, cb) {
                            const res = AndroidBridge.execSync(cmd);
                            if (typeof cb === 'function') {
                                try { cb(null, res, ''); } catch(e) {}
                            }
                            return res;
                        },
                        spawn: function(cmd, args) {
                            const fullCmd = [cmd].concat(args || []).join(' ');
                            const out = AndroidBridge.execSync(fullCmd);
                            return {
                                stdout: { on: function(ev, cb) { if (ev === 'data' && cb) cb(out); } },
                                stderr: { on: function(ev, cb) {} },
                                on: function(ev, cb) { if (ev === 'close' && cb) cb(0); }
                            };
                        }
                    };

                    const os = {
                        platform: function() { return 'android'; },
                        arch: function() { return 'arm64'; },
                        type: function() { return 'Linux'; },
                        release: function() { return '6.6.0-android16'; },
                        homedir: function() { return '/storage/emulated/0'; },
                        tmpdir: function() { return '/data/local/tmp'; },
                        totalmem: function() { return 12 * 1024 * 1024 * 1024; },
                        freemem: function() { return 6 * 1024 * 1024 * 1024; },
                        cpus: function() {
                            return [
                                { model: 'ARM Cortex-X4', speed: 3300 },
                                { model: 'ARM Cortex-A720', speed: 2950 },
                                { model: 'ARM Cortex-A520', speed: 2270 }
                            ];
                        }
                    };

                    const path = {
                        join: function() {
                            const parts = Array.prototype.slice.call(arguments);
                            return parts.join('/').replace(/\/+/g, '/');
                        },
                        resolve: function() {
                            const parts = Array.prototype.slice.call(arguments);
                            return parts.join('/').replace(/\/+/g, '/');
                        },
                        dirname: function(p) {
                            return p.substring(0, p.lastIndexOf('/')) || '.';
                        },
                        basename: function(p) {
                            return p.substring(p.lastIndexOf('/') + 1);
                        },
                        extname: function(p) {
                            const b = p.substring(p.lastIndexOf('/') + 1);
                            const idx = b.lastIndexOf('.');
                            return idx >= 0 ? b.substring(idx) : '';
                        }
                    };

                    const process = {
                        platform: 'android',
                        version: 'v22.12.0',
                        versions: { node: '22.12.0', v8: '12.8.374.38', uv: '1.48.0' },
                        arch: 'arm64',
                        pid: 24510,
                        cwd: function() { return __dirname; },
                        env: {
                            NODE_ENV: 'production',
                            HOME: '/storage/emulated/0',
                            PATH: '/data/data/com.termux/files/usr/bin:/system/bin:/system/xbin:/data/local/tmp',
                            PREFIX: '/data/data/com.termux/files/usr',
                            TMPDIR: '/data/local/tmp',
                            UPLOAD_DIR: __dirname + '/uploads'
                        },
                        exit: function(code) {
                            AndroidBridge.onScriptComplete(code || 0);
                        },
                        on: function(ev, cb) {},
                        stdout: {
                            write: function(str) { AndroidBridge.consoleLog(String(str)); }
                        },
                        stderr: {
                            write: function(str) { AndroidBridge.consoleError(String(str)); }
                        }
                    };

                    const console = {
                        log: function() {
                            const args = Array.prototype.slice.call(arguments);
                            AndroidBridge.consoleLog(args.map(a => (typeof a === 'object' && a !== null) ? JSON.stringify(a, null, 2) : String(a)).join(' '));
                        },
                        info: function() {
                            const args = Array.prototype.slice.call(arguments);
                            AndroidBridge.consoleLog(args.map(a => (typeof a === 'object' && a !== null) ? JSON.stringify(a, null, 2) : String(a)).join(' '));
                        },
                        warn: function() {
                            const args = Array.prototype.slice.call(arguments);
                            AndroidBridge.consoleWarn(args.map(a => (typeof a === 'object' && a !== null) ? JSON.stringify(a, null, 2) : String(a)).join(' '));
                        },
                        error: function() {
                            const args = Array.prototype.slice.call(arguments);
                            AndroidBridge.consoleError(args.map(a => (typeof a === 'object' && a !== null) ? JSON.stringify(a, null, 2) : String(a)).join(' '));
                        },
                        table: function(obj) {
                            AndroidBridge.consoleLog(JSON.stringify(obj, null, 2));
                        },
                        dir: function(obj) {
                            AndroidBridge.consoleLog(JSON.stringify(obj, null, 2));
                        }
                    };

                    const require = function(mod) {
                        const m = String(mod).toLowerCase();
                        if (m === 'fs' || m === 'node:fs' || m === 'fs/promises') return fs;
                        if (m === 'child_process' || m === 'node:child_process') return child_process;
                        if (m === 'os' || m === 'node:os') return os;
                        if (m === 'path' || m === 'node:path') return path;
                        if (m === 'util' || m === 'node:util') return { inspect: function(o) { return JSON.stringify(o, null, 2); } };
                        if (m === 'axios' || m === 'node-fetch' || m === 'got') {
                            return {
                                get: function(u) { return Promise.resolve({ data: AndroidBridge.httpGet(u), status: 200 }); },
                                post: function(u, b) { return Promise.resolve({ data: AndroidBridge.httpPost(u, JSON.stringify(b)), status: 200 }); }
                            };
                        }
                        if (m === 'chalk' || m === 'colors') {
                            const wrap = function(s) { return s; };
                            return {
                                cyan: Object.assign(wrap, { bold: wrap }),
                                green: Object.assign(wrap, { bold: wrap }),
                                red: Object.assign(wrap, { bold: wrap }),
                                yellow: Object.assign(wrap, { bold: wrap }),
                                bold: wrap
                            };
                        }
                        return {};
                    };

                                        const UPLOAD_DIR = process.env.UPLOAD_DIR;
                    const global = window;

                                        const userCode = $sanitizedCode;
                    const evalResult = eval(userCode);

                    if (evalResult !== undefined && evalResult !== null && typeof evalResult !== 'function') {
                        console.log(evalResult);
                    }

                                        setTimeout(function() {
                        AndroidBridge.onScriptComplete(0);
                    }, 150);

                } catch (err) {
                    AndroidBridge.consoleError(err.name + ': ' + err.message + '\n' + (err.stack || ''));
                    AndroidBridge.onScriptComplete(1);
                }
            })();
        """.trimIndent()

        mainHandler.post {
            webView?.evaluateJavascript(bootstrapWrapper, null)
        }

        val result = withTimeoutOrNull(timeoutMs) {
            deferred.await()
        } ?: outputBuffer.toList()

        return@withContext if (result.isNotEmpty()) result else listOf("[✓] Script executed successfully with return code 0.")
    }
}
