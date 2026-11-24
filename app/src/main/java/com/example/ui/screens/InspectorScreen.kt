package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.DdsAndYtdDecoder
import com.example.data.ExplorerItem
import com.example.ui.InspectorUiState
import com.example.ui.components.EditModeBanner
import com.example.ui.components.FormatBadge
import com.example.ui.components.XmlCodeEditor
import com.example.ui.theme.AmberGold
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.ElectricEmerald
import com.example.ui.theme.JetBrainsMonoFontFamily
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InspectorScreen(
    state: InspectorUiState,
    isEditMode: Boolean,
    isDarkTheme: Boolean,
    onToggleEditMode: (Boolean) -> Unit,
    onBack: () -> Unit,
    onTextChanged: (String) -> Unit,
    onRevertChanges: () -> Unit,
    onSaveChanges: () -> Unit,
    onExportItem: (ExplorerItem) -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val item = state.item
    val isHandlingMeta = item?.name?.equals("handling.meta", ignoreCase = true) == true
    val hasVisualPreview = state.decodedBitmap != null || state.ddsInfo != null || state.rsc7Info != null

    val initialTab = remember(item?.path, state.isTextRepresentable, hasVisualPreview) {
        when {
            hasVisualPreview || isHandlingMeta -> 0
            state.isTextRepresentable -> 1
            else -> 2
        }
    }
    var selectedTab by remember(item?.path) { mutableIntStateOf(initialTab) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = item?.name ?: "Inspektor Berkas",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (item != null) {
                            Text(
                                text = "${DdsAndYtdDecoder.formatBytes(state.rawBytes.size.toLong())} • ${if (item.isInsideRpf) "Di dalam RPF7" else "Berkas Lokal"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("btn_inspector_back")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Kembali"
                        )
                    }
                },
                actions = {
                    if (state.hasUnsavedChanges && isEditMode) {
                        IconButton(
                            onClick = onSaveChanges,
                            modifier = Modifier.testTag("btn_inspector_save")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Save,
                                contentDescription = "Simpan ke RPF",
                                tint = ElectricEmerald
                            )
                        }
                    }
                    if (item != null) {
                        IconButton(
                            onClick = { onExportItem(item) },
                            modifier = Modifier.testTag("btn_inspector_export")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.FileDownload,
                                contentDescription = "Ekspor Berkas"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 840.dp)
                        .fillMaxSize()
                ) {
                    TabRow(selectedTabIndex = selectedTab) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text("Pratinjau") },
                            icon = { Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text("Kode / XML") },
                            icon = { Icon(Icons.Filled.Code, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                        Tab(
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2 },
                            text = { Text("Hex Dump") },
                            icon = { Icon(Icons.Filled.Memory, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                    }

                    when (selectedTab) {
                        0 -> PreviewTabContent(
                            state = state,
                            isHandlingMeta = isHandlingMeta,
                            isEditMode = isEditMode,
                            onToggleEditMode = onToggleEditMode,
                            onTextChanged = onTextChanged,
                            onSaveChanges = onSaveChanges
                        )
                        1 -> CodeTabContent(
                            state = state,
                            isEditMode = isEditMode,
                            isDarkTheme = isDarkTheme,
                            onToggleEditMode = onToggleEditMode,
                            onTextChanged = onTextChanged,
                            onRevertChanges = onRevertChanges,
                            onSaveChanges = onSaveChanges,
                            onMessage = onMessage
                        )
                        2 -> HexDumpTabContent(rawBytes = state.rawBytes)
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewTabContent(
    state: InspectorUiState,
    isHandlingMeta: Boolean,
    isEditMode: Boolean,
    onToggleEditMode: (Boolean) -> Unit,
    onTextChanged: (String) -> Unit,
    onSaveChanges: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (isHandlingMeta) {
            EditModeBanner(isEditMode = isEditMode, onToggle = onToggleEditMode)
            HandlingMetaVisualTuner(
                xmlText = state.editedText,
                isEditMode = isEditMode,
                hasUnsavedChanges = state.hasUnsavedChanges,
                onXmlUpdated = onTextChanged,
                onSave = onSaveChanges
            )
        }

        val bitmap = state.decodedBitmap
        if (bitmap != null) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CheckerboardBackground(modifier = Modifier.fillMaxSize())
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Pratinjau Tekstur ${state.item?.name}",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    )
                }
            }
        }

        val dds = state.ddsInfo ?: state.rsc7Info?.embeddedDds
        if (dds != null) {
            MetadataCard(
                title = "Metadata Tekstur DDS",
                accent = Color(0xFFEC407A),
                rows = listOf(
                    "Resolusi" to "${dds.width} × ${dds.height} px",
                    "Format Piksel" to dds.fourCc,
                    "Bit per Piksel" to "${dds.bitsPerPixel} bpp",
                    "Mipmaps" to dds.mipMapCount.toString(),
                    "Kompresi Blok" to if (dds.isCompressed) "Ya (BC/DXT)" else "Tidak (Raw RGBA)"
                )
            )
        }

        val rsc7 = state.rsc7Info
        if (rsc7 != null) {
            MetadataCard(
                title = "Header Kontainer RSC7",
                accent = Color(0xFFAB47BC),
                rows = listOf(
                    "Signature" to rsc7.magic,
                    "Versi Resource" to rsc7.version.toString(),
                    "System Flags" to "0x%08X".format(rsc7.systemFlags),
                    "Graphics Flags" to "0x%08X".format(rsc7.graphicsFlags),
                    "Virtual Page Size" to DdsAndYtdDecoder.formatBytes(rsc7.virtualSize),
                    "Physical Page Size" to DdsAndYtdDecoder.formatBytes(rsc7.physicalSize)
                )
            )
        }

        if (!isHandlingMeta && bitmap == null && dds == null && rsc7 == null) {
            val magicAscii = state.rawBytes.take(4).map { b ->
                val c = b.toInt() and 0xFF
                if (c in 32..126) c.toChar() else '.'
            }.joinToString("")
            val magicHex = state.rawBytes.take(4).joinToString(" ") { "%02X".format(it) }
            MetadataCard(
                title = "Ringkasan Berkas",
                accent = CyberCyan,
                rows = listOf(
                    "Nama Berkas" to (state.item?.name ?: "-"),
                    "Ukuran" to DdsAndYtdDecoder.formatBytes(state.rawBytes.size.toLong()),
                    "Header Magic" to "$magicHex ($magicAscii)",
                    "Representasi" to if (state.isTextRepresentable) "Teks / XML / Konfigurasi" else "Data Biner",
                    "Lokasi" to (state.item?.path ?: "-")
                )
            )
        }
    }
}

@Composable
private fun HandlingMetaVisualTuner(
    xmlText: String,
    isEditMode: Boolean,
    hasUnsavedChanges: Boolean,
    onXmlUpdated: (String) -> Unit,
    onSave: () -> Unit
) {
    // Parse <Item type="CHandlingData">...</Item> blocks
    val itemRegex = remember {
        Regex("""<Item\s+type="CHandlingData">([\s\S]*?)</Item>""")
    }
    val nameRegex = remember { Regex("""<handlingName>(.*?)</handlingName>""") }
    val matches = remember(xmlText) { itemRegex.findAll(xmlText).toList() }
    val vehicleNames = remember(matches) {
        matches.mapIndexed { idx, m ->
            nameRegex.find(m.groupValues[1])?.groupValues?.get(1)?.trim() ?: "VEHICLE_$idx"
        }
    }
    var selectedIdx by remember { mutableIntStateOf(0) }
    val safeIdx = selectedIdx.coerceIn(0, (vehicleNames.size - 1).coerceAtLeast(0))

    if (vehicleNames.isEmpty()) {
        Text(
            text = "Tidak ditemukan blok CHandlingData pada berkas ini. Gunakan tab Kode / XML untuk mengedit manual.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    val currentBlock = matches[safeIdx].value

    fun readFloatTag(tag: String, default: Float): Float {
        val r = Regex("""<$tag\s+value="([^"]+)"\s*/>""")
        return r.find(currentBlock)?.groupValues?.get(1)?.toFloatOrNull() ?: default
    }

    fun updateFloatTag(tag: String, newVal: Float, decimals: Int = 6) {
        val formatted = String.format(Locale.US, "%.${decimals}f", newVal)
        val r = Regex("""(<$tag\s+value=")([^"]+)("\s*/>)""")
        val updatedBlock = r.replace(currentBlock) { "${it.groupValues[1]}$formatted${it.groupValues[3]}" }
        val updatedFull = xmlText.replaceFirst(currentBlock, updatedBlock)
        onXmlUpdated(updatedFull)
    }

    fun updateIntTag(tag: String, newVal: Int) {
        val r = Regex("""(<$tag\s+value=")([^"]+)("\s*/>)""")
        val updatedBlock = r.replace(currentBlock) { "${it.groupValues[1]}$newVal${it.groupValues[3]}" }
        val updatedFull = xmlText.replaceFirst(currentBlock, updatedBlock)
        onXmlUpdated(updatedFull)
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Tuner Fisika Kendaraan",
                style = MaterialTheme.typography.titleMedium
            )
            FormatBadge("handling.meta", ElectricEmerald)
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(vehicleNames.size) { idx ->
                FilterChip(
                    selected = safeIdx == idx,
                    onClick = { selectedIdx = idx },
                    label = { Text(vehicleNames[idx]) }
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HandlingSliderRow(
                    label = "fMass (Massa kg)",
                    value = readFloatTag("fMass", 1500f),
                    range = 500f..4000f,
                    enabled = isEditMode,
                    format = "%.0f kg",
                    onChange = { updateFloatTag("fMass", it) }
                )
                HandlingSliderRow(
                    label = "fInitialDriveForce (Akselerasi)",
                    value = readFloatTag("fInitialDriveForce", 0.34f),
                    range = 0.1f..0.8f,
                    enabled = isEditMode,
                    format = "%.3f",
                    onChange = { updateFloatTag("fInitialDriveForce", it) }
                )
                HandlingSliderRow(
                    label = "fInitialDriveMaxFlatVel (Top Speed)",
                    value = readFloatTag("fInitialDriveMaxFlatVel", 160f),
                    range = 80f..260f,
                    enabled = isEditMode,
                    format = "%.0f",
                    onChange = { updateFloatTag("fInitialDriveMaxFlatVel", it) }
                )
                HandlingSliderRow(
                    label = "fDriveBiasFront (0=RWD, 0.5=AWD, 1=FWD)",
                    value = readFloatTag("fDriveBiasFront", 0.2f),
                    range = 0f..1f,
                    enabled = isEditMode,
                    format = "%.2f",
                    onChange = { updateFloatTag("fDriveBiasFront", it) }
                )
                HandlingSliderRow(
                    label = "fBrakeForce (Daya Rem)",
                    value = readFloatTag("fBrakeForce", 0.9f),
                    range = 0.2f..2.5f,
                    enabled = isEditMode,
                    format = "%.2f",
                    onChange = { updateFloatTag("fBrakeForce", it) }
                )
                HandlingSliderRow(
                    label = "fTractionCurveMax (Grip Tikungan)",
                    value = readFloatTag("fTractionCurveMax", 2.4f),
                    range = 1.0f..4.0f,
                    enabled = isEditMode,
                    format = "%.2f",
                    onChange = { updateFloatTag("fTractionCurveMax", it) }
                )
                val gears = readFloatTag("nInitialDriveGears", 6f).toInt().coerceIn(3, 8)
                HandlingSliderRow(
                    label = "nInitialDriveGears (Jumlah Gigi)",
                    value = gears.toFloat(),
                    range = 3f..8f,
                    enabled = isEditMode,
                    format = "%.0f gigi",
                    steps = 4,
                    onChange = { updateIntTag("nInitialDriveGears", kotlin.math.round(it).toInt()) }
                )
            }
        }

        if (isEditMode && hasUnsavedChanges) {
            Button(
                onClick = onSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("btn_save_tuner")
            ) {
                Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Simpan & Repack RPF7")
            }
        }
    }
}

@Composable
private fun HandlingSliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    format: String,
    steps: Int = 0,
    onChange: (Float) -> Unit
) {
    val clamped = value.coerceIn(range.start, range.endInclusive)
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, style = MaterialTheme.typography.bodySmall)
            Text(
                text = String.format(Locale.US, format, clamped),
                style = MaterialTheme.typography.labelMedium,
                color = ElectricEmerald
            )
        }
        Slider(
            value = clamped,
            onValueChange = onChange,
            valueRange = range,
            steps = steps,
            enabled = enabled
        )
    }
}

@Composable
private fun CodeTabContent(
    state: InspectorUiState,
    isEditMode: Boolean,
    isDarkTheme: Boolean,
    onToggleEditMode: (Boolean) -> Unit,
    onTextChanged: (String) -> Unit,
    onRevertChanges: () -> Unit,
    onSaveChanges: () -> Unit,
    onMessage: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        EditModeBanner(isEditMode = isEditMode, onToggle = onToggleEditMode)

        if (!state.isTextRepresentable) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Berkas ini berformat biner (${state.rawBytes.size} byte). Gunakan tab Hex Dump atau Pratinjau untuk memeriksa isinya.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp)
                )
            }
            return
        }

        XmlCodeEditor(
            fileName = state.item?.name ?: "file.xml",
            text = if (isEditMode) state.editedText else state.textContent,
            isEditMode = isEditMode,
            hasUnsavedChanges = state.hasUnsavedChanges,
            isDarkTheme = isDarkTheme,
            onTextChanged = onTextChanged,
            onRevertChanges = onRevertChanges,
            onSaveChanges = onSaveChanges,
            onMessage = onMessage,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun HexDumpTabContent(rawBytes: ByteArray) {
    val maxPreviewBytes = rawBytes.size.coerceAtMost(65536) // 64KB fast paged view
    val rowCount = (maxPreviewBytes + 15) / 16

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Menampilkan $maxPreviewBytes dari ${rawBytes.size} byte (16 byte/baris)",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(rememberScrollState()),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(rowCount) { rowIdx ->
                    val offset = rowIdx * 16
                    val end = (offset + 16).coerceAtMost(maxPreviewBytes)
                    val hexPart = buildString {
                        for (i in offset until offset + 16) {
                            if (i < end) {
                                append(String.format(Locale.US, "%02X ", rawBytes[i]))
                            } else {
                                append("   ")
                            }
                            if (i == offset + 7) append(" ")
                        }
                    }
                    val asciiPart = buildString {
                        for (i in offset until end) {
                            val c = rawBytes[i].toInt() and 0xFF
                            append(if (c in 32..126) c.toChar() else '.')
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = String.format(Locale.US, "%08X  ", offset),
                            style = TextStyle(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 11.sp,
                                color = AmberGold
                            )
                        )
                        Text(
                            text = hexPart,
                            style = TextStyle(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        Text(
                            text = " |$asciiPart|",
                            style = TextStyle(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 11.sp,
                                color = ElectricEmerald
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetadataCard(
    title: String,
    accent: Color,
    rows: List<Pair<String, String>>
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                FormatBadge("INFO", accent)
            }
            rows.forEach { (k, v) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = k,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = v,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun CheckerboardBackground(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val cell = 16.dp.toPx()
        val cols = (size.width / cell).toInt() + 1
        val rows = (size.height / cell).toInt() + 1
        val light = Color(0xFF21262D)
        val dark = Color(0xFF161B22)
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                drawRect(
                    color = if ((r + c) % 2 == 0) light else dark,
                    topLeft = Offset(c * cell, r * cell),
                    size = Size(cell, cell)
                )
            }
        }
    }
}
