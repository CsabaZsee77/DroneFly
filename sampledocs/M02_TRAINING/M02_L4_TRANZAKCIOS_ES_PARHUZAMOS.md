# L4 – Tranzakciós és Párhuzamos – Training (Modell Betanítás)

**Modul:** M02
**Szint:** L4 – Tranzakciós és Párhuzamos
**Forrásdokumentumok:** TRAINING_JOB_INTEGRATION.md, EMLEKEZTETO_TRAINING_JOB_MANAGEMENT.md, recover_failed_training.py

---

## Adatperzisztencia

### Training eredmények tárolása

| Adat | Tárolási forma | Elérési út | Maradandóság |
|------|----------------|------------|--------------|
| Training job history | JSON | `data/training_jobs.json` | Állandó |
| Training output (grafikon, stb.) | Fájlok | `runs/detect/train/` | **Felülíródik** következő training-nél |
| Best PT weights (másolat) | PT fájl | `models/pt_weights/*.pt` | Állandó |
| ONNX modell | ONNX fájl | `models/*.onnx` | Állandó |
| Modell metaadatok | JSON | `models/model_registry.json` | Állandó |
| Sample képek | JPG | `models/samples/*.jpg` | Állandó |

> **FONTOS:** A `runs/detect/train/` Ultralytics által felülírt minden training-nél!
> Az eredmények mentéséről a TrainingWrapper gondoskodik (PT másolat, ONNX export, sample képek).

---

## Tranzakció szimuláció

A training egy hosszú, blokkolt műveletsort jelent. Az egyes fázisok atomikusak szempontjából:

### Commit pontok

```
PHASE 1: Dataset előkészítés
    → data/training/ létrehozva
    COMMIT: Ha ez kész, training indítható

PHASE 2: Training (Ultralytics)
    → runs/detect/train/ feltöltve
    COMMIT: Ha best.pt létezik, training sikeres

PHASE 3: Post-processing (mind kötelező, sorban)
    3a. sample képek mentése → models/samples/
    3b. PT weights másolása → models/pt_weights/
    3c. ONNX export → models/*.onnx
    3d. Metadata mentés → models/model_registry.json frissítés
    3e. Job update → data/training_jobs.json frissítés
    COMMIT: Csak ha 3e-ig minden sikeres → "completed"
```

### Részleges sikertelenség kezelése

| Hiba helye | Következmény | Helyreállítás |
|------------|--------------|---------------|
| 3a előtt | Nincs sample kép | recover_failed_training.py |
| 3b előtt | Nincs PT backup | recover_failed_training.py |
| 3c sikertelen | Nincs ONNX | MODEL_EXPORT_GUIDE.md szerint kézi export |
| 3d sikertelen | Nincs registry bejegyzés | recover_failed_training.py --register |
| 3e sikertelen | Job "running"-ban ragad | Kézi job cleanup |

---

## Párhuzamos hozzáférés — Training Queue rendszer (v0.8.0)

### Architektúra

```
TrainingJobManager (utils/training_job_manager.py)
  ├─ create_job() → (job_id, 'running' | 'queued')
  ├─ promote_next_queued_job() → atomi promóció filelock alatt
  ├─ cancel_queued_job() → user általi törlés
  └─ mark_stale_running_jobs() → startup recovery

TrainingQueueRunner (utils/training_queue_runner.py) — Singleton
  ├─ start_training(job_id) → daemon thread indítás
  ├─ _on_training_complete() → queue promóció + auto-start
  └─ recover_queued_jobs() → startup: queued → running
```

### Per-user queue logika

```
FUNCTION create_job(user_id, ...):
    WITH file_lock(jobs_file):
        IF user has running OR queued job:
            RAISE ValueError  # Per-user: max 1 aktív job

        running_count = count_jobs(status='running')
        IF running_count < MAX_CONCURRENT_TRAININGS:
            status = 'running'
        ELSE:
            status = 'queued'  # Sorba kerül

        save_job(status, train_kwargs)
        RETURN (job_id, status)
```

### Queue promóció (automatikus)

```
FUNCTION promote_next_queued_job():
    WITH file_lock(jobs_file):  # Atomi művelet
        IF count_running < MAX_CONCURRENT_TRAININGS:
            oldest_queued = get_queued_jobs().first()  # FIFO
            IF oldest_queued:
                oldest_queued.status = 'running'
                oldest_queued.started_at = now()
                RETURN oldest_queued
        RETURN None
```

A promóció automatikusan történik, amikor egy training szál befejeződik (`_on_training_complete()`).

### Háttérszál végrehajtás

- Training-ek `threading.Thread(daemon=True)` szálakban futnak
- A Streamlit UI nem blokkolódik — a felhasználó elhagyhatja az oldalt
- Minden job izolált könyvtárba ír: `runs/detect/job_{job_id[:8]}/`
- Szerver restart esetén daemon szálak meghalnak → `mark_stale_running_jobs()` kezeli

### Globális concurrency limit

```
MAX_CONCURRENT_TRAININGS = int(os.environ.get("MAX_CONCURRENT_TRAININGS", "2"))
```

**Garancia:** Egy felhasználónak egyszerre max 1 aktív (running/queued) job-ja lehet.
**Rendszer szinten:** Max `MAX_CONCURRENT_TRAININGS` párhuzamos training fut, a többi sorban áll.

### Job izolálás

Minden training job saját könyvtárba ír:
- Training output: `runs/detect/job_{job_id[:8]}/`
- PT weights másolat: `models/pt_weights/model_{timestamp}_{uuid}.pt`
- ONNX export: `models/model_{timestamp}_{uuid}.onnx`

Ezért párhuzamos training-ek **nem ütköznek** a fájlrendszeren.

---

## Session state és browser persistence

### Training session elvesztése (böngésző refresh)

Ha a felhasználó frissíti az oldalt training közben:
- Streamlit session state törlődik
- A training **folytatódik** (Streamlit szerveroldali szálban fut)
- A `st.session_state.job_id` elvész → web notification nem látható
- Adat: a training lefut, ONNX modell meglesz
- UI: a "Training folyamatban" állapot elvész

**Következmény:** A training végeredménye meglesz, de a web notification tájékoztatás nem jelenik meg.
**Megoldás:** Email értesítés beállítása!

### Web notification mechanizmus

```python
# Oldal betöltésekor (2_Training.py)
notifications = job_manager.get_unseen_notifications(user_id)

FOR notif IN notifications:
    IF notif.status == "completed":
        st.balloons()
        st.success(f"🎉 Training befejezve! Model: {notif.model_name}")
    ELIF notif.status == "failed":
        st.error(f"❌ Training hiba! {notif.error}")
    job_manager.mark_notification_seen(notif.job_id)
```

---

## Helyreállítás (recover_failed_training.py)

### Mikor használd?

- A training lefutott (best.pt létezik a `runs/detect/train/weights/` mappában)
- De valami hiba miatt nem jött létre az ONNX modell vagy a registry bejegyzés
- A job "running" állapotban ragadt

### Futtatás

```bash
python recover_failed_training.py
```

**Mit csinál:**
1. Megkeresi a legutóbbi `runs/detect/train/weights/best.pt`-t
2. Ha nincs registry bejegyzés: ONNX exportot végez
3. Minimális metaadattal regisztrálja a modellt
4. Job állapotát "failed" vagy "completed"-re állítja

---

## Backup és helyreállítás

### Fontos fájlok backup-ja

```bash
# Modellek backup
xcopy models\ backup\models\ /E /I /Y

# Jobs history backup
copy data\training_jobs.json backup\training_jobs.json
```

### runs/ mappa kezelése

A `runs/` mappa **nem backup-olandó** – mindig felülíródik.
A `models/pt_weights/` és `models/*.onnx` a valódi backup-ok.

---

## Ismert korlátok és kockázatok

| Korlát | Hatás | Javaslat |
|--------|-------|---------|
| Daemon szálak szerver restart-nál | Futó training-ek elvesznek | Startup recovery automatikusan kezeli: stale → failed, queued → újraindul |
| CPU terhelés N párhuzamos training-nél | Lassabb training per job | `MAX_CONCURRENT_TRAININGS=2` alapértelmezett, szerver kapacitás szerint állítható |
| Nincs progress bar training közben | Nem látod az epoch előrehaladást | Email értesítés beállítása |
| runs/ nagy mérete | Disk hely foglalás | cleanup_guide.md szerint runs/ törölhető ONNX export után |
| train_kwargs a JSON-ban | Nagyobb jobs fájl méret | `cleanup_old_jobs(30)` rendszeres futtatása |
