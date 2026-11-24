package com.example

import com.example.core.DdsAndYtdDecoder
import com.example.core.JoaatHasher
import com.example.core.MutableRpfItem
import com.example.core.OivPackageEngine
import com.example.core.Rpf7Engine
import com.example.core.RpfEncryption
import com.example.core.RpfEntryNode
import com.example.core.XmlUtils
import com.example.core.XmlValidationResult
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenVCoreTest {

    @Test
    fun joaat_hash_matches_known_gtav_values() {
        val res = JoaatHasher.hash("adder")
        assertEquals("0xB779A091", res.hexUpper)
    }

    @Test
    fun rpf7_build_and_parse_roundtrip_preserves_folders_and_files() {
        val sampleXml = "<CHandlingDataMgr><Test>123</Test></CHandlingDataMgr>".toByteArray(Charsets.UTF_8)
        val tree = MutableRpfItem.Folder("").apply {
            children.add(
                MutableRpfItem.Folder("common").apply {
                    children.add(
                        MutableRpfItem.Folder("data").apply {
                            children.add(MutableRpfItem.Blob("handling.meta", sampleXml))
                        }
                    )
                }
            )
            children.add(MutableRpfItem.Blob("readme.txt", "OpenV".toByteArray(Charsets.UTF_8)))
        }

        val rpfBytes = Rpf7Engine.buildRpf7Bytes(tree)
        val parsed = Rpf7Engine.parseFromBytes("test.rpf", rpfBytes)

        assertEquals("RPF7", parsed.header.magic)
        assertEquals(RpfEncryption.OPEN, parsed.header.encryption)
        val handlingNode = parsed.flatEntries
            .filterIsInstance<RpfEntryNode.FileNode>()
            .firstOrNull { it.fullPath == "common/data/handling.meta" }
        assertNotNull(handlingNode)
        val extracted = Rpf7Engine.extractEntryBytes(rpfBytes, handlingNode!!)
        assertArrayEquals(sampleXml, extracted)
    }

    @Test
    fun dds_and_rsc7_signatures_are_detected() {
        val rsc7Magic = byteArrayOf('R'.code.toByte(), 'S'.code.toByte(), 'C'.code.toByte(), '7'.code.toByte()) + ByteArray(12)
        assertTrue(DdsAndYtdDecoder.isRsc7(rsc7Magic))
        val ddsMagic = byteArrayOf('D'.code.toByte(), 'D'.code.toByte(), 'S'.code.toByte(), ' '.code.toByte()) + ByteArray(124)
        assertTrue(DdsAndYtdDecoder.isDds(ddsMagic))
    }

    @Test
    fun oiv_package_builder_and_parser_extracts_assembly_metadata() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <package version="2.1" id="com.test.mod">
              <metadata>
                <name>Test Mod</name>
                <version><major>2</major><minor>0</minor></version>
                <author><displayName>Tester</displayName></author>
                <description>Desc</description>
              </metadata>
              <content>
                <archive path="update/update.rpf">
                  <replace source="content/handling.meta">common/data/handling.meta</replace>
                </archive>
              </content>
            </package>
        """.trimIndent()
        val oivBytes = OivPackageEngine.buildOivArchive(xml, mapOf("content/handling.meta" to "abc".toByteArray()))
        val parsed = OivPackageEngine.parseOivBytes("test.oiv", "/tmp/test.oiv", oivBytes)
        assertEquals("Test Mod", parsed.name)
        assertEquals("Tester", parsed.author)
        assertEquals(1, parsed.operations.size)
        assertTrue(parsed.operations.first().action == "REPLACE")
    }

    @Test
    fun xml_utils_validates_and_formats_meta_xml() {
        val validMeta = "<CHandlingDataMgr><HandlingData><Item><handlingName>ADDER</handlingName></Item></HandlingData></CHandlingDataMgr>"
        assertTrue(XmlUtils.validateXml(validMeta) is XmlValidationResult.Valid)

        val formatted = XmlUtils.prettyFormatXml(validMeta).getOrThrow()
        assertTrue(formatted.contains("\n"))
        assertTrue(formatted.contains("<handlingName>ADDER</handlingName>"))

        val brokenMeta = "<CHandlingDataMgr><Item></CHandlingDataMgr>"
        assertTrue(XmlUtils.validateXml(brokenMeta) is XmlValidationResult.Invalid)
    }
}
