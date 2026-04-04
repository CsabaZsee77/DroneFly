# Production Readiness — Biztonsági és Migrációs Terv

**Verzió:** v1.0.0
**Dátum:** 2026-03-10
**Státusz:** Aktív fejlesztési terv
**Cél:** Biztonságos publikus publikálás + Hetzner migráció előkészítése

---

## Összefoglalás

Az alkalmazás jelenlegi állapotában **nem publikálható biztonságosan** nyilvános internetre. Az alábbi terv leírja az összes szükséges fejlesztést, azok prioritását, és azt, hogy mit kell elvégezni a Hetzner migrációs lépésekhez képest kódszinten (Codex feladata) vs. szerver-konfigurációs szinten (Hetzner beállítás).

**Fejlesztési stratégia:** Minden kódszintű változtatás a jelenlegi architektúrán, a jelenlegi szerveren kerül elvégzésre. A Hetzner migrációkor csak infrastruktúra-konfiguráció marad.

---

## Jelenlegi architektúra — Gyors referencia

```
Backend:        Streamlit (nem Flask/FastAPI — nincs API réteg)
Auth:           JSON + bcrypt + filelock-alapú session
Adattárolás:    Fájlrendszer (JSON + ONNX + képfájlok)
Task queue:     Nincs (fájl-alapú lock)
Felhasználók:   Logikai izoláció (metaadat-alapú), NEM fizikai
Szerver:        Windows fejlesztői gép, ngrok tunnel
```

**Jelenlegi fájlstruktúra problémája:**
```
data/
  uploads/          ← minden user képei KEVERTEN
  datasets/         ← UUID-k, de közös namespace
  results/          ← közös
  detection_results/ ← közös
models/             ← közös namespace (privát + publikus vegyesen)
```

---

## Fejlesztési fázisok

### FÁZIS 1 — Kódszintű változtatások (Hetzner előtt)
Ezek elvégzése után az alkalmazás biztonságosan migrálható.

### FÁZIS 2 — Szerver-konfiguráció (Hetzner-en)
Kódot nem érint, csak infrastruktúra-beállítás.

---

## FÁZIS 1 — Kódszintű fejlesztések

### F1-01 — User-scoped könyvtárstruktúra ⭐ LEGFONTOSABB

**Prioritás:** Kritikus
**Státusz:** Tervezett
**Modul:** M01, M02, M03, M04, M05 (keresztmetszeti)

**Probléma:**
Az adatizoláció jelenleg kizárólag metaadat-alapú (JSON mezők). Ha az alkalmazáslogikában bármilyen hiba van (pl. hibás szűrő, race condition), más user adatai is megjelenhetnek. GDPR-szempontból sem elfogadható a fizikailag közös adattárolás.

**Célállapot — fájlstruktúra:**
```
data/
  users/
    {user_id}/
      uploads/          ← user saját feltöltött drónképei
      datasets/         ← user saját annotált adathalmazai
      results/          ← user saját detektálási eredményei
      detection_results/ ← user saját detektálási mappái
      temp/             ← user saját temp fájljai
models/
  users/
    {user_id}/          ← user privát modelljei
  public/               ← admin által közzétett, mindenki számára elérhető modellek
```

**Szükséges kódváltoztatások:**
1. `utils/path_manager.py` (ÚJ) — centralizált path-generáló helper:
   - `get_user_data_dir(user_id)` → `data/users/{user_id}/`
   - `get_user_uploads_dir(user_id)` → `data/users/{user_id}/uploads/`
   - `get_user_datasets_dir(user_id)` → `data/users/{user_id}/datasets/`
   - `get_user_results_dir(user_id)` → `data/users/{user_id}/results/`
   - `get_user_detection_results_dir(user_id)` → `data/users/{user_id}/detection_results/`
   - `get_model_dir(user_id=None)` → `models/users/{user_id}/` vagy `models/public/`
   - Path traversal védelem: minden path `os.path.realpath()` + prefix-ellenőrzés
2. `utils/dataset_manager.py` — path generálás átírása `path_manager`-re
3. `utils/model_metadata.py` — modell-path generálás átírása
4. `core/predictor.py` — eredmény-mentési path átírása
5. `core/sliding_window_detector.py` — temp és result path átírása
6. `pages/3_Counting.py` — detection_results path átírása
7. `pages/4_Results.py` — results böngészési path átírása
8. `pages/10_Admin.py` — admin látja az összes user könyvtárát (cross-user view)
9. **Adatmigráció:** meglévő fájlok áthelyezése az új struktúrába (egyszeri script)

**Elvárt eredmény:** User A fizikailag nem férhet hozzá User B adataihoz még alkalmazáslogikai hiba esetén sem.

---

### F1-02 — Path traversal védelem

**Prioritás:** Kritikus
**Státusz:** Tervezett
**Modul:** M01, M03 (minden fájlkezelő pont)

**Probléma:**
Ha bármilyen felhasználói inputból épül fájlútvonal (fájlnév, dataset ID, model ID), path traversal támadással más könyvtárakba lehet navigálni (`../../etc/passwd` típusú támadás).

**Szükséges kódváltoztatások:**
- A `path_manager.py`-ban minden generált path validálása:
  ```python
  def safe_join(base_dir: str, *parts: str) -> str:
      path = os.path.realpath(os.path.join(base_dir, *parts))
      if not path.startswith(os.path.realpath(base_dir)):
          raise ValueError("Path traversal detected")
      return path
  ```
- Fájlnév sanitizálás: `secure_filename()` (werkzeug) vagy saját UUID-alapú névgenerálás
- Eredeti fájlnevet csak display célra tárolni (metaadatban), belső névként soha

---

### F1-03 — Credential-ek és .env kezelés

**Prioritás:** Kritikus
**Státusz:** Tervezett

**Probléma:**
A `.env` fájl éles jelszavakat tartalmaz, és valószínűleg bekerült a git history-ba.

**Szükséges lépések:**
1. **Azonnali:** A kiszivárgott jelszavak megváltoztatása (Gmail app password, CDSE jelszó)
2. **Git history tisztítás:** `git filter-repo --path .env --invert-paths` (Codex NEM végzi el — ez destruktív git művelet, manuálisan)
3. `.gitignore` ellenőrzés: `.env` szerepel-e benne (jelenleg igen, de ellenőrizni)
4. `.env.example` megmarad a repo-ban (placeholder értékekkel)
5. A szerveren: OS-szintű environment variable (systemd `EnvironmentFile=`) vagy Docker secret

**Nem kódváltoztatás:** A runtime betöltés (`python-dotenv`) megfelelő, csak a fájl kezelése a probléma.

---

### F1-04 — Session token biztonság

**Prioritás:** Magas
**Státusz:** Tervezett
**Modul:** M04 (`utils/session_manager.py`)

**Jelenlegi implementáció:**
Session token = SHA256(username + timestamp), plaintext tárolva `data/sessions.json`-ban.

**Probléma:**
Ha a `sessions.json` fájl kompromittálódik (szerver-betörés, hiba), az összes aktív session érvénytelenítés nélkül felhasználható.

**Szükséges kódváltoztatások:**
1. A sessions.json-ban **ne a token maga**, hanem annak bcrypt hash-e legyen tárolva
2. Belépéskor: token generálva → hash tárolva → eredeti token session state-be kerül
3. Ellenőrzéskor: `bcrypt.checkpw(submitted_token, stored_hash)`
4. Sliding expiry: aktivitásnál automatikus megújítás (ne fix 7 nap)
5. "Összes session invalidálása" funkció admin számára

---

### F1-05 — Per-user training queue (globális lock megszüntetése)

**Prioritás:** Magas
**Státusz:** ✅ Implementálva (v0.8.0, 2026-03-20)
**Modul:** M02 (`utils/training_job_manager.py`, `utils/training_queue_runner.py`)

**Megoldás:**
1. Per-user lock: egy felhasználónak max 1 aktív (running/queued) job-ja lehet
2. Globális párhuzamossági limit: `MAX_CONCURRENT_TRAININGS=2` (env var)
3. Queue rendszer: ha minden slot foglalt → `queued` státusz, FIFO promóció
4. Háttérszál: `threading.Thread` daemon szálakban futnak a training-ek (nem blokkolják a UI-t)
5. Startup recovery: stale running jobok → failed, queued jobok auto-promóció
6. Job státuszok: `queued | running | completed | failed | cancelled`

**Fájlok:**
- `utils/training_job_manager.py` — queue logika, atomi promóció
- `utils/training_queue_runner.py` — singleton háttérszál-kezelő
- `utils/training_wrapper.py` — existing_job_id támogatás
- `_pages/2_🎓_Training.py` — queue-aware UI

---

### F1-06 — Input validáció és sanitizálás

**Prioritás:** Magas
**Státusz:** Tervezett
**Modul:** M01, M02, M03, M04

**Szükséges kódváltoztatások:**
1. Feltöltött fájlok: MIME type ellenőrzés (ne csak kiterjesztés)
   - Képfájlok: ténylegesen képek-e (PIL.Image.open ellenőrzés)
   - ONNX modellek: ONNX runtime-mal validálni megnyitás előtt
2. Szöveg inputok: max hossz korlát, HTML/script sanitizálás
3. Numerikus inputok (epoch szám, threshold): tartomány-ellenőrzés
4. Fájlméret limit: `MAX_UPLOAD_SIZE_MB = 500` konfiguráció

---

### F1-07 — Audit log kiterjesztése

**Prioritás:** Közepes
**Státusz:** Tervezett
**Modul:** M04 (`data/joblog.json`)

**Jelenlegi állapot:**
Az `joblog.json` létezik, de nem konzisztensen használt.

**Szükséges kódváltoztatások:**
1. Centralizált audit logger: `utils/audit_log.py`
2. Rögzítendő események:
   - Bejelentkezés (sikeres + sikertelen)
   - Jelszóváltoztatás
   - User törlése / létrehozása (admin)
   - Model publikus tétele / visszavonása
   - Dataset törlése
   - Training indítás / leállítás
3. Log formátum: `{timestamp, user_id, action, resource_id, ip_address, result}`
4. Admin oldalon: audit log megtekintés szűréssel

---

### F1-08 — CSRF és devcontainer konfig szétválasztása

**Prioritás:** Közepes
**Státusz:** Tervezett

**Probléma:**
A `.devcontainer/devcontainer.json` tartalmazza a `--server.enableCORS false --server.enableXsrfProtection false` flageket. Ezek fejlesztéshez szükségesek, de véletlenül átkerülhetnek az éles konfigba.

**Szükséges lépések:**
1. Éles `streamlit_config.toml` létrehozása (nem a devcontainer verzió):
   ```toml
   [server]
   enableCORS = true
   enableXsrfProtection = true
   headless = true
   port = 8501
   address = "127.0.0.1"
   ```
2. Docker Dockerfile-ban ez a config mountolva, a devcontainer verzió nem

---

### F1-09 — Docker csomagolás

**Prioritás:** Közepes (migráció előtt szükséges)
**Státusz:** Tervezett

**Szükséges fájlok (Codex hozza létre):**

**`Dockerfile`:**
```dockerfile
FROM python:3.11-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY . .
# Adatkönyvtárak volume-ként csatolva, nem az image-be másolva
VOLUME ["/app/data", "/app/models"]
EXPOSE 8501
CMD ["streamlit", "run", "Home.py", \
     "--server.port=8501", \
     "--server.address=127.0.0.1", \
     "--server.headless=true"]
```

**`docker-compose.yml`:**
```yaml
version: '3.8'
services:
  app:
    build: .
    volumes:
      - ./data:/app/data
      - ./models:/app/models
      - ./.streamlit:/app/.streamlit:ro
    env_file:
      - .env
    ports:
      - "127.0.0.1:8501:8501"
    restart: unless-stopped

  nginx:
    image: nginx:alpine
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./nginx/nginx.conf:/etc/nginx/nginx.conf:ro
      - ./certbot/conf:/etc/letsencrypt:ro
      - ./certbot/www:/var/www/certbot:ro
    depends_on:
      - app
    restart: unless-stopped
```

**`nginx/nginx.conf`** (vázlat):
```nginx
server {
    listen 80;
    server_name yourdomain.com;
    location /.well-known/acme-challenge/ { root /var/www/certbot; }
    location / { return 301 https://$host$request_uri; }
}
server {
    listen 443 ssl;
    server_name yourdomain.com;
    ssl_certificate /etc/letsencrypt/live/yourdomain.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/yourdomain.com/privkey.pem;
    location / {
        proxy_pass http://app:8501;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_read_timeout 300s;  # hosszú ML műveletek miatt
    }
}
```

---

## FÁZIS 2 — Hetzner szerver-konfiguráció

Ez a fázis **nem igényel kódváltoztatást** — tisztán infrastruktúra.

### Ajánlott szerver

**Hetzner CX32** (cloud VPS):
- 4 vCPU (shared), 8 GB RAM, 80 GB SSD NVMe
- ~8.5 €/hó (Falkenstein vagy Helsinki adatközpont — EU, GDPR kompatibilis)
- Ha training is fut: **CCX23** (4 dedicated vCPU, 8 GB RAM) ~18 €/hó

**Indoklás:**
- YOLOv11n/s ONNX inference CPU-n elfér 8 GB RAM-ban
- 4 GB RAM minimum (Streamlit + ONNX + OpenCV egyidejű terhelése)
- EU adatközpont: GDPR szempontból előnyös

### Hetzner telepítési lépések (FÁZIS 1 után)

1. **Szerver létrehozása:** Ubuntu 24.04 LTS, SSH kulcs hozzáadása
2. **Docker telepítése:** `apt install docker.io docker-compose-v2`
3. **Repo klónozása:** `git clone ...` (privát repo esetén deploy key)
4. **`.env` létrehozása:** manuálisan, git-en kívül
5. **Data migráció:** `rsync -avz data/ user@hetzner:/app/data/` + `rsync models/`
6. **SSL cert:** Certbot Let's Encrypt (automatikus megújítás cron-nal)
7. **Docker stack indítása:** `docker-compose up -d`
8. **Tűzfal:** csak 80 és 443 nyitva (Hetzner Cloud Firewall)
9. **Backup:** Hetzner Volume snapshot (heti automatikus)

---

## Fejlesztési sorrend és státusz

| ID | Feladat | Prioritás | Státusz | Fázis |
|----|---------|-----------|---------|-------|
| F1-01 | User-scoped könyvtárstruktúra | ⭐ Kritikus | ✅ Kész (2026-03-10) | Kód |
| F1-02 | Path traversal védelem | ⭐ Kritikus | ✅ Kész (2026-03-10) | Kód |
| F1-03 | Credential/.env kezelés | ⭐ Kritikus | 🔲 Tervezett (manuális) | Kód+Manuális |
| F1-04 | Session token biztonság | 🔴 Magas | ✅ Kész (2026-03-10) | Kód |
| F1-05 | Per-user training queue | 🔴 Magas | ✅ Kész (2026-03-20) | Kód |
| F1-06 | Input validáció | 🔴 Magas | ✅ Kész (2026-03-10) | Kód |
| F1-07 | Audit log kiterjesztése | 🟡 Közepes | ✅ Kész (2026-03-10) | Kód |
| F1-08 | CSRF / devcontainer szétválasztás | 🟡 Közepes | ✅ Kész (2026-03-10) | Kód+Konfig |
| F1-09 | Docker csomagolás | 🟡 Közepes | ✅ Kész (2026-03-10) | Kód |
| F2-01 | Hetzner VPS + Docker deploy | — | 🔲 Fázis 2 | Szerver |
| F2-02 | Nginx + Let's Encrypt SSL | — | 🔲 Fázis 2 | Szerver |
| F2-03 | Tűzfal + SSH hardening | — | 🔲 Fázis 2 | Szerver |
| F2-04 | Backup automatizálás | — | 🔲 Fázis 2 | Szerver |

---

## Hátralévő feladatok (Hetzner migráció előtt)

### F1-03 — Credential/.env kezelés (manuális lépések!)
1. Gmail app password változtatása
2. CDSE jelszó változtatása
3. `git filter-repo --path .env --invert-paths` (git history tisztítás)
4. `.env.example` ellenőrzése

### F1-05 — Per-user training queue
Lásd fent a részletes specifikációt. Ez a legkomplexebb feladat — külön munkamenetben.

---

## Következő lépés (Hetzner migráció előtt)

Kódszintű feladatok közül **csak F1-03 és F1-05 maradt**:
- F1-03: manuális credential csere (nem kód)
- F1-05: per-user training queue (opcionális — az app működik nélküle is, csak blokkolás marad)

Ha F1-05 elhagyható (pl. egyszerre max 1-2 aktív user várható), **a Hetzner migráció megkezdhető**.

---

*Dokumentum karbantartója: Claude (Architect role)*
*Frissítendő, amikor egy feladat státusza változik.*
