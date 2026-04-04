# L4 – Tranzakciós és Párhuzamos Kezelés – ODM Ortomozaik Pipeline

**Modul:** M08
**Szint:** L4 – Tranzakciós és Párhuzamos Kezelés
**Verzió:** v1.0.0
**Létrehozva:** 2026-03-16
**Státusz:** ✅ Implementálva (2026-03-18)

---

## 1. Adatperzisztencia

### Fájlrendszer struktúra

```
data/users/{user_id}/
  odm_jobs.json                          ← job nyilvántartás
  odm_uploads/{job_id}/                  ← nyers képek (temp)
    IMG_0001.JPG
    IMG_0002.JPG
    ...
  uploads/{geotiff_filename}.tif         ← kész ortomozaik (végleges)
```

A nyers képek (`odm_uploads/`) ideiglenes tárhely:
- Sikeres feldolgozás után 24 óra múlva törlődnek
- Sikertelen feldolgozás után 72 óra múlva törlődnek
- A kész GeoTIFF (`uploads/`) a felhasználó tárhely kvótájába számít

### odm_jobs.json írási műveletek

| Esemény | Írás típusa |
|---------|-------------|
| Job létrehozva | Új rekord hozzáfűzve |
| Státusz frissítés (polling) | In-place frissítés job_id alapján |
| Befejezés (output_geotiff_path) | In-place frissítés |
| Nyers képek törölve | `raw_images_delete_at` → null, `raw_images_path` → null |

Minden írás atomilag: temp fájl + `os.replace()` (ugyanaz a pattern, mint a többi JSON modul).

---

## 2. Párhuzamossági korlátok

### ODM job limit

```
Maximum 1 futó ODM job egyszerre a szerveren.
Indok: ODM memóriaigénye kizárja a párhuzamos futtatást
       (8 GB RAM esetén 2 párhuzamos job = szerver összeomlás)

Implementáció:
  ODMManager.get_active_job_count() > 0 → új job: queued, nem processing
  Queued jobok indítása: háttér szál (threading.Thread),
  30 másodpercenként ellenőrzi van-e szabad kapacitás
```

### Képfeltöltés párhuzamossága

```
Több felhasználó egyidejű feltöltése:
  → Minden user saját könyvtárba tölt (data/users/{user_id}/odm_uploads/)
  → Nincs konfliktus
  → Sávszélesség megosztás: Streamlit natívan kezeli (HTTP streaming)

Egyetlen felhasználó dupla kattintás problémája:
  → [Feldolgozás indítása] gomb letilva feltöltés / validálás közben
  → st.session_state["odm_upload_in_progress"] = True
```

---

## 3. NodeODM kommunikáció és hibakezelés

### Polling stratégia

```python
# Streamlit nem tud hosszú futó folyamatot közvetlen várni.
# Megoldás: background thread + session state frissítés

def poll_odm_status(job_id: str, odm_task_id: str, user_id: str):
    """Háttér szálban fut, 30mp-enként frissíti az odm_jobs.json-t."""
    while True:
        status = ODMManager.get_status(odm_task_id)
        ODMJobManager.update_status(user_id, job_id, status)
        if status["code"] in [30, 40]:  # failed or completed
            if status["code"] == 40:
                ODMManager.download_orthophoto(...)
                ParcelManager.attach_image_to_parcel(...)
                EmailNotifier.send_completion(...)
            break
        time.sleep(30)
```

### NodeODM elérhetetlen (hiba)

```
Streamlit → NodeODM HTTP timeout (5 másodperc):
  │
  ▼
3 újrapróbálkozás 2 másodperc várakozással
  │
  ▼
Még mindig sikertelen:
  → job státusz: "failed"
  → hiba üzenet: "A feldolgozó szerver nem érhető el.
     Kérjük, próbáld újra 5 percen belül."
  → rendszergazda email értesítés (ha konfigurálva)

NodeODM konténer automatikus újraindítása:
  → docker-compose.yml: restart: unless-stopped
  → Legtöbb átmeneti hibát önmagától megoldja
```

---

## 4. Nagy fájlok feltöltésének kezelése

### Streamlit upload korlát

Az alapértelmezett Streamlit upload limit 200 MB. Nagy képcsomag esetén:

```toml
# .streamlit/config.toml
[server]
maxUploadSize = 2000   # 2 GB (ZIP feltöltéshez)
```

### ZIP kicsomagolás

```python
# Ha ZIP fájlt tölt fel a felhasználó:
if uploaded_file.name.endswith(".zip"):
    with zipfile.ZipFile(uploaded_file) as zf:
        # Biztonsági ellenőrzés: path traversal védelem
        for member in zf.namelist():
            if ".." in member or member.startswith("/"):
                raise ValueError(f"Gyanús ZIP tartalom: {member}")
        zf.extractall(upload_dir)
```

### Feltöltési progress

```python
# Nagy ZIP fájlnál: chunked upload progress bar
# st.progress() frissítése chunk-onként
```

---

## 5. Tárhely kvóta integritás

```python
# Feldolgozás indítása előtt:
def check_storage_quota(user_id: str, estimated_output_mb: float) -> bool:
    used = StorageQuota.get_used_mb(user_id)
    limit = UserManager.get_storage_limit_mb(user_id)
    return (used + estimated_output_mb) <= limit

# Kvóta számítás:
# → data/users/{user_id}/uploads/ könyvtár mérete (GeoTIFF-ek)
# → data/users/{user_id}/odm_uploads/ NEM számít (temp)
# → data/users/{user_id}/models/ NEM számít (opcionálisan)
```

---

## 6. ODM output integráció M06-tal

```python
# Sikeres ODM befejezés után (ODMManager poll thread):

geotiff_path = f"data/users/{user_id}/uploads/{job_id}_orthophoto.tif"

# GeoTIFF letöltés NodeODM-ből
ODMManager.download_orthophoto(odm_task_id, geotiff_path)

# Parcellához csatolás (M06 adatmodell szerint)
image_record = {
    "image_id": str(uuid4()),
    "file_path": geotiff_path,
    "image_type": "drone",
    "date_acquired": datetime.now().strftime("%Y-%m-%d"),
    "band_count": 3,           # RGB ortomozaik (ODM alap output)
    "is_georeferenced": True,
    "notes": f"ODM feldolgozás — {job['preset']} preset, {job['image_count']} kép",
    "attached_at": datetime.now().isoformat(),
    "source_odm_job_id": job_id
}
ParcelManager.attach_image_to_parcel(user_id, parcel_id, image_record)

# M07 flight_plan frissítés (ha van kapcsolt terv)
if job.get("flight_plan_id"):
    FlightPlanManager.update_status(
        user_id, job["flight_plan_id"],
        status="processed",
        linked_odm_job_id=job_id
    )
```

---

## 7. Szerver skálázási döntések

```
Jelenlegi Hetzner szerver (alapszezon):
  CX22: 4 GB RAM, 2 vCPU
  → ODM NEM futtatható (minimum 8 GB kell)
  → M08 funkció: "Nem elérhető – szerver kapacitásbővítés szükséges"
  → Felhasználó tájékoztatva: "Tőszámlálási szezonban (ápr-jún) elérhető"

Tőszámlálási szezonban (ápr-jún):
  CX32: 8 GB RAM, 4 vCPU (vagy CX42: 16 GB)
  → ODM elérhető, max 200 kép / 5 ha limit
  → CX42 esetén: max 500 kép / 20 ha

Implementáció:
  ODM_AVAILABLE = os.getenv("ODM_AVAILABLE", "false") == "true"
  Ha False → M08 oldal "Jelenleg nem elérhető" üzenetet mutat
  Ez environment variable-ként állítható szerver méretezéskor
```

---

## 8. Adatvesztés elleni védelem

```
Kockázat: ODM feldolgozás közben szerver újraindítás
Következmény: NodeODM elveszíti a task-ot

Mitigáció:
  1. odm_jobs.json perzisztens → szerver újraindítás után
     a "processing" statusú jobok újra ellenőrizve
  2. Ha ODM task_id-ra GET /task/{id}/info → 404:
     → job "failed" státuszba kerül, felhasználó értesítve
     → nyers képek megmaradnak 72 óráig → újrafuttatás lehetséges
  3. NodeODM saját adatai docker volume-on → konténer újraindítás
     után a task folytatódhat (ODM belső checkpoint rendszer)
```
