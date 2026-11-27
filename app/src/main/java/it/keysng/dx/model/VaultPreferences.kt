package it.keysng.dx.model

import org.json.JSONObject
import java.util.Locale

enum class FolderStartupView { EXPANDED, COMPACT }

data class VaultPreferences(
    val folderStartupView: FolderStartupView,
) {
    companion object {
        /** Reads the optional per-vault UI preference from the clear-text vault.json. */
        fun parse(json: String): VaultPreferences {
            val root = JSONObject(json)
            require(root.optInt("schema", -1) == 1) { "Schema vault.json non supportato" }
            val value = root.optString("tree_startup_view", "expanded").trim().lowercase(Locale.ROOT)
            val view = when (value) {
                "expanded" -> FolderStartupView.EXPANDED
                "compact" -> FolderStartupView.COMPACT
                else -> throw IllegalArgumentException("tree_startup_view in vault.json deve essere 'expanded' o 'compact'")
            }
            return VaultPreferences(view)
        }
    }
}
