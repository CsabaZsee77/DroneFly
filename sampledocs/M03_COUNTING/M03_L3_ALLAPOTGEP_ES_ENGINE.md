# L3 – Állapotgép és Engine – Counting (Tőszámlálás / Inference)

**Modul:** M03
**Szint:** L3 – Állapotgép és Engine
**Forrásdokumentumok:** GSD_ADAPTIVE_SLIDING_WINDOW.md, DETECTION_RESULTS_MISSING_DIAGNOSIS.md, DEEPNESS_ISSUES_AND_SOLUTIONS.md

---

## Forrásfájlok

| Fájl | Szerepkör |
|------|-----------|
| `pages/3_Counting.py` | Streamlit UI – teljes counting felület |
| `core/predictor.py` | YOLO ONNX inference engine |
| `core/model_manager.py` | ONNX modell betöltés és cache |
| `core/sliding_window_detector.py` | Nagy kép ablakos feldolgozása |
| `utils/image_preprocessing.py` | Preprocessing presetek |
| `utils/geotiff.py` | GeoTIFF metaadatok, koordináta transzformáció |
| `utils/export.py` | CSV, JSON export |
| `utils/detection_results_manager.py` | Eredmény cache és perzisztencia |
| `utils/pdf_generator.py` | PDF riport generálás |
| `utils/counting_calculator.py` | Statisztikák számítása |
| `utils/image_manager.py` | Kép feltöltés és galéria kezelés |
| `utils/credit_manager.py` | Kredit egyenleg |
| `utils/model_metadata.py` | Modell registry olvasás |
| `utils/prescription_map.py` | Prescription map: raszteres, KDE, cluster + kelési arány |
| `utils/media_manager.py` | Médiatár kezelés — heatmap mentés `save_generated_image()` |
| `utils/parcel_manager.py` | Parcella adatmodell + vetési paraméterek |
| `utils/row_detector.py` | Növényi sor-detekció PCA/SVD + sortáv-validáció |
| `utils/detection_presets.py` | Detekciós paraméter presetek CRUD (felhasználónként) |
| `utils/annotation_qa.py` | Annotáció minőség-ellenőrzés (pre-training QA engine) |

---

## Adatmodell

### Detection eredmény struktúra

```python
@dataclass
class Detection:
    class_id: int           # 0, 1, 2, ...
    class_name: str         # "kukorica_to"
    confidence: float       # 0.0 - 1.0
    x_min: float            # Pixel koordináta
    y_min: float
    x_max: float
    y_max: float
    # Számított mezők:
    width: float            # x_max - x_min
    height: float           # y_max - y_min
    # Szegmentációs mező (csak szegmentációs modell esetén):
    # mask: np.ndarray       # Bináris maszk, shape (orig_h, orig_w), dtype uint8

@dataclass
class DetectionResult:
    image_path: str
    image_width: int
    image_height: int
    model_id: str
    model_name: str
    model_input_size: int
    preprocessing_preset: str
    confidence_threshold: float
    iou_threshold: float
    detections: List[Detection]
    detection_time_ms: float
    timestamp: str          # ISO 8601
```

### DetectionResultsManager cache struktúra

**Elérési út:** `data/detection_results/{uuid}/`

```
data/detection_results/
└── {result_uuid}/
    ├── metadata.json       # DetectionResult metaadatok
    ├── input_image.tif     # Eredeti kép másolata
    └── annotated.png       # Vizualizált eredmény
```

---

## Állapotgép

### Counting oldal állapotok

```
[Betöltve - Várakozás]
    Nincs kép VAGY nincs modell
    │
    ├── Kép betöltve → [Kép kész]
    ├── Modell kiválasztva → [Modell kész]
    └── Mindkettő → [Futtatásra kész]
                        │
                        │ "▶️ Predikció futtatása" gomb
                        ▼
                   [Predikció folyamatban]
                    Spinner + "Modell betöltése..." / "Predikció futtatása..."
                        │
                   ─────┴──────
                   │           │
              [Sikeres]   [Sikertelen]
                   │           │
              Eredmények    Hibaüzenet
              megjelenítve
```

### ONNX modell cache állapotok (model_manager.py)

```python
# Streamlit @st.cache_resource dekorátor
@st.cache_resource
def load_model_manager(_registry_mtime):
    return get_model_manager(models_dir="models")

# Cache invalidálás: ha model_registry.json megváltozott
if registry_mtime != st.session_state.last_registry_mtime:
    load_model_manager.clear()
    st.session_state.last_registry_mtime = registry_mtime
```

---

## YOLOPredictor engine (core/predictor.py)

### ONNX modell típus felismerés

```python
# Inicializáláskor automatikus felismerés:
self.is_segmentation = (
    len(output_names) >= 2 and
    len(session.get_outputs()[1].shape) == 4
)
# Detection ONNX: 1 output, shape (1, 4+nc, 8400)
# Segmentation ONNX: 2 output:
#   output[0]: (1, 4+nc+32, 8400) — box + class scores + maszk együtthatók
#   output[1]: (1, 32, 160, 160)  — prototípus maszkok
```

### ONNX inference folyamata

```python
class YOLOPredictor:
    def __init__(self, session, conf_threshold=0.25, iou_threshold=0.45):
        self.session = session
        self.is_segmentation = ...  # Automatikus felismerés

    def predict(self, image: Image.Image) -> Dict:
        # 1. Előfeldolgozás (letterbox resize, normalizálás)
        input_tensor, original_size, ratio_pad = self.preprocess_image(image)

        # 2. ONNX inference
        outputs = self.session.run(output_names, {input_name: input_tensor})

        # 3. Utófeldolgozás (model típustól függő ág)
        if self.is_segmentation:
            detections, masks = self._process_segmentation_output(outputs, ...)
        else:
            detections = self._process_detections(outputs[0], ...)
            masks = []

        return {
            'detections': detections,       # List[Dict] — bbox + confidence + class
            'count': len(detections),
            'image_size': original_size,
            'model_input_size': (...),
            'masks': masks,                 # List[np.ndarray] — bináris maszkok
            'has_masks': len(masks) > 0     # bool — szegmentációs modell jelzése
        }
```

### Szegmentációs maszk számítás (_compute_mask)

```python
def _compute_mask(coeffs, protos, box_letterbox, box_original, original_size, ratio_pad):
    """
    Egylépéses maszk pipeline:
    1. mask = sigmoid(coeffs @ protos.reshape(32, 160*160)).reshape(160, 160)
    2. Upscale: 160x160 → 640x640 (input_size, bilinear)
    3. Letterbox padding eltávolítása: crop a valódi kép területére
    4. Resize: → eredeti kép mérete
    5. Bináris küszöbölés (0.5)
    6. Clip a bounding box területére
    Returns: np.ndarray (orig_h, orig_w), dtype=uint8
    """

def draw_masks(image, detections, masks, alpha=0.45) -> Image.Image:
    """
    Szegmentációs vizualizáció:
    - Maszk overlay: félátlátszó szín a maszk területén (alpha blending)
    - Bounding box + label: opencv rectangle + szöveg
    Returns: PIL Image (RGB)
    """
```

---

## SlidingWindowDetector engine (core/sliding_window_detector.py)

### Főbb paraméterek

```python
class SlidingWindowDetector:
    def __init__(
        self,
        predictor: YOLOPredictor,
        window_size: int = None,  # None = modell input méret
        overlap: float = 0.25     # 25% átfedés
    ):
        ...

    def detect(
        self,
        image: Image.Image,
        progress_callback: Optional[Callable] = None,
        abort_event=None,
        gsd_cm_per_pixel: Optional[float] = None,
        bio_nms_min_distance_cm: Optional[float] = None,
        adaptive_confidence: bool = False,
        conf_adj_range: Tuple[float, float] = (0.7, 1.3),
        tta_transforms: Optional[List[str]] = None   # ✅ 2026-03-10
    ) -> Dict:
        """
        Nagy kép ablakos feldolgozása.
        Minden ablak → inference [+ TTA] → koordináta visszavetítés → NMS merge
        → Biológiai NMS (opcionális) → raw_confidences gyűjtés

        Returns Dict kulcsai:
            detections, count, image_size, model_input_size,
            num_windows, raw_detection_count, window_stats,
            aborted, bio_nms_filtered, raw_confidences, tta_active
        """
```

### detect() visszatérési dict

```python
{
    'detections':         List[Dict],   # Végső detekciók (NMS + bio NMS után)
    'count':              int,          # len(detections)
    'image_size':         Tuple[int,int],
    'model_input_size':   Tuple[int,int],
    'num_windows':        int,          # Tile-ok száma
    'raw_detection_count': int,         # Nyers detekciók (NMS előtt, TTA-val együtt)
    'window_stats':       List[Dict],   # Per-tile statisztika
    'aborted':            bool,
    'bio_nms_filtered':   int,          # Biológiai NMS által szűrt count
    'raw_confidences':    List[float],  # Nyers confidence értékek (> 0.01, NMS előtt)
                                        # → Confidence distribution analysis-hoz
    'tta_active':         bool,         # ✅ 2026-03-10 — TTA ténylegesen futott-e
}
```

### Koordináta visszavetítés

```python
# Ablak (window) detekciói → eredeti képre vetítve
# offset x, y = ablak bal felső sarka az eredeti képen
bbox_global = [
    bbox_window[0] + x,   # x1
    bbox_window[1] + y,   # y1
    bbox_window[2] + x,   # x2
    bbox_window[3] + y    # y2
]
```

### TTA — `_apply_tta()` API

**Státusz:** ✅ IMPLEMENTÁLVA (2026-03-10)

```python
def _apply_tta(
    self,
    tile_img: Image.Image,
    tta_transforms: List[str],   # pl. ['flip_h', 'flip_v', 'rot90']
    tile_w: int,                 # eredeti tile szélesség
    tile_h: int                  # eredeti tile magasság
) -> List[Dict]:
    """
    Minden transzformációra:
      1. aug_img = transform(tile_img)
      2. aug_dets = predictor.predict(aug_img)
      3. Minden aug_det bbox-ra: inv_bbox = inverse_transform(bbox, tile_w, tile_h)
      4. Visszatér az inv_bbox-os detekciók listájával
    """
```

| Transzformáció | PIL | Bbox inverz képlet |
|----------------|-----|--------------------|
| `flip_h` | `ImageOps.mirror(tile)` | `[tile_w - x2, y1, tile_w - x1, y2]` |
| `flip_v` | `ImageOps.flip(tile)` | `[x1, tile_h - y2, x2, tile_h - y1]` |
| `rot90` | `tile.rotate(90, expand=True)` <br> (CCW, forgatott méret: tile_h × tile_w) | `[tile_w - y2_r, x1_r, tile_w - y1_r, x2_r]` |

> **Megjegyzés:** A TTA által keletkező duplikátumokat a globális IoU NMS szűri ki.
> Szegmentációs modellel nem érhető el (`predictor.is_segmentation == True` → `tta_active = False`).

### TTA UI integráció — `pages/3_Counting.py`

**Sidebar** (`🔲 Sliding Window` szekció, `Egyedi ablak méret` alatt):
- `TTA (Test-Time Augmentation)` checkbox (alapért. ki; csak ha `use_sliding_window == True`)
- Ha aktív:
  - `TTA transzformációk` multiselect — alapért. `['flip_h', 'flip_v']`
  - Lehetséges értékek: `Vízszintes tükrözés (flip_h)`, `Függőleges tükrözés (flip_v)`, `90°-os forgatás (rot90 CCW)`
  - Ha nincs kiválasztva semmi: `tta_transforms = None` (TTA nem fut)

**Sliding Window statisztika blokkban:**
- Ha `result['tta_active'] == True`: `✅ TTA aktív volt (Vízszintes tükrözés, Függőleges tükrözés)` sor megjelenik

**Paraméter flow:**
```
sidebar tta_selected → params['tta_transforms'] → det.detect(tta_transforms=...) → result['tta_active']
```

---

## GeoTIFF kezelés (utils/geotiff.py)

### Főbb függvények

```python
def is_geotiff(file_path: str) -> bool:
    """Ellenőrzi, hogy van-e georeferencia a TIFF-ben"""

def extract_gsd(file_path: str) -> float:
    """GSD (cm/pixel) kinyerése a GeoTIFF metaadatokból"""

class GeoTIFFHandler:
    def get_crs(self) -> str:
        """Koordináta-referencia rendszer (pl. EPSG:4326)"""

    def get_bounds(self) -> Tuple[float, float, float, float]:
        """Térképi határok (lon_min, lat_min, lon_max, lat_max)"""

    def get_transform(self):
        """Rasterio affine transform (pixel → georeferált)"""

    def pixel_to_geo(self, x: float, y: float) -> Tuple[float, float]:
        """Pixel koordináta → GPS koordináta"""
```

---

## CountingCalculator engine (utils/counting_calculator.py)

```python
class CountingCalculator:
    def __init__(self, gsd_cm_per_pixel: float): ...

    def bbox_area_to_cm2(self, bbox: List[float]) -> float:
        """Bounding box terület: area_px = w_px * h_px → area_cm2 = area_px * gsd²"""

    def mask_area_to_cm2(self, mask_array: np.ndarray) -> float:
        """
        Szegmentációs maszk terület: area_px = sum(mask > 0) → area_cm2 = area_px * gsd²
        Pontosabb mint a bbox módszer (nincs "levegő" az objektum körül).
        Args:
            mask_array: 2D bináris numpy array (uint8, shape: orig_h x orig_w)
        """

    def calculate_detection_statistics(self, detections) -> Dict: ...
    def classify_detections(self, detections, class_calibrations) -> Dict: ...
    def calculate_counting_results(self, detections, class_calibrations) -> Dict: ...
```

### Bbox vs maszk területszámítás összehasonlítása

| Módszer | Alapja | Pontosság | Elérhetőség |
|---------|--------|-----------|-------------|
| `bbox_area_to_cm2` | Téglalap (w × h) pixelek | ±20-40% (a "levegőt" is számolja) | Minden modellnél |
| `mask_area_to_cm2` | Tényleges maszk pixelek | ~95%+ (csak az objektum pixelei) | Csak szegmentációs modellnél |

---

## Prescription Map engine (utils/prescription_map.py)

**Státusz:** ✅ BŐVÍTVE (2026-03-27) — KDE + Cluster + Kelési arány

### Osztály API

```python
class PrescriptionMapGenerator:
    def __init__(
        self,
        image_width: int,
        image_height: int,
        detections: List[Dict],
        gsd_cm_per_pixel: Optional[float] = None,
        geo_handler: Optional[GeoTIFFHandler] = None,
        expected_density_per_ha: Optional[float] = None   # ÚJ
    ): ...

    # --- Rács és count (változatlan) ---
    def generate_grid(self, cell_size_m: float = 2.0) -> List[Dict]: ...
    def count_per_cell(self, grid) -> Dict[Tuple[int,int], int]: ...

    # --- Vizualizáció ---
    def generate_heatmap_image(
        self, original_image, grid, counts,
        alpha=0.55,
        cell_size_m: Optional[float] = None    # ÚJ — kelési arány módhoz
    ) -> Image.Image:
        """
        Ha expected_density_per_ha megadva ÉS cell_size_m ismert:
          → Kelési arány mód: piros(0%) → sárga(50%) → zöld(80%+)
          → Colorbar: "0%" (alul) ... "100%+" (felül)
        Különben:
          → Relatív mód: zöld(low) → piros(high), max count normalizált
        """

    def generate_kde_heatmap_image(                         # ÚJ
        self, original_image, bandwidth_m=3.0, alpha=0.65
    ) -> Image.Image:
        """
        Gaussian KDE: minden detekciónál σ = bandwidth_m / GSD Gaussian kernel.
        Numpy-alapú (sklearn nem szükséges).
        """

    def generate_cluster_heatmap_image(                     # ÚJ
        self, original_image, grid, counts,
        n_clusters=4, alpha=0.65
    ) -> Image.Image:
        """
        K-means klaszterezés a nemüres cellák count-jain (k=2/3/4).
        Jelmagyarázat sávban: low/medium-low/medium/high zóna-színek.
        """

    # --- Export (változatlan) ---
    def export_csv(self, grid, counts, cell_size_m) -> str: ...
    def export_geojson(self, grid, counts, cell_size_m) -> Optional[Dict]: ...
    def _classify_zones(self, counts) -> Dict[Tuple[int,int], str]: ...

    @staticmethod
    def _kmeans_cluster(values, n_clusters=4, max_iter=100) -> List[int]:   # ÚJ
        """Pure numpy K-means. Inicializálás: egyenletes percentilek."""
```

### Kelési arány számítás

```
expected_per_cell = expected_density_per_ha × (cell_size_m²) / 10 000
emergence_rate    = detected_count / expected_per_cell      ∈ [0.0, 1.0+]
global_rate       = Σdetected / (expected_per_cell × n_cells)
```

A `cell_size_m` mindig a rácshoz használt érték (UI sliderből).

### Pixel → WGS84 lineáris interpoláció (változatlan)

```python
(sw_lat, sw_lon), (ne_lat, ne_lon) = geo_handler.get_bounds_wgs84()
lon = sw_lon + (px / image_width) * (ne_lon - sw_lon)
lat = ne_lat - (py / image_height) * (ne_lat - sw_lat)
```

### UI integráció — Counting.py struktúra

```
🗺️ Prescription map  [fő expander]
  │
  ├── Cellaméret slider
  ├── 🌱 Kelési arány beállítás  [sub-expander, csak GeoTIFF]
  │     Forrás: Parcellából | Kézi megadás
  │     Mód: Sortáv×Tőtáv | Közvetlen sűrűség
  │     → _expected_density_per_ha
  │
  ├── Statisztika sor: cellák / foglalt / max / kelési arány %
  │
  ├── 📊 Raszteres heatmap    [sub-expander, expanded=True]
  │     PNG + CSV + GeoJSON (utóbbi csak GeoTIFF)
  │
  ├── 🌊 Folyamatos heatmap (KDE)  [sub-expander]
  │     Sávszélesség slider + PNG
  │
  └── 🔵 Cluster zónatérkép  [sub-expander]
        Zónák száma (2/3/4) + PNG
```

### Parcellákon tárolt vetési paraméterek (parcels.json bővítés)

```json
{
  "parcel_id": "...",
  "row_spacing_cm":       75.0,
  "plant_spacing_cm":     20.0,
  "target_density_per_ha": 66667.0
}
```

`target_density_per_ha` auto-számítva ha csak `row_spacing_cm` + `plant_spacing_cm` adott:
```
density = 100 000 000 / (row_spacing_cm × plant_spacing_cm)
```

---

## Row Detector engine (utils/row_detector.py)

**Státusz:** ✅ FRISSÍTVE (2026-03-30) — PCA/SVD + elméleti sortáv-validáció

### Osztály

```python
class RowDetector:
    _SPACING_MIN_FRACTION = 0.65  # elméleti távolság × 0.65 = minimum elfogadott

    @staticmethod
    def detect(
        detections: List[Dict],
        image_width: int,
        image_height: int,
        gsd_cm_per_pixel: Optional[float] = None,
        min_row_points: int = 3,
        hough_threshold: int = 8,   # backward-compat, nem használt
        poly_degree: int = 2,
        max_bend_deg: float = 15.0,
        theoretical_row_spacing_cm: Optional[float] = None,
    ) -> Dict:
        """
        Returns:
            {
              'success': bool,
              'row_count': int,
              'dominant_angle_deg': float,
              'avg_spacing_px': float|None,
              'avg_spacing_cm': float|None,
              'row_assignments': List[int],       # -1 = outlier
              'row_positions': List[float],
              'row_polylines': List[dict|None],
              'row_valid_flags': List[bool],       # True = elsődleges
              'row_section_validity': List[...],   # per-sor szakaszok vagy None
              'outlier_count': int,
              'message': str,
            }
        """

    @staticmethod
    def filter_by_rows(detections, row_result) -> List[Dict]:
        """Outlier detekciók teljes eltávolítása."""

    @staticmethod
    def apply_row_confidence_penalty(
        detections, row_result,
        penalty_factor=0.5, conf_threshold=0.25
    ) -> Tuple[List[Dict], int]:
        """Confidence büntetés outlier detekciókra. Returns: (szűrt_lista, eltávolított_count)"""

    @staticmethod
    def draw_rows(
        image, detections, row_result,
        row_color=(0,200,80), outlier_color=(220,50,50),
        secondary_color=(200,180,50), line_width=2,
        show_lines=True, draw_outlier_boxes=True,
    ) -> Image.Image:
        """
        Sor-vonalak + kiugró bboxok rajzolása.
        - Folytonos zöld: elsődleges (valid) sorok
        - Szaggatott sárga: másodlagos sorok / érvénytelen szakaszok
        - Piros keret: kiugró detekciók (ha draw_outlier_boxes=True)
        - show_lines=False: csak outlier bboxok jelennek meg
        """
```

### Belső segédmetódusok

| Metódus | Leírás |
|---------|--------|
| `_find_dominant_angle_pca()` | SVD alapú szögbecslés |
| `_find_row_peaks()` | Tukey-kerítéses gap-alapú csúcskereső |
| `_fit_row_polylines()` | Per-sor polinom illesztés, bend constraint |
| `_calc_avg_spacing()` | Szomszéd-sorok átlagos távolsága |
| `_validate_row_spacing()` | Elméleti sortáv-validáció (sortáv min ellenőrzés) |
| `_reapply_assignments_for_validity()` | Érvénytelen sor/szakasz → outlier átminősítés |
| `_draw_dashed_pts()` | Szaggatott vonal PIL Image-re pontlista alapján |
| `_draw_sectioned_pts()` | Szakaszos vonalrajzolás (valid=folytonos, invalid=szaggatott) |

### Függőségek

| Könyvtár | Szerep |
|----------|--------|
| `numpy` | SVD, vetítés, polinom illesztés, mintavételezés |
| `PIL (Pillow)` | Overlay rajzolás, szaggatott vonalak, PNG export |

> **Megjegyzés:** `opencv` és `scipy` nem szükséges.

### UI integráció — `_pages/3_🔍_Counting.py`

**Expander: `🌾 Sor-detekció`** (Haladó NMS szekció után):
- `Sor-detekció engedélyezése` checkbox (alapért. ki)
- Ha aktív:
  - `Min. pont/sor` slider (2–10)
  - `Soron kívüli detekciók kezelése` radio (`penalty` / `remove`)
  - Ha `penalty`: `Büntetési szorzó` slider (0.1–0.9)
  - `Sor-illesztés típusa` radio (0=egyenes, 1=lineáris, 2=másodfokú)
  - Ha poly_degree > 0: `Max. sor-kanyar` slider (5–45°)
  - **Elméleti sortáv-validáció szekció:**
    - `Elméleti sortáv figyelése` checkbox (alapért. ki)
    - Ha aktív: `Elméleti sortávolság (cm)` number input (alapért. 75)
  - `Sorvonalak megjelenítése a detekciós képeken` checkbox (alapért. be)

**Pipeline helye:** Sor-detekció a tabak **előtt** fut — `_row_result` és `_row_filtered_dets` változókba mentve.

**Tab integráció:**
- Tab 1 (Egyedi detekciók): `annotated_image` + sor overlay (ha `show_row_lines`)
- Tab 2 (Cluster alapú): `draw_counting_overlay(_row_filtered_dets)` + sor overlay (ha `show_row_lines`)

**Eredmény blokk** (Prescription map szekció után):
- Expander: `🌾 Sor-detekció részletek` — összezárva alapból
- Metrikák: Sorok száma | Szög | Sor-köz (px) | Sor-köz (cm) | Másodlagos sorok
- Önálló overlay kép (outlier bboxokkal együtt)
- `🌾 Sor-overlay PNG letöltés` gomb
- Szűrés összefoglalója (eltávolított / megtartott detekciók)

---

## Confidence Distribution Analysis (pages/3_Counting.py)

**Státusz:** ✅ IMPLEMENTÁLVA (2026-03-10)

### Célja

A sliding window inferencia során minden tile-ból keletkező nyers detekciók confidence
értékeit összegyűjti és vizualizálja. A felhasználó leolvashatja az optimális küszöböt
a bimodális eloszlásból (zaj-klaszter vs. valódi detekciók klasztere).

### Megjelenés feltétele

```
IF use_sliding_window == True
AND len(raw_confidences) >= 10:
    "📊 Confidence eloszlás elemzés" expander jelenik meg
    (a sliding window statisztika blokk UTÁN)
```

### Threshold javaslat logika — `_compute_suggested_threshold()`

```python
def _compute_suggested_threshold(raw_confs, current_threshold, n_bins=30):
    """
    Bimodális eloszlás két csúcsa közötti völgyet keresi.

    1. Hisztogram 30 binnel [0.01, 1.0] tartományon
    2. Simítás: 3-elemű mozgóátlag (numpy convolve, scipy nélkül)
    3. Top-2 elkülönülő csúcs keresése (minimum 3 bin távolságra egymástól)
    4. Ha < 2 csúcs: None (nem bimodális)
    5. A két csúcs közötti minimum → javasolt threshold
    6. Minimum visszatérési érték: 0.05
    """
```

| Eloszlás típusa | Visszatérési érték |
|-----------------|-------------------|
| Üres lista / < 10 elem | `None` |
| Nem bimodális (< 2 csúcs) | `None` |
| Bimodális | `float` — a völgy pozíciója |

### UI elemek

| Elem | Leírás |
|------|--------|
| Plotly chart | Hisztogram, piros vonal = jelenlegi, zöld vonal = javasolt |
| st.bar_chart | Fallback ha plotly nincs telepítve |
| st.info | Javasolt küszöb szöveges magyarázattal (delta is megjelenik) |
| st.caption | Ha nincs javaslat (homogén eloszlás) |
| 4 metrika | Nyers detekciók / Min / Átlag / Max confidence |

---

## Export engine (utils/export.py)

### CSV export formátum

```csv
class_id,class_name,confidence,x_min,y_min,x_max,y_max,width,height
0,kukorica_to,0.923,120.5,340.2,185.3,425.8,64.8,85.6
```

### JSON export formátum

```json
{
  "image": {"filename": "...", "width": 2304, "height": 2304},
  "model": {"name": "...", "id": "...", "input_size": "640x640"},
  "config": {"confidence": 0.25, "iou": 0.45, "preset": "ExGreen"},
  "detections": [{"class_id": 0, "class_name": "kukorica_to", ...}],
  "summary": {"total_detections": 42, "average_confidence": 0.87}
}
```

---

## Streamlit Session State kezelés

| Kulcs | Típus | Tartalma |
|-------|-------|----------|
| `selected_model_id` | str | Kiválasztott modell UUID |
| `current_image` | np.ndarray | Aktuális kép (memóriában) |
| `current_image_path` | str | Kép fájl elérési útja |
| `detection_results` | list | Utolsó detekció eredményei |
| `last_detection_id` | str | Utolsó DetectionResult UUID |
| `preprocessing_preset` | str | Kiválasztott preset neve |
| `image_source` | str | "upload"/"previous"/"sample" |

---

## Teljesítmény szempontok

| Tényező | CPU Idő | Optimalizáció |
|---------|---------|---------------|
| ONNX modell betöltés | ~0.5-2s | Cache (st.cache_resource) |
| Single image inference (640px) | ~0.1-0.5s | — |
| Sliding window (2000px, 25% overlap) | ~5-15s | Overlap csökkentése |
| Sliding window (5000px, 25% overlap) | ~30-60s | Tile méret növelése |
| GeoTIFF betöltés | ~0.5-2s | — |
| GeoJSON export (1000 detekció) | ~0.1s | — |
| PDF generálás | ~1-3s | — |

### Cache invalidálás

A model manager `@st.cache_resource` cache-elt:
```python
# Registry változáskor automatikus invalidálás
if registry_mtime_changed:
    load_model_manager.clear()
```

---

## Detection Presets engine (utils/detection_presets.py)

Felhasználónkénti detekciós paraméter-készletek mentése és betöltése.

### Adatmodell

**Tárolás:** `data/users/{user_id}/detection_presets.json`

```json
{
  "presets": [
    {
      "name": "Kukorica - alacsony conf",
      "description": "Sűrű állomány, bio NMS 15cm",
      "params": {
        "conf_threshold": 0.15,
        "iou_threshold": 0.45,
        "use_sliding_window": true,
        "overlap_ratio": 0.25,
        "custom_window_size": null,
        "use_bio_nms": true,
        "bio_nms_min_distance_cm": 15.0,
        "use_adaptive_conf": false,
        "conf_adj_range": [0.7, 1.3],
        "use_tta": false,
        "tta_transforms": null
      },
      "created_at": "2026-03-23T10:00:00",
      "updated_at": "2026-03-23T10:00:00"
    }
  ]
}
```

### API

| Metódus | Leírás | Visszatérés |
|---------|--------|-------------|
| `__init__(user_id, data_root)` | Manager inicializálás | — |
| `list_presets()` | Összes preset (legújabb elöl) | `List[Dict]` |
| `get_preset(name)` | Preset lekérés név alapján | `Dict` vagy `None` |
| `save_preset(name, params, description)` | Mentés vagy frissítés (upsert) | `bool` |
| `delete_preset(name)` | Preset törlése | `bool` |
| `rename_preset(old_name, new_name)` | Átnevezés (egyediség ellenőrzéssel) | `bool` |

### Preset betöltés flow

```
session_state['_preset_loaded'] = preset_params
  → st.rerun()
  → oldal tetején: _loaded_preset = session_state.pop('_preset_loaded')
  → widget default értékek = preset értékek
```

---

## Batch feldolgozás engine (pages/3_Counting.py)

### Működés

1. Felhasználó feltölt max. 20 képet (`st.file_uploader`, `accept_multiple_files=True`)
2. Kredit ellenőrzés (`get_user_credits` ≥ képek száma)
3. Modell egyszeri betöltés (első kép → cache)
4. Képenként: `save_uploaded_image` → `Image.open` → `StandCountDetector.detect()` → `draw_detections` → `save_detection_result`
5. Kredit levonás képenként (`deduct_credit`)
6. Összesítő DataFrame: fájlnév, tőszám, átlag confidence, státusz

### Hibakezelés

Képenként try-except: ha egy kép hibás, a többi továbbfut. A státusz oszlopban jelenik meg a hiba.

### Teljesítmény

| Paraméter | Hatás |
|-----------|-------|
| Képek száma | Lineáris futásidő |
| Modell betöltés | Egyszer (első kép) |
| Kreditek | Csak sikeres feldolgozásnál vonódik le |

---

## Ground Truth validáció engine (pages/4_Results.py)

### Adatmodell

A `metadata.json`-ba kerül egy opcionális `ground_truth` mező:

```json
{
  "ground_truth": {
    "count": 142,
    "note": "Kézi számlálás 2026.03.20.",
    "recorded_at": "2026-03-20T14:30:00",
    "detection_count_at_validation": 138
  }
}
```

### Számított metrikák

| Metrika | Képlet |
|---------|--------|
| Eltérés (db) | `abs(detected - ground_truth)` |
| Eltérés (%) | `(detected - ground_truth) / ground_truth * 100` |
| Pontosság (%) | `max(0, 100 - abs(eltérés_%))` |

### Persistencia

Közvetlenül a `metadata.json` fájlba írás (`json.load` → módosítás → `json.dump`). Nem megy a `detection_results_manager`-en keresztül (egyszerűbb, nincs index frissítés szükség).

---

## Modell összehasonlítás engine (pages/4_Results.py)

### Session state

| Kulcs | Típus | Leírás |
|-------|-------|--------|
| `compare_result_ids` | `set` | Kiválasztott result ID-k |
| `show_compare_view` | `bool` | Összehasonlító nézet aktív-e |

### Működés

1. Grid nézetben checkbox toggleli a `compare_result_ids` set-et
2. ≥ 2 kiválasztás → "Összehasonlítás" gomb megjelenik
3. Betöltés: `detection_results_manager.get_result_by_id()` minden ID-re
4. Megjelenítés: `st.columns(n)` — egymás melletti annotált képek + metrikák
5. Összehasonlító DataFrame: modell, tőszám, conf/iou küszöb, avg/min/max confidence, SW/BioNMS/TTA állapot

### Teljesítmény

N eredmény betöltése: N × JSON olvasás + N × kép betöltés. Tipikusan 2-3 eredmény → elhanyagolható.

---

## Tőszám trend engine (pages/4_Results.py)

### Működés

1. A `results` lista (már betöltött index adatok) alapján DataFrame készül
2. Szűrés: kép neve alapján (`selectbox` → "Összes" vagy konkrét fájlnév)
3. Grafikon: `st.line_chart` — X: timestamp, Y: tőszám
4. Statisztikák: mérések száma, átlag, min/max, szórás (`pd.DataFrame` aggregáció)

### Teljesítmény

Tiszta DataFrame művelet a már memóriában lévő index adatokon — < 1ms.

---

## Model Rating/Review engine (utils/model_metadata.py)

### Adatmodell

A `model_registry.json`-ban minden modellnél:

```json
{
  "ratings": {
    "average": 4.2,
    "count": 3,
    "distribution": {"5": 1, "4": 1, "3": 1, "2": 0, "1": 0}
  },
  "reviews": [
    {
      "user": "username",
      "rating": 4,
      "comment": "Jól detektál kukoricán...",
      "created_at": "2026-03-23T10:00:00",
      "helpful_votes": 0
    }
  ]
}
```

### API (ModelMetadata)

| Metódus | Leírás |
|---------|--------|
| `add_review(model_id, user, rating, comment)` | Vélemény hozzáadása + rating újraszámítás |
| `_update_rating(model)` | Átlag, count, distribution újraszámítása a reviews-ból |

### Megjelenítés (Counting oldal)

- `render_stars(rating)` → HTML csillagok
- Modell kártyán: valós átlag (ha van review) vagy mAP-alapú becsült rating
- Expander: utolsó 3 vélemény + beküldő form (select_slider + text_input)

---

## GT Paraméter-kereső engine (pages/3_Counting.py)

**Státusz:** ✅ IMPLEMENTÁLVA (2026-03-24)

### Cél

Automatikus optimális konfidencia és IoU küszöb keresés egy kijelölt régió (Ground Truth) alapján.
A felhasználó manuálisan megszámolja a növényeket egy kijelölt területen, majd a rendszer 2D rács-kereséssel
(conf × IoU) megtalálja azt a paraméterkombinációt, amely a legközelebb áll a kézi számoláshoz.

### Üzemmódok

```
Radio: "Teljes terület" (alapértelmezett) | "Paraméter-kereső (GT régió)"
session_state['counting_mode'] → a radio aktuális értéke
```

| Üzemmód | Conf/IoU sliderek | Inference conf | Sweep | Normál eredmények |
|---------|-------------------|----------------|-------|-------------------|
| Teljes terület | Látható, kézi állítás | Felhasználó által megadott | Nem | Igen — teljes pipeline |
| Paraméter-kereső | Rejtett | 0.01 (alacsony, mindent detektál) | Igen — 2D grid | Nem — csak sweep eredmény |

### Régió kijelölés (slider-alapú)

```
4 slider: bal%, felső%, szélesség%, magasság% (0–100 tartomány)
→ session_state['gt_roi'] = (x1, y1, x2, y2)  # pixel koordináták
→ Vizualizáció: narancssárga keret + sötétített külső terület
→ Zoom + letöltés gomb a kijelölt régióra
```

### Sweep tartomány UI

Felhasználó által megadható:
- Conf: min (0.01–0.99), max (0.02–1.00), lépésköz (0.005–0.50)
- IoU: min (0.01–0.99), max (0.02–1.00), lépésköz (0.005–0.50)
- Kombináció-szám előzetes kijelzése + figyelmeztetés >5000 felett

Alapértelmezések: conf 0.05–0.90 / 0.05 lépés, IoU 0.10–0.90 / 0.10 lépés

### Tile-szintű NMS lazítás GT módban

```python
# GT sweep módban a predictor IoU-t 0.99-re állítjuk az inference előtt,
# hogy a tile-szintű NMS szinte semmit ne szűrjön ki.
# Így az all_detections "nyers" marad, és a sweep tetszőleges IoU-val dolgozhat.
if _is_gt_sweep:
    pred.iou_threshold = 0.99   # lazított tile NMS
    # ... inference ...
    pred.iou_threshold = eredeti_iou  # visszaállítás
```

### predict_raw() metódus (core/predictor.py)

```python
def predict_raw(self, image: Image.Image) -> Dict:
    """YOLO inferencia NMS nélküli nyers eredményekkel (nem sliding window)."""
    input_tensor, original_size, ratio_pad = self.preprocess_image(image)
    outputs = self.session.run(self.output_names, {self.input_name: input_tensor})
    # Feldolgozás NMS nélkül → összes detekció
    return {
        'raw_boxes': np.ndarray,       # (N, 4) xyxy
        'raw_confidences': np.ndarray,  # (N,)
        'raw_class_ids': np.ndarray,    # (N,)
        'image_size': tuple,
        'model_input_size': tuple,
    }
```

### 2D Sweep algoritmus

```
INPUT:
  all_detections[]     → tile NMS utáni detekciók (IoU=0.99 lazított)
  conf_range[]         → felhasználó által megadott conf tartomány
  iou_range[]          → felhasználó által megadott IoU tartomány
  gt_expected          → kézi tőszám

FOR iou_thr IN iou_range:
    FOR conf_thr IN conf_range:
        1. Conf szűrés: raw_confs >= conf_thr
        2. Per-class globális NMS: cv2.dnn.NMSBoxes(boxes, confs, conf_thr, iou_thr)
        3. Biológiai NMS (ha aktív): távolság-alapú szűrés (gsd × min_distance_cm)
        4. cnt = végső detekció szám
        5. err = abs(cnt - gt_expected)
        6. if err < best_error: best = (conf_thr, iou_thr, cnt)

OUTPUT:
  best_combo = {conf, iou, count, error}
  sweep_results_2d[] → minden kombináció eredménye
```

### Pipeline konzisztencia

A sweep **ugyanazt** a három lépéses pipeline-t reprodukálja, mint a normál inference:

| Lépés | Normál pipeline | Sweep |
|-------|----------------|-------|
| 1. Globális NMS | `_apply_global_nms()` per-class | `cv2.dnn.NMSBoxes()` per-class |
| 2. Biológiai NMS | `_apply_biological_nms()` per-class, centroid távolság | Azonos logika inline |
| 3. Végső count | `len(merged_detections)` | `len(nms_kept)` vagy `len(bio_filtered)` |

### Vizualizáció

1. **2D heatmap tábla**: conf × IoU pivot, színkódolás eltérés alapján
   - < 5%: zöld | < 10%: világoszöld | < 20%: sárga | < 40%: narancssárga | ≥ 40%: piros
2. **Legjobb kombó metrikák**: GT tőszám, legjobb egyezés, pontosság %, ajánlott conf/IoU
3. **Annotált kép**: a legjobb paraméterekkel detektált boxok a GT régión
4. **Preset mentés gomb**: az ajánlott beállítások egyetlen kattintással presetbe menthetők

### GT mód eredmény-megjelenítés

GT módban a normál eredmény-szekciók (finomhangolás, heatmap, export, statisztikák) **nem jelennek meg**.
Ehelyett:
```
✅ Következő lépés
  1. Mentsd el presetként
  2. Válts "Teljes terület" módra
  3. Töltsd be a presetet és futtasd a teljes képen
```
