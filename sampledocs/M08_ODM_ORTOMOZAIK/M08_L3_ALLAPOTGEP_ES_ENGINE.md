# L3 – Állapotgép és Engine – ODM Ortomozaik Pipeline

**Modul:** M08
**Szint:** L3 – Állapotgép és Engine
**Verzió:** v1.0.0
**Létrehozva:** 2026-03-16
**Státusz:** ✅ Implementálva (2026-03-18)

---

## Forrásfájlok

| Fájl | Szerepkör | Státusz |
|------|-----------|---------|
| `utils/odm_manager.py` | ODM job CRUD, státusz polling, NodeODM API kliens, preset kezelés | ✅ Implementálva |
| `_pages/12_🛠️_ODM_Processing.py` | Streamlit UI — képfeltöltés, validáció, job kezelés, GeoTIFF letöltés | ✅ Implementálva |
| `docker-compose.yml` | NodeODM konténer (opendronemap/nodeodm, port 3000, 6GB mem limit) | ✅ Implementálva |

**Függőségek (már meglévő):**
- `utils/parcel_manager.py` — GeoTIFF csatolás a parcellához
- `utils/path_manager.py` — felhasználói könyvtár kezelés
- `utils/user_manager.py` — előfizetési csomag, tárhely kvóta
- `utils/email_notifier.py` — befejezési értesítés

**Új Python csomagok:**
- `exifread` — GPS EXIF kinyerés
- `requests` — NodeODM REST API hívások
- `shapely` — GPS pontok konvex hullja, területbecslés

---

## ODM integráció architektúra

```
Streamlit app (Python)
        │
        │ HTTP REST API
        ▼
NodeODM konténer (port 3000)
  – OpenDroneMap fotogrammetria motor
  – REST API: /task/new, /task/{id}/info, /task/{id}/download
        │
        ▼
Feldolgozott output:
  /var/www/data/{task_id}/odm_orthophoto/odm_orthophoto.tif
```

### NodeODM Docker Compose konfiguráció

```yaml
# docker-compose.yml kiegészítés
services:
  nodeodm:
    image: opendronemap/nodeodm:latest
    ports:
      - "3000:3000"
    volumes:
      - odm_data:/var/www/data
    restart: unless-stopped
    mem_limit: 8g          # Hetzner csomag szerint állítandó
    environment:
      - NODE_OPTIONS=--max-old-space-size=6144

volumes:
  odm_data:
```

---

## ODMManager engine (utils/odm_manager.py)

```python
class ODMManager:

    ODM_URL = "http://nodeodm:3000"  # Docker network belső cím

    def create_task(self, image_paths: list[str], preset: str,
                    user_id: str) -> str:
        """
        Képeket feltölti NodeODM-be és elindítja a feldolgozást.
        Visszaadja a task_id-t.

        Preset → ODM options mapping:
          "fast":     {"fast-orthophoto": True, "resize-to": 2048}
          "standard": {}  (ODM alapértelmezések)
          "quality":  {"pc-quality": "ultra", "orthophoto-resolution": 2}
        """
        ...

    def get_status(self, task_id: str) -> dict:
        """
        GET /task/{id}/info
        Visszaadja: status_code, progress %, processing_time, error
        Status kódok: 10=queued, 20=running, 30=failed, 40=completed
        """
        ...

    def download_orthophoto(self, task_id: str,
                             output_path: str) -> bool:
        """
        GET /task/{id}/download/odm_orthophoto.tif
        Letölti a kész GeoTIFF-et a megadott útvonalra.
        """
        ...

    def delete_task(self, task_id: str):
        """
        POST /task/remove — ODM szerveren takarít
        A nyers képek és közbenső fájlok törlése.
        """
        ...
```

---

## EXIFParser engine (utils/exif_parser.py)

```python
class EXIFParser:

    def extract_gps(self, image_path: str) -> dict | None:
        """
        GPS koordináták kinyerése JPG/TIFF EXIF adatokból.
        Visszaad: {"lat": float, "lon": float, "alt": float} vagy None
        """
        ...

    def estimate_area(self, image_paths: list[str]) -> dict:
        """
        Képek GPS koordinátáiból területbecslés.
        1. GPS kinyerés minden képből
        2. Konvex burok (Shapely convex_hull)
        3. Terület számítás metrikus CRS-ben (UTM)
        Visszaad: {"area_ha": float, "center": [lat, lon],
                   "images_with_gps": int, "images_without_gps": int}
        """
        ...
```

---

## ODM job adatmodell

```json
// data/users/{user_id}/odm_jobs.json – egy job eleme
{
  "job_id": "uuid4",
  "user_id": "uuid4",
  "parcel_id": "uuid4",
  "flight_plan_id": "uuid4_or_null",
  "created_at": "2026-05-10T09:00:00",
  "preset": "standard",
  "image_count": 142,
  "estimated_area_ha": 3.8,
  "odm_task_id": "nodeodm_internal_uuid",
  "status": "processing",
  "progress_pct": 45,
  "started_at": "2026-05-10T09:02:00",
  "completed_at": null,
  "output_geotiff_path": null,
  "error_message": null,
  "raw_images_path": "data/users/{user_id}/odm_uploads/{job_id}/",
  "raw_images_delete_at": "2026-05-11T09:00:00",
  "output_size_mb": null
}
```

### Job állapotgép

```
[uploading]
    ↓ (összes kép feltöltve)
[validating]
    ↓ (GPS ok, méret ok, kvóta ok)
[queued]
    ↓ (ODM szabad, job elindult)
[processing]  ←→ progress_pct: 0–100%
    ↓              ↓
[completed]    [failed]
    ↓
GeoTIFF parcellához csatolva
    ↓
[archived]  (nyers képek törölve 24h után)
```

---

## Streamlit UI (pages/10_ODM_Processing.py)

```
Tab 1: Új feldolgozás
  ├─ Multi-file upload (drag & drop, ZIP is fogadható)
  ├─ Feltöltés progress bar
  ├─ EXIF validáció eredménye (GPS térkép preview: pontok a képek helyén)
  ├─ Terület és képszám badge-ek
  ├─ Méretlimit jelzés (zöld / sárga / piros)
  ├─ Parcella hozzárendelés (auto javaslat + manuális override)
  ├─ Preset választás (3 gomb: Gyors / Standard / Minőségi)
  └─ [Feldolgozás indítása] gomb

Tab 2: Futó és korábbi jobok
  ├─ Aktív job: progress bar, becsült hátralévő idő
  ├─ Korábbi jobok táblázata:
  │    dátum | parcella | képszám | preset | státusz | GeoTIFF méret
  ├─ [Eredmény megtekintése] → M06 Parcel Analysis-ra navigál
  └─ [Nyers képek törlése] (ha még megvannak)
```

---

## M07 ↔ M08 integráció

```python
# Ha M07-ben egy repülési terv státusza "flown"-ra változik:
# → M08 oldalon megjelenik egy gyors indítógomb:

if flight_plan["status"] == "flown" and flight_plan["linked_odm_job_id"] is None:
    st.info(f"A '{flight_plan['parcel_name']}' fölötti repülés elvégezve. "
            f"Szeretnéd feldolgozni a képeket ortomozaikká?")
    if st.button("Igen, feldolgozom"):
        # navigáció az ODM Processing oldalra,
        # parcella előre kitöltve
        st.session_state["odm_preselect_parcel"] = flight_plan["parcel_id"]
        st.switch_page("pages/10_ODM_Processing.py")
```

---

## Növényfajta alapú preset ajánlás

```python
# Ha a parcellához növényfajta van rendelve:
CROP_PRESET_RECOMMENDATIONS = {
    "Kukorica":   "standard",   # Nagyobb tő, standard elég
    "Napraforgó": "standard",
    "Cukorrépa":  "quality",    # Kis rozetta, magasabb felbontás kell
    "Szója":      "quality",
    "Hagyma":     "quality",    # Nagyon kis egyedek
    "Burgonya":   "standard",
}
# UI: st.info(f"Ajánlott preset {crop_type}-hoz: {rec}")
```
