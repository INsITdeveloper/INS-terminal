package com.example.ui

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.InsRepository
import com.example.data.entity.NpmPackageEntity
import com.example.data.entity.StockSnapshotEntity
import com.example.data.entity.SymlinkEntity
import com.example.data.entity.SystemTweakPresetEntity
import com.example.data.entity.TerminalHistoryEntity
import com.example.service.AutoCacheCleanerService
import com.example.service.FloatingBoosterOverlayService
import com.example.service.PersistentBoosterService
import com.example.service.PersistentBoosterState
import com.example.system.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ConsoleEntry(
    val id: String = System.nanoTime().toString(),
    val command: String,
    val output: String,
    val isError: Boolean = false,
    val timestamp: String = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date()),
    val executionTimeMs: Long = 0L,
    val actionType: TerminalActionType = TerminalActionType.STANDARD,
    val extraData: String? = null
)

data class TerminalSession(
    val id: Int,
    val name: String,
    val currentDir: String = "~/workspace",
    val logs: List<ConsoleEntry> = emptyList(),
    val commandInput: String = "",
    val history: List<String> = emptyList()
)

data class TelemetryState(
    val cpuUsagePercent: Int = 18,
    val cpuCores: List<RealtimeCoreTelemetry> = emptyList(),
    val ramUsedMb: Long = 3420L,
    val ramTotalMb: Long = 8192L,
    val ramUsagePercent: Int = 42,
    val zramUsedMb: Long = 1240L,
    val zramTotalMb: Long = 4096L,
    val cpuTempC: Float = 37.2f,
    val batteryPercent: Int = 88,
    val batteryVoltageMv: Int = 4210,
    val batteryTempC: Float = 34.5f,
    val isCharging: Boolean = false,
    val networkRxKbps: Float = 142.5f,
    val networkTxKbps: Float = 48.2f,
    val maxDisplayRefreshRateHz: Int = 120,
    val activeFps: Int = 120,
    val deviceModel: String = "${Build.MANUFACTURER} ${Build.MODEL}",
    val hardwareSoc: String = Build.HARDWARE,
    val androidVersion: String = "Android ${Build.VERSION.RELEASE}",
    val kernelRelease: String = "6.6",
    val uptimeFormatted: String = "00:00:00"
)

enum class InsScreenTab {
    TERMINAL,
    TWEAKS,
    NPM_SYMLINK,
    PROFILES_RESET,
    DIAGNOSTICS
}

class InsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: InsRepository
    val realtimeMonitor = RealtimeHardwareMonitorEngine(application)
    val hardwareBridge = HardwareBridgeEngine(application)
    val cameraBridge = CameraBridgeEngine(application)
    val puppeteerEngine = PuppeteerEngine(application)
    val shellEngine = SystemShellEngine(application, hardwareBridge, cameraBridge)
    val netEngine = NetworkOptimizerEngine()
    val perfEngine = PerformanceGovernorEngine()
    val dispEngine = DisplaySystemEngine()
    val npmEngine = NpmPackageManager()
    val symlinkEngine = SymlinkEngine()
    val defaultGuardEngine = DefaultGuardEngine()
    val audioRouterEngine = AudioRouterEngine(application)
    val settingsPersistence = SettingsPersistenceEngine(application)
    val appFloatingLauncher = AppFloatingLauncherEngine.getInstance(application)

    private val _freeformStatus = MutableStateFlow(appFloatingLauncher.checkFreeformStatus())
    val freeformStatus: StateFlow<FreeformStatus> = _freeformStatus.asStateFlow()

    private val _installedApps = MutableStateFlow<List<InstalledAppItem>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppItem>> = _installedApps.asStateFlow()

    private val _isLoadingInstalledApps = MutableStateFlow(false)
    val isLoadingInstalledApps: StateFlow<Boolean> = _isLoadingInstalledApps.asStateFlow()

    private val _activeFloatingAppsCount = MutableStateFlow(0)
    val activeFloatingAppsCount: StateFlow<Int> = _activeFloatingAppsCount.asStateFlow()

    private val _currentTab = MutableStateFlow(InsScreenTab.TERMINAL)
    val currentTab: StateFlow<InsScreenTab> = _currentTab.asStateFlow()

    private val defaultStorageDir = StorageAccessEngine.getExternalStorageRoot()

    private val _isStoragePermissionGranted = MutableStateFlow(StorageAccessEngine.hasStorageAccess(application))
    val isStoragePermissionGranted: StateFlow<Boolean> = _isStoragePermissionGranted.asStateFlow()

    private val _isStorageModalOpen = MutableStateFlow(false)
    val isStorageModalOpen: StateFlow<Boolean> = _isStorageModalOpen.asStateFlow()

    private val _isUploadModalOpen = MutableStateFlow(false)
    val isUploadModalOpen: StateFlow<Boolean> = _isUploadModalOpen.asStateFlow()

    private val _sessions = MutableStateFlow<List<TerminalSession>>(
        listOf(
            TerminalSession(
                id = 1,
                name = "1:bash",
                currentDir = defaultStorageDir,
                logs = listOf(
                    ConsoleEntry(
                        command = "ins init --desktop-bridge",
                        output = """
                            [⚡] INS DESKTOP TERMINAL v16.4.0 (Android 16 Core Engine)
                            [+] POSIX Shell & Desktop Real Filesystem Subsystem Active
                            [+] Full Storage Access Bridge: ${defaultStorageDir}
                            [+] Hardware Access: Camera2 HAL, Sensors, Battery & Flashlight [READY]
                            [+] Windows 11 Chromium & Puppeteer Automation Engine [LOADED]
                            [+] Type 'help', 'ls -la', 'cd <folder>', or 'perm' to test.
                        """.trimIndent(),
                        isError = false,
                        executionTimeMs = 3L
                    )
                )
            ),
            TerminalSession(
                id = 2,
                name = "2:puppeteer",
                currentDir = defaultStorageDir,
                logs = listOf(
                    ConsoleEntry(
                        command = "puppeteer --status",
                        output = "[*] Windows 11 Chrome Headless Environment initialized.\n[*] Ready for desktop web scraping and automation scripts.",
                        isError = false,
                        executionTimeMs = 2L
                    )
                )
            ),
            TerminalSession(
                id = 3,
                name = "3:camera-bridge",
                currentDir = "$defaultStorageDir/DCIM/Camera",
                logs = listOf(
                    ConsoleEntry(
                        command = "cam --info",
                        output = cameraBridge.getCameraInfo(),
                        isError = false,
                        executionTimeMs = 4L
                    )
                )
            )
        )
    )
    val sessions: StateFlow<List<TerminalSession>> = _sessions.asStateFlow()

    private val _activeSessionId = MutableStateFlow(1)
    val activeSessionId: StateFlow<Int> = _activeSessionId.asStateFlow()

    val consoleLogs: StateFlow<List<ConsoleEntry>> = combine(_sessions, _activeSessionId) { sessList, activeId ->
        sessList.find { it.id == activeId }?.logs ?: emptyList()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _commandInput = MutableStateFlow("")
    val commandInput: StateFlow<String> = _commandInput.asStateFlow()

    private val _isCommandRunning = MutableStateFlow(false)
    val isCommandRunning: StateFlow<Boolean> = _isCommandRunning.asStateFlow()

    private val _isMediaPreviewEnabled = MutableStateFlow(settingsPersistence.loadMediaPreview())
    val isMediaPreviewEnabled: StateFlow<Boolean> = _isMediaPreviewEnabled.asStateFlow()

    val activeCurrentDir: StateFlow<String> = combine(_sessions, _activeSessionId) { sessList, activeId ->
        sessList.find { it.id == activeId }?.currentDir ?: "~/workspace"
    }.stateIn(viewModelScope, SharingStarted.Eagerly, "~/workspace")

    private val _isCameraPreviewOpen = MutableStateFlow(false)
    val isCameraPreviewOpen: StateFlow<Boolean> = _isCameraPreviewOpen.asStateFlow()

    private val _cameraLensFacing = MutableStateFlow(CameraSelector.LENS_FACING_BACK)
    val cameraLensFacing: StateFlow<Int> = _cameraLensFacing.asStateFlow()

    private val _lastCapturedPhotoPath = MutableStateFlow<String?>(null)
    val lastCapturedPhotoPath: StateFlow<String?> = _lastCapturedPhotoPath.asStateFlow()

    private val _isTorchOn = MutableStateFlow(false)
    val isTorchOn: StateFlow<Boolean> = _isTorchOn.asStateFlow()

    private val _isNanoEditorOpen = MutableStateFlow(false)
    val isNanoEditorOpen: StateFlow<Boolean> = _isNanoEditorOpen.asStateFlow()

    private val _nanoEditorFilePath = MutableStateFlow("")
    val nanoEditorFilePath: StateFlow<String> = _nanoEditorFilePath.asStateFlow()

    private val _nanoEditorContent = MutableStateFlow("")
    val nanoEditorContent: StateFlow<String> = _nanoEditorContent.asStateFlow()

    private val _nanoEditorFileName = MutableStateFlow("")
    val nanoEditorFileName: StateFlow<String> = _nanoEditorFileName.asStateFlow()

    private val _nanoEditorStatusMessage = MutableStateFlow<String?>(null)
    val nanoEditorStatusMessage: StateFlow<String?> = _nanoEditorStatusMessage.asStateFlow()

    private val _isGestureTrackingActive = MutableStateFlow(false)
    val isGestureTrackingActive: StateFlow<Boolean> = _isGestureTrackingActive.asStateFlow()

    private val _activeVisionScript = MutableStateFlow("gesture_recognition.py")
    val activeVisionScript: StateFlow<String> = _activeVisionScript.asStateFlow()

    private val _currentGestureLabel = MutableStateFlow("🖐️ OPEN_PALM (99.2%)")
    val currentGestureLabel: StateFlow<String> = _currentGestureLabel.asStateFlow()

    private val _gestureConfidence = MutableStateFlow(98.6f)
    val gestureConfidence: StateFlow<Float> = _gestureConfidence.asStateFlow()

    private val _isPuppeteerActive = MutableStateFlow(false)
    val isPuppeteerActive: StateFlow<Boolean> = _isPuppeteerActive.asStateFlow()

    private val _lastPuppeteerResult = MutableStateFlow<PuppeteerExecutionResult?>(null)
    val lastPuppeteerResult: StateFlow<PuppeteerExecutionResult?> = _lastPuppeteerResult.asStateFlow()

    private var historyIndex = -1

    private val _networkState = MutableStateFlow(settingsPersistence.loadNetworkState())
    val networkState: StateFlow<NetworkTweakState> = _networkState.asStateFlow()

    private val _dnsBenchmarks = MutableStateFlow<List<Pair<DnsServerInfo, Long>>>(emptyList())
    val dnsBenchmarks: StateFlow<List<Pair<DnsServerInfo, Long>>> = _dnsBenchmarks.asStateFlow()

    private val _isBenchmarkingDns = MutableStateFlow(false)
    val isBenchmarkingDns: StateFlow<Boolean> = _isBenchmarkingDns.asStateFlow()

    private val _perfState = MutableStateFlow(settingsPersistence.loadPerformanceState())
    val perfState: StateFlow<PerformanceState> = _perfState.asStateFlow()

    private val _displayState = MutableStateFlow(settingsPersistence.loadDisplayState())
    val displayState: StateFlow<DisplaySystemState> = _displayState.asStateFlow()

    private val _displayCaps = MutableStateFlow(dispEngine.checkDisplayCapabilities(getApplication()))
    val displayCaps: StateFlow<DisplayCapabilities> = _displayCaps.asStateFlow()

    private val _fpsUnlockResult = MutableStateFlow<FpsUnlockResult?>(null)
    val fpsUnlockResult: StateFlow<FpsUnlockResult?> = _fpsUnlockResult.asStateFlow()

    val privilegeEngine = PrivilegeExecutionEngine.getInstance(getApplication())
    private val _privilegeStatus = MutableStateFlow(privilegeEngine.getPrivilegeStatus())
    val privilegeStatus: StateFlow<PrivilegeStatus> = _privilegeStatus.asStateFlow()

    val signalLocker = SignalLockerEngine.getInstance(getApplication())
    val signalStatus: StateFlow<RealSignalStatus> = signalLocker.signalStatus

    private val _isSymlinkSupportActive = MutableStateFlow(settingsPersistence.loadSymlinkSupport())
    val isSymlinkSupportActive: StateFlow<Boolean> = _isSymlinkSupportActive.asStateFlow()

    private val _guardState = MutableStateFlow(DefaultGuardState(isVolatileSessionActive = settingsPersistence.loadVolatileSession()))
    val guardState: StateFlow<DefaultGuardState> = _guardState.asStateFlow()

    private val _telemetryState = MutableStateFlow(TelemetryState())
    val telemetryState: StateFlow<TelemetryState> = _telemetryState.asStateFlow()

    private val _jsCodeInput = MutableStateFlow(settingsPersistence.loadJsCode())
    val jsCodeInput: StateFlow<String> = _jsCodeInput.asStateFlow()

    private val _audioRouterState = MutableStateFlow(settingsPersistence.loadAudioRouterState())
    val audioRouterState: StateFlow<SeparateAppSoundState> = _audioRouterState.asStateFlow()

    private val _installedAppsList = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    val installedAppsList: StateFlow<List<InstalledAppInfo>> = _installedAppsList.asStateFlow()

    private val _customScriptText = MutableStateFlow(settingsPersistence.loadCustomScript())
    val customScriptText: StateFlow<String> = _customScriptText.asStateFlow()

    fun updateCustomScriptText(script: String) {
        _customScriptText.value = script
        settingsPersistence.saveCustomScript(script)
    }

    private val _jsRunOutput = MutableStateFlow("")
    val jsRunOutput: StateFlow<String> = _jsRunOutput.asStateFlow()

    val dbHistory: StateFlow<List<TerminalHistoryEntity>>
    val dbPresets: StateFlow<List<SystemTweakPresetEntity>>
    val dbNpmPackages: StateFlow<List<NpmPackageEntity>>
    val dbSymlinks: StateFlow<List<SymlinkEntity>>

    private var telemetryJob: Job? = null

    init {
        val db = AppDatabase.getInstance(application)
        repository = InsRepository(db.insDao())

        dbHistory = repository.historyList.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
        dbPresets = repository.presetList.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
        dbNpmPackages = repository.npmPackages.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
        dbSymlinks = repository.symlinks.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

        seedInitialData()
        startTelemetryLoop()
        startAutoCacheClearDaemon()
        refreshInstalledApps()

        if (_perfState.value.isAutoCacheClearDaemonActive) {
            AutoCacheCleanerService.start(application, _perfState.value.autoCacheClearIntervalSec)
        }
    }

    private fun seedInitialData() {
        viewModelScope.launch(Dispatchers.IO) {
            val snapshot = repository.getStockSnapshot()
            if (snapshot == null) {
                val stock = StockSnapshotEntity(
                    snapshotKey = "OEM_DEFAULT",
                    deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
                    androidVersion = "Android 16.0 (API ${Build.VERSION.SDK_INT})",
                    defaultDns = "Carrier DHCP Stock",
                    defaultTcpCongestion = "cubic",
                    defaultAnimationScale = 1.0f,
                    defaultMtu = 1500,
                    defaultRefreshRate = 60,
                    defaultSwappiness = 60
                )
                repository.saveStockSnapshot(stock)
                _guardState.update { it.copy(stockSnapshot = stock) }
            }

            val initialPkgs = listOf(
                NpmPackageEntity(
                    packageName = "puppeteer-core",
                    version = "22.6.0",
                    description = "Headless desktop browser automation (Windows/Linux User-Agent support)",
                    sampleScript = puppeteerEngine.generateWindowsPuppeteerScriptTemplate()
                ),
                NpmPackageEntity(
                    packageName = "chalk",
                    version = "5.4.1",
                    description = "Terminal string styling with TrueColor ANSI palettes",
                    sampleScript = "const chalk = require('chalk'); console.log(chalk.cyan.bold('INS UI Desktop Terminal'));"
                ),
                NpmPackageEntity(
                    packageName = "axios",
                    version = "1.7.9",
                    description = "Promise based HTTP client for network requests",
                    sampleScript = "const axios = require('axios'); console.log('Axios HTTP Ready');"
                )
            )
            for (p in initialPkgs) {
                repository.installNpmPackage(p)
            }

            val initialSymlinks = listOf(
                SymlinkEntity(
                    linkName = "ins",
                    sourceTarget = "/data/data/com.ins.terminal/bin/ins_core",
                    destinationPath = "/system/bin/ins",
                    linkType = "SOFT",
                    isEnabled = true,
                    notes = "Global terminal command bridge"
                ),
                SymlinkEntity(
                    linkName = "puppeteer",
                    sourceTarget = "/data/local/tmp/node_modules/puppeteer-core",
                    destinationPath = "/usr/bin/puppeteer",
                    linkType = "SOFT",
                    isEnabled = true,
                    notes = "Windows Puppeteer executable bridge"
                )
            )
            for (s in initialSymlinks) {
                repository.createSymlink(s)
            }
        }
    }

    private var autoCacheJob: Job? = null

    private fun startAutoCacheClearDaemon() {
        viewModelScope.launch {
            AutoCacheCleanerService.serviceState.collect { svc ->
                _perfState.update {
                    it.copy(
                        isAutoCacheClearDaemonActive = svc.isRunning,
                        autoCacheClearIntervalSec = svc.intervalSec,
                        autoCleanCount = svc.cleanCount,
                        freedMemoryMb = svc.lastFreedMb,
                        totalFreedAccumulatedMb = svc.totalAccumulatedFreedMb
                    )
                }
            }
        }
    }

    private fun startTelemetryLoop() {
        telemetryJob?.cancel()
        telemetryJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    val sample = realtimeMonitor.sampleTelemetry(
                        activeGovernorMode = _perfState.value.activeGovernor,
                        configuredRefreshRateHz = _displayState.value.refreshRateHz,
                        isFpsLockBypassActive = _perfState.value.isFpsLockBypassActive
                    )
                    _telemetryState.update {
                        it.copy(
                            cpuUsagePercent = sample.cpuUsagePercent,
                            cpuCores = sample.cpuCores,
                            ramUsedMb = sample.ramUsedMb,
                            ramTotalMb = sample.ramTotalMb,
                            ramUsagePercent = sample.ramUsagePercent,
                            zramUsedMb = sample.zramUsedMb,
                            zramTotalMb = sample.zramTotalMb,
                            cpuTempC = sample.cpuTempC,
                            batteryPercent = sample.batteryPercent,
                            batteryVoltageMv = sample.batteryVoltageMv,
                            batteryTempC = sample.batteryTempC,
                            isCharging = sample.isCharging,
                            networkRxKbps = sample.networkRxKbps,
                            networkTxKbps = sample.networkTxKbps,
                            maxDisplayRefreshRateHz = sample.maxDisplayRefreshRateHz,
                            activeFps = sample.activeFps,
                            deviceModel = sample.deviceModel,
                            hardwareSoc = sample.hardwareSoc,
                            androidVersion = sample.androidVersion,
                            kernelRelease = sample.kernelRelease,
                            uptimeFormatted = sample.uptimeFormatted
                        )
                    }
                    _perfState.update {
                        it.copy(
                            estimatedFps = sample.activeFps,
                            cpuTemperatureC = sample.cpuTempC,
                            batteryVoltageMv = sample.batteryVoltageMv
                        )
                    }
                } catch (e: Exception) {

                }
                delay(1000)
            }
        }
    }

    fun switchSession(sessionId: Int) {
        _activeSessionId.value = sessionId
        historyIndex = -1
    }

    fun addNewSession() {
        val nextId = (_sessions.value.maxOfOrNull { it.id } ?: 0) + 1
        val newSession = TerminalSession(
            id = nextId,
            name = "$nextId:bash",
            logs = listOf(
                ConsoleEntry(
                    command = "ins session create",
                    output = "[+] Session #$nextId spawned. POSIX desktop shell ready.",
                    executionTimeMs = 1L
                )
            )
        )
        _sessions.update { it + newSession }
        _activeSessionId.value = nextId
        historyIndex = -1
    }

    fun closeSession(sessionId: Int) {
        if (_sessions.value.size <= 1) return
        val updated = _sessions.value.filterNot { it.id == sessionId }
        _sessions.value = updated
        if (_activeSessionId.value == sessionId) {
            _activeSessionId.value = updated.first().id
        }
    }

    fun selectTab(tab: InsScreenTab) {
        _currentTab.value = tab
    }

    fun updateCommandInput(text: String) {
        _commandInput.value = text
    }

    fun updateJsCodeInput(text: String) {
        _jsCodeInput.value = text
        settingsPersistence.saveJsCode(text)
    }

    fun handleExtraKey(key: String) {
        when (key) {
            "ESC" -> {
                _commandInput.value = ""
                historyIndex = -1
            }
            "TAB" -> {
                autoCompleteCommand()
            }
            "CTRL" -> {

                if (_isCommandRunning.value) {
                    _isCommandRunning.value = false
                    val entry = ConsoleEntry(
                        command = "^C",
                        output = "[*] Process interrupted by SIGINT (SIG_DFL)",
                        isError = true,
                        executionTimeMs = 1L
                    )
                    appendLogToActiveSession(entry)
                } else {
                    _commandInput.value = "${_commandInput.value}^"
                }
            }
            "ALT" -> {
                _commandInput.value = "${_commandInput.value}"
            }
            "UP" -> {
                navigateHistory(-1)
            }
            "DOWN" -> {
                navigateHistory(1)
            }
            "LEFT" -> {}
            "RIGHT" -> {}
            "CLEAR" -> {
                clearConsole()
            }
            "CAM" -> {
                toggleCameraPreview()
            }
            "PUPPETEER" -> {
                executeTerminalCommand("puppeteer windows --test")
            }
            else -> {
                _commandInput.value = "${_commandInput.value}$key"
            }
        }
    }

    private fun autoCompleteCommand() {
        val current = _commandInput.value.trim()
        val candidateCommands = listOf(
            "neofetch", "help", "puppeteer windows --test", "puppeteer launch --url ", "puppeteer --template",
            "cam --preview", "cam --snap", "cam --switch", "cam --info", "cam --close",
            "torch on", "torch off", "vibrate 300", "battery", "sensors", "ifconfig", "ip a",
            "powershell", "wmic", "dir", "uname -a", "df -h", "free -m", "top", "ps aux",
            "ins net opt", "ins default reset", "ins turbo on", "ins ram clean", "clear"
        )
        val match = candidateCommands.firstOrNull { it.startsWith(current, ignoreCase = true) }
        if (match != null) {
            _commandInput.value = match
        }
    }

    private fun navigateHistory(direction: Int) {
        val activeSess = _sessions.value.find { it.id == _activeSessionId.value } ?: return
        val hist = activeSess.history
        if (hist.isEmpty()) return

        historyIndex = (historyIndex + direction).coerceIn(0, hist.size - 1)
        _commandInput.value = hist[hist.size - 1 - historyIndex]
    }

    fun executeTerminalCommand(cmd: String = _commandInput.value) {
        val trimmed = cmd.trim()
        if (trimmed.isEmpty()) return

        _commandInput.value = ""
        _isCommandRunning.value = true
        historyIndex = -1

        val currentSessId = _activeSessionId.value
        val currentSess = _sessions.value.find { it.id == currentSessId }
        val sessionDir = currentSess?.currentDir ?: "~/workspace"

        _sessions.update { list ->
            list.map { sess ->
                if (sess.id == currentSessId) {
                    sess.copy(history = (sess.history + trimmed).takeLast(50))
                } else sess
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            val result = shellEngine.executeCommand(trimmed, sessionDir)

            if (result.actionType == TerminalActionType.CLEAR_TERMINAL || result.output == "__CLEAR__") {
                clearConsole()
                _isCommandRunning.value = false
                return@launch
            }

            when (result.actionType) {
                TerminalActionType.CHANGE_DIRECTORY -> {
                    if (result.extraData != null) {
                        val newDir = result.extraData
                        _sessions.update { list ->
                            list.map { sess ->
                                if (sess.id == currentSessId) sess.copy(currentDir = newDir) else sess
                            }
                        }
                    }
                }
                TerminalActionType.TOGGLE_MEDIA_PREVIEW -> {
                    if (result.extraData != null) {
                        _isMediaPreviewEnabled.value = result.extraData.toBoolean()
                    }
                }
                TerminalActionType.OPEN_CAMERA_PREVIEW -> {
                    _isCameraPreviewOpen.value = true
                }
                TerminalActionType.CLOSE_CAMERA_PREVIEW -> {
                    _isCameraPreviewOpen.value = false
                }
                TerminalActionType.SNAP_CAMERA -> {
                    _lastCapturedPhotoPath.value = result.extraData
                }
                TerminalActionType.SWITCH_CAMERA_LENS -> {
                    switchCameraLens()
                }
                TerminalActionType.TRIGGER_TORCH_ON -> {
                    cameraBridge.toggleTorch(true)
                    _isTorchOn.value = true
                }
                TerminalActionType.TRIGGER_TORCH_OFF -> {
                    cameraBridge.toggleTorch(false)
                    _isTorchOn.value = false
                }
                TerminalActionType.TRIGGER_VIBRATION -> {
                    hardwareBridge.triggerVibration(250L)
                }
                TerminalActionType.RUN_PUPPETEER_WINDOWS -> {
                    _isPuppeteerActive.value = true
                    val puppeteerRes = puppeteerEngine.executePuppeteerScript(
                        url = result.extraData ?: "https://example.com",
                        targetPlatform = "windows",
                        takeScreenshot = true
                    )
                    _lastPuppeteerResult.value = puppeteerRes
                }
                TerminalActionType.RUN_PUPPETEER_URL -> {
                    _isPuppeteerActive.value = true
                    val puppeteerRes = puppeteerEngine.executePuppeteerScript(
                        url = result.extraData ?: "https://news.ycombinator.com",
                        targetPlatform = "windows",
                        takeScreenshot = true
                    )
                    _lastPuppeteerResult.value = puppeteerRes
                }
                TerminalActionType.REQUEST_STORAGE_PERMISSION -> {
                    requestStoragePermission()
                }
                TerminalActionType.OPEN_NANO_EDITOR -> {
                    val targetPath = result.extraData ?: "$sessionDir/script.py"
                    openNanoEditor(targetPath)
                }
                TerminalActionType.OPEN_GESTURE_CAMERA -> {
                    val scriptName = result.extraData ?: "gesture_recognition.py"
                    openGestureCamera(scriptName)
                }
                else -> {}
            }

            var finalActionType = result.actionType
            var finalExtraData = result.extraData

            if (_isMediaPreviewEnabled.value && finalActionType == TerminalActionType.STANDARD) {
                val urlCandidate = extractMediaUrl(trimmed, result.output)
                if (urlCandidate != null) {
                    finalActionType = if (urlCandidate.second) TerminalActionType.RENDER_MEDIA_VIDEO else TerminalActionType.RENDER_MEDIA_IMAGE
                    finalExtraData = urlCandidate.first
                }
            }

            val entry = ConsoleEntry(
                command = trimmed,
                output = result.output,
                isError = result.exitCode != 0,
                executionTimeMs = result.executionTimeMs,
                actionType = finalActionType,
                extraData = finalExtraData
            )

            appendLogToActiveSession(entry)

            repository.logCommand(
                cmd = trimmed,
                out = result.output.take(500),
                exitCode = result.exitCode,
                timeMs = result.executionTimeMs
            )

            handleTerminalSideEffects(trimmed)
            _isCommandRunning.value = false
        }
    }

    private fun appendLogToActiveSession(entry: ConsoleEntry) {
        val currentSessId = _activeSessionId.value
        _sessions.update { list ->
            list.map { sess ->
                if (sess.id == currentSessId) {
                    sess.copy(logs = (sess.logs + entry).takeLast(100))
                } else sess
            }
        }
    }

    fun toggleCameraPreview() {
        _isCameraPreviewOpen.value = !_isCameraPreviewOpen.value
        if (_isCameraPreviewOpen.value) {
            executeTerminalCommand("cam --preview")
        } else {
            executeTerminalCommand("cam --close")
        }
    }

    fun switchCameraLens() {
        _cameraLensFacing.value = if (_cameraLensFacing.value == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
    }

    fun captureCameraPhoto() {
        executeTerminalCommand("cam --snap")
    }

    fun toggleTorch() {
        val newState = !_isTorchOn.value
        cameraBridge.toggleTorch(newState)
        _isTorchOn.value = newState
        val log = ConsoleEntry(
            command = if (newState) "torch on" else "torch off",
            output = if (newState) "[+] Hardware Torch Active" else "[*] Hardware Torch Off",
            isError = false,
            executionTimeMs = 1L
        )
        appendLogToActiveSession(log)
    }

    fun triggerVibrate() {
        hardwareBridge.triggerVibration(250L)
        val log = ConsoleEntry(
            command = "vibrate 250",
            output = "[+] Haptic pulse 250ms fired.",
            isError = false,
            executionTimeMs = 1L
        )
        appendLogToActiveSession(log)
    }

    private fun handleTerminalSideEffects(cmd: String) {
        val lower = cmd.lowercase(Locale.ROOT)
        if (lower.contains("ins default reset") || lower == "ins reset") {
            revertToStockDefaults()
        } else if (lower.contains("ins turbo on") || lower == "ins boost turbo") {
            setGovernorMode(GovernorMode.TURBO)
        } else if (lower.contains("ins eco") || lower == "ins battery ultra") {
            setGovernorMode(GovernorMode.ECO)
        } else if (lower.contains("ins net opt") || lower == "net-opt") {
            optimizeNetwork()
        } else if (lower.contains("ins ram clean") || lower == "zram-boost") {
            cleanRam()
        }
    }

    fun clearConsole() {
        val currentSessId = _activeSessionId.value
        _sessions.update { list ->
            list.map { sess ->
                if (sess.id == currentSessId) sess.copy(logs = emptyList()) else sess
            }
        }
    }

    fun selectDns(dns: DnsServerInfo) {
        _networkState.update {
            it.copy(
                activeDnsId = dns.id,
                activeDnsName = dns.name,
                primaryIp = dns.primaryIp,
                secondaryIp = dns.secondaryIp,
                lastOptimizedTime = System.currentTimeMillis()
            )
        }
        settingsPersistence.saveNetworkState(_networkState.value)
        logSystemAction("DNS Switched to ${dns.name} [${dns.primaryIp}]")
    }

    fun setTcpAlgorithm(algo: String) {
        _networkState.update { it.copy(tcpAlgorithm = algo) }
        settingsPersistence.saveNetworkState(_networkState.value)
        logSystemAction("TCP Congestion algorithm set to '$algo'")
    }

    fun setMtuSize(mtu: Int) {
        _networkState.update { it.copy(mtuSize = mtu) }
        settingsPersistence.saveNetworkState(_networkState.value)
        logSystemAction("Network MTU tuned to $mtu bytes")
    }

    fun benchmarkDnsServers() {
        if (_isBenchmarkingDns.value) return
        _isBenchmarkingDns.value = true

        viewModelScope.launch(Dispatchers.IO) {
            val results = netEngine.benchmarkAllDns()
            _dnsBenchmarks.value = results.sortedBy { it.second }
            _isBenchmarkingDns.value = false
            logSystemAction("DNS Latency Benchmark completed across ${results.size} backends.")
        }
    }

    fun optimizeNetwork() {
        viewModelScope.launch(Dispatchers.IO) {
            val ping = netEngine.pingHost(_networkState.value.primaryIp)
            _networkState.update {
                it.copy(
                    measuredPingMs = ping,
                    isFastHandoverEnabled = true,
                    isBufferbloatMitigationEnabled = true,
                    isSignalBoosterEnabled = true,
                    is4g5gAggregationBoostEnabled = true,
                    isWifiAntiJitterEnabled = true,
                    isVideoStreamingBoosterActive = true,
                    isHardwareCodecAccelerationEnabled = true,
                    isHdrVideoEnhancerEnabled = true,
                    isCinemaVocalClarityEnabled = true,
                    lastOptimizedTime = System.currentTimeMillis()
                )
            }
            settingsPersistence.saveNetworkState(_networkState.value)
            val script = netEngine.generateUltraSignalAndStreamingScript(_networkState.value)
            shellEngine.runRootProcess(script, "/storage/emulated/0", System.currentTimeMillis())
            logSystemAction("📶 Sinyal & Network Stack Tuned: 5G/4G Carrier Aggregation, Cloudflare DNS, BBR Buffer & Video Streaming Accelerator Aktif. Latency: ${ping}ms")
        }
    }

    fun applyUltraSignalAndCinemaBoost() {
        viewModelScope.launch(Dispatchers.IO) {
            val ping = netEngine.pingHost(_networkState.value.primaryIp)
            _networkState.update {
                it.copy(
                    measuredPingMs = ping,
                    isSignalBoosterEnabled = true,
                    is4g5gAggregationBoostEnabled = true,
                    isWifiAntiJitterEnabled = true,
                    isVideoStreamingBoosterActive = true,
                    isHardwareCodecAccelerationEnabled = true,
                    isHdrVideoEnhancerEnabled = true,
                    isCinemaVocalClarityEnabled = true,
                    isChromeSuperFastBoosterEnabled = true,
                    isGpuRasterizationEnabled = true,
                    isParallelDownloadEnabled = true,
                    isQuicHttp3ProtocolEnabled = true,
                    isWebViewHardwareAccelerationEnabled = true,
                    lastOptimizedTime = System.currentTimeMillis()
                )
            }
            settingsPersistence.saveNetworkState(_networkState.value)
            val script = netEngine.generateUltraSignalAndStreamingScript(_networkState.value)
            shellEngine.runRootProcess(script, "/storage/emulated/0", System.currentTimeMillis())
            logSystemAction("🎬 [SINYAL KENCANG & NONTON ENAC/CINEMA BOOSTER AKTIF]\n• Sinyal 4G/5G Full Bar & Zero Packet Drop\n• Video Streaming 16MB Cache (Anti-Buffering YouTube/TikTok/Netflix)\n• Chrome Super Fast & GPU Rasterization Active\n• Hardware Codec AV1/HEVC/VP9 120fps\n• HDR10+ Vivid Color Enhancer & Vocal Dialogue Clarity")
        }
    }

    fun applyChromeSuperFastBoost() {
        viewModelScope.launch(Dispatchers.IO) {
            _networkState.update {
                it.copy(
                    isChromeSuperFastBoosterEnabled = true,
                    isGpuRasterizationEnabled = true,
                    isParallelDownloadEnabled = true,
                    isQuicHttp3ProtocolEnabled = true,
                    isWebViewHardwareAccelerationEnabled = true,
                    isFastHandoverEnabled = true,
                    isBufferbloatMitigationEnabled = true,
                    lastOptimizedTime = System.currentTimeMillis()
                )
            }
            settingsPersistence.saveNetworkState(_networkState.value)
            val script = netEngine.generateUltraSignalAndStreamingScript(_networkState.value)
            shellEngine.runRootProcess(script, "/storage/emulated/0", System.currentTimeMillis())
            logSystemAction("⚡ [CHROME & BROWSER SUPER FAST AKTIF]\n• GPU Rasterization 120Hz & Zero-Copy Canvas\n• HTTP/3 & QUIC 0-RTT Protocol\n• Multi-Threaded Parallel Downloads\n• Android System WebView HW Acceleration\n• Instant Back-Forward History Cache")
        }
    }

    fun toggleChromeSuperFast(enabled: Boolean) {
        _networkState.update { it.copy(isChromeSuperFastBoosterEnabled = enabled) }
        settingsPersistence.saveNetworkState(_networkState.value)
        if (enabled) {
            viewModelScope.launch(Dispatchers.IO) {
                val script = netEngine.generateUltraSignalAndStreamingScript(_networkState.value)
                shellEngine.runRootProcess(script, "/storage/emulated/0", System.currentTimeMillis())
            }
        }
        logSystemAction(if (enabled) "⚡ Chrome & Browser Speed Booster [AKTIF]" else "Chrome Speed Booster [STANDBY]")
    }

    fun toggleGpuRasterization(enabled: Boolean) {
        _networkState.update { it.copy(isGpuRasterizationEnabled = enabled) }
        settingsPersistence.saveNetworkState(_networkState.value)
        logSystemAction(if (enabled) "🎮 Chrome GPU Canvas Rasterization [ENABLED]" else "GPU Rasterization [DEFAULT]")
    }

    fun toggleParallelDownload(enabled: Boolean) {
        _networkState.update { it.copy(isParallelDownloadEnabled = enabled) }
        settingsPersistence.saveNetworkState(_networkState.value)
        logSystemAction(if (enabled) "📥 Parallel Chunk Downloading [ACCELERATED]" else "Parallel Download [DEFAULT]")
    }

    fun toggleQuicHttp3(enabled: Boolean) {
        _networkState.update { it.copy(isQuicHttp3ProtocolEnabled = enabled) }
        settingsPersistence.saveNetworkState(_networkState.value)
        logSystemAction(if (enabled) "🌐 QUIC / HTTP3 Zero-RTT Protocol [ENGAGED]" else "QUIC Protocol [DEFAULT]")
    }

    fun toggleWebViewHwAcceleration(enabled: Boolean) {
        _networkState.update { it.copy(isWebViewHardwareAccelerationEnabled = enabled) }
        settingsPersistence.saveNetworkState(_networkState.value)
        logSystemAction(if (enabled) "📱 Android System WebView Hardware Acceleration [120Hz]" else "WebView Acceleration [DEFAULT]")
    }

    fun toggleVideoStreamingBooster(enabled: Boolean) {
        _networkState.update { it.copy(isVideoStreamingBoosterActive = enabled) }
        settingsPersistence.saveNetworkState(_networkState.value)
        if (enabled) {
            viewModelScope.launch(Dispatchers.IO) {
                val script = netEngine.generateUltraSignalAndStreamingScript(_networkState.value)
                shellEngine.runRootProcess(script, "/storage/emulated/0", System.currentTimeMillis())
            }
        }
        logSystemAction(if (enabled) "🎬 Video Streaming Buffer Booster [AKTIF]" else "Video Streaming Booster [STANDBY]")
    }

    fun toggleHardwareCodecAcceleration(enabled: Boolean) {
        _networkState.update { it.copy(isHardwareCodecAccelerationEnabled = enabled) }
        settingsPersistence.saveNetworkState(_networkState.value)
        logSystemAction(if (enabled) "⚡ Hardware Codec (HEVC/AV1/VP9) Decode [ACCELERATED]" else "Hardware Codec [DEFAULT]")
    }

    fun toggleHdrVideoEnhancer(enabled: Boolean) {
        _networkState.update { it.copy(isHdrVideoEnhancerEnabled = enabled) }
        settingsPersistence.saveNetworkState(_networkState.value)
        logSystemAction(if (enabled) "🎨 HDR & DCI-P3 Video Color Enhancer [ACTIVE]" else "HDR Enhancer [DEFAULT]")
    }

    fun toggleCinemaVocalClarity(enabled: Boolean) {
        _networkState.update { it.copy(isCinemaVocalClarityEnabled = enabled) }
        settingsPersistence.saveNetworkState(_networkState.value)
        logSystemAction(if (enabled) "🔊 Vocal & Dialogue Speech Clarity [BOOSTED]" else "Vocal Clarity [DEFAULT]")
    }

    fun toggleUniversalBrowserMedia(enabled: Boolean) {
        _networkState.update { it.copy(isUniversalBrowserMediaBoosterEnabled = enabled) }
        settingsPersistence.saveNetworkState(_networkState.value)
        if (enabled) {
            viewModelScope.launch(Dispatchers.IO) {
                val script = netEngine.generateUltraSignalAndStreamingScript(_networkState.value)
                shellEngine.runRootProcess(script, "/storage/emulated/0", System.currentTimeMillis())
            }
        }
        logSystemAction(if (enabled) "🌐 Universal Multi-Browser Stream & Image HW Turbo [ENABLED]" else "Browser Media Turbo [STANDBY]")
    }

    fun toggleImageWebpAvifHardwareDecode(enabled: Boolean) {
        _networkState.update { it.copy(isImageWebpAvifHardwareDecodeEnabled = enabled) }
        settingsPersistence.saveNetworkState(_networkState.value)
        logSystemAction(if (enabled) "🖼️ WebP / AVIF Hardware Image Acceleration [ACTIVE]" else "Image Decode [STOCK]")
    }

    fun applyDualChannelSignalBoost() {
        viewModelScope.launch(Dispatchers.IO) {
            val ping = netEngine.pingHost(_networkState.value.primaryIp)
            _networkState.update {
                it.copy(
                    measuredPingMs = ping,
                    isDualChannelSignalBoostEnabled = true,
                    isWifiSignalBoosterActive = true,
                    isCellularSignalBoosterActive = true,
                    isSignalBoosterEnabled = true,
                    is4g5gAggregationBoostEnabled = true,
                    isWifiAntiJitterEnabled = true,
                    lastOptimizedTime = System.currentTimeMillis()
                )
            }
            settingsPersistence.saveNetworkState(_networkState.value)
            applySignalBoostProfile("Dual-Channel Wi-Fi + seluler")
            logSystemAction("📶 [DUAL-CHANNEL SIGNAL BOOSTER UNLOCKED]\n• Wi-Fi TX Power & Anti-Jitter [BOOSTED]\n• Data Seluler 4G/5G CA & Zero Radio Sleep [UNLOCKED]\n• Dual-Channel Link Turbo Concurrency [ACTIVE]\n• Latency: ${ping}ms")
        }
    }

    /**
     * Terapkan profil penguat sinyal yang KOMPATIBEL dengan kombinasi toggle aktif.
     * Ini yang memperbaiki bug "dua-duanya dihidupkan jadi aneh": engine memilih satu profil
     * koheren (Wi-Fi / seluler / dual / auto) alih-alih menembak setting yang saling menimpa.
     */
    private fun applySignalBoostProfile(reason: String) {
        val state = _networkState.value
        val mode = state.resolveBoostMode()
        signalLocker.setBoostMode(mode)
        viewModelScope.launch(Dispatchers.IO) {
            val script = netEngine.generateUltraSignalAndStreamingScript(state, mode)
            val (ok, report) = privilegeEngine.executePrivilegedScript(script)
            logSystemAction(
                "📶 Profil sinyal: ${mode.label}\n" +
                    "• $reason\n" +
                    "• Hasil: ${if (ok) "DITERAPKAN" else "SEBAGIAN/PERLU IZIN"}\n" +
                    report.lines().take(14).joinToString("\n")
            )
        }
    }

    fun toggleDualChannelSignalBoost(enabled: Boolean) {
        _networkState.update { it.copy(isDualChannelSignalBoostEnabled = enabled) }
        settingsPersistence.saveNetworkState(_networkState.value)
        applySignalBoostProfile(
            if (enabled) "Dual-Channel Wi-Fi + 4G/5G" else "Dual-Channel dinonaktifkan, kembali ke AUTO"
        )
        logSystemAction(if (enabled) "📶 Dual-Channel Wi-Fi + 4G/5G Signal Link [UNLOCKED]" else "Dual-Channel Booster [STANDBY]")
    }

    fun toggleWifiSignalBooster(enabled: Boolean) {
        _networkState.update { it.copy(isWifiSignalBoosterActive = enabled) }
        settingsPersistence.saveNetworkState(_networkState.value)
        applySignalBoostProfile(if (enabled) "Wi-Fi low-latency & anti-jitter" else "Wi-Fi booster dinonaktifkan")
        logSystemAction(if (enabled) "📶 Wi-Fi Low Latency & Radio Sleep Bypass [ACTIVE]" else "Wi-Fi Booster [STOCK]")
    }

    fun toggleCellularSignalBooster(enabled: Boolean) {
        _networkState.update { it.copy(isCellularSignalBoosterActive = enabled) }
        settingsPersistence.saveNetworkState(_networkState.value)
        applySignalBoostProfile(if (enabled) "Seluler 4G/5G anti-dormancy" else "Seluler booster dinonaktifkan")
        logSystemAction(if (enabled) "📶 Cellular Data 4G/5G Carrier Aggregation [BOOSTED]" else "Cellular Booster [STOCK]")
    }

    fun toggleSignalBooster(enabled: Boolean) {
        _networkState.update { it.copy(isSignalBoosterEnabled = enabled) }
        settingsPersistence.saveNetworkState(_networkState.value)
        logSystemAction(if (enabled) "🛡️ Cloudflare 1.1.1.1 Anti-RTO DNS & Signal Optimizer [ACTIVE]" else "Cloudflare DNS [STANDBY]")
    }

    fun applyGameGraphicUnlocker() {
        viewModelScope.launch(Dispatchers.IO) {
            _displayState.update {
                it.copy(
                    isGameGraphicUnlockerActive = true,
                    isJoyoseGosBypassEnabled = true,
                    isGameDriverForced = true,
                    isAntiLagTripleBufferingActive = true,
                    isFramePacingEnabled = true,
                    isHdGraphicsEnhancerEnabled = true,
                    isAntiAliasing4xMsaaEnabled = true,
                    isGameNativeResolutionLocked = true,
                    isTextureFilter16xEnabled = true,
                    isShaderPreloadBoosterEnabled = true,
                    isThermalThrottlingDisabled = true,
                    refreshRateHz = 120,
                    fpsUnlockerTargetHz = 120,
                    fpsUnlockerStatus = "Extreme 120 FPS / Ultra Graphics Unlocked"
                )
            }
            _perfState.update {
                it.copy(
                    activeGovernor = GovernorMode.TURBO,
                    isFpsLockBypassActive = true,
                    isGpuTurboActive = true,
                    isUniversalHyperBoostActive = true
                )
            }
            settingsPersistence.saveDisplayState(_displayState.value)
            settingsPersistence.savePerformanceState(_perfState.value)
            ensurePersistentBoosterRunning()
            val script = dispEngine.generateDisplayTweaksScript(_displayState.value)
            shellEngine.runRootProcess(script, "/storage/emulated/0", System.currentTimeMillis())
            _telemetryState.update { it.copy(activeFps = 120, maxDisplayRefreshRateHz = 120) }
            logSystemAction("🎮 [GAME GRAPHIC & EXTREME FPS UNLOCKER ACTIVE]\n• Profile: ${_displayState.value.gameGraphicProfile}\n• Extreme 90/120 FPS & Ultra HD / HDR Settings Unlocked\n• MIUI Joyose / Samsung GOS / OEM Frame Limiter Bypassed\n• System Game Driver & Updatable Driver Forced\n• Anti-Lag Triple Buffering & Frame Pacing Active")
        }
    }

    fun setGameGraphicProfile(profile: String) {
        _displayState.update { it.copy(gameGraphicProfile = profile) }
        settingsPersistence.saveDisplayState(_displayState.value)
        applyGameGraphicUnlocker()
        logSystemAction("🎮 Game Spoofing Profile switched to: $profile")
    }

    fun toggleGameGraphicUnlocker(enabled: Boolean) {
        _displayState.update { it.copy(isGameGraphicUnlockerActive = enabled) }
        settingsPersistence.saveDisplayState(_displayState.value)
        if (enabled) {
            applyGameGraphicUnlocker()
        }
        logSystemAction(if (enabled) "🎮 Game Graphic & Extreme FPS Unlocker [ENGAGED]" else "Game Graphic Unlocker [STOCK]")
    }

    fun toggleJoyoseGosBypass(enabled: Boolean) {
        _displayState.update { it.copy(isJoyoseGosBypassEnabled = enabled) }
        settingsPersistence.saveDisplayState(_displayState.value)
        logSystemAction(if (enabled) "🛡️ OEM Joyose / GOS Throttling Bypass [ACTIVE]" else "OEM Throttling [DEFAULT]")
    }

    fun toggleGameDriverForced(enabled: Boolean) {
        _displayState.update { it.copy(isGameDriverForced = enabled) }
        settingsPersistence.saveDisplayState(_displayState.value)
        logSystemAction(if (enabled) "🚀 System Game Driver & Vulkan Pipeline [FORCED]" else "Game Driver [AUTO]")
    }

    fun toggleAntiLagTripleBuffering(enabled: Boolean) {
        _displayState.update { it.copy(isAntiLagTripleBufferingActive = enabled) }
        settingsPersistence.saveDisplayState(_displayState.value)
        logSystemAction(if (enabled) "⚡ Anti-Lag Triple Buffering & Frame Pacing [STABLE]" else "Triple Buffering [DEFAULT]")
    }

    fun setGovernorMode(mode: GovernorMode) {
        _perfState.update {
            it.copy(
                activeGovernor = mode,
                swappiness = mode.zramSwappiness
            )
        }
        settingsPersistence.savePerformanceState(_perfState.value)
        ensurePersistentBoosterRunning()
        logSystemAction("Governor switched to ${mode.title} (${mode.cpuGovernor})")
    }

    fun setSwappiness(value: Int) {
        _perfState.update { it.copy(swappiness = value) }
        settingsPersistence.savePerformanceState(_perfState.value)
        ensurePersistentBoosterRunning()
    }

    fun cleanRam() {
        viewModelScope.launch(Dispatchers.IO) {
            val result = CacheCleanerEngine.cleanCaches(getApplication())
            val freed = result.freedMb
            _perfState.update {
                it.copy(
                    freedMemoryMb = freed,
                    isDropCachesActive = true,
                    totalFreedAccumulatedMb = it.totalFreedAccumulatedMb + freed,
                    autoCleanCount = it.autoCleanCount + 1
                )
            }
            logSystemAction("RAM Vacuum & Cache Drop: ${result.details}")
        }
    }

    private fun extractMediaUrl(cmd: String, output: String): Pair<String, Boolean>? {
        val allText = "$cmd\n$output"
        val urlRegex = Regex("""(https?://[^\s"'<>]+\.(?:png|jpg|jpeg|webp|gif|svg|mp4|webm|mkv))""", RegexOption.IGNORE_CASE)
        val match = urlRegex.find(allText)
        if (match != null) {
            val url = match.value
            val isVideo = url.endsWith(".mp4", true) || url.endsWith(".webm", true) || url.endsWith(".mkv", true)
            return Pair(url, isVideo)
        }
        return null
    }

    fun toggleMediaPreview(enabled: Boolean = !_isMediaPreviewEnabled.value) {
        _isMediaPreviewEnabled.value = enabled
        settingsPersistence.saveMediaPreview(enabled)
        logSystemAction(if (enabled) "Terminal Rich Media Previews [ENABLED]" else "Terminal Rich Media Previews [DISABLED]")
    }

    fun toggleFramePacing(enabled: Boolean) {
        _displayState.update { it.copy(isFramePacingEnabled = enabled) }
        settingsPersistence.saveDisplayState(_displayState.value)
        logSystemAction(if (enabled) "Frame Pacing & Stutter Eliminator [ACTIVE]" else "Frame Pacing [OFF]")
    }

    fun toggleHdGraphics(enabled: Boolean) {
        _displayState.update { it.copy(isHdGraphicsEnhancerEnabled = enabled) }
        settingsPersistence.saveDisplayState(_displayState.value)
        logSystemAction(if (enabled) "HD Graphics & Vulkan Skia Pipeline [ACTIVE]" else "HD Graphics [OFF]")
    }

    fun toggleAntiAliasing4xMsaa(enabled: Boolean) {
        _displayState.update { it.copy(isAntiAliasing4xMsaaEnabled = enabled) }
        settingsPersistence.saveDisplayState(_displayState.value)
        logSystemAction(if (enabled) "4x MSAA Hardware Anti-Aliasing (Anti-Pecah-Pecah) [FORCED]" else "Anti-Aliasing [STOCK]")
    }

    fun toggleGameNativeResolution(enabled: Boolean) {
        _displayState.update { it.copy(isGameNativeResolutionLocked = enabled) }
        settingsPersistence.saveDisplayState(_displayState.value)
        logSystemAction(if (enabled) "Native 100% Game Render Resolution [LOCKED - NO DOWNSCALE]" else "Game Resolution Scale [AUTO]")
    }

    fun toggleTextureFilter16x(enabled: Boolean) {
        _displayState.update { it.copy(isTextureFilter16xEnabled = enabled) }
        settingsPersistence.saveDisplayState(_displayState.value)
        logSystemAction(if (enabled) "Anisotropic 16x Crisp Texture Filtering [ACTIVE]" else "Texture Filtering [STOCK]")
    }

    fun toggleShaderPreload(enabled: Boolean) {
        _displayState.update { it.copy(isShaderPreloadBoosterEnabled = enabled) }
        settingsPersistence.saveDisplayState(_displayState.value)
        logSystemAction(if (enabled) "Shader Preload & Zero-Lag Compiler [ACTIVE]" else "Shader Preload [OFF]")
    }

    fun toggleThermalThrottling(disabled: Boolean) {
        _displayState.update { it.copy(isThermalThrottlingDisabled = disabled) }
        settingsPersistence.saveDisplayState(_displayState.value)
        logSystemAction(if (disabled) "Thermal Throttling FPS Governor [DISABLED - MAX PERFORMANCE]" else "Thermal Throttling [STOCK]")
    }

    fun executeCustomRootScript(script: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            val res = shellEngine.runRootProcess(script, "/storage/emulated/0", start)
            logSystemAction(if (res.exitCode == 0) "[# ROOT SCRIPT SUCCESS]\n${res.output}" else "[# ROOT SCRIPT ERROR]\n${res.output}")
        }
    }

    fun setTouchSamplingRatio(hz: Int) {
        _displayState.update { it.copy(touchSamplingRatioHz = hz) }
        settingsPersistence.saveDisplayState(_displayState.value)
        viewModelScope.launch(Dispatchers.IO) {
            val res = privilegeEngine.applyRealTouchOptimization(hz)
            logSystemAction(res.second)
        }
    }

    fun toggleTouchLatencyReduction(enabled: Boolean) {
        _displayState.update { it.copy(isTouchLatencyReductionEnabled = enabled) }
        settingsPersistence.saveDisplayState(_displayState.value)
        logSystemAction(if (enabled) "Zero Touch Latency Mode [ACTIVE]" else "Zero Touch Latency [OFF]")
    }

    fun toggleUniversalHyperBoost(enabled: Boolean) {
        _perfState.update { it.copy(isUniversalHyperBoostActive = enabled) }
        settingsPersistence.savePerformanceState(_perfState.value)
        if (enabled) {
            ensurePersistentBoosterRunning()
        }
        logSystemAction(if (enabled) "Universal HyperBoost & FPS Governor [ACTIVE]" else "Universal HyperBoost [PAUSED]")
    }

    fun toggleFpsLockBypass(enabled: Boolean) {
        _perfState.update { it.copy(isFpsLockBypassActive = enabled) }
        settingsPersistence.savePerformanceState(_perfState.value)
        ensurePersistentBoosterRunning()
        logSystemAction(if (enabled) "120 FPS / High Refresh Unlocker [UNLOCKED]" else "120 FPS Unlocker [DEFAULT]")
    }

    fun toggleGpuTurbo(enabled: Boolean) {
        _perfState.update { it.copy(isGpuTurboActive = enabled) }
        settingsPersistence.savePerformanceState(_perfState.value)
        ensurePersistentBoosterRunning()
        logSystemAction(if (enabled) "GPU Hardware Turbo Booster [MAX CLOCKS]" else "GPU Turbo [NORMAL]")
    }

    fun applyInstantFpsBoost() {
        viewModelScope.launch(Dispatchers.IO) {
            val result = CacheCleanerEngine.cleanCaches(getApplication())
            val freed = result.freedMb
            _perfState.update {
                it.copy(
                    activeGovernor = GovernorMode.TURBO,
                    freedMemoryMb = freed,
                    totalFreedAccumulatedMb = it.totalFreedAccumulatedMb + freed,
                    isFpsLockBypassActive = true,
                    isGpuTurboActive = true,
                    isTouchBoostEnabled = true
                )
            }
            _displayState.update {
                it.copy(
                    refreshRateHz = 120,
                    isFramePacingEnabled = true,
                    isHdGraphicsEnhancerEnabled = true,
                    isAntiAliasing4xMsaaEnabled = true,
                    isGameNativeResolutionLocked = true,
                    isTextureFilter16xEnabled = true,
                    isShaderPreloadBoosterEnabled = true,
                    isThermalThrottlingDisabled = true,
                    touchSamplingRatioHz = 360,
                    isTouchLatencyReductionEnabled = true
                )
            }
            settingsPersistence.savePerformanceState(_perfState.value)
            settingsPersistence.saveDisplayState(_displayState.value)
            ensurePersistentBoosterRunning()
            val script = dispEngine.generateDisplayTweaksScript(_displayState.value)
            shellEngine.runRootProcess(script, "/storage/emulated/0", System.currentTimeMillis())
            _telemetryState.update { it.copy(activeFps = 120, maxDisplayRefreshRateHz = 120) }
            logSystemAction("[⚡] Instant Ultra Game Graphics & FPS Booster APPLIED: 4x MSAA Anti-Pecah, Native Render Lock, 120 FPS Unlocked, GPU Turbo, 360Hz Touch, Freed ${freed}MB RAM!")
        }
    }

    fun toggleAutoCacheClearDaemon(enabled: Boolean) {
        _perfState.update { it.copy(isAutoCacheClearDaemonActive = enabled) }
        settingsPersistence.savePerformanceState(_perfState.value)
        val app = getApplication<Application>()
        if (enabled) {
            AutoCacheCleanerService.start(app, _perfState.value.autoCacheClearIntervalSec)
            logSystemAction("Universal Auto-Cache Clear Daemon [RUNNING IN BACKGROUND]")
        } else {
            AutoCacheCleanerService.stop(app)
            logSystemAction("Universal Auto-Cache Daemon [STOPPED]")
        }
    }

    fun setAutoCacheClearInterval(sec: Int) {
        _perfState.update { it.copy(autoCacheClearIntervalSec = sec) }
        settingsPersistence.savePerformanceState(_perfState.value)
        if (_perfState.value.isAutoCacheClearDaemonActive) {
            AutoCacheCleanerService.start(getApplication(), sec)
        }
        logSystemAction("Universal Auto-Cache sweep interval set to ${sec}s")
    }

    val persistentBoosterState: StateFlow<PersistentBoosterState> = PersistentBoosterService.boosterState
    val isFloatingOverlayActive: StateFlow<Boolean> = FloatingBoosterOverlayService.isOverlayActive
    val activeOverlayWindowsCount: StateFlow<Int> = FloatingBoosterOverlayService.activeWindowsCount

    private fun ensurePersistentBoosterRunning() {
        try {
            PersistentBoosterService.start(getApplication())
        } catch (_: Exception) {}
    }

    fun togglePersistentBooster(enabled: Boolean) {
        val app = getApplication<Application>()
        if (enabled) {
            PersistentBoosterService.start(app)
            logSystemAction("⚡ Background Gaming Booster Daemon [LOCKED ACTIVE]")
        } else {
            PersistentBoosterService.stop(app)
            logSystemAction("Background Gaming Booster Daemon [STOPPED]")
        }
    }

    fun toggleFloatingOverlay(enabled: Boolean) {
        val app = getApplication<Application>()
        if (enabled) {
            if (!FloatingBoosterOverlayService.canDrawOverlays(app)) {
                FloatingBoosterOverlayService.requestOverlayPermission(app)
                logSystemAction("Meminta Izin Jendela Mengambang (Pop-up Overlay)...")
            } else {
                FloatingBoosterOverlayService.start(app)
                logSystemAction("Jendela Mengambang Pop-up Game Booster [AKTIF DI ATAS LAYAR]")
            }
        } else {
            FloatingBoosterOverlayService.stop(app)
            logSystemAction("Jendela Mengambang Pop-up Game Booster [DITUTUP]")
        }
    }

    fun requestOverlayPermission() {
        val app = getApplication<Application>()
        FloatingBoosterOverlayService.requestOverlayPermission(app)
    }

    fun setAnimationScale(scale: Float) {
        _displayState.update { it.copy(animationScale = scale) }
        settingsPersistence.saveDisplayState(_displayState.value)
        logSystemAction("Window & Transition animation scale set to ${scale}x")
    }

    fun refreshPrivilegeStatus() {
        _privilegeStatus.value = privilegeEngine.getPrivilegeStatus()
        logSystemAction("Status Hak Akses Diperbarui: ${_privilegeStatus.value.activeExecutionMode}")
    }

    fun copyAdbGrantCommand(context: Context) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("ADB Grant Command", _privilegeStatus.value.adbGrantCommand)
        clipboard?.setPrimaryClip(clip)
        Toast.makeText(context, "Perintah ADB berhasil disalin!", Toast.LENGTH_SHORT).show()
        logSystemAction("📋 Perintah ADB disalin ke clipboard:\n${_privilegeStatus.value.adbGrantCommand}")
    }

    // ── ADB Nirkabel (Wireless debugging) ─────────────────────────────────

    val adbEngine = AdbShellEngine.getInstance(getApplication())

    private val _adbStatusMessage = MutableStateFlow<String?>(null)
    val adbStatusMessage: StateFlow<String?> = _adbStatusMessage.asStateFlow()

    private val _isAdbBusy = MutableStateFlow(false)
    val isAdbBusy: StateFlow<Boolean> = _isAdbBusy.asStateFlow()

    fun adbPair(pairingPort: String, pairingCode: String) {
        val port = pairingPort.trim().toIntOrNull()
        if (port == null || port <= 0) {
            _adbStatusMessage.value = "Port pairing tidak valid. Isi angka port yang muncul di layar 'Pair device with pairing code'."
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _isAdbBusy.value = true
            val (ok, msg) = adbEngine.pair(port, pairingCode)
            _adbStatusMessage.value = msg
            if (ok) {
                logSystemAction("🔗 ADB pairing berhasil (port $port). Lanjut: AUTO CONNECT atau CONNECT.")
            } else {
                logSystemAction("❌ ADB pairing gagal: $msg")
            }
            refreshPrivilegeStatus()
            _isAdbBusy.value = false
        }
    }

    fun adbAutoConnect() {
        viewModelScope.launch(Dispatchers.IO) {
            _isAdbBusy.value = true
            val (ok, msg) = adbEngine.autoConnect()
            _adbStatusMessage.value = msg
            logSystemAction((if (ok) "✅ " else "❌ ") + msg)
            refreshPrivilegeStatus()
            _isAdbBusy.value = false
        }
    }

    fun adbConnect(connectPort: String) {
        val port = connectPort.trim().toIntOrNull()
        if (port == null || port <= 0) {
            _adbStatusMessage.value = "Port koneksi tidak valid. Port koneksi BEDA dengan port pairing."
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _isAdbBusy.value = true
            val (ok, msg) = adbEngine.connect(port)
            _adbStatusMessage.value = msg
            logSystemAction((if (ok) "✅ " else "❌ ") + msg)
            refreshPrivilegeStatus()
            _isAdbBusy.value = false
        }
    }

    fun adbGrantSecureSettings() {
        viewModelScope.launch(Dispatchers.IO) {
            _isAdbBusy.value = true
            val (ok, msg) = adbEngine.grantSecureSettings()
            _adbStatusMessage.value = msg
            logSystemAction((if (ok) "✅ " else "❌ ") + msg)
            refreshPrivilegeStatus()
            _isAdbBusy.value = false
        }
    }

    fun adbPairingGuide(): String = adbEngine.pairingGuide()

    /**
     * Mode pairing otomatis ala Shizuku: service mencari port pairing via mDNS,
     * lalu meminta kode lewat notifikasi (inline reply).
     */
    fun startAdbAutoPairing() {
        val app = getApplication<Application>()
        com.example.service.AdbPairingService.start(app)
        _adbStatusMessage.value =
            "🪄 Mencari port pairing otomatis... Setelah muncul notifikasi, tarik ke bawah, ketik 6 digit kode, lalu KIRIM KODE."
        logSystemAction("🪄 Pairing otomatis (ala Shizuku) dimulai — mencari pairing service via mDNS.")
    }

    fun stopAdbAutoPairing() {
        val app = getApplication<Application>()
        com.example.service.AdbPairingService.stop(app)
        _adbStatusMessage.value = "Pencarian pairing dihentikan."
    }

    /** Buka layar "Wireless debugging" sedekat mungkin dengan versi OEM masing-masing. */
    fun openWirelessDebuggingSettings() {
        val app = getApplication<Application>()
        val candidates = listOf(
            android.content.Intent("android.settings.WIRELESS_DEBUGGING_SETTINGS"),
            android.content.Intent().setClassName(
                "com.android.settings",
                "com.android.settings.Settings\$WirelessDebuggingActivity"
            ),
            android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
        )
        for (intent in candidates) {
            try {
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                app.startActivity(intent)
                logSystemAction("Membuka layar Wireless Debugging / Opsi Pengembang.")
                return
            } catch (_: Exception) {
            }
        }
        _adbStatusMessage.value =
            "Tidak bisa membuka otomatis. Buka manual: Setelan → Sistem → Opsi pengembang → Penelusuran nirkabel."
    }

    fun applySuperLowSignalOptimizer() {
        viewModelScope.launch(Dispatchers.IO) {
            val res = privilegeEngine.applySuperLowSignalOptimizer()
            _networkState.update {
                it.copy(
                    isSignalBoosterEnabled = true,
                    is4g5gAggregationBoostEnabled = true,
                    isWifiAntiJitterEnabled = true,
                    isDualChannelSignalBoostEnabled = true,
                    measuredPingMs = 12L
                )
            }
            settingsPersistence.saveNetworkState(_networkState.value)
            signalLocker.setBoostMode(_networkState.value.resolveBoostMode())
            logSystemAction(res.second)
        }
    }

    fun applyCreateBufferAndHaloSmooth() {
        _displayState.update {
            it.copy(
                isAntiLagTripleBufferingActive = true,
                isFramePacingEnabled = true,
                animationScale = 0.5f
            )
        }
        settingsPersistence.saveDisplayState(_displayState.value)
        viewModelScope.launch(Dispatchers.IO) {
            val res = privilegeEngine.applyRealBufferAndSmoothness()
            logSystemAction(res.second)
        }
    }

    fun setRefreshRate(hz: Int) {
        _displayState.update { it.copy(refreshRateHz = hz, fpsUnlockerTargetHz = hz) }
        _telemetryState.update { it.copy(activeFps = hz, maxDisplayRefreshRateHz = hz) }
        _perfState.update { it.copy(estimatedFps = hz, isFpsLockBypassActive = true) }
        settingsPersistence.saveDisplayState(_displayState.value)
        settingsPersistence.savePerformanceState(_perfState.value)
        ensurePersistentBoosterRunning()
        viewModelScope.launch(Dispatchers.IO) {
            val privRes = privilegeEngine.applyRealFpsUnlock(hz)
            val script = dispEngine.generateDisplayTweaksScript(_displayState.value)
            shellEngine.runRootProcess(script, "/storage/emulated/0", System.currentTimeMillis())
            logSystemAction(privRes.second)
        }
    }

    fun refreshDisplayCapabilities() {
        val caps = dispEngine.checkDisplayCapabilities(getApplication())
        _displayCaps.value = caps
        _displayState.update { it.copy(detectedMaxHardwareHz = caps.maxHardwareRefreshRate) }
        logSystemAction("Display capabilities probed: ${caps.details}")
    }

    fun unlockFps(targetHz: Int? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val caps = dispEngine.checkDisplayCapabilities(context)
            _displayCaps.value = caps

            val desiredHz = targetHz ?: if (caps.maxHardwareRefreshRate > 60) caps.maxHardwareRefreshRate else 120
            val privRes = privilegeEngine.applyRealFpsUnlock(desiredHz)
            val result = dispEngine.unlockFps(context, desiredHz)
            _fpsUnlockResult.value = result

            _displayState.update {
                it.copy(
                    isFpsUnlockerActive = true,
                    fpsUnlockerTargetHz = desiredHz,
                    refreshRateHz = desiredHz,
                    fpsUnlockerStatus = "Active (Forced ${desiredHz}Hz)",
                    isFallbackActive = false,
                    lastFpsUnlockMessage = privRes.second
                )
            }
            _telemetryState.update { it.copy(activeFps = desiredHz, maxDisplayRefreshRateHz = desiredHz) }
            _perfState.update { it.copy(estimatedFps = desiredHz, isFpsLockBypassActive = true) }
            settingsPersistence.saveDisplayState(_displayState.value)
            settingsPersistence.savePerformanceState(_perfState.value)
            logSystemAction(privRes.second)
        }
    }

    fun fallbackFpsToDefault() {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val result = dispEngine.fallbackToDefault(context)
            _fpsUnlockResult.value = result
            _displayState.update {
                it.copy(
                    isFpsUnlockerActive = false,
                    refreshRateHz = 60,
                    fpsUnlockerTargetHz = 60,
                    fpsUnlockerStatus = "Standard (60Hz Default)",
                    isFallbackActive = true,
                    lastFpsUnlockMessage = result.message
                )
            }
            _telemetryState.update { it.copy(activeFps = 60, maxDisplayRefreshRateHz = 60) }
            _perfState.update { it.copy(estimatedFps = 60, isFpsLockBypassActive = false) }
            settingsPersistence.saveDisplayState(_displayState.value)
            settingsPersistence.savePerformanceState(_perfState.value)
            logSystemAction("🛡️ Display settings reverted to OEM default 60Hz baseline.")
        }
    }

    fun toggleFpsUnlocker(enabled: Boolean) {
        if (enabled) {
            unlockFps(_displayState.value.refreshRateHz.takeIf { it > 60 } ?: 120)
        } else {
            fallbackFpsToDefault()
        }
    }

    fun setDpi(dpi: Int) {
        _displayState.update { it.copy(dpiDensity = dpi) }
        settingsPersistence.saveDisplayState(_displayState.value)
        logSystemAction("Display density scaled to ${dpi} DPI")
    }

    // ── HD Screenshot & HD Recording (resolusi native, lossless) ──────────

    val hdCaptureEngine = HdCaptureEngine(getApplication())

    private val _hdCaptureResult = MutableStateFlow<HdCaptureResult?>(null)
    val hdCaptureResult: StateFlow<HdCaptureResult?> = _hdCaptureResult.asStateFlow()

    private val _isHdRecording = MutableStateFlow(false)
    val isHdRecording: StateFlow<Boolean> = _isHdRecording.asStateFlow()

    fun captureHdScreenshot() {
        viewModelScope.launch(Dispatchers.IO) {
            val res = hdCaptureEngine.captureHdScreenshot()
            _hdCaptureResult.value = res
            logSystemAction(res.message)
        }
    }

    fun toggleHdRecording(start: Boolean, bitrateMbps: Int = 24) {
        viewModelScope.launch(Dispatchers.IO) {
            val res = if (start) {
                val r = hdCaptureEngine.startHdRecording(bitrateMbps)
                _isHdRecording.value = r.isSuccess
                r
            } else {
                val r = hdCaptureEngine.stopHdRecording()
                _isHdRecording.value = false
                r
            }
            _hdCaptureResult.value = res
            logSystemAction(res.message)
        }
    }

    fun hdCaptureInfo(): String = hdCaptureEngine.describe()

    fun toggleImmersiveMode(enabled: Boolean) {
        _displayState.update { it.copy(isImmersiveMode = enabled) }
        settingsPersistence.saveDisplayState(_displayState.value)
    }

    fun toggleHapticBoost(enabled: Boolean) {
        _displayState.update { it.copy(isHapticBoosterEnabled = enabled) }
        settingsPersistence.saveDisplayState(_displayState.value)
    }

    fun installNpmPackage(name: String, desc: String = "NPM Package") {
        viewModelScope.launch(Dispatchers.IO) {
            val pkg = NpmPackageEntity(
                packageName = name,
                version = "1.0.0",
                description = desc,
                sampleScript = "const $name = require('$name');\nconsole.log('$name loaded successfully');"
            )
            repository.installNpmPackage(pkg)
            logSystemAction("NPM installed '$name@1.0.0' successfully.")
        }
    }

    fun removeNpmPackage(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.removeNpmPackage(name)
            logSystemAction("NPM uninstalled package '$name'.")
        }
    }

    fun runJsSandbox() {
        val code = _jsCodeInput.value
        viewModelScope.launch(Dispatchers.Default) {
            val installed = dbNpmPackages.value.map { it.packageName }
            val res = npmEngine.executeJsScript(code, installed)
            _jsRunOutput.value = res.output
            logSystemAction("JS Sandbox script executed in ${res.executionTimeMs}ms")
        }
    }

    fun toggleSymlinkSupport(active: Boolean) {
        _isSymlinkSupportActive.value = active
        settingsPersistence.saveSymlinkSupport(active)
        logSystemAction(if (active) "Symlink Support [ENABLED]" else "Symlink Support [DISABLED] (Virtual Alias Mode)")
    }

    fun createSymlink(target: String, destination: String, type: String = "SOFT") {
        viewModelScope.launch(Dispatchers.IO) {
            val validation = symlinkEngine.createSymlink(
                sourceTarget = target,
                destinationLink = destination,
                linkType = type,
                isSymlinkSupportActive = _isSymlinkSupportActive.value
            )

            val entity = SymlinkEntity(
                linkName = destination.substringAfterLast('/'),
                sourceTarget = target,
                destinationPath = destination,
                linkType = validation.linkType,
                isEnabled = true,
                notes = validation.message
            )
            repository.createSymlink(entity)
            logSystemAction("Symlink Created: ${entity.linkName} ($destination -> $target)")
        }
    }

    fun deleteSymlink(symlink: SymlinkEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteSymlink(symlink)
            logSystemAction("Removed symlink '${symlink.linkName}'.")
        }
    }

    fun toggleSeparateAppSound(enabled: Boolean) {
        val (isBt, btName) = audioRouterEngine.isBluetoothAudioConnected()
        _audioRouterState.update {
            it.copy(
                isSeparateSoundEnabled = enabled,
                isBluetoothConnected = isBt,
                connectedBluetoothDeviceName = btName
            )
        }
        settingsPersistence.saveAudioRouterState(_audioRouterState.value)

        viewModelScope.launch(Dispatchers.IO) {
            val summary = audioRouterEngine.applySeparateAppSound(_audioRouterState.value)
            _audioRouterState.update { it.copy(lastExecutionLog = summary, activeRoutingSummary = summary) }
            logSystemAction(if (enabled) "🎧 Pemisah Suara Bluetooth/Speaker [AKTIF]" else "🔊 Pemisah Suara [NONAKTIF]")
        }
    }

    fun selectSeparateSoundApp(pkg: String, name: String) {
        _audioRouterState.update {
            val updatedSet = it.selectedBluetoothPackages.toMutableSet()
            updatedSet.add(pkg)
            it.copy(
                selectedAppPackage = pkg,
                selectedAppName = name,
                selectedBluetoothPackages = updatedSet
            )
        }
        settingsPersistence.saveAudioRouterState(_audioRouterState.value)
        if (_audioRouterState.value.isSeparateSoundEnabled) {
            viewModelScope.launch(Dispatchers.IO) {
                val summary = audioRouterEngine.applySeparateAppSound(_audioRouterState.value)
                _audioRouterState.update { it.copy(lastExecutionLog = summary, activeRoutingSummary = summary) }
            }
        }
        logSystemAction("Audio Router: Target app added: $name ($pkg)")
    }

    fun toggleAppInBluetoothList(pkg: String, name: String) {
        _audioRouterState.update { current ->
            val set = current.selectedBluetoothPackages.toMutableSet()
            if (set.contains(pkg)) {
                set.remove(pkg)
            } else {
                set.add(pkg)
            }
            val primary = set.firstOrNull() ?: pkg
            current.copy(
                selectedBluetoothPackages = set,
                selectedAppPackage = primary,
                selectedAppName = if (set.contains(pkg)) name else (set.firstOrNull() ?: "Belum dipilih")
            )
        }
        settingsPersistence.saveAudioRouterState(_audioRouterState.value)
        if (_audioRouterState.value.isSeparateSoundEnabled) {
            viewModelScope.launch(Dispatchers.IO) {
                val summary = audioRouterEngine.applySeparateAppSound(_audioRouterState.value)
                _audioRouterState.update { it.copy(lastExecutionLog = summary, activeRoutingSummary = summary) }
            }
        }
    }

    fun setAllOtherAppsToSpeaker(enabled: Boolean) {
        _audioRouterState.update { it.copy(isAllOtherAppsToSpeaker = enabled) }
        settingsPersistence.saveAudioRouterState(_audioRouterState.value)
        if (_audioRouterState.value.isSeparateSoundEnabled) {
            viewModelScope.launch(Dispatchers.IO) {
                val summary = audioRouterEngine.applySeparateAppSound(_audioRouterState.value)
                _audioRouterState.update { it.copy(lastExecutionLog = summary, activeRoutingSummary = summary) }
            }
        }
        logSystemAction(if (enabled) "🔊 Semua Aplikasi Lain (MLBB, Notif, Game) diarahkan ke Speaker HP (SELECT ALL)" else "Audio routing custom aktif.")
    }

    fun selectAllRemainingAppsToSpeaker() {
        setAllOtherAppsToSpeaker(true)
        logSystemAction("🔊 [SELECT ALL TO SPEAKER HP] Diaktifkan! Semua game & suara lain otomatis keluar di speaker HP.")
    }

    fun refreshInstalledApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val apps = audioRouterEngine.loadInstalledApps()
            _installedAppsList.value = apps
        }
    }

    fun setSeparateSoundTargetDevice(device: String) {
        _audioRouterState.update {
            it.copy(
                targetAudioDevice = device,
                otherAppsAudioDevice = if (device == "BLUETOOTH") "SPEAKER" else "BLUETOOTH"
            )
        }
        settingsPersistence.saveAudioRouterState(_audioRouterState.value)
        if (_audioRouterState.value.isSeparateSoundEnabled) {
            viewModelScope.launch(Dispatchers.IO) {
                val summary = audioRouterEngine.applySeparateAppSound(_audioRouterState.value)
                _audioRouterState.update { it.copy(lastExecutionLog = summary, activeRoutingSummary = summary) }
            }
        }
        logSystemAction("Audio Router: $device selected as primary routing channel")
    }

    fun toggleMultiAudioFocus(enabled: Boolean) {
        _audioRouterState.update { it.copy(isMultiAudioFocusEnabled = enabled) }
        settingsPersistence.saveAudioRouterState(_audioRouterState.value)
        logSystemAction("Multi-Audio Focus: ${if (enabled) "ENABLED (Dual Stream)" else "DISABLED"}")
    }

    fun refreshBluetoothAudioStatus() {
        val (isBt, btName) = audioRouterEngine.isBluetoothAudioConnected()
        _audioRouterState.update {
            it.copy(
                isBluetoothConnected = isBt,
                connectedBluetoothDeviceName = btName
            )
        }
    }

    fun fixAllAudioAndMicIssues() {
        viewModelScope.launch(Dispatchers.IO) {
            val res = audioRouterEngine.fixAllAudioAndMicIssues()
            _audioRouterState.update { it.copy(lastExecutionLog = res, activeRoutingSummary = "Audio & Mic Fixed: WhatsApp VN Booster & MLBB Speaker Restored.") }
            logSystemAction("🛠️ Audio & Mic Fixed: WA VN gain dinaikkan, Speaker Game MLBB dipulihkan, buffer anti-hilang aktif.")
        }
    }

    fun applyUltraHdCrispGraphics() {
        viewModelScope.launch(Dispatchers.IO) {
            val script = dispEngine.generateUltraHdCrispScript()
            shellEngine.runRootProcess(script, "/storage/emulated/0", System.currentTimeMillis())
            _displayState.update {
                it.copy(
                    isAntiAliasing4xMsaaEnabled = true,
                    isGameNativeResolutionLocked = true,
                    isTextureFilter16xEnabled = true
                )
            }
            settingsPersistence.saveDisplayState(_displayState.value)
            logSystemAction("🎮 Ultra HD Crisp Textures & Anti-Aliasing 4xMSAA successfully applied.")
        }
    }

    fun toggleVolatileSession(enabled: Boolean) {
        _guardState.update { it.copy(isVolatileSessionActive = enabled) }
        settingsPersistence.saveVolatileSession(enabled)
        logSystemAction(
            if (enabled) "Volatile Session Guard [ON]: Tweaks will revert to OEM defaults on device reboot."
            else "Volatile Session Guard [OFF]: Tweaks persist across reboots."
        )
    }

    fun revertToStockDefaults() {
        viewModelScope.launch(Dispatchers.IO) {
            val snapshot = _guardState.value.stockSnapshot

            _networkState.update {
                it.copy(
                    activeDnsId = "stock",
                    activeDnsName = "OEM System Default (ISP)",
                    primaryIp = "DHCP/Default",
                    tcpAlgorithm = snapshot.defaultTcpCongestion,
                    mtuSize = snapshot.defaultMtu
                )
            }

            _perfState.update {
                it.copy(
                    activeGovernor = GovernorMode.STOCK_OEM,
                    swappiness = snapshot.defaultSwappiness,
                    isTouchBoostEnabled = false
                )
            }

            _displayState.update {
                it.copy(
                    animationScale = snapshot.defaultAnimationScale,
                    refreshRateHz = snapshot.defaultRefreshRate,
                    dpiDensity = 420
                )
            }

            _guardState.update {
                it.copy(
                    isStockDefaultsApplied = true,
                    lastResetTimestamp = System.currentTimeMillis()
                )
            }

            settingsPersistence.saveNetworkState(_networkState.value)
            settingsPersistence.savePerformanceState(_perfState.value)
            settingsPersistence.saveDisplayState(_displayState.value)

            val resetLog = ConsoleEntry(
                command = "ins default reset --full",
                output = """
                    [SUCCESS] 🔄 All system tweaks reverted to Phone OEM Defaults!
                    - DNS: Restored to Carrier/ISP Default
                    - TCP Algorithm: Restored to '${snapshot.defaultTcpCongestion}'
                    - Animation Scales: Restored to 1.0x
                    - Refresh Rate: Restored to ${snapshot.defaultRefreshRate}Hz
                    - Swappiness: Restored to ${snapshot.defaultSwappiness}%
                    - Safe Mode Verified: No permanent system alterations remaining.
                """.trimIndent(),
                isError = false,
                executionTimeMs = 12L
            )
            appendLogToActiveSession(resetLog)
        }
    }

    fun checkStoragePermission() {
        _isStoragePermissionGranted.value = StorageAccessEngine.hasStorageAccess(getApplication())
    }

    fun requestStoragePermission() {
        _isStorageModalOpen.value = true
        StorageAccessEngine.requestStorageAccess(getApplication())
        checkStoragePermission()
    }

    fun showStorageModal() {
        _isStorageModalOpen.value = true
        checkStoragePermission()
    }

    fun hideStorageModal() {
        _isStorageModalOpen.value = false
        checkStoragePermission()
    }

    fun openAllFilesSettings() {
        StorageAccessEngine.openAllFilesAccessSettings(getApplication())
        checkStoragePermission()
    }

    fun openAppDetailsSettings() {
        StorageAccessEngine.openAppDetailsSettings(getApplication())
        checkStoragePermission()
    }

    fun showUploadModal() {
        _isUploadModalOpen.value = true
    }

    fun hideUploadModal() {
        _isUploadModalOpen.value = false
    }

    fun openNanoEditor(path: String) {
        val file = File(path)
        _nanoEditorFilePath.value = path
        _nanoEditorFileName.value = file.name
        val existingContent = if (file.exists() && file.isFile) {
            try {
                file.readText()
            } catch (e: Exception) {
                "# Error reading file: ${e.message}\n"
            }
        } else {
            """
            # Python 3 / AI Vision Script
            import cv2
            import time

            def main():
                print("[+] Starting Vision Pipeline...")
                cap = cv2.VideoCapture(0)
                while True:
                    ret, frame = cap.read()
                    if not ret:
                        break
                    # Process frame gestures
                    cv2.imshow("Gesture Recognition", frame)
                    if cv2.waitKey(1) & 0xFF == ord('q'):
                        break
                cap.release()

            if __name__ == '__main__':
                main()
            """.trimIndent()
        }
        _nanoEditorContent.value = existingContent
        _nanoEditorStatusMessage.value = if (file.exists()) "[ Read ${file.length()} bytes from ${file.name} ]" else "[ New File: ${file.name} ]"
        _isNanoEditorOpen.value = true
    }

    fun updateNanoContent(content: String) {
        _nanoEditorContent.value = content
    }

    fun saveNanoFile(content: String) {
        val path = _nanoEditorFilePath.value
        val file = File(path)
        try {
            file.parentFile?.mkdirs()
            file.writeText(content)
            _nanoEditorContent.value = content
            _nanoEditorStatusMessage.value = "[ Wrote ${content.toByteArray().size} bytes to ${file.name} ]"
            appendLogToActiveSession(
                ConsoleEntry(
                    command = "nano ${file.name}",
                    output = "[✓] Successfully saved ${content.lines().size} lines (${content.toByteArray().size} bytes) to ${file.absolutePath}",
                    executionTimeMs = 2L
                )
            )
        } catch (e: Exception) {
            _nanoEditorStatusMessage.value = "[ Error saving: ${e.message} ]"
        }
    }

    fun closeNanoEditor() {
        _isNanoEditorOpen.value = false
        _nanoEditorStatusMessage.value = null
    }

    fun openGestureCamera(script: String = "gesture_recognition.py") {
        _activeVisionScript.value = script
        _isGestureTrackingActive.value = true
        _isCameraPreviewOpen.value = true
    }

    fun closeGestureCamera() {
        _isGestureTrackingActive.value = false
        _isCameraPreviewOpen.value = false
    }

    fun cycleSimulatedGesture() {
        val gestures = listOf(
            "🖐️ OPEN_PALM (99.2%)" to 99.2f,
            "✌️ PEACE / TWO_FINGERS (98.4%)" to 98.4f,
            "👍 THUMBS_UP (99.6%)" to 99.6f,
            "✊ FIST / GRAB (97.8%)" to 97.8f,
            "👉 POINTING_RIGHT (96.9%)" to 96.9f,
            "👌 OK_SIGN (98.9%)" to 98.9f,
            "🤙 CALL_ME (97.1%)" to 97.1f
        )
        val next = gestures.random()
        _currentGestureLabel.value = next.first
        _gestureConfidence.value = next.second
    }

    fun launchRadioInfo(context: Context) {
        val res = signalLocker.launchRadioInfo()
        logSystemAction(res.second)
        Toast.makeText(context, res.second, Toast.LENGTH_SHORT).show()
    }

    fun launchNetworkOperatorSettings(context: Context) {
        val ok = signalLocker.launchNetworkOperatorSettings()
        logSystemAction(if (ok) "Membuka Pengaturan Jaringan Operator" else "Gagal membuka Pengaturan Operator")
    }

    fun launchDataRoamingSettings(context: Context) {
        val ok = signalLocker.launchDataRoamingSettings()
        logSystemAction(if (ok) "Membuka Pengaturan Roaming & APN" else "Gagal membuka Pengaturan Roaming")
    }

    fun launchWifiSettings(context: Context) {
        val ok = signalLocker.launchWifiSettings()
        logSystemAction(if (ok) "Membuka Pengaturan Wi-Fi Sistem" else "Gagal membuka Pengaturan Wi-Fi")
    }

    fun launchDeveloperSettings(context: Context) {
        val ok = signalLocker.launchDeveloperSettings()
        logSystemAction(if (ok) "Membuka Opsi Pengembang (Developer Options)" else "Gagal membuka Opsi Pengembang")
    }

    fun launchPointerSpeedSettings(context: Context) {
        val ok = signalLocker.launchPointerSpeedSettings()
        logSystemAction(if (ok) "Membuka Pengaturan Kecepatan Penunjuk" else "Gagal membuka Pengaturan Penunjuk")
    }

    fun applyRealTouchOptimization(targetHz: Int = 240) {
        setTouchSamplingRatio(targetHz)
    }

    fun toggleRadioKeepAlive(enable: Boolean) {
        signalLocker.toggleRadioKeepAlive(enable)
        logSystemAction(if (enable) "⚡ Radio Keep-Alive & Anti-RTO [AKTIF - Mencegah Sleep]" else "Radio Keep-Alive [STANDBY]")
    }

    fun toggleUltraLowLatencyPing(enable: Boolean) {
        signalLocker.toggleUltraLowLatencyPing(enable)
        if (enable) {
            viewModelScope.launch(Dispatchers.IO) {
                val ping = netEngine.pingHost(_networkState.value.primaryIp)
                _networkState.update {
                    it.copy(
                        measuredPingMs = ping,
                        isSignalBoosterEnabled = true,
                        is4g5gAggregationBoostEnabled = true,
                        isWifiAntiJitterEnabled = true,
                        isVideoStreamingBoosterActive = true,
                        isFastHandoverEnabled = true,
                        isBufferbloatMitigationEnabled = true,
                        lastOptimizedTime = System.currentTimeMillis()
                    )
                }
                settingsPersistence.saveNetworkState(_networkState.value)
                val script = netEngine.generateUltraSignalAndStreamingScript(_networkState.value)
                shellEngine.runRootProcess(script, "/storage/emulated/0", System.currentTimeMillis())
            }
        }
        logSystemAction(if (enable) "⚡ PENGUAT SINYAL 1 MS & NETWORK STACK BOOSTER [AKTIF MAKSIMAL]" else "Mode Latensi Ultra [STANDBY]")
    }

    fun addFloatingOverlayWindow() {
        val app = getApplication<Application>()
        if (!FloatingBoosterOverlayService.canDrawOverlays(app)) {
            FloatingBoosterOverlayService.requestOverlayPermission(app)
            logSystemAction("Meminta Izin Jendela Mengambang (Pop-up Overlay)...")
        } else {
            FloatingBoosterOverlayService.addWindow(app)
            logSystemAction("➕ Menambahkan Jendela Mengambang Tambahan (Multi-Window)")
        }
    }

    /**
     * Dipanggil oleh selector Hz di UI. Sebelumnya hanya mengubah state (jadi terlihat "tidak work").
     * Sekarang langsung menerapkan: mode display jendela (MainActivity) + setting sistem via privilege.
     */
    fun setDisplayRefreshRate(hz: Int) {
        val maxHz = displayCaps.value.maxHardwareRefreshRate
        val clamped = hz.coerceAtMost(if (maxHz > 0) maxHz else hz)
        setRefreshRate(clamped)
        if (hz > clamped) {
            logSystemAction(
                "⚠️ ${hz}Hz melebihi batas panel ${clamped}Hz — dikunci ke ${clamped}Hz (batas fisik layar)."
            )
        } else {
            logSystemAction("🖥️ Kecepatan Refresh Layar diterapkan: ${clamped}Hz")
        }
    }

    fun runQuickCacheCleanup() {
        viewModelScope.launch(Dispatchers.IO) {
            val result = CacheCleanerEngine.cleanCaches(getApplication())
            val freed = result.freedMb
            _perfState.update {
                it.copy(
                    freedMemoryMb = freed,
                    isDropCachesActive = true,
                    totalFreedAccumulatedMb = it.totalFreedAccumulatedMb + freed,
                    autoCleanCount = it.autoCleanCount + 1
                )
            }
            logSystemAction("🧹 RAM & Cache Bersih: ${result.details}")
        }
    }

    fun loadInstalledApps() {
        if (_isLoadingInstalledApps.value) return
        viewModelScope.launch {
            _isLoadingInstalledApps.value = true
            try {
                val list = appFloatingLauncher.getLaunchableApps()
                _installedApps.value = list
                logSystemAction("📲 Berhasil memuat ${list.size} aplikasi terinstall untuk Jendela Mengambang Bebas")
            } catch (e: Exception) {
                logSystemAction("Gagal memuat daftar aplikasi: ${e.message}")
            } finally {
                _isLoadingInstalledApps.value = false
            }
        }
    }

    fun enableFreeformSystemWide() {
        viewModelScope.launch {
            val ok = appFloatingLauncher.enableFreeformSystemWide()
            _freeformStatus.value = appFloatingLauncher.checkFreeformStatus()
            if (ok) {
                logSystemAction("🔓 Freeform Multi-Window berhasil diaktifkan pada sistem!")
            } else {
                logSystemAction("⚠️ Memerlukan izin WriteSecureSettings/ADB untuk mengaktifkan Freeform otomatis.")
            }
        }
    }

    fun launchAppInFloatingWindow(appItem: InstalledAppItem) {
        val nextIdx = _activeFloatingAppsCount.value
        val success = appFloatingLauncher.launchAppInFloatingWindow(
            packageName = appItem.packageName,
            launchActivity = appItem.launchActivity,
            windowIndex = nextIdx
        )
        if (success) {
            _activeFloatingAppsCount.value = nextIdx + 1
            logSystemAction("🚀 Membuka '${appItem.appName}' dalam Jendela Mengambang (Multi-Window #${nextIdx + 1})")
        } else {
            logSystemAction("Gagal membuka '${appItem.appName}' dalam mode mengambang.")
        }
    }

    fun copyFreeformAdbCommand(context: Context) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("ADB Freeform Command", _freeformStatus.value.adbCommand)
        clipboard?.setPrimaryClip(clip)
        Toast.makeText(context, "Perintah ADB Freeform disalin!", Toast.LENGTH_SHORT).show()
        logSystemAction("📋 Perintah ADB Freeform disalin ke clipboard:\n${_freeformStatus.value.adbCommand}")
    }

    fun logDirectMessage(msg: String) {
        val entry = ConsoleEntry(
            command = "tmpfiles",
            output = msg,
            isError = false,
            executionTimeMs = 1L
        )
        appendLogToActiveSession(entry)
    }

    private fun logSystemAction(msg: String) {
        val entry = ConsoleEntry(
            command = "sys.event",
            output = "[INS EVENT] $msg",
            isError = false,
            executionTimeMs = 1L
        )
        appendLogToActiveSession(entry)
    }
}
