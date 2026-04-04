# L3 – Állapotgép és Engine – Parcella Képek és Spektrális Analízis

**Modul:** M06
**Szint:** L3 – Állapotgép és Engine
**Verzió:** v2.1.0
**Létrehozva:** 2026-03-07
**Frissítve:** 2026-03-07 (Sentinel-2 CDSE letöltő modul)
**Frissítve:** 2026-03-28 (Parcella UI újraírás, Map rétegkezelés)
**Implementálva (v1):** 2026-03-07 (Codex)
**Ellenőrizve (v1):** 2026-03-07 (Claude Architect)
**Implementálva (v2):** 2026-03-07 (Claude – explicit felhasználói kérésre)
**Megjegyzés:** FEATURE_PLAN_SENTINEL2_CDSE_DOWNLOAD.md tartalma beépítve és törölve.

---

## Forrásfájlok

| Fájl | Szerepkör | Státusz |
|------|-----------|---------|
| `utils/parcel_manager.py` | Parcella CRUD + képrekord csatolás | ✅ Implementálva |
| `utils/geotiff.py` | GeoTIFF olvasás + multi-band + band layout | ✅ Implementálva |
| `utils/spectral_indices.py` | NDVI/NDRE/EVI/SAVI számítás + vizualizáció | ✅ Implementálva |
| `utils/satellite_downloader.py` | CDSE keresés + letöltés + band stacking | ✅ Implementálva |
| `pages/8_Parcel_Analysis.py` | 5-tabos analízis oldal (+ Sentinel-2 tab) | ✅ Implementálva |
| `pages/7_Parcels.py` | Parcella CRUD UI — térképes létrehozás (Draw plugin), tabos parcella/napló nézet | ✅ Implementálva |
| `pages/6_Map.py` | Map viewer — egyedi parcella toggle, réteg sorrend, LayerControl | ✅ Implementálva |
| `requirements.txt` | matplotlib + folium + requests | ✅ Implementálva |

---

## UI változások (v2.1.0)

### 7_Parcels.py — Parcella létrehozás és nézet

**Parcella létrehozása (térképen):**
- "➕ Új parcella létrehozása" expander mindig látható az oldal tetején
- Expander nyitva van, ha nincs parcella; csukva ha van
- Alaptérkép választó: OpenStreetMap | Satellite (Esri) | CartoDB Positron | CartoDB Dark Matter
- Folium `Draw` plugin: polygon + rectangle engedélyezve
- Rajzolt geometria: `st_folium()` → `last_active_drawing` → `session_state["np_drawn_geom"]`
- Törlés: `np_just_cleared` flag + `np_map_version` counter (kettős védelem a ghost drawing ellen)
- Parcella mentéskor: geometria a rajzolt GeoJSON `Polygon`-ból kerül a `create_parcel()`-be

**Parcella részlet nézet:**
- Egyetlen expander: `📋 {parcel_name} — Adatok & Művelési napló`
- 3 tab: `📊 Parcella adatok` | `📅 Művelési napló` | `🖼️ Képek`
- A művelési napló összesítők (összes művelet, költség, utolsó dátum) a tab tetején jelennek meg
- A `selected_parcel['parcel_id']`-t minden napló-lekérés explicit átadja

### 6_Map.py — Rétegkezelés

**Egyedi parcella toggle:**
- "🌾 Parcellák kiválasztása" expander — minden parcellához checkbox
- "Mind be / Mind ki" gyorsgombok (`st.session_state[f"parcel_sel_{parcel_id}"]`)
- Csak a kijelölt parcellák kerülnek `folium.FeatureGroup`-ba és a térképre

**Réteg sorrend:**
- Csak aktív raszteres rétegek (GeoTIFF + Heatmap) esetén jelenik meg
- `session_state["map_raster_layer_order"]` tárolja a sorrendet
- ▲/▼ gombokra: lista swap + `st.rerun()`
- A térkép az `add_to(m)` sorrendet a tárolt lista alapján határozza meg

**LayerControl:**
- `folium.LayerControl(collapsed=False, position='topright')` a térkép jobb felső sarkában
- Minden GeoTIFF: `ImageOverlay` `control=True, overlay=True, name="📷 {stem}"`
- Minden Heatmap: `ImageOverlay` `control=True, overlay=True, name="🌡️ {name}"`
- Minden Parcella: `FeatureGroup` `control=True, name="🌾 {name}"`
- Detekciók: egyetlen `FeatureGroup` `name="🔍 Detekciók"`

**Térkép renderelés:** `m._repr_html_()` + `streamlit.components.v1.html()` — Leaflet JS fut az iframe-ben, LayerControl interaktív marad.

---

## Adatmodell

### ParcelManager – kiterjesztett parcella struktúra

```python
# data/parcels.json – egy parcella eleme
{
    "parcel_id": "uuid4",
    "user_id": "uuid4",
    "name": "Északi tábla",
    "crop_type": "Kukorica",
    "geometry": {"type": "Polygon", "coordinates": [...]},
    "area_m2": 45000.0,
    "area_hectares": 4.5,
    "center_point": [47.1234, 19.5678],
    "notes": "",
    "created_at": "2026-03-07T10:00:00",
    "updated_at": "2026-03-07T10:00:00",
    "images": [                              # ÚJ MEZŐ
        {
            "image_id": "uuid4",
            "file_path": "data/uploads/20260307_100000_field.tif",
            "image_type": "drone",           # "drone" | "satellite"
            "date_acquired": "2026-03-07",
            "band_count": 4,
            "is_georeferenced": True,
            "notes": "Tavaszi felvétel",
            "attached_at": "2026-03-07T10:05:00"
        }
    ]
}
```

### SpectralIndices – Band mapping konstansok

```python
BAND_MAPS = {
    "rgb":       {"red": 1, "green": 2, "blue": 3},
    "rgb_nir":   {"red": 1, "green": 2, "blue": 3, "nir": 4},
    "micasense": {"blue": 1, "green": 2, "red": 3, "nir": 4, "rededge": 5},
    "sentinel2": {"blue": 2, "green": 3, "red": 4, "rededge": 5,
                  "nir": 8, "swir1": 11, "swir2": 12},
}

INDEX_REQUIREMENTS = {
    "NDVI":    ["red", "nir"],
    "NDRE":    ["red", "rededge"],
    "EVI":     ["red", "nir", "blue"],
    "SAVI":    ["red", "nir"],
    "GNDVI":   ["green", "nir"],
    "VARI":    ["red", "green", "blue"],
    "ExG":     ["red", "green", "blue"],
    "MSAVI2":  ["red", "nir"],
    "CIgreen": ["green", "nir"],
    "NDMI":    ["nir", "swir1"],
}
```

> **v0.6.0 bővítés:** 6 új index (GNDVI, VARI, ExG, MSAVI2, CIgreen, NDMI) + `rgb` layout.
> Részletes specifikáció: [BOVITETT_SPEKTRALIS_INDEXEK.md](BOVITETT_SPEKTRALIS_INDEXEK.md)

---

## utils/parcel_manager.py – Módosítások

### _load_parcels() visszafelé kompatibilitás

```python
def _load_parcels(self) -> List[Dict]:
    # ... meglévő kód ...
    parcels = json.load(f)
    # Visszafelé kompatibilitás: images mező hozzáadása
    for parcel in parcels:
        if 'images' not in parcel:
            parcel['images'] = []
    return parcels
```

### Új metódusok (a class végéhez)

```python
def attach_image_to_parcel(
    self, parcel_id: str, file_path: str,
    image_type: str, date_acquired: str, notes: str = ""
) -> Optional[Dict]:
    parcel = self.get_parcel(parcel_id)
    if not parcel:
        return None

    # Band info kinyerése
    band_count = 0
    is_georef = False
    try:
        import rasterio
        with rasterio.open(file_path) as src:
            band_count = src.count
            is_georef = src.crs is not None
    except:
        pass

    image_record = {
        "image_id": str(uuid.uuid4()),
        "file_path": file_path,
        "image_type": image_type,
        "date_acquired": date_acquired,
        "band_count": band_count,
        "is_georeferenced": is_georef,
        "notes": notes,
        "attached_at": datetime.now().isoformat()
    }

    parcel['images'].append(image_record)
    parcel['updated_at'] = datetime.now().isoformat()
    self._save_parcels()
    return image_record

def get_images_for_parcel(self, parcel_id: str) -> List[Dict]:
    parcel = self.get_parcel(parcel_id)
    if not parcel:
        return []
    return parcel.get('images', [])

def detach_image_from_parcel(self, parcel_id: str, image_id: str) -> bool:
    parcel = self.get_parcel(parcel_id)
    if not parcel:
        return False
    original_len = len(parcel.get('images', []))
    parcel['images'] = [img for img in parcel.get('images', [])
                        if img['image_id'] != image_id]
    if len(parcel['images']) < original_len:
        parcel['updated_at'] = datetime.now().isoformat()
        self._save_parcels()
        return True
    return False
```

---

## utils/geotiff.py – Új metódusok (GeoTIFFHandler class-ba)

```python
def get_band_count(self) -> int:
    return self.metadata['count']

def get_band(self, band_number: int) -> np.ndarray:
    if band_number < 1 or band_number > self.metadata['count']:
        raise ValueError(f"Band {band_number} nem létezik (1-{self.metadata['count']})")
    return self.dataset.read(band_number).astype(np.float32)

def get_all_bands(self) -> Dict[int, np.ndarray]:
    return {i: self.dataset.read(i).astype(np.float32)
            for i in range(1, self.metadata['count'] + 1)}

def detect_band_layout(self) -> str:
    count = self.metadata['count']
    layout_map = {1: "grayscale", 3: "rgb", 4: "rgb_nir",
                  5: "micasense", 13: "sentinel2"}
    return layout_map.get(count, f"unknown_{count}")
```

---

## utils/spectral_indices.py – Teljes specifikáció

Lásd: [FEATURE_PLAN_SATELLITE_DRONE_PARCEL_ANALYSIS.md](../../FEATURE_PLAN_SATELLITE_DRONE_PARCEL_ANALYSIS.md) – Feladat 3.

Fő osztály: `SpectralIndices` (statikus metódusok)
- `compute_ndvi(red, nir)` → float32 [-1, 1]
- `compute_ndre(red, rededge)` → float32 [-1, 1]
- `compute_evi(red, nir, blue, G, C1, C2, L)` → float32 clip[-1, 1]
- `compute_savi(red, nir, L)` → float32 clip[-1, 1]
- `colorize_index(arr, colormap, vmin, vmax)` → PIL Image RGBA
- `compute_statistics(arr, mask)` → Dict (mean, std, percentilisek, kategóriák)
- `compute_index_from_bands(index_name, bands, band_layout)` → (array, error_str)

---

## pages/8_Parcel_Analysis.py – Állapot és flow

```
Oldal betölt
    │
    ▼
User auth ellenőrzés (azonos pattern mint 6_Map.py:142-156)
    │
    ▼
parcel_manager.get_parcels_for_user(user_id)
    │ nincs parcella → st.info + st.page_link(7_Parcels) + stop
    │ van parcella
    ▼
Sidebar selectbox → selected_parcel
    │
    ▼
tab1, tab2, tab3, tab4 = st.tabs([...])

[tab1] → get_images_for_parcel + feltöltés form
[tab2] → filter multi-band → index számítás + vizualizáció
[tab3] → Folium map + overlay
[tab4] → NDVI idősor (on-demand)
```

---

## pages/7_Parcels.py – Módosítás pozíciója

```python
# Az oldal LEGVÉGÉN, közvetlenül a footer előtt (sor ~495 körül)
st.markdown("---")
st.subheader("📷 Hozzárendelt képek")
# ... get_images_for_parcel + preview + page_link ...
st.markdown("---")
st.markdown("**v0.3.0** | © 2025-2026 DrónTerápia")
```

---

## pages/6_Map.py – Javítás pozíciója

Sor 394-422 (detection loop) módosítandó:
- Véletlenszerű `lat = 47.0 + random.random() * 0.5` eltávolítása
- Koordináta lekérés: `GeoTIFFHandler.get_bounds_wgs84()` az `input_filename` alapján
- Ha nem találja a georeferált fájlt: `continue` (kihagyás, nem mutat semmit)

---

## requirements.txt – Módosítás

```
# Előtte:
# folium==0.15.0

# Utána:
folium>=0.15.0
matplotlib>=3.7.0
```

---

## Dependency fa

```
pages/8_Parcel_Analysis.py
    ├── utils/parcel_manager.py (images mezők)
    ├── utils/geotiff.py (multi-band + detect_band_layout)
    ├── utils/spectral_indices.py
    ├── utils/image_manager.py (feltöltés)
    └── utils/satellite_downloader.py (ÚJ – CDSE Tab 5)

utils/spectral_indices.py
    ├── numpy (már megvan)
    ├── PIL/Pillow (már megvan)
    └── matplotlib

utils/satellite_downloader.py (ÚJ)
    ├── requests (külső HTTP kliens)
    ├── rasterio (JP2 olvasás + stacking)
    ├── pyproj (CRS transzformáció)
    └── numpy
```

---

## utils/satellite_downloader.py – Architektúra (v2.0.0, implementálandó)

### CDSEClient osztály – metódusok

```python
class CDSEClient:
    # Állapot
    self.username: str
    self.password: str
    self._token: Optional[str]   # OAuth2 bearer token

    # Hitelesítés
    authenticate(self) -> str
        # POST CDSE_TOKEN_URL, grant_type=password
        # → self._token beállítva, token visszaadva

    _auth_header(self) -> Dict[str, str]
        # Visszaad: {"Authorization": "Bearer {token}"}
        # Ha nincs token: authenticate() hívás

    # Keresés
    search(
        self,
        bbox: Tuple[float, float, float, float],   # (W, S, E, N) WGS84
        date_from: str,    # "YYYY-MM-DD"
        date_to: str,      # "YYYY-MM-DD"
        max_cloud_cover: int = 20,
        max_results: int = 10
    ) -> List[Dict]
        # GET CDSE_SEARCH_URL?$filter=...&$orderby=ContentDate/Start desc
        # OData filter: Collection SENTINEL-2 + Intersects + MSIL2A + dátum + felhő
        # → nyers CDSE Product objektumok listája

    # Letöltés
    download_product(
        self,
        product_id: str,
        temp_dir: Path,
        progress_callback: Optional[Callable] = None
    ) -> Path
        # GET CDSE_DOWNLOAD_URL({product_id})/$value, stream=True
        # chunk_size=1MB, 401 esetén: re-authenticate + retry
        # progress_callback(bytes_downloaded, total_bytes)
        # → letöltött zip fájl Path

    extract_safe(self, zip_path: Path, output_dir: Path) -> Path
        # zipfile.ZipFile.extractall()
        # zip_path.unlink() – zip törlése
        # → .SAFE könyvtár Path

    stack_bands(
        self,
        safe_path: Path,
        parcel_bbox_wgs84: Tuple[float, float, float, float],
        output_path: str
    ) -> str
        # 1. GRANULE/*/IMG_DATA/ keresése
        # 2. B02 (R10m) referencia megnyitása
        # 3. Parcella bbox konvertálása natív CRS-be + 500m buffer
        # 4. 12 sáv beolvasása SENTINEL2_BAND_SPECS sorrendben:
        #    - rasterio.open(jp2) + window + resample to 10m
        # 5. np.zeros((12, h, w), float32) töltése
        # 6. Kiírás: 12-band GeoTIFF, LZW compress
        # → output_path (str)

    _find_band_file(img_data: Path, band_name: str, resolution: str) -> Path
        # Keresi: img_data/{resolution}/*_{band_name}_*.jp2
        # Fallback: img_data/**/*_{band_name}_*.jp2
        # → első találat Path, FileNotFoundError ha nincs
```

### Sentinel-2 sáv stack sorrendje (SENTINEL2_BAND_SPECS konstans)

```
Pozíció | Sentinel-2 sáv | Felbontás | Felhasználás (BAND_MAPS)
---------|----------------|-----------|-------------------------
1        | B01            | R60m      | – (Coastal aerosol)
2        | B02            | R10m      | blue ← BAND_MAPS["sentinel2"]["blue"]
3        | B03            | R10m      | green
4        | B04            | R10m      | red
5        | B05            | R20m      | rededge
6        | B06            | R20m      | –
7        | B07            | R20m      | –
8        | B08            | R10m      | nir
9        | B8A            | R20m      | –
10       | B09            | R60m      | –
11       | B11            | R20m      | swir1
12       | B12            | R20m      | swir2
         B10 (Cirrus)     KIHAGYVA   – B10 kihagyásával pos 11=B11 alignál
```

### pages/8_Parcel_Analysis.py – Tab 5 flow

```
Tab 5 betölt
    │
    ▼
CDSE_AVAILABLE? NEM → warning + stop
    │
    ▼
Credentials: .env-ből CDSE_USER/CDSE_PASSWORD?
    IGEN → collapsed expander, sikeres üzenet
    NEM  → expanded form (text_input + password)
    │
    ▼
Keresési paraméterek: date_from, date_to, max_cloud (slider)
    │
    ▼
[Keresés inditasa] gomb:
    CDSEClient.authenticate() → search() → session_state["s2_search_results"]
    │
    ▼
Eredmény lista: container soronként (tile, dátum, felhő%, méret MB, [Letöltés] gomb)
    │
    ▼
[Letöltés] gomb: session_state["s2_selected_product"] = raw_product
    │
    ▼
Megerősítő gomb megjelenik mérettel + figyelmeztetéssel
    │
    ▼
[Letöltés és csatolas] gomb:
    1. st.progress indítása
    2. download_product() → zip letöltés (progress_callback → progress bar)
    3. progress 70% → extract_safe()
    4. progress 80% → stack_bands()
    5. progress 95% → shutil.rmtree(temp_dir)
    6. attach_image_to_parcel()
    7. progress 100% → st.success + st.rerun()
    Hiba esetén: shutil.rmtree(temp_dir, ignore_errors=True) + st.error()
```

---

## Implementáció utáni ellenőrzési megjegyzések (2026-03-07)

### Spec-től való eltérések

| Pont | Leírás | Hatás |
|------|--------|-------|
| EVI normalizálás | Codex per-sáv max-ot használt; javítva global_max-ra (közös skála) | Helyes fizikai értékek |
| st.line_chart params | `x_label`/`y_label` eltávolítva (Streamlit 1.28 compat) | Kompatibilitás megőrzve |
| Docstring encoding | Garbled UTF-8 karakterek a docstring-ekben | Kozmetikai, nem funkcionális |
| Tab emoji | ASCII felirat emoji nélkül (stab. okokból) | Vizuális, nem funkcionális |
| `st.page_link()` | Streamlit 1.28.0 venv nem támogatja (1.36.0+ kell); mindkét helyen `st.info()`-ra cserélve | Kompatibilitás megőrzve |

### Végleges implementált EVI normalizálás

```python
# utils/spectral_indices.py – compute_evi()
global_max = max(float(np.max(red)), float(np.max(nir)), float(np.max(blue)))
if global_max > 1.0:
    red /= global_max
    nir /= global_max
    blue /= global_max
```
**Indoklás:** Sentinel-2-nél a sávértékek 0-10000 skálán vannak. Ha minden sávot saját maximumával normalizálnánk, az eltérő skálák torzítanák az EVI-t. A common global_max garantálja a sávok egységes skálázását.

---

*L4 tranzakciós kezelés: [M06_L4_TRANZAKCIOS_ES_PARHUZAMOS.md](M06_L4_TRANZAKCIOS_ES_PARHUZAMOS.md)*
