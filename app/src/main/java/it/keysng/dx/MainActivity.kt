package it.keysng.dx

import android.app.Activity
import android.app.ActivityOptions
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import it.keysng.dx.crypto.OpenPgpProviderClient
import it.keysng.dx.model.CatalogItem
import it.keysng.dx.model.CredentialEntry
import it.keysng.dx.model.EntryAction
import it.keysng.dx.model.EntryJsonParser
import it.keysng.dx.model.VaultCatalog
import it.keysng.dx.model.VaultFolder
import it.keysng.dx.model.VaultFolderStore
import it.keysng.dx.model.VaultIndexParser
import it.keysng.dx.storage.VaultDocument
import it.keysng.dx.storage.VaultDocumentTree
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest

class MainActivity : ComponentActivity() {
    private lateinit var content: LinearLayout
    private lateinit var statusView: TextView
    private lateinit var chooseVaultButton: Button
    private lateinit var refreshButton: Button
    private lateinit var providerClient: OpenPgpProviderClient
    private lateinit var entryBackCallback: OnBackPressedCallback

    private var providerReady = false
    private var busy = false
    private var selectedVaultUri: Uri? = null
    private var documentTree: VaultDocumentTree? = null
    private var foldersDocument: VaultDocument? = null
    private var catalog: VaultCatalog? = null
    private var folders: VaultFolderStore? = null
    private var recordUris: Map<String, Uri> = emptyMap()
    private var currentEntry: CredentialEntry? = null
    private val collapsedFolders = mutableSetOf<String>()
    private var pendingDecryption: PendingDecryption? = null
    private var clipboardGeneration = 0
    private val handler = Handler(Looper.getMainLooper())

    private data class PendingDecryption(
        val uri: Uri,
        var requestData: Intent,
        val onSuccess: (ByteArray, String) -> Unit,
    )

    private val openVaultTree = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) selectVault(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE,
        )
        buildScreen()
        entryBackCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                currentEntry = null
                isEnabled = false
                renderBrowser()
                setStatus("Elenco del vault")
            }
        }
        onBackPressedDispatcher.addCallback(this, entryBackCallback)

        providerClient = OpenPgpProviderClient(
            context = applicationContext,
            listener = object : OpenPgpProviderClient.Listener {
                override fun onProviderReady() {
                    providerReady = true
                    setBusy(false)
                    val saved = getPreferences(Context.MODE_PRIVATE).getString(PREF_VAULT_URI, null)
                    if (saved != null && selectedVaultUri == null) {
                        loadVault(Uri.parse(saved))
                    } else {
                        setStatus("PGPony connesso. Seleziona la cartella di un vault Keys NG.")
                        renderBrowser()
                    }
                }

                override fun onProviderError(message: String) {
                    providerReady = false
                    setBusy(false)
                    setStatus(message)
                }
            },
        )
        setBusy(true)
        setStatus("Connessione al provider OpenPGP…")
        renderWelcome()
        providerClient.bind()
    }

    override fun onStop() {
        super.onStop()
        if (currentEntry != null) {
            currentEntry = null
            entryBackCallback.isEnabled = false
            renderBrowser()
            setStatus("Dettagli nascosti in background. Seleziona di nuovo la voce per consultarli.")
        }
    }

    override fun onDestroy() {
        if (::providerClient.isInitialized) providerClient.unbind()
        super.onDestroy()
    }

    @Deprecated("The provider IntentSender needs Android 14 ActivityOptions")
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != PROVIDER_INTERACTION_REQUEST) return
        val operation = pendingDecryption
        if (resultCode == Activity.RESULT_OK && operation != null) {
            runProviderDecrypt(operation, data ?: operation.requestData)
        } else {
            pendingDecryption = null
            setBusy(false)
            setStatus(
                "PGPony non ha completato la richiesta (codice $resultCode). " +
                    "Se compare il dialogo, scegli Allow e completa l'eventuale sblocco della chiave."
            )
        }
    }

    private fun buildScreen() {
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(24))
        }
        scroll.addView(root)

        root.addView(TextView(this).apply {
            text = "Keys NG DX"
            textSize = 26f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "Vault Keys NG · sola lettura"
            textSize = 14f
            setPadding(0, dp(4), 0, dp(14))
        })

        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        chooseVaultButton = Button(this).apply {
            text = "Apri vault"
            isAllCaps = false
            setOnClickListener {
                val previous = selectedVaultUri
                openVaultTree.launch(previous)
            }
        }
        refreshButton = Button(this).apply {
            text = "Aggiorna"
            isAllCaps = false
            setOnClickListener { selectedVaultUri?.let(::loadVault) }
        }
        toolbar.addView(chooseVaultButton, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        toolbar.addView(refreshButton)
        root.addView(toolbar)

        statusView = TextView(this).apply {
            textSize = 14f
            setPadding(0, dp(10), 0, dp(10))
        }
        root.addView(statusView)

        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(content)

        root.addView(Button(this).apply {
            text = "Licenza e informazioni"
            isAllCaps = false
            setOnClickListener {
                android.app.AlertDialog.Builder(this@MainActivity)
                    .setTitle("Informazioni")
                    .setMessage(
                        "Keys NG DX · lettore Android in sola lettura\n\n" +
                            "Le chiavi OpenPGP restano in PGPony. Questo client non modifica il vault e non esegue azioni SSH/RDP/comando.\n\n" +
                            "Copyright © 2026 Franco ‘frakbe’ Bersani · GNU GPL v3 o successiva.\n" +
                            "https://www.gnu.org/licenses/gpl-3.0.html",
                    )
                    .setPositiveButton("Chiudi", null)
                    .show()
            }
        })
        setContentView(scroll)
    }

    private fun renderWelcome() {
        content.removeAllViews()
        content.addView(TextView(this).apply {
            text = "Scegli la cartella principale del vault. Keys NG DX leggerà catalogo, cartelle e record senza modificare i file."
            textSize = 16f
            setPadding(0, dp(10), 0, dp(16))
        })
    }

    private fun selectVault(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
            setStatus("Accesso temporaneo alla cartella; selezionala di nuovo se Android revoca il permesso.")
        }
        selectedVaultUri = uri
        getPreferences(Context.MODE_PRIVATE).edit().putString(PREF_VAULT_URI, uri.toString()).apply()
        loadVault(uri)
    }

    private fun loadVault(uri: Uri) {
        if (!providerReady) {
            setStatus("Provider OpenPGP non connesso")
            return
        }
        selectedVaultUri = uri
        currentEntry = null
        catalog = null
        folders = null
        documentTree = null
        recordUris = emptyMap()
        entryBackCallback.isEnabled = false
        setBusy(true)
        setStatus("Ricerca catalogo, cartelle e record…")
        content.removeAllViews()
        content.addView(TextView(this).apply { text = "Apertura del vault…" })

        try {
            val tree = VaultDocumentTree(contentResolver, uri)
            val catalogFile = tree.requiredFile(tree.rootDocumentId, "catalog.gpg")
            val foldersFile = tree.optionalFile(tree.rootDocumentId, "folders.gpg")
            val recordsDir = tree.requiredDirectory(tree.rootDocumentId, "records")
            val foundRecords = tree.findRecordUris(recordsDir.documentId)
            documentTree = tree
            foldersDocument = foldersFile
            recordUris = foundRecords

            decryptDocument(catalogFile.uri) { plaintext, catalogSignature ->
                val parsedCatalog = try {
                    VaultIndexParser.parseCatalog(decodeUtf8(plaintext))
                } catch (error: Exception) {
                    throw IllegalArgumentException("Catalogo non valido: ${error.message}", error)
                }
                val folderFile = foldersDocument
                if (folderFile == null) {
                    completeVaultLoad(parsedCatalog, parsedCatalog.folders, catalogSignature, "cartelle dal catalogo")
                } else {
                    decryptDocument(folderFile.uri) { folderPlaintext, folderSignature ->
                        val folderStore = try {
                            VaultIndexParser.parseFolders(decodeUtf8(folderPlaintext))
                        } catch (error: Exception) {
                            throw IllegalArgumentException("Elenco cartelle non valido: ${error.message}", error)
                        }
                        completeVaultLoad(parsedCatalog, folderStore.folders, catalogSignature, folderSignature)
                    }
                }
            }
        } catch (error: Exception) {
            setBusy(false)
            setStatus(error.message ?: "Impossibile aprire il vault selezionato")
            renderWelcome()
        }
    }

    private fun completeVaultLoad(
        loadedCatalog: VaultCatalog,
        loadedFolders: List<VaultFolder>,
        catalogSignature: String,
        foldersSignature: String,
    ) {
        try {
            VaultIndexParser.validateFolderTree(loadedFolders)
            val folderIds = loadedFolders.map(VaultFolder::id).toSet()
            require(loadedCatalog.items.all { it.folderId == null || it.folderId in folderIds }) {
                "Il catalogo assegna una voce a una cartella inesistente"
            }
            if (foldersDocument != null && loadedCatalog.folders.isNotEmpty()) {
                val catalogSnapshot = loadedCatalog.folders.sortedBy(VaultFolder::id)
                val folderFile = loadedFolders.sortedBy(VaultFolder::id)
                require(catalogSnapshot == folderFile) {
                    "L'elenco cartelle non corrisponde allo snapshot del catalogo"
                }
            }
            catalog = loadedCatalog
            folders = VaultFolderStore(loadedFolders)
            collapsedFolders.clear()
            setBusy(false)
            setStatus(
                "Vault caricato · ${loadedCatalog.items.size} voci · catalogo: $catalogSignature · cartelle: $foldersSignature"
            )
            renderBrowser()
        } catch (error: Exception) {
            setBusy(false)
            setStatus(error.message ?: "Metadati del vault non coerenti")
            renderWelcome()
        }
    }

    private fun decryptDocument(uri: Uri, onSuccess: (ByteArray, String) -> Unit) {
        if (!providerReady) {
            setBusy(false)
            setStatus("Provider OpenPGP non connesso")
            return
        }
        runProviderDecrypt(PendingDecryption(uri, Intent(), onSuccess))
    }

    private fun runProviderDecrypt(operation: PendingDecryption, requestData: Intent = operation.requestData) {
        pendingDecryption = operation
        providerClient.decrypt(operation.uri, requestData) { outcome ->
            when (outcome) {
                is OpenPgpProviderClient.Outcome.InteractionRequired -> {
                    operation.requestData = outcome.requestData
                    pendingDecryption = operation
                    launchProviderInteraction(outcome.pendingIntent.intentSender)
                }

                is OpenPgpProviderClient.Outcome.Failure -> {
                    pendingDecryption = null
                    setBusy(false)
                    setStatus(outcome.message)
                }

                is OpenPgpProviderClient.Outcome.Success -> {
                    pendingDecryption = null
                    try {
                        operation.onSuccess(outcome.plaintext, outcome.signatureStatus)
                    } catch (error: Exception) {
                        setBusy(false)
                        setStatus(error.message ?: "Errore durante la lettura del contenuto decifrato")
                    } finally {
                        outcome.plaintext.fill(0)
                    }
                }
            }
        }
    }

    private fun launchProviderInteraction(intentSender: IntentSender) {
        val options = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ActivityOptions.makeBasic()
                .setPendingIntentBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED,
                )
                .toBundle()
        } else {
            null
        }
        try {
            @Suppress("DEPRECATION")
            startIntentSenderForResult(
                intentSender,
                PROVIDER_INTERACTION_REQUEST,
                null,
                0,
                0,
                0,
                options,
            )
        } catch (_: IntentSender.SendIntentException) {
            pendingDecryption = null
            setBusy(false)
            setStatus("Android non ha potuto avviare la schermata di PGPony")
        }
    }

    private fun openEntry(item: CatalogItem) {
        val uri = recordUris[item.id]
        if (uri == null) {
            setStatus("File del record mancante: ${item.id}.gpg")
            return
        }
        val tree = documentTree ?: return
        setBusy(true)
        currentEntry = null
        setStatus("Verifica e apertura di '${item.title}'…")
        content.removeAllViews()
        content.addView(TextView(this).apply { text = "Decifratura della voce…" })

        try {
            val encrypted = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("Impossibile leggere il file della voce")
            try {
                val expectedHash = item.ciphertextSha256
                if (expectedHash != null) {
                    val actualHash = MessageDigest.getInstance("SHA-256")
                        .digest(encrypted)
                        .joinToString("") { "%02x".format(it) }
                    require(actualHash.equals(expectedHash, ignoreCase = true)) {
                        "Il record non corrisponde all'hash del catalogo; la voce potrebbe essere stata sostituita"
                    }
                }
            } finally {
                encrypted.fill(0)
            }

            decryptDocument(uri) { plaintext, signatureStatus ->
                val entry = EntryJsonParser.parse(decodeUtf8(plaintext))
                require(entry.id == item.id) { "UUID del record diverso dal catalogo" }
                require(entry.title == item.title) { "Titolo del record diverso dal catalogo" }
                require(entry.revision == item.revision) { "Revisione del record diversa dal catalogo" }
                require(entry.folderId == item.folderId) { "Cartella del record diversa dal catalogo" }
                currentEntry = entry
                entryBackCallback.isEnabled = true
                setBusy(false)
                setStatus("Voce decifrata · $signatureStatus")
                renderEntry(entry)
            }
        } catch (error: Exception) {
            setBusy(false)
            setStatus(error.message ?: "Impossibile aprire la voce")
            renderBrowser()
        }
    }

    private fun renderBrowser() {
        content.removeAllViews()
        val currentCatalog = catalog
        val currentFolders = folders
        if (currentCatalog == null || currentFolders == null) {
            renderWelcome()
            return
        }

        content.addView(TextView(this).apply {
            text = "${currentCatalog.items.size} voci"
            textSize = 18f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, dp(4), 0, dp(8))
        })
        val foldersByParent = currentFolders.folders.groupBy(VaultFolder::parentId)
        val entriesByFolder = currentCatalog.items.groupBy(CatalogItem::folderId)

        fun addEntry(item: CatalogItem, depth: Int) {
            content.addView(Button(this).apply {
                text = item.title
                isAllCaps = false
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                setPadding(dp(12 + depth * 20), dp(8), dp(12), dp(8))
                setOnClickListener { openEntry(item) }
            })
        }

        fun addFolder(folder: VaultFolder, depth: Int) {
            val collapsed = folder.id in collapsedFolders
            content.addView(Button(this).apply {
                text = "${if (collapsed) "▸" else "▾"}  ${folder.name}"
                isAllCaps = false
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                setPadding(dp(12 + depth * 20), dp(8), dp(12), dp(8))
                setOnClickListener {
                    if (collapsed) collapsedFolders.remove(folder.id) else collapsedFolders.add(folder.id)
                    renderBrowser()
                }
            })
            if (!collapsed) {
                foldersByParent[folder.id].orEmpty().sortedBy { it.name.lowercase() }
                    .forEach { addFolder(it, depth + 1) }
                entriesByFolder[folder.id].orEmpty().sortedBy { it.title.lowercase() }
                    .forEach { addEntry(it, depth + 1) }
            }
        }

        foldersByParent[null].orEmpty().sortedBy { it.name.lowercase() }
            .forEach { addFolder(it, 0) }
        val looseEntries = entriesByFolder[null].orEmpty().sortedBy { it.title.lowercase() }
        if (looseEntries.isNotEmpty()) {
            content.addView(TextView(this).apply {
                text = "Senza cartella"
                textSize = 15f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(dp(4), dp(14), 0, dp(4))
            })
            looseEntries.forEach { addEntry(it, 0) }
        }
        if (currentCatalog.items.isEmpty() && currentFolders.folders.isEmpty()) {
            content.addView(TextView(this).apply { text = "Il vault non contiene voci." })
        }
    }

    private fun renderEntry(entry: CredentialEntry) {
        content.removeAllViews()
        content.addView(Button(this).apply {
            text = "← Elenco del vault"
            isAllCaps = false
            setOnClickListener {
                currentEntry = null
                entryBackCallback.isEnabled = false
                renderBrowser()
                setStatus("Elenco del vault")
            }
        })
        addSection("Titolo", entry.title)
        entry.usernames.forEachIndexed { index, value ->
            addSection(if (entry.usernames.size == 1) "Username" else "Username ${index + 1}", value)
        }
        entry.password?.let { addSection("Password", it) }
        entry.totp.forEachIndexed { index, token ->
            val account = listOf(token.issuer, token.accountName)
                .filter(String::isNotBlank).joinToString(" · ")
            val label = if (account.isEmpty()) "TOTP ${index + 1}" else "TOTP ${index + 1} · $account"
            addSection(label, token.secret)
        }
        val urls = entry.actions.mapNotNull(EntryAction::url).filter(String::isNotBlank)
        urls.forEachIndexed { index, url ->
            addSection(if (urls.size == 1) "URL" else "URL ${index + 1}", url, browserUrl = url)
        }
        val hosts = entry.actions.mapNotNull(EntryAction::host).filter(String::isNotBlank)
        hosts.forEachIndexed { index, host ->
            addSection(if (hosts.size == 1) "Host" else "Host ${index + 1}", host)
        }
        if (entry.notes.isNotBlank()) addSection("Note", entry.notes)
        entry.customFields.forEach { (label, value) -> addSection(label, value) }
        if (entry.tags.isNotEmpty()) addSection("Tag", entry.tags.joinToString(", "), copyable = false)
    }

    private fun addSection(
        label: String,
        value: String,
        copyable: Boolean = true,
        browserUrl: String? = null,
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(12), 0, dp(4))
        }
        row.addView(TextView(this).apply {
            text = label
            textSize = 14f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val valueView = TextView(this).apply {
            text = value
            textSize = 16f
            setPadding(0, dp(6), dp(8), dp(6))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            if (browserUrl != null) {
                paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG
                setTextColor(0xFF1565C0.toInt())
                setOnClickListener { openUrl(browserUrl) }
            }
        }
        controls.addView(valueView)
        if (copyable) {
            controls.addView(Button(this).apply {
                text = "Copia"
                isAllCaps = false
                setOnClickListener { copyValue(label, value) }
            })
        }
        if (browserUrl != null) {
            controls.addView(Button(this).apply {
                text = "Apri"
                isAllCaps = false
                setOnClickListener { openUrl(browserUrl) }
            })
        }
        row.addView(controls)
        content.addView(row)
    }

    private fun openUrl(rawUrl: String) {
        val uri = Uri.parse(rawUrl)
        if (uri.scheme !in setOf("http", "https") || uri.host.isNullOrBlank()) {
            setStatus("URL non valido o non web; apertura nel browser non disponibile")
            return
        }
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: android.content.ActivityNotFoundException) {
            setStatus("Nessun browser disponibile per aprire questo URL")
        }
    }

    private fun copyValue(label: String, value: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Keys NG · $label", value))
        val generation = ++clipboardGeneration
        Toast.makeText(this, "$label copiato (si cancella tra 30 secondi)", Toast.LENGTH_SHORT).show()
        handler.postDelayed({
            if (generation == clipboardGeneration && clipboard.hasPrimaryClip()) {
                val clip = clipboard.primaryClip
                val copiedText = clip?.takeIf { it.itemCount > 0 }
                    ?.getItemAt(0)?.coerceToText(this)?.toString()
                if (copiedText == value) {
                    if (Build.VERSION.SDK_INT >= 28) clipboard.clearPrimaryClip()
                    else clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
                }
            }
        }, CLIPBOARD_CLEAR_DELAY_MS)
    }

    private fun decodeUtf8(bytes: ByteArray): String {
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return decoder.decode(ByteBuffer.wrap(bytes)).toString()
    }

    private fun setBusy(value: Boolean) {
        busy = value
        if (::chooseVaultButton.isInitialized) chooseVaultButton.isEnabled = providerReady && !busy
        if (::refreshButton.isInitialized) refreshButton.isEnabled = providerReady && !busy && selectedVaultUri != null
    }

    private fun setStatus(message: String) {
        if (::statusView.isInitialized) statusView.text = message
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val PREF_VAULT_URI = "selected_vault_tree_uri"
        private const val PROVIDER_INTERACTION_REQUEST = 8401
        private const val CLIPBOARD_CLEAR_DELAY_MS = 30_000L
    }
}
