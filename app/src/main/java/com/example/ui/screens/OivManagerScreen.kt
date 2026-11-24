package com.example.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.OivPackageInfo
import com.example.data.InstalledModRecord
import com.example.ui.OivUiState
import com.example.ui.components.FormatBadge
import com.example.ui.theme.AlertRed
import com.example.ui.theme.AmberGold
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.ElectricEmerald

@Composable
fun OivManagerScreen(
    state: OivUiState,
    installedMods: List<InstalledModRecord>,
    onPickOivFile: () -> Unit,
    onSelectPackage: (OivPackageInfo?) -> Unit,
    onInstallSelected: () -> Unit,
    onUninstallMod: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 840.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Manajer Paket .OIV",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = "Baca assembly.xml, pasang mod ke RPF7, dan rollback backup kapan saja.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = onPickOivFile,
                        modifier = Modifier.testTag("btn_pick_oiv")
                    ) {
                        Icon(Icons.Filled.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Buka .OIV")
                    }
                }
            }

            if (state.isLoading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }

            item {
                Text(
                    text = "Paket .OIV Tersedia (${state.packages.size})",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            if (state.packages.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Belum ada paket .OIV di workspace. Tekan 'Buka .OIV' untuk memilih file mod dari perangkat Anda.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(state.packages, key = { it.filePath }) { pkg ->
                    val isInstalled = installedMods.any { it.id == pkg.id }
                    OivPackageCard(
                        pkg = pkg,
                        isInstalled = isInstalled,
                        onClick = { onSelectPackage(pkg) }
                    )
                }
            }

            if (installedMods.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Mod Terpasang & Rollback (${installedMods.size})",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                items(installedMods, key = { it.id }) { mod ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, ElectricEmerald.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = "Terpasang",
                                tint = ElectricEmerald,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = mod.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "v${mod.version} • oleh ${mod.author}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            OutlinedButton(
                                onClick = { onUninstallMod(mod.id) },
                                modifier = Modifier.testTag("btn_rollback_${mod.id}")
                            ) {
                                Icon(Icons.Filled.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Rollback")
                            }
                        }
                    }
                }
            }
        }
    }

    val selected = state.selectedPackage
    if (selected != null) {
        OivInstallerDialog(
            pkg = selected,
            isInstalling = state.isInstalling,
            onDismiss = { onSelectPackage(null) },
            onInstall = {
                onInstallSelected()
                onSelectPackage(null)
            }
        )
    }
}

@Composable
private fun OivPackageCard(
    pkg: OivPackageInfo,
    isInstalled: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag("oiv_card_${pkg.fileName}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val iconBmp = remember(pkg.iconBytes) {
                pkg.iconBytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
            }
            if (iconBmp != null) {
                Image(
                    bitmap = iconBmp.asImageBitmap(),
                    contentDescription = pkg.name,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
            } else {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = CyberCyan.copy(alpha = 0.14f),
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Extension,
                            contentDescription = null,
                            tint = CyberCyan,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = pkg.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isInstalled) {
                        FormatBadge("TERPASANG", ElectricEmerald)
                    } else {
                        FormatBadge("v${pkg.version}", CyberCyan)
                    }
                }
                Text(
                    text = "Oleh ${pkg.author} • ${pkg.operations.size} operasi arsip",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = pkg.description,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun OivInstallerDialog(
    pkg: OivPackageInfo,
    isInstalling: Boolean,
    onDismiss: () -> Unit,
    onInstall: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                FormatBadge("OPENIV PACKAGE v${pkg.version}", ElectricEmerald)
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = pkg.name, style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "Pembuat: ${pkg.author}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = pkg.largeDescription.ifBlank { pkg.description },
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Operasi Berkas (${pkg.operations.size}):",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                pkg.operations.take(5).forEach { op ->
                    val (icon, tint) = when (op.action) {
                        "ADD" -> Icons.Filled.AddCircleOutline to ElectricEmerald
                        "DELETE" -> Icons.Filled.DeleteSweep to AlertRed
                        else -> Icons.Filled.SwapHoriz to AmberGold
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, contentDescription = op.action, tint = tint, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${op.action}: ${op.archiveTarget} → ${op.destinationPath}",
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onInstall,
                enabled = !isInstalling,
                modifier = Modifier.testTag("btn_confirm_install_oiv")
            ) {
                Icon(Icons.Filled.InstallMobile, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (isInstalling) "Memasang..." else "Pasang ke Workspace")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Tutup") }
        }
    )
}
