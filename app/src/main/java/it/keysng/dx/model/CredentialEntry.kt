package it.keysng.dx.model

data class CredentialEntry(
    val id: String,
    val title: String,
    val kind: String,
    val usernames: List<String>,
    val password: String?,
    val notes: String,
    val customFields: Map<String, String>,
    val totp: List<TotpField>,
    val tags: List<String>,
    val revision: Int,
)

data class TotpField(
    val secret: String,
    val issuer: String,
    val accountName: String,
    val algorithm: String,
    val digits: Int,
    val period: Int,
)
