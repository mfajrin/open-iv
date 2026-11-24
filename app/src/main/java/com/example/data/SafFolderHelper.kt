package com.example.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SafDocItem(
    val name: String,
    val uri: Uri,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val mimeType: String
)

object SafFolderHelper {

    suspend fun listChildren(context: Context, treeUri: Uri, parentDocId: String? = null): Pair<String, List<SafDocItem>> =
        withContext(Dispatchers.IO) {
            val docId = parentDocId ?: DocumentsContract.getTreeDocumentId(treeUri)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
            val items = mutableListOf<SafDocItem>()
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE
            )
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
                while (cursor.moveToNext()) {
                    val childId = cursor.getString(idIdx)
                    val name = cursor.getString(nameIdx) ?: "berkas"
                    val mime = cursor.getString(mimeIdx) ?: "application/octet-stream"
                    val size = cursor.getLong(sizeIdx)
                    val isDir = mime == DocumentsContract.Document.MIME_TYPE_DIR
                    val childUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                    items.add(SafDocItem(name, childUri, isDir, size, mime))
                }
            }
            val sorted = items.sortedWith(compareBy<SafDocItem> { !it.isDirectory }.thenBy { it.name.lowercase() })
            val folderLabel = docId.substringAfterLast(':').substringAfterLast('/').ifEmpty { "Folder Eksternal" }
            folderLabel to sorted
        }

    fun docIdFromUri(uri: Uri): String? = try {
        DocumentsContract.getDocumentId(uri)
    } catch (_: Exception) {
        null
    }
}
