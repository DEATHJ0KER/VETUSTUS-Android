# Porting notes: VETUSTUS desktop → VETUSTUS Micro

Riferimento analizzato: VETUSTUS desktop `0.2.0-alpha.13`.

| Desktop | Android Alpha | Stato |
| --- | --- | --- |
| `parseIrcLine` | `IrcProtocol.parseLine` | Portato |
| `parseDccArguments` | `IrcProtocol.parseDccArguments` | Portato |
| `decodeDccIp` | `IrcProtocol.decodeDccIp` | Portato |
| `XDCC SEND #pack` | `IrcSession.connectAndRequest` | Portato |
| `DCC SEND` | `IrcProtocol.parseDccOffer` | Portato + verifica nick |
| `DCC RESUME/ACCEPT` | `IrcSession.negotiateResume` | Portato |
| ACK DCC cumulativo UInt32 BE | `DccDownloader` | Portato |
| verifica bytes su disco | `.part` + controllo `RandomAccessFile.length()` | Portato |
| `network-directory.json` | asset Android omonimo | Riutilizzato |
| `servers.ini` completo | non mostrato nella UI Micro | Escluso intenzionalmente |
| multi-server/chat/query | nessuna UI IRC | Escluso intenzionalmente |
| media library desktop | SQLite + MediaStore | Ridisegnato nativo |

## Decisioni Android

- **MediaStore**: il file finale è accessibile all'utente senza permessi storage legacy.
- **Area privata dell'app per `.part`**: privilegia lo spazio esterno app-specifico, consente resume e impedisce ad altri player di vedere file corrotti.
- **Foreground Service `dataSync`**: il trasferimento resta esplicito e visibile al sistema.
- **Socket Java/Kotlin**: nessun WebView, processo Node o motore IRC esterno.
- **Coda seriale**: riduce consumo radio, nickname simultanei e conflitti nella prima Alpha.
- **TLS rigoroso**: nessun fallback automatico a certificati non validi.

## Cosa è dimostrato dal codice desktop

- VETUSTUS non dipende più da mIRC per IRC/DCC.
- Il download XDCC è un socket in uscita verso il bot: Android non deve aprire porte pubbliche.
- Resume e ACK classico sono già compatibili con i bot usati dal progetto desktop.

## Cosa resta da verificare su dispositivo

- comportamento con i bot e le reti effettivamente restituiti da `xdcc.eu`;
- tolleranza dei produttori Android più aggressivi nella gestione batteria;
- matrice codec MKV sui dispositivi target;
- download reali oltre 4 GiB e ripresa dopo kill del processo;
- eventuali reti che richiedono SASL, NickServ o JOIN differiti.
