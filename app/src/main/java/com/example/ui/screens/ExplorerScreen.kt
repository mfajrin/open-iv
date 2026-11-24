package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.DdsAndYtdDecoder
import com.example.data.ExplorerItem
import com.example.data.ExplorerItemKind
import com.example.data.SafDocItem
import com.example.ui.ExplorerFilter
import com.example.ui.ExplorerUiState
import com.example.ui.components.EditModeBanner
import com.example.ui.components.ExplorerItemIcon
import com.example.ui.components.FormatBadge
import com.example.ui.theme.AmberGold
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.ElectricEmerald

@Composable
fun ExplorerScreen(
    state: ExplorerUiState,
    isEditMode: Boolean,
    onToggleEditMode: (Boolean) -> Unit,
    onStepUp: () -> Boolean,
    onBreadcrumbClick: (String) -> Unit,
    onItemClick: (ExplorerItem) -> Unit,
    onSafItemClick: (SafDocItem) -> Unit,
    onSearchChange: (String) -> Unit,
    onFilterChange: (ExplorerFilter) -> Unit,
    onImportFileIntoArchive: () -> Unit,
    onCreateFolder: (String) -> Unit,
    onExportItem: (ExplorerItem) -> Unit,
    onRenameItem: (ExplorerItem, String) -> Unit,
    onDeleteItem: (ExplorerItem) -> Unit,
    modifier: Modifier = Modifier
) {
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<ExplorerItem?>(null) }
    var renameText by remember { mutableStateOf("") }

    val canStepUp = state.activeRpfFile != null || state.isBrowsingSaf ||
        (state.currentFsDir != null && state.listing?.locationSegments?.size ?: 0 > 1)

    BackHandler(enabled = canStepUp) {
        onStepUp()
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 840.dp)
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            EditModeBanner(
                isEditMode = isEditMode,
                onToggle = onToggleEditMode
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Breadcrumb & Up Navigation Bar
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { onStepUp() },
                        enabled = canStepUp,
                        modifier = Modifier.testTag("btn_explorer_up")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Naik satu tingkat"
                        )
                    }

                    if (state.isBrowsingSaf) {
                        Text(
                            text = "SAF / ${state.safTitle}",
                            style = MaterialTheme.typography.titleSmall,
                            color = AmberGold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 6.dp)
                        )
                    } else {
                        val segments = state.listing?.locationSegments.orEmpty()
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(rememberScrollState()),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            segments.forEachIndexed { idx, (label, target) ->
                                val isLast = idx == segments.lastIndex
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (isLast) ElectricEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable(enabled = !isLast) { onBreadcrumbClick(target) }
                                        .padding(horizontal = 8.dp, vertical = 8.dp)
                                )
                                if (!isLast) {
                                    Icon(
                                        imageVector = Icons.Filled.ChevronRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // RPF7 Header Diagnostic Chip
            val rpfHeader = state.listing?.activeRpfHeaderInfo
            if (rpfHeader != null && !state.isBrowsingSaf) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = rpfHeader,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Search Bar
            if (!state.isBrowsingSaf) {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = onSearchChange,
                    placeholder = { Text("Cari berkas dalam folder / arsip RPF...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Cari") },
                    trailingIcon = {
                        if (state.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchChange("") }) {
                                Icon(Icons.Filled.Clear, contentDescription = "Hapus pencarian")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("explorer_search_input")
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Filter Chips + Edit Mode Actions
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(ExplorerFilter.entries) { filter ->
                        FilterChip(
                            selected = state.selectedFilter == filter,
                            onClick = { onFilterChange(filter) },
                            label = { Text(filter.label) },
                            modifier = Modifier.testTag("filter_${filter.name}")
                        )
                    }
                }

                if (isEditMode && state.listing?.isActiveRpfWritable != false) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onImportFileIntoArchive,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_inject_file")
                        ) {
                            Icon(Icons.Filled.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Impor Berkas")
                        }
                        OutlinedButton(
                            onClick = {
                                newFolderName = ""
                                showNewFolderDialog = true
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_new_folder")
                        ) {
                            Icon(Icons.Filled.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Folder Baru")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Main Directory / Archive Listing
            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (state.isBrowsingSaf) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.safItems) { doc ->
                        SafRowCard(doc = doc, onClick = { onSafItemClick(doc) })
                    }
                }
            } else {
                val baseItems = state.searchResults ?: state.listing?.items.orEmpty()
                val filteredItems = remember(baseItems, state.selectedFilter) {
                    when (state.selectedFilter) {
                        ExplorerFilter.ALL -> baseItems
                        ExplorerFilter.META_XML -> baseItems.filter {
                            it.kind == ExplorerItemKind.XML_META || it.kind == ExplorerItemKind.TEXT_CONFIG
                        }
                        ExplorerFilter.RPF -> baseItems.filter { it.kind == ExplorerItemKind.RPF_ARCHIVE }
                        ExplorerFilter.TEXTURES -> baseItems.filter {
                            it.kind == ExplorerItemKind.TEXTURE_DDS || it.kind == ExplorerItemKind.TEXTURE_YTD
                        }
                        ExplorerFilter.MODELS -> baseItems.filter { it.kind == ExplorerItemKind.MODEL_3D }
                    }
                }

                val encNote = state.listing?.activeRpfEncryptionNote
                if (encNote != null) {
                    EmptyExplorerState(
                        title = "Arsip Terenkripsi Retail",
                        message = encNote
                    )
                } else if (filteredItems.isEmpty()) {
                    EmptyExplorerState(
                        title = "Tidak ada item ditemukan",
                        message = "Folder atau filter ini belum memiliki berkas yang cocok. Coba ubah filter atau impor berkas baru."
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredItems, key = { "${it.path}_${it.name}" }) { item ->
                            ExplorerRowCard(
                                item = item,
                                isEditMode = isEditMode && (state.listing?.isActiveRpfWritable != false),
                                onClick = { onItemClick(item) },
                                onExport = { onExportItem(item) },
                                onRename = {
                                    renameTarget = item
                                    renameText = item.name
                                },
                                onDelete = { onDeleteItem(item) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showNewFolderDialog) {
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            title = { Text("Buat Folder Baru") },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    label = { Text("Nama Folder") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (newFolderName.isNotBlank()) {
                        onCreateFolder(newFolderName.trim())
                        showNewFolderDialog = false
                    }
                }) { Text("Buat") }
            },
            dismissButton = {
                TextButton(onClick = { showNewFolderDialog = false }) { Text("Batal") }
            }
        )
    }

    val rTarget = renameTarget
    if (rTarget != null) {
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Ganti Nama Entri") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("Nama Baru") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (renameText.isNotBlank()) {
                        onRenameItem(rTarget, renameText.trim())
                        renameTarget = null
                    }
                }) { Text("Simpan") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Batal") }
            }
        )
    }
}

@Composable
private fun ExplorerRowCard(
    item: ExplorerItem,
    isEditMode: Boolean,
    onClick: () -> Unit,
    onExport: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag("explorer_item_${item.name}"),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ExplorerItemIcon(kind = item.kind)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val subtitle = if (item.kind == ExplorerItemKind.FOLDER) {
                        "${item.sizeBytes} item"
                    } else {
                        DdsAndYtdDecoder.formatBytes(item.sizeBytes)
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (item.badgeLabel.isNotBlank()) {
                        FormatBadge(
                            label = item.badgeLabel,
                            accentColor = when (item.kind) {
                                ExplorerItemKind.RPF_ARCHIVE -> ElectricEmerald
                                ExplorerItemKind.OIV_PACKAGE, ExplorerItemKind.XML_META -> CyberCyan
                                ExplorerItemKind.FOLDER -> AmberGold
                                else -> MaterialTheme.colorScheme.secondary
                            }
                        )
                    }
                }
            }
            if (item.kind != ExplorerItemKind.FOLDER) {
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.testTag("more_${item.name}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "Menu aksi ${item.name}"
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Ekspor / Ekstrak") },
                            leadingIcon = { Icon(Icons.Filled.FileDownload, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onExport()
                            }
                        )
                        if (isEditMode) {
                            DropdownMenuItem(
                                text = { Text("Ganti Nama") },
                                leadingIcon = { Icon(Icons.Filled.DriveFileRenameOutline, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onRename()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Hapus") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onDelete()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SafRowCard(doc: SafDocItem, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (doc.isDirectory) Icons.Filled.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,
                contentDescription = doc.name,
                tint = if (doc.isDirectory) AmberGold else CyberCyan,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = doc.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (doc.isDirectory) "Folder SAF" else DdsAndYtdDecoder.formatBytes(doc.sizeBytes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EmptyExplorerState(title: String, message: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_archive_empty),
            contentDescription = "Ilustrasi arsip kosong",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(180.dp)
                .clip(RoundedCornerShape(20.dp))
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
