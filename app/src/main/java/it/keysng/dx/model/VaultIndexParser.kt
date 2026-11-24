package it.keysng.dx.model

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object VaultIndexParser {
    fun parseCatalog(json: String): VaultCatalog {
        val root = JSONObject(json)
        require(root.optInt("schema", -1) == 1) { "Schema catalogo non supportato" }
        val items = root.optJSONArray("items").objects().map { item ->
            val id = item.getString("id").also { UUID.fromString(it) }
            val title = item.getString("title").trim()
            require(title.isNotEmpty()) { "Il catalogo contiene una voce senza titolo" }
            val revision = item.getInt("revision")
            require(revision > 0) { "Revisione non valida nel catalogo" }
            val hash = item.optionalString("ciphertext_sha256")
            require(hash == null || hash.matches(Regex("[0-9a-fA-F]{64}"))) {
                "Hash ciphertext non valido nel catalogo"
            }
            CatalogItem(
                id = id,
                title = title,
                kind = item.optString("kind", "account"),
                usernames = item.optJSONArray("usernames").stringValues(),
                tags = item.optJSONArray("tags").stringValues(),
                urlHosts = item.optJSONArray("url_hosts").stringValues(),
                capabilities = item.optJSONArray("capabilities").stringValues(),
                revision = revision,
                ciphertextSha256 = hash,
                folderId = item.optionalString("folder_id")?.also { UUID.fromString(it) },
            )
        }
        require(items.map(CatalogItem::id).distinct().size == items.size) {
            "Il catalogo contiene UUID duplicati"
        }
        return VaultCatalog(items, root.optJSONArray("folders").folders())
    }

    fun parseFolders(json: String): VaultFolderStore {
        val root = JSONObject(json)
        require(root.optInt("schema", -1) == 1) { "Schema cartelle non supportato" }
        return VaultFolderStore(root.optJSONArray("folders").folders()).also {
            validateFolderTree(it.folders)
        }
    }

    fun validateFolderTree(folders: List<VaultFolder>) {
        require(folders.map(VaultFolder::id).distinct().size == folders.size) {
            "L'elenco cartelle contiene UUID duplicati"
        }
        val byId = folders.associateBy(VaultFolder::id)
        folders.forEach { folder ->
            require(folder.name.isNotBlank()) { "Il nome di una cartella è vuoto" }
            require(folder.parentId == null || byId.containsKey(folder.parentId)) {
                "La cartella '${folder.name}' ha un genitore inesistente"
            }
        }
        folders.forEach { start ->
            val seen = mutableSetOf<String>()
            var current: VaultFolder? = start
            while (current != null) {
                require(seen.add(current.id)) { "La struttura delle cartelle contiene un ciclo" }
                current = current.parentId?.let(byId::get)
            }
        }
    }

    private fun JSONArray?.folders(): List<VaultFolder> = objects().map { folder ->
        VaultFolder(
            id = folder.getString("id").also { UUID.fromString(it) },
            name = folder.getString("name"),
            parentId = folder.optionalString("parent_id")?.also { UUID.fromString(it) },
        )
    }

    private fun JSONObject.optionalString(key: String): String? =
        if (!has(key) || isNull(key)) null else getString(key)

    private fun JSONArray?.stringValues(): List<String> {
        if (this == null) return emptyList()
        return (0 until length()).map { index -> getString(index) }
    }

    private fun JSONArray?.objects(): List<JSONObject> {
        if (this == null) return emptyList()
        return (0 until length()).map { index -> getJSONObject(index) }
    }
}
