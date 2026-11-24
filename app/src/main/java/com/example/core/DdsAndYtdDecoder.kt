package com.example.core

import android.graphics.Bitmap
import android.graphics.Color
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.zip.Deflater
import java.util.zip.Inflater

data class DdsInfo(
    val width: Int,
    val height: Int,
    val mipMapCount: Int,
    val fourCc: String,
    val bitsPerPixel: Int,
    val isCompressed: Boolean,
    val bitmap: Bitmap?
)

data class Rsc7Info(
    val magic: String,
    val version: Int,
    val systemFlags: Int,
    val graphicsFlags: Int,
    val virtualSize: Long,
    val physicalSize: Long,
    val embeddedDds: DdsInfo? = null,
    val decompressedSampleBytes: ByteArray? = null
)

object DdsAndYtdDecoder {

    private const val DDS_MAGIC = 0x20534444 // "DDS "
    private const val DDPF_FOURCC = 0x4
    private const val DDPF_RGB = 0x40

    fun isDds(bytes: ByteArray): Boolean {
        if (bytes.size < 128) return false
        val bb = ByteBuffer.wrap(bytes, 0, 4).order(ByteOrder.LITTLE_ENDIAN)
        return bb.int == DDS_MAGIC
    }

    fun isRsc7(bytes: ByteArray): Boolean {
        if (bytes.size < 16) return false
        return bytes[0] == 'R'.code.toByte() &&
            bytes[1] == 'S'.code.toByte() &&
            bytes[2] == 'C'.code.toByte() &&
            bytes[3] == '7'.code.toByte()
    }

    fun parseRsc7(bytes: ByteArray): Rsc7Info? {
        if (!isRsc7(bytes)) return null
        val bb = ByteBuffer.wrap(bytes, 0, 16).order(ByteOrder.LITTLE_ENDIAN)
        bb.int // magic
        val version = bb.int
        val sysFlags = bb.int
        val gfxFlags = bb.int

        val virtualSize = computePageSize(sysFlags)
        val physicalSize = computePageSize(gfxFlags)

        // Try decompressing payload after 16-byte RSC7 header if raw deflate
        var decompressed: ByteArray? = null
        var embeddedDds: DdsInfo? = null
        if (bytes.size > 16) {
            val payload = bytes.copyOfRange(16, bytes.size)
            decompressed = tryInflate(payload) ?: payload
            val ddsOffset = findDdsSignature(decompressed)
            if (ddsOffset >= 0 && ddsOffset + 128 <= decompressed.size) {
                embeddedDds = parseDds(decompressed.copyOfRange(ddsOffset, decompressed.size))
            }
        }

        return Rsc7Info(
            magic = "RSC7",
            version = version,
            systemFlags = sysFlags,
            graphicsFlags = gfxFlags,
            virtualSize = virtualSize,
            physicalSize = physicalSize,
            embeddedDds = embeddedDds,
            decompressedSampleBytes = decompressed
        )
    }

    private fun computePageSize(flags: Int): Long {
        val baseShift = (flags and 0xF) + 12
        val baseSize = 1L shl baseShift
        val totalPages = ((flags ushr 4) and 0x7F).toLong() +
            (((flags ushr 11) and 0x3F) shl 1) +
            (((flags ushr 17) and 0xF) shl 2) +
            (((flags ushr 21) and 0x1) shl 3)
        return if (totalPages == 0L) baseSize else totalPages * (baseSize / 16L).coerceAtLeast(512L)
    }

    private fun findDdsSignature(bytes: ByteArray): Int {
        for (i in 0..(bytes.size - 4)) {
            if (bytes[i] == 'D'.code.toByte() &&
                bytes[i + 1] == 'D'.code.toByte() &&
                bytes[i + 2] == 'S'.code.toByte() &&
                bytes[i + 3] == ' '.code.toByte()
            ) {
                return i
            }
        }
        return -1
    }

    fun parseDds(bytes: ByteArray): DdsInfo? {
        if (!isDds(bytes)) return null
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        bb.position(12)
        val height = bb.int
        val width = bb.int
        bb.int // pitchOrLinearSize
        bb.int // depth
        val mipMaps = bb.int.coerceAtLeast(1)

        // PixelFormat starts at offset 76
        bb.position(80)
        val pfFlags = bb.int
        val fourCcBytes = ByteArray(4)
        bb.get(fourCcBytes)
        val fourCcStr = if ((pfFlags and DDPF_FOURCC) != 0) {
            String(fourCcBytes, Charsets.US_ASCII)
        } else {
            "RGBA"
        }
        val rgbBitCount = bb.int
        val rMask = bb.int
        val gMask = bb.int
        val bMask = bb.int
        val aMask = bb.int

        val dataOffset = if (fourCcStr == "DX10" && bytes.size >= 148) 148 else 128
        val safeW = width.coerceIn(1, 2048)
        val safeH = height.coerceIn(1, 2048)

        val bitmap = when {
            width !in 1..2048 || height !in 1..2048 -> null
            fourCcStr == "DXT1" -> decodeDxt1(bytes, dataOffset, safeW, safeH)
            fourCcStr == "DXT5" -> decodeDxt5(bytes, dataOffset, safeW, safeH)
            (pfFlags and DDPF_RGB) != 0 && rgbBitCount == 32 -> decodeRgba32(
                bytes, dataOffset, safeW, safeH, rMask, gMask, bMask, aMask
            )
            else -> null
        }

        return DdsInfo(
            width = width,
            height = height,
            mipMapCount = mipMaps,
            fourCc = fourCcStr,
            bitsPerPixel = if ((pfFlags and DDPF_FOURCC) != 0) {
                if (fourCcStr == "DXT1") 4 else 8
            } else rgbBitCount,
            isCompressed = (pfFlags and DDPF_FOURCC) != 0,
            bitmap = bitmap
        )
    }

    private fun decodeRgba32(
        data: ByteArray,
        offset: Int,
        width: Int,
        height: Int,
        rMask: Int,
        gMask: Int,
        bMask: Int,
        aMask: Int
    ): Bitmap? {
        val needed = offset + width * height * 4
        if (data.size < needed) return null
        val pixels = IntArray(width * height)
        val bb = ByteBuffer.wrap(data, offset, width * height * 4).order(ByteOrder.LITTLE_ENDIAN)
        val isBgra = (rMask == 0x00FF0000 && bMask == 0x000000FF)
        for (i in pixels.indices) {
            val v = bb.int
            val r: Int
            val g: Int
            val b: Int
            val a: Int
            if (isBgra) {
                b = v and 0xFF
                g = (v ushr 8) and 0xFF
                r = (v ushr 16) and 0xFF
                a = if (aMask == 0) 255 else (v ushr 24) and 0xFF
            } else {
                r = v and 0xFF
                g = (v ushr 8) and 0xFF
                b = (v ushr 16) and 0xFF
                a = if (aMask == 0) 255 else (v ushr 24) and 0xFF
            }
            pixels[i] = Color.argb(a, r, g, b)
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun decodeDxt1(data: ByteArray, offset: Int, width: Int, height: Int): Bitmap? {
        val blocksX = (width + 3) / 4
        val blocksY = (height + 3) / 4
        if (data.size < offset + blocksX * blocksY * 8) return null
        val pixels = IntArray(width * height)
        val bb = ByteBuffer.wrap(data, offset, blocksX * blocksY * 8).order(ByteOrder.LITTLE_ENDIAN)

        for (by in 0 until blocksY) {
            for (bx in 0 until blocksX) {
                val c0 = bb.short.toInt() and 0xFFFF
                val c1 = bb.short.toInt() and 0xFFFF
                val code = bb.int
                val colors = IntArray(4)
                colors[0] = rgb565ToArgb(c0, 255)
                colors[1] = rgb565ToArgb(c1, 255)
                if (c0 > c1) {
                    colors[2] = lerpArgb(colors[0], colors[1], 2, 1, 3)
                    colors[3] = lerpArgb(colors[0], colors[1], 1, 2, 3)
                } else {
                    colors[2] = lerpArgb(colors[0], colors[1], 1, 1, 2)
                    colors[3] = Color.TRANSPARENT
                }
                for (py in 0 until 4) {
                    for (px in 0 until 4) {
                        val x = bx * 4 + px
                        val y = by * 4 + py
                        val idx = (code ushr (2 * (py * 4 + px))) and 0x3
                        if (x < width && y < height) {
                            pixels[y * width + x] = colors[idx]
                        }
                    }
                }
            }
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun decodeDxt5(data: ByteArray, offset: Int, width: Int, height: Int): Bitmap? {
        val blocksX = (width + 3) / 4
        val blocksY = (height + 3) / 4
        if (data.size < offset + blocksX * blocksY * 16) return null
        val pixels = IntArray(width * height)
        val bb = ByteBuffer.wrap(data, offset, blocksX * blocksY * 16).order(ByteOrder.LITTLE_ENDIAN)

        for (by in 0 until blocksY) {
            for (bx in 0 until blocksX) {
                val a0 = bb.get().toInt() and 0xFF
                val a1 = bb.get().toInt() and 0xFF
                var alphaBits = 0L
                for (i in 0 until 6) {
                    alphaBits = alphaBits or ((bb.get().toLong() and 0xFFL) shl (8 * i))
                }
                val alphas = IntArray(8)
                alphas[0] = a0
                alphas[1] = a1
                if (a0 > a1) {
                    for (i in 1..6) alphas[i + 1] = ((7 - i) * a0 + i * a1) / 7
                } else {
                    for (i in 1..4) alphas[i + 1] = ((5 - i) * a0 + i * a1) / 5
                    alphas[6] = 0
                    alphas[7] = 255
                }

                val c0 = bb.short.toInt() and 0xFFFF
                val c1 = bb.short.toInt() and 0xFFFF
                val code = bb.int
                val colors = IntArray(4)
                colors[0] = rgb565ToArgb(c0, 255)
                colors[1] = rgb565ToArgb(c1, 255)
                colors[2] = lerpArgb(colors[0], colors[1], 2, 1, 3)
                colors[3] = lerpArgb(colors[0], colors[1], 1, 2, 3)

                for (py in 0 until 4) {
                    for (px in 0 until 4) {
                        val x = bx * 4 + px
                        val y = by * 4 + py
                        val bitIdx = py * 4 + px
                        val colorIdx = (code ushr (2 * bitIdx)) and 0x3
                        val alphaIdx = ((alphaBits ushr (3 * bitIdx)) and 0x7L).toInt()
                        if (x < width && y < height) {
                            val rgb = colors[colorIdx] and 0x00FFFFFF
                            pixels[y * width + x] = (alphas[alphaIdx] shl 24) or rgb
                        }
                    }
                }
            }
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun rgb565ToArgb(c: Int, alpha: Int): Int {
        val r = ((c ushr 11) and 0x1F) * 255 / 31
        val g = ((c ushr 5) and 0x3F) * 255 / 63
        val b = (c and 0x1F) * 255 / 31
        return Color.argb(alpha, r, g, b)
    }

    private fun lerpArgb(c0: Int, c1: Int, w0: Int, w1: Int, div: Int): Int {
        val r = (Color.red(c0) * w0 + Color.red(c1) * w1) / div
        val g = (Color.green(c0) * w0 + Color.green(c1) * w1) / div
        val b = (Color.blue(c0) * w0 + Color.blue(c1) * w1) / div
        return Color.argb(255, r, g, b)
    }

    fun tryInflate(compressed: ByteArray): ByteArray? {
        return try {
            val inflater = Inflater(true)
            inflater.setInput(compressed)
            val out = ByteArrayOutputStream()
            val buf = ByteArray(4096)
            while (!inflater.finished()) {
                val count = inflater.inflate(buf)
                if (count == 0) {
                    if (inflater.needsInput()) break
                } else {
                    out.write(buf, 0, count)
                }
            }
            inflater.end()
            val res = out.toByteArray()
            if (res.isNotEmpty()) res else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Builds a real uncompressed 32-bit RGBA DDS image for the built-in GTA V Sandbox.
     */
    fun createProceduralDds(width: Int = 128, height: Int = 128, accentColor: Int = 0xFF00E676.toInt()): ByteArray {
        val header = ByteBuffer.allocate(128).order(ByteOrder.LITTLE_ENDIAN)
        header.putInt(DDS_MAGIC)
        header.putInt(124) // dwSize
        header.putInt(0x1 or 0x2 or 0x4 or 0x1000) // flags
        header.putInt(height)
        header.putInt(width)
        header.putInt(width * 4)
        header.putInt(0) // depth
        header.putInt(1) // mipmaps
        for (i in 0 until 11) header.putInt(0)
        // PixelFormat
        header.putInt(32)
        header.putInt(0x41) // DDPF_RGB | DDPF_ALPHAPIXELS
        header.putInt(0) // FourCC
        header.putInt(32) // bitCount
        header.putInt(0x000000FF) // R mask
        header.putInt(0x0000FF00) // G mask
        header.putInt(0x00FF0000) // B mask
        header.putInt(0xFF000000.toInt()) // A mask
        header.putInt(0x1000) // caps
        for (i in 0 until 4) header.putInt(0)

        val out = ByteArrayOutputStream()
        out.write(header.array())
        val ar = Color.red(accentColor)
        val ag = Color.green(accentColor)
        val ab = Color.blue(accentColor)

        val pixelBuf = ByteBuffer.allocate(width * height * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val border = x < 4 || y < 4 || x >= width - 4 || y >= height - 4
                val grid = (x % 16 == 0) || (y % 16 == 0)
                val diag = kotlin.math.abs(x - y) <= 3 || kotlin.math.abs((width - 1 - x) - y) <= 3
                val r: Int
                val g: Int
                val b: Int
                when {
                    border || diag -> { r = ar; g = ag; b = ab }
                    grid -> { r = 36; g = 48; b = 62 }
                    else -> { r = 15; g = 21; b = 28 }
                }
                val rgba = (0xFF shl 24) or (b shl 16) or (g shl 8) or r
                pixelBuf.putInt(rgba)
            }
        }
        out.write(pixelBuf.array())
        return out.toByteArray()
    }

    /**
     * Wraps a DDS texture inside a valid RSC7 container (.ytd) with raw Deflate payload.
     */
    fun createRsc7YtdFromDds(ddsBytes: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.DEFAULT_COMPRESSION, true)
        deflater.setInput(ddsBytes)
        deflater.finish()
        val compressedOut = ByteArrayOutputStream()
        val buf = ByteArray(4096)
        while (!deflater.finished()) {
            val n = deflater.deflate(buf)
            compressedOut.write(buf, 0, n)
        }
        deflater.end()

        val header = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RSC7".toByteArray(Charsets.US_ASCII))
        header.putInt(13) // YTD resource version 13 in GTA V
        header.putInt(0x00010002) // system flags
        header.putInt(0x00040005) // graphics flags

        val out = ByteArrayOutputStream()
        out.write(header.array())
        out.write(compressedOut.toByteArray())
        return out.toByteArray()
    }

    fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
            else -> String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
        }
    }
}
