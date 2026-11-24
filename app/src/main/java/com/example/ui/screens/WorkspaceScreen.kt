package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.AppSettings
import com.example.data.RecentItemRecord
import com.example.ui.components.EditModeBanner
import com.example.ui.components.FormatBadge
import com.example.ui.theme.AmberGold
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.ElectricEmerald

@Composable
fun WorkspaceScreen(
    settings: AppSettings,
    oivCount: Int,
    onToggleEditMode: (Boolean) -> Unit,
    onOpenRpfOrOiv: () -> Unit,
    onOpenFolderTree: () -> Unit,
    onOpenSandboxExplorer: () -> Unit,
    onOpenQuickPath: (String) -> Unit,
    onCreateNewRpf: (String) -> Unit,
    onOpenRecent: (RecentItemRecord) -> Unit,
    onNavigateToMods: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var newArchiveName by remember { mutableStateOf("custom_mods.rpf") }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 840.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hero Banner Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(190.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_workspace_hero),
                            contentDescription = "Banner OpenV Studio GTA V",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color(0x330D1117),
                                            Color(0xCC0D1117),
                                            Color(0xF50D1117)
                                        )
                                    )
                                )
                        )
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(16.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FormatBadge("RPF7 ENGINE", ElectricEmerald)
                                FormatBadge("OIV v2.1", CyberCyan)
                                FormatBadge("RSC7 / DDS", AmberGold)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "OpenV Studio",
                                style = MaterialTheme.typography.headlineMedium,
                                color = Color.White
                            )
                            Text(
                                text = "Penjelajah arsip GTA V RPF7, editor handling.meta, inspektor tekstur DDS/YTD, dan manajer paket .OIV.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFD0D7DE)
                            )
                        }
                    }
                }
            }

            // Edit Mode Banner
            item {
                EditModeBanner(
                    isEditMode = settings.isEditMode,
                    onToggle = onToggleEditMode
                )
            }

            // Primary Action Grid
            item {
                Text(
                    text = "Aksi Utama",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        WorkspaceActionCard(
                            title = "Buka RPF / OIV",
                            subtitle = "Pilih arsip dari perangkat",
                            icon = Icons.Filled.FolderOpen,
                            accent = ElectricEmerald,
                            testTag = "btn_open_rpf_oiv",
                            onClick = onOpenRpfOrOiv,
                            modifier = Modifier.weight(1f)
                        )
                        WorkspaceActionCard(
                            title = "Buka Folder",
                            subtitle = "Jelajahi direktori SAF",
                            icon = Icons.Filled.CreateNewFolder,
                            accent = AmberGold,
                            testTag = "btn_open_folder",
                            onClick = onOpenFolderTree,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        WorkspaceActionCard(
                            title = "Sandbox GTA V",
                            subtitle = "update.rpf & x64a.rpf siap pakai",
                            icon = Icons.Filled.Archive,
                            accent = CyberCyan,
                            testTag = "btn_open_sandbox",
                            onClick = onOpenSandboxExplorer,
                            modifier = Modifier.weight(1f)
                        )
                        WorkspaceActionCard(
                            title = "Buat Arsip RPF7",
                            subtitle = "Wadah mod RPF7 OPEN baru",
                            icon = Icons.Filled.AddBox,
                            accent = Color(0xFFAB47BC),
                            testTag = "btn_create_rpf7",
                            onClick = { showCreateDialog = true },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Quick GTA V Shortcuts
            item {
                Text(
                    text = "Pintasan Modding GTA V",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ShortcutRowCard(
                        title = "update/update.rpf",
                        subtitle = "Buka arsip inti update.rpf (common/data, x64/textures)",
                        badge = "RPF7",
                        icon = Icons.Filled.Terminal,
                        accent = ElectricEmerald,
                        testTag = "shortcut_update_rpf",
                        onClick = { onOpenQuickPath("update.rpf") }
                    )
                    ShortcutRowCard(
                        title = "common/data/handling.meta",
                        subtitle = "Langsung ke folder fisika kendaraan & DLC config",
                        badge = "META",
                        icon = Icons.Filled.Tune,
                        accent = CyberCyan,
                        testTag = "shortcut_handling_meta",
                        onClick = { onOpenQuickPath("handling.meta") }
                    )
                    ShortcutRowCard(
                        title = "Paket Mod .OIV ($oivCount tersedia • ${settings.installedMods.size} terpasang)",
                        subtitle = "Kelola instalasi mod otomatis & rollback backup",
                        badge = "OIV",
                        icon = Icons.Filled.Archive,
                        accent = AmberGold,
                        testTag = "shortcut_oiv_mods",
                        onClick = onNavigateToMods
                    )
                }
            }

            // Recent Items
            if (settings.recentItems.isNotEmpty()) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Terakhir Dibuka",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
                items(settings.recentItems) { rec ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onOpenRecent(rec) }
                            .testTag("recent_item_${rec.title}"),
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = rec.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = rec.path,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            FormatBadge(rec.type, ElectricEmerald)
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Buat Arsip RPF7 Baru") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Membuat arsip biner RPF7 (format OPEN) baru di workspace saat ini.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = newArchiveName,
                        onValueChange = { newArchiveName = it },
                        label = { Text("Nama Berkas (.rpf)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_new_rpf_name")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newArchiveName.isNotBlank()) {
                            onCreateNewRpf(newArchiveName.trim())
                            showCreateDialog = false
                        }
                    },
                    modifier = Modifier.testTag("confirm_create_rpf")
                ) {
                    Text("Buat")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }
}

@Composable
private fun WorkspaceActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag(testTag),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = accent.copy(alpha = 0.15f),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = accent,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ShortcutRowCard(
    title: String,
    subtitle: String,
    badge: String,
    icon: ImageVector,
    accent: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag(testTag),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = accent.copy(alpha = 0.15f),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = accent,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    FormatBadge(badge, accent)
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Buka",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
