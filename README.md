# Keys NG DX

Prototipo Android nativo in Kotlin per leggere una singola voce Keys NG cifrata in OpenPGP. La decifratura viene richiesta a un provider Android, con PGPony come provider predefinito; l'app interpreta il JSON schema 1 e mostra i campi copiabili.

Questa è una prova tecnica, non ancora una versione per l'uso quotidiano. Leggere [la guida del prototipo](docs/it/ANDROID_PROTOTYPE.md) e le sue limitazioni prima di provarlo. La guida contiene anche i passaggi per generare l'APK.

## Struttura

- `app/src/main/java/it/keysng/dx/crypto`: adapter Kotlin dell'API OpenPGP Android.
- `app/src/main/java/it/keysng/dx/model`: modello e parser riutilizzabile del record Keys NG schema 1.
- `app/src/main/java/it/keysng/dx/MainActivity.kt`: interfaccia Android temporanea; in una futura app Flutter può essere sostituita da una schermata Dart mantenendo il confine Kotlin per il provider.

## Licenza

Il progetto è rilasciato sotto GPL-3.0-or-later. Le dipendenze mantengono le rispettive licenze; vedi `THIRD_PARTY_NOTICES.md`.
