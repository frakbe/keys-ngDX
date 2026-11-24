package it.keysng.dx.crypto

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Parcelable
import android.os.Build
import android.app.PendingIntent
import org.openintents.openpgp.IOpenPgpService2
import org.openintents.openpgp.OpenPgpError
import org.openintents.openpgp.OpenPgpSignatureResult
import org.openintents.openpgp.util.OpenPgpApi
import org.openintents.openpgp.util.OpenPgpServiceConnection
import java.io.ByteArrayOutputStream

private class WipingByteArrayOutputStream : ByteArrayOutputStream() {
    fun wipe() {
        buf.fill(0)
        reset()
    }
}

/**
 * Small Android boundary around a separate OpenPGP provider (PGPony by default).
 * No key material or passphrase is read or stored by this app.
 */
class OpenPgpProviderClient(
    context: Context,
    private val providerPackage: String = PGPONY_PACKAGE,
    private val listener: Listener,
) {
    interface Listener {
        fun onProviderReady()
        fun onProviderError(message: String)
    }

    sealed interface Outcome {
        data class Success(val plaintext: ByteArray, val signatureStatus: String) : Outcome
        data class InteractionRequired(
            val pendingIntent: PendingIntent,
            val requestData: Intent,
        ) : Outcome
        data class Failure(val message: String) : Outcome
    }

    private val applicationContext = context.applicationContext
    private var service: IOpenPgpService2? = null
    private val connection = OpenPgpServiceConnection(
        applicationContext,
        providerPackage,
        object : OpenPgpServiceConnection.OnBound {
            override fun onBound(boundService: IOpenPgpService2) {
                service = boundService
                listener.onProviderReady()
            }

            override fun onError(error: Exception) {
                listener.onProviderError(
                    "Provider OpenPGP non disponibile. Verifica che PGPony sia installato e abilitato."
                )
            }
        },
    )

    fun bind() = connection.bindToService()

    fun unbind() {
        try {
            connection.unbindFromService()
        } catch (_: IllegalArgumentException) {
            // A failed bind has no active service connection to unbind.
        }
        service = null
    }

    fun decrypt(
        source: Uri,
        requestData: Intent = Intent(),
        callback: (Outcome) -> Unit,
    ) {
        val boundService = service
        if (boundService == null) {
            callback(Outcome.Failure("Provider OpenPGP non ancora connesso"))
            return
        }

        val input = try {
            applicationContext.contentResolver.openInputStream(source)
        } catch (error: Exception) {
            null
        }
        if (input == null) {
            callback(Outcome.Failure("Impossibile leggere il file selezionato"))
            return
        }

        requestData.action = OpenPgpApi.ACTION_DECRYPT_VERIFY
        val output = WipingByteArrayOutputStream()
        val api = OpenPgpApi(applicationContext, boundService)
        api.executeApiAsync(requestData, input, output) { result ->
            when (result.getIntExtra(OpenPgpApi.RESULT_CODE, OpenPgpApi.RESULT_CODE_ERROR)) {
                OpenPgpApi.RESULT_CODE_SUCCESS -> {
                    val signature = result.parcelable<OpenPgpSignatureResult>(OpenPgpApi.RESULT_SIGNATURE)
                    try {
                        callback(
                            Outcome.Success(
                                plaintext = output.toByteArray(),
                                signatureStatus = signature?.let(::describeSignature) ?: "Stato firma non disponibile",
                            ),
                        )
                    } finally {
                        output.wipe()
                    }
                }

                OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED -> {
                    output.wipe()
                    val pendingIntent = result.parcelable<PendingIntent>(OpenPgpApi.RESULT_INTENT)
                    if (pendingIntent == null) {
                        callback(Outcome.Failure("Il provider ha richiesto un'interazione ma non ha fornito la schermata"))
                    } else {
                        callback(Outcome.InteractionRequired(pendingIntent, requestData))
                    }
                }

                else -> {
                    output.wipe()
                    val error = result.parcelable<OpenPgpError>(OpenPgpApi.RESULT_ERROR)
                    // Provider messages may include sensitive context. Do not log them.
                    callback(Outcome.Failure(error?.message ?: "Decifratura non riuscita"))
                }
            }
        }
    }

    private fun describeSignature(result: OpenPgpSignatureResult): String = when (result.result) {
        OpenPgpSignatureResult.RESULT_NO_SIGNATURE -> "Voce non firmata"
        OpenPgpSignatureResult.RESULT_VALID_KEY_CONFIRMED -> "Firma valida; chiave confermata dal provider"
        OpenPgpSignatureResult.RESULT_VALID_KEY_UNCONFIRMED -> "Firma valida; chiave non confermata dal provider"
        OpenPgpSignatureResult.RESULT_KEY_MISSING -> "Chiave del firmatario non disponibile"
        OpenPgpSignatureResult.RESULT_INVALID_SIGNATURE -> "Firma non valida"
        OpenPgpSignatureResult.RESULT_INVALID_KEY_REVOKED -> "Firma non valida: chiave revocata"
        OpenPgpSignatureResult.RESULT_INVALID_KEY_EXPIRED -> "Firma non valida: chiave scaduta"
        OpenPgpSignatureResult.RESULT_INVALID_KEY_INSECURE -> "Firma non valida: algoritmo non sicuro"
        OpenPgpSignatureResult.RESULT_INVALID_NOT_INTENDED_RECIPIENT -> "Firma non valida: destinatario non corrispondente"
        else -> "Stato firma sconosciuto"
    }

    @Suppress("DEPRECATION")
    private inline fun <reified T : Parcelable> Intent.parcelable(key: String): T? =
        if (Build.VERSION.SDK_INT >= 33) getParcelableExtra(key, T::class.java) else getParcelableExtra(key)

    companion object {
        const val PGPONY_PACKAGE = "com.pgpony.android"
    }
}
