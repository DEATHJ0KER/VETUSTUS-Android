# Piano test Alpha 1

## Stato della consegna (13 settembre 2026)

- superati: integrità del wrapper Gradle 8.13, struttura ZIP/JAR, XML Android, JSON reti, riferimenti a classi del manifest, link locali e scansione delimitatori Kotlin;
- verificato sul provider corrente: la tabella di ricerca xdcc.eu mantiene le sette colonne attese dal parser;
- non eseguiti in questo ambiente: compilazione Android, test JUnit e smoke test su dispositivo, perché non sono disponibili Android SDK e accesso ai repository Maven/Gradle;
- il comando da eseguire in Android Studio o in CI resta `./gradlew testDebugUnitTest assembleDebug`.

## Test automatici inclusi

- parsing tabella HTML compatibile con il provider desktop;
- decoding entità HTML e conteggi;
- parsing riga IRC e case-fold RFC1459 essenziale;
- `DCC SEND` con filename quotato e IPv4 numerico;
- rifiuto di offerte provenienti da un nick diverso;
- `DCC ACCEPT` e messaggi di coda;
- bonifica filename;
- trasferimento DCC su socket reale locale con `.part`, resume e ACK cumulativi.

## Smoke test su dispositivo

1. Installare la build debug su Android 10+.
2. Concedere le notifiche.
3. Cercare un pacchetto di test legalmente distribuibile con estensione supportata.
4. Avviare il download e portare l'app in background.
5. Verificare notifica, stato coda, progresso, velocità e completamento.
6. Controllare il file in `Download/VETUSTUS Micro`.
7. Riprodurre MP4/MKV/MP3/FLAC dalla Libreria.
8. Mettere in pausa a metà, chiudere l'app, riaprire e premere Riprendi.
9. Scaricare uno ZIP di prova e verificare la sottocartella estratta.
10. Verificare eliminazione ZIP solo dopo estrazione riuscita.

## Casi di errore obbligatori prima della Beta

- bot offline / numeric 401;
- server TLS con certificato non valido;
- rete persa durante DCC e durante la coda;
- offerta da nick diverso;
- IP DCC loopback, link-local e LAN;
- dimensione finale diversa da quella dichiarata;
- spazio insufficiente durante pubblicazione ed estrazione;
- ZIP con `../`, percorso assoluto, duplicati e rapporto di espansione eccessivo;
- timeout Android del Foreground Service;
- negazione del permesso notifiche;
- codec MKV assente.
