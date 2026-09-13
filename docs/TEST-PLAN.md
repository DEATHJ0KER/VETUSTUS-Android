# Piano test Beta 1

## Stato della consegna (13 settembre 2026)

- GitHub Actions esegue `testDebugUnitTest assembleDebug` su ogni aggiornamento del PR;
- verificato sul provider corrente: la tabella di ricerca `xdcc.eu` mantiene le sette colonne attese dal parser;
- verificato sul campo: download DCC diretto, pubblicazione in Libreria e riproduzione video;
- verificato sul campo: download di un archivio, estrazione selettiva di un file audio MP2 e riproduzione nel player interno;
- verificati errori reali: bot offline (`401`) e mismatch del certificato TLS su una rete IRC;
- la Beta introduce fallback IRC plain solo per endpoint dichiarati nella allow-list locale, mantenendo la verifica TLS rigorosa sul tentativo primario;
- la Beta permette più richieste IRC contemporanee affinché una coda lenta non blocchi i bot già pronti.

## Test automatici inclusi

- parsing tabella HTML XDCC;
- decoding entità HTML e conteggi;
- assenza del vecchio taglio applicativo a 200 risultati;
- parsing riga IRC e case-fold RFC1459 essenziale;
- `DCC SEND` con filename quotato e IPv4 numerico;
- rifiuto di offerte provenienti da un nick diverso;
- `DCC ACCEPT` e messaggi di coda;
- bonifica filename;
- trasferimento DCC su socket reale locale con `.part`, resume e ACK cumulativi;
- riconoscimento ZIP/RAR/TAR/TGZ/TBZ/TXZ;
- riconoscimento payload audio/video, incluso MP1/MP2/MP3/MPA.

## Smoke test Beta su dispositivo

1. Installare `0.2.0-beta.1` su Android 10+.
2. Concedere le notifiche.
3. Verificare lingua automatica Android e cambio manuale da Impostazioni.
4. Cercare un termine con risultati su più reti.
5. Accodare almeno due file, uno su bot congestionato e uno disponibile subito.
6. Verificare che il file disponibile inizi senza attendere la fine della coda dell'altro bot.
7. Controllare notifica, stato coda, progresso, velocità e completamento.
8. Mettere in pausa a metà, chiudere l'app, riaprire e premere Riprendi.
9. Scaricare audio e video diretti e riprodurli dalla Libreria.
10. Scaricare ZIP/RAR/TAR con media e verificare estrazione e riproduzione.
11. Verificare che l'archivio originale sia conservato per default.
12. Attivare `Elimina l'archivio dopo l'estrazione` e verificare che la cancellazione avvenga solo dopo estrazione riuscita.
13. Testare una rete con TLS valido e verificare che resti in TLS.
14. Testare una rete configurata `auto` con certificato incompatibile e verificare il fallback dichiarato su 6667.

## Casi di errore Beta

- bot offline / numeric 401;
- server TLS con certificato non valido o hostname errato;
- fallback plain non raggiungibile;
- rete persa durante DCC e durante la coda;
- offerta da nick diverso;
- IP DCC loopback, link-local e LAN;
- dimensione finale diversa da quella dichiarata;
- spazio insufficiente durante pubblicazione ed estrazione;
- ZIP/TAR/RAR con percorso interno non sicuro o duplicato;
- rapporto di espansione eccessivo / decompression bomb;
- archivio protetto da password;
- set RAR multi-volume incompleto;
- timeout Android del Foreground Service;
- negazione del permesso notifiche;
- contenitore supportato ma codec video/audio assente sul dispositivo.

## Criterio di promozione

La Beta 1 è pronta per distribuzione di test quando l'ultimo commit del branch ha CI verde e produce l'artifact `VETUSTUS-Micro-Android-0.2.0-beta.1-debug`.
