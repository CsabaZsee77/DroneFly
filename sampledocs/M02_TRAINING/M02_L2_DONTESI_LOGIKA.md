# L2 – Döntési Logika – Training (Modell Betanítás)

**Modul:** M02
**Szint:** L2 – Döntési Logika
**Forrásdokumentumok:** TRAINING_MODULE.md, TRAINING_JOB_INTEGRATION.md, TRAINING_INTEGRATION_PHASE3.md, ONNX_CLASS_MAPPING.md

---

## Validációs szabályok

### Training indítása előtt

| Feltétel | Érvényes | Érvénytelen | Kezelés |
|----------|----------|-------------|---------|
| Dataset kiválasztva | Van training version | Nincs | "Nincs elérhető dataset!" |
| Annotált csempék száma | ≥ 10 | < 10 | "Legalább 10 annotált csempe szükséges" |
| Train + Val ≤ 100% | OK | > 100% | "Dataset split összege nem lehet több mint 100%!" |
| Futó job | Nincs | Van | "Már fut egy training, várj!" |
| Modell neve | Nem üres | Üres | Hibaüzenet |

### Paraméter korlátok (slider-ek)

| Paraméter | Min | Max | Alapértelmezett |
|-----------|-----|-----|-----------------|
| Epochs | 10 | 300 | 100 |
| Batch size | 4 | 32 (4 értékes) | 16 |
| Image size | 320 | 640 (értékes: 320/416/512/640) | 640 |
| Learning rate | 0.001 | 0.1 | 0.01 |
| Patience | 10 | 100 | 50 |
| Workers | 1 | 8 | 4 |
| Train ratio | 50% | 90% | 80% |
| Val ratio | 5% | 30% | 15% |
| Test ratio | automatikus | — | 5% |

---

## Döntési logika – Modell méret választás

| Méret | Kód | Sebesség | Pontosság | CPU ajánlott |
|-------|-----|----------|-----------|--------------|
| Nano | n | Nagyon gyors | Alacsony | Igen (első modell) |
| Small | s | Gyors | Jó | Igen (éles) |
| Medium | m | Közepes | Jobb | CPU-n lassú |
| Large | l | Lassú | Nagyon jó | Nem ajánlott CPU-n |
| XLarge | x | Nagyon lassú | Maximum | Nem ajánlott CPU-n |

**Döntési szabály CPU-n:**
```
IF training_purpose == "quick_test":
    RETURN "n"
IF training_purpose == "production":
    RETURN "s"  (nano ha sok kép, small ha elég idő van)
```

---

## Döntési logika – YOLO verzió választás

| Verzió | Státusz | Ajánlott |
|--------|---------|---------|
| YOLOv8 | Stabil, régebbi | Meglévő munkáknál |
| YOLOv11 | Legújabb | Minden új modellnél |

**Megjegyzés:** A YOLOv11 általában jobb eredményt ad azonos méretkategóriában.

---

## Döntési logika – Early stopping

```
FOR each epoch:
    train_model()
    evaluate(val_set)

    IF val_mAP50 > best_val_mAP50:
        best_val_mAP50 = val_mAP50
        save_best_weights()
        patience_counter = 0
    ELSE:
        patience_counter += 1

    IF patience_counter >= patience_threshold:
        STOP training
        LOG "Early stopping at epoch {epoch}"
```

**Ajánlott patience értékek:**
- Gyors teszt: 20 epoch
- Éles betanítás: 50 epoch
- Kis dataset (< 50 kép): 30 epoch

---

## Döntési logika – Augmentation beállítások

### Mikor kapcsold be az augmentációt?

| Helyzet | Döntés |
|---------|--------|
| < 50 annotált kép | Igen, augmentation be |
| 50-200 kép, változatos körülmények | Mosaic + Flip |
| > 200 kép, egységes körülmények | Mosaic elegendő |
| Overfitting (val loss nő, train loss csökken) | Augmentation fokozása |
| Underfitting (mindkét loss magas) | Augmentation csökkentése |

### Augmentation paraméterek döntési táblája

| Paraméter | Alacsony (0.0-0.3) | Közepes (0.3-0.7) | Magas (0.7-1.0) |
|-----------|---------------------|-------------------|-----------------|
| Mosaic | Kevés összefűzés | Normál | Sok összetett kép |
| MixUp | Nem ajánlott | Kis segítség | Nem ajánlott |
| Flip H | Drónképeknél OK | Drónképeknél OK | Drónképeknél OK |
| Flip V | Drónképeknél OK | — | — |
| Rotate | 0-10° | — | Nem ajánlott |
| Scale | 0.1-0.3 | — | Változatos méret |

---

## Döntési logika – Dataset felosztás

### Train/Val/Test arányok szabályai

```
FUNCTION validate_split(train_ratio, val_ratio):
    test_ratio = 1.0 - train_ratio - val_ratio

    IF test_ratio < 0:
        RETURN ERROR "Split összege > 100%"

    IF train_ratio < 0.5:
        SHOW WARNING "Kevés training adat"

    IF val_ratio < 0.05:
        SHOW WARNING "Kevés validációs adat"

    RETURN OK
```

### Minimum adat követelmények osztályonként

| Osztályok száma | Ajánlott min. annotált csempe |
|-----------------|-------------------------------|
| 1 osztály | 10 (absolute min), 50+ (ajánlott) |
| 2-3 osztály | 20+, 100+ ajánlott |
| 4+ osztály | 50+, 200+ ajánlott |

---

## Döntési logika – Job management

### Concurrent training lock szabálya

```
FUNCTION can_start_training(user_id):
    jobs = load_training_jobs()
    user_jobs = [j for j in jobs if j.user_id == user_id]
    running_jobs = [j for j in user_jobs if j.status == "running"]

    IF len(running_jobs) > 0:
        RETURN False, running_jobs[0]  # Mutasd a futó jobot
    ELSE:
        RETURN True, None
```

**Megjegyzés:** A lock user-szintű, nem globális. Két különböző felhasználó egyszerre is taníthat.

### Job state transitions

```
[Nincs] → running → completed
                  → failed
```

**Automatikus cleanup:** Ha egy "running" job > 24 óra régiben van és nincs aktív process, az integrity checker "stale_running"-nek jelöli.

---

## Döntési logika – ONNX export

### best.pt → ONNX konverzió

```
FUNCTION export_to_onnx(model_size, imgsz):
    model = YOLO("runs/detect/train/weights/best.pt")

    export_path = model.export(
        format="onnx",
        imgsz=imgsz,      # Training-ban használt méret
        opset=12,          # ONNX Runtime kompatibilis
        simplify=True      # ONNX Simplifier futtatás
    )

    # Elnevezés: model_{dátum}_{idő}_{uuid8}.onnx
    new_name = f"model_{datetime.now():%Y_%m_%d_%H%M}_{uuid[:8]}.onnx"
    shutil.move(export_path, f"models/{new_name}")

    RETURN new_name
```

### ONNX Class mapping (ONNX_CLASS_MAPPING.md)

Az ONNX modellben az osztályok sorrendje a training-kori `classes.txt` sorrendjét követi:
```
0: kukorica_to
1: (ha lenne 2. osztály)
...
```

**Fontos:** Ha az osztályok sorrendje változik, a modell újabb training nélkül inkompatibilis!

---

## Döntési logika – Email értesítés

```
FUNCTION send_notification(job_result, user_id):
    user = user_manager.get_user(user_id)

    IF user.email IS NULL OR user.email == "":
        SKIP email (web notification csak)

    IF ENV["EMAIL_ENABLED"] != "true":
        SKIP email

    IF job_result.status == "completed":
        send_success_email(user.email, job_result)
    ELIF job_result.status == "failed":
        send_failure_email(user.email, job_result)
```

---

## Hibaágak és kezelésük

### "Memory error" (RAM kifogyás)

**Detektálás:** Python MemoryError exception
**Döntés:** Job failed állapotba kerül
**Javaslat:** Csökkentsd batch size-t, kapcsold ki cache-t, csökkentsd image size-t

### "ONNX export sikertelen"

**Detektálás:** Export exception
**Döntés:** Training completed (best.pt megmarad), de ONNX export hiba
**Kezelés:** `MODEL_EXPORT_GUIDE.md` szerint manuális export lehetséges

### "Training lefagy / nem válaszol"

**Detektálás:** Nincs (Streamlit nem jelez)
**Döntés:** CPU-n normális a lassúság; ne zárd be az oldalt
**Kezelés:** Ha valóban lefagyott: `recover_failed_training.py` script
