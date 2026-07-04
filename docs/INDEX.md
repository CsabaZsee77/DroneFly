# DroneFly — Dokumentáció Index

**Projekt:** DroneFly Android App
**Céleszköz:** DJI Crystal Sky (Android 5.1)
**Drón:** Phantom 4 Pro v1
**SDK:** DJI Mobile SDK v4.18
**Verzió:** v2.1.0
**Utolsó frissítés:** 2026-07-03

---

## Modulok

| Modul | Leírás | Státusz |
|-------|--------|---------|
| [M01 Misszió Tervező](M01_MISSZIO_TERVEZO/) | Térképes UI, polygon rajzolás, paraméter beállítás | ✅ Implementálva (v1.0.0) — + ✅ Mintavételi misszió mód (§10). Terepi teszt 2026-07-03: 3 hiba azonosítva (ld. M02/M03/M04) — javítások implementálva, eszközön még nem tesztelve |
| [M02 Grid Engine](M02_GRID_ENGINE/) | GSD kalkulátor, rácsútvonal generátor (kígyózó minta), crosshatch kettős rács | ✅ Implementálva (v1.0.0) — + ✅ SamplingMissionGenerator (§7). Terepi teszt 2026-07-03: fotószám-becslés akár 4×-esen túlbecsült (kamera 2 mp-es intervallum-korlát figyelmen kívül hagyása miatt) — ✅ javítva (§8, effektív fotótávolság + UI figyelmeztetés), eszközön még nem tesztelve |
| [M03 Export / Import](M03_EXPORT_IMPORT/) | Litchi CSV export, KMZ export, CSV import | ✅ Implementálva (v1.0.0). Terepi teszt 2026-07-03: mintavételi beállítások + kamera-formátum (JPEG/RAW) nem mentődött projekt-mentéskor — ✅ javítva (§7, additív séma, migráció nélkül), eszközön még nem tesztelve |
| [M04 DJI Integráció](M04_DJI_INTEGRACIO/) | MSDK v4 feltöltés, misszió vezérlés, kamera feed PiP, tap-to-expose, kézi felszállás utáni indítás, folyamatos repülés (CURVED), gimbal nadir, SD kártya ellenőrzés | ✅ Implementálva — telemetria, kamera, misszió feltöltés/indítás, isFlying állapot, intervallum fotózás — + ✅ NORMAL-módú mintavételi végrehajtás és session-alapú médialetöltés (§15–16). Terepi teszt 2026-07-03: fotó-trigger megbízhatósági hiba (10 mintapontból 6 fotó) — ✅ javítva (§18, app-vezérelt trigger + retry `CameraConfigurator`-ban), eszközön még nem tesztelve |
| [M05 Kamera Konfigurátor](M05_KAMERA_KONFIGURATOR/) | Manuális expozíció (ISO/rekesz/zár/WB/fókusz), élő hisztogram, EV csúszka smart prioritással, kamera profilok | 🔲 Tervezve |
| [M06 Dronterapia Szinkron](M06_DRONTERAPIA_SYNC/) | NetworkMonitor, AuthManager (username+jelszó), SyncManager (kétirányú .flightprogram.json szinkronizáció), Sync UI | ✅ Implementálva (v2.1.0) |
| [M07 Blokk-felosztás](M07_BLOKK_FELOSZTAS/) | Nagy területű AOI felbontása rácscellákra (W×H + dőlés + átfedés-puffer), blokk-tap kiválasztás, blokkonkénti misszió generálás, állapotkövetés | ✅ Kézi UI-verifikáció OK Crystal Sky-n (2026-07-02) |
| [M09 Edge AI Tőszámlálás](M09_EDGE_AI_TOSZAMLALAS/) | Mintavételi session fotóin tabletes YOLO (**ONNX Runtime Mobile** — a Dronterápia natív ONNX modelljei konverzió nélkül) inferencia, azonnali db/ha sűrűség + FPC/Student-t alapú konfidenciaintervallummal extrapolált összlétszám, offline; önálló `SamplingResultsActivity` + `PhotoImportActivity` (fotóimport drón SD-ről / tablet tárolóból, repülési terv kontextussal, repüléstől függetlenül); állítható konf/IoU küszöb; fotó-előnézetek | ✅ **Eszközön verifikálva Crystal Sky-n (2026-07-03)** — importált kukoricatábla-fotón 178 detektálás, EXIF GPS, extrapolált eredmény + CSV export OK; API 22 Optional-crash javítva (desugaring). ✅ **GSD vonalzós kalibráció** (§10) — mért footprint a bizonytalan barometrikus magasság helyett; eszközön verifikálva (footprint 41,6 m², db/ha inferencia nélkül újraszámol, EXIF-kereszt-ellenőrzés) |
| [M10 Sűrű Rács (alacsony repülés)](M10_SURU_ALACSONY_RACS/) | NORMAL-módú, sűrű waypoint-rács + app-vezérelt trigger minden fotópozíción — a CURVED+intervallum mód 20 m alatti strukturális korlátjára (M02 §8.3) ad megoldást teljes ortomozaikhoz | ✅ Implementálva (2026-07-03), eszközön még nem tesztelve |
| Kp-index (státuszsáv) | NOAA geomágneses aktivitás lekérő, 10 percenként frissül, MAG: 0–9 színkódolva | ✅ Implementálva (v1.9.3) |
| Offline térkép | OSMDroid cache, automatikus offline mód WiFi nélkül, letöltés gomb (hosszú nyomás SAT/MAP gombon), réteg guard offline módban | ✅ Implementálva (v1.9.5) |
| GPS gomb | Drón GPS (DJI telemetria) / tablet GPS prioritás, zoom 15 (~1 km), forrás toast | ✅ Implementálva (v1.9.6) |
| REC gomb | Képernyőkép (rövid nyomás, zöld villanás visszajelzés) + képernyővideó 720p (hosszú nyomás, MediaProjection), /sdcard/Pictures/DroneFly/ mentés; stopRecording háttérszálon (UI nem fagy) | ✅ Implementálva (v1.9.9) |
| Térképi pozíció jelzők | Tablet helyzete: kék emberke ikon (MyLocationNewOverlay, egyéni ic_person_marker); drón helyzete: sárga drón ikon (ic_drone_marker), mindig látható ha csatlakoztatva van (nem csak misszió alatt) | ✅ Implementálva (v2.0.0) |
| Domborzatkövetés — referenciapont | Terep-korrekcióhoz a drón aktuális GPS-pozíciója az elsődleges referencia; ha nincs csatlakoztatva: startPoint → első waypoint fallback | ✅ Implementálva (v2.0.0) |

---

## Fájlstruktúra

```
app/src/main/java/com/dronefly/app/
├── App.java                          ← MultiDex Application
├── MainActivity.java                 ← Főképernyő, DJI státusz
├── MissionPlannerActivity.java       ← M01 fő activity
├── model/
│   ├── MissionConfig.java            ← M01/M02 közös adatmodell
│   └── WaypointData.java             ← M01/M02/M03 közös
├── mission/
│   ├── GsdCalculator.java            ← M02 engine
│   ├── GridMissionGenerator.java     ← M02 engine
│   ├── BlockGridGenerator.java       ← M07 engine
│   ├── PolygonClipper.java           ← M07 Sutherland-Hodgman
│   ├── CsvMissionParser.java         ← M03 import
│   └── MissionExporter.java          ← M03 export
├── layers/
│   ├── OverpassClient.java           ← HTTP POST kliens az OSM Overpass API-hoz
│   ├── ProtectedAreasLayer.java      ← N2K réteg (Natura 2000, nemzeti parkok)
│   ├── AirspaceLayer.java            ← LGT réteg (OpenAIP Core API, magassági szűrővel)
│   └── LandUseLayer.java             ← ZÓN réteg (OSM landuse: lakó, ipari, katonai, repülőtér)
├── KpIndexProvider.java              ← NOAA Kp-index lekérő (geomágneses aktivitás, státuszsáv)
├── sync/
│   ├── NetworkMonitor.java           ← hálózati állapot figyelő (OFFLINE/ONLINE)
│   ├── AuthManager.java              ← Dronterapia auth (username+jelszó → Bearer token)
│   └── SyncManager.java              ← kétirányú .flightprogram.json szinkronizáció
└── dji/
    ├── DJIHelper.java                ← M04 SDK init, telemetria (reflection)
    │   takeScreenshot()              ← (MissionPlannerActivity) PNG → /sdcard/DroneFly/
    │   startScreenRecording()        ← (MissionPlannerActivity) MediaProjection MP4 rögzítés
    │   jumpToCurrentPosition()       ← (MissionPlannerActivity) drón/tablet GPS, zoom 15
    ├── DroneVideoWidget.java         ← M04 kamera feed PiP + tap-to-expose
    ├── MissionUploader.java          ← M04 feltöltés + vezérlés (stub)
    ├── CameraConfigurator.java       ← M04 kamera beállítások
    └── MediaSessionDownloader.java   ← M04 §16 session-alapú médialetöltés

ai/                                    ← M09, implementálva (2026-07-03)
├── YoloModelManager.java             ← .onnx + sidecar .json betöltés/validáció
├── ModelMetadata.java                ← modell-metaadat POJO
├── YoloInferenceEngine.java          ← ONNX Runtime (OrtSession) wrapper, NCHW preprocess, NMS
├── Detection.java                    ← detekció POJO
├── PointResult.java                  ← mintaponti eredmény POJO
├── SamplingCountResult.java          ← összesített eredmény POJO + results.json (de)szerializáció
└── SamplingResultCalculator.java     ← db/ha sűrűség + FPC/Student-t extrapoláció

SamplingResultsActivity.java          ← M09 eredmény-képernyő (önálló, nem a MissionPlannerActivity panelje)
PhotoImportActivity.java              ← M09 fotóimport (drón SD / tablet, repülési terv kontextus, EXIF GPS)

app/src/main/res/
├── layout/
│   ├── activity_main.xml
│   └── activity_mission_planner.xml  ← M01 UI layout
├── drawable/
│   ├── focus_ring.xml                ← Tap-to-expose fókuszgyűrű
│   ├── ic_launcher.xml
│   ├── ic_drone_marker.xml           ← Drón pozíció marker (sárga/narancs/fehér quadcopter)
│   ├── ic_person_marker.xml          ← Tablet/kezelő pozíció marker (kék emberke)
│   └── status_bar_bg.xml
├── xml/
│   ├── device_filter.xml             ← DJI USB filter
│   └── file_paths.xml                ← FileProvider export útvonal
└── values/
    └── styles.xml
```

---

## Architektúra összefoglaló

```
┌─────────────────────────────────────────────────────────────┐
│                    MissionPlannerActivity                    │
│  (M01 — térkép, polygon, seekbar-ok, gombok, státusz)       │
│                                                             │
│   OSMDroid MapView    │    ScrollView panel (35%)           │
│   (65% szélesség)     │    GSD / Sidelap / Frontlap         │
│                       │    Speed / Angle sliders            │
│   Polygon overlay     │    Draw / Clear / Generate          │
│   Mission path (orange│    SetStart / Import / Export       │
│   polyline)           │    Upload+Start / Pause / Stop      │
│                       │                                     │
│   Bal sarok gombok:   │                                     │
│   GPS / SAT / CAM     │                                     │
│   N2K / LGT / ZÓN     │                                     │
│   [▼ Xm ▲] (LGT alt.) │                                     │
└──┬──────────┬──────────────────────────┬────────────────────┘
   │          │                          │
   ▼          ▼                          ▼
┌──────┐  ┌─────────────────┐  ┌──────────────────────┐
│ M02  │  │  Térkép rétegek │  │  M04 DJI Integration │
│ Grid │  │  (layers/)      │  │  MissionUploader     │
│Engine│  │  N2K → Overpass │  │  (stub / MSDK v4)    │
└──┬───┘  │  LGT → OpenAIP  │  └──────────────────────┘
   │      │  ZÓN → Overpass  │
   ▼      └─────────────────┘
┌──────────────────┐
│  M03 Export      │
│  MissionExporter │  → Litchi CSV (48 col)
│  CsvMission-     │  → KMZ (KML+ZIP)
│  Parser          │  ← CSV import
└──────────────────┘
```

### Térkép rétegek – technikai összefoglaló

| Réteg | Forrás | Protokoll | Teljesítmény megoldás |
|-------|--------|-----------|-----------------------|
| N2K (védett területek) | OSM Overpass API | HTTP POST (HTTPS SSL hiba Android 5.1-en) | Polygon + Polyline, zárt/nyílt detektálás |
| LGT (légterek) | OpenAIP Core API v1 | HTTPS GET + `x-openaip-api-key` header | Egyetlen `AirspaceDrawOverlay extends Overlay` (1 draw() hívás) |
| ZÓN (területhasználat) | OSM Overpass API | HTTP POST | MAX_OVERLAYS=300, MAX_POINTS=150 subsample |

**LGT magassági szűrő:** `lowerLimit.value/unit/referenceDatum` parse az API-válaszból → méter konverzió (láb/FL/méter) → GND-referenciájú légterek szűrhetők altitudeFilter alapján → az overlay `draw()` metódusában real-time kizárás, újabb API hívás nélkül.

---

## Fontos konstansok — Phantom 4 Pro v1

| Paraméter | Érték |
|-----------|-------|
| Szenzor szélesség | 13.2 mm |
| Szenzor magasság | 8.8 mm |
| Fókusztávolság | 8.8 mm |
| Kép szélesség | 5472 px |
| Kép magasság | 3648 px |
| Mechanikus záridő | 1/800 s (alapértelmezett) |
| Min. repülési sebesség | 3 m/s |
| Max. repülési sebesség | 12 m/s |
| Max. waypoint / misszió (MSDK v4) | 99 |

---

## Build konfiguráció

| Paraméter | Érték |
|-----------|-------|
| minSdk | 22 (Android 5.1) |
| compileSdk | 34 |
| AGP | 8.2.2 |
| Gradle | 8.6 |
| DJI SDK | v4.18 (emulátor: stub) |
| OSMDroid | 6.1.17 |
| MultiDex | Engedélyezve |

---

## Kapcsolódó dokumentumok

- [CLAUDE.md](CLAUDE.md) — munkamódszer szabályok
- [FEJLESZTESI_OTLETEK.md](FEJLESZTESI_OTLETEK.md) — felmerült fejlesztési ötletek, nyitott/elvetett javaslatok
- [PLATFORM_INTEGRACIO.md](PLATFORM_INTEGRACIO.md) — DroneFly ↔ Dronterapia szinkronizáció, `.flightprogram.json` séma, Mission Hub terv
- [M01 L1 Üzleti folyamat](M01_MISSZIO_TERVEZO/M01_L1_UZLETI_FOLYAMAT.md)
- [M02 L1 Grid Engine folyamat](M02_GRID_ENGINE/M02_L1_UZLETI_FOLYAMAT.md)
- [M03 L1 Export/Import folyamat](M03_EXPORT_IMPORT/M03_L1_UZLETI_FOLYAMAT.md)
- [M04 L1 DJI Integráció folyamat](M04_DJI_INTEGRACIO/M04_L1_UZLETI_FOLYAMAT.md)
- [M05 L1 Kamera Konfigurátor folyamat](M05_KAMERA_KONFIGURATOR/M05_L1_UZLETI_FOLYAMAT.md)
- [M06 L1 Dronterapia Szinkron folyamat](M06_DRONTERAPIA_SYNC/M06_L1_UZLETI_FOLYAMAT.md)
- [M06 L2 Dronterapia Szinkron döntési logika](M06_DRONTERAPIA_SYNC/M06_L2_DONTESI_LOGIKA.md)
- [M06 L3 Dronterapia Szinkron állapotgép](M06_DRONTERAPIA_SYNC/M06_L3_ALLAPOTGEP_ES_ENGINE.md)
- [M06 L4 Dronterapia Szinkron tranzakciók](M06_DRONTERAPIA_SYNC/M06_L4_TRANZAKCIOS_ES_PARHUZAMOS.md)
- [M07 L1 Blokk-felosztás üzleti folyamat](M07_BLOKK_FELOSZTAS/M07_L1_UZLETI_FOLYAMAT.md)
- [M07 L2 Blokk-felosztás döntési logika](M07_BLOKK_FELOSZTAS/M07_L2_DONTESI_LOGIKA.md)
- [M07 L3 Blokk-felosztás állapotgép és engine](M07_BLOKK_FELOSZTAS/M07_L3_ALLAPOTGEP_ES_ENGINE.md)
- [M07 L4 Blokk-felosztás tranzakciók](M07_BLOKK_FELOSZTAS/M07_L4_TRANZAKCIOS_ES_PARHUZAMOS.md)
- [M09 L1 Edge AI Tőszámlálás üzleti folyamat](M09_EDGE_AI_TOSZAMLALAS/M09_L1_UZLETI_FOLYAMAT.md)
- [M09 L2 Edge AI Tőszámlálás döntési logika](M09_EDGE_AI_TOSZAMLALAS/M09_L2_DONTESI_LOGIKA.md)
- [M09 L3 Edge AI Tőszámlálás állapotgép és engine](M09_EDGE_AI_TOSZAMLALAS/M09_L3_ALLAPOTGEP_ES_ENGINE.md)
- [M09 L4 Edge AI Tőszámlálás tranzakciók](M09_EDGE_AI_TOSZAMLALAS/M09_L4_TRANZAKCIOS_ES_PARHUZAMOS.md)
- [M10 L1 Sűrű Rács üzleti folyamat](M10_SURU_ALACSONY_RACS/M10_L1_UZLETI_FOLYAMAT.md)
- [M10 L2 Sűrű Rács döntési logika](M10_SURU_ALACSONY_RACS/M10_L2_DONTESI_LOGIKA.md)
- [M10 L3 Sűrű Rács állapotgép és engine (vázlat)](M10_SURU_ALACSONY_RACS/M10_L3_ALLAPOTGEP_ES_ENGINE.md)
- [M10 L4 Sűrű Rács tranzakciók (vázlat)](M10_SURU_ALACSONY_RACS/M10_L4_TRANZAKCIOS_ES_PARHUZAMOS.md)
