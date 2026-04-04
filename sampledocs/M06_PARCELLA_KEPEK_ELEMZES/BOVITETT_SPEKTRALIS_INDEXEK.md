# Bővített Spektrális Indexek — Specifikáció

**Modul:** M06 — Parcella, Képek, Elemzés
**Verzió:** v0.6.0
**Dátum:** 2026-03-19

---

## Összefoglaló

A meglévő 4 index (NDVI, NDRE, EVI, SAVI) mellé 6 új spektrális index kerül bevezetésre.
Az indexek drone és műhold felvételekből egyaránt számolhatók, ahol a szükséges sávok rendelkezésre állnak.

---

## Új indexek

### 1. GNDVI — Green Normalized Difference Vegetation Index

- **Képlet:** `(NIR - Green) / (NIR + Green)`
- **Szükséges sávok:** NIR, Green
- **Elérhetőség:** rgb_nir ✅ | micasense ✅ | sentinel2 ✅
- **Értéktartomány:** −1 … +1
- **Hasznosság:** A klorofill-tartalomra érzékenyebb, mint az NDVI. Korai stressz detektálásra alkalmas, amikor a növény vizuálisan még zöldnek tűnik.
- **Normalizálás:** Nem szükséges (ratio-típusú index).

### 2. VARI — Visible Atmospherically Resistant Index

- **Képlet:** `(Green - Red) / (Green + Red - Blue)`
- **Szükséges sávok:** Red, Green, Blue
- **Elérhetőség:** rgb_nir ✅ | micasense ✅ | sentinel2 ✅
- **Értéktartomány:** −1 … +1 (clip)
- **Hasznosság:** Kizárólag látható sávokból számolt, így NIR-sáv nélküli RGB drónfelvételekhez is alkalmas. Vegetáció-borítottság becslése.
- **Normalizálás:** Nem szükséges (ratio-típusú index).
- **Megjegyzés:** Egyedülálló abban, hogy 3-sávos RGB képekből is számolható — ehhez a `rgb` layout-ot is támogatni kell.

### 3. ExG — Excess Green Index

- **Képlet:** `2 * Green_n - Red_n - Blue_n` ahol `X_n = X / (R+G+B)`
- **Szükséges sávok:** Red, Green, Blue
- **Elérhetőség:** rgb_nir ✅ | micasense ✅ | sentinel2 ✅
- **Értéktartomány:** −1 … +2 (clip −1 … +1)
- **Hasznosság:** Gyomfelismerés, zöld biomassza mennyiség. Különösen hasznos korai növekedési fázisban, amikor a talaj is látható.
- **Normalizálás:** Chromatikus normalizálás (sávonként osztás az összegükkel).

### 4. MSAVI2 — Modified Soil-Adjusted Vegetation Index 2

- **Képlet:** `(2 * NIR + 1 - sqrt((2 * NIR + 1)² - 8 * (NIR - Red))) / 2`
- **Szükséges sávok:** NIR, Red
- **Elérhetőség:** rgb_nir ✅ | micasense ✅ | sentinel2 ✅
- **Értéktartomány:** 0 … +1
- **Hasznosság:** Automatikusan korrigálja a talajhatást (nincs szükség L paraméter kézi beállítására, mint SAVI-nál). Ritka vegetációnál megbízhatóbb.
- **Normalizálás:** Percentilis-alapú [0,1] normalizálás szükséges (mint SAVI-nál).

### 5. CIgreen — Chlorophyll Index Green

- **Képlet:** `(NIR / Green) - 1`
- **Szükséges sávok:** NIR, Green
- **Elérhetőség:** rgb_nir ✅ | micasense ✅ | sentinel2 ✅
- **Értéktartomány:** −1 … ∞ (clip 0 … 10 a megjelenítéshez)
- **Hasznosság:** Klorofill-koncentráció közvetlen becslése. Érzékenyebb a klorofill-változásra, mint az NDVI, különösen sűrű vegetációnál, ahol az NDVI telítődik.
- **Normalizálás:** Percentilis-alapú normalizálás szükséges. A vizualizáció [0, 5] tartományra skálázható.
- **Colormap:** `YlGn` (sárga-zöld) 0–5 tartomány.

### 6. NDMI — Normalized Difference Moisture Index

- **Képlet:** `(NIR - SWIR1) / (NIR + SWIR1)`
- **Szükséges sávok:** NIR, SWIR1
- **Elérhetőség:** rgb_nir ❌ | micasense ❌ | sentinel2 ✅
- **Értéktartomány:** −1 … +1
- **Hasznosság:** Növényi vízstressz és nedvességi állapot. Aszály-monitoring és öntözési döntéstámogatás.
- **Normalizálás:** Nem szükséges (ratio-típusú index).
- **Megjegyzés:** Csak Sentinel-2-ből számolható (drone-on jellemzően nincs SWIR sáv).

---

## Kompatibilitási mátrix (teljes, bővített)

| Index | rgb_nir (4-band) | micasense (5-band) | sentinel2 (12-band) | rgb (3-band) |
|-------|:-:|:-:|:-:|:-:|
| NDVI | ✅ | ✅ | ✅ | ❌ |
| NDRE | ❌ | ✅ | ✅ | ❌ |
| EVI | ✅ | ✅ | ✅ | ❌ |
| SAVI | ✅ | ✅ | ✅ | ❌ |
| **GNDVI** | ✅ | ✅ | ✅ | ❌ |
| **VARI** | ✅ | ✅ | ✅ | ✅ |
| **ExG** | ✅ | ✅ | ✅ | ✅ |
| **MSAVI2** | ✅ | ✅ | ✅ | ❌ |
| **CIgreen** | ✅ | ✅ | ✅ | ❌ |
| **NDMI** | ❌ | ❌ | ✅ | ❌ |

---

## Implementációs állapot — ✅ KÉSZ

### 1. `utils/spectral_indices.py` — ✅ implementálva

- `BAND_MAPS`: `rgb` layout hozzáadva (VARI, ExG 3-sávos képekhez)
- `INDEX_REQUIREMENTS`: mind a 10 index definiálva
- `INDEX_VIS_PARAMS`: egyedi colormap/tartomány indexenként
- `INDEX_DESCRIPTIONS`: magyar nyelvű leírások az UI-hoz
- 6 új metódus: `compute_gndvi`, `compute_vari`, `compute_exg`, `compute_msavi2`, `compute_cigreen`, `compute_ndmi`
- `compute_index_from_bands` dispatcher: mind a 10 index támogatva

### 2. `utils/geotiff.py` — ✅ nincs változás szükséges

- `detect_band_layout()`: `count == 3` → `"rgb"` már implementálva volt

### 3. `_pages/8_🛰️_Parcel_Analysis.py` — ✅ implementálva

- `layout_options`: `["rgb", "rgb_nir", "micasense", "sentinel2"]`
- Egyedi colormap-ek és skálafeliratok indexenként
- `import numpy as np` hozzáadva (Prescription Map `np.isnan`/`np.mean` bug javítva)
- **Gyors elemzés mód** hozzáadva (parcella nélküli közvetlen fájlfeltöltés)

### 4. Dokumentáció — ✅ frissítve

- Jelen dokumentum (BOVITETT_SPEKTRALIS_INDEXEK.md)
- Felhasználói útmutató (FELHASZNALOI_UTMUTATO.md) — Spektrális elemzés szekció bővítve

---

## Vizualizációs paraméterek indexenként

| Index | Colormap | vmin | vmax | Skálafelirat |
|-------|----------|------|------|-------------|
| NDVI | RdYlGn | −1 | +1 | Piros (−1) → Sárga (0) → Zöld (+1) |
| NDRE | RdYlGn | −1 | +1 | Piros (−1) → Sárga (0) → Zöld (+1) |
| EVI | RdYlGn | −1 | +1 | Piros (−1) → Sárga (0) → Zöld (+1) |
| SAVI | RdYlGn | −1 | +1 | Piros (−1) → Sárga (0) → Zöld (+1) |
| **GNDVI** | RdYlGn | −1 | +1 | Piros (−1) → Sárga (0) → Zöld (+1) |
| **VARI** | RdYlGn | −1 | +1 | Piros (−1) → Sárga (0) → Zöld (+1) |
| **ExG** | RdYlGn | −1 | +1 | Piros (−1) → Sárga (0) → Zöld (+1) |
| **MSAVI2** | RdYlGn | 0 | +1 | Piros (0) → Zöld (+1) |
| **CIgreen** | YlGn | 0 | 5 | Sárga (0) → Sötétzöld (5) |
| **NDMI** | RdYlBu | −1 | +1 | Piros/száraz (−1) → Kék/nedves (+1) |

---

## Prescription Map — index-alapú zónatérkép ✅ implementálva

A Prescription Map két forráson működik:

### 1. Tőszám-sűrűség alapú (meglévő — Counting oldalon)
- **Input:** YOLO detekciók bounding box centroidjai
- **Metrika:** detekciók száma / cella
- **Elérhetőség:** Counting és Results oldalak

### 2. Spektrális index alapú (új — Parcel Analysis oldalon)
- **Input:** Bármely kiszámolt spektrális index (NDVI, GNDVI, MSAVI2, NDMI, stb.)
- **Metrika:** index átlagérték / cella
- **Elérhetőség:** Parcel Analysis → Spektrális elemzés tab → index számítás után

### Elhelyezés az UI-ban

A Parcel Analysis oldalon két mód érhető el:

#### A) Parcella-alapú mód
```
Parcel Analysis → [📋 Parcella-alapú elemzés] mód
  ├─ Parcella kiválasztás
  ├─ Tab: Spektrális elemzés
  │   ├─ Kép kiválasztás (parcellához rendelt képekből)
  │   ├─ Band elrendezés
  │   ├─ Index kiválasztás + Számítás gomb
  │   ├─ Eredmény: RGB + Index térkép + Statisztikák
  │   ├─ Export PNG gomb
  │   └─ 🗺️ Prescription Map [expander]
  ├─ Tab: Képek / Térkép / Idősor / Sentinel-2
```

#### B) Gyors elemzés mód (parcella nélkül) ✅ ÚJ
```
Parcel Analysis → [🔬 Gyors elemzés] mód
  ├─ Képforrás választás:
  │   ├─ 🖼️ Médiatárból (korábban feltöltött ≥3 sávos GeoTIFF-ek)
  │   └─ 📤 Új fájl feltöltése (auto-mentés a Médiatárba)
  ├─ Kép metaadatok: sávszám, méret, georeferáltság
  ├─ Band elrendezés (auto-detektálva)
  ├─ Index kiválasztás + Számítás gomb
  ├─ Eredmény: RGB + Index térkép + Statisztikák + Skálafelirat
  ├─ Export PNG gomb
  └─ 🗺️ Prescription Map [expander]
       ├─ Cella méret slider (1–20 m, alapért. 5 m)
       ├─ Statisztikák: cellák száma, érvényes cellák, átlag index
       └─ Export: Heatmap PNG | CSV | GeoJSON (csak georeferált képnél)
```

> **Megjegyzés:** A Gyors elemzés mód lehetővé teszi, hogy a felhasználó egy ODM ortomozaikot
> vagy bármilyen GeoTIFF-et közvetlenül töltsön fel és elemezzen, parcella létrehozása nélkül.
> A Médiatárból (M09) közvetlenül választható kép, vagy új fájl feltöltésekor automatikusan
> bekerül a Médiatárba — így legközelebb már onnan elérhető.

### Implementáció

**Új osztály:** `SpectralPrescriptionMapGenerator` (utils/prescription_map.py)

- `generate_grid(cell_size_m)` → rács generálás (azonos logika mint detekció-alapú)
- `mean_per_cell(grid)` → cellaátlag számítás (NaN-toleráns)
- `_classify_zones(means)` → kvartilis-alapú zónabesorolás:
  - `nodata` — NaN cella
  - `low` — < Q25 (stresszelt / gyenge vegetáció)
  - `medium` — Q25–Q75 (átlagos)
  - `high` — ≥ Q75 (egészséges / sűrű)
- `generate_heatmap_image()` → piros→sárga→zöld overlay
- `export_csv()` → row, col, center_lon/lat, index_name, mean_value, zone
- `export_geojson()` → WGS84 Polygon per cella (GeoTIFF esetén)
