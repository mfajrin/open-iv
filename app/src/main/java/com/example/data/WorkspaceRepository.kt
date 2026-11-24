package com.example.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.core.MutableRpfItem
import com.example.core.OivPackageEngine
import com.example.core.OivPackageInfo
import com.example.core.ParsedRpfArchive
import com.example.core.Rpf7Engine
import com.example.core.RpfEncryption
import com.example.core.RpfEntryNode
import com.example.core.SampleWorkspaceGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

enum class ExplorerItemKind {
    FOLDER,
    RPF_ARCHIVE,
    OIV_PACKAGE,
    XML_META,
    TEXTURE_DDS,
    TEXTURE_YTD,
    MODEL_3D,
    TEXT_CONFIG,
    BINARY
}

data class ExplorerItem(
    val name: String,
    val path: String,
    val kind: ExplorerItemKind,
    val sizeBytes: Long,
    val isInsideRpf: Boolean,
    val rpfArchiveFilePath: String? = null,
    val rpfEntryPath: String? = null,
    val isCompressed: Boolean = false,
    val isResource: Boolean = false,
    val isEncrypted: Boolean = false,
    val badgeLabel: String = ""
)

data class DirectoryListing(
    val title: String,
    val locationSegments: List<Pair<String, String>>,
    val items: List<ExplorerItem>,
    val activeRpfHeaderInfo: String? = null,
    val isActiveRpfWritable: Boolean = true,
    val activeRpfEncryptionNote: String? = null
)

class WorkspaceRepository(private val context: Context) {

    val workspacesRoot: File = File(context.filesDir, "workspaces").apply { mkdirs() }
    val importsRoot: File = File(context.filesDir, "imports").apply { mkdirs() }
    val backupsRoot: File = File(context.filesDir, "backups").apply { mkdirs() }

    suspend fun initSandbox(forceReset: Boolean = false): File = withContext(Dispatchers.IO) {
        SampleWorkspaceGenerator.ensureSandboxInitialized(workspacesRoot, forceReset)
    }

    fun getSandboxDir(): File = File(workspacesRoot, "gtav_sandbox")

    suspend fun listDirectory(
        fsDir: File,
        rpfFile: File? = null,
        rpfSubPath: String = ""
    ): DirectoryListing = withContext(Dispatchers.IO) {
        if (rpfFile != null) {
            listRpfDirectory(rpfFile, rpfSubPath)
        } else {
            listFileSystemDirectory(fsDir)
        }
    }

    private fun listFileSystemDirectory(dir: File): DirectoryListing {
        val sandboxRoot = getSandboxDir()
        val files = (dir.listFiles() ?: emptyArray())
            .filter { !it.name.startsWith(".") }
            .sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })

        val items = files.map { f ->
            val kind = when {
                f.isDirectory -> ExplorerItemKind.FOLDER
                f.name.endsWith(".rpf", ignoreCase = true) -> ExplorerItemKind.RPF_ARCHIVE
                f.name.endsWith(".oiv", ignoreCase = true) -> ExplorerItemKind.OIV_PACKAGE
                else -> classifyExtension(f.name)
            }
            ExplorerItem(
                name = f.name,
                path = f.absolutePath,
                kind = kind,
                sizeBytes = if (f.isDirectory) (f.listFiles()?.count { !it.name.startsWith(".") }?.toLong() ?: 0L) else f.length(),
                isInsideRpf = false,
                badgeLabel = when (kind) {
                    ExplorerItemKind.FOLDER -> "FOLDER"
                    ExplorerItemKind.RPF_ARCHIVE -> "RPF7"
                    ExplorerItemKind.OIV_PACKAGE -> "OIV"
                    else -> f.extension.uppercase().ifEmpty { "BIN" }
                }
            )
        }

        val segments = buildFsBreadcrumbs(dir, sandboxRoot)
        return DirectoryListing(
            title = dir.name.ifEmpty { "Workspace" },
            locationSegments = segments,
            items = items,
            activeRpfHeaderInfo = null,
            isActiveRpfWritable = true
        )
    }

    private fun listRpfDirectory(rpfFile: File, subPath: String): DirectoryListing {
        val parsed = Rpf7Engine.parseFromFile(rpfFile)
        val header = parsed.header
        val headerSummary = "${header.magic} • ${header.entryCount} entri • ${header.encryption.label}"
        val isEncryptedLocked = header.encryption == RpfEncryption.AES || header.encryption == RpfEncryption.NG

        if (isEncryptedLocked) {
            return DirectoryListing(
                title = rpfFile.name,
                locationSegments = buildFsBreadcrumbs(rpfFile, getSandboxDir()),
                items = emptyList(),
                activeRpfHeaderInfo = headerSummary,
                isActiveRpfWritable = false,
                activeRpfEncryptionNote = "Arsip ini menggunakan enkripsi ${header.encryption.label}. Header & jumlah entri berhasil dibaca, tetapi konten retail GTA V terenkripsi tidak dapat didekripsi tanpa tabel kunci game PC."
            )
        }

        val targetDir = findDirInParsed(parsed.root, subPath) ?: parsed.root
        val sortedChildren = targetDir.children.sortedWith(
            compareBy<RpfEntryNode> { it !is RpfEntryNode.DirectoryNode }.thenBy { it.name.lowercase() }
        )

        val items = sortedChildren.map { node ->
            when (node) {
                is RpfEntryNode.DirectoryNode -> ExplorerItem(
                    name = node.name,
                    path = node.fullPath,
                    kind = ExplorerItemKind.FOLDER,
                    sizeBytes = node.children.size.toLong(),
                    isInsideRpf = true,
                    rpfArchiveFilePath = rpfFile.absolutePath,
                    rpfEntryPath = node.fullPath,
                    badgeLabel = "DIR"
                )
                is RpfEntryNode.FileNode -> {
                    val kind = classifyExtension(node.name)
                    ExplorerItem(
                        name = node.name,
                        path = node.fullPath,
                        kind = kind,
                        sizeBytes = node.uncompressedSize.toLong().coerceAtLeast(node.sizeInArchive.toLong()),
                        isInsideRpf = true,
                        rpfArchiveFilePath = rpfFile.absolutePath,
                        rpfEntryPath = node.fullPath,
                        isCompressed = node.isCompressed,
                        isResource = node.isResource,
                        isEncrypted = node.isEncrypted,
                        badgeLabel = when {
                            node.isResource -> "RSC7"
                            node.isCompressed -> "DEFLATE"
                            else -> node.name.substringAfterLast('.', "BIN").uppercase()
                        }
                    )
                }
            }
        }

        val fsSegments = buildFsBreadcrumbs(rpfFile, getSandboxDir()).toMutableList()
        if (subPath.isNotBlank()) {
            var acc = ""
            for (part in subPath.split("/").filter { it.isNotBlank() }) {
                acc = if (acc.isEmpty()) part else "$acc/$part"
                fsSegments.add(part to "rpf://${rpfFile.absolutePath}#$acc")
            }
        }

        return DirectoryListing(
            title = if (subPath.isBlank()) rpfFile.name else subPath.substringAfterLast('/'),
            locationSegments = fsSegments,
            items = items,
            activeRpfHeaderInfo = headerSummary,
            isActiveRpfWritable = header.encryption == RpfEncryption.OPEN || header.encryption == RpfEncryption.NONE
        )
    }

    private fun buildFsBreadcrumbs(target: File, sandboxRoot: File): List<Pair<String, String>> {
        val list = mutableListOf<Pair<String, String>>()
        val rootPath = sandboxRoot.absolutePath
        val targetPath = target.absolutePath
        if (targetPath.startsWith(rootPath)) {
            list.add("Sandbox" to rootPath)
            val rel = targetPath.removePrefix(rootPath).trimStart('/')
            if (rel.isNotEmpty()) {
                var cur = rootPath
                for (seg in rel.split('/')) {
                    cur = "$cur/$seg"
                    list.add(seg to cur)
                }
            }
        } else {
            list.add("Impor" to importsRoot.absolutePath)
            list.add(target.name to target.absolutePath)
        }
        return list
    }

    private fun findDirInParsed(root: RpfEntryNode.DirectoryNode, subPath: String): RpfEntryNode.DirectoryNode? {
        if (subPath.isBlank()) return root
        val parts = subPath.split('/').filter { it.isNotBlank() }
        var cur: RpfEntryNode.DirectoryNode = root
        for (p in parts) {
            val next = cur.children.filterIsInstance<RpfEntryNode.DirectoryNode>()
                .firstOrNull { it.name.equals(p, ignoreCase = true) } ?: return null
            cur = next
        }
        return cur
    }

    suspend fun readExplorerItemBytes(item: ExplorerItem): ByteArray = withContext(Dispatchers.IO) {
        if (!item.isInsideRpf) {
            File(item.path).readBytes()
        } else {
            val rpfFile = File(item.rpfArchiveFilePath ?: error("Path RPF tidak ditemukan"))
            val archiveBytes = rpfFile.readBytes()
            val parsed = Rpf7Engine.parseFromBytes(rpfFile.name, archiveBytes)
            val node = parsed.flatEntries.filterIsInstance<RpfEntryNode.FileNode>()
                .firstOrNull { it.fullPath == item.rpfEntryPath }
                ?: error("Entri '${item.name}' tidak ditemukan di dalam ${rpfFile.name}")
            Rpf7Engine.extractEntryBytes(archiveBytes, node)
        }
    }

    suspend fun writeExplorerItemBytes(
        item: ExplorerItem,
        newBytes: ByteArray,
        createBackup: Boolean
    ) = withContext(Dispatchers.IO) {
        if (!item.isInsideRpf) {
            val f = File(item.path)
            if (createBackup && f.exists()) backupFile(f)
            f.writeBytes(newBytes)
        } else {
            val rpfFile = File(item.rpfArchiveFilePath ?: error("Path RPF tidak ditemukan"))
            if (createBackup) backupFile(rpfFile)
            val archiveBytes = rpfFile.readBytes()
            val parsed = Rpf7Engine.parseFromBytes(rpfFile.name, archiveBytes)
            val tree = Rpf7Engine.toMutableTree(archiveBytes, parsed)
            replaceBlobInTree(tree, item.rpfEntryPath ?: item.name, newBytes)
            rpfFile.writeBytes(Rpf7Engine.buildRpf7Bytes(tree))
        }
    }

    suspend fun injectFileIntoCurrentLocation(
        fsDir: File,
        rpfFile: File?,
        rpfSubPath: String,
        fileName: String,
        data: ByteArray,
        createBackup: Boolean
    ) = withContext(Dispatchers.IO) {
        if (rpfFile == null) {
            fsDir.mkdirs()
            File(fsDir, fileName).writeBytes(data)
        } else {
            if (createBackup) backupFile(rpfFile)
            val archiveBytes = rpfFile.readBytes()
            val parsed = Rpf7Engine.parseFromBytes(rpfFile.name, archiveBytes)
            val tree = Rpf7Engine.toMutableTree(archiveBytes, parsed)
            val targetFolder = findOrCreateMutableFolder(tree, rpfSubPath)
            targetFolder.children.removeAll { it.name.equals(fileName, ignoreCase = true) }
            targetFolder.children.add(MutableRpfItem.Blob(fileName, data))
            rpfFile.writeBytes(Rpf7Engine.buildRpf7Bytes(tree))
        }
    }

    suspend fun createFolderInLocation(
        fsDir: File,
        rpfFile: File?,
        rpfSubPath: String,
        folderName: String,
        createBackup: Boolean
    ) = withContext(Dispatchers.IO) {
        if (rpfFile == null) {
            File(fsDir, folderName).mkdirs()
        } else {
            if (createBackup) backupFile(rpfFile)
            val archiveBytes = rpfFile.readBytes()
            val parsed = Rpf7Engine.parseFromBytes(rpfFile.name, archiveBytes)
            val tree = Rpf7Engine.toMutableTree(archiveBytes, parsed)
            val targetFolder = findOrCreateMutableFolder(tree, rpfSubPath)
            if (targetFolder.children.none { it.name.equals(folderName, ignoreCase = true) }) {
                targetFolder.children.add(MutableRpfItem.Folder(folderName))
            }
            rpfFile.writeBytes(Rpf7Engine.buildRpf7Bytes(tree))
        }
    }

    suspend fun deleteExplorerItem(item: ExplorerItem, createBackup: Boolean) = withContext(Dispatchers.IO) {
        if (!item.isInsideRpf) {
            val f = File(item.path)
            if (createBackup && f.isFile) backupFile(f)
            f.deleteRecursively()
        } else {
            val rpfFile = File(item.rpfArchiveFilePath ?: return@withContext)
            if (createBackup) backupFile(rpfFile)
            val archiveBytes = rpfFile.readBytes()
            val parsed = Rpf7Engine.parseFromBytes(rpfFile.name, archiveBytes)
            val tree = Rpf7Engine.toMutableTree(archiveBytes, parsed)
            deleteFromMutableTree(tree, item.rpfEntryPath ?: item.name)
            rpfFile.writeBytes(Rpf7Engine.buildRpf7Bytes(tree))
        }
    }

    suspend fun renameExplorerItem(item: ExplorerItem, newName: String, createBackup: Boolean) = withContext(Dispatchers.IO) {
        if (!item.isInsideRpf) {
            val f = File(item.path)
            f.renameTo(File(f.parentFile, newName))
        } else {
            val rpfFile = File(item.rpfArchiveFilePath ?: return@withContext)
            if (createBackup) backupFile(rpfFile)
            val archiveBytes = rpfFile.readBytes()
            val parsed = Rpf7Engine.parseFromBytes(rpfFile.name, archiveBytes)
            val tree = Rpf7Engine.toMutableTree(archiveBytes, parsed)
            renameInMutableTree(tree, item.rpfEntryPath ?: item.name, newName)
            rpfFile.writeBytes(Rpf7Engine.buildRpf7Bytes(tree))
        }
    }

    suspend fun createEmptyRpf7Archive(targetDir: File, fileName: String): File = withContext(Dispatchers.IO) {
        val cleanName = if (fileName.endsWith(".rpf", ignoreCase = true)) fileName else "$fileName.rpf"
        targetDir.mkdirs()
        val outFile = File(targetDir, cleanName)
        val root = MutableRpfItem.Folder("").apply {
            children.add(
                MutableRpfItem.Blob(
                    "manifest.txt",
                    "Created with OpenV Studio\nFormat: RPF7 OPEN\n".toByteArray(Charsets.UTF_8)
                )
            )
        }
        outFile.writeBytes(Rpf7Engine.buildRpf7Bytes(root))
        outFile
    }

    suspend fun importDocumentUri(uri: Uri): File = withContext(Dispatchers.IO) {
        val displayName = resolveDisplayName(uri) ?: "imported_${System.currentTimeMillis()}.bin"
        val destFile = File(importsRoot, displayName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: error("Gagal membaca berkas dari penyedia dokumen.")
        destFile
    }

    suspend fun exportBytesToUri(uri: Uri, bytes: ByteArray) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(bytes)
        } ?: error("Gagal menulis berkas ekspor.")
    }

    suspend fun scanAllOivPackages(): List<OivPackageInfo> = withContext(Dispatchers.IO) {
        val files = mutableListOf<File>()
        fun walk(dir: File) {
            dir.listFiles()?.forEach { f ->
                if (f.isDirectory) walk(f)
                else if (f.name.endsWith(".oiv", ignoreCase = true)) files.add(f)
            }
        }
        walk(workspacesRoot)
        walk(importsRoot)
        files.mapNotNull { f ->
            try {
                OivPackageEngine.parseOivFile(f)
            } catch (_: Exception) {
                null
            }
        }
    }

    suspend fun installOivPackage(pkg: OivPackageInfo): InstalledModRecord = withContext(Dispatchers.IO) {
        val oivFile = File(pkg.filePath)
        val sandbox = getSandboxDir()
        var targetArchiveFile = File(sandbox, "update/update.rpf")
        if (pkg.operations.isNotEmpty()) {
            val firstTarget = pkg.operations.first().archiveTarget
            if (firstTarget.isNotBlank()) {
                val candidate = File(sandbox, firstTarget)
                if (candidate.exists()) targetArchiveFile = candidate
            }
        }

        val backupFile = backupFile(targetArchiveFile)

        if (targetArchiveFile.exists() && Rpf7Engine.isRpfFile(targetArchiveFile)) {
            val archiveBytes = targetArchiveFile.readBytes()
            val parsed = Rpf7Engine.parseFromBytes(targetArchiveFile.name, archiveBytes)
            val tree = Rpf7Engine.toMutableTree(archiveBytes, parsed)

            for (op in pkg.operations) {
                when (op.action) {
                    "ADD", "REPLACE" -> {
                        val contentBytes = OivPackageEngine.readEntryFromOiv(oivFile, op.sourceContentPath)
                        if (contentBytes != null) {
                            val destPath = op.destinationPath.ifBlank { op.sourceContentPath.removePrefix("content/") }
                            putBlobAtPath(tree, destPath, contentBytes)
                        }
                    }
                    "DELETE" -> {
                        if (op.destinationPath.isNotBlank()) {
                            deleteFromMutableTree(tree, op.destinationPath)
                        }
                    }
                }
            }
            targetArchiveFile.writeBytes(Rpf7Engine.buildRpf7Bytes(tree))
        }

        InstalledModRecord(
            id = pkg.id,
            name = pkg.name,
            version = pkg.version,
            author = pkg.author,
            targetArchive = targetArchiveFile.absolutePath,
            backupPath = backupFile.absolutePath,
            installedAtMillis = System.currentTimeMillis()
        )
    }

    suspend fun uninstallOivMod(record: InstalledModRecord) = withContext(Dispatchers.IO) {
        val backup = File(record.backupPath)
        val target = File(record.targetArchive)
        if (backup.exists()) {
            backup.copyTo(target, overwrite = true)
            backup.delete()
        }
    }

    suspend fun searchInsideArchiveOrWorkspace(
        fsDir: File,
        rpfFile: File?,
        query: String
    ): List<ExplorerItem> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        if (rpfFile != null) {
            val parsed = Rpf7Engine.parseFromFile(rpfFile)
            parsed.flatEntries.filter { it.name.contains(query, ignoreCase = true) || it.fullPath.contains(query, ignoreCase = true) }
                .take(80)
                .map { node ->
                    when (node) {
                        is RpfEntryNode.DirectoryNode -> ExplorerItem(
                            name = node.name,
                            path = node.fullPath,
                            kind = ExplorerItemKind.FOLDER,
                            sizeBytes = node.children.size.toLong(),
                            isInsideRpf = true,
                            rpfArchiveFilePath = rpfFile.absolutePath,
                            rpfEntryPath = node.fullPath,
                            badgeLabel = "DIR"
                        )
                        is RpfEntryNode.FileNode -> ExplorerItem(
                            name = node.name,
                            path = node.fullPath,
                            kind = classifyExtension(node.name),
                            sizeBytes = node.uncompressedSize.toLong().coerceAtLeast(node.sizeInArchive.toLong()),
                            isInsideRpf = true,
                            rpfArchiveFilePath = rpfFile.absolutePath,
                            rpfEntryPath = node.fullPath,
                            isCompressed = node.isCompressed,
                            isResource = node.isResource,
                            badgeLabel = node.fullPath
                        )
                    }
                }
        } else {
            val results = mutableListOf<ExplorerItem>()
            fun walk(d: File) {
                d.listFiles()?.forEach { f ->
                    if (f.name.startsWith(".")) return@forEach
                    if (f.name.contains(query, ignoreCase = true)) {
                        results.add(
                            ExplorerItem(
                                name = f.name,
                                path = f.absolutePath,
                                kind = if (f.isDirectory) ExplorerItemKind.FOLDER else classifyExtension(f.name),
                                sizeBytes = if (f.isDirectory) 0L else f.length(),
                                isInsideRpf = false,
                                badgeLabel = f.parentFile?.name ?: "DIR"
                            )
                        )
                    }
                    if (f.isDirectory) walk(f)
                }
            }
            walk(fsDir)
            results.take(80)
        }
    }

    private fun backupFile(file: File): File {
        val bak = File(backupsRoot, "${file.name}.${System.currentTimeMillis()}.bak")
        if (file.exists()) file.copyTo(bak, overwrite = true)
        return bak
    }

    private fun resolveDisplayName(uri: Uri): String? {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) return cursor.getString(idx)
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')
    }

    private fun findOrCreateMutableFolder(root: MutableRpfItem.Folder, path: String): MutableRpfItem.Folder {
        if (path.isBlank()) return root
        var cur = root
        for (part in path.split('/').filter { it.isNotBlank() }) {
            val existing = cur.children.filterIsInstance<MutableRpfItem.Folder>()
                .firstOrNull { it.name.equals(part, ignoreCase = true) }
            cur = if (existing != null) {
                existing
            } else {
                MutableRpfItem.Folder(part).also { cur.children.add(it) }
            }
        }
        return cur
    }

    private fun putBlobAtPath(root: MutableRpfItem.Folder, path: String, data: ByteArray) {
        val clean = path.trimStart('/')
        val parts = clean.split('/').filter { it.isNotBlank() }
        if (parts.isEmpty()) return
        val folderPath = parts.dropLast(1).joinToString("/")
        val fileName = parts.last()
        val folder = findOrCreateMutableFolder(root, folderPath)
        val existing = folder.children.filterIsInstance<MutableRpfItem.Blob>()
            .firstOrNull { it.name.equals(fileName, ignoreCase = true) }
        if (existing != null) {
            existing.data = data
        } else {
            folder.children.add(MutableRpfItem.Blob(fileName, data))
        }
    }

    private fun replaceBlobInTree(root: MutableRpfItem.Folder, path: String, newBytes: ByteArray) {
        putBlobAtPath(root, path, newBytes)
    }

    private fun deleteFromMutableTree(root: MutableRpfItem.Folder, path: String): Boolean {
        val parts = path.split('/').filter { it.isNotBlank() }
        if (parts.isEmpty()) return false
        var cur = root
        for (p in parts.dropLast(1)) {
            cur = cur.children.filterIsInstance<MutableRpfItem.Folder>()
                .firstOrNull { it.name.equals(p, ignoreCase = true) } ?: return false
        }
        return cur.children.removeAll { it.name.equals(parts.last(), ignoreCase = true) }
    }

    private fun renameInMutableTree(root: MutableRpfItem.Folder, path: String, newName: String) {
        val parts = path.split('/').filter { it.isNotBlank() }
        if (parts.isEmpty()) return
        var cur = root
        for (p in parts.dropLast(1)) {
            cur = cur.children.filterIsInstance<MutableRpfItem.Folder>()
                .firstOrNull { it.name.equals(p, ignoreCase = true) } ?: return
        }
        cur.children.firstOrNull { it.name.equals(parts.last(), ignoreCase = true) }?.name = newName
    }

    fun classifyExtension(name: String): ExplorerItemKind {
        val lower = name.lowercase()
        return when {
            lower.endsWith(".rpf") -> ExplorerItemKind.RPF_ARCHIVE
            lower.endsWith(".oiv") -> ExplorerItemKind.OIV_PACKAGE
            lower.endsWith(".xml") || lower.endsWith(".meta") -> ExplorerItemKind.XML_META
            lower.endsWith(".dds") || lower.endsWith(".png") || lower.endsWith(".jpg") -> ExplorerItemKind.TEXTURE_DDS
            lower.endsWith(".ytd") -> ExplorerItemKind.TEXTURE_YTD
            lower.endsWith(".ydr") || lower.endsWith(".yft") || lower.endsWith(".ydd") || lower.endsWith(".ymap") || lower.endsWith(".ytyp") -> ExplorerItemKind.MODEL_3D
            lower.endsWith(".txt") || lower.endsWith(".ini") || lower.endsWith(".cfg") || lower.endsWith(".dat") || lower.endsWith(".asi") -> ExplorerItemKind.TEXT_CONFIG
            else -> ExplorerItemKind.BINARY
        }
    }
}
