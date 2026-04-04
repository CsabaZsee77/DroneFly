# L4 – Tranzakciós és Párhuzamos – Felhasználókezelés és Hitelesítés

**Modul:** M04
**Szint:** L4 – Tranzakciós és Párhuzamos
**Utolsó frissítés:** 2026-03-10

---

## Adatperzisztencia

| Adat | Fájl | Formátum | Zárolás |
|------|------|----------|---------|
| Felhasználók | `data/users.json` | JSON | File lock (fcntl/msvcrt) |
| Rate limit | `data/login_attempts.json` | JSON | File lock |
| Session tokenek | `data/sessions.json` | JSON (egyetlen fájl, bcrypt hash-ek) | threading.Lock |
| Audit napló | `data/audit_log.json` | JSON (entries lista) | threading.Lock |

---

## Tranzakcionális garanták

### Regisztráció atomicitása

```
1. users.json olvasás (LOCK_SH)
2. Duplikáció ellenőrzés
3. Ha OK: users.json írás (LOCK_EX)
4. Zárás feloldása
5. ensure_user_dirs(user_id) — data/users/{user_id}/ könyvtárak létrehozása
6. audit_log: REGISTER esemény naplózása
```

**Kockázat:** A 2. és 3. lépés között kis időablak van (TOCTOU). Ha két felhasználó egyszerre regisztrál azonos névvel, az egyik hibás adatot írhat felül. Enyhíti: az exclusive lock megakadályozza az egyidejű írást.

### Jelszó módosítás atomicitása

```
1. Jelenlegi jelszó hitelesítése (read + bcrypt)
2. users.json írás (LOCK_EX) – csak a password_hash mező frissítése
3. audit_log: PASSWORD_CHANGE esemény naplózása
```

**Megjegyzés:** Nincs rollback mechanizmus – ha az írás félbeszakad, a fájl korrupt lehet.

### Session létrehozás atomicitása

```
1. sessions.json betöltése (threading.Lock)
2. session_id = uuid4().hex  →  secret = secrets.token_hex(32)
3. token_hash = bcrypt.hashpw(secret.encode(), gensalt())
4. sessions["sessions"][session_id] = {token_hash, user_data, expires_at, ...}
5. sessions["_last_active_session_id"] = session_id
6. sessions.json mentése (threading.Lock feloldás)
7. Visszaadja: f"{session_id}:{secret}"  ← csak a browserben él, fájlban NEM tárolódik
```

---

## Párhuzamos hozzáférés

### Több felhasználó egyszerre

| Forgatókönyv | Kezelés |
|--------------|---------|
| 2 felhasználó bejelentkezik egyszerre | threading.Lock védi a sessions.json írást |
| 2 felhasználó regisztrál egyszerre | Exclusive file lock véd, de TOCTOU kockázat (lásd fent) |
| 1 felhasználó 2 tabban van | Azonos session_id → mindkét tab működik |
| Admin törli a felhasználót bejelentkezés közben | Session megmarad sessions.json-ban → következő oldalbetöltésnél user = None |

### sessions.json párhuzamossága

Egyetlen fájl, `threading.Lock`-kal védve:
- Minden `create_session`, `get_session`, `delete_session` hívás lock-ol olvasás+írás közben
- `get_session` is ír (last_activity frissítés) → exclusive lock szükséges

---

## Session expiry és cleanup

### Session lejárat kezelése

```python
def get_session(self, session_token: str) -> Optional[dict]:
    parts = session_token.split(":", 1)
    session_id, secret = parts[0], parts[1]

    data = self._load_sessions()
    session = data["sessions"].get(session_id)

    expiry = datetime.fromisoformat(session["expires_at"])
    if datetime.now() > expiry:
        del data["sessions"][session_id]
        self._save_sessions(data)
        return None

    # Bcrypt ellenőrzés
    if not bcrypt.checkpw(secret.encode(), session["token_hash"].encode()):
        return None

    # Sliding expiry: last_activity frissítése
    session["last_activity"] = datetime.now().isoformat()
    self._save_sessions(data)
    return session["user_data"]
```

### F5 visszaállítás — ismert trade-off

F5 után a `st.session_state` törlődik. A rendszer a `_last_active_session_id` alapján visszaállítja a sessiont **bcrypt ellenőrzés nélkül**.

**Miért elfogadható ez a trade-off:**
- A `_restore_session_by_id()` belső Python függvény, nem HTTP endpoint — külső támadó nem hívhatja
- Egy sessions.json fájlhoz való hozzáférés önmagában nem elegendő a kihasználáshoz
- Csak az utolsó aktív session érintett; az összes többi session bcrypt-tel védett
- A lejárat ellenőrzés F5 esetén is fut

### Lejárt session-ök cleanup

```python
session_manager.cleanup_expired_sessions()
# Ajánlott: alkalmazás indításakor vagy napi cron-nal
```

---

## Audit log párhuzamossága

```python
# utils/audit_log.py
_lock = threading.Lock()

def log_event(...):
    with _lock:
        entries = _load_entries()
        entries.append(entry)
        if len(entries) > 10_000:
            entries = entries[-10_000:]
        _save_entries(entries)
```

**Szálbiztonság:** `threading.Lock` védi az olvasás+módosítás+írás sorozatot.
**Teljesítmény:** Minden log_event disk I/O-t jelent. Kis felhasználószámnál elfogadható.

---

## Backup és helyreállítás

### users.json backup

```bash
# Manuális backup (deploy előtt ajánlott)
copy data\users.json backup\users_backup_%DATE%.json
```

### Helyreállítás sérült users.json esetén

```bash
# 1. Ellenőrizd a JSON érvényességét
python -c "import json; json.load(open('data/users.json'))"

# 2. Ha invalid: backup visszaállítása
copy backup\users_backup_LEGFRISSEBB.json data\users.json

# 3. Ha nincs backup: üres inicializálás (minden user elvész!)
echo {} > data\users.json
python set_admin_user.py zsigmond  # Admin újra beállítása
```

### sessions.json sérülés esetén

```bash
# Törölhető — mindenki újra bejelentkezik
echo {"sessions": {}, "_last_active_session_id": null} > data\sessions.json
```

---

## Biztonsági audit szempontok

| Szempont | Állapot | Kockázat |
|----------|---------|---------|
| Jelszó hash | bcrypt | Alacsony |
| Session token entrópia | 256 bit (secrets.token_hex(32)) | Alacsony |
| Session token tárolás | bcrypt hash (nem plaintext) | Alacsony |
| HTTPS | Nginx + Let's Encrypt (éles deploy-on) | Alacsony (éles) |
| Cookie HttpOnly | Nem (Streamlit korlát — session_state alapú) | Közepes |
| CSRF védelem | enableXsrfProtection = true (production config) | Alacsony (éles) |
| CORS | enableCORS = true (production config, nginx proxyn át) | Alacsony (éles) |
| Jelszó minimális hossz | 6 karakter | Gyenge (8+ ajánlott) |
| Audit log | Igen — login, logout, register + bővíthető | Alacsony |
| User adatok izolációja | Fizikai könyvtárszeparáció (data/users/{user_id}/) | Alacsony |

---

## Ismert korlátok

| Korlát | Hatás | Javaslat |
|--------|-------|---------|
| JSON-based tároló (nem adatbázis) | Nagy felhasználószámnál lassú | SQLite/PostgreSQL migráció (ROADMAP) |
| Nincs jelszó komplexitás szabály | Gyenge jelszavak lehetségesek | Min. hossz + karakter típus validáció |
| Nincs email-alapú jelszó visszaállítás | Admin kell a resethez | Email token alapú reset (tervezett) |
| F5 recovery bcrypt nélkül | _last_active session session_id alapján visszaállítható | Elfogadható trade-off (részletezve fent) |
| Audit log disk I/O minden eventnél | Nagy terhelésnél lassít | Queue-alapú async logging (ROADMAP) |
