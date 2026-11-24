package it.keysng.dx

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import it.keysng.dx.crypto.OpenPgpProviderClient
import it.keysng.dx.model.CredentialEntry
import it.keysng.dx.model.EntryJsonParser
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

class MainActivity : ComponentActivity() {
    private lateinit var container: LinearLayout
    private lateinit var statusView: TextView
    private lateinit var openButton: Button
    private lateinit var providerClient: OpenPgpProviderClient
    private var providerReady = false
    private var pendingSource: Uri? = null
    private var pendingRequestData: Intent? = null
    private var currentEntry: CredentialEntry? = null
    private var clipboardGeneration = 0
    private val handler = Handler(Looper.getMainLooper())

    private val openDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            pendingSource = uri
            currentEntry = null
            clearEntryViews()
            setStatus("Richiesta di decifratura a PGPony…")
            decrypt(uri)
        }
    }

    private val providerInteraction = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val source = pendingSource
        if (result.resultCode == Activity.RESULT_OK && source != null) {
            setStatus("Completamento della decifratura…")
            val retryData = result.data ?: pendingRequestData ?: Intent()
            pendingRequestData = null
            decrypt(source, retryData)
        } else {
            pendingRequestData = null
            setStatus(
                "PGPony ha chiuso o rifiutato la richiesta (codice ${result.resultCode}). " +
                    "Se non intendevi annullarla, apri PGPony → Settings → Connected apps, " +
                    "autorizza Keys NG DX e riprova."
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE,
        )
        buildScreen()
        providerClient = OpenPgpProviderClient(
            context = applicationContext,
            listener = object : OpenPgpProviderClient.Listener {
                override fun onProviderReady() {
                    providerReady = true
                    openButton.isEnabled = true
                    setStatus("PGPony connesso. Apri una singola voce .gpg di prova.")
                }

                override fun onProviderError(message: String) {
                    providerReady = false
                    openButton.isEnabled = false
                    setStatus(message)
                }
            },
        )
        openButton.isEnabled = false
        setStatus("Connessione al provider OpenPGP…")
        providerClient.bind()
    }

    override fun onStop() {
        super.onStop()
        if (currentEntry != null) {
            currentEntry = null
            clearEntryViews()
            setStatus("Voce nascosta quando l’app è in background. Riaprila per consultarla di nuovo.")
        }
    }

    override fun onDestroy() {
        if (::providerClient.isInitialized) providerClient.unbind()
        super.onDestroy()
    }

    private fun buildScreen() {
        val scroll = ScrollView(this)
        container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(24))
        }
        scroll.addView(container)

        val heading = TextView(this).apply {
            text = "Keys NG DX"
            textSize = 26f
            setTypeface(typeface, Typeface.BOLD)
        }
        container.addView(heading)

        val subtitle = TextView(this).apply {
            text = "Prototipo tecnico · sola lettura · una voce per volta"
            textSize = 14f
            setPadding(0, dp(4), 0, dp(16))
        }
        container.addView(subtitle)

        openButton = Button(this).apply {
            text = "Apri una voce cifrata (.gpg)"
            setOnClickListener {
                if (providerReady) openDocument.launch(arrayOf("*/*"))
            }
        }
        container.addView(openButton)

        statusView = TextView(this).apply {
            textSize = 14f
            setPadding(0, dp(12), 0, dp(12))
        }
        container.addView(statusView)

        val warning = TextView(this).apply {
            text = "Usa soltanto vault e chiavi di prova. Il prototipo non applica ancora la policy completa del vault (firmatari fidati e riferimenti tra voci)."
            textSize = 13f
            setPadding(dp(10), dp(10), dp(10), dp(10))
            setBackgroundColor(0xFFFFF1CC.toInt())
        }
        container.addView(warning)

        container.addView(Button(this).apply {
            text = "Licenza e informazioni"
            setOnClickListener {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Licenza")
                    .setMessage(
                        "Keys NG DX · prototipo tecnico\n" +
                            "Copyright © 2026 Franco ‘frakbe’ Bersani\n\n" +
                            "Software libero distribuito secondo la GNU General Public License, versione 3 o successiva. " +
                            "È fornito senza garanzia. Puoi ridistribuirlo e modificarlo secondo i termini della licenza.\n\n" +
                            "Testo completo: https://www.gnu.org/licenses/gpl-3.0.html\n" +
                            "Nel repository: LICENSE",
                    )
                    .setPositiveButton("Chiudi", null)
                    .show()
            }
        })
        setContentView(scroll)
    }

    private fun decrypt(source: Uri, requestData: Intent = Intent()) {
        if (!providerReady) {
            setStatus("Provider OpenPGP non connesso")
            return
        }
        providerClient.decrypt(source, requestData) { outcome ->
            when (outcome) {
                is OpenPgpProviderClient.Outcome.InteractionRequired -> {
                    pendingSource = source
                    pendingRequestData = outcome.requestData
                    try {
                        providerInteraction.launch(
                            IntentSenderRequest.Builder(outcome.pendingIntent.intentSender).build(),
                        )
                    } catch (_: Exception) {
                        setStatus("Impossibile aprire la richiesta di autorizzazione del provider")
                    }
                }

                is OpenPgpProviderClient.Outcome.Failure -> setStatus(outcome.message)

                is OpenPgpProviderClient.Outcome.Success -> {
                    val entry = try {
                        val decoder = Charsets.UTF_8.newDecoder()
                            .onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT)
                        val json = decoder.decode(ByteBuffer.wrap(outcome.plaintext)).toString()
                        EntryJsonParser.parse(json)
                    } catch (_: Exception) {
                        setStatus("Decifratura riuscita, ma il contenuto non è un record JSON Keys NG schema 1 valido")
                        return@decrypt
                    } finally {
                        outcome.plaintext.fill(0)
                    }
                    renderEntry(entry, outcome.signatureStatus)
                }
            }
        }
    }

    private fun renderEntry(entry: CredentialEntry, signatureStatus: String) {
        currentEntry = entry
        clearEntryViews()
        setStatus("Voce decifrata · $signatureStatus")
        addSection("Titolo", entry.title, copyable = false)
        addSection("Tipo", entry.kind)
        entry.usernames.forEachIndexed { index, value ->
            addSection(if (entry.usernames.size == 1) "Username" else "Username ${index + 1}", value)
        }
        entry.password?.let { addSection("Password", it) }
        entry.totp.forEachIndexed { index, token ->
            val label = buildString {
                append("TOTP ${index + 1}")
                val account = listOf(token.issuer, token.accountName).filter(String::isNotBlank).joinToString(" · ")
                if (account.isNotEmpty()) append(" · ").append(account)
            }
            addSection(label, token.secret)
        }
        if (entry.notes.isNotBlank()) addSection("Note", entry.notes)
        entry.customFields.forEach { (label, value) -> addSection(label, value) }
        if (entry.tags.isNotEmpty()) addSection("Tag", entry.tags.joinToString(", "), copyable = false)
        addSection("Revisione", entry.revision.toString(), copyable = false)
    }

    private fun addSection(label: String, value: String, copyable: Boolean = true) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(14), 0, dp(6))
        }
        row.addView(TextView(this).apply {
            text = label
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
        })
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        controls.addView(TextView(this).apply {
            text = value
            textSize = 16f
            setPadding(0, dp(6), dp(8), dp(6))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        if (copyable) {
            controls.addView(Button(this).apply {
                text = "Copia"
                setOnClickListener { copyValue(label, value) }
            })
        }
        row.addView(controls)
        container.addView(row)
    }

    private fun copyValue(label: String, value: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Keys NG · $label", value))
        val generation = ++clipboardGeneration
        Toast.makeText(this, "$label copiato (si cancella tra 30 secondi)", Toast.LENGTH_SHORT).show()
        handler.postDelayed({
            if (generation == clipboardGeneration && clipboard.hasPrimaryClip()) {
                val clip = clipboard.primaryClip
                val copiedText = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()
                if (copiedText == value) {
                    if (Build.VERSION.SDK_INT >= 28) {
                        clipboard.clearPrimaryClip()
                    } else {
                        clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
                    }
                }
            }
        }, CLIPBOARD_CLEAR_DELAY_MS)
    }

    private fun setStatus(message: String) {
        if (::statusView.isInitialized) statusView.text = message
    }

    private fun clearEntryViews() {
        if (container.childCount > FIXED_CHILD_COUNT) {
            container.removeViews(FIXED_CHILD_COUNT, container.childCount - FIXED_CHILD_COUNT)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val FIXED_CHILD_COUNT = 6
        private const val CLIPBOARD_CLEAR_DELAY_MS = 30_000L
    }
}
