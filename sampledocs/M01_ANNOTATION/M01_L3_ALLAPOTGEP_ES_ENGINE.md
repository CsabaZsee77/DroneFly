# L3 – Állapotgép és Engine – Annotation (Képcímkézés és Dataset Kezelés)

**Modul:** M01
**Szint:** L3 – Állapotgép és Engine
**Utolsó frissítés:** 2026-03-10

---

## Forrásfájlok

| Fájl | Szerepkör |
|------|-----------|
| `pages/1_Annotation.py` | Streamlit UI – teljes annotációs felület |
| `core/tiling.py` | TIFF feldarabolás tile-okra |
| `utils/dataset_manager.py` | Dataset kezelés, training version létrehozás |
| `utils/tiling_utils.py` | Tiling helper függvények |
| `utils/image_manager.py` | Kép feltöltés és galéria |
| `utils/auth_helpers.py` | Autentikáció ellenőrzés |
| `utils/styles.py` | CSS styling |

---

## Adatmodell

### Projekt struktúra (JSON metadata)

**Elérési út:** `data/datasets/{project_id}/metadata.json`

```json
{
  "project_id": "uuid-v4",
  "name": "Kukorica Annotation 2026",
  "description": "Kukorica tövek annotálása barna talajon",
  "owner_id": "user-uuid",
  "owner_username": "zsigmond",
  "created_at": "2026-01-15T10:30:00Z",
  "updated_at": "2026-02-10T14:20:00Z",
  "tile_size": 640,
  "overlap": 10,
  "tile_format": "png",
  "classes": ["kukorica_to"],
  "tags": ["kukorica", "2026", "barna_talaj"],
  "dataset_id": "dataset-uuid",
  "versions": [
    {
      "version_id": "version-uuid",
      "name": "v1",
      "created_at": "2026-01-20T09:00:00Z",
      "train_ratio": 0.80,
      "val_ratio": 0.15,
      "test_ratio": 0.05,
      "tile_count": 180,
      "annotated_count": 145
    }
  ]
}
```

### Könyvtárstruktúra (fájlrendszer, F1-01 után — user-scoped)

```
data/
└── users/
    └── {user_id}/
        ├── uploads/               # Feltöltött drónképek (TIFF, JPG, PNG)
        ├── datasets/
        │   └── {project_id}/
        │       ├── metadata.json          # Projekt metaadatok
        │       ├── tiles/                 # Tile képek
        │       │   ├── tile_000.png
        │       │   ├── tile_001.png
        │       │   └── ...
        │       ├── labels/                # YOLO annotációk
        │       │   ├── tile_000.txt       # Annotált tile
        │       │   ├── tile_001.txt       # Annotálatlan (üres fájl)
        │       │   └── ...
        │       ├── source_images/         # Eredeti TIFF-ek
        │       │   └── original.tif
        │       └── versions/
        │           └── {version_id}/
        │               ├── version_meta.json
        │               ├── train/
        │               │   ├── images/
        │               │   └── labels/
        │               ├── val/
        │               │   ├── images/
        │               │   └── labels/
        │               └── test/
        │                   ├── images/
        │                   └── labels/
        ├── detection_results/     # Detekciós eredmények
        └── temp/                  # Ideiglenes fájlok (map overlay cache stb.)
```

**Path generálás:** minden path a `utils/path_manager.py` függvényein keresztül keletkezik.
`get_datasets_dir(user_id)` → `data/users/{user_id}/datasets/`
`get_uploads_dir(user_id)` → `data/users/{user_id}/uploads/`

### YOLO annotáció formátum

**Fájlnév:** `{tile_neve}.txt`

**Detection (bounding box) formátum:**
```
class_id x_center y_center width height
```
- Minden sor: 1 bounding box
- Koordináták normalizálva (0.0 - 1.0)

**Segmentation (polygon) formátum:**
```
class_id x1 y1 x2 y2 x3 y3 ... xn yn
```
- Minden sor: 1 objektum polygonnal (min. 3 pont)
- Koordináták normalizálva (0.0 - 1.0)
- Ugyanaz a `.txt` fájlkiterjesztés mint detection esetén

**Közös szabályok:**
- Üres fájl = annotálatlan tile (0 objektum)
- A formátum az annotáció típusától függ (detection vs. segmentation)
- Egy projekten belül NE keverjük a két formátumot (training inkompatibilitás)

---

## Állapotgép

### Tile állapotok

```
[Nincs tile]
    │
    │ Csempézés sikeres
    ▼
[Annotálatlan tile]
    │
    │ Felhasználó bounding box-ot rajzol és ment
    ▼
[Annotált tile]  ←──────────────── Újraszerkesztés
    │
    │ Training version létrehozása
    ▼
[Training version-be felvéve]
```

### Projekt állapot-átmenetek

```
DRAFT (kép nélkül)
    │  Kép feltöltés + csempézés
    ▼
TILED (csempék vannak, nincs annotáció)
    │  Első annotáció mentése
    ▼
IN_PROGRESS (részlegesen annotált)
    │  Min. 10 annotált csempe
    ▼
READY (training verzió létrehozható)
    │  Verzió létrehozva
    ▼
VERSIONED (van training verzió)
```

---

## Streamlit Session State kezelés (1_Annotation.py)

A következő session state kulcsok kezelődnek:

| Kulcs | Típus | Tartalma |
|-------|-------|----------|
| `current_project_id` | str | Aktív projekt UUID |
| `current_tile_index` | int | Aktuálisan nézett csempe indexe |
| `annotation_mode` | str | "existing" vagy "new_image" |
| `canvas_key` | str | Canvas újrarenderelés kényszerítéséhez |
| `projects_list` | list | Projektek cache |
| `creating_project` | bool | Projekt létrehozás form látható |
| `creating_version` | bool | Version létrehozás form látható |

---

## Csempézési motor (core/tiling.py)

### Főbb függvények

```python
def tile_tiff(
    tiff_path: str,
    output_dir: str,
    tile_size: int = 640,
    overlap: float = 0.1,
    output_format: str = "png"
) -> int:
    """
    TIFF fájl tile-okra bontása.
    Returns: létrehozott tile-ok száma
    """

def calculate_tile_positions(
    image_width: int,
    image_height: int,
    tile_size: int,
    overlap: float
) -> List[Tuple[int, int]]:
    """
    Tile pozíciók kiszámítása (x, y kezdőpontok listája)
    """
```

### Rasterio-alapú TIFF kezelés

```python
import rasterio
from rasterio.windows import Window

with rasterio.open(tiff_path) as src:
    for (x, y) in tile_positions:
        window = Window(x, y, tile_size, tile_size)
        tile_data = src.read(window=window)
        # GeoTIFF esetén: georeferencia megőrzés
        tile_transform = src.window_transform(window)
```

---

## ImageManager engine (utils/image_manager.py)

### save_uploaded_image — validáció (F1-06)

A `save_uploaded_image()` minden feltöltéskor három ellenőrzést végez a mentés előtt:

```python
# 1. Fájlméret
MAX_UPLOAD_SIZE_MB = 500
if len(file_buffer) > MAX_UPLOAD_SIZE_MB * 1024 * 1024:
    raise ValueError("A fájl mérete meghaladja az 500 MB-os limitet!")

# 2. Kiterjesztés whitelist
ALLOWED_EXTENSIONS = {".jpg", ".jpeg", ".png", ".tif", ".tiff"}
if extension not in ALLOWED_EXTENSIONS:
    raise ValueError(f"Nem engedélyezett kiterjesztés: '{extension}'")

# 3. MIME validáció (PIL)
img = Image.open(io.BytesIO(file_buffer))
img.verify()  # Kép integritás ellenőrzés — kivétel ha nem valódi képfájl
```

**Hibakezelés a page-eken:** a `ValueError` üzenetet a Streamlit `st.error()` jeleníti meg.

### Fájlnév sanitizálás

```python
# Nem biztonságos karakterek cseréje aláhúzásra (path traversal megelőzés)
sanitize_filename(original_name)  # → [a-zA-Z0-9_\-\.] karakterek megtartva
```

A belső fájlnév: `{timestamp}_{safe_base}.{ext}` — az eredeti név csak display célra tárolódik.

---

## Dataset Manager engine (utils/dataset_manager.py)

### Főbb osztályok és metódusok

```python
class DatasetManager:
    def create_project(self, name, description, classes, owner_id, ...) -> str:
        """Új projekt létrehozása, könyvtárstruktúra inicializálás"""

    def get_projects(self, user_id=None) -> List[dict]:
        """Felhasználóhoz tartozó projektek listája"""

    def get_project(self, project_id) -> dict:
        """Egy projekt metaadatai"""

    def save_annotation(self, project_id, tile_name, annotations: List[dict]) -> None:
        """YOLO formátumú annotáció mentése .txt fájlba"""

    def load_annotation(self, project_id, tile_name) -> List[dict]:
        """Annotáció betöltése canvas-ra"""

    def get_tiles(self, project_id) -> List[str]:
        """Tile-ok listája a projekthez"""

    def get_annotated_tiles(self, project_id) -> List[str]:
        """Annotált tile-ok listája (tartalmaz legalább 1 bounding box-ot)"""

    def create_version(self, project_id, version_name, tile_names, train_ratio, val_ratio) -> str:
        """Training version létrehozása (fájlok másolása, meta frissítés)"""

    def copy_project(self, source_project_id, new_name, owner_id) -> str:
        """Projekt másolása (Save As)"""
```

### Training version létrehozás belső folyamata

```
1. Annotált tile-ok szűrése (üres label fájlok kizárva)
2. Shuffle (random seed = 42)
3. Split: train_tiles, val_tiles, test_tiles
4. Könyvtárstruktúra:
   versions/{version_id}/train/images/
   versions/{version_id}/train/labels/
   versions/{version_id}/val/...
   versions/{version_id}/test/...
5. Fájlok másolása (symlink helyett fizikai másolat)
6. data.yaml generálás YOLO formátumban:
   path: .../versions/{version_id}
   train: train/images
   val: val/images
   test: test/images
   nc: {class_count}
   names: {class_names}
7. Metadata frissítés
```

---

## Drawable Canvas integráció

A `streamlit-drawable-canvas` könyvtár kezeli a rajzolást:

```python
from streamlit_drawable_canvas import st_canvas

# Annotáció mód: "detection" → "rect", "segmentation" → "polygon"
annotation_mode = st.sidebar.radio("Annotáció típusa", ["detection", "segmentation"])
drawing_mode = "rect" if annotation_mode == "detection" else "polygon"

canvas_result = st_canvas(
    fill_color="rgba(255, 165, 0, 0.3)",
    stroke_width=stroke_width,
    stroke_color=stroke_color,
    background_image=pil_image,
    update_streamlit=True,
    height=display_size,
    width=display_size,
    drawing_mode=drawing_mode,   # "rect" (detection) vagy "polygon" (segmentation)
    key=canvas_key,
    initial_drawing=existing_drawing  # Meglévő annotációk betöltésekor
)

# Canvas output feldolgozás – Detection (bounding box)
if canvas_result.json_data and annotation_mode == "detection":
    objects = canvas_result.json_data["objects"]
    for obj in objects:
        x_min = obj["left"]
        y_min = obj["top"]
        x_max = obj["left"] + obj["width"]
        y_max = obj["top"] + obj["height"]
        # → YOLO detection koordinátákra konverzió: cx cy w h

# Canvas output feldolgozás – Segmentation (polygon)
if canvas_result.json_data and annotation_mode == "segmentation":
    objects = canvas_result.json_data["objects"]
    for obj in objects:
        if obj.get("type") == "path":
            points = obj.get("path", [])  # SVG path pontok
            # → YOLO segmentation koordinátákra konverzió: x1 y1 x2 y2 ... xn yn
```

### Verzió-kompatibilitási megjegyzés

> **FONTOS:** A `streamlit-drawable-canvas` NEM kompatibilis Streamlit 1.52+ verziókkal az eredeti verziójában. A projektben ez a kompatibilitási probléma megoldott (lásd ANNOTATION_TOOL.md).

---

## Sidebar beállítások és hatásuk

| Sidebar elem | Streamlit widget | Hatás |
|--------------|-----------------|-------|
| Tile méret | `st.slider(320, 1280)` | Csempézéskor alkalmazott méret |
| Átfedés (%) | `st.slider(0, 50)` | Csempézéskor alkalmazott átfedés |
| Tile formátum | `st.selectbox(["PNG", "TIFF"])` | Output fájl formátum |
| Vonal vastagság | `st.slider(1, 10)` | Canvas bounding box vastagság |
| Vonal szín | `st.color_picker()` | Canvas bounding box szín |
| Új osztály | `st.text_input()` + gomb | Osztály hozzáadása a projekthez |

---

## Teljesítmény szempontok

| Tényező | Hatás | Optimalizáció |
|---------|-------|---------------|
| Nagy TIFF (> 100MB) | Lassú csempézés | Tile méret növelése (640→1280) |
| Sok tile (> 1000 db) | Lassú navigáció | Projektenkénti tile limit nincs |
| Canvas renderelés | CPU-intenzív | Display méret korlátozása (640px) |
| Annotáció betöltés | Fájl I/O | Cache: session state |

---

## Annotáció Ellenőrzőpont (Snapshot) rendszer

### Állapotgép

```
[Annotáció szerkesztés]
        │
        ▼ (felhasználó kézi mentés VAGY auto-snapshot visszaállítás előtt)
[Snapshot létrehozva]  ←─── labels/*.txt másolva → snapshots/{id}/labels/
        │
        ├── ♻️ Visszaállítás → [Annotáció visszaállítva]
        │       (auto-checkpoint a jelenlegi állapotról + régi labels visszamásolva)
        │
        └── 🗑️ Törlés → [Snapshot törölve]
                (snapshot mappa + manifest entry eltávolítva)
```

### Adatmodell

**Tárolási struktúra:**
```
data/datasets/{project}/
  snapshots/
    snapshot_manifest.json          ← összes snapshot indexe
    {snapshot_id}/
      labels/                       ← mentett .txt label fájlok
        tile_0_0.txt
        tile_1_0.txt
        ...
```

**Manifest JSON séma (`snapshot_manifest.json`):**
```json
{
  "snapshots": [
    {
      "snapshot_id": "UUID",
      "name": "50 kép kész",
      "created_at": "2026-03-23T14:30:00",
      "auto": false,
      "label_count": 42,
      "object_count": 187
    }
  ]
}
```

| Mező | Típus | Leírás |
|------|-------|--------|
| `snapshot_id` | UUID string | Egyedi azonosító |
| `name` | string | Felhasználó által adott név (vagy auto-generált) |
| `created_at` | ISO timestamp | Létrehozás időpontja |
| `auto` | bool | `true` = automatikusan generált (visszaállítás előtt) |
| `label_count` | int | Nem-üres label fájlok száma |
| `object_count` | int | Összes annotált objektum (label sorok) |

### Engine: `DatasetManager` snapshot metódusok

| Metódus | Fájl | Leírás |
|---------|------|--------|
| `create_snapshot(project_id, name, auto)` | `dataset_manager.py` | Label fájlok másolása snapshot mappába + manifest frissítés |
| `list_snapshots(project_id)` | `dataset_manager.py` | Manifest olvasás, `created_at` desc rendezés |
| `restore_snapshot(project_id, snapshot_id)` | `dataset_manager.py` | Auto-checkpoint + labels felülírás snapshot-ból |
| `delete_snapshot(project_id, snapshot_id)` | `dataset_manager.py` | Snapshot mappa + manifest entry törlés |

### Auto-prune szabály

- Max **20 snapshot** projektenként
- Ha túllépné: legrégebbi `auto=true` snapshot automatikusan törlődik
- Kézi (`auto=false`) snapshot-ok soha nem törlődnek automatikusan
