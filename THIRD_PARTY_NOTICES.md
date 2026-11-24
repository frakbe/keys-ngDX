# Avvisi sulle dipendenze di terze parti

## OpenPGP API

Il prototipo usa `com.github.open-keychain.open-keychain:openpgp-api:v5.7.1` per collegarsi a un provider OpenPGP Android (per esempio PGPony) e richiedere la decifratura. La libreria è distribuita con licenza Apache-2.0. Sorgenti e testo della licenza: <https://github.com/open-keychain/openpgp-api>.

## Gradle Wrapper

Il wrapper Gradle è distribuito con licenza Apache-2.0. La licenza e i termini sono disponibili in <https://github.com/gradle/gradle/blob/master/LICENSE>.

## Provider OpenPGP

PGPony e OpenKeychain sono applicazioni separate, non incluse nell'APK. Ognuna mantiene il proprio codice, portachiavi e licenza. Il prototipo non incorpora codice di PGPony.
