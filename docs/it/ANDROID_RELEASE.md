# Firma e release Android di Keys NG DX

Questa guida crea una chiave di firma permanente su Linux, la salva nei segreti del repository GitHub e avvia la workflow che genera l'APK firmato e una prerelease GitHub. La stessa chiave deve essere riutilizzata per ogni aggiornamento: se viene persa o sostituita, Android non consente l'aggiornamento diretto dell'app già installata.

La workflow crea la prerelease quando viene avviata manualmente. Il tag suggerito per la versione corrente è `v0.2.0-beta.3`.

## 1. Crea il keystore sul PC Linux

Installa un JDK 17, che include `keytool`. Su Debian o Ubuntu:

```bash
sudo apt update
sudo apt install openjdk-17-jdk
```

Controlla che `keytool` sia disponibile:

```bash
keytool -help
```

Crea una directory privata e il keystore PKCS#12:

```bash
install -d -m 700 "$HOME/.local/share/keys-ngdx-signing"
umask 077
keytool -genkeypair -v \
  -keystore "$HOME/.local/share/keys-ngdx-signing/keys-ngdx-release.p12" \
  -storetype PKCS12 \
  -alias keys-ngdx \
  -keyalg RSA \
  -keysize 3072 \
  -validity 10000
```

`keytool` chiederà una password: scegline una lunga e unica, conservala in un password manager e non inviarla in chat. Puoi inserire informazioni descrittive generiche (ad esempio `Keys NG DX`) quando chiede nome e organizzazione. Quando chiede la password della chiave, premi Invio per riutilizzare quella del keystore.

Verifica il keystore:

```bash
keytool -list -v \
  -keystore "$HOME/.local/share/keys-ngdx-signing/keys-ngdx-release.p12" \
  -alias keys-ngdx
```

Conserva una copia di backup del file `.p12` e della password in posti sicuri e separati. La directory è esclusa da Git; non caricare il keystore nel repository, in issue o in chat.

## 2. Aggiungi i segreti al repository

La workflow usa questi due segreti di repository:

| Nome | Contenuto |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Keystore codificato in Base64 |
| `ANDROID_KEYSTORE_PASSWORD` | Password inserita quando hai creato il keystore |

### Metodo consigliato: GitHub CLI

Installa GitHub CLI (`gh`) se non è già presente, poi accedi:

```bash
gh auth login
```

Imposta i segreti senza stamparne i valori nel terminale:

```bash
base64 -w0 "$HOME/.local/share/keys-ngdx-signing/keys-ngdx-release.p12" \
  | gh secret set ANDROID_KEYSTORE_BASE64 --repo frakbe/keys-ngDX

read -rsp 'Password del keystore: ' ANDROID_KEYSTORE_PASSWORD
printf '\n'
printf '%s' "$ANDROID_KEYSTORE_PASSWORD" \
  | gh secret set ANDROID_KEYSTORE_PASSWORD --repo frakbe/keys-ngDX
unset ANDROID_KEYSTORE_PASSWORD
```

Se `gh auth login` non ha già accesso al repository, completa l'autenticazione con l'account che ne è proprietario o collaboratore.

### Metodo tramite sito GitHub

Apri il repository `frakbe/keys-ngDX`, poi vai in **Settings → Secrets and variables → Actions → New repository secret**. Crea i due segreti sopra. Per il primo, codifica il keystore nel terminale con:

```bash
base64 -w0 "$HOME/.local/share/keys-ngdx-signing/keys-ngdx-release.p12"
```

Incolla l'output come valore di `ANDROID_KEYSTORE_BASE64`; per `ANDROID_KEYSTORE_PASSWORD` inserisci la password scelta in `keytool`. Non inviare questi valori a nessuno.

## 3. Avvia la prima release

1. Apri **Actions → Android release** nel repository.
2. Premi **Run workflow**.
3. Lascia selezionato il branch predefinito e inserisci `v0.2.0-beta.3` in **Tag della release**.
4. Avvia la workflow e attendi che tutti i passaggi risultino verdi.
5. Apri **Releases** e scarica `app-release.apk` dalla prerelease appena creata.

Il repository è attualmente privato: mentre resta privato, la release e il suo APK sono accessibili solo a chi ha accesso al repository. Quando renderai pubblico il repository, la prerelease diventerà pubblicamente accessibile.

## Versioni future

Riutilizza sempre lo stesso file `.p12`, password e alias `keys-ngdx`. Prima di ogni nuova release, incrementa `versionCode` e aggiorna `versionName` in `app/build.gradle.kts`; scegli un nuovo tag Git, ad esempio `v0.2.0-beta.4`. Non rigenerare il keystore per gli aggiornamenti.

## Risoluzione problemi

- **`Missing repository secret ANDROID_KEYSTORE_BASE64`**: crea o correggi il segreto con quel nome esatto.
- **Errore di password o firma**: controlla che `ANDROID_KEYSTORE_PASSWORD` sia la password del keystore e che il file Base64 corrisponda al keystore creato con alias `keys-ngdx`.
- **La nuova APK non aggiorna quella installata**: il keystore usato per firmare deve essere identico alla firma della versione installata. Una vecchia build debug firmata con chiave temporanea potrebbe richiedere la disinstallazione prima della prima installazione release.
- **Keystore perso**: non sarà possibile firmare aggiornamenti compatibili con la stessa app. Ripristina la copia di backup.
