package com.example.core

import org.xml.sax.InputSource
import org.xml.sax.SAXParseException
import java.io.StringReader
import java.io.StringWriter
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

sealed class XmlValidationResult {
    data object Valid : XmlValidationResult()
    data class Invalid(val line: Int, val column: Int, val message: String) : XmlValidationResult()
}

object XmlUtils {

    fun isXmlOrMetaFile(fileName: String): Boolean {
        val lower = fileName.lowercase()
        return lower.endsWith(".xml") ||
            lower.endsWith(".meta") ||
            lower.endsWith(".ymt") ||
            lower.endsWith(".ytyp") ||
            lower.endsWith(".ymap")
    }

    fun validateXml(xmlText: String): XmlValidationResult {
        if (xmlText.isBlank()) {
            return XmlValidationResult.Invalid(1, 1, "Dokumen XML kosong")
        }
        return try {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = false
            val builder = factory.newDocumentBuilder()
            // Suppress default stderr error printing on SAXParseException
            builder.setErrorHandler(null)
            builder.parse(InputSource(StringReader(xmlText.trimStart('\uFEFF'))))
            XmlValidationResult.Valid
        } catch (e: SAXParseException) {
            XmlValidationResult.Invalid(
                line = e.lineNumber.coerceAtLeast(1),
                column = e.columnNumber.coerceAtLeast(1),
                message = e.message ?: "Kesalahan sintaks XML"
            )
        } catch (e: Exception) {
            XmlValidationResult.Invalid(
                line = 1,
                column = 1,
                message = e.message ?: "XML tidak valid"
            )
        }
    }

    fun prettyFormatXml(xmlText: String): Result<String> {
        return try {
            val clean = xmlText.trimStart('\uFEFF').trim()
            val factory = DocumentBuilderFactory.newInstance()
            val builder = factory.newDocumentBuilder()
            builder.setErrorHandler(null)
            val doc = builder.parse(InputSource(StringReader(clean)))
            trimWhitespaceNodes(doc.documentElement)

            val transformer = TransformerFactory.newInstance().newTransformer().apply {
                setOutputProperty(OutputKeys.INDENT, "yes")
                setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2")
                setOutputProperty(OutputKeys.ENCODING, "UTF-8")
                if (!clean.startsWith("<?xml")) {
                    setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")
                }
            }
            val writer = StringWriter()
            transformer.transform(DOMSource(doc), StreamResult(writer))
            Result.success(writer.toString().trimEnd())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun trimWhitespaceNodes(node: org.w3c.dom.Node?) {
        if (node == null) return
        val children = node.childNodes
        val toRemove = mutableListOf<org.w3c.dom.Node>()
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == org.w3c.dom.Node.TEXT_NODE && child.textContent.isNullOrBlank()) {
                toRemove.add(child)
            } else if (child.hasChildNodes()) {
                trimWhitespaceNodes(child)
            }
        }
        toRemove.forEach { node.removeChild(it) }
    }
}
