# Keys NG DX per Android

Prima build funzionale del lettore Android nativo in Kotlin. L'app apre una directory scelta tramite il selettore documenti Android, decifra `catalog.gpg` e `folders.gpg` tramite PGPony e mostra l'alberatura del vault. Le singole voci si aprono in sola lettura; i campi possono essere copiati e gli URL web (`http`/`https`) possono essere aperti nel browser.

## Funzioni incluse

- Accesso in sola lettura alla directory del vault tramite Storage Access Framework; la selezione viene ricordata sul dispositivo.
- Caricamento di `catalog.gpg`, `folders.gpg` (se presente) e dei file `records/<uuid>.gpg`.
- Alberatura di cartelle annidate, voci senza cartella e ordinamento alfabetico.
- Decifratura OpenPGP delegata a PGPony. Keys NG DX non importa né conserva chiavi private o passphrase.
- Verifica dell'hash ciphertext quando presente nel catalogo e confronto di UUID, titolo, revisione e cartella del record con il catalogo.
- Visualizzazione e copia di titolo, username, password, TOTP, URL, host, note, tag e campi personalizzati.
- Gli URL `http` e `https` sono cliccabili e si aprono con il browser installato.
- L'app non modifica il vault e non esegue azioni SSH, RDP o comandi.

## Limiti della prima build

Questa build è destinata alla verifica con vault di prova. Non legge ancora `vault.json`, non applica la policy `require_signature` e non confronta l'identità completa del firmatario con `trusted_signers`. Lo stato firma restituito da PGPony è mostrato a schermo come informazione, ma non è sufficiente a dimostrare che il firmatario sia autorizzato dal vault. I riferimenti `{REF:...}` fra record restano letterali. Non usare questa build come unico controllo di integrità o fiducia per dati critici.

Il JSON decifrato non viene scritto su disco e i segreti non vengono scritti nei log. L'app usa `FLAG_SECURE` per limitare screenshot e anteprime nello switcher e cancella i dettagli dalla schermata quando passa in background. I dati rimangono comunque in memoria mentre la voce è aperta.

## Requisiti

- Android 8.0 (API 26) o successivo.
- PGPony installato e inizializzato; il package provider predefinito è `com.pgpony.android`.
- Una chiave già disponibile in PGPony, autorizzata a decifrare catalogo, cartelle e record.
- Un vault Keys NG di prova accessibile dal selettore Android.

Alla prima richiesta PGPony mostra una schermata di consenso per collegare Keys NG DX. Conferma con **Allow**; dopo l'approvazione l'app compare in **Settings → Keys & Servers → Connected apps**. L'elenco mostra solo le app autorizzate. PGPony può inoltre richiedere lo sblocco della chiave.

## Uso

1. Installa la build APK.
2. Avvia Keys NG DX e tocca **Apri vault**.
3. Nel selettore Android scegli la directory principale del vault (quella che contiene `catalog.gpg` e `records/`).
4. Completa in PGPony il consenso iniziale e gli eventuali passaggi di sblocco per decifrare catalogo e cartelle.
5. Espandi le cartelle e seleziona una voce. PGPony può richiedere di nuovo lo sblocco per il record.
6. Tocca **Copia** accanto al campo desiderato. La clipboard viene svuotata dopo 30 secondi se il contenuto non è stato sostituito.
7. Tocca il valore URL o **Apri** per aprire un indirizzo web nel browser. Solo schemi `http` e `https` vengono aperti.
8. Usa **Aggiorna** per rileggere i file dal vault. Se Android revoca l'accesso alla directory, selezionala nuovamente.

## Generare l'APK

### Android Studio

1. Installa Android Studio stabile, Android SDK Platform 35, Build Tools 35 e JDK 17.
2. Clona il repository e aprilo in Android Studio:

   ```bash
   git clone https://github.com/frakbe/keys-ngDX.git
   cd keys-ngDX
   ```

3. Attendi il completamento di **Gradle Sync** e installa gli SDK richiesti se Android Studio li propone.
4. Seleziona **Build → Build Bundle(s) / APK(s) → Build APK(s)**, quindi **Locate** nella notifica.

L'APK di debug si trova in `app/build/outputs/apk/debug/app-debug.apk`.

### Terminale

Con JDK 17 e Android SDK configurati:

```bash
./gradlew :app:assembleDebug
```

Su Windows:

```bat
gradlew.bat :app:assembleDebug
```

Il wrapper scarica Gradle 8.11.1 al primo avvio; serve una connessione internet. Per installare con ADB:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Le build CI sono firmate con una chiave debug temporanea. Per installare una build CI sopra una build precedente potrebbe essere necessario disinstallare prima Keys NG DX. Una distribuzione stabile richiede un APK firmato con un keystore release conservato fuori dal repository.

Il workflow GitHub Actions **Android app** compila l'APK a ogni aggiornamento del branch e pubblica l'artifact `keys-ngdx-android-debug-apk` per 14 giorni. L'artifact è una build di prova, non una release firmata per la distribuzione.

## Diagnostica

- **Provider OpenPGP non disponibile**: verifica che PGPony sia installato e poi riapri Keys NG DX.
- **PGPony non mostra il consenso**: alla prima apertura dovrebbe comparire il dialogo di autorizzazione. Dopo aver consentito, controlla **Settings → Keys & Servers → Connected apps**.
- **Catalogo o cartelle non decifrati**: verifica che la chiave in PGPony possa leggere entrambi i file, oltre ai record.
- **File record mancante**: assicurati che `records/` si trovi direttamente nella directory scelta e contenga `<uuid>.gpg`.
- **Hash o metadati non corrispondenti**: aggiorna il vault e riprova; il record cifrato deve corrispondere alla voce del catalogo.
- **URL non si apre**: sono ammessi solo indirizzi web completi con schema `http` o `https` e host valido.

## Struttura del codice

- `crypto/OpenPgpProviderClient.kt`: adapter Kotlin dell'API OpenPGP del provider.
- `storage/VaultDocumentTree.kt`: accesso SAF in sola lettura.
- `model/EntryJsonParser.kt` e `model/VaultIndexParser.kt`: parser indipendenti dalla UI per i payload schema 1.
- `MainActivity.kt`: interfaccia Android iniziale. Il livello provider e i parser possono essere riutilizzati se in seguito si porta la UI in Flutter tramite platform channel.
