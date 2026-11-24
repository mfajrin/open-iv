package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.ExplorerItemKind
import com.example.ui.theme.AmberGold
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.ElectricEmerald

@Composable
fun EditModeBanner(
    isEditMode: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor by animateColorAsState(
        targetValue = if (isEditMode) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        label = "editModeBg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isEditMode) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outline
        },
        label = "editModeBorder"
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isEditMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isEditMode) Icons.Filled.LockOpen else Icons.Filled.Lock,
                            contentDescription = if (isEditMode) "Edit Mode Aktif" else "Mode Baca Saja",
                            tint = if (isEditMode) MaterialTheme.colorScheme.onPrimary else AmberGold,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = if (isEditMode) "Edit Mode Aktif" else "Mode Baca Saja",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isEditMode) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (isEditMode) {
                            "Arsip RPF7 dapat dimodifikasi & di-repack"
                        } else {
                            "Aktifkan untuk mengedit .meta, impor & hapus berkas"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isEditMode) {
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = isEditMode,
                onCheckedChange = onToggle,
                modifier = Modifier.testTag("edit_mode_switch"),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}

@Composable
fun ExplorerItemIcon(
    kind: ExplorerItemKind,
    modifier: Modifier = Modifier
) {
    val (icon, tint, container) = when (kind) {
        ExplorerItemKind.FOLDER -> Triple(
            Icons.Filled.Folder,
            AmberGold,
            AmberGold.copy(alpha = 0.14f)
        )
        ExplorerItemKind.RPF_ARCHIVE -> Triple(
            Icons.Filled.Archive,
            ElectricEmerald,
            ElectricEmerald.copy(alpha = 0.14f)
        )
        ExplorerItemKind.OIV_PACKAGE -> Triple(
            Icons.Filled.Extension,
            CyberCyan,
            CyberCyan.copy(alpha = 0.14f)
        )
        ExplorerItemKind.XML_META -> Triple(
            Icons.Filled.Code,
            CyberCyan,
            CyberCyan.copy(alpha = 0.14f)
        )
        ExplorerItemKind.TEXTURE_DDS, ExplorerItemKind.TEXTURE_YTD -> Triple(
            Icons.Filled.Image,
            Color(0xFFEC407A),
            Color(0xFFEC407A).copy(alpha = 0.14f)
        )
        ExplorerItemKind.MODEL_3D -> Triple(
            Icons.Filled.ViewInAr,
            Color(0xFFAB47BC),
            Color(0xFFAB47BC).copy(alpha = 0.14f)
        )
        ExplorerItemKind.TEXT_CONFIG -> Triple(
            Icons.Filled.Description,
            Color(0xFF90CAF9),
            Color(0xFF90CAF9).copy(alpha = 0.14f)
        )
        ExplorerItemKind.BINARY -> Triple(
            Icons.Filled.Memory,
            MaterialTheme.colorScheme.onSurfaceVariant,
            MaterialTheme.colorScheme.surfaceVariant
        )
    }

    Surface(
        modifier = modifier.size(42.dp),
        shape = RoundedCornerShape(10.dp),
        color = container
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = kind.name,
                tint = tint,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
fun FormatBadge(
    label: String,
    accentColor: Color = MaterialTheme.colorScheme.secondary,
    modifier: Modifier = Modifier
) {
    if (label.isBlank()) return
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = accentColor.copy(alpha = 0.15f),
        border = BorderStroke(0.5.dp, accentColor.copy(alpha = 0.4f))
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = accentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
