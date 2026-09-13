# VETUSTUS Micro for Android

[![Android CI](https://github.com/DEATHJ0KER/VETUSTUS-Android/actions/workflows/android-ci.yml/badge.svg)](https://github.com/DEATHJ0KER/VETUSTUS-Android/actions/workflows/android-ci.yml)

**VETUSTUS Micro 0.2.0-beta.2** è la versione Android nativa e minimale di **VETUSTUS Script by VxD aka DEATHJ0KER**. IRC resta dietro le quinte: l'utente cerca, scarica, apre la Libreria e riproduce i contenuti senza usare un client IRC visibile.

## Stato Beta

Funzioni operative e verificate sul campo:

- ricerca XDCC tramite `xdcc.eu`, senza il vecchio limite applicativo di 200 risultati;
- download IRC/DCC nativo con code bot, resume e ACK;
- richieste IRC parallele e priorità ai bot che diventano disponibili prima;
- fino a 3 trasferimenti DCC contemporanei;
- MP1/MP2/MP3/MPA, FLAC, AAC, M4A, OGG/OPUS, WAV, AC3/EAC3/AC4, AMR e MKA;
- MP4/M4V, MKV, WebM, AVI, FLV, MPEG/PS e MPEG-TS/M2TS/MTS;
- ZIP, RAR, TAR, TGZ/TAR.GZ, TBZ/TAR.BZ2 e TXZ/TAR.XZ;
- estrazione automatica selettiva di audio e video dagli archivi;
- Libreria locale con apertura, condivisione, eliminazione e player Media3/ExoPlayer;
- fallback a player Android esterno se il dispositivo non dispone del codec necessario;
- Foreground Service per i trasferimenti in background;
- 14 lingue: **EN, IT, DE, ES, FR, PT, NL, PL, RU, UK, TR, JA, KO, ZH**;
- rilevamento automatico della lingua Android e selezione manuale nelle Impostazioni.

La Beta 2 corregge inoltre il crash all'avvio introdotto dal passaggio ad `AppCompatActivity` per la gestione della lingua, usando ora un tema AppCompat compatibile.

## Priorità e code XDCC

VETUSTUS Micro non lascia che un bot con una coda lunga blocchi gli altri download. Può mantenere fino a **6 richieste IRC** in attesa contemporaneamente e assegna priorità DCC ai bot che diventano pronti per primi, con fino a **3 trasferimenti DCC** attivi in parallelo.

In pratica, un file fermo in `Coda 9/19 · Wait 16min` può restare in attesa mentre un altro bot già libero comincia subito a trasferire.

## TLS e server IRC con certificati problematici

La verifica TLS resta rigorosa: **non viene usato nessun trust-all e non vengono accettati certificati non validi o con hostname errato**.

Per le reti inserite nella allow-list locale con modalità `auto`, VETUSTUS prova prima l'endpoint TLS su 6697. Se il certificato è incompatibile o l'endpoint TLS non è utilizzabile, prova esclusivamente il fallback IRC plain su 6667 già dichiarato nel catalogo locale. Non viene mai trasformato un nome rete proveniente dalla pagina HTML in un hostname arbitrario.

Questo permette di gestire reti che espongono certificati TLS non coerenti senza disattivare globalmente la sicurezza TLS.

## Archivi

Per default la Beta:

1. scarica il pacchetto XDCC in area privata;
2. verifica il file ricevuto;
3. se è un archivio supportato, estrae soltanto i media riconosciuti;
4. pubblica i media estratti in `Download/VETUSTUS Micro` e nella Libreria;
5. conserva l'archivio originale per default.

Nelle Impostazioni è possibile attivare **Elimina l'archivio dopo l'estrazione**. L'originale viene eliminato soltanto se almeno un file audio/video è stato estratto correttamente. In caso di errore o archivio senza media riconosciuti l'originale viene conservato.

## Requisiti

- Android 10 / API 29 o successivo;
- JDK 17 per la build;
- Android SDK 36;
- Android Studio compatibile con AGP 8.13.2.

Dipendenze principali: Kotlin 2.3.21, Compose BOM 2026.06.01, Media3 1.11.0, Apache Commons Compress 1.28.0 e junrar 8.1.1.

## Build

```bash
./gradlew testDebugUnitTest assembleDebug
```

APK debug:

```text
app/build/outputs/apk/debug/app-debug.apk
```

La GitHub Action esegue unit test, build e pubblica l'artifact:

```text
VETUSTUS-Micro-Android-0.2.0-beta.2-debug
```

## Flusso reale

1. La ricerca restituisce rete, canale, bot, pacchetto, dimensione e nome file.
2. La rete viene risolta esclusivamente dal catalogo locale autorizzato.
3. VETUSTUS prova TLS rigoroso e, solo dove configurato, il fallback IRC compatibile.
4. Invia `PRIVMSG <bot> :XDCC SEND #<pack>`.
5. Interpreta messaggi di coda e attende l'offerta DCC del bot corretto.
6. Se esiste un `.part`, negozia `DCC RESUME/ACCEPT`.
7. Scarica in area privata, invia ACK DCC e verifica la dimensione finale.
8. Pubblica il file tramite MediaStore soltanto dopo il completamento.
9. Per gli archivi supportati estrae i media in modo selettivo e applica difese contro path traversal, duplicati e decompression bomb.

## Sicurezza

- verifica TLS con hostname;
- fallback plain solo per endpoint presenti nella allow-list locale;
- nessun bypass globale dei certificati;
- nessuna password o sessione IRC salvata;
- correlazione tra bot richiesto e offerta DCC;
- loopback, link-local, multicast e host LAN bloccati per default;
- nomi file bonificati e righe IRC limitate;
- file incompleti mai pubblicati come download completati;
- limiti di estrazione per numero file, dimensione, spazio disponibile e rapporto di espansione.

## Limiti Beta dichiarati

- `xdcc.eu` è una sorgente HTML esterna e può cambiare struttura o imporre propri limiti ai risultati;
- reverse/passive DCC non è ancora incluso;
- set RAR multi-volume incompleti vengono conservati, non distrutti; l'estrazione richiede che il set necessario sia disponibile;
- archivi protetti da password possono richiedere un'app esterna;
- la riproduzione dipende anche dai codec presenti sul dispositivo: il contenitore può essere supportato mentre il codec interno no;
- Android 15+ applica limiti temporali ai Foreground Service `dataSync`; VETUSTUS conserva il `.part` e permette la ripresa.

Usare XDCC esclusivamente per contenuti che si ha il diritto di scaricare e distribuire.

## Provenienza tecnica

Il protocollo è stato portato dal core autonomo di VETUSTUS desktop: la Micro non usa mIRC, Electron o codice Windows. Le note di porting sono in [docs/PORTING-NOTES.md](docs/PORTING-NOTES.md), mentre i controlli manuali sono in [docs/TEST-PLAN.md](docs/TEST-PLAN.md).
