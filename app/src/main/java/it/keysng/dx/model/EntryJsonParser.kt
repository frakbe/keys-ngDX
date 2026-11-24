package it.keysng.dx.model

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Reads the schema-1 JSON payload emitted by Keys NG for one encrypted record. */
object EntryJsonParser {
    fun parse(json: String): CredentialEntry {
        val root = JSONObject(json)
        val schema = root.optInt("schema", -1)
        require(schema == 1) { "Schema voce non supportato: $schema" }

        val id = root.getString("id")
        UUID.fromString(id)

        val title = root.getString("title").trim()
        require(title.isNotEmpty()) { "Il titolo della voce è vuoto" }

        val revision = root.getInt("revision")
        require(revision > 0) { "Revisione della voce non valida" }

        val customFields = linkedMapOf<String, String>()
        val customObject = root.optJSONObject("custom_fields") ?: JSONObject()
        val customKeys = customObject.keys()
        while (customKeys.hasNext()) {
            val key = customKeys.next()
            val value = customObject.opt(key)
            require(value is String) { "Il campo personalizzato '$key' non è una stringa" }
            customFields[key] = value
        }

        return CredentialEntry(
            id = id,
            title = title,
            kind = root.optString("kind", "account"),
            usernames = root.optJSONArray("usernames").stringValues(),
            password = root.optionalString("password"),
            notes = root.optString("notes", ""),
            customFields = customFields,
            totp = root.optJSONArray("totp").objects().map { token ->
                TotpField(
                    secret = token.getString("secret"),
                    issuer = token.optString("issuer", ""),
                    accountName = token.optString("account_name", ""),
                    algorithm = token.optString("algorithm", "SHA1"),
                    digits = token.optInt("digits", 6),
                    period = token.optInt("period", 30),
                )
            },
            actions = root.optJSONArray("actions").objects().map { action ->
                EntryAction(
                    type = action.getString("type"),
                    url = action.optionalString("url"),
                    host = action.optionalString("host"),
                )
            },
            tags = root.optJSONArray("tags").stringValues(),
            revision = revision,
            folderId = root.optionalString("folder_id")?.also { UUID.fromString(it) },
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
