# L4 – Tranzakciós és Párhuzamos – Modell Nyilvántartás (Model Registry)

**Modul:** M05
**Szint:** L4 – Tranzakciós és Párhuzamos
**Forrásdokumentumok:** TRAINING_MODULE.md, MODEL_EXPORT_GUIDE.md, INTEGRITY_CHECKER_V2_GUIDE.md

---

## Adatperzisztencia

| Adat | Fájl | Formátum | Zárolás |
|------|------|----------|---------|
| Modell metaadatok | `models/model_registry.json` | JSON | Nincs explicit lock (atomic write) |
| ONNX modellek | `models/*.onnx` | ONNX | Csak íráskor (training egyszerre fut) |
| PT weights | `models/pt_weights/*.pt` | PyTorch | Csak íráskor |
| Sample képek | `models/samples/*.jpg` | JPEG | Csak íráskor |

---

## Tranzakcionális garanták

### Registry írás atomicitása

```python
def _save(self, data: dict) -> None:
    """
    Atomic write: temp fájl → rename
    """
    tmp_path = Path(self.REGISTRY_PATH + ".tmp")
    with open(tmp_path, 'w', encoding='utf-8') as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
    tmp_path.rename(self.REGISTRY_PATH)  # Atomic on same filesystem
```

**Garancia:** Ha a folyamat a JSON írás közben omlik meg, a `.tmp` fájl marad (nem az eredeti). Az eredeti `model_registry.json` megmarad érintetlen.

**Megjegyzés:** A `rename()` csak akkor atomikus, ha forrás és cél ugyanazon a fájlrendszeren van.

---

## Párhuzamos hozzáférés

### Registry olvasás

- Csak olvasás: több felhasználó egyszerre biztonságosan olvashatja
- `@st.cache_resource` biztosítja, hogy a Streamlit egy folyamaton belül ne töltsön be kétszer

### Registry írás

| Forgatókönyv | Kockázat | Kezelés |
|--------------|----------|---------|
| Training befejezés után `create_metadata()` | Alacsony (user-szintű lock megelőzi az egyidejű training-et) | Atomic write |
| Két felhasználó egyszerre töröl | Alacsony valószínűségű | Last-write-wins |
| Usage count párhuzamos növelés | Alacsony kockázat (±1 pontatlanság) | Elfogadható |

### Streamlit cache és registry frissítés

```python
# 3_Counting.py
registry_path = Path("models/model_registry.json")
registry_mtime = registry_path.stat().st_mtime

if 'last_registry_mtime' not in st.session_state:
    st.session_state.last_registry_mtime = registry_mtime
elif st.session_state.last_registry_mtime != registry_mtime:
    load_model_manager.clear()   # Cache invalidálás
    st.session_state.last_registry_mtime = registry_mtime
```

Ez biztosítja, hogy ha a registry megváltozott (pl. új modell betanítva), a Counting oldal friss adatokat mutat.

---

## Registry integritás ellenőrzése

### Manuális szinkronizáció

```bash
# Fájlrendszer vs. Registry szinkron ellenőrzés
python -c "
from core.model_manager import get_model_manager
mm = get_model_manager()
orphaned, unregistered = mm.check_sync()
print('Sérült bejegyzések:', orphaned)
print('Regisztrálatlan fájlok:', unregistered)
"
```

### Integrity Checker (integrity_checker_v2.py)

```bash
python integrity_checker_v2.py
```

**Mit ellenőriz a modell registry-re:**
1. `model_registry.json` valid JSON-e?
2. Minden `file_name` létezik a `models/` mappában?
3. Minden `sample_images` fájl létezik?
4. Minden `pt_weights` fájl létezik?
5. UUID-ok egyediek-e?

```bash
python integrity_checker_v2.py --fix
```

**Javítás:**
- Sérült bejegyzések (fájl nélkül) törlése a registry-ből
- Regisztrálatlan .onnx fájlokhoz minimális bejegyzés létrehozása

---

## Backup és helyreállítás

### Registry backup

```bash
# Ajánlott: minden deploy/training előtt
copy models\model_registry.json backup\model_registry_%DATE%.json
```

### Teljes modellek backup

```bash
# ONNX fájlok backup (fontosabb!)
xcopy models\*.onnx backup\onnx\

# PT weights backup (transfer learning-hez)
xcopy models\pt_weights\*.pt backup\pt_weights\
```

### Helyreállítás elveszett registry esetén

Ha a `model_registry.json` elveszett, de az ONNX fájlok megmaradtak:

```bash
# 1. Üres registry inicializálás
python -c "
import json
with open('models/model_registry.json', 'w') as f:
    json.dump({'version': '2.0', 'models': []}, f)
"

# 2. ONNX fájlok regisztrálása (recover script)
python recover_failed_training.py --register-all
```

**Megjegyzés:** A visszaállított metaadatok minimálisak lesznek (mAP metrikák elvesznek, ha a `runs/` is törölt).

---

## ONNX Runtime kompatibilitás (check_onnx_version.py)

### IR verzió kompatibilitás

```bash
python check_onnx_version.py
```

**Kimenet:**
```
ONNX Runtime version: 1.20.0
ONNX IR version support: 10
Models IR versions:
  model_2026_01_15_1430_a1b2c3d4.onnx: IR version 8 → OK
  model_2025_12_31_1000_xyz.onnx: IR version 11 → INCOMPATIBLE!
```

**Megoldás inkompatibilis modellre:**
- ONNX Runtime frissítése (`pip install onnxruntime --upgrade`)
- Vagy modell újra exportálása alacsonyabb IR verzióval

---

## Ismert korlátok

| Korlát | Hatás | Javaslat |
|--------|-------|---------|
| JSON-alapú registry | Nagy modellszámnál (> 100) lassú | SQLite/PostgreSQL migráció (ROADMAP) |
| Nincs verziókövető a registry-ben | Ha registry felülíródik, nincs rollback | Git commit a models/ mappára |
| Sample képek nem törlődnek törléskor | Disk hely pazarlás | `cleanup_guide.md` alapján manuálisan |
| Rating rendszer stub (0.0) | Értékelés nem funkcionál | ROADMAP: Rating & Reviews |
