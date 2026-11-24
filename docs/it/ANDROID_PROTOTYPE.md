# Prototipo Android Keys NG DX

Il prototipo apre un singolo file record `.gpg`, chiede la decifratura a un provider OpenPGP Android tramite l'API OpenPGP, interpreta il JSON Keys NG schema 1 e mostra i campi in sola lettura. Non contiene né importa chiavi private e non implementa la crittografia: il provider installato sul dispositivo gestisce la chiave e l'eventuale sblocco. Il pulsante **Licenza e informazioni** mostra una breve nota GPL; il testo GPL completo è nel repository.

## Avvertenza

È un prototipo tecnico. Non usarlo per consultare vault reali o conservare dati sensibili. Non implementa ancora la policy completa del vault: non apre `vault.json`, `catalog.gpg` o `folders.gpg`; non controlla `require_signature` e `trusted_signers`; non verifica che l'UUID del record corrisponda al file selezionato; non risolve i riferimenti `{REF:...}` tra voci. Mostra lo stato firma restituito dal provider solo come informazione diagnostica.

## Requisiti per provarlo

- Telefono o emulatore Android 8.0 (API 26) o successivo.
- PGPony installato e avviato almeno una volta. Nel prototipo il package provider predefinito è `com.pgpony.android`.
- Una chiave di prova già importata in PGPony e autorizzata a decifrare il record scelto.
- Una voce cifrata `.gpg` di un vault di prova, con dati fittizi.

Quando l'app richiede per la prima volta il collegamento, autorizza Keys NG DX in PGPony. PGPony può mostrare una propria schermata per scegliere la chiave o sbloccarla; completata l'interazione, il provider restituisce il risultato all'app.

## Uso

1. Genera e installa l'APK seguendo la sezione seguente.
2. In PGPony importa una chiave creata appositamente per la prova.
3. Copia sul telefono una singola voce `.gpg` cifrata a quella chiave.
4. Avvia **Keys NG DX**, tocca **Apri una voce cifrata (.gpg)** e seleziona il file tramite il selettore Android.
5. Autorizza l'app in PGPony e completa la richiesta di decifratura.
6. Controlla titolo, tipo, username, password, TOTP, note e campi personalizzati. Usa **Copia** per copiare un valore. La clipboard viene svuotata dopo 30 secondi, se nel frattempo non è stato copiato un altro valore.
7. Quando l'app passa in background, il contenuto mostrato viene rimosso dalla schermata.

Il prototipo non salva il JSON decifrato su disco e non scrive i segreti nei log. Usa `FLAG_SECURE` per impedire screenshot e anteprime dell'app switcher, e disabilita il backup Android.

## Generare l'APK con Android Studio

### Software necessario

- Android Studio stabile con Android SDK Platform 35 e Build Tools 35.
- JDK 17. Android Studio include un JDK compatibile selezionabile in **Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK**.
- Connessione internet al primo avvio per scaricare Gradle e le dipendenze da Google Maven, Maven Central e JitPack.

### Procedura

1. Clona il repository:

   ```bash
   git clone https://github.com/frakbe/keys-ngDX.git
   cd keys-ngDX
   ```

2. Apri la cartella del repository in Android Studio e attendi il completamento di **Gradle Sync**. Se richiesto, installa Android SDK Platform 35 e Build Tools 35.

3. Seleziona **Build → Build Bundle(s) / APK(s) → Build APK(s)**. Al termine usa **Locate** nella notifica di Android Studio.

   L'APK di debug è anche in:

   ```text
   app/build/outputs/apk/debug/app-debug.apk
   ```

4. Installa l'APK sul dispositivo. Con `adb`:

   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

### Procedura da terminale

Con JDK 17 e Android SDK installati e configurati in Android Studio:

```bash
./gradlew :app:assembleDebug
```

Su Windows usare:

```bat
gradlew.bat :app:assembleDebug
```

Il wrapper scarica Gradle 8.11.1 al primo avvio. Android Studio deve avere installato Android SDK Platform 35 e Build Tools 35.

## APK di release

L'APK `debug` viene firmato automaticamente con una chiave di debug e serve per prove locali. Per distribuire una build, usare **Build → Generate Signed Bundle / APK → APK** in Android Studio e creare o selezionare una chiave release. Conservare keystore e password fuori dal repository e farne un backup sicuro; non aggiungerli a Git.

## Compilazione automatica

Il workflow GitHub Actions `Android prototype` compila `:app:assembleDebug` quando viene aggiornato un branch e conserva l'APK come artifact della run. L'artifact è una build di debug per prova; non è una release firmata per distribuzione.

## Modello e campi

Il parser supporta lo schema 1 descritto in `docs/it/VAULT_FORMAT_1.0.md` del progetto Keys NG: `title`, `kind`, `usernames`, `password`, `totp`, `tags`, `notes`, `custom_fields` e `revision`. Campi con valore `{REF:U@I:...}` o `{REF:P@I:...}` sono mostrati letteralmente: la risoluzione richiede l'accesso agli altri record del vault.

Le azioni SSH/RDP/command non vengono eseguite né mostrate in questo client generico.

## Diagnostica

- **Provider non disponibile**: verifica che PGPony sia installato; chiudi e riapri Keys NG DX.
- **Richiesta di autorizzazione**: accetta Keys NG DX nell'elenco delle app collegate di PGPony.
- **“PGPony ha chiuso o rifiutato la richiesta”**: controlla in PGPony → Settings → Connected apps che Keys NG DX sia autorizzata. Se avevi appena autorizzato l'app, chiudi e riapri Keys NG DX e riprova.
- **Chiave mancante o passphrase errata**: controlla che la chiave privata corretta sia nel portachiavi PGPony e che possa decifrare il record.
- **JSON non valido**: assicurati di selezionare il file record di una voce, non `catalog.gpg` o `folders.gpg`, e verifica che il record sia schema 1.
- **Firma non valida o assente**: il prototipo mostra il risultato del provider, ma non applica la policy del vault; non usare il risultato come verifica di integrità per un vault reale.

## Estensioni previste

Per passare alla app definitiva, conservare `OpenPgpProviderClient` come adapter Android dietro un'interfaccia e spostare la vista in Flutter tramite un plugin o un platform channel. Il modello e il parser JSON sono indipendenti dalla UI. I prossimi componenti necessari sono un selettore del vault via Storage Access Framework, lettura e validazione del catalogo, verifica dei firmatari secondo `vault.json`, risoluzione dei riferimenti e una gestione della sessione con cancellazione esplicita dei dati in memoria.
