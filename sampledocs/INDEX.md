# DrónTerápia - Dokumentáció Főoldal

**Verzió:** v0.10.0
**Utolsó frissítés:** 2026-03-28
**Rendszer:** Mezőgazdasági Tőszámláló Rendszer

---

## Dokumentáció Struktúra

Ez a dokumentáció a wmsminta mintát követve **modulonként 4 szinten** készült el.

### Szintek magyarázata

| Szint | Neve | Tartalma |
|-------|------|----------|
| **L1** | Üzleti Folyamat | Végponttól végpontig tartó folyamat, szereplők, események, végállapotok |
| **L2** | Döntési Logika | Üzleti szabályok, validációk, feltételes logika, döntési pontok |
| **L3** | Állapotgép és Engine | Technikai implementáció, adatmodell, állapotok, forrásfájlok |
| **L4** | Tranzakciós és Párhuzamos | Adatperzisztencia, session kezelés, párhuzamosság, integritás |

---

## Modulok

### M01 – Annotation (Képcímkézés és Dataset Kezelés)
Drónfelvételek feldarabolása, kézi bounding box annotáció, training verziók kezelése.

| Szint | Fájl |
|-------|------|
| L1 | [M01_L1_UZLETI_FOLYAMAT.md](M01_ANNOTATION/M01_L1_UZLETI_FOLYAMAT.md) |
| L2 | [M01_L2_DONTESI_LOGIKA.md](M01_ANNOTATION/M01_L2_DONTESI_LOGIKA.md) |
| L3 | [M01_L3_ALLAPOTGEP_ES_ENGINE.md](M01_ANNOTATION/M01_L3_ALLAPOTGEP_ES_ENGINE.md) |
| L4 | [M01_L4_TRANZAKCIOS_ES_PARHUZAMOS.md](M01_ANNOTATION/M01_L4_TRANZAKCIOS_ES_PARHUZAMOS.md) |

### M02 – Training (Modell Betanítás)
YOLOv8/v11 modell betanítása annotált adatokból, ONNX export, job menedzsment.

| Szint | Fájl |
|-------|------|
| L1 | [M02_L1_UZLETI_FOLYAMAT.md](M02_TRAINING/M02_L1_UZLETI_FOLYAMAT.md) |
| L2 | [M02_L2_DONTESI_LOGIKA.md](M02_TRAINING/M02_L2_DONTESI_LOGIKA.md) |
| L3 | [M02_L3_ALLAPOTGEP_ES_ENGINE.md](M02_TRAINING/M02_L3_ALLAPOTGEP_ES_ENGINE.md) |
| L4 | [M02_L4_TRANZAKCIOS_ES_PARHUZAMOS.md](M02_TRAINING/M02_L4_TRANZAKCIOS_ES_PARHUZAMOS.md) |

### M03 – Counting (Tőszámlálás / Inference)
Drónfelvételek automatikus elemzése betanított YOLO modellekkel, export.

| Szint | Fájl |
|-------|------|
| L1 | [M03_L1_UZLETI_FOLYAMAT.md](M03_COUNTING/M03_L1_UZLETI_FOLYAMAT.md) |
| L2 | [M03_L2_DONTESI_LOGIKA.md](M03_COUNTING/M03_L2_DONTESI_LOGIKA.md) |
| L3 | [M03_L3_ALLAPOTGEP_ES_ENGINE.md](M03_COUNTING/M03_L3_ALLAPOTGEP_ES_ENGINE.md) |
| L4 | [M03_L4_TRANZAKCIOS_ES_PARHUZAMOS.md](M03_COUNTING/M03_L4_TRANZAKCIOS_ES_PARHUZAMOS.md) |

### M04 – Felhasználókezelés és Hitelesítés
Regisztráció, bejelentkezés, session kezelés, jelszókezelés, admin funkciók.

| Szint | Fájl |
|-------|------|
| L1 | [M04_L1_UZLETI_FOLYAMAT.md](M04_FELHASZNALOKELES/M04_L1_UZLETI_FOLYAMAT.md) |
| L2 | [M04_L2_DONTESI_LOGIKA.md](M04_FELHASZNALOKELES/M04_L2_DONTESI_LOGIKA.md) |
| L3 | [M04_L3_ALLAPOTGEP_ES_ENGINE.md](M04_FELHASZNALOKELES/M04_L3_ALLAPOTGEP_ES_ENGINE.md) |
| L4 | [M04_L4_TRANZAKCIOS_ES_PARHUZAMOS.md](M04_FELHASZNALOKELES/M04_L4_TRANZAKCIOS_ES_PARHUZAMOS.md) |

### M05 – Modell Nyilvántartás (Model Registry)
ONNX modellek kezelése, metaadatok, láthatóság, használati statisztikák.

| Szint | Fájl |
|-------|------|
| L1 | [M05_L1_UZLETI_FOLYAMAT.md](M05_MODELL_NYILVANTARTAS/M05_L1_UZLETI_FOLYAMAT.md) |
| L2 | [M05_L2_DONTESI_LOGIKA.md](M05_MODELL_NYILVANTARTAS/M05_L2_DONTESI_LOGIKA.md) |
| L3 | [M05_L3_ALLAPOTGEP_ES_ENGINE.md](M05_MODELL_NYILVANTARTAS/M05_L3_ALLAPOTGEP_ES_ENGINE.md) |
| L4 | [M05_L4_TRANZAKCIOS_ES_PARHUZAMOS.md](M05_MODELL_NYILVANTARTAS/M05_L4_TRANZAKCIOS_ES_PARHUZAMOS.md) |

### M06 – Parcella Képek és Spektrális Analízis ✅
Drón- és műholdképek hozzárendelése parcellákhoz, NDVI/NDRE/EVI/SAVI számítás, idősor.
Sentinel-2 L2A letöltés CDSE API-n keresztül.
**Implementálva és ellenőrizve: 2026-03-07 (v1), 2026-03-07 (v2 – CDSE)**

| Szint | Fájl | Státusz |
|-------|------|---------|
| L1 | [M06_L1_UZLETI_FOLYAMAT.md](M06_PARCELLA_KEPEK_ELEMZES/M06_L1_UZLETI_FOLYAMAT.md) | ✅ v2.1.0 — Parcella UI v0.10.0 |
| L2 | [M06_L2_DONTESI_LOGIKA.md](M06_PARCELLA_KEPEK_ELEMZES/M06_L2_DONTESI_LOGIKA.md) | ✅ v2.1.0 — Parcella UI v0.10.0 |
| L3 | [M06_L3_ALLAPOTGEP_ES_ENGINE.md](M06_PARCELLA_KEPEK_ELEMZES/M06_L3_ALLAPOTGEP_ES_ENGINE.md) | ✅ v2.1.0 — Parcella UI v0.10.0 |
| L4 | [M06_L4_TRANZAKCIOS_ES_PARHUZAMOS.md](M06_PARCELLA_KEPEK_ELEMZES/M06_L4_TRANZAKCIOS_ES_PARHUZAMOS.md) | ✅ v2.1.0 — Parcella UI v0.10.0 |

### M07 – Repülési Terv Generátor (Flight Planning) ✅
Drón kamera profil alapú GSD-számítás, grid misszió generálás, KMZ export DJI Fly-hoz.
Parcella-tudatos, idősor-támogató. **Modul kulcs: `flight`**
**Implementálva és ellenőrizve: 2026-03-18**

| Szint | Fájl | Státusz |
|-------|------|---------|
| L1 | [M07_L1_UZLETI_FOLYAMAT.md](M07_REPULESI_TERV/M07_L1_UZLETI_FOLYAMAT.md) | ✅ v1.1.0 — Preset rendszer, akkumulátor felosztás |
| L2 | [M07_L2_DONTESI_LOGIKA.md](M07_REPULESI_TERV/M07_L2_DONTESI_LOGIKA.md) | ✅ v1.1.0 — Preset rendszer, akkumulátor felosztás |
| L3 | [M07_L3_ALLAPOTGEP_ES_ENGINE.md](M07_REPULESI_TERV/M07_L3_ALLAPOTGEP_ES_ENGINE.md) | ✅ v1.1.0 — Preset rendszer, akkumulátor felosztás |
| L4 | [M07_L4_TRANZAKCIOS_ES_PARHUZAMOS.md](M07_REPULESI_TERV/M07_L4_TRANZAKCIOS_ES_PARHUZAMOS.md) | ✅ v1.1.0 — Preset rendszer, akkumulátor felosztás |

### M08 – ODM Ortomozaik Pipeline ✅
Nyers drónképek (JPG/TIFF) ortomozaikká alakítása OpenDroneMap segítségével.
NodeODM Docker integráció, job queue, méretlimit, automatikus parcella-csatolás.
**Modul kulcs: `odm`**
**Implementálva és ellenőrizve: 2026-03-18**

| Szint | Fájl | Státusz |
|-------|------|---------|
| L1 | [M08_L1_UZLETI_FOLYAMAT.md](M08_ODM_ORTOMOZAIK/M08_L1_UZLETI_FOLYAMAT.md) | ✅ v1.0.0 |
| L2 | [M08_L2_DONTESI_LOGIKA.md](M08_ODM_ORTOMOZAIK/M08_L2_DONTESI_LOGIKA.md) | ✅ v1.0.0 |
| L3 | [M08_L3_ALLAPOTGEP_ES_ENGINE.md](M08_ODM_ORTOMOZAIK/M08_L3_ALLAPOTGEP_ES_ENGINE.md) | ✅ v1.0.0 |
| L4 | [M08_L4_TRANZAKCIOS_ES_PARHUZAMOS.md](M08_ODM_ORTOMOZAIK/M08_L4_TRANZAKCIOS_ES_PARHUZAMOS.md) | ✅ v1.0.0 |

### M10 – AI Segéd (Beépített asszisztens)
Dokumentáció- és szaktudás-alapú AI chat segéd, amely természetes nyelven válaszol.
Hibrid UI: dedikált chat oldal + lebegő segítség-gomb minden oldalon.
**Modul kulcs: `assistant`**

| Szint | Fájl | Státusz |
|-------|------|---------|
| L1 | [M10_L1_UZLETI_FOLYAMAT.md](M10_AI_SEGED/M10_L1_UZLETI_FOLYAMAT.md) | ✅ v1.0.0 |
| L2 | M10_L2_DONTESI_LOGIKA.md | ⏳ Következő |
| L3 | M10_L3_ALLAPOTGEP_ES_ENGINE.md | ⏳ Következő |
| L4 | M10_L4_TRANZAKCIOS_ES_PARHUZAMOS.md | ⏳ Következő |

---

## Gyors Navigáció – Forrás Dokumentumok

A következő eredeti dokumentumok kerültek beolvasztásra és strukturálva ebbe a rendszerbe.
A forrásanyagok **nem törlendők** – referencia és auditálási célból megmaradnak.

### Felhasználói dokumentáció
- [FELHASZNALOI_UTMUTATO.md](../FELHASZNALOI_UTMUTATO.md) – Átfogó felhasználói útmutató
- [GYORS_UTMUTATO.md](../GYORS_UTMUTATO.md) – Gyors kezdés, email értesítések
- [ADMIN_GUIDE.md](../ADMIN_GUIDE.md) – Admin panel útmutató
- [DEPLOYMENT.md](../DEPLOYMENT.md) – Telepítési útmutató
- [README.md](../README.md) – Projekt áttekintés

### Fejlesztői dokumentáció
- [ANNOTATION_TOOL.md](../ANNOTATION_TOOL.md) – Annotáció modul technikai leírás
- [TRAINING_MODULE.md](../TRAINING_MODULE.md) – Training modul technikai leírás
- [MULTI_DATASET_SYSTEM.md](../MULTI_DATASET_SYSTEM.md) – Dataset kezelés
- [GSD_ADAPTIVE_SLIDING_WINDOW.md](../GSD_ADAPTIVE_SLIDING_WINDOW.md) – Sliding window
- [FEATURE_PLAN_IMAGE_PREPROCESSING_PRESETS.md](../FEATURE_PLAN_IMAGE_PREPROCESSING_PRESETS.md) – Preprocessing presetek
- [MODEL_EXPORT_GUIDE.md](../MODEL_EXPORT_GUIDE.md) – ONNX export
- [ONNX_CLASS_MAPPING.md](../ONNX_CLASS_MAPPING.md) – Osztály leképezés
- [TRAINING_JOB_INTEGRATION.md](../TRAINING_JOB_INTEGRATION.md) – Job management
- [INTEGRITY_CHECKER_V2_GUIDE.md](../INTEGRITY_CHECKER_V2_GUIDE.md) – Adatintegritás
- [CLEANUP_GUIDE.md](../CLEANUP_GUIDE.md) – Karbantartás

### Tervezési és roadmap dokumentumok
- [ROADMAP.md](../ROADMAP.md) – Fejlesztési tervek
- [SAAS_BUSINESS_MODEL.md](../SAAS_BUSINESS_MODEL.md) – Üzleti modell
- [PRODUCTION_READINESS.md](PRODUCTION_READINESS.md) – Biztonsági és migrációs terv (publikálás előtti teendők)

---

## Rendszer Architektúra

```
Drón Felvétel (TIFF/GeoTIFF)
        │
        ▼
┌─────────────────┐
│   M01           │   Képcímkézés és Dataset Kezelés
│   Annotation    │   pages/1_Annotation.py
│                 │   core/tiling.py, utils/dataset_manager.py
└────────┬────────┘
         │ Training Version
         ▼
┌─────────────────┐
│   M02           │   Modell Betanítás
│   Training      │   pages/2_Training.py
│                 │   core/trainer.py, utils/training_wrapper.py
└────────┬────────┘
         │ ONNX Modell
         ▼
┌─────────────────┐
│   M03           │   Tőszámlálás / Inference
│   Counting      │   pages/3_Counting.py
│                 │   core/predictor.py, core/sliding_window_detector.py
└────────┬────────┘
         │ Eredmények
         ▼
    CSV / JSON / GeoJSON / PNG
```

### Felmérési Workflow (M07 → M08 → M06/M03)

```
[DJI Mini 3 / Mini 4 Pro]   ← olcsó fogyasztói drón elegendő
        │
        ▼
┌─────────────────┐
│   M07           │   Repülési Terv Generátor         [modul: flight]
│   Flight Plan   │   pages/9_Flight_Planning.py
│                 │   GSD → magasság → grid → KMZ
└────────┬────────┘
         │ KMZ (DJI Fly app-ba töltve)
         ▼
    [TEREPI REPÜLÉS]   →   JPG képek memóriakártyán
         │
         ▼
┌─────────────────┐
│   M08           │   ODM Ortomozaik Pipeline          [modul: odm]
│   ODM           │   pages/11_ODM_Processing.py
│                 │   NodeODM Docker → GeoTIFF
└────────┬────────┘
         │ GeoTIFF (automatikusan parcellához csatolva)
         ├──────────────────────┐
         ▼                      ▼
┌─────────────────┐   ┌─────────────────┐
│   M06           │   │   M03           │
│   Spectral      │   │   Counting      │
│   NDVI, VARI    │   │   Tőszámlálás   │
└─────────────────┘   └─────────────────┘

Megjegyzés: M08 M07 nélkül is használható (B útvonal: külső mission planning)
            M06 M08 nélkül is használható (C útvonal: közvetlen GeoTIFF import ✅)
```

### Keresztmetszeti modulok

```
M04 Felhasználókezelés          M05 Modell Nyilvántartás
utils/user_manager.py           utils/model_metadata.py
utils/session_manager.py        models/model_registry.json
utils/audit_log.py              models/*.onnx
utils/path_manager.py
utils/auth_helpers.py
data/users.json
data/sessions.json              (bcrypt hash-elt tokenek)
data/audit_log.json

M06 Parcella Képek + Spektrális Analízis (✅ implementálva 2026-03-07)
utils/parcel_manager.py (+images)   utils/spectral_indices.py
utils/geotiff.py (+multi-band)      pages/8_Parcel_Analysis.py
data/parcels.json (+images[])       10 spektrális index (NDVI, GNDVI, VARI stb.)
                                    Gyors elemzés mód (parcella nélkül)

M10 AI Segéd (✅ implementálva 2026-03-25)                [modul: assistant]
utils/ai_assistant.py               _pages/14_💬_AI_Seged.py
data/ai_knowledge_base.md           Anthropic Claude Haiku 4.5 API
Lebegő segítség-gomb (Home.py)      30 kérdés/óra rate limit

M09 Médiatár (✅ implementálva 2026-03-19)
utils/media_manager.py              _pages/10_🖼️_Media_Library.py
data/users/{user_id}/media/         media_registry.json (per-user)
GeoTIFF metaadat felismerés          Integrálva: Gyors elemzés képforrás

Főoldal Dashboard (✅ implementálva 2026-03-19, beolvasztva Home.py-ba)
Home.py _render_dashboard()          Fő metrikák + workflow + parcella térkép
Modell & detekció statisztikák       Aktivitás idővonal (10 utolsó esemény)
Médiatár összesítés                  Admin szekció (rendszer szintű)

M07 Repülési Terv Generátor (✅ implementálva 2026-03-18)   [modul: flight]
utils/flight_planner.py             utils/kmz_generator.py
utils/drone_manager.py              _pages/11_✈️_Flight_Planning.py
_pages/9_🚁_Drones.py              data/camera_profiles.json
data/user_drones.json

M08 ODM Ortomozaik Pipeline (✅ implementálva 2026-03-18)  [modul: odm]
utils/odm_manager.py                _pages/12_🛠️_ODM_Processing.py
docker-compose.yml (nodeodm)        NodeODM Docker (port 3000)
[Min. 8 GB RAM szerver]
```

---

## Modul Rendszer

A navigáció és az oldalak láthatósága felhasználónként, modul-alapon szabályozott.
Az admin felületen (`10_Admin.py`) per-user checkboxokkal állítható be.

| Modul kulcs | Megjelenített név | Modul | Érintett oldalak |
|-------------|------------------|-------|-----------------|
| *(alap)* | 🗺️ Parcellák | — | `7_Parcels.py`, `6_Map.py`, `4_Results.py` — mindenkinél |
| `flight` | ✈️ Repülési Terv | M07 | `9_Flight_Planning.py` |
| `odm` | 🛠️ Ortomozaik | M08 | `11_ODM_Processing.py` |
| `counting` | 🌾 Tőszámlálás | M03 | `3_Counting.py` |
| `spectral` | 📊 Spektrális Elemzés | M06 | `8_Parcel_Analysis.py` |
| `model` | 🔬 Modell Fejlesztés | M01+M02+M05 | `1_Annotation.py`, `2_Training.py`, `5_Models.py` |
| `assistant` | 💬 AI Segéd | M10 | `14_AI_Seged.py` |

### Előre definiált bundle-k

| Bundle | Modulok | Célcsoport |
|--------|---------|------------|
| Alap | `counting` | Kész képpel érkező operátor |
| Elemző | `counting`, `spectral` | NDVI + számlálás, saját ortomozaikkal |
| Felmérő | `flight`, `odm`, `counting`, `spectral` | Teljes önálló workflow DJI Mini-vel |
| Fejlesztő | `model` | Saját modell építés |
| Teljes | mind | Admin / power user |

### Navigáció szűrés (Home.py)

A `Home.py` `st.navigation()` alapú, szakaszos sidebar. A megjelenített oldalak listája
a bejelentkezett felhasználó `user["modules"]` listájától függ. Admin `role` esetén
minden oldal látható, modul-ellenőrzés nélkül.

---

## Technológiai Stack

| Réteg | Technológia |
|-------|-------------|
| Web UI | Streamlit 1.52+ |
| ML Framework | YOLOv8/v11 (Ultralytics) |
| Inference | ONNX Runtime 1.20+ (CPU) |
| Képfeldolgozás | OpenCV 4.8+, Pillow 10.0+, Rasterio 1.3+ |
| Adatkezelés | JSON (users, registry), CSV |
| Geospatial | PyProj 3.6+, GeoJSON, Folium 0.15+ |
| Spektrális analízis | Matplotlib 3.7+, NumPy |
| Hitelesítés | bcrypt (jelszó + session token hash), session_state alapú session |
| Audit log | utils/audit_log.py → data/audit_log.json |
| Path izoláció | utils/path_manager.py — data/users/{user_id}/ |
| Email | Gmail SMTP (python-dotenv) |
| AI Segéd | Anthropic Claude Haiku 4.5 API (anthropic SDK) |

---

---

## Changelog

### v0.10.0 (2026-03-28) — Parcella UI újraírás, Map rétegkezelés, Flight Planning presetek + akkumulátor felosztás

- **F20: Parcella létrehozás térképen**
  - Az "➕ Új parcella létrehozása" expander mindig látható (nem csak 0 parcella esetén)
  - Folium térkép Draw pluginnel — polygon és téglalap rajzolható közvetlenül
  - Alaptérkép választó: OpenStreetMap, Satellite (Esri), CartoDB Positron, CartoDB Dark Matter
  - Rajzolás után megjelenik a parcella adatok form (név, növénykultúra, jegyzetek)
  - Bug fix: korábban csak akkor jelent meg a létrehozó form, ha nem volt parcella
- **F21: Parcella + Művelési Napló UX összevonás**
  - Parcella adatok és Művelési Napló mostantól egyetlen expanderben, 3 tabban: 📊 Adatok | 📅 Művelési napló | 🖼️ Képek
  - A művelési napló mindig a kiválasztott parcellához kötődik (vizuálisan is)
- **F22: Map — egyedi parcella réteg toggle**
  - Új "🌾 Parcellák kiválasztása" expander, minden parcellához külön checkbox
  - "Mind be / Mind ki" gyorsgombok
  - Minden parcella külön FeatureGroup-ként kerül a térképre
- **F23: Map — réteg sorrend vezérlés**
  - "🗂️ Raszter réteg sorrend" expander (csak ha GeoTIFF + Heatmap egyszerre aktív)
  - ▲/▼ gombokkal módosítható a GeoTIFF és Heatmap rétegek egymáshoz viszonyított sorrendje
  - Sorrend session_state-ben tárolódik
- **F24: Map — Leaflet LayerControl**
  - `folium.LayerControl(collapsed=False)` hozzáadva — jobb felső sarokban réteg kapcsoló a térképen belül
  - Minden GeoTIFF, Heatmap, Parcella és Detekció réteg egyedileg kapcsolható
- **F25: Flight Planning — Repülési paraméter presetek**
  - "💾 Preset mentése" expander a repülési paraméterek alatt: névvel menthetők a beállítások
  - "📂 Preset betöltése" expander a paraméterek felett: selectboxból visszatölthető + előnézet
  - Preset tartalom: magasság, sebesség, frontális/oldalsó átfedés, irány, drón neve
  - Tárolás: `data/users/{user_id}/flight_presets.json`
  - Ha ugyanolyan névvel ment: felülírja (update)
  - "🗑️ Törlés" gomb a preset mellé
- **F26: Flight Planning — Akkumulátor-alapú misszió felosztás**
  - "🔋 Felosztás akkumulátoronként" expander a KMZ export alatt
  - Input: akkumulátor repülési idő (perc, default 25) + felhasználható % (default 80%)
  - Ha belefér 1 akkumulátorba: zöld tájékoztató üzenet
  - Ha nem: szegmens táblázat (waypoint szám, táv, idő) + ZIP letöltése (N×KMZ)
  - Felosztás akkumulált repülési idő alapján (pontosabb mint waypoint-szám alapján)
  - Fájlnév konvenció: `DronTerapia_Mini4Pro_5ha_01of3.kmz`, `_02of3.kmz` stb.
- Módosított fájlok: `_pages/7_📋_Parcels.py`, `_pages/6_🗺️_Map.py`, `_pages/11_✈️_Flight_Planning.py`, `utils/flight_planner.py`, `utils/kmz_generator.py`
- **M06 L3, M07 L3** dokumentáció frissítve, felhasználói útmutató frissítve

### v0.9.3 (2026-03-27) — Médiatár képelőnézet + Map heatmap overlay + UI javítások

- **F15: Médiatár képelőnézet**
  - Korábban metadata-only volt, most tényleges kép thumbnail jelenik meg (JPEG, max 700px)
  - GeoTIFF: `GeoTIFFHandler.get_image()`, egyéb: PIL Image thumbnail
  - `@st.cache_data` cache `(file_path, mtime)` kulccsal — automatikus invalidálás
  - 2 oszlopos layout: előnézet (2/3) + metaadat (1/3)
- **F16: Heatmap mentés médiatárba**
  - Minden heatmap (raszter, KDE, cluster) mellett `💾 Médiatárba` gomb
  - Mentéskor `geo_bounds` (`sw_lat/lon`, `ne_lat/lon`) is eltárolódik, ha georeferált a forráskép
  - `MediaManager.save_generated_image()` — bytes-t, nem UploadedFile-t vár
  - Fájlnév konvenció: pont helyett "m" → pl. `raster_heatmap_2m0_kepnev.png`
- **F17: Map oldal heatmap overlay**
  - Új `🌡️ Heatmap rétegek` checkbox + expander a Map oldalon
  - Médiatárból szűri: `image_type == "heatmap"` ÉS `geo_bounds` megadva ÉS fájl létezik
  - `folium.raster_layers.ImageOverlay` a tárolt PNG-vel + `geo_bounds` Folium bounds formátumra
  - Saját opacity slider (alapért. 0.65)
  - Térkép középpontja heatmap-re ugrik, ha nincs GeoTIFF kiválasztva
- **F18: Közös raszterméret slider**
  - Egyetlen slider a Detekciós hőtérkép és Prescription map szekcióra
  - `st.session_state['shared_cell_size_m']` — mindkét szekció innen olvassa
- **F19: Counting UI javítások**
  - `base_name` NameError fix — korábban a hőtérkép szekció a letöltés/mentés gombokban használta, de csak később volt definiálva
  - KDE és Cluster heatmap "Generate" gomb — korábban minden slider-húzásnál újragenerálódtak (szürke UI), most csak explicit kattintásra futnak, session state cache-sel
  - Megerősítő dialog robusztusabbá téve — GeoTIFF esetén mindig megjelenik, még ha `calculate_area_ha()` None-t ad is
  - Modell választás szekció összecsukható `st.expander`-be került; címsor mutatja az aktív modell nevét
- Módosított fájlok: `_pages/3_🔍_Counting.py`, `_pages/6_🗺️_Map.py`, `_pages/10_🖼️_Media_Library.py`, `utils/media_manager.py`
- **M03 L1** dokumentáció frissítve, felhasználói útmutató frissítve

### v0.9.2 (2026-03-27) — Háromféle heatmap + Kelési arány + Parcella vetési paraméterek

- **F12: Prescription map — háromféle térkép típus**
  - `📊 Raszteres heatmap` — meglévő, bővítve kelési arány móddal
  - `🌊 Folyamatos heatmap (KDE)` — numpy-alapú Gaussian kernel sűrűségbecslés, beállítható sávszélesség
  - `🔵 Cluster zónatérkép (K-means)` — 2/3/4 zóna, pure numpy K-means
- **F13: Kelési arány számítás** (csak GeoTIFF esetén)
  - Elméleti vetési sűrűség megadása: Sortáv×Tőtáv (cm) vagy közvetlen tő/ha érték
  - Forrás: parcellából (tárolt paraméter) vagy kézi megadás
  - Globális kelési arány % a statisztika sorban
  - Raszteres heatmap: kelési arány % alapú színezés (piros→sárga→zöld)
- **F14: Parcella vetési paraméterek**
  - Új mezők: `row_spacing_cm`, `plant_spacing_cm`, `target_density_per_ha`
  - Szerkesztés: `_pages/7_Parcels.py` — Vetési paraméterek szekció
  - Megadás módjai: Sortáv×Tőtáv (auto-számítás) | Közvetlen sűrűség | Nincs/Törlés
  - `utils/parcel_manager.py` — `create_parcel()` és `update_parcel()` bővítve
- Módosított fájlok: `utils/prescription_map.py`, `utils/parcel_manager.py`, `_pages/3_🔍_Counting.py`, `_pages/7_📋_Parcels.py`
- **M03 L1/L3** dokumentáció frissítve

### v0.9.1 (2026-03-25) — AI Segéd (M10)

- **M10: AI Segéd modul** — Beépített chat asszisztens Claude Haiku 4.5 API-val
  - Dedikált chat oldal (`_pages/14_💬_AI_Seged.py`) streaming válaszokkal
  - Lebegő „💬 Segéd" gomb minden oldalon (jobb alsó sarok)
  - Három tudásréteg: rendszer-dokumentáció (L), szaktudás (T), oldal-kontextus (K)
  - Szerkeszthető tudásbázis: `data/ai_knowledge_base.md`
  - Rate limiting: 30 kérdés/óra/felhasználó
  - Modul kulcs: `assistant` — admin által engedélyezhető per-user
- **Session cookie javítás** — Böngésző cookie (`dt_session`) a `session_state` mellett
  - `st.context.cookies` fallback teljes oldal-újratöltés után
  - `unsafe_allow_javascript=True` az `st.html()` hívásoknál (Streamlit 1.55 DOMPurify)
- Új fájlok: `utils/ai_assistant.py`, `_pages/14_💬_AI_Seged.py`, `data/ai_knowledge_base.md`
- Módosított fájlok: `Home.py`, `utils/session_manager.py`, `requirements.txt`, `.env.example`
- **M10 L1** üzleti dokumentáció elkészítve
- Felhasználói útmutató frissítve (11. fejezet: AI Segéd)

### v0.9.0 (2026-03-24) — GT Paraméter-kereső (2D Sweep)

- **F11: GT Paraméter-kereső üzemmód** — 2D grid search (conf × IoU) optimális detekciós paraméterek keresésére
  - Slider-alapú régió kijelölés (narancssárga keret + sötétítés)
  - Felhasználó által megadható sweep tartományok (min/max/lépésköz mindkét tengelyre)
  - 2D heatmap vizualizáció + legjobb kombináció kiemelése
  - Annotált kép a legjobb paraméterekkel + preset mentés gomb
  - Tile-szintű NMS lazítás (IoU=0.99) GT módban a nyers detekciók megőrzéséhez
  - Per-class globális NMS + biológiai NMS a sweep-ben (teljes pipeline konzisztencia)
  - GT módban normál eredmények elrejtése (csak sweep + következő lépés)
- **predict_raw()** metódus a `core/predictor.py`-ban — NMS-mentes nyers detekciók
- Módosított fájlok: `_pages/3_Counting.py`, `core/predictor.py`, `core/sliding_window_detector.py`
- **M03 L1/L2/L3** dokumentáció frissítve

### v0.8.2 (2026-03-23) — L3 engine dokumentacio kiegeszites

- **M03 L3** kiegeszitve: Detection Presets engine, Batch feldolgozas engine, Ground Truth validacio, Modell osszehasonlitas, Toszam trend, Model Rating/Review engine leirasok
- **M02 L3** kiegeszitve: Training Progress Tracking engine (callback lifecycle, adatmodell, API)
- Forrasfajl tablazatok frissitve (`detection_presets.py`, `annotation_qa.py`, `training_progress.py`)

### v0.8.1 (2026-03-21) — Annotacio minoseg-ellenorzes

- **F10: Annotation QA Engine** — Pre-training annotacio minoseg-ellenorzes
  - Uj fajl: `utils/annotation_qa.py`
  - Training oldalon: "Annotacio minoseg-ellenorzes" expander
  - Ellenorzesek: atfedo bboxok, meret outlierek, osztaly-egyensulytalansag,
    ures tile arany, annotacio suruseg, format validacio
  - 0-100 pontszam + A-F grade + grafikus elemzes (hisztogramok, tablazatok)

### v0.8.0 (2026-03-21) — Toszamlalas minoseg-javito funkciok

**Uj fajlok:**
- `utils/detection_presets.py` — Felhasznalonkenti detekcios preset kezelo (CRUD)
- `utils/training_progress.py` — YOLO training epoch-szintu progress tracking (callback factory)

**Modositott fajlok:**
- `_pages/3_Counting.py` — Detection Presetek UI, Batch feldolgozas, Konfidencia-analizis, Model Rating/Review UI, parameterek rogzitese
- `_pages/4_Results.py` — Detekcios parameterek megjelenitese, Modell osszehasonlitas, Ground Truth validacio, Toszam trend grafikon
- `_pages/2_Training.py` — Epoch-szintu progress bar, elo metrikak (mAP, loss, ETA)
- `core/trainer.py` — `job_id` parameter + YOLO callback regisztracio
- `utils/training_wrapper.py` — `job_id` atadas + progress cleanup
- `utils/detection_results_manager.py` — `detection_params` mezo + `conf_threshold` az indexben

**Funkciok:**
| Kod | Funkio | Leiras |
|-----|--------|--------|
| F1 | Detekcios parameterek rogzitese | Minden eredmennyel mentodnek a teljes parameterek (conf, iou, SW, TTA, bio NMS stb.) |
| F2 | Detection Preset rendszer | Parameterbeallitasok mentese/betoltese/torlese felhasznalonkent |
| F3 | Training Progress Tracking | Epoch-szintu progress bar, elo loss/mAP/ETA a Training oldalon |
| F4 | Modell osszehasonlitas | Results grid-ben tobbes kivalasztas + egymas melletti osszehasonlito nezet |
| F5 | Batch feldolgozas | Tobb kep egyideju feldolgozasa azonos parameterekkel + osszesito tablazat + CSV |
| F6 | Ground Truth validacio | Kezi toszam rogzitese + elteres %,  pontossag kiszamitas |
| F7 | Szamolasi trendek | Idovonal grafikon + statisztikak (atlag, szo ras, min/max) + kepenkneti szures |
| F8 | Model Rating/Review | Valos ertekelesek a modell kartyakon (1-5 csillag + szoveges velemeny) |
| F9 | Konfidencia-analizis | Hisztogram + kuszob-gorbe + GT alapu automatikus optimalis kuszob kereses |

---

*A dokumentáció a wmsminta 4-szintű struktúráját követi.*
*Forrás: wmsminta/M01_ARUATVETEL_L*.md*
