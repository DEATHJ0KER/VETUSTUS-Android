# Porting notes: VETUSTUS desktop → VETUSTUS Micro

Riferimento iniziale analizzato: VETUSTUS desktop `0.2.0-alpha.13`.

| Desktop | Android Beta | Stato |
| --- | --- | --- |
| `parseIrcLine` | `IrcProtocol.parseLine` | Portato |
| `parseDccArguments` | `IrcProtocol.parseDccArguments` | Portato |
| `decodeDccIp` | `IrcProtocol.decodeDccIp` | Portato |
| `XDCC SEND #pack` | `IrcSession.connectAndRequest` | Portato |
| `DCC SEND` | `IrcProtocol.parseDccOffer` | Portato + verifica nick |
| `DCC RESUME/ACCEPT` | `IrcSession.negotiateResume` | Portato |
| ACK DCC cumulativo UInt32 BE | `DccDownloader` | Portato |
| verifica bytes su disco | `.part` + controllo dimensione | Portato |
| `network-directory.json` | asset Android omonimo | Riutilizzato ed esteso |
| `servers.ini` completo | nessuna UI server nella Micro | Escluso intenzionalmente |
| multi-server/chat/query | nessuna UI IRC | Escluso intenzionalmente |
| media library desktop | SQLite + MediaStore | Ridisegnato nativo |
| gestione archivi | `MediaArchiveManager` | ZIP/RAR/TAR e compressioni TAR |

## Decisioni Android Beta

- **MediaStore**: i file finali sono accessibili all'utente senza permessi storage legacy.
- **Area privata per `.part`**: consente resume e impedisce ai player di vedere file incompleti.
- **Foreground Service `dataSync`**: il trasferimento resta esplicito e visibile al sistema.
- **Socket Java/Kotlin**: nessun WebView, Node, mIRC o motore IRC esterno.
- **Scouting IRC parallelo**: fino a 6 richieste possono attendere bot differenti; una coda lunga non blocca i bot già pronti.
- **DCC parallelo controllato**: fino a 3 payload contemporanei, assegnati in base all'ordine in cui i bot diventano pronti.
- **TLS rigoroso**: hostname e catena certificato vengono verificati normalmente.
- **Fallback compatibile dichiarato**: per le reti `auto`, se TLS fallisce viene provato soltanto l'endpoint plain già presente nella allow-list locale. Non esiste trust-all.
- **Archivi**: l'estrazione automatica seleziona audio/video e conserva l'originale per default nella Beta.
- **Localizzazione**: 14 lingue con locale Android automatico e override manuale.

## Cosa è dimostrato dal porting

- VETUSTUS Micro non dipende da mIRC per IRC/DCC.
- Il download XDCC è una connessione in uscita verso il bot: Android non deve aprire porte pubbliche.
- Resume e ACK classico sono compatibili con i bot già usati nei test reali.
- Download DCC, player video e estrazione archivio → MP2 → player sono stati verificati sul campo.

## Cosa resta da osservare durante la Beta

- reti effettivamente restituite da `xdcc.eu` che non siano ancora nella directory locale;
- server che richiedano SASL, NickServ o JOIN differiti;
- set RAR multi-volume completi/incompleti;
- comportamento dei produttori Android più aggressivi con i processi in background;
- matrice codec hardware/software sui dispositivi target;
- download reali molto grandi e ripresa dopo kill del processo;
- limiti temporali dei Foreground Service `dataSync` su Android recenti.
