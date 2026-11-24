package it.keysng.dx.storage

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract

data class VaultDocument(
    val name: String,
    val uri: Uri,
    val documentId: String,
    val isDirectory: Boolean,
)

class VaultDocumentTree(
    private val contentResolver: ContentResolver,
    val treeUri: Uri,
) {
    val rootDocumentId: String = DocumentsContract.getTreeDocumentId(treeUri)

    fun listChildren(parentDocumentId: String): List<VaultDocument> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocumentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
        val result = mutableListOf<VaultDocument>()
        contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            while (cursor.moveToNext()) {
                val id = cursor.getString(idColumn)
                val name = cursor.getString(nameColumn) ?: continue
                val mimeType = cursor.getString(mimeColumn)
                result += VaultDocument(
                    name = name,
                    uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id),
                    documentId = id,
                    isDirectory = mimeType == DocumentsContract.Document.MIME_TYPE_DIR,
                )
            }
        } ?: error("Android non riesce a leggere questa cartella")
        return result
    }

    fun requiredFile(parentDocumentId: String, name: String): VaultDocument =
        listChildren(parentDocumentId).singleOrNull { it.name == name && !it.isDirectory }
            ?: error("Nel vault manca il file '$name'")

    fun optionalFile(parentDocumentId: String, name: String): VaultDocument? =
        listChildren(parentDocumentId).singleOrNull { it.name == name && !it.isDirectory }

    fun requiredDirectory(parentDocumentId: String, name: String): VaultDocument =
        listChildren(parentDocumentId).singleOrNull { it.name == name && it.isDirectory }
            ?: error("Nel vault manca la cartella '$name'")

    fun readBytes(document: VaultDocument): ByteArray =
        contentResolver.openInputStream(document.uri)?.use { input -> input.readBytes() }
            ?: error("Impossibile aprire '${document.name}'")

    fun findRecordUris(recordsDocumentId: String): Map<String, Uri> =
        listChildren(recordsDocumentId)
            .filterNot(VaultDocument::isDirectory)
            .mapNotNull { document ->
                if (document.name.endsWith(".gpg")) {
                    document.name.removeSuffix(".gpg").let { id -> id to document.uri }
                } else {
                    null
                }
            }
            .toMap()
}
