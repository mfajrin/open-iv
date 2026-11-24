package com.example.core

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.Deflater
import java.util.zip.Inflater

enum class RpfEncryption(val label: String) {
    OPEN("OPEN (Tidak Terenkripsi)"),
    NONE("NONE (Tidak Terenkripsi)"),
    AES("AES-256 (Retail GTA V)"),
    NG("NG Whitebox (Retail GTA V)"),
    UNKNOWN("Tidak Dikenal")
}

sealed class RpfEntryNode {
    abstract val index: Int
    abstract val name: String
    abstract val fullPath: String

    data class DirectoryNode(
        override val index: Int,
        override val name: String,
        override val fullPath: String,
        val entriesIndex: Int,
        val entriesCount: Int,
        val children: MutableList<RpfEntryNode> = mutableListOf()
    ) : RpfEntryNode()

    data class FileNode(
        override val index: Int,
        override val name: String,
        override val fullPath: String,
        val offsetBytes: Long,
        val sizeInArchive: Int,
        val uncompressedSize: Int,
        val isResource: Boolean,
        val isCompressed: Boolean,
        val isEncrypted: Boolean,
        val systemFlags: Int = 0,
        val graphicsFlags: Int = 0
    ) : RpfEntryNode()
}

data class RpfArchiveHeader(
    val magic: String,
    val entryCount: Int,
    val namesLength: Int,
    val encryption: RpfEncryption,
    val rawEncryptionFlag: Int
)

data class ParsedRpfArchive(
    val sourceName: String,
    val header: RpfArchiveHeader,
    val root: RpfEntryNode.DirectoryNode,
    val flatEntries: List<RpfEntryNode>
)

/**
 * In-memory mutable tree used when editing/repacking an OPEN RPF7 archive.
 */
sealed class MutableRpfItem {
    abstract var name: String

    data class Folder(
        override var name: String,
        val children: MutableList<MutableRpfItem> = mutableListOf()
    ) : MutableRpfItem()

    data class Blob(
        override var name: String,
        var data: ByteArray,
        var isResource: Boolean = false,
        var systemFlags: Int = 0,
        var graphicsFlags: Int = 0,
        var compressBinary: Boolean = true
    ) : MutableRpfItem()
}

object Rpf7Engine {

    private const val DIR_OFFSET_SENTINEL = 0x7FFFFF

    fun isRpfFile(file: File): Boolean {
        if (!file.exists() || file.length() < 16) return false
        return RandomAccessFile(file, "r").use { raf ->
            val magic = ByteArray(4)
            raf.readFully(magic)
            val s = String(magic, Charsets.US_ASCII)
            s.startsWith("RPF")
        }
    }

    fun isRpfBytes(bytes: ByteArray): Boolean {
        if (bytes.size < 16) return false
        val s = String(bytes, 0, 4, Charsets.US_ASCII)
        return s.startsWith("RPF")
    }

    fun parseFromFile(file: File): ParsedRpfArchive {
        val bytes = file.readBytes()
        return parseFromBytes(file.name, bytes)
    }

    fun parseFromBytes(sourceName: String, bytes: ByteArray): ParsedRpfArchive {
        require(bytes.size >= 16) { "Berkas terlalu kecil untuk header RPF (< 16 byte)." }
        val magic = String(bytes, 0, 4, Charsets.US_ASCII)
        require(magic == "RPF7") { "Format arsip '$magic' terdeteksi. Saat ini parser penuh mendukung RPF7 (GTA V)." }

        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        bb.position(4)
        val entryCount = bb.int
        val namesLength = bb.int
        val rawEnc = bb.int

        val encryption = when (rawEnc) {
            0x4E45504F -> RpfEncryption.OPEN
            0 -> RpfEncryption.NONE
            0x0FFFFFF9 -> RpfEncryption.AES
            0x0FEFFFFF -> RpfEncryption.NG
            else -> RpfEncryption.UNKNOWN
        }

        val header = RpfArchiveHeader(
            magic = magic,
            entryCount = entryCount,
            namesLength = namesLength,
            encryption = encryption,
            rawEncryptionFlag = rawEnc
        )

        val tocBytesEnd = 16 + entryCount * 16
        val namesStart = tocBytesEnd
        if (encryption == RpfEncryption.AES || encryption == RpfEncryption.NG || bytes.size < namesStart + namesLength) {
            val emptyRoot = RpfEntryNode.DirectoryNode(0, "", "", 0, 0)
            return ParsedRpfArchive(sourceName, header, emptyRoot, emptyList())
        }

        val namesBlock = bytes.copyOfRange(namesStart, namesStart + namesLength)

        fun readName(offset: Int): String {
            if (offset < 0 || offset >= namesBlock.size) return "entry_$offset"
            var end = offset
            while (end < namesBlock.size && namesBlock[end] != 0.toByte()) end++
            return String(namesBlock, offset, end - offset, Charsets.UTF_8)
        }

        // First pass: parse raw entries
        val rawNodes = ArrayList<RpfEntryNode>(entryCount)
        for (i in 0 until entryCount) {
            val pos = 16 + i * 16
            val val0 = bb.getLong(pos)
            val val1 = bb.getLong(pos + 8)

            val nameOffset = (val0 and 0xFFFFL).toInt()
            val sizeInArchive = ((val0 ushr 16) and 0xFFFFFFL).toInt()
            val offsetBlocks = ((val0 ushr 40) and 0x7FFFFFL).toInt()
            val isResource = ((val0 ushr 63) and 1L) == 1L
            val name = readName(nameOffset).ifEmpty { if (i == 0) "/" else "item_$i" }

            if (!isResource && offsetBlocks == DIR_OFFSET_SENTINEL) {
                val entriesIdx = (val1 and 0xFFFFFFFFL).toInt()
                val entriesCnt = ((val1 ushr 32) and 0xFFFFFFFFL).toInt()
                rawNodes.add(
                    RpfEntryNode.DirectoryNode(
                        index = i,
                        name = if (i == 0) "" else name,
                        fullPath = "",
                        entriesIndex = entriesIdx,
                        entriesCount = entriesCnt
                    )
                )
            } else if (isResource) {
                val sysFlags = (val1 and 0xFFFFFFFFL).toInt()
                val gfxFlags = ((val1 ushr 32) and 0xFFFFFFFFL).toInt()
                rawNodes.add(
                    RpfEntryNode.FileNode(
                        index = i,
                        name = name,
                        fullPath = "",
                        offsetBytes = offsetBlocks.toLong() * 512L,
                        sizeInArchive = sizeInArchive,
                        uncompressedSize = sizeInArchive,
                        isResource = true,
                        isCompressed = false,
                        isEncrypted = false,
                        systemFlags = sysFlags,
                        graphicsFlags = gfxFlags
                    )
                )
            } else {
                val uncompressed = (val1 and 0xFFFFFFFFL).toInt()
                val encFlag = ((val1 ushr 32) and 0xFFFFFFFFL).toInt()
                val compressed = sizeInArchive > 0 && uncompressed > 0 && sizeInArchive != uncompressed
                rawNodes.add(
                    RpfEntryNode.FileNode(
                        index = i,
                        name = name,
                        fullPath = "",
                        offsetBytes = offsetBlocks.toLong() * 512L,
                        sizeInArchive = if (sizeInArchive == 0) uncompressed else sizeInArchive,
                        uncompressedSize = uncompressed,
                        isResource = false,
                        isCompressed = compressed,
                        isEncrypted = encFlag != 0
                    )
                )
            }
        }

        if (rawNodes.isEmpty() || rawNodes[0] !is RpfEntryNode.DirectoryNode) {
            val fallback = RpfEntryNode.DirectoryNode(0, "", "", 0, 0)
            return ParsedRpfArchive(sourceName, header, fallback, emptyList())
        }

        fun buildTree(dir: RpfEntryNode.DirectoryNode, parentPath: String): RpfEntryNode.DirectoryNode {
            val resolvedDirPath = if (dir.index == 0) "" else if (parentPath.isEmpty()) dir.name else "$parentPath/${dir.name}"
            val rebuilt = dir.copy(fullPath = resolvedDirPath)
            val start = dir.entriesIndex
            val end = (start + dir.entriesCount).coerceAtMost(rawNodes.size)
            for (idx in start until end) {
                if (idx == dir.index) continue
                when (val child = rawNodes[idx]) {
                    is RpfEntryNode.DirectoryNode -> {
                        rebuilt.children.add(buildTree(child, resolvedDirPath))
                    }
                    is RpfEntryNode.FileNode -> {
                        val childPath = if (resolvedDirPath.isEmpty()) child.name else "$resolvedDirPath/${child.name}"
                        rebuilt.children.add(child.copy(fullPath = childPath))
                    }
                }
            }
            return rebuilt
        }

        val root = buildTree(rawNodes[0] as RpfEntryNode.DirectoryNode, "")
        val flat = mutableListOf<RpfEntryNode>()
        fun collectFlat(d: RpfEntryNode.DirectoryNode) {
            for (c in d.children) {
                flat.add(c)
                if (c is RpfEntryNode.DirectoryNode) collectFlat(c)
            }
        }
        collectFlat(root)

        return ParsedRpfArchive(sourceName, header, root, flat)
    }

    fun extractEntryBytes(archiveBytes: ByteArray, entry: RpfEntryNode.FileNode): ByteArray {
        if (entry.isEncrypted) {
            throw IllegalStateException("Entri '${entry.name}' terenkripsi dan membutuhkan kunci retail GTA V.")
        }
        val start = entry.offsetBytes.toInt()
        val len = if (entry.sizeInArchive > 0) entry.sizeInArchive else entry.uncompressedSize
        if (start < 0 || start >= archiveBytes.size || len <= 0) return ByteArray(0)
        val end = (start + len).coerceAtMost(archiveBytes.size)
        val slice = archiveBytes.copyOfRange(start, end)

        if (!entry.isResource && entry.isCompressed) {
            return inflateRaw(slice, entry.uncompressedSize)
        }
        return slice
    }

    private fun inflateRaw(compressed: ByteArray, expectedSize: Int): ByteArray {
        val inflater = Inflater(true)
        inflater.setInput(compressed)
        val out = ByteArrayOutputStream(expectedSize.coerceAtLeast(1024))
        val buf = ByteArray(4096)
        try {
            while (!inflater.finished()) {
                val read = inflater.inflate(buf)
                if (read == 0) {
                    if (inflater.needsInput()) break
                } else {
                    out.write(buf, 0, read)
                }
            }
        } finally {
            inflater.end()
        }
        val result = out.toByteArray()
        return if (result.isNotEmpty()) result else compressed
    }

    private fun deflateRaw(raw: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.DEFAULT_COMPRESSION, true)
        deflater.setInput(raw)
        deflater.finish()
        val out = ByteArrayOutputStream()
        val buf = ByteArray(4096)
        while (!deflater.finished()) {
            val count = deflater.deflate(buf)
            out.write(buf, 0, count)
        }
        deflater.end()
        return out.toByteArray()
    }

    fun toMutableTree(archiveBytes: ByteArray, parsed: ParsedRpfArchive): MutableRpfItem.Folder {
        fun convertDir(dir: RpfEntryNode.DirectoryNode): MutableRpfItem.Folder {
            val folder = MutableRpfItem.Folder(dir.name)
            for (child in dir.children) {
                when (child) {
                    is RpfEntryNode.DirectoryNode -> folder.children.add(convertDir(child))
                    is RpfEntryNode.FileNode -> {
                        val rawData = extractEntryBytes(archiveBytes, child)
                        folder.children.add(
                            MutableRpfItem.Blob(
                                name = child.name,
                                data = rawData,
                                isResource = child.isResource,
                                systemFlags = child.systemFlags,
                                graphicsFlags = child.graphicsFlags,
                                compressBinary = child.isCompressed
                            )
                        )
                    }
                }
            }
            return folder
        }
        return convertDir(parsed.root)
    }

    /**
     * Serializes a MutableRpfItem.Folder into a valid OPEN RPF7 binary byte array.
     */
    fun buildRpf7Bytes(root: MutableRpfItem.Folder): ByteArray {
        // Assign BFS indices so children of each directory are contiguous
        class IndexedEntry(
            val item: MutableRpfItem,
            var entriesIndex: Int = 0,
            var entriesCount: Int = 0,
            var nameOffset: Int = 0,
            var payload: ByteArray = ByteArray(0),
            var uncompressedSize: Int = 0,
            var offsetBlocks: Int = DIR_OFFSET_SENTINEL
        )

        val entries = mutableListOf<IndexedEntry>()
        val queue = ArrayDeque<IndexedEntry>()
        val rootEntry = IndexedEntry(root)
        entries.add(rootEntry)
        queue.add(rootEntry)

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            val folder = current.item as MutableRpfItem.Folder
            val sortedChildren = folder.children.sortedBy { it.name.lowercase() }
            current.entriesIndex = entries.size
            current.entriesCount = sortedChildren.size
            for (child in sortedChildren) {
                val childIndexed = IndexedEntry(child)
                entries.add(childIndexed)
                if (child is MutableRpfItem.Folder) {
                    queue.add(childIndexed)
                }
            }
        }

        // Build null-terminated name table
        val namesOut = ByteArrayOutputStream()
        for ((idx, entry) in entries.withIndex()) {
            entry.nameOffset = namesOut.size()
            val nameBytes = if (idx == 0) ByteArray(0) else entry.item.name.toByteArray(Charsets.UTF_8)
            namesOut.write(nameBytes)
            namesOut.write(0)
        }
        // Pad name table to 16-byte alignment
        while (namesOut.size() % 16 != 0) {
            namesOut.write(0)
        }
        val namesBytes = namesOut.toByteArray()

        val headerAndTocSize = 16 + entries.size * 16 + namesBytes.size
        val dataStartOffset = ((headerAndTocSize + 511) / 512) * 512
        var currentBlock = dataStartOffset / 512

        // Prepare payloads and compute 512-byte sector offsets
        val dataBlocksOut = ByteArrayOutputStream()
        for (entry in entries) {
            val item = entry.item
            if (item is MutableRpfItem.Blob) {
                entry.uncompressedSize = item.data.size
                val stored = if (!item.isResource && item.compressBinary && item.data.size > 64) {
                    val compressed = deflateRaw(item.data)
                    if (compressed.size < item.data.size) compressed else item.data
                } else {
                    item.data
                }
                entry.payload = stored
                entry.offsetBlocks = currentBlock
                dataBlocksOut.write(stored)
                val pad = (512 - (stored.size % 512)) % 512
                if (pad > 0) dataBlocksOut.write(ByteArray(pad))
                currentBlock += (stored.size + 511) / 512
            }
        }

        val totalSize = dataStartOffset + dataBlocksOut.size()
        val out = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)

        // Header (16 bytes)
        out.put("RPF7".toByteArray(Charsets.US_ASCII))
        out.putInt(entries.size)
        out.putInt(namesBytes.size)
        out.putInt(0x4E45504F) // "OPEN"

        // TOC entries
        for (entry in entries) {
            val item = entry.item
            when (item) {
                is MutableRpfItem.Folder -> {
                    val val0 = (entry.nameOffset.toLong() and 0xFFFFL) or
                        (0L shl 16) or
                        (DIR_OFFSET_SENTINEL.toLong() shl 40)
                    val val1 = (entry.entriesIndex.toLong() and 0xFFFFFFFFL) or
                        ((entry.entriesCount.toLong() and 0xFFFFFFFFL) shl 32)
                    out.putLong(val0)
                    out.putLong(val1)
                }
                is MutableRpfItem.Blob -> {
                    val isResBit = if (item.isResource) (1L shl 63) else 0L
                    val sizeField = entry.payload.size.toLong() and 0xFFFFFFL
                    val val0 = (entry.nameOffset.toLong() and 0xFFFFL) or
                        (sizeField shl 16) or
                        ((entry.offsetBlocks.toLong() and 0x7FFFFFL) shl 40) or
                        isResBit
                    val val1 = if (item.isResource) {
                        (item.systemFlags.toLong() and 0xFFFFFFFFL) or
                            ((item.graphicsFlags.toLong() and 0xFFFFFFFFL) shl 32)
                    } else {
                        (entry.uncompressedSize.toLong() and 0xFFFFFFFFL)
                    }
                    out.putLong(val0)
                    out.putLong(val1)
                }
            }
        }

        // Names table
        out.put(namesBytes)

        // Padding to dataStartOffset
        val padToData = dataStartOffset - (16 + entries.size * 16 + namesBytes.size)
        if (padToData > 0) out.put(ByteArray(padToData))

        // Data blocks
        out.put(dataBlocksOut.toByteArray())
        return out.array()
    }
}
