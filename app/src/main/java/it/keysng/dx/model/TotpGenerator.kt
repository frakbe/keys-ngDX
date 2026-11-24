package it.keysng.dx.model

import java.nio.ByteBuffer
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** RFC 6238 TOTP generation for the Base32 secrets stored in Keys NG records. */
object TotpGenerator {
    fun code(token: TotpField, epochSeconds: Long = System.currentTimeMillis() / 1_000L): String {
        require(token.period in 1..3_600) { "Intervallo TOTP non valido" }
        require(token.digits in 6..8) { "Numero di cifre TOTP non supportato" }
        val counter = epochSeconds / token.period
        val secret = decodeBase32(token.secret)
        val macName = when (token.algorithm.uppercase(Locale.ROOT)) {
            "SHA1" -> "HmacSHA1"
            "SHA256" -> "HmacSHA256"
            "SHA512" -> "HmacSHA512"
            else -> throw IllegalArgumentException("Algoritmo TOTP non supportato")
        }
        val mac = Mac.getInstance(macName)
        val digest = try {
            mac.init(SecretKeySpec(secret, macName))
            mac.doFinal(ByteBuffer.allocate(8).putLong(counter).array())
        } finally {
            secret.fill(0)
        }
        val offset = digest.last().toInt() and 0x0f
        val binary = ((digest[offset].toInt() and 0x7f) shl 24) or
            ((digest[offset + 1].toInt() and 0xff) shl 16) or
            ((digest[offset + 2].toInt() and 0xff) shl 8) or
            (digest[offset + 3].toInt() and 0xff)
        var modulus = 1L
        repeat(token.digits) { modulus *= 10L }
        return (binary.toLong() % modulus).toString().padStart(token.digits, '0')
    }

    fun counter(epochSeconds: Long, period: Int): Long {
        require(period in 1..3_600) { "Intervallo TOTP non valido" }
        return epochSeconds / period
    }

    fun secondsRemaining(epochSeconds: Long, period: Int): Int {
        require(period in 1..3_600) { "Intervallo TOTP non valido" }
        return period - (epochSeconds % period).toInt()
    }

    private fun decodeBase32(value: String): ByteArray {
        val normalized = value.trim().replace(" ", "").replace("-", "")
            .uppercase(Locale.ROOT).trimEnd('=')
        require(normalized.isNotEmpty()) { "Secret TOTP vuota" }

        val output = ArrayList<Byte>(normalized.length * 5 / 8)
        var buffer = 0
        var bits = 0
        normalized.forEach { character ->
            val symbol = when (character) {
                in 'A'..'Z' -> character.code - 'A'.code
                in '2'..'7' -> character.code - '2'.code + 26
                else -> throw IllegalArgumentException("Secret TOTP Base32 non valida")
            }
            buffer = (buffer shl 5) or symbol
            bits += 5
            if (bits >= 8) {
                bits -= 8
                output += ((buffer shr bits) and 0xff).toByte()
                buffer = if (bits == 0) 0 else buffer and ((1 shl bits) - 1)
            }
        }
        require(bits < 5 && (bits == 0 || buffer == 0)) { "Secret TOTP Base32 non valida" }
        require(output.isNotEmpty()) { "Secret TOTP vuota" }
        return output.toByteArray()
    }
}
