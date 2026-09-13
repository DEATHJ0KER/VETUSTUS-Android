# Changelog

## 0.2.0-beta.2

- Corretto il crash all'avvio introdotto dal passaggio ad `AppCompatActivity` per il cambio lingua.
- Tema applicazione aggiornato a `Theme.AppCompat.DayNight.NoActionBar`.
- 14 lingue disponibili con rilevamento automatico della lingua Android e selezione manuale.
- Ricerca XDCC senza il precedente limite applicativo di 200 risultati.
- Richieste IRC parallele, priorità ai bot pronti prima e fino a 3 trasferimenti DCC simultanei.
- Fallback IRC plain controllato per reti allow-list con certificati TLS non compatibili, senza disabilitare la verifica TLS globale.
- Supporto esteso a ZIP, RAR, TAR, TGZ/TAR.GZ, TBZ/TAR.BZ2 e TXZ/TAR.XZ.
- Estrazione selettiva di audio/video dagli archivi con conservazione sicura dell'originale in caso di errore.
- Supporto audio esteso a MP1/MP2/MP3/MPA e altri formati già gestiti dal player.
- Beta verificata sul campo con download XDCC, estrazione archivio, Libreria e riproduzione audio/video.
- Pipeline GitHub Actions verde con unit test, build APK debug e artifact `VETUSTUS-Micro-Android-0.2.0-beta.2-debug`.

## 0.2.0-beta.1

- Prima promozione del progetto da Alpha a Beta.
- Download IRC/DCC nativo con code bot, resume e ACK.
- Player Media3/ExoPlayer con fallback a player esterno.
- Gestione automatica degli archivi e Libreria locale.

## 0.1.0-alpha.1

- Nuovo progetto Android Kotlin nativo.
- UI Compose con Cerca, Download, Libreria e Impostazioni.
- Porting del protocollo IRC/XDCC/DCC di VETUSTUS desktop.
- Download seriali in Foreground Service con pausa, annullamento e ripresa.
- File parziali privati e pubblicazione MediaStore verificata.
- Player Media3/ExoPlayer.
- Estrazione ZIP protetta e opzionale eliminazione archivio.
- Test parser IRC/XDCC e prova socket DCC con resume/ACK.
- Pipeline GitHub Actions per test e generazione dell'APK debug.
- Compose bloccato alla serie compatibile con SDK 36 e AGP 8.13.
