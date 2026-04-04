# L3 – Állapotgép és Engine – Modell Nyilvántartás (Model Registry)

**Modul:** M05
**Szint:** L3 – Állapotgép és Engine
**Forrásdokumentumok:** TRAINING_MODULE.md, MODEL_EXPORT_GUIDE.md, core/model_manager.py, utils/model_metadata.py

---

## Forrásfájlok

| Fájl | Szerepkör |
|------|-----------|
| `utils/model_metadata.py` | Model Registry CRUD műveletek |
| `core/model_manager.py` | ONNX betöltés, cache, registry integráció |
| `models/model_registry.json` | Központi modell adatbázis (JSON) |
| `models/*.onnx` | ONNX inference modellek |
| `models/pt_weights/*.pt` | PyTorch súlyok (transfer learning) |
| `models/samples/*.jpg` | Modell sample thumbnail-ek |

---

## Adatmodell – model_registry.json

```json
{
  "version": "2.0",
  "models": [
    {
      "id": "uuid-v4",
      "file_name": "model_2026_01_15_1430_a1b2c3d4.onnx",
      "display_name": "Kukorica Tő - YOLOv11n",
      "version": "1.0",
      "visibility": "public",
      "owner": {
        "user_id": "user-uuid",
        "username": "zsigmond"
      },
      "created_at": "2026-01-15T14:30:00Z",
      "technical": {
        "yolo_version": "YOLOv11",
        "model_size": "n",
        "input_size": 640,
        "classes": ["kukorica_to"],
        "num_classes": 1,
        "file_size_mb": 11.2,
        "mAP50": 0.923,
        "mAP50_95": 0.641,
        "precision": 0.91,
        "recall": 0.88,
        "best_epoch": 87,
        "training_images": 360,
        "dataset_version": "v1"
      },
      "tags": ["kukorica", "barna_talaj", "száraz"],
      "description": "Kukoricatő számolás annotált képeken betanítva.",
      "ratings": {
        "average": 0.0,
        "count": 0
      },
      "reviews": [],
      "deleted_at": null,
      "deleted_by": null,
      "stats": {
        "usage_count": 5
      },
      "sample_images": [
        "models/samples/model_2026_01_15_1430_a1b2c3d4_sample1.jpg",
        "models/samples/model_2026_01_15_1430_a1b2c3d4_sample2.jpg",
        "models/samples/model_2026_01_15_1430_a1b2c3d4_sample3.jpg",
        "models/samples/model_2026_01_15_1430_a1b2c3d4_sample4.jpg",
        "models/samples/model_2026_01_15_1430_a1b2c3d4_sample5.jpg"
      ]
    }
  ]
}
```

---

## Állapotgép

### Modell életciklus állapotok

```
[Betanítás folyamatban]
    │
    │ Training success
    ▼
[ONNX exportálva]
    │
    │ create_metadata() hívás
    ▼
[Regisztrált] ◄──────────────────────────┐
    │                                     │
    ├── Kiválasztás (Counting) → usage_count++
    │                                     │
    ├── Láthatóság módosítás → visibility frissítés
    │                                     │
    └── Soft-delete (delete_model)        │
            │                             │
            ▼                             │
        [Kukában]                         │
            │  deleted_at = timestamp     │
            │  deleted_by = user_id       │
            │                             │
            ├── Visszaállítás ────────────┘
            │   (restore_model)
            │
            ├── Végleges törlés (hard_delete_model)
            │       │
            │       ▼
            │   [Véglegesen törölt] (fájlok + registry bejegyzés eltávolítva)
            │
            └── Automatikus purge (30 nap után)
                    │  purge_old_deleted_models()
                    ▼
                [Véglegesen törölt] (fájlok + registry bejegyzés eltávolítva)
```

> **Soft-delete:** A törlés alapértelmezetten kukába helyezi a modellt.
> A `list_models(include_deleted=False)` alapértelmezésben kiszűri a kukában lévő modelleket.
> A `get_model_by_id()` és `get_model_by_filename()` továbbra is visszaadja a kukában lévő modelleket
> (detekciós eredmények hivatkozhatnak rájuk).

---

## ModelMetadata engine (utils/model_metadata.py)

### Főbb metódusok

```python
class ModelMetadata:
    REGISTRY_PATH = "models/model_registry.json"

    def create_metadata(
        self,
        model_file: str,
        display_name: str,
        training_results: dict,
        config: dict,
        tags: List[str],
        description: str,
        visibility: str,
        owner: dict
    ) -> str:
        """
        Új modell regisztrálása.
        Returns: model_id (UUID)
        """
        model_id = str(uuid.uuid4())
        entry = {
            "id": model_id,
            "file_name": model_file,
            "display_name": display_name,
            ...
        }
        registry = self._load()
        registry["models"].append(entry)
        self._save(registry)
        return model_id

    def get_all_models(self, include_deleted: bool = False) -> List[dict]:
        """Összes modell a registry-ből (alapértelmezésben kiszűri a kukában lévőket)"""

    def get_model_by_id(self, model_id: str) -> Optional[dict]:
        """Egy modell lekérése UUID alapján"""

    def update_usage_count(self, model_id: str) -> None:
        """stats.usage_count növelése"""

    def update_visibility(self, model_id: str, visibility: str) -> None:
        """Láthatóság módosítása"""

    def delete_model(self, model_id: str, user_id: str) -> dict:
        """Soft-delete: kukába helyezés (deleted_at, deleted_by beállítás)"""

    def restore_model(self, model_id: str, user_id: str) -> bool:
        """Visszaállítás kukából (deleted_at, deleted_by törlése)"""

    def hard_delete_model(self, model_id: str, user_id: str) -> dict:
        """Végleges törlés: fájlok + registry bejegyzés eltávolítása (csak kukában lévő modellekre)"""

    def list_deleted_models(self, user_id: str = None) -> List[dict]:
        """Kukában lévő modellek listája (user_id szűréssel)"""

    def purge_old_deleted_models(self, days: int = 30) -> int:
        """30+ napja kukában lévő modellek automatikus végleges törlése"""

    def _get_model_files(self, model: dict) -> List[Path]:
        """Modellhez tartozó fájlok (ONNX, PT, samples) összegyűjtése"""

    def _load(self) -> dict:
        """JSON betöltése"""

    def _save(self, data: dict) -> None:
        """JSON mentése (atomic write)"""
```

---

## ModelManager engine (core/model_manager.py)

### ONNX betöltés és cache

```python
class ModelManager:
    def __init__(self, models_dir: str = "models"):
        self.models_dir = Path(models_dir)
        self.registry = ModelMetadata()
        self._model_cache: Dict[str, ort.InferenceSession] = {}

    def get_available_models(self, user_id: str = None) -> List[dict]:
        """
        Szűrt modell lista (láthatóság alapján).
        Szinkronizálja a registry-t a fájlrendszerrel.
        """

    def load_model(self, model_id: str) -> ort.InferenceSession:
        """
        ONNX modell betöltése és cache-elése.
        Cache key: model_id
        """
        if model_id in self._model_cache:
            return self._model_cache[model_id]

        model_info = self.registry.get_model_by_id(model_id)
        onnx_path = self.models_dir / model_info["file_name"]

        session = ort.InferenceSession(str(onnx_path))
        self._model_cache[model_id] = session
        return session

    def get_model_info(self, model_id: str) -> Optional[dict]:
        """Registry bejegyzés lekérése"""


def get_model_manager(models_dir: str = "models") -> ModelManager:
    """Singleton-szerű factory (Streamlit cache kezeli)"""
    return ModelManager(models_dir)
```

---

## Sample képek kezelése

### Generálás (Training során)

```python
def save_sample_images(self, dataset_path: str, model_file: str, num_samples: int = 5) -> List[str]:
    """
    Training adatból minta thumbnailek generálása.
    Output: models/samples/{model_file_name}_sample1-5.jpg
    """
    tile_files = list(Path(dataset_path).glob("**/*.png"))[:num_samples]
    sample_paths = []

    for i, tile in enumerate(tile_files):
        sample_name = f"{model_stem}_sample{i+1}.jpg"
        sample_path = Path("models/samples") / sample_name

        img = Image.open(tile)
        img.thumbnail((200, 200))  # Thumbnail méret
        img.save(sample_path, "JPEG", quality=85)

        sample_paths.append(str(sample_path))

    return sample_paths
```

### Megjelenítés (Counting oldalon)

```python
for model in visible_models:
    if model["sample_images"]:
        sample_path = model["sample_images"][0]
        if Path(sample_path).exists():
            st.image(sample_path, width=150)
        else:
            st.caption("Nincs sample kép")
```

---

## ONNX Inspector (utils/onnx_inspector.py)

```python
from utils.onnx_inspector import inspect_onnx_model

info = inspect_onnx_model("models/model_xyz.onnx")
# Returns: {
#   "input_shape": [1, 3, 640, 640],
#   "output_shape": [1, 84, 8400],   (YOLOv8 format)
#   "ir_version": 8,
#   "opset": 12,
#   "metadata": {...}
# }
```

**Használata:**
- Kompatibilitás ellenőrzés (ONNX Runtime verzió vs. ONNX IR verzió)
- Input méret detektálás (640x640 vs. más)
- Hibakeresés (`check_onnx_version.py`)
