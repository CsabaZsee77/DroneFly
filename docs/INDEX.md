# DroneFly — Dokumentáció Index

**Projekt:** DroneFly Android App
**Céleszköz:** DJI Crystal Sky (Android 5.1)
**Drón:** Phantom 4 Pro v1
**SDK:** DJI Mobile SDK v4.18
**Verzió:** v2.1.0
**Utolsó frissítés:** 2026-04-21

---

## Modulok

| Modul | Leírás | Státusz |
|-------|--------|---------|
| [M01 Misszió Tervező](M01_MISSZIO_TERVEZO/) | Térképes UI, polygon rajzolás, paraméter beállítás | ✅ Implementálva (v1.0.0) |
| [M02 Grid Engine](M02_GRID_ENGINE/) | GSD kalkulátor, rácsútvonal generátor (kígyózó minta), crosshatch kettős rács | ✅ Implementálva (v1.0.0) |
| [M03 Export / Import](M03_EXPORT_IMPORT/) | Litchi CSV export, KMZ export, CSV import | ✅ Implementálva (v1.0.0) |
| [M04 DJI Integráció](M04_DJI_INTEGRACIO/) | MSDK v4 feltöltés, misszió vezérlés, kamera feed PiP, tap-to-expose, kézi felszállás utáni indítás, folyamatos repülés (CURVED), gimbal nadir, SD kártya ellenőrzés | ✅ Implementálva — telemetria, kamera, misszió feltöltés/indítás, isFlying állapot, intervallum fotózás |
| [M05 Kamera Konfigurátor](M05_KAMERA_KONFIGURATOR/) | Manuális expozíció (ISO/rekesz/zár/WB/fókusz), élő hisztogram, EV csúszka smart prioritással, kamera profilok | 🔲 Tervezve |
| [M06 Dronterapia Szinkron](M06_DRONTERAPIA_SYNC/) | NetworkMonitor, AuthManager (username+jelszó), SyncManager (kétirányú .flightprogram.json szinkronizáció), Sync UI | ✅ Implementálva (v2.1.0) |
| [M07 Blokk-felosztás](M07_BLOKK_FELOSZTAS/) | Nagy területű AOI felbontása rácscellákra (W×H + dőlés + átfedés-puffer), blokk-tap kiválasztás, blokkonkénti misszió generálás, állapotkövetés | ✅ Kézi UI-verifikáció OK Crystal Sky-n (2026-07-02) |
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
    └── CameraConfigurator.java       ← M04 kamera beállítások

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
