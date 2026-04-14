# DroneFly — Dokumentáció Index

**Projekt:** DroneFly Android App
**Céleszköz:** DJI Crystal Sky (Android 5.1)
**Drón:** Phantom 4 Pro v1
**SDK:** DJI Mobile SDK v4.18
**Verzió:** v1.9.1
**Utolsó frissítés:** 2026-04-13

---

## Modulok

| Modul | Leírás | Státusz |
|-------|--------|---------|
| [M01 Misszió Tervező](M01_MISSZIO_TERVEZO/) | Térképes UI, polygon rajzolás, paraméter beállítás | ✅ Implementálva (v1.0.0) |
| [M02 Grid Engine](M02_GRID_ENGINE/) | GSD kalkulátor, rácsútvonal generátor (kígyózó minta) | ✅ Implementálva (v1.0.0) |
| [M03 Export / Import](M03_EXPORT_IMPORT/) | Litchi CSV export, KMZ export, CSV import | ✅ Implementálva (v1.0.0) |
| [M04 DJI Integráció](M04_DJI_INTEGRACIO/) | MSDK v4 feltöltés, misszió vezérlés, kamera feed PiP, tap-to-expose, kézi felszállás utáni indítás | ✅ Implementálva — telemetria, kamera, misszió feltöltés/indítás, isFlying állapot |

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
│   ├── CsvMissionParser.java         ← M03 import
│   └── MissionExporter.java          ← M03 export
├── layers/
│   ├── OverpassClient.java           ← HTTP POST kliens az OSM Overpass API-hoz
│   ├── ProtectedAreasLayer.java      ← N2K réteg (Natura 2000, nemzeti parkok)
│   ├── AirspaceLayer.java            ← LGT réteg (OpenAIP Core API, magassági szűrővel)
│   └── LandUseLayer.java             ← ZÓN réteg (OSM landuse: lakó, ipari, katonai, repülőtér)
└── dji/
    ├── DJIHelper.java                ← M04 SDK init, telemetria (reflection)
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
- [M01 L1 Üzleti folyamat](M01_MISSZIO_TERVEZO/M01_L1_UZLETI_FOLYAMAT.md)
- [M02 L1 Grid Engine folyamat](M02_GRID_ENGINE/M02_L1_UZLETI_FOLYAMAT.md)
- [M03 L1 Export/Import folyamat](M03_EXPORT_IMPORT/M03_L1_UZLETI_FOLYAMAT.md)
- [M04 L1 DJI Integráció folyamat](M04_DJI_INTEGRACIO/M04_L1_UZLETI_FOLYAMAT.md)
