# L2 – Döntési Logika – Counting (Tőszámlálás / Inference)

**Modul:** M03
**Szint:** L2 – Döntési Logika
**Forrásdokumentumok:** GSD_ADAPTIVE_SLIDING_WINDOW.md, FEATURE_PLAN_IMAGE_PREPROCESSING_PRESETS.md, ONNX_CLASS_MAPPING.md

---

## Validációs szabályok

### Predikció indítása előtt

| Feltétel | Érvényes | Érvénytelen | Kezelés |
|----------|----------|-------------|---------|
| Kép betöltve | Van kép memóriában | Nincs | Gomb disabled |
| Modell kiválasztva | Van kiválasztott modell | Nincs | Gomb disabled |
| GeoTIFF követelmény | GeoTIFF kép VAGY require_geotiff=False | Nem GeoTIFF + require_geotiff=True | Hibaüzenet, megállás |
| Kredit egyenleg | ≥ szükséges kreditek (hektáronként 1) | Kevés kredit | InsufficientCreditsError |
| Konfidencia küszöb | 0.0-1.0 | Tartományon kívül | Slider korlátoz |
| IoU küszöb | 0.0-1.0 | Tartományon kívül | Slider korlátoz |

### Képformátum validáció

| Formátum | Elfogadott | GeoJSON export | Megjegyzés |
|----------|-----------|----------------|------------|
| JPG/JPEG | Igen | Nem | Veszteséges, de rendszer kezeli |
| PNG | Igen | Nem | Veszteségmentes |
| TIFF (nem geo) | Igen | Nem | Georeferencia nélkül |
| GeoTIFF | Igen | Igen | CRS-t ment, WGS84 export |

---

## Döntési logika – Preprocessing Presetek

### Preset algoritmusa (utils/image_preprocessing.py)

```python
def apply_preset(image: np.ndarray, preset: str) -> np.ndarray:
    R, G, B = image[:,:,0], image[:,:,1], image[:,:,2]

    IF preset == "ExGreen":
        index = 2*G - R - B
        RETURN normalize_to_uint8(index)

    ELIF preset == "ExRed":
        index = 2*R - G - B
        RETURN normalize_to_uint8(index)

    ELIF preset == "ExBlue":
        index = 2*B - R - G
        RETURN normalize_to_uint8(index)

    ELIF preset == "VARI":
        index = (G - R) / (G + R - B + epsilon)
        RETURN normalize_to_uint8(index)

    ELIF preset == "NGRDI":
        index = (G - R) / (G + R + epsilon)
        RETURN normalize_to_uint8(index)

    ELIF preset == "GLI":
        index = (2*G - R - B) / (2*G + R + B + epsilon)
        RETURN normalize_to_uint8(index)

    RETURN image  # None preset: eredeti kép
```

### Preset választási útmutató

| Növény típus | Talaj | Ajánlott preset |
|--------------|-------|-----------------|
| Kukorica (korai stádium) | Barna / száraz | ExGreen |
| Kukorica (fejlett) | Zöld gyom háttér | VARI |
| Napraforgó | Általános | ExGreen vagy NGRDI |
| Összefüggő növényzet | Vegyes | GLI |
| Nincs különleges igény | — | None |

---

## Döntési logika – Sliding Window

### Mikor érdemes sliding window-t használni?

```
IF kép_szélesség > modell_input_méret * 2:
    AJÁNLOTT sliding window
ELIF kép_szélesség > 2048:
    ERŐSEN AJÁNLOTT sliding window
ELSE:
    Direkt inference elég
```

### Sliding window algoritmus (core/sliding_window_detector.py)

```
INPUT: nagy kép, window_size (= modell input méret), overlap_ratio

stride = window_size * (1 - overlap_ratio)

FOR y IN range(0, kép_magasság, stride):
    FOR x IN range(0, kép_szélesség, stride):
        window = kép[y:y+window_size, x:x+window_size]
        IF window mérete != window_size:
            window = pad(window)  # Széleken padding

        detections = model.predict(window)

        # Koordináták visszavetítése az eredeti képre
        FOR each detection IN detections:
            detection.x_min += x
            detection.y_min += y
            detection.x_max += x
            detection.y_max += y

global_detections = merge(all_window_detections)
final_detections = apply_nms(global_detections, iou_threshold)
```

### GSD (Ground Sampling Distance) alapú adaptív sliding window

A `GSD_ADAPTIVE_SLIDING_WINDOW.md` dokumentálja:
- GSD = terep egy pixelnek megfelelő valós mérete (cm/px)
- Ha GSD ismert (GeoTIFF-ből kinyerhető), a csempe mérete adaptálható:
  ```
  optimal_window_px = növény_átlagos_méret_cm / GSD_cm_px
  ```
- Pl. kukorica ~50cm átmérő, GSD = 2cm/px → 25px/növény
  - Modell: 640px, szükséges növény/ablak: 20 → ablak rendben
- Ez a funkció tervezett/részleges implementáció

### TTA (Test-Time Augmentation) — tile szintű augmentáció

**Státusz:** ✅ IMPLEMENTÁLVA (2026-03-10) — `core/sliding_window_detector.py` (`_apply_tta`)

```
IF tta_transforms is not None AND len(tta_transforms) > 0:
    IF predictor.is_segmentation:
        → TTA kihagyva (szegmentációs maszkokat nem lehet visszatranszformálni)
    ELSE:
        tta_active = True

FOR minden tile:
    dets = predictor.predict(tile)           # eredeti
    FOR transform IN tta_transforms:
        aug_tile = apply_transform(tile)     # augmentáció
        aug_dets = predictor.predict(aug_tile)
        orig_dets = inverse_transform(aug_dets, transform, tile_w, tile_h)
        dets += orig_dets                    # összevonás
    all_detections += dets                   # globális koordinátákba

global_nms(all_detections)                   # TTA duplikátumokat is kiszűri
```

### Inverz transzformáció logika

| Transzformáció | PIL művelet | Bbox inverz |
|----------------|-------------|-------------|
| `flip_h` | `ImageOps.mirror(tile)` | `x1 = tile_w - x2_aug` <br> `x2 = tile_w - x1_aug` <br> y változatlan |
| `flip_v` | `ImageOps.flip(tile)` | `y1 = tile_h - y2_aug` <br> `y2 = tile_h - y1_aug` <br> x változatlan |
| `rot90` | `tile.rotate(90, expand=True)` <br> → forgatott méret: `(tile_h, tile_w)` | `x1 = tile_w - y2_aug` <br> `y1 = x1_aug` <br> `x2 = tile_w - y1_aug` <br> `y2 = x2_aug` |

---

## Döntési logika – Non-Maximum Suppression (NMS)

```
FUNCTION apply_nms(detections, iou_threshold):
    detections = sort_by_confidence(detections, descending=True)
    kept = []

    WHILE detections is not empty:
        best = detections[0]
        kept.append(best)
        detections = detections[1:]

        detections = [d for d in detections
                      if iou(best.bbox, d.bbox) < iou_threshold]

    RETURN kept
```

### IoU küszöb hatása

| IoU küszöb | Hatás | Ajánlott eset |
|------------|-------|---------------|
| 0.1-0.2 | Agresszív szűrés (sok duplikát kiszűr) | Ha sok átfedő detekció van |
| 0.45 | Általános (alapértelmezett) | Legtöbb esetben |
| 0.7-0.8 | Enyhe szűrés (megmaradnak az átfedők) | Ha sűrű növényzet |

---

## Döntési logika – GeoTIFF és GeoJSON export

### GeoTIFF detektálás

```python
from utils.geotiff import is_geotiff, GeoTIFFHandler

if is_geotiff(file_path):
    SHOW GeoTIFF metadata (CRS, bounds)
    ENABLE GeoJSON export gomb
else:
    HIDE GeoJSON export gomb
```

### Pixel → GPS koordináta konverzió

```python
handler = GeoTIFFHandler(file_path)
transform = handler.get_transform()  # Rasterio affine transform

FOR each detection:
    # Pixel koordináták → georeferált koordináták
    lon_min, lat_min = transform * (detection.x_min, detection.y_max)
    lon_max, lat_max = transform * (detection.x_max, detection.y_min)

    # Ha CRS nem WGS84:
    IF crs != EPSG:4326:
        transformer = Transformer.from_crs(crs, "EPSG:4326")
        lon_min, lat_min = transformer.transform(lon_min, lat_min)
        lon_max, lat_max = transformer.transform(lon_max, lat_max)

    # GeoJSON Polygon feature
    feature = {
        "type": "Feature",
        "geometry": {"type": "Polygon", "coordinates": [[
            [lon_min, lat_min], [lon_max, lat_min],
            [lon_max, lat_max], [lon_min, lat_max],
            [lon_min, lat_min]
        ]]},
        "properties": {
            "class_id": detection.class_id,
            "class_name": detection.class_name,
            "confidence": detection.confidence
        }
    }
```

---

## Döntési logika – Területszámítás (calculate_area_ha)

**Státusz:** ✅ IMPLEMENTÁLVA (2026-03-13) — `utils/geotiff.py` (`calculate_area_ha`)

### Vetületi vs. földrajzi CRS kezelés

```python
with rasterio.open(file_path) as src:
    crs = src.crs
    transform = src.transform
    width, height = src.width, src.height

    pixel_size_x = abs(transform[0])  # vízszintes pixelméret
    pixel_size_y = abs(transform[4])  # függőleges pixelméret

    IF crs.is_geographic:  # pl. EPSG:4326 (fokokban)
        bounds = src.bounds
        center_lat = (bounds.top + bounds.bottom) / 2.0
        lat_rad = math.radians(center_lat)
        pixel_size_x_m = pixel_size_x * 111320.0 * cos(lat_rad)  # fok → méter
        pixel_size_y_m = pixel_size_y * 111320.0
    ELSE:  # vetületi CRS, pl. EPSG:32633 UTM (méterben)
        pixel_size_x_m = pixel_size_x
        pixel_size_y_m = pixel_size_y

    area_m2 = pixel_size_x_m * width * pixel_size_y_m * height
    RETURN round(area_m2 / 10000.0, 4)  # m² → hektár, 4 tizedesjegy
```

### Visszatérési értékek

| Feltétel | Visszatérés |
|----------|-------------|
| Nincs CRS vagy transform | `None` |
| Vetületi CRS (pl. UTM) | `float` ha (pixel méret m-ben × kép méret) |
| Földrajzi CRS (pl. WGS84) | `float` (cos(lat) korrekcióval) |

---

## Döntési logika – Sor-detekció algoritmus

**Státusz:** ✅ FRISSÍTVE (2026-03-30) — `utils/row_detector.py`

### Pipeline sorrend

```
YOLO detekció → IoU NMS → Bio NMS → Sor-detekció + penalty → Cluster osztályozás → Tab megjelenítés
```

A sor-detekció a tabak **előtt** fut, a szűrt detekciók mindkét tabban megjelennek.

### 1. Domináns szög meghatározása (PCA/SVD)

```python
# Centroids: Nx2 tömb, minden detekció bbox közepéből
mean = np.mean(centroids, axis=0)
centered = centroids - mean
_, _, Vt = np.linalg.svd(centered, full_matrices=False)
dx, dy = Vt[0]                            # legnagyobb variancia iránya
angle_deg = degrees(atan2(dy, dx))        # -90° .. +90° tartományba normálva
```

> Korábban Hough-transzformáció volt; az SVD-alapú megközelítés pontmintázaton dolgozik, nem pixel intenzitáson, ezért megbízhatóbb soros kultúrákon.

### 2. Sorcsoport azonosítás (gap-alapú Tukey-kerítés)

```python
projections = centroids @ np.array([-sin(angle_rad), cos(angle_rad)])

sorted_proj = np.sort(projections)
diffs = np.diff(sorted_proj)
q25, q75 = np.percentile(diffs, [25, 75])
threshold = q75 + 1.5 * (q75 - q25)   # Tukey-kerítés

boundaries = np.where(diffs > threshold)[0]
groups = np.split(sorted_proj, boundaries + 1)
row_peaks = [mean(g) for g in groups if len(g) >= min_row_points]
```

### 3. Polilinek illesztése soronként

```python
for row_idx, row_peak in enumerate(row_peaks):
    u_coords = centroids[assignments == row_idx] @ [cos(a), sin(a)]
    coeffs = np.polyfit(u_coords, v_residuals, deg=poly_degree)
    IF max_bend_exceeded:
        coeffs = np.polyfit(u_coords, v_residuals, deg=max(0, poly_degree - 1))
```

### 4. Elméleti sortáv-validáció (opcionális, 6.5. lépés)

**Státusz:** ✅ IMPLEMENTÁLVA (2026-03-30)

```
IF theoretical_row_spacing_cm megadva AND gsd_cm_per_pixel ismert:
    min_spacing_px = theoretical_row_spacing_cm × 0.65 / gsd_cm_per_pixel

    FOR minden szomszédos sorpár (i, i+1):
        weaker = argmin(support[i], support[j])  # support = medián_conf × count

        IF poly_degree == 0 (egyenes):
            dist = |row_positions[j] - row_positions[i]|
            IF dist < min_spacing_px:
                → weaker sor: row_valid_flags[weaker] = False (egész sor érvénytelen)

        ELSE (polinom görbe):
            u_samp = linspace(u_overlap_min, u_overlap_max, N)
            dist_samp = |v_j(u) - v_i(u)|  ← görbék távolsága mintavételezve
            too_close = dist_samp < min_spacing_px

            IF all(too_close):
                → weaker sor egészében érvénytelen
            ELIF any(too_close):
                → weaker sorhoz szakaszlista: [(u_start, u_end, is_valid), ...]
                → érintett u szakaszon: is_valid = False
```

**Aszimmetria:** Két sor közelebb nem futhat, mint az elméleti minimum. Távolabb igen (kihagyott sor).

### 5. Assignment újraszámítás (érvénytelen sorok és szakaszok alapján)

```
FOR minden detekció (asgn, centroid):
    IF asgn == -1: → marad outlier
    IF row_valid_flags[asgn] == False AND sections == None: → outlier
    IF sections != None:
        u = centroid @ [cos(a), sin(a)]  ← forgott rendszer u koordinátája
        section = find_section(sections, u)
        IF section.is_valid == False: → outlier
```

### 6. Paraméterek

| Paraméter | Típus | Tartomány | Alapért. | Hatás |
|-----------|-------|-----------|----------|-------|
| `min_row_points` | int | 2–10 | 3 | Min. pont egy érvényes sorhoz |
| `poly_degree` | int | 0, 1, 2 | 2 | Polinom foka (0=pont, 2=másodfokú) |
| `max_bend_deg` | float | 5–45° | 15° | Max. törési szög a görbén |
| `theoretical_row_spacing_cm` | float \| None | 5–500 | None | Elméleti sortávolság (cm); ha megadva, sortáv-validáció fut |

---

## Döntési logika – Sor-alapú confidence penalty

**Státusz:** ✅ FRISSÍTVE (2026-03-30) — `utils/row_detector.py` (`apply_row_confidence_penalty`)

### Mikor érhető el?

```
IF use_row_detection == True:
    "Soron kívüli detekciók kezelése" radio jelenik meg
    options: ["Confidence büntetés (ajánlott)", "Teljes eltávolítás"]
    IF mode == "penalty":
        "Büntetési szorzó" slider (0.1–0.9, alapért. 0.5)
```

### Penalty logika

```
FOR minden detection, assignment IN zip(detections, row_assignments):
    IF assignment == -1:  # outlier (soron kívüli VAGY érvénytelen szakasz)
        new_conf = detection['confidence'] * penalty_factor
        IF new_conf < conf_threshold:
            → törlés (removed_count++)
        ELSE:
            → megtartás new_conf értékkel (dict másolat, nem in-place!)
    ELSE:
        → változatlanul megtartás
```

### Kimeneti viselkedés összehasonlítása

| Mód | Outlier viselkedés | Eredmény |
|-----|--------------------|----------|
| `penalty` | conf × szorzó → threshold alatt töröl | Finomabb szűrés |
| `remove` | Minden outlier törlés | Konzervatív szűrés |
| (nincs szűrés) | Minden detekció megmarad | RowDetector csak infó |

> **Fontos:** A szűrés a tabak **előtt** fut — mindkét tab (egyedi + cluster) a szűrt detekciókból dolgozik. A `result['count']` az eredeti detekciós count marad; a tab fejlécben a szűrt szám jelenik meg.

### Vizualizáció

| Elem | Megjelenés | Jelentés |
|------|-----------|----------|
| Elsődleges sor | Folytonos zöld vonal | Érvényes, sortáv-konzisztens |
| Másodlagos sor | Szaggatott sárga vonal | Túl közel futott egy erősebb sorhoz |
| Érvénytelen szakasz | Szaggatott sárga szakasz | Csak ezen a részen közelít egy másik sorhoz |
| Outlier detekció | Piros keretű bbox | Soron kívüli vagy érvénytelen szakaszon |

A sorvonalak **mindkét tab képén** megjelennek (toggle: "Sorvonalak megjelenítése" — alapért. be).

---

## Döntési logika – Cluster alapú tőszám becslés

**Státusz:** ✅ IMPLEMENTÁLVA (2026-03-29) — `utils/counting_calculator.py` (`CountingCalculator`)

### Mikor érhető el?

```
IF gsd_cm_per_pixel > 0:          # GeoTIFF-ből kinyert VAGY kézi megadás
    Counting eredmény blokk látható (cluster becslés + kelési arány)
    "🔄 Paraméterek finomhangolása" expander aktív
ELSE:
    Counting eredmény blokk rejtve
```

### Osztályozás logika (individual vs cluster)

```python
bbox_area_cm2 = bbox_area_px * gsd_cm_per_pixel²

avg_size   = class_calibrations[class_id]['avg_bbox_size_cm2']
threshold  = class_calibrations[class_id].get('cluster_threshold', 2.0)
cluster_threshold_cm2 = avg_size * threshold

IF bbox_area_cm2 <= cluster_threshold_cm2:
    → "individual" (egyedi tő)
ELIF avg_size is None:
    → "uncalibrated" (nincs kalibráció)
ELSE:
    → "cluster"
```

### Cluster tőszám becslés

```python
estimated_plants = round(cluster_area_cm2 / avg_plant_size_cm2)
# minimum 1 tő per cluster
```

### Vizualizáció (`draw_counting_overlay`)

| Osztály | Szín | Jelölés |
|---------|------|---------|
| Individual | 🟢 `#00C850` | Sima téglalap |
| Cluster | 🟠 `#FF8C00` | Vastag téglalap + fekete hátteres számfelirat (becsült tövek) |
| Uncalibrated | 🟡 `#C8C800` | Sima téglalap |

### GSD érzékenység

A cluster becslés pontossága erősen függ a helyes GSD értéktől:
- GSD = 8 cm/px: cluster küszöb = 450 × 2 = 900 cm² ≈ ~14 px² → szinte minden detekció cluster
- GSD = 0.5 cm/px: cluster küszöb = 2500 × 2 = 5000 cm² → tipikus tőméretek (100×100 px = 2500 cm²) egyediek

> **Irányelv:** A kalibrációs `avg_bbox_size_cm2` értéket egyetlen reprezentatív tő bbox méreteként kell megadni az adott GSD mellett. Pl. GSD=0.5 cm/px, tipikus tő 100×100 px → avg_bbox_size = 0.5² × 10000 = 2500 cm².

---

## Döntési logika – Confidence Distribution Analysis

**Státusz:** ✅ IMPLEMENTÁLVA (2026-03-10) — `pages/3_Counting.py` (`_compute_suggested_threshold`)

### Mikor jelenik meg?

```
IF use_sliding_window == True
AND len(result['raw_confidences']) >= 10:
    "📊 Confidence eloszlás elemzés" expander látható
```

### Threshold javaslat döntési logika

```
hist = histogram(raw_confidences, bins=30, range=[0.01, 1.0])
smoothed = moving_average(hist, window=3)

peaks = top-2 csúcs (minimum 3 bin távolságra egymástól)

IF len(peaks) < 2:
    → None  (nem bimodális, nincs javaslat)
ELSE:
    valley = argmin(smoothed[left_peak+1 : right_peak])
    → round(bin_centers[valley], 2)  (minimum: 0.05)
```

### Megjelenítési döntés

```
IF plotly telepítve:
    Interaktív Plotly chart
    + piros vline (jelenlegi threshold)
    + zöld vline (javasolt, ha van)
ELSE:
    st.bar_chart fallback (pandas)
```

---

## Döntési logika – Prescription Map

**Státusz:** ✅ IMPLEMENTÁLVA (2026-03-07) — `utils/prescription_map.py`

### Mikor elérhető?

```
IF detekciók száma == 0:
    INFO üzenet — nem generálható
ELSE:
    Prescription map expander aktív
```

### Cellaméret logika

```
IF gsd_cm_per_pixel ismert (GeoTIFF):
    cell_size_px = cell_size_m * 100 / gsd_cm_per_pixel
    → slider: 1–10 m, alapértelmezett: 2 m
ELSE:
    cell_size_px = 100 (rögzített fallback)
    → slider nem jelenik meg
```

### Zóna osztályozás (kvartilis alapján)

```
nonzero_counts = [c for c in counts if c > 0]
Q1 = percentile(nonzero_counts, 25)
Q3 = percentile(nonzero_counts, 75)

IF count == 0:     → "empty"
IF count < Q1:     → "low"
IF count < Q3:     → "medium"
IF count >= Q3:    → "high"
Edge case (Q1 == Q3): minden nonzero → "medium"
```

### Heatmap szín logika

| t = count / max_count | Zóna | Szín |
|-----------------------|------|------|
| 0 (count == 0) | (kihagyva) | átlátszó |
| 0.00 – 0.33 | low | #2ecc71 (zöld) |
| 0.33 – 0.67 | medium | #f39c12 (sárga) |
| 0.67 – 1.00 | high | #e74c3c (piros) |

### Export elérhetőség

| Export | Feltétel |
|--------|----------|
| Heatmap PNG | Mindig (ha van detekció) |
| CSV | Mindig — lon/lat üres ha nem GeoTIFF |
| GeoJSON | Csak GeoTIFF + geo_handler esetén |

### UI belépési pontok

| Oldal | Hely | Megjegyzés |
|-------|------|------------|
| `3_Counting.py` | Export szekció után | Friss inferencia eredményéből |
| `4_Results.py` | Detekciók részletei után | Mentett eredményből visszatölti GeoTIFFHandler-t |

---

## Döntési logika – Kredit rendszer

### Hektáralapú kredit számítás

**Státusz:** ✅ IMPLEMENTÁLVA (2026-03-13)

```python
import math
from utils.geotiff import calculate_area_ha

# Területszámítás GeoTIFF esetén
area_ha = calculate_area_ha(image_path)       # float, pl. 1.65
required_credits = max(math.ceil(area_ha), 1) # felkerekítés, minimum 1
```

| Terület | Számított kredit |
|---------|-----------------|
| 0.3 ha | 1 kredit |
| 1.0 ha | 1 kredit |
| 1.65 ha | 2 kredit |
| 3.2 ha | 4 kredit |

### GeoTIFF követelmény döntési fa

```
require_geotiff = user_manager.get_require_geotiff(user_id)

IF require_geotiff == True:
    IF NOT is_geotiff(image_path):
        → ERROR: "Georeferált kép szükséges"  (megállás)
    ELSE:
        → Területszámítás + kredit ellenőrzés + megerősítő dialog

IF require_geotiff == False:  # admin bypass
    IF is_geotiff(image_path):
        → Területszámítás + kredit ellenőrzés + megerősítő dialog
    ELSE:
        → required_credits = 1 (fix, terület nélkül)
        → Kredit ellenőrzés + direkt indítás (dialog nélkül)
```

### Kredit ellenőrzés és levonás

```python
from utils.credit_manager import get_user_credits, check_credits, deduct_credits

credits = get_user_credits(user_id)

IF NOT check_credits(credits, required_credits):
    RAISE InsufficientCreditsError("Nincs elegendő kredit!")

# Megerősítő dialog (GeoTIFF esetén) → felhasználó jóváhagyja
# Kredit levonás a detekció ELŐTT (megerősítés után)
deduct_credits(user_id, amount=required_credits)

# Detekció futtatása
results = run_detection(...)
```

---

## Döntési logika – Modell szűrés (láthatóság alapján)

```python
all_models = model_registry.get_all_models()

visible_models = [m for m in all_models if
    m.visibility == "public"
    OR m.owner_id == current_user_id]

# Megjelenítés: saját modellek (private) + nyilvános modellek (public)
```

---

## Döntési logika – GT Paraméter-kereső (2D Sweep)

**Státusz:** ✅ IMPLEMENTÁLVA (2026-03-24)

### Mikor érhető el?

```
IF counting_mode == "Paraméter-kereső (GT régió)":
    → Régió kijelölés sliderek megjelennek
    → Conf és IoU sliderek elrejtve
    → Sweep tartomány beállítások megjelennek
```

### Régió kijelölés döntési logika

```
user_input: left_pct, top_pct, width_pct, height_pct  (0–100)
→ x1 = int(left_pct / 100 * image_width)
→ y1 = int(top_pct / 100 * image_height)
→ x2 = min(x1 + int(width_pct / 100 * image_width), image_width)
→ y2 = min(y1 + int(height_pct / 100 * image_height), image_height)
→ session_state['gt_roi'] = (x1, y1, x2, y2)
→ image = image.crop((x1, y1, x2, y2))  # csak a régió megy inference-re
```

### Sweep indítás feltételei

```
IF gt_roi is not None
AND gt_region_count > 0  (kézi tőszám megadva)
AND result is not None   (inference lefutott)
AND len(raw_boxes) > 0:
    → 2D sweep fut
ELSE:
    → "Add meg a kézi tőszámot és futtasd az inference-t!"
```

### IoU tartomány megválasztása

| Növénytípus | Ajánlott IoU tartomány | Indoklás |
|-------------|----------------------|----------|
| Kukorica (korai) | 0.60–0.95 | Közel álló tövek, magas IoU kell |
| Kukorica (fejlett) | 0.70–0.95 | Átfedő levelek, sok duplikátum |
| Napraforgó | 0.30–0.80 | Távolabb álló tövek |
| Általános | 0.10–0.90 | Első keresés, durva rács |

### Iteratív finomítás stratégia

```
1. KERESŐ (durva):
   conf: 0.01–0.50, step 0.05
   IoU: 0.10–0.95, step 0.10
   → talált optimum: conf≈0.05, IoU≈0.85

2. FINOMÍTÓ:
   conf: 0.01–0.10, step 0.01
   IoU: 0.75–0.95, step 0.02
   → pontosabb: conf=0.03, IoU=0.87
```

### Biológiai NMS hatása a sweep-re

```
IF bio_nms aktív (gsd > 0 AND min_distance_cm > 0):
    → Minden (conf, IoU) kombinációnál:
      globális NMS → biológiai NMS → végső count
    → A sweep tökéletesen reprodukálja a normál pipeline-t
ELSE:
    → Csak globális NMS → végső count
```

### Eredmény értékelés

| Pontosság (%) | Értékelés | Színkód |
|---------------|-----------|---------|
| ≥ 95% | Kiváló | 🟢 Zöld |
| 80–95% | Jó | 🟡 Sárga |
| < 80% | Gyenge — modell finomhangolás javasolt | 🔴 Piros |

---

## Hibaágak

### "Nem találtam objektumokat a képen"

**Ok:** Konfidencia küszöb túl magas VAGY helytelen modell választás
**Döntés:** 0 detekció, de nem hiba
**Tipp:** Csökkentsd konfidencia küszöböt (0.25 → 0.10-0.15)

### "Hiba a modell betöltése során!"

**Ok:** ONNX fájl sérült vagy nem kompatibilis ONNX Runtime verzióval
**Döntés:** Modell kizárva
**Kezelés:** Modell újratanítása vagy ONNX Runtime frissítés

### "Túl sok false positive"

**Ok:** Konfidencia küszöb túl alacsony
**Döntés:** Eredmény megmutatva, de figyelmeztetés
**Tipp:** Növeld konfidencia küszöböt (0.25 → 0.35-0.45)

### "GeoJSON export nem jelenik meg"

**Ok:** Nem GeoTIFF fájl (nincs CRS)
**Döntés:** GeoJSON gomb rejtett
**Kezelés:** Használj georeferált TIFF-et (RTK GPS vagy GCP-k)
