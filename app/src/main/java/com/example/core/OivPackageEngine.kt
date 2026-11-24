package com.example.core

import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory

data class OivOperation(
    val archiveTarget: String,
    val action: String, // ADD, REPLACE, DELETE
    val sourceContentPath: String,
    val destinationPath: String
)

data class OivPackageInfo(
    val fileName: String,
    val filePath: String,
    val id: String,
    val version: String,
    val name: String,
    val author: String,
    val description: String,
    val largeDescription: String,
    val targetFormatVersion: String,
    val operations: List<OivOperation>,
    val contentEntries: List<String>,
    val iconBytes: ByteArray? = null,
    val assemblyXmlRaw: String = ""
)

object OivPackageEngine {

    fun parseOivFile(file: File): OivPackageInfo {
        return parseOivBytes(file.name, file.absolutePath, file.readBytes())
    }

    fun parseOivBytes(fileName: String, filePath: String, zipBytes: ByteArray): OivPackageInfo {
        var assemblyXml = ""
        var iconBytes: ByteArray? = null
        val contentEntries = mutableListOf<String>()

        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val normalized = entry.name.replace('\\', '/')
                    if (normalized.equals("assembly.xml", ignoreCase = true)) {
                        assemblyXml = zis.readBytes().toString(Charsets.UTF_8)
                    } else if (normalized.equals("icon.png", ignoreCase = true) ||
                        normalized.equals("content/icon.png", ignoreCase = true)
                    ) {
                        iconBytes = zis.readBytes()
                    } else {
                        contentEntries.add(normalized)
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        return parseAssemblyXml(
            fileName = fileName,
            filePath = filePath,
            assemblyXml = assemblyXml,
            contentEntries = contentEntries,
            iconBytes = iconBytes
        )
    }

    fun readEntryFromOiv(oivFile: File, targetEntryPath: String): ByteArray? {
        ZipInputStream(oivFile.inputStream().buffered()).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val normalized = entry.name.replace('\\', '/')
                if (normalized.equals(targetEntryPath, ignoreCase = true) ||
                    normalized.equals("content/$targetEntryPath", ignoreCase = true)
                ) {
                    return zis.readBytes()
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        return null
    }

    private fun parseAssemblyXml(
        fileName: String,
        filePath: String,
        assemblyXml: String,
        contentEntries: List<String>,
        iconBytes: ByteArray?
    ): OivPackageInfo {
        if (assemblyXml.isBlank()) {
            return OivPackageInfo(
                fileName = fileName,
                filePath = filePath,
                id = fileName,
                version = "1.0",
                name = fileName.removeSuffix(".oiv"),
                author = "Tidak diketahui",
                description = "Paket ZIP/OIV tanpa assembly.xml",
                largeDescription = "",
                targetFormatVersion = "2.1",
                operations = emptyList(),
                contentEntries = contentEntries,
                iconBytes = iconBytes,
                assemblyXmlRaw = ""
            )
        }

        var id = fileName
        var version = "1.0"
        var pkgName = fileName.removeSuffix(".oiv")
        var author = "Komunitas Modder"
        var description = ""
        var largeDescription = ""
        var formatVersion = "2.1"
        val operations = mutableListOf<OivOperation>()

        try {
            val factory = DocumentBuilderFactory.newInstance()
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(ByteArrayInputStream(assemblyXml.trim().toByteArray(Charsets.UTF_8)))
            val root = doc.documentElement

            if (root != null) {
                id = root.getAttribute("id").ifBlank { id }
                version = root.getAttribute("version").ifBlank { version }
                formatVersion = root.getAttribute("target").ifBlank { formatVersion }

                val metadataList = root.getElementsByTagName("metadata")
                if (metadataList.length > 0) {
                    val metaEl = metadataList.item(0) as? Element
                    if (metaEl != null) {
                        pkgName = firstChildText(metaEl, "name").ifBlank { pkgName }
                        description = firstChildText(metaEl, "description")
                        largeDescription = firstChildText(metaEl, "largeDescription")

                        val authorNodes = metaEl.getElementsByTagName("author")
                        if (authorNodes.length > 0) {
                            val authorEl = authorNodes.item(0) as? Element
                            if (authorEl != null) {
                                author = firstChildText(authorEl, "displayName").ifBlank { author }
                            }
                        }

                        val verNodes = metaEl.getElementsByTagName("version")
                        if (verNodes.length > 0) {
                            val verEl = verNodes.item(0) as? Element
                            if (verEl != null) {
                                val maj = firstChildText(verEl, "major")
                                val min = firstChildText(verEl, "minor")
                                if (maj.isNotBlank()) {
                                    version = if (min.isNotBlank()) "$maj.$min" else maj
                                }
                            }
                        }
                    }
                }

                val archiveNodes = root.getElementsByTagName("archive")
                for (i in 0 until archiveNodes.length) {
                    val archiveEl = archiveNodes.item(i) as? Element ?: continue
                    val archivePath = archiveEl.getAttribute("path").replace('\\', '/')
                    val children = archiveEl.childNodes
                    for (j in 0 until children.length) {
                        val node = children.item(j) as? Element ?: continue
                        val tag = node.tagName.lowercase()
                        val src = node.getAttribute("source").replace('\\', '/')
                        val dest = node.textContent.orEmpty().trim().replace('\\', '/')
                        when (tag) {
                            "add" -> operations.add(OivOperation(archivePath, "ADD", src, dest.ifBlank { src }))
                            "replace" -> operations.add(OivOperation(archivePath, "REPLACE", src, dest.ifBlank { src }))
                            "delete" -> operations.add(OivOperation(archivePath, "DELETE", "", dest))
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Fallback gracefully with partial parsed metadata
        }

        return OivPackageInfo(
            fileName = fileName,
            filePath = filePath,
            id = id,
            version = version,
            name = pkgName,
            author = author,
            description = description.ifEmpty { "Paket modifikasi OpenIV (.OIV) siap dipasang." },
            largeDescription = largeDescription,
            targetFormatVersion = formatVersion,
            operations = operations,
            contentEntries = contentEntries,
            iconBytes = iconBytes,
            assemblyXmlRaw = assemblyXml
        )
    }

    private fun firstChildText(parent: Element, tagName: String): String {
        val list = parent.getElementsByTagName(tagName)
        if (list.length == 0) return ""
        return list.item(0)?.textContent?.trim().orEmpty()
    }

    /**
     * Generates a valid .oiv ZIP package byte array for the built-in GTA V Sandbox.
     */
    fun buildOivArchive(
        assemblyXml: String,
        contentFiles: Map<String, ByteArray>
    ): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zos ->
            zos.putNextEntry(ZipEntry("assembly.xml"))
            zos.write(assemblyXml.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            for ((path, data) in contentFiles) {
                zos.putNextEntry(ZipEntry(path))
                zos.write(data)
                zos.closeEntry()
            }
        }
        return out.toByteArray()
    }
}
