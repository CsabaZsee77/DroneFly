# L3 – Állapotgép és Engine – Training (Modell Betanítás)

**Modul:** M02
**Szint:** L3 – Állapotgép és Engine
**Forrásdokumentumok:** TRAINING_MODULE.md, TRAINING_JOB_INTEGRATION.md, MODEL_EXPORT_GUIDE.md, SESSION_NOTES_2026_01_03.md

---

## Forrásfájlok

| Fájl | Szerepkör |
|------|-----------|
| `pages/2_Training.py` | Streamlit UI – training felület |
| `core/trainer.py` | YOLOv8/v11 training wrapper |
| `utils/training_wrapper.py` | Job tracking + trainer integráció |
| `utils/training_job_manager.py` | Job lifecycle és history kezelés |
| `utils/dataset_manager.py` | Dataset előkészítés (split) |
| `utils/model_metadata.py` | Model registry kezelés |
| `utils/email_notifier.py` | Email értesítések |
| `utils/training_wrapper.py` | Összekapcsol mindent |
| `utils/training_progress.py` | Epoch-szintű progress tracking (YOLO callback + JSON) |
| `utils/annotation_qa.py` | Annotáció minőség-ellenőrzés engine (pre-training QA) |
| `recover_failed_training.py` | Helyreállítás |

---

## Adatmodell

### Training Job struktúra (data/training_jobs.json)

```json
{
  "jobs": [
    {
      "job_id": "uuid-v4",
      "user_id": "user-uuid",
      "username": "zsigmond",
      "model_name": "Kukorica Tő - YOLOv11n",
      "dataset_version": "v1",
      "status": "completed",
      "started_at": "2026-01-15T10:00:00Z",
      "completed_at": "2026-01-15T16:30:00Z",
      "config": {
        "yolo_version": "YOLOv11",
        "model_size": "n",
        "epochs": 100,
        "batch": 16,
        "imgsz": 640,
        "optimizer": "AdamW",
        "lr0": 0.01,
        "patience": 50
      },
      "results": {
        "best_epoch": 87,
        "mAP50": 0.923,
        "precision": 0.91,
        "recall": 0.88
      },
      "onnx_path": "models/model_2026_01_15_1030_a1b2c3d4.onnx",
      "notification_sent": true,
      "notification_seen": false
    }
  ]
}
```

### Fájlstruktúra (training után)

```
Dronterapia/
├── data/
│   └── training/                     # Auto-generált, felülíródik minden trainingnél
│       ├── images/
│       │   ├── train/                 # 80% képek
│       │   ├── val/                   # 15% képek
│       │   └── test/                  # 5% képek
│       ├── labels/
│       │   ├── train/
│       │   ├── val/
│       │   └── test/
│       ├── classes.txt
│       └── data.yaml
├── models/
│   ├── model_2026_01_15_1030_a1b2c3d4.onnx   # ONNX modell
│   ├── model_registry.json                   # Registry (frissítve)
│   ├── pt_weights/
│   │   └── model_2026_01_15_1030_a1b2c3d4.pt  # PyTorch súlyok
│   └── samples/
│       ├── model_2026_01_15_1030_a1b2c3d4_sample1.jpg
│       └── ...  (5 db)
└── runs/
    └── detect/
        └── train/                             # Ultralytics output
            ├── weights/
            │   ├── best.pt
            │   └── last.pt
            ├── results.csv
            ├── results.png
            ├── confusion_matrix.png
            ├── F1_curve.png
            ├── PR_curve.png
            ├── P_curve.png
            ├── R_curve.png
            ├── train_batch*.jpg
            └── val_batch*.jpg
```

---

## Állapotgép

### Training Workflow állapotok

```
[Konfigurálás]
    │
    │ "🎓 Training indítása" gomb
    ▼
[Dataset előkészítés]
    │ DatasetManager.prepare_dataset()
    │ data/training/ strukturálása
    ▼
[Training folyamatban]  ─────────────────→ [FAILED]
    │ YOLOTrainer.train()                      │
    │ core/trainer.py                          │ Hiba: email + job update
    │ (blokkol a Streamlit threadben)          │
    ▼                                          │
[Sample képek mentése]                         │
    │ trainer.save_sample_images()             │
    ▼                                          │
[PT weights mentése]                           │
    │ shutil.copy(best.pt → pt_weights/)       │
    ▼                                          │
[ONNX export]                                  │
    │ trainer.export_to_onnx()                 │
    ▼                                          │
[Metadata mentés]                              │
    │ model_metadata.create_metadata()         │
    ▼                                          │
[COMPLETED] ←──────────────────────────────────┘
    │
    │ Email értesítés (ha beállítva)
    │ Web notification flag beállítás
    ▼
[Eredmények megjelenítése]
```

---

## YOLOTrainer engine (core/trainer.py)

### Főbb metódusok

```python
class YOLOTrainer:
    def __init__(self, data_yaml: str):
        """
        Args:
            data_yaml: YOLO adatstruktúra konfigurációs fájl elérési útja
        """

    def train(
        self,
        yolo_version: str = "YOLOv11",
        model_size: str = "n",
        annotation_method: str = "detection",  # "detection" vagy "segmentation"
        epochs: int = 100,
        batch: int = 16,
        imgsz: int = 640,
        device: str = "cpu",
        optimizer: str = "AdamW",
        lr0: float = 0.01,
        patience: int = 50,
        workers: int = 4,
        cache: bool = False,
        augment: bool = True,
        mosaic: float = 1.0,
        mixup: float = 0.0,
        flipud: float = 0.0,
        fliplr: float = 0.5,
        hsv_h: float = 0.015,
        hsv_s: float = 0.7,
        hsv_v: float = 0.4,
        degrees: float = 0.0,
        translate: float = 0.1,
        scale: float = 0.5,
        weight_decay: float = 0.0005,
        **kwargs
    ) -> dict:
        """
        YOLO training végrehajtása.
        Returns: training_results dict (metrics, paths)
        """

    def export_to_onnx(self, model_name: str, output_dir: str = "models") -> str:
        """
        best.pt → ONNX konverzió.
        Returns: ONNX fájl elérési útja
        """

    def save_sample_images(self, num_samples: int = 5) -> List[str]:
        """
        Training adatból minta thumbnailek mentése.
        Returns: sample fájlútvonalak listája
        """
```

### Belső model loader logika

```python
# YOLOv8 vagy YOLOv11 pretrained súlyok letöltése
# annotation_method alapján: detection → standard, segmentation → -seg suffix
if yolo_version == "YOLOv11":
    base = f"yolo11{model_size}"
else:
    base = f"yolov8{model_size}"

suffix = "-seg" if annotation_method == "segmentation" else ""
model_code = f"{base}{suffix}.pt"
# Pl.: "yolo11n.pt" (detection) vagy "yolo11n-seg.pt" (segmentation)

# Ultralytics automatikusan letölti ha nincs lokálisan
model = YOLO(model_code)
self.annotation_method = annotation_method  # _collect_results() számára

# Projekt mappa automatikus beállítás
if annotation_method == "segmentation" and project == "runs/detect":
    project = "runs/segment"  # Szegmentáció: runs/segment/{name}/
```

### Szegmentációs training metrikák (_collect_results visszatérési értéke)

**Detection modelleknél** (`annotation_method == "detection"`):
```python
{
    "best_epoch": int,
    "metrics/mAP50(B)": float,     # Bbox mAP@50
    "metrics/mAP50-95(B)": float,  # Bbox mAP@50-95
    "metrics/precision(B)": float,
    "metrics/recall(B)": float,
    "train/box_loss": float,
    "train/cls_loss": float,
    "train/dfl_loss": float,
    "val/box_loss": float,
    "val/cls_loss": float,
    "val/dfl_loss": float,
    "files": {...}
}
```

**Segmentation modelleknél** (`annotation_method == "segmentation"`), a fenti mezőkön FELÜL:
```python
{
    "metrics/mAP50(M)": float,     # Maszk mAP@50
    "metrics/mAP50-95(M)": float,  # Maszk mAP@50-95
    "train/seg_loss": float,       # Szegmentációs veszteség (training)
    "val/seg_loss": float,         # Szegmentációs veszteség (validation)
}
```

---

## TrainingJobManager engine (utils/training_job_manager.py)

### Főbb metódusok

```python
class TrainingJobManager:
    def __init__(self, jobs_file: str = "data/training_jobs.json"):
        ...

    def create_job(self, user_id, username, model_name, dataset_version, config) -> str:
        """Új job létrehozása 'running' státusszal. Returns: job_id"""

    def update_job(self, job_id, status, results=None, onnx_path=None) -> None:
        """Job státusz frissítése"""

    def get_running_job(self, user_id) -> Optional[dict]:
        """Visszaadja a futó jobot (ha van) a felhasználónak"""

    def get_job_history(self, user_id, limit=10) -> List[dict]:
        """Job history lekérése"""

    def mark_notification_seen(self, job_id) -> None:
        """Web notification megjelölt olvasottként"""

    def get_unseen_notifications(self, user_id) -> List[dict]:
        """Olvasatlan értesítések lekérése"""
```

---

## EmailNotifier engine (utils/email_notifier.py)

### Konfiguráció (.env fájl)

```bash
SMTP_USERNAME=sajat_gmail@gmail.com
SMTP_PASSWORD=abcd efgh ijkl mnop   # Gmail App Password (16 karakter)
EMAIL_ENABLED=true
ADMIN_EMAIL=zsigmond.csaba@logpilot.hu
```

### Email küldés folyamata

```python
class EmailNotifier:
    def send_training_completion(self, user_email, model_name, results) -> bool:
        """
        Gmail SMTP-n keresztül küld HTML emailt.
        Returns: True ha sikeres, False ha hiba
        """
        with smtplib.SMTP_SSL("smtp.gmail.com", 465) as server:
            server.login(SMTP_USERNAME, SMTP_PASSWORD)
            server.sendmail(from_addr, user_email, msg.as_string())

    def send_new_user_registration(self, admin_email, new_username, ...) -> bool:
        """Admin értesítés új regisztrációkor"""
```

---

## Streamlit Session State kezelés (2_Training.py)

| Kulcs | Típus | Tartalma |
|-------|-------|----------|
| `training_completed` | bool | Training befejezve flag |
| `training_results` | dict | Training metrikák |
| `training_config` | dict | Utolsó training konfiguráció |
| `selected_dataset` | str | Kiválasztott version ID |
| `job_id` | str | Aktuális job ID |

### Session state reset ("Új Training" gomb)

```python
for key in ['training_completed', 'training_results', 'training_config', 'job_id']:
    if key in st.session_state:
        del st.session_state[key]
st.rerun()
```

---

## Teljesítmény és erőforrás szempontok

| Tényező | Hatás | Optimalizáció |
|---------|-------|---------------|
| Epoch count | Lineárisan arányos az idővel | Early stopping csökkenti |
| Image size | Négyzetes memória igény | 416 CPU-n, 640 GPU-n |
| Batch size | RAM igény | 16 CPU-n biztonságos |
| Workers | CPU magok kihasználása | 4-6 tipikusan optimális |
| Cache | RAM in teljes dataset | Csak ha > 16GB RAM |
| Model size (n→x) | 10x időkülönbség | Nano az ajánlott CPU-n |

### Várható erőforrás igények (CPU, YOLOv11n)

| Dataset méret | Epoch | Várható idő | RAM igény |
|---------------|-------|-------------|-----------|
| 50 kép | 10 | ~5 perc | ~2GB |
| 100 kép | 50 | ~30 perc | ~3GB |
| 200 kép | 100 | ~2-3 óra | ~4GB |
| 500 kép | 150 | ~6-10 óra | ~6GB |

---

## Training Progress Tracking engine (utils/training_progress.py)

Epoch-szintű training előrehaladás rögzítése és lekérdezése YOLO callback-eken keresztül.

### Adatmodell

**Tárolás:** `data/training_progress/{job_id}.json` (ideiglenes, training végén törlődik)

```json
{
  "job_id": "uuid...",
  "current_epoch": 15,
  "total_epochs": 100,
  "progress_pct": 15.0,
  "elapsed_seconds": 450.3,
  "eta_seconds": 2551.7,
  "avg_epoch_seconds": 30.0,
  "metrics": {
    "metrics/mAP50(B)": 0.4523,
    "metrics/precision(B)": 0.6812,
    "metrics/recall(B)": 0.5234
  },
  "loss": {
    "box_loss": 1.2345,
    "cls_loss": 0.8765,
    "dfl_loss": 1.0234
  },
  "lr": {"pg0": 0.00098},
  "updated_at": 1711200000.0
}
```

### Callback integráció

```python
# core/trainer.py — train() metódusban
if job_id:
    callbacks = create_yolo_callbacks(job_id, epochs)
    model.add_callback('on_train_start', callbacks['on_train_start'])
    model.add_callback('on_train_epoch_end', callbacks['on_train_epoch_end'])
    model.add_callback('on_train_end', callbacks['on_train_end'])
```

### API

| Függvény | Leírás |
|----------|--------|
| `create_yolo_callbacks(job_id, total_epochs)` | Callback dict factory |
| `write_progress(job_id, data)` | Progress JSON írás |
| `read_progress(job_id)` | Progress JSON olvasás (UI polling) |
| `cleanup_progress(job_id)` | Progress fájl törlése (training vége) |

### Lifecycle

```
training_wrapper.run_training_job()
  → trainer.train(job_id=job_id)
    → on_train_start: üres progress fájl létrehozása
    → on_train_epoch_end (×N): progress frissítés (metrikák, loss, ETA)
    → on_train_end: —
  → completed/failed → cleanup_progress(job_id)
```

### UI megjelenítés (2_Training.py)

A Training oldal `read_progress(job_id)` hívással olvassa a JSON-t, és megjeleníti:

| UI elem | Adat |
|---------|------|
| `st.progress()` | `progress_pct / 100` |
| `st.metric` × 4 | Epoch, eltelt idő, ETA, átlag epoch idő |
| Expander: élő metrikák | mAP50, precision, recall, loss értékek |

Ha nincs még epoch adat (`current_epoch == 0`), a régi egyszerű nézet jelenik meg (visszafele kompatibilis).
