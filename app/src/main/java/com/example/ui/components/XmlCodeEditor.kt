package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.XmlUtils
import com.example.core.XmlValidationResult
import com.example.ui.theme.AlertRed
import com.example.ui.theme.AmberGold
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.ElectricEmerald
import com.example.ui.theme.JetBrainsMonoFontFamily

@Immutable
data class XmlHighlightPalette(
    val tagColor: Color,
    val attrNameColor: Color,
    val attrValueColor: Color,
    val commentColor: Color,
    val declarationColor: Color,
    val innerValueColor: Color,
    val punctuationColor: Color
)

private val DarkXmlPalette = XmlHighlightPalette(
    tagColor = CyberCyan,
    attrNameColor = AmberGold,
    attrValueColor = ElectricEmerald,
    commentColor = Color(0xFF7D8590),
    declarationColor = Color(0xFFCE93D8),
    innerValueColor = Color(0xFFF0F6FC),
    punctuationColor = Color(0xFF8B949E)
)

private val LightXmlPalette = XmlHighlightPalette(
    tagColor = Color(0xFF0277BD),
    attrNameColor = Color(0xFFB45309),
    attrValueColor = Color(0xFF007A3D),
    commentColor = Color(0xFF6B7280),
    declarationColor = Color(0xFF7E22CE),
    innerValueColor = Color(0xFF111827),
    punctuationColor = Color(0xFF4B5563)
)

private class XmlSyntaxVisualTransformation(
    private val palette: XmlHighlightPalette,
    private val findQuery: String
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        return TransformedText(
            text = buildHighlightedXml(text.text, palette, findQuery),
            offsetMapping = OffsetMapping.Identity
        )
    }
}

/**
 * Dedicated XML/Meta code editor with live syntax highlighting, line numbers,
 * XML well-formedness validation, find-in-file, pretty formatting, and quick snippets.
 */
@Composable
fun XmlCodeEditor(
    fileName: String,
    text: String,
    isEditMode: Boolean,
    hasUnsavedChanges: Boolean,
    isDarkTheme: Boolean,
    onTextChanged: (String) -> Unit,
    onRevertChanges: () -> Unit,
    onSaveChanges: () -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isXml = remember(fileName, text) {
        XmlUtils.isXmlOrMetaFile(fileName) || text.trimStart().startsWith("<")
    }
    val validation = remember(text, isXml) {
        if (isXml) XmlUtils.validateXml(text) else XmlValidationResult.Valid
    }

    val palette = if (isDarkTheme) DarkXmlPalette else LightXmlPalette

    var showFindBar by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }
    var currentMatchIndex by remember { mutableIntStateOf(0) }

    // Keep TextFieldValue synced with external updates (e.g. from Handling Tuner or Format button)
    var fieldValue by remember(fileName) {
        mutableStateOf(TextFieldValue(text))
    }
    if (fieldValue.text != text) {
        val clampedCursor = fieldValue.selection.end.coerceIn(0, text.length)
        fieldValue = TextFieldValue(text = text, selection = TextRange(clampedCursor))
    }

    val matchOffsets = remember(text, findQuery) {
        if (findQuery.isBlank()) emptyList()
        else {
            val list = mutableListOf<Int>()
            var idx = text.indexOf(findQuery, ignoreCase = true)
            while (idx >= 0 && list.size < 200) {
                list.add(idx)
                idx = text.indexOf(findQuery, idx + findQuery.length, ignoreCase = true)
            }
            list
        }
    }

    val lineCount = remember(text) {
        if (text.isEmpty()) 1 else text.count { it == '\n' } + 1
    }

    val verticalScroll = rememberScrollState()
    val horizontalScroll = rememberScrollState()

    val visualTransformation = remember(palette, findQuery, isXml) {
        if (isXml) XmlSyntaxVisualTransformation(palette, findQuery)
        else VisualTransformation.None
    }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Editor Status & Toolbar Row
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // XML Validity Pill
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isXml) {
                            when (validation) {
                                is XmlValidationResult.Valid -> {
                                    Icon(
                                        imageVector = Icons.Filled.CheckCircle,
                                        contentDescription = "XML Valid",
                                        tint = ElectricEmerald,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "XML Valid • $lineCount baris",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = ElectricEmerald,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                is XmlValidationResult.Invalid -> {
                                    Icon(
                                        imageVector = Icons.Filled.ErrorOutline,
                                        contentDescription = "Kesalahan XML",
                                        tint = AlertRed,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Error B${validation.line}:K${validation.column}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = AlertRed,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = "Teks • $lineCount baris",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Toolbar Icon Buttons
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { showFindBar = !showFindBar },
                            modifier = Modifier.testTag("btn_xml_find")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = "Cari dalam XML",
                                tint = if (showFindBar) ElectricEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        if (isEditMode && isXml) {
                            IconButton(
                                onClick = {
                                    XmlUtils.prettyFormatXml(text).onSuccess { formatted ->
                                        onTextChanged(formatted)
                                        onMessage("XML dirapikan.")
                                    }.onFailure { err ->
                                        onMessage("Tidak dapat merapikan XML: ${err.message}")
                                    }
                                },
                                modifier = Modifier.testTag("btn_xml_format")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AutoFixHigh,
                                    contentDescription = "Rapikan Indentasi XML",
                                    tint = CyberCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        if (isEditMode && hasUnsavedChanges) {
                            IconButton(
                                onClick = onRevertChanges,
                                modifier = Modifier.testTag("btn_xml_undo")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Undo,
                                    contentDescription = "Batalkan Perubahan",
                                    tint = AmberGold,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                // Inline Validation Error Message Banner if invalid
                if (isXml && validation is XmlValidationResult.Invalid) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text(
                        text = validation.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = AlertRed,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(AlertRed.copy(alpha = 0.08f))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }

                // Toggleable Find Bar
                AnimatedVisibility(visible = showFindBar) {
                    Column {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedTextField(
                                value = findQuery,
                                onValueChange = {
                                    findQuery = it
                                    currentMatchIndex = 0
                                },
                                placeholder = { Text("Cari tag / nilai (mis. fMass)...") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("input_xml_find")
                            )
                            Text(
                                text = if (matchOffsets.isEmpty()) "0" else "${(currentMatchIndex % matchOffsets.size) + 1}/${matchOffsets.size}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            IconButton(
                                onClick = {
                                    if (matchOffsets.isNotEmpty()) {
                                        currentMatchIndex = (currentMatchIndex - 1 + matchOffsets.size) % matchOffsets.size
                                        val pos = matchOffsets[currentMatchIndex]
                                        fieldValue = fieldValue.copy(selection = TextRange(pos, pos + findQuery.length))
                                    }
                                },
                                enabled = matchOffsets.isNotEmpty()
                            ) {
                                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Temuan sebelumnya")
                            }
                            IconButton(
                                onClick = {
                                    if (matchOffsets.isNotEmpty()) {
                                        currentMatchIndex = (currentMatchIndex + 1) % matchOffsets.size
                                        val pos = matchOffsets[currentMatchIndex]
                                        fieldValue = fieldValue.copy(selection = TextRange(pos, pos + findQuery.length))
                                    }
                                },
                                enabled = matchOffsets.isNotEmpty()
                            ) {
                                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Temuan berikutnya")
                            }
                            IconButton(onClick = {
                                showFindBar = false
                                findQuery = ""
                            }) {
                                Icon(Icons.Filled.Close, contentDescription = "Tutup pencarian")
                            }
                        }
                    }
                }
            }
        }

        // Quick Snippet Bar when Editing XML
        if (isEditMode && isXml) {
            val snippets = remember {
                listOf(
                    "value=\"\"" to "value=\"\"",
                    "<Item>" to "<Item>\n  \n</Item>",
                    "<!-- -->" to "<!-- catatan -->",
                    "handling" to "<fMass value=\"1500.000000\" />"
                )
            }
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(snippets) { (label, snippet) ->
                    AssistChip(
                        onClick = {
                            val selStart = fieldValue.selection.min.coerceIn(0, text.length)
                            val selEnd = fieldValue.selection.max.coerceIn(selStart, text.length)
                            val updated = text.substring(0, selStart) + snippet + text.substring(selEnd)
                            val newCursor = selStart + snippet.length
                            fieldValue = TextFieldValue(updated, TextRange(newCursor))
                            onTextChanged(updated)
                        },
                        label = {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    )
                }
            }
        }

        // Main Syntax-Highlighted Editor Surface with Line Number Gutter
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(
                1.dp,
                if (isEditMode) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.outlineVariant
            ),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val editorMinWidth = (maxWidth - 52.dp).coerceAtLeast(200.dp)
                val lineNumbersText = remember(lineCount) {
                    (1..lineCount).joinToString("\n")
                }

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(verticalScroll)
                ) {
                    // Line Number Gutter
                    Box(
                        modifier = Modifier
                            .width(46.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                            .padding(vertical = 12.dp, horizontal = 6.dp)
                    ) {
                        Text(
                            text = lineNumbersText,
                            style = TextStyle(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 12.sp,
                                lineHeight = 19.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.End
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Syntax-Highlighted Editable Code Field
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(horizontalScroll)
                            .padding(12.dp)
                    ) {
                        BasicTextField(
                            value = fieldValue,
                            onValueChange = { newVal ->
                                if (isEditMode) {
                                    fieldValue = newVal
                                    if (newVal.text != text) {
                                        onTextChanged(newVal.text)
                                    }
                                }
                            },
                            readOnly = !isEditMode,
                            textStyle = TextStyle(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 12.sp,
                                lineHeight = 19.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(ElectricEmerald),
                            visualTransformation = visualTransformation,
                            modifier = Modifier
                                .widthIn(min = editorMinWidth)
                                .testTag("code_editor_field")
                        )
                    }
                }
            }
        }

        // Save Action Button when modified
        if (isEditMode && hasUnsavedChanges) {
            Button(
                onClick = onSaveChanges,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("btn_save_code")
            ) {
                Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (validation is XmlValidationResult.Invalid) {
                        "Simpan Paksa (Ada Error XML)"
                    } else {
                        "Simpan & Repack ke Arsip"
                    }
                )
            }
        }
    }
}

/**
 * Produces an AnnotatedString whose plain text length is STRICTLY identical to [source]
 * so OffsetMapping.Identity remains exact during live editing.
 */
internal fun buildHighlightedXml(
    source: String,
    palette: XmlHighlightPalette,
    findQuery: String = ""
): AnnotatedString {
    val maxHighlightLen = 60000
    val head = if (source.length > maxHighlightLen) source.substring(0, maxHighlightLen) else source
    val tail = if (source.length > maxHighlightLen) source.substring(maxHighlightLen) else ""

    return buildAnnotatedString {
        var idx = 0
        val tokenRegex = Regex(
            """<!--[\s\S]*?-->|<\?[\s\S]*?\?>|</?[A-Za-z0-9_:\-]+|/?\s*>|[A-Za-z0-9_:\-]+="[^"]*"|[A-Za-z0-9_:\-]+='[^']*'"""
        )
        for (match in tokenRegex.findAll(head)) {
            if (match.range.first > idx) {
                val plain = head.substring(idx, match.range.first)
                withStyle(SpanStyle(color = palette.innerValueColor)) {
                    append(plain)
                }
            }
            val token = match.value
            when {
                token.startsWith("<!--") -> {
                    withStyle(SpanStyle(color = palette.commentColor, fontStyle = FontStyle.Italic)) {
                        append(token)
                    }
                }
                token.startsWith("<?") -> {
                    withStyle(SpanStyle(color = palette.declarationColor, fontWeight = FontWeight.Medium)) {
                        append(token)
                    }
                }
                token.startsWith("<") -> {
                    val bracketLen = if (token.startsWith("</")) 2 else 1
                    withStyle(SpanStyle(color = palette.punctuationColor)) {
                        append(token.substring(0, bracketLen))
                    }
                    withStyle(SpanStyle(color = palette.tagColor, fontWeight = FontWeight.SemiBold)) {
                        append(token.substring(bracketLen))
                    }
                }
                token.endsWith(">") -> {
                    withStyle(SpanStyle(color = palette.punctuationColor, fontWeight = FontWeight.SemiBold)) {
                        append(token)
                    }
                }
                token.contains("=") -> {
                    val eq = token.indexOf('=')
                    withStyle(SpanStyle(color = palette.attrNameColor)) {
                        append(token.substring(0, eq))
                    }
                    withStyle(SpanStyle(color = palette.punctuationColor)) {
                        append("=")
                    }
                    withStyle(SpanStyle(color = palette.attrValueColor)) {
                        append(token.substring(eq + 1))
                    }
                }
                else -> append(token)
            }
            idx = match.range.last + 1
        }
        if (idx < head.length) {
            withStyle(SpanStyle(color = palette.innerValueColor)) {
                append(head.substring(idx))
            }
        }
        if (tail.isNotEmpty()) {
            append(tail)
        }

        // Highlight find matches if present
        if (findQuery.isNotBlank()) {
            var searchIdx = source.indexOf(findQuery, ignoreCase = true)
            var count = 0
            while (searchIdx >= 0 && count < 200) {
                addStyle(
                    style = SpanStyle(
                        background = AmberGold.copy(alpha = 0.35f),
                        fontWeight = FontWeight.Bold
                    ),
                    start = searchIdx,
                    end = searchIdx + findQuery.length
                )
                searchIdx = source.indexOf(findQuery, searchIdx + findQuery.length, ignoreCase = true)
                count++
            }
        }
    }
}
