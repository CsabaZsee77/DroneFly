# L4 – Tranzakciós és Párhuzamos – Counting (Tőszámlálás / Inference)

**Modul:** M03
**Szint:** L4 – Tranzakciós és Párhuzamos
**Forrásdokumentumok:** DETECTION_RESULTS_MISSING_DIAGNOSIS.md, DEEPNESS_ISSUES_AND_SOLUTIONS.md

---

## Adatperzisztencia

### Detekciós eredmények tárolása

| Adat | Tárolási forma | Elérési út | Maradandóság |
|------|----------------|------------|--------------|
| Detekció metaadatok | JSON | `data/detection_results/{uuid}/metadata.json` | Állandó |
| Eredeti kép másolata | TIFF/PNG | `data/detection_results/{uuid}/input_image.*` | Állandó |
| Annotált kép | PNG | `data/detection_results/{uuid}/annotated.png` | Állandó |
| Feltöltött képek | Fájlok | `data/uploads/` | Állandó (manuális cleanup szükséges) |
| Minta képek | Fájlok | `data/samples/` | Állandó (nem törlendő) |

### Kredit tranzakció

```python
# utils/credit_manager.py
# A kredit rendszer JSON-ban tárolja az egyenleget:
# data/credits.json
{
  "users": {
    "user-uuid": {
      "balance": 100,
      "transactions": [
        {"timestamp": "...", "amount": -1, "type": "detection", "result_id": "..."}
      ]
    }
  }
}
```

**Tranzakció atomicitása:** Kredit levonás csak sikeres detekció után, JSON fájl íráskor file lock alkalmazva.

---

## Párhuzamos hozzáférés

### ONNX inferencia szálbiztossága

```python
@st.cache_resource
def load_model_manager(_registry_mtime):
    return get_model_manager(models_dir="models")
```

- Az ONNX Runtime session (`ort.InferenceSession`) thread-safe olvasáshoz
- Streamlit egy felhasználónak egy szálat futtat → nincs konkurencia probléma
- Két különböző felhasználó egyszerre futtathat detekciót (külön session-ök)

### Képfeltöltés konkurencia

Ha két felhasználó egyszerre tölt fel azonos nevű képet:
- `data/uploads/` mappa közös
- Névütközés: `ImageManager` UUID-alapú átnevezést alkalmaz

```python
class ImageManager:
    def save_upload(self, uploaded_file) -> str:
        # UUID prefix az eredeti névhez: {uuid8}_{original_name}
        safe_name = f"{uuid.uuid4().hex[:8]}_{uploaded_file.name}"
        save_path = self.upload_dir / safe_name
        ...
```

---

## Session kezelés a Counting kontextusában

### Streamlit session state lifecycle

```
Oldal betöltés
    │
    ├─ require_authentication()    # Nincs bejelentkezés → redirect Home
    │
    ├─ ONNX cache ellenőrzés      # Registry mtime-os invalidáció
    │
    ├─ ImageManager betöltés       # st.cache_resource
    │
    ├─ DetectionResultsManager     # st.cache_resource
    │
    └─ Session state init
        ├─ selected_model_id = None
        ├─ current_image = None
        └─ detection_results = None
```

### Oldal frissítés hatása

Ha a felhasználó frissíti az oldalt detekció közben:
- Streamlit session state törlődik
- Az ONNX detekció cache-elt, újrainduláskor gyorsan visszaáll
- **Az eredmény elveszhet** ha nem volt persistálva
- **A DetectionResultsManager UUID-os cache** megőrzi az eredményeket

---

## Eredmény perzisztencia (DetectionResultsManager)

### Automatikus mentés

```python
# 3_Counting.py – detekció után
result = DetectionResult(...)
result_id = detection_results_manager.save(result, image, annotated_img)
st.session_state.last_detection_id = result_id
```

### Eredmény lekérés korábbi sessionsből

```python
# Korábbi eredmények listázása
history = detection_results_manager.list_results(user_id=current_user_id)
# Ez lehetővé teszi a korábbi detekciók visszanézését
```

---

## Detekció eredmények diagnosztika

### "Detekciók hiányoznak" probléma (DETECTION_RESULTS_MISSING_DIAGNOSIS.md)

Ismert ok: A session state törlődik frissítéskor, és az eredmény nincs cache-elve.

**Diagnosztika lépések:**
```bash
# 1. Ellenőrizd a detection_results mappát
ls data/detection_results/

# 2. Ellenőrizd a legutóbbi eredményt
python -c "
from utils.detection_results_manager import DetectionResultsManager
dm = DetectionResultsManager()
results = dm.list_results()
print(results[-5:])
"

# 3. Ha üres a mappa: az eredmény nem volt persistálva
# Megoldás: futtasd újra a detekciót
```

---

## Cleanup folyamatok

### data/uploads/ méretkezelés

A feltöltött képek nem törlődnek automatikusan.

```bash
# Manuális cleanup (CLEANUP_GUIDE.md alapján)
python cleanup_old_uploads.py --days 30
```

### data/detection_results/ cleanup

```bash
python cleanup_detection_results.py --days 7
```

---

## Ismert korlátok és kockázatok

| Korlát | Hatás | Javaslat |
|--------|-------|---------|
| Nagy kép (> 50MP) memória | Out of memory crash | Sliding window kötelező |
| ONNX IO bound CPU-n | Lassú sliding window (sok ablak) | Overlap csökkentése (25% → 10%) |
| Session state elvesztése | Eredmény eltűnik | DetectionResultsManager cache segít |
| GeoTIFF > 500MB | Lassú betöltés, memória | Crop vagy lower resolution előfeldolgozás |
| Kredit rendszer JSON-based | Párhuzamos race condition lehetséges | File lock alkalmazva, kis kockázat |

---

## Export integritás

### CSV export

Az export a `utils/export.py` segítségével történik, streamed download:
```python
csv_data = export_to_csv(detections)
st.download_button(
    label="📄 CSV letöltés",
    data=csv_data,
    file_name=f"detections_{image_name}.csv",
    mime="text/csv"
)
```

### GeoJSON export koordináta pontosság

A koordináta transzformáció pontossága a GeoTIFF georeferálásának minőségétől függ:
- RTK GPS: ±1-5 cm pontosság
- GCP (Ground Control Points): ±5-30 cm pontosság
- GNSS nélkül: nem georeferált → GeoJSON export nem elérhető
