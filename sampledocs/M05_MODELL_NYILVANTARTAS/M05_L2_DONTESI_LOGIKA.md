# L2 – Döntési Logika – Modell Nyilvántartás (Model Registry)

**Modul:** M05
**Szint:** L2 – Döntési Logika
**Forrásdokumentumok:** TRAINING_MODULE.md, ONNX_CLASS_MAPPING.md, MODEL_EXPORT_GUIDE.md

---

## Validációs szabályok

### Regisztráció előtt

| Feltétel | Érvényes | Kezelés |
|----------|----------|---------|
| ONNX fájl létezik | Igen | Folytatás |
| ONNX fájl nem létezik | Nem | ValueError: "ONNX export sikertelen" |
| Megjelenítendő név | Nem üres | — |
| UUID egyediség | UUID4 (szinte garantált) | — |

### Modell törléskor

| Feltétel | Döntés |
|----------|--------|
| Saját modell | Törlés engedélyezett |
| Más tulajdonos + nem admin | Törlés tiltott |
| Admin | Bármely modell törölhető |
| Modell Counting-ban éppen használatban | Nincs ellenőrzés (ONNX cache szinten kezelt) |

---

## Döntési logika – Modell szűrés (Counting oldalon)

```python
def get_visible_models(user_id: str) -> List[dict]:
    all_models = registry["models"]

    return [m for m in all_models if
        m["visibility"] == "public"
        OR m["owner"]["user_id"] == user_id
    ]
```

### Modell rendezés megjelenítéskor

```python
# Alapértelmezett rendezés: saját modellek előre, majd dátum szerint csökkenő
visible = sorted(visible_models, key=lambda m: (
    m["owner"]["user_id"] != current_user_id,  # Saját először
    -datetime_parse(m["created_at"]).timestamp()  # Legújabb előre
))
```

---

## Döntési logika – Osztály mapping (ONNX Class Mapping)

### Osztályok és model_registry.json kapcsolata

```json
"technical": {
    "classes": ["kukorica_to"],
    "num_classes": 1
}
```

Az ONNX modellben az osztályok sorrendje a training-kori `classes.txt` alapján van beégetve. Ha a registry és az ONNX modell eltér:
- Detekció futhat, de osztálynév hibás lesz
- Megoldás: `ONNX_CLASS_MAPPING.md` szerint manuális ellenőrzés

---

## Döntési logika – Metrikanévek és értékek

### Training metrikák tárolása

| Metrika | Forrás | Tartomány | Megjelenítés |
|---------|--------|-----------|--------------|
| mAP50 | YOLO results.csv | 0.0-1.0 | Százalékos? Nem, raw |
| Precision | YOLO results.csv | 0.0-1.0 | Raw |
| Recall | YOLO results.csv | 0.0-1.0 | Raw |
| mAP50-95 | YOLO results.csv | 0.0-1.0 | Raw (kisebb szám) |
| best_epoch | YOLO training | Integer | Szám |

### Nincs metrika eset

Ha a training nem adott vissza metrikákat (pl. hiba történt):
```python
results = {
    "mAP50": 0.0,
    "precision": 0.0,
    "recall": 0.0,
    "best_epoch": 0
}
```

---

## Döntési logika – ONNX fájlnév egyediség

### Elnevezési konvenció

```python
timestamp = datetime.now().strftime("%Y_%m_%d_%H%M")
unique_suffix = uuid.uuid4().hex[:8]
model_name = f"model_{timestamp}_{unique_suffix}.onnx"
# Példa: model_2026_01_15_1430_a1b2c3d4.onnx
```

**Garantál:**
- Időbélyeg: sorrendbe rendezhető
- UUID suffix: ütközés elkerülés (még azonos időpontban is)

---

## Döntési logika – Usage count frissítés

```python
def increment_usage_count(model_id: str) -> None:
    registry = load_registry()
    for model in registry["models"]:
        IF model["id"] == model_id:
            model["stats"]["usage_count"] += 1
            break
    save_registry(registry)
```

**Megjegyzés:** Nem atomic, de kis kockázatú (usage count pontatlanság max ±1 párhuzamos esetben).

---

## ONNX Registry fájl integritás

### Szinkronizáció a fájlrendszerrel

```python
def sync_registry_with_filesystem(models_dir: str) -> None:
    """
    ONNX fájlok és registry szinkronizálása:
    - Ha registry bejegyzés van, de .onnx fájl nincs: sérült bejegyzés
    - Ha .onnx fájl van, de registry nincs: unregisztrált modell
    """
    onnx_files = set(Path(models_dir).glob("*.onnx"))
    registry_files = set(m["file_name"] for m in registry["models"])

    orphaned_registry = registry_files - onnx_files  # Registry-ben van, fájl nincs
    unregistered_files = onnx_files - registry_files  # Fájl van, registry nincs

    FOR orphaned IN orphaned_registry:
        LOG WARNING f"Sérült registry bejegyzés: {orphaned}"

    FOR unregistered IN unregistered_files:
        LOG WARNING f"Regisztrálatlan modell: {unregistered}"
```
