# VETUSTUS Micro for Android

[![Android CI](https://github.com/DEATHJ0KER/VETUSTUS-Android/actions/workflows/android-ci.yml/badge.svg)](https://github.com/DEATHJ0KER/VETUSTUS-Android/actions/workflows/android-ci.yml)

Versione minimale di **VETUSTUS Script by VxD aka DEATHJ0KER**, dedicata a ricerca XDCC, download DCC, riproduzione locale e gestione automatica degli archivi.

Questa repository contiene la prima Alpha nativa Android:

- quattro aree visibili: **Cerca**, **Download**, **Libreria**, **Impostazioni**;
- IRC TCP/TLS interamente dietro le quinte;
- ricerca federata iniziale tramite `xdcc.eu`;
- richiesta `XDCC SEND`, coda bot, `DCC SEND`, ACK cumulativo e resume `DCC RESUME/ACCEPT`;
- Foreground Service `dataSync` per trasferimenti in background;
- pubblicazione finale in `Download/VETUSTUS Micro` tramite MediaStore;
- player interno Media3/ExoPlayer per MP4, MKV, MP3 e FLAC;
- estrazione ZIP automatica con difese contro path traversal, duplicati e decompression bomb.

## Requisiti

- Android Studio compatibile con AGP 8.13.2;
- JDK 17;
- Android SDK 36;
- dispositivo o emulatore Android 10 (API 29) o successivo.

Dipendenze principali: Kotlin 2.3.21, Compose BOM 2026.08.00, Media3 1.11.0.

## Apertura e build

1. Apri questa cartella in Android Studio.
2. Consenti il download delle dipendenze Gradle richieste.
3. Esegui **File > Sync Project with Gradle Files**.
4. Avvia la variante `debug` su un dispositivo API 29+.

Comando equivalente da terminale:

```bash
./gradlew testDebugUnitTest assembleDebug
```

L'APK debug sarà in `app/build/outputs/apk/debug/app-debug.apk`.

## Flusso reale

1. La ricerca restituisce rete, canale, bot e numero pacchetto.
2. Il servizio risolve la rete dal catalogo derivato da VETUSTUS desktop.
3. Apre IRC in TLS quando disponibile, registra un nickname tecnico e fa JOIN al canale richiesto.
4. Invia `PRIVMSG <bot> :XDCC SEND #<pack>`.
5. Accetta soltanto un'offerta CTCP proveniente dal bot atteso.
6. Se esiste un `.part`, negozia il resume; altrimenti inizia da zero.
7. Scrive in area privata, invia ACK DCC a 32 bit e verifica la dimensione finale.
8. Pubblica il file in MediaStore. Solo a quel punto il file diventa visibile in Download e Libreria.
9. Se è ZIP e l'opzione è attiva, estrae in una sottocartella omonima.

## Limiti dichiarati della prima Alpha

- il provider di ricerca iniziale è HTML e può richiedere un aggiornamento del parser se il sito cambia struttura;
- sono gestite offerte DCC SEND classiche in uscita dal telefono verso il bot; reverse/passive DCC non è ancora incluso;
- ZIP cifrati, RAR e 7z non sono inclusi;
- Media3 riconosce Matroska/MKV, ma la decodifica dei codec interni dipende anche dall'hardware e dai codec Android del dispositivo;
- Android 15+ limita i Foreground Service `dataSync` in background a 6 ore complessive ogni 24 ore. Alla scadenza VETUSTUS conserva il `.part` e mostra **Riprendi**;
- la coda locale è volutamente seriale nella prima Alpha: un download DCC attivo alla volta.

## Sicurezza

- certificati TLS verificati con hostname;
- nessuna password o sessione IRC salvata;
- bot e offerta DCC correlati prima della connessione dati;
- reti IRC limitate alla allow-list locale derivata dal catalogo VETUSTUS;
- loopback, link-local, multicast e host LAN bloccati per default;
- nomi file bonificati e righe IRC limitate;
- file incompleti mai pubblicati nella cartella Download;
- estrazione ZIP limitata per numero file, dimensione singola, spazio disponibile e rapporto di espansione.

Usare XDCC esclusivamente per contenuti che si ha il diritto di scaricare e distribuire.

## Provenienza tecnica

Il protocollo è stato portato dal core autonomo presente in VETUSTUS desktop `0.2.0-alpha.13`: non usa mIRC, Electron o codice Windows. La mappatura è descritta in [docs/PORTING-NOTES.md](docs/PORTING-NOTES.md).

Lo stato dei controlli e i casi manuali sono in [docs/TEST-PLAN.md](docs/TEST-PLAN.md).
