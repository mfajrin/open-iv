package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.DdsAndYtdDecoder
import com.example.core.DdsInfo
import com.example.core.JOAATResult
import com.example.core.JoaatHasher
import com.example.core.OivPackageEngine
import com.example.core.OivPackageInfo
import com.example.core.Rpf7Engine
import com.example.core.Rsc7Info
import com.example.data.AppSettings
import com.example.data.DirectoryListing
import com.example.data.ExplorerItem
import com.example.data.ExplorerItemKind
import com.example.data.PreferencesRepository
import com.example.data.RecentItemRecord
import com.example.data.SafDocItem
import com.example.data.SafFolderHelper
import com.example.data.WorkspaceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class ExplorerFilter(val label: String) {
    ALL("Semua"),
    META_XML(".meta / .xml"),
    RPF(".rpf"),
    TEXTURES(".ytd / .dds"),
    MODELS(".ydr / .yft")
}

data class HeaderAnalysisResult(
    val fileName: String,
    val sizeBytes: Long,
    val magicHex: String,
    val magicAscii: String,
    val detectedFormat: String,
    val details: List<Pair<String, String>>
)

data class ExplorerUiState(
    val currentFsDir: File? = null,
    val activeRpfFile: File? = null,
    val activeRpfSubPath: String = "",
    val listing: DirectoryListing? = null,
    val searchQuery: String = "",
    val selectedFilter: ExplorerFilter = ExplorerFilter.ALL,
    val searchResults: List<ExplorerItem>? = null,
    val isLoading: Boolean = false,
    val safTreeUri: Uri? = null,
    val safDocStack: List<String?> = emptyList(),
    val safTitle: String = "",
    val safItems: List<SafDocItem> = emptyList(),
    val isBrowsingSaf: Boolean = false
)

data class InspectorUiState(
    val item: ExplorerItem? = null,
    val rawBytes: ByteArray = ByteArray(0),
    val textContent: String = "",
    val editedText: String = "",
    val isTextRepresentable: Boolean = false,
    val ddsInfo: DdsInfo? = null,
    val rsc7Info: Rsc7Info? = null,
    val decodedBitmap: Bitmap? = null,
    val isLoading: Boolean = false,
    val hasUnsavedChanges: Boolean = false
)

data class OivUiState(
    val packages: List<OivPackageInfo> = emptyList(),
    val selectedPackage: OivPackageInfo? = null,
    val isLoading: Boolean = false,
    val isInstalling: Boolean = false
)

data class ToolsUiState(
    val hashInput: String = "adder",
    val forceLowercase: Boolean = true,
    val hashResult: JOAATResult = JoaatHasher.hash("adder"),
    val dictionaryFilter: String = "",
    val dictionaryResults: List<Pair<JOAATResult, String>> = JoaatHasher.lookupByHexOrName(""),
    val headerAnalysis: HeaderAnalysisResult? = null
)

class OpenVViewModel(application: Application) : AndroidViewModel(application) {

    private val prefsRepo = PreferencesRepository(application)
    private val workspaceRepo = WorkspaceRepository(application)

    val settings: StateFlow<AppSettings> = prefsRepo.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AppSettings()
    )

    private val _explorerState = MutableStateFlow(ExplorerUiState())
    val explorerState: StateFlow<ExplorerUiState> = _explorerState.asStateFlow()

    private val _inspectorState = MutableStateFlow(InspectorUiState())
    val inspectorState: StateFlow<InspectorUiState> = _inspectorState.asStateFlow()

    private val _oivState = MutableStateFlow(OivUiState())
    val oivState: StateFlow<OivUiState> = _oivState.asStateFlow()

    private val _toolsState = MutableStateFlow(ToolsUiState())
    val toolsState: StateFlow<ToolsUiState> = _toolsState.asStateFlow()

    private val _snackbarEvents = MutableSharedFlow<String>()
    val snackbarEvents = _snackbarEvents.asSharedFlow()

    private val _pendingExportBytes = MutableStateFlow<Pair<String, ByteArray>?>(null)
    val pendingExportBytes: StateFlow<Pair<String, ByteArray>?> = _pendingExportBytes.asStateFlow()

    init {
        initializeWorkspace()
    }

    private fun initializeWorkspace() {
        viewModelScope.launch {
            _explorerState.update { it.copy(isLoading = true) }
            val sandbox = workspaceRepo.initSandbox(forceReset = false)
            openFsDirectory(sandbox)
            refreshOivPackages()
        }
    }

    fun resetSandbox() {
        viewModelScope.launch {
            _explorerState.update { it.copy(isLoading = true) }
            val sandbox = workspaceRepo.initSandbox(forceReset = true)
            openFsDirectory(sandbox)
            refreshOivPackages()
            emitMessage("Sandbox GTA V berhasil dipulihkan ke kondisi awal.")
        }
    }

    fun toggleDarkTheme(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setDarkTheme(enabled) }
    }

    fun toggleEditMode(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.setEditMode(enabled)
            emitMessage(if (enabled) "Edit Mode diaktifkan — modifikasi RPF7 terbuka." else "Mode Baca Saja aktif — arsip terlindungi.")
        }
    }

    fun toggleAutoBackup(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setAutoBackup(enabled) }
    }

    fun openSandboxRoot() {
        val sandbox = workspaceRepo.getSandboxDir()
        openFsDirectory(sandbox)
    }

    fun openQuickPath(relativePath: String) {
        val sandbox = workspaceRepo.getSandboxDir()
        val updateRpf = File(sandbox, "update/update.rpf")
        if (relativePath == "update.rpf" && updateRpf.exists()) {
            openRpfArchive(updateRpf)
        } else if (relativePath == "handling.meta" && updateRpf.exists()) {
            openRpfArchive(updateRpf, "common/data")
        } else {
            openFsDirectory(sandbox)
        }
    }

    fun openFsDirectory(dir: File) {
        viewModelScope.launch {
            _explorerState.update {
                it.copy(
                    isLoading = true,
                    currentFsDir = dir,
                    activeRpfFile = null,
                    activeRpfSubPath = "",
                    isBrowsingSaf = false,
                    searchQuery = "",
                    searchResults = null
                )
            }
            try {
                val listing = workspaceRepo.listDirectory(dir, null, "")
                _explorerState.update { it.copy(listing = listing, isLoading = false) }
            } catch (e: Exception) {
                _explorerState.update { it.copy(isLoading = false) }
                emitMessage("Gagal membuka folder: ${e.message}")
            }
        }
    }

    fun openRpfArchive(rpfFile: File, subPath: String = "") {
        viewModelScope.launch {
            _explorerState.update {
                it.copy(
                    isLoading = true,
                    currentFsDir = rpfFile.parentFile ?: workspaceRepo.getSandboxDir(),
                    activeRpfFile = rpfFile,
                    activeRpfSubPath = subPath,
                    isBrowsingSaf = false,
                    searchQuery = "",
                    searchResults = null
                )
            }
            try {
                val listing = workspaceRepo.listDirectory(
                    fsDir = rpfFile.parentFile ?: workspaceRepo.getSandboxDir(),
                    rpfFile = rpfFile,
                    rpfSubPath = subPath
                )
                _explorerState.update { it.copy(listing = listing, isLoading = false) }
                prefsRepo.addRecentItem(RecentItemRecord(rpfFile.name, rpfFile.absolutePath, "RPF"))
            } catch (e: Exception) {
                _explorerState.update { it.copy(isLoading = false) }
                emitMessage("Gagal membaca RPF: ${e.message}")
            }
        }
    }

    fun navigateBreadcrumb(target: String) {
        if (target.startsWith("rpf://")) {
            val withoutScheme = target.removePrefix("rpf://")
            val rpfPath = withoutScheme.substringBefore('#')
            val sub = withoutScheme.substringAfter('#', "")
            openRpfArchive(File(rpfPath), sub)
        } else {
            val f = File(target)
            if (f.isFile && f.name.endsWith(".rpf", ignoreCase = true)) {
                openRpfArchive(f, "")
            } else if (f.exists() && f.isDirectory) {
                openFsDirectory(f)
            }
        }
    }

    fun stepUpDirectory(): Boolean {
        val state = _explorerState.value
        if (state.isBrowsingSaf) {
            val stack = state.safDocStack
            if (stack.size > 1) {
                val newStack = stack.dropLast(1)
                val treeUri = state.safTreeUri ?: return false
                viewModelScope.launch {
                    val (title, items) = SafFolderHelper.listChildren(getApplication(), treeUri, newStack.lastOrNull())
                    _explorerState.update { it.copy(safDocStack = newStack, safTitle = title, safItems = items) }
                }
                return true
            }
            openSandboxRoot()
            return true
        }
        val activeRpf = state.activeRpfFile
        if (activeRpf != null) {
            if (state.activeRpfSubPath.isNotBlank()) {
                val parent = state.activeRpfSubPath.substringBeforeLast('/', "")
                openRpfArchive(activeRpf, parent)
            } else {
                openFsDirectory(activeRpf.parentFile ?: workspaceRepo.getSandboxDir())
            }
            return true
        }
        val curDir = state.currentFsDir
        val sandbox = workspaceRepo.getSandboxDir()
        if (curDir != null && curDir.absolutePath != sandbox.absolutePath &&
            curDir.absolutePath.startsWith(sandbox.absolutePath)
        ) {
            curDir.parentFile?.let { openFsDirectory(it) }
            return true
        }
        return false
    }

    fun onItemClicked(item: ExplorerItem, onOpenInspector: () -> Unit, onOpenOiv: () -> Unit) {
        when (item.kind) {
            ExplorerItemKind.FOLDER -> {
                val rpfPath = item.rpfArchiveFilePath
                if (item.isInsideRpf && rpfPath != null) {
                    openRpfArchive(File(rpfPath), item.rpfEntryPath ?: "")
                } else {
                    openFsDirectory(File(item.path))
                }
            }
            ExplorerItemKind.RPF_ARCHIVE -> {
                if (!item.isInsideRpf) {
                    openRpfArchive(File(item.path), "")
                } else {
                    // Nested RPF inside RPF: extract to temp and open!
                    viewModelScope.launch {
                        try {
                            val bytes = workspaceRepo.readExplorerItemBytes(item)
                            val tempFile = File(workspaceRepo.importsRoot, item.name)
                            tempFile.writeBytes(bytes)
                            openRpfArchive(tempFile, "")
                        } catch (e: Exception) {
                            emitMessage("Gagal membuka RPF bersarang: ${e.message}")
                        }
                    }
                }
            }
            ExplorerItemKind.OIV_PACKAGE -> {
                viewModelScope.launch {
                    try {
                        val pkg = if (!item.isInsideRpf) {
                            OivPackageEngine.parseOivFile(File(item.path))
                        } else {
                            val bytes = workspaceRepo.readExplorerItemBytes(item)
                            OivPackageEngine.parseOivBytes(item.name, item.path, bytes)
                        }
                        _oivState.update { it.copy(selectedPackage = pkg) }
                        onOpenOiv()
                    } catch (e: Exception) {
                        emitMessage("Gagal membaca paket .OIV: ${e.message}")
                    }
                }
            }
            else -> {
                openItemInInspector(item)
                onOpenInspector()
            }
        }
    }

    fun openItemInInspector(item: ExplorerItem) {
        viewModelScope.launch {
            _inspectorState.value = InspectorUiState(item = item, isLoading = true)
            try {
                val bytes = workspaceRepo.readExplorerItemBytes(item)
                val isText = isProbablyText(item.name, bytes)
                val text = if (isText) bytes.toString(Charsets.UTF_8) else ""
                val dds = if (DdsAndYtdDecoder.isDds(bytes)) DdsAndYtdDecoder.parseDds(bytes) else null
                val rsc7 = if (DdsAndYtdDecoder.isRsc7(bytes)) DdsAndYtdDecoder.parseRsc7(bytes) else null
                val standardBmp = if (dds == null && rsc7 == null &&
                    (item.name.endsWith(".png", true) || item.name.endsWith(".jpg", true))
                ) {
                    withContext(Dispatchers.Default) {
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    }
                } else null

                _inspectorState.value = InspectorUiState(
                    item = item,
                    rawBytes = bytes,
                    textContent = text,
                    editedText = text,
                    isTextRepresentable = isText,
                    ddsInfo = dds,
                    rsc7Info = rsc7,
                    decodedBitmap = dds?.bitmap ?: rsc7?.embeddedDds?.bitmap ?: standardBmp,
                    isLoading = false,
                    hasUnsavedChanges = false
                )
            } catch (e: Exception) {
                _inspectorState.update { it.copy(isLoading = false) }
                emitMessage("Gagal memuat berkas: ${e.message}")
            }
        }
    }

    fun updateEditedText(newText: String) {
        _inspectorState.update {
            it.copy(
                editedText = newText,
                hasUnsavedChanges = newText != it.textContent
            )
        }
    }

    fun saveInspectorChanges() {
        val state = _inspectorState.value
        val item = state.item ?: return
        if (!settings.value.isEditMode) {
            emitMessage("Aktifkan Edit Mode terlebih dahulu untuk menyimpan perubahan.")
            return
        }
        viewModelScope.launch {
            try {
                val newBytes = state.editedText.toByteArray(Charsets.UTF_8)
                workspaceRepo.writeExplorerItemBytes(
                    item = item,
                    newBytes = newBytes,
                    createBackup = settings.value.autoBackupOnEdit
                )
                _inspectorState.update {
                    it.copy(
                        rawBytes = newBytes,
                        textContent = state.editedText,
                        hasUnsavedChanges = false
                    )
                }
                refreshCurrentListing()
                emitMessage("Disimpan & RPF7 di-repack: ${item.name}")
            } catch (e: Exception) {
                emitMessage("Gagal menyimpan: ${e.message}")
            }
        }
    }

    fun revertInspectorChanges() {
        _inspectorState.update {
            it.copy(
                editedText = it.textContent,
                hasUnsavedChanges = false
            )
        }
        emitMessage("Perubahan dibatalkan.")
    }

    fun notifyMessage(message: String) {
        emitMessage(message)
    }

    fun updateSearchQuery(query: String) {
        _explorerState.update { it.copy(searchQuery = query) }
        if (query.isBlank()) {
            _explorerState.update { it.copy(searchResults = null) }
            return
        }
        val state = _explorerState.value
        val dir = state.currentFsDir ?: workspaceRepo.getSandboxDir()
        viewModelScope.launch {
            val results = workspaceRepo.searchInsideArchiveOrWorkspace(dir, state.activeRpfFile, query)
            _explorerState.update { it.copy(searchResults = results) }
        }
    }

    fun setExplorerFilter(filter: ExplorerFilter) {
        _explorerState.update { it.copy(selectedFilter = filter) }
    }

    fun injectFileFromUri(uri: Uri) {
        if (!settings.value.isEditMode) {
            emitMessage("Aktifkan Edit Mode untuk mengimpor berkas ke arsip.")
            return
        }
        val state = _explorerState.value
        val fsDir = state.currentFsDir ?: workspaceRepo.getSandboxDir()
        viewModelScope.launch {
            try {
                val imported = workspaceRepo.importDocumentUri(uri)
                val bytes = imported.readBytes()
                workspaceRepo.injectFileIntoCurrentLocation(
                    fsDir = fsDir,
                    rpfFile = state.activeRpfFile,
                    rpfSubPath = state.activeRpfSubPath,
                    fileName = imported.name,
                    data = bytes,
                    createBackup = settings.value.autoBackupOnEdit
                )
                refreshCurrentListing()
                emitMessage("Berhasil mengimpor ${imported.name}")
            } catch (e: Exception) {
                emitMessage("Gagal mengimpor: ${e.message}")
            }
        }
    }

    fun openExternalDocumentUri(uri: Uri, onOpenExplorer: () -> Unit, onOpenOiv: () -> Unit, onOpenInspector: () -> Unit) {
        viewModelScope.launch {
            try {
                val imported = workspaceRepo.importDocumentUri(uri)
                prefsRepo.addRecentItem(
                    RecentItemRecord(
                        title = imported.name,
                        path = imported.absolutePath,
                        type = imported.extension.uppercase().ifEmpty { "FILE" }
                    )
                )
                when {
                    imported.name.endsWith(".rpf", true) -> {
                        openRpfArchive(imported)
                        onOpenExplorer()
                    }
                    imported.name.endsWith(".oiv", true) -> {
                        val pkg = OivPackageEngine.parseOivFile(imported)
                        refreshOivPackages()
                        _oivState.update { it.copy(selectedPackage = pkg) }
                        onOpenOiv()
                    }
                    else -> {
                        val item = ExplorerItem(
                            name = imported.name,
                            path = imported.absolutePath,
                            kind = workspaceRepo.classifyExtension(imported.name),
                            sizeBytes = imported.length(),
                            isInsideRpf = false
                        )
                        openItemInInspector(item)
                        onOpenInspector()
                    }
                }
            } catch (e: Exception) {
                emitMessage("Gagal membuka dokumen: ${e.message}")
            }
        }
    }

    fun openExternalTreeUri(treeUri: Uri) {
        viewModelScope.launch {
            try {
                val (title, items) = SafFolderHelper.listChildren(getApplication(), treeUri, null)
                _explorerState.update {
                    it.copy(
                        isBrowsingSaf = true,
                        safTreeUri = treeUri,
                        safDocStack = listOf(null),
                        safTitle = title,
                        safItems = items
                    )
                }
                prefsRepo.addRecentItem(RecentItemRecord(title, treeUri.toString(), "DIR"))
            } catch (e: Exception) {
                emitMessage("Gagal membuka folder SAF: ${e.message}")
            }
        }
    }

    fun onSafItemClick(doc: SafDocItem, onOpenExplorer: () -> Unit, onOpenOiv: () -> Unit, onOpenInspector: () -> Unit) {
        if (doc.isDirectory) {
            val treeUri = _explorerState.value.safTreeUri ?: return
            val docId = SafFolderHelper.docIdFromUri(doc.uri) ?: return
            viewModelScope.launch {
                val (title, items) = SafFolderHelper.listChildren(getApplication(), treeUri, docId)
                _explorerState.update {
                    it.copy(
                        safDocStack = it.safDocStack + docId,
                        safTitle = title,
                        safItems = items
                    )
                }
            }
        } else {
            openExternalDocumentUri(doc.uri, onOpenExplorer, onOpenOiv, onOpenInspector)
        }
    }

    fun createNewRpf7(name: String) {
        val dir = _explorerState.value.currentFsDir ?: workspaceRepo.getSandboxDir()
        viewModelScope.launch {
            try {
                val created = workspaceRepo.createEmptyRpf7Archive(dir, name)
                refreshCurrentListing()
                openRpfArchive(created)
                emitMessage("Arsip RPF7 baru dibuat: ${created.name}")
            } catch (e: Exception) {
                emitMessage("Gagal membuat RPF7: ${e.message}")
            }
        }
    }

    fun createFolder(name: String) {
        if (!settings.value.isEditMode) {
            emitMessage("Aktifkan Edit Mode untuk membuat folder.")
            return
        }
        val state = _explorerState.value
        val dir = state.currentFsDir ?: workspaceRepo.getSandboxDir()
        viewModelScope.launch {
            try {
                workspaceRepo.createFolderInLocation(
                    fsDir = dir,
                    rpfFile = state.activeRpfFile,
                    rpfSubPath = state.activeRpfSubPath,
                    folderName = name,
                    createBackup = settings.value.autoBackupOnEdit
                )
                refreshCurrentListing()
                emitMessage("Folder '$name' dibuat.")
            } catch (e: Exception) {
                emitMessage("Gagal membuat folder: ${e.message}")
            }
        }
    }

    fun deleteItem(item: ExplorerItem) {
        if (!settings.value.isEditMode) {
            emitMessage("Aktifkan Edit Mode untuk menghapus entri.")
            return
        }
        viewModelScope.launch {
            try {
                workspaceRepo.deleteExplorerItem(item, settings.value.autoBackupOnEdit)
                refreshCurrentListing()
                emitMessage("'${item.name}' dihapus.")
            } catch (e: Exception) {
                emitMessage("Gagal menghapus: ${e.message}")
            }
        }
    }

    fun renameItem(item: ExplorerItem, newName: String) {
        if (!settings.value.isEditMode) {
            emitMessage("Aktifkan Edit Mode untuk mengganti nama.")
            return
        }
        viewModelScope.launch {
            try {
                workspaceRepo.renameExplorerItem(item, newName, settings.value.autoBackupOnEdit)
                refreshCurrentListing()
                emitMessage("Nama diubah menjadi '$newName'.")
            } catch (e: Exception) {
                emitMessage("Gagal mengganti nama: ${e.message}")
            }
        }
    }

    fun prepareExport(item: ExplorerItem) {
        viewModelScope.launch {
            try {
                val bytes = workspaceRepo.readExplorerItemBytes(item)
                _pendingExportBytes.value = item.name to bytes
            } catch (e: Exception) {
                emitMessage("Gagal menyiapkan ekspor: ${e.message}")
            }
        }
    }

    fun completeExport(uri: Uri) {
        val pending = _pendingExportBytes.value ?: return
        _pendingExportBytes.value = null
        viewModelScope.launch {
            try {
                workspaceRepo.exportBytesToUri(uri, pending.second)
                emitMessage("Berhasil mengekspor ${pending.first}")
            } catch (e: Exception) {
                emitMessage("Gagal mengekspor: ${e.message}")
            }
        }
    }

    fun clearPendingExport() {
        _pendingExportBytes.value = null
    }

    fun refreshOivPackages() {
        viewModelScope.launch {
            _oivState.update { it.copy(isLoading = true) }
            val pkgs = workspaceRepo.scanAllOivPackages()
            _oivState.update { it.copy(packages = pkgs, isLoading = false) }
        }
    }

    fun selectOivPackage(pkg: OivPackageInfo?) {
        _oivState.update { it.copy(selectedPackage = pkg) }
    }

    fun installSelectedOiv() {
        val pkg = _oivState.value.selectedPackage ?: return
        viewModelScope.launch {
            _oivState.update { it.copy(isInstalling = true) }
            try {
                val record = workspaceRepo.installOivPackage(pkg)
                prefsRepo.addInstalledMod(record)
                refreshCurrentListing()
                emitMessage("Mod '${pkg.name}' berhasil dipasang ke ${File(record.targetArchive).name}!")
            } catch (e: Exception) {
                emitMessage("Gagal memasang .OIV: ${e.message}")
            } finally {
                _oivState.update { it.copy(isInstalling = false) }
            }
        }
    }

    fun uninstallMod(modId: String) {
        val record = settings.value.installedMods.firstOrNull { it.id == modId } ?: return
        viewModelScope.launch {
            try {
                workspaceRepo.uninstallOivMod(record)
                prefsRepo.removeInstalledMod(modId)
                refreshCurrentListing()
                emitMessage("Mod '${record.name}' di-rollback dari backup.")
            } catch (e: Exception) {
                emitMessage("Gagal rollback mod: ${e.message}")
            }
        }
    }

    fun updateHashInput(input: String, forceLower: Boolean = _toolsState.value.forceLowercase) {
        val res = JoaatHasher.hash(input, forceLower)
        _toolsState.update {
            it.copy(
                hashInput = input,
                forceLowercase = forceLower,
                hashResult = res
            )
        }
    }

    fun updateDictionaryFilter(query: String) {
        _toolsState.update {
            it.copy(
                dictionaryFilter = query,
                dictionaryResults = JoaatHasher.lookupByHexOrName(query)
            )
        }
    }

    fun analyzeHeaderFromUri(uri: Uri) {
        viewModelScope.launch {
            try {
                val imported = workspaceRepo.importDocumentUri(uri)
                val bytes = imported.readBytes()
                val magicBytes = bytes.take(4)
                val magicHex = magicBytes.joinToString(" ") { "%02X".format(it) }
                val magicAscii = magicBytes.map { b ->
                    val c = b.toInt() and 0xFF
                    if (c in 32..126) c.toChar() else '.'
                }.joinToString("")

                val details = mutableListOf<Pair<String, String>>()
                val detected = when {
                    Rpf7Engine.isRpfBytes(bytes) -> {
                        val parsed = Rpf7Engine.parseFromBytes(imported.name, bytes)
                        details.add("Versi Arsip" to parsed.header.magic)
                        details.add("Jumlah Entri" to parsed.header.entryCount.toString())
                        details.add("Panjang Tabel Nama" to "${parsed.header.namesLength} byte")
                        details.add("Enkripsi" to parsed.header.encryption.label)
                        "RAGE Package File (${parsed.header.magic})"
                    }
                    DdsAndYtdDecoder.isRsc7(bytes) -> {
                        val rsc = DdsAndYtdDecoder.parseRsc7(bytes)
                        if (rsc != null) {
                            details.add("Versi Resource" to rsc.version.toString())
                            details.add("System Flags" to "0x%08X".format(rsc.systemFlags))
                            details.add("Graphics Flags" to "0x%08X".format(rsc.graphicsFlags))
                            details.add("Virtual Size" to DdsAndYtdDecoder.formatBytes(rsc.virtualSize))
                            details.add("Physical Size" to DdsAndYtdDecoder.formatBytes(rsc.physicalSize))
                        }
                        "RAGE Resource Container (RSC7)"
                    }
                    DdsAndYtdDecoder.isDds(bytes) -> {
                        val dds = DdsAndYtdDecoder.parseDds(bytes)
                        if (dds != null) {
                            details.add("Resolusi" to "${dds.width} × ${dds.height}")
                            details.add("Format Piksel" to dds.fourCc)
                            details.add("Mipmaps" to dds.mipMapCount.toString())
                        }
                        "DirectDraw Surface (DDS)"
                    }
                    else -> {
                        details.add("Ukuran Berkas" to DdsAndYtdDecoder.formatBytes(bytes.size.toLong()))
                        "Berkas Biner / Teks"
                    }
                }

                _toolsState.update {
                    it.copy(
                        headerAnalysis = HeaderAnalysisResult(
                            fileName = imported.name,
                            sizeBytes = bytes.size.toLong(),
                            magicHex = magicHex,
                            magicAscii = magicAscii,
                            detectedFormat = detected,
                            details = details
                        )
                    )
                }
            } catch (e: Exception) {
                emitMessage("Gagal menganalisis header: ${e.message}")
            }
        }
    }

    private fun refreshCurrentListing() {
        val state = _explorerState.value
        val rpf = state.activeRpfFile
        if (rpf != null && rpf.exists()) {
            openRpfArchive(rpf, state.activeRpfSubPath)
        } else {
            openFsDirectory(state.currentFsDir ?: workspaceRepo.getSandboxDir())
        }
    }

    private fun isProbablyText(name: String, bytes: ByteArray): Boolean {
        val lower = name.lowercase()
        if (lower.endsWith(".xml") || lower.endsWith(".meta") || lower.endsWith(".txt") ||
            lower.endsWith(".ini") || lower.endsWith(".cfg") || lower.endsWith(".dat") ||
            lower.endsWith(".log") || lower.endsWith(".json")
        ) return true
        if (bytes.isEmpty()) return true
        val sampleSize = bytes.size.coerceAtMost(512)
        var nonText = 0
        for (i in 0 until sampleSize) {
            val b = bytes[i].toInt() and 0xFF
            if (b == 0) return false
            if (b < 9 || (b in 14..31)) nonText++
        }
        return nonText * 10 < sampleSize
    }

    private fun emitMessage(msg: String) {
        viewModelScope.launch { _snackbarEvents.emit(msg) }
    }
}
