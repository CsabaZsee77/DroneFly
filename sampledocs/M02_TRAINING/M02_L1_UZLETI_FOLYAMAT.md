# L1 – Üzleti Folyamat – Training (Modell Betanítás)

**Modul:** M02
**Szint:** L1 – Üzleti Folyamat
**Forrásdokumentumok:** TRAINING_MODULE.md, FELHASZNALOI_UTMUTATO.md, TRAINING_JOB_INTEGRATION.md, GYORS_UTMUTATO.md, MODEL_EXPORT_GUIDE.md

---

## Fő cél

Annotált képdataset-ből YOLOv8 vagy YOLOv11 gépi tanulási modell betanítása, ONNX formátumú export és a modell regisztrálása a modell nyilvántartásba. A training eredménye: egy betanított, azonnal Counting oldalon használható ONNX modell teljes metaadatával.

---

## Szereplők

| Szereplő | Szerepkör |
|----------|-----------|
| **Felhasználó** | Konfigurálja a training paramétereket, elindítja, nyomon követi |
| **Rendszer (Streamlit)** | Training folyamatot vezérli, job-ot nyilvántart |
| **YOLOTrainer (core/trainer.py)** | Tényleges ML training végrehajtása |
| **TrainingJobManager** | Párhuzamos training lockot kezel, history tárol |
| **EmailNotifier** | Befejezéskor email küld (ha beállítva) |

---

## Mi indítja a folyamatot

1. **Training Version elérhető:** A felhasználó az Annotation oldalon létrehozott legalább 1 training verziót
2. **Felhasználó a Training oldalon van:** Dataset kiválasztása után megadja a konfiguráció
3. **"🎓 Training indítása" gomb:** Csak akkor aktív, ha nincs már futó job és van dataset

---

## Képernyő sorrend (webes felület – 2_Training.py)

```
┌─────────────────────────────────────────────────────────────┐
│ 1. Dataset Választás                                        │
│    Dropdown: "Válassz datasetet a traininghez"              │
│    Statisztikák: Total tiles, Annotált, Objektumok, Osztályok│
└─────────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────────┐
│ 2. Training Konfiguráció                                    │
│    Alap: YOLO verzió, méret, epochs, batch, image size      │
│    Optimizer & LR: SGD/Adam/AdamW, LR, patience             │
│    Haladó (expander): augmentation, transzformációk, CPU   │
└─────────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────────┐
│ 3. Dataset Felosztás (slider-ek)                            │
│    Train % | Val % | Test % (automatikus)                   │
└─────────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────────┐
│ 4. Modell Információk                                       │
│    Név, Láthatóság, Kulcsszavak, Leírás                     │
└─────────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────────┐
│ 5. Training Indítása / Futó Job Jelzés                      │
│    [🎓 Training indítása] VAGY [⚠️ Már fut egy training]    │
└─────────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────────┐
│ 6. Training Eredmények (betanítás után)                     │
│    Metrikák, Grafikonok, Sample képek, ONNX elérési út      │
└─────────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────────┐
│ 7. Létező Modellek (ONNX lista)                             │
└─────────────────────────────────────────────────────────────┘
```

---

## Üzleti események (végponttól végpontig)

### 1. Dataset választás

- Felhasználó kiválasztja a training version-t a dropdownból
- Rendszer megmutatja a statisztikákat:
  - Összes tile, annotált tile-ok, objektumok, osztályok
- Ellenőrzés: legalább 10 annotált csempe kell a folytatáshoz

### 2. Training konfiguráció megadása

**Alap beállítások:**
- YOLO verzió: YOLOv8 vagy YOLOv11 (ajánlott: YOLOv11)
- Modell méret: n/s/m/l/x (nano a leggyorsabb CPU-n)
- Epochs: 10-300 (ajánlott CPU: 100-150)
- Batch size: 4/8/16/32 (ajánlott CPU: 16)
- Kép méret: 320/416/512/640 px (ajánlott: 640)

**Optimizer & LR:**
- Optimizer: SGD / Adam / AdamW (ajánlott: AdamW)
- Learning rate: 0.001-0.1 (alapértelmezett: 0.01)
- Early stopping patience: 10-100 (alapértelmezett: 50)

**Haladó beállítások (opcionális):**
- Data augmentation (mosaic, mixup, flip-ek)
- Szín transzformációk (HSV hue/saturation/value)
- Geometriai transzformációk (rotate, translate, scale)
- Regularizáció (weight decay)
- CPU optimalizáció (workers 1-8, cache on/off)

### 3. Modell információk megadása

- **Modell neve:** Megjelenítendő neve (pl. "Kukorica Tő - YOLOv11n")
- **Láthatóság:** 🔒 Privát (csak a felhasználóé) / 🌍 Nyilvános
- **Kulcsszavak:** pl. "kukorica, barna_talaj, száraz"
- **Leírás:** Rövid szöveges leírás

### 4. Training indítása

- Rendszer ellenőrzi: fut-e már training job?
  - Ha igen → Hibaüzenet + "📜 Job history" gomb
  - Ha nem → Training megkezdődik
- **Training fázisok:**
  1. "📦 Dataset előkészítése..." – train/val/test szétválasztás, data.yaml
  2. "🏋️ Training folyamatban..." – YOLOv11 tényleges betanítás
  3. "📸 Sample képek mentése..." – 5 db minta thumbnail
  4. "💾 PT weights mentése..." – PyTorch súlyok másolása
  5. "📦 ONNX export..." – inference-kész modell létrehozása
  6. "💾 Model metadata mentése..." – registry frissítés
  7. 🎉 Lufik! – Sikeres befejezés

### 5. Értesítések

- **Web notification:** Pop-up oldal visszaváltáskor
  - Siker: "Training befejezve! Model: {név}" + balloon animáció
  - Hiba: "Training hiba! Model: {név}" + hibaüzenet
- **Email notification** (ha beállítva):
  - Gmail SMTP-n keresztül küld
  - Tartalmazza: modell neve, dataset, dátum, mAP50, Precision, Recall

### 6. Eredmények megtekintése

- **Metrikák:** Best Epoch, mAP50, Precision, Recall
- **Grafikonok:** results.png, confusion_matrix.png, F1/PR/P curve-ök
- **Batch példák:** train_batch*.jpg, val_batch*.jpg
- **Sample képek:** 5 db training adatból
- **ONNX modell elérési út** (pl. `models/model_2026_01_15_1430_a1b2.onnx`)

### 7. Új training indítása

- "🔄 Új Training Indítása" gomb → session state visszaállítás
- Lehetővé teszi újabb modell betanítását

---

## Állapotok (üzleti nézet)

### Training Job állapotok

| Állapot | Jelentés |
|---------|----------|
| Nincs futó job | Training indítható |
| running | Training folyamatban (blokkolja az újabb indítást) |
| completed | Sikeresen befejezve |
| failed | Hiba történt |

### Modell láthatóság állapotok

| Állapot | Látható |
|---------|---------|
| private | Csak a tulajdonos felhasználó |
| public | Minden bejelentkezett felhasználó |

---

## Végállapotok

1. **Sikeres training:** ONNX modell létrehozva, metaadatok regisztrálva → Counting oldalon elérhető
2. **Sikertelen training:** Job "failed" állapotba kerül, email értesítés küldve
3. **Megszakított training:** Böngésző bezárás esetén a training folytatódik (background process)
4. **Ütköző job:** Egy felhasználónak csak egy training futhat egyszerre

---

## Paraméter ajánlások

| Paraméter | Gyors teszt | Éles betanítás |
|-----------|-------------|----------------|
| YOLO verzió | YOLOv11 | YOLOv11 |
| Modell méret | n (nano) | n vagy s |
| Epochs | 10-20 | 100-150 |
| Batch size | 8 | 16 |
| Image size | 416 | 640 |
| Optimizer | AdamW | AdamW |
| LR | 0.01 | 0.01 |
| Patience | 20 | 50 |
| Workers | 4 | 4-6 |
| Cache | False | False |

**Várható idő:**
- Gyors teszt: 10-30 perc (10 epoch, 100 kép, CPU)
- Éles betanítás: 4-12 óra (100 epoch, 500 kép, CPU)

---

## Kapcsolódó modulok

- ← **M01 Annotation:** A training dataset az annotációból érkezik
- → **M05 Modell Nyilvántartás:** A betanított modell ide kerül regisztrálásra
- → **M03 Counting:** A betanított ONNX modell ott lesz elérhető
