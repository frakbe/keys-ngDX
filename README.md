# Keys NG DX

Lettore Android nativo in Kotlin per vault Keys NG. Apre la directory del vault, decifra catalogo e cartelle con PGPony, mostra l'alberatura e permette di consultare e copiare i campi delle voci. Le password partono oscurate; i TOTP sono calcolati e aggiornati automaticamente. Sono inclusi ricerca delle voci e apertura degli URL web nel browser. L'app non modifica i file e non esegue azioni remote.

Questa prima build è destinata alla prova: la policy di fiducia `trusted_signers` non è ancora applicata. Prima di usarla con dati critici, leggi [la guida Android](docs/it/ANDROID_PROTOTYPE.md), inclusi limiti e procedura per generare l'APK.

Per preparare una release firmata, creare e custodire il keystore Linux e configurare i segreti GitHub Actions, segui la [guida alle release Android](docs/it/ANDROID_RELEASE.md).

## Struttura

- `app/src/main/java/it/keysng/dx/crypto`: adapter Kotlin dell'API OpenPGP Android.
- `app/src/main/java/it/keysng/dx/storage`: accesso in sola lettura al vault tramite Storage Access Framework.
- `app/src/main/java/it/keysng/dx/model`: modelli e parser riutilizzabili di catalogo, cartelle e record Keys NG schema 1.
- `app/src/main/java/it/keysng/dx/MainActivity.kt`: interfaccia Android iniziale; in una futura app Flutter può essere sostituita da una schermata Dart mantenendo il confine Kotlin per il provider.

## Licenza

Il progetto è rilasciato sotto GPL-3.0-or-later. Le dipendenze mantengono le rispettive licenze; vedi `THIRD_PARTY_NOTICES.md`.
