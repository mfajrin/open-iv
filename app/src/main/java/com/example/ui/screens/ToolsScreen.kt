package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.AppSettings
import com.example.ui.ToolsUiState
import com.example.ui.components.FormatBadge
import com.example.ui.theme.AmberGold
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.ElectricEmerald

@Composable
fun ToolsScreen(
    state: ToolsUiState,
    settings: AppSettings,
    onHashInputChange: (String, Boolean) -> Unit,
    onDictionaryFilterChange: (String) -> Unit,
    onAnalyzeHeaderClick: () -> Unit,
    onToggleDarkTheme: (Boolean) -> Unit,
    onToggleAutoBackup: (Boolean) -> Unit,
    onResetSandbox: () -> Unit,
    onCopyFeedback: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboardManager.current

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
            // 1. JOAAT Hash Calculator
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Tag, contentDescription = null, tint = ElectricEmerald)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Kalkulator Hash JOAAT", style = MaterialTheme.typography.titleMedium)
                            }
                            FormatBadge("GTA V", ElectricEmerald)
                        }

                        OutlinedTextField(
                            value = state.hashInput,
                            onValueChange = { onHashInputChange(it, state.forceLowercase) },
                            label = { Text("Nama Model / String (mis. adder, handling.meta)") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_joaat")
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable {
                                onHashInputChange(state.hashInput, !state.forceLowercase)
                            }
                        ) {
                            Checkbox(
                                checked = state.forceLowercase,
                                onCheckedChange = { onHashInputChange(state.hashInput, it) }
                            )
                            Text(
                                text = "Konversi huruf kecil otomatis (standar RAGE)",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        HashValueRow(
                            label = "Hex (32-bit)",
                            value = state.hashResult.hexUpper,
                            onCopy = {
                                clipboard.setText(AnnotatedString(state.hashResult.hexUpper))
                                onCopyFeedback("Hash Hex disalin: ${state.hashResult.hexUpper}")
                            }
                        )
                        HashValueRow(
                            label = "Unsigned Int32",
                            value = state.hashResult.unsignedHash.toString(),
                            onCopy = {
                                val v = state.hashResult.unsignedHash.toString()
                                clipboard.setText(AnnotatedString(v))
                                onCopyFeedback("Unsigned hash disalin: $v")
                            }
                        )
                        HashValueRow(
                            label = "Signed Int32",
                            value = state.hashResult.signedHash.toString(),
                            onCopy = {
                                val v = state.hashResult.signedHash.toString()
                                clipboard.setText(AnnotatedString(v))
                                onCopyFeedback("Signed hash disalin: $v")
                            }
                        )
                    }
                }
            }

            // 2. Binary Header Analyzer
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.DocumentScanner, contentDescription = null, tint = CyberCyan)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Analisis Header RPF7 / RSC7 / DDS", style = MaterialTheme.typography.titleMedium)
                            }
                            FormatBadge("BINER", CyberCyan)
                        }
                        Text(
                            text = "Pilih berkas apa pun untuk membaca signature magic, jumlah entri RPF, flag enkripsi, atau halaman virtual RSC7.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = onAnalyzeHeaderClick,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("btn_analyze_header")
                        ) {
                            Text("Pilih Berkas untuk Dianalisis")
                        }

                        val analysis = state.headerAnalysis
                        if (analysis != null) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "${analysis.fileName} — ${analysis.detectedFormat}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = ElectricEmerald
                                    )
                                    Text(
                                        text = "Magic: ${analysis.magicHex} ('${analysis.magicAscii}')",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                    analysis.details.forEach { (k, v) ->
                                        Text(
                                            text = "$k: $v",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. Settings & Sandbox Reset
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Pengaturan Studio", style = MaterialTheme.typography.titleMedium)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Tema Gelap Workstation", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Tampilan kontras tinggi untuk membaca XML & Hex",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = settings.isDarkTheme,
                                onCheckedChange = onToggleDarkTheme,
                                modifier = Modifier.testTag("switch_dark_theme")
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Backup Otomatis Saat Edit", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Buat salinan .bak sebelum me-repack arsip RPF7",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = settings.autoBackupOnEdit,
                                onCheckedChange = onToggleAutoBackup,
                                modifier = Modifier.testTag("switch_auto_backup")
                            )
                        }

                        OutlinedButton(
                            onClick = onResetSandbox,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("btn_reset_sandbox")
                        ) {
                            Icon(Icons.Filled.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Pulihkan Sandbox GTA V ke Default")
                        }
                    }
                }
            }

            // 4. GTA V Known Hash Dictionary
            item {
                Text("Kamus Hash GTA V", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = state.dictionaryFilter,
                    onValueChange = onDictionaryFilterChange,
                    label = { Text("Cari nama model atau hex (mis. adder, 0xB779)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            items(state.dictionaryResults) { (res, desc) ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onHashInputChange(res.input, true) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(res.input, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        FormatBadge(res.hexUpper, AmberGold)
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun HashValueRow(
    label: String,
    value: String,
    onCopy: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.labelMedium, color = ElectricEmerald)
            }
            IconButton(onClick = onCopy) {
                Icon(Icons.Filled.ContentCopy, contentDescription = "Salin $label", modifier = Modifier.size(18.dp))
            }
        }
    }
}
