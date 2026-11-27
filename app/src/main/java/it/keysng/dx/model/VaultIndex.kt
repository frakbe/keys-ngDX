package it.keysng.dx.model

data class CatalogItem(
    val id: String,
    val title: String,
    val kind: String,
    val usernames: List<String>,
    val tags: List<String>,
    val urlHosts: List<String>,
    val capabilities: List<String>,
    val revision: Int,
    val ciphertextSha256: String?,
    val folderId: String?,
)

data class VaultFolder(
    val id: String,
    val name: String,
    val parentId: String?,
)

data class VaultCatalog(
    val items: List<CatalogItem>,
    val folders: List<VaultFolder>,
)

data class VaultFolderStore(val folders: List<VaultFolder>)
