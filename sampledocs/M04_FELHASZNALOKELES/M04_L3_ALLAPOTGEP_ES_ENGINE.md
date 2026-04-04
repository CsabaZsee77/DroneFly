# L3 – Állapotgép és Engine – Felhasználókezelés és Hitelesítés

**Modul:** M04
**Szint:** L3 – Állapotgép és Engine
**Utolsó frissítés:** 2026-03-10

---

## Forrásfájlok

| Fájl | Szerepkör |
|------|-----------|
| `utils/user_manager.py` | Felhasználói adatok kezelése (CRUD, hitelesítés) |
| `utils/session_manager.py` | Session token és bcrypt-alapú session kezelés |
| `utils/auth_helpers.py` | `require_authentication()`, `ensure_user_dirs()` |
| `utils/audit_log.py` | Biztonsági esemény naplózás |
| `utils/path_manager.py` | Centralizált user-scoped path generálás |
| `Home.py` | Login/Register form, sidebar user menu |
| `pages/10_Admin.py` | Admin panel UI |
| `set_admin_user.py` | Admin jogosultság beállítása (CLI script) |
| `data/users.json` | Felhasználói adatbázis (JSON) |
| `data/login_attempts.json` | Rate limiting állapot |
| `data/sessions.json` | Session tokenek (bcrypt hash-elt, egyetlen fájl) |
| `data/audit_log.json` | Audit napló (biztonsági eseménynaplózás) |

---

## Adatmodell

### users.json struktúra

```json
{
  "zsigmond": {
    "user_id": "uuid4",
    "username": "zsigmond",
    "email": "sajat@email.hu",
    "full_name": "Zsigmond Csaba",
    "password_hash": "$2b$12$...",
    "role": "admin",
    "created_at": "ISO8601",
    "last_login": "ISO8601",
    "require_geotiff": true,
    "modules": ["flight", "odm", "counting", "spectral", "model"]
  },
  "opertor_user": {
    "user_id": "uuid4",
    "username": "kovacs_janos",
    "email": "kovacs@farm.hu",
    "full_name": "Kovács János",
    "password_hash": "$2b$12$...",
    "role": "user",
    "created_at": "ISO8601",
    "last_login": "ISO8601",
    "require_geotiff": true,
    "modules": ["counting", "spectral"]
  }
}
```

**Mező magyarázatok:**

| Mező | Típus | Kötelező | Leírás |
|------|-------|----------|--------|
| `user_id` | string (uuid4) | ✅ | Egyedi azonosító |
| `username` | string | ✅ | Bejelentkezési név |
| `email` | string | ✅ | Email cím |
| `full_name` | string | ✅ | Megjelenített név |
| `password_hash` | string | ✅ | bcrypt hash |
| `role` | `"user"` \| `"admin"` | ✅ | Szerepkör |
| `created_at` | ISO8601 | ✅ | Regisztráció ideje |
| `last_login` | ISO8601 \| null | — | Utolsó bejelentkezés |
| `require_geotiff` | bool | — | GeoTIFF követelmény (default: true) |
| `modules` | list of string | — | Engedélyezett modulok (default: []) |

### login_attempts.json struktúra

```json
{
  "zsigmond": {
    "failed_count": 0,
    "last_attempt": "ISO8601",
    "locked_until": null
  }
}
```

### sessions.json struktúra (bcrypt-alapú, F1-04)

```json
{
  "sessions": {
    "{session_id_uuid_hex}": {
      "token_hash": "$2b$12$...",
      "user_data": {
        "user_id": "uuid4",
        "username": "zsigmond",
        "role": "admin"
      },
      "created_at": "ISO8601",
      "expires_at": "ISO8601",
      "last_activity": "ISO8601"
    }
  },
  "_last_active_session_id": "{session_id_uuid_hex}"
}
```

**Token formátum (browser session state-ben tárolt):**
```
{session_id}:{secret}
```
- `session_id` = UUID4 hex (32 char) → sessions.json kulcsa
- `secret` = `secrets.token_hex(32)` (64 hex char = 256 bit entropy)
- `token_hash` = `bcrypt(secret)` — csak ez tárolódik fájlban

**Biztonsági megjegyzés:** A `sessions.json` ellopása esetén a bcrypt hash-ek miatt a session tokenek nem reprodukálhatók. Kivétel: `_last_active_session_id` alapján az utolsó aktív session F5 visszaállítással hozzáférhető (ismert trade-off, részletezve M04_L4-ben).

### audit_log.json struktúra (F1-07)

```json
{
  "entries": [
    {
      "timestamp": "ISO8601",
      "action": "LOGIN_SUCCESS",
      "user_id": "uuid4",
      "username": "zsigmond",
      "resource_id": null,
      "result": "ok",
      "detail": null,
      "ip_address": null
    }
  ]
}
```

**Action konstansok:**

| Kategória | Értékek |
|-----------|---------|
| Hitelesítés | `LOGIN_SUCCESS`, `LOGIN_FAILED`, `LOGOUT`, `REGISTER`, `PASSWORD_CHANGE` |
| Fájlkezelés | `UPLOAD_IMAGE`, `DELETE_IMAGE` |
| Adathalmaz | `DATASET_CREATE`, `DATASET_DELETE` |
| Training | `TRAINING_START`, `TRAINING_STOP` |
| Modell | `MODEL_PUBLISH`, `MODEL_UNPUBLISH`, `MODEL_DELETE` |
| Detekció | `DETECTION_RUN`, `DETECTION_DELETE` |
| Admin | `ADMIN_ACTION`, `USER_DELETE` |

---

## Állapotgép

### Felhasználói session állapotok

```
[Vendég]
    │
    ├── Regisztráció
    │       │ Validáció OK → ensure_user_dirs(user_id) → audit: REGISTER
    │       ▼
    │   [Regisztrált – nem bejelentkezett]
    │
    └── Bejelentkezés
            │ Token generálás + bcrypt hash mentés → audit: LOGIN_SUCCESS
            ▼
        [Bejelentkezett]
            │
            ├── Jelszó módosítás → [Bejelentkezett] + audit: PASSWORD_CHANGE
            │
            ├── Rate limit elérve → [Zárolva (15 perc)] + audit: LOGIN_FAILED
            │       │ 15 perc eltelt
            │       └── [Bejelentkezett] (újra próbálható)
            │
            └── Kijelentkezés → Session törlés → audit: LOGOUT → [Vendég]

[Bejelentkezett] + role="admin"
    │
    └── [Admin]
            Admin panel hozzáférés
```

### Session token állapotok

```
[Nincs token a session state-ben]
    │
    │ Sikeres bejelentkezés
    ▼
[session_state['_session_token'] = "{session_id}:{secret}"]
    │
    ├── Navigáció (oldal váltás): token megmarad session_state-ben
    │       → get_session(token): bcrypt.checkpw(secret, hash) → OK
    │
    ├── Token lejárt → sessions.json törlés → [Nincs token]
    │
    ├── F5 refresh → session_state törlődik
    │       → _last_active_session_id alapján visszaállítás (bcrypt nélkül)
    │       → session megmarad ha nem járt le
    │
    └── Kijelentkezés → sessions.json[session_id] törlés → [Nincs token]
```

---

## UserManager engine (utils/user_manager.py)

### Főbb metódusok

```python
class UserManager:
    def __init__(self, users_file: str = "data/users.json"):
        ...

    def register(self, username, email, password, full_name) -> str:
        """Új felhasználó. Returns: user_id. Raises: ValueError duplikáció esetén."""

    def authenticate(self, username, password) -> Optional[dict]:
        """Hitelesítés. Returns: user dict | None. Raises: ValueError rate limit esetén."""

    def change_password(self, user_id, old_password, new_password) -> None:
        """Jelszó módosítás. Raises: ValueError ha régi jelszó helytelen."""

    def get_user_by_id(self, user_id) -> Optional[dict]: ...
    def get_user_by_username(self, username) -> Optional[dict]: ...
    def get_all_users(self) -> List[dict]: ...
    def update_user(self, user_id, **kwargs) -> None: ...
    def delete_user(self, user_id) -> None: ...
    def reset_password_by_admin(self, user_id, new_password) -> None: ...
```

### Fájl zárolás (concurrent write védelem)

```python
class UserManager:
    def _load_users_with_lock(self) -> dict:
        with open(self.users_file, 'r') as f:
            fcntl.flock(f, fcntl.LOCK_SH)  # Shared lock olvasáshoz
            data = json.load(f)
            fcntl.flock(f, fcntl.LOCK_UN)
        return data

    def _save_users_with_lock(self, users: dict) -> None:
        with open(self.users_file, 'w') as f:
            fcntl.flock(f, fcntl.LOCK_EX)  # Exclusive lock íráshoz
            json.dump(users, f, indent=2)
            fcntl.flock(f, fcntl.LOCK_UN)
```

---

## SessionManager engine (utils/session_manager.py)

### Session lifecycle

```python
class SessionManager:
    session_expiry_days = 7
    sessions_file = "data/sessions.json"

    def create_session(self, user_data: dict) -> str:
        """
        Új session.
        - Generál: session_id = uuid4().hex, secret = secrets.token_hex(32)
        - Tárol: sessions.json[session_id]["token_hash"] = bcrypt(secret)
        - Visszaad: f"{session_id}:{secret}" → session state-be kerül
        """

    def get_session(self, session_token: str) -> Optional[dict]:
        """
        Token validálás bcrypt-tel.
        - Bontja: session_id, secret = token.split(":", 1)
        - Keres: sessions.json["sessions"][session_id]
        - Ellenőriz: bcrypt.checkpw(secret.encode(), token_hash.encode())
        - Frissít: last_activity (sliding expiry)
        """

    def delete_session(self, session_token: str) -> None:
        """Session törlése (kijelentkezés). Bontja a tokent, session_id alapján töröl."""

    def initialize_session_from_cookie(self) -> bool:
        """
        Session visszaállítása induláskor.
        1. Full token session_state-ben? → get_session(token) bcrypt-tel
        2. Nincs (F5 után)? → _last_active_session_id alapján bcrypt nélkül
        """

    def cleanup_expired_sessions(self) -> None:
        """Lejárt session-ök törlése sessions.json-ból."""
```

---

## Audit Logger engine (utils/audit_log.py)

### Főbb függvények

```python
def log_event(
    action: str,
    user_id: Optional[str] = None,
    username: Optional[str] = None,
    resource_id: Optional[str] = None,
    result: str = "ok",       # "ok" | "fail" | "warn"
    detail: Optional[str] = None,
    ip_address: Optional[str] = None,
) -> None:
    """
    Audit esemény szálbiztos naplózása threading.Lock()-kal.
    Tároló: data/audit_log.json
    Limit: 10 000 bejegyzés (FIFO — régebbiek eldobva)
    """

def get_recent_events(
    limit: int = 100,
    user_id: Optional[str] = None,
    action_filter: Optional[str] = None,
    result_filter: Optional[str] = None,
) -> list:
    """Legutóbbi bejegyzések szűréssel — admin nézethez."""

def get_statistics() -> dict:
    """Eseményszámok összefoglalója — admin dashboard."""
```

### Home.py integráció

| Esemény | `action` | `result` |
|---------|----------|----------|
| Sikeres bejelentkezés | `LOGIN_SUCCESS` | `ok` |
| Hibás jelszó | `LOGIN_FAILED` | `fail` |
| Rate limit blokk | `LOGIN_FAILED` | `fail` |
| Kijelentkezés | `LOGOUT` | `ok` |
| Sikeres regisztráció | `REGISTER` | `ok` |
| Sikertelen regisztráció | `REGISTER` | `fail` |

---

## Path Manager (utils/path_manager.py)

Minden felhasználó adata fizikailag izolált könyvtárban tárolódik.
Az `auth_helpers.require_authentication()` minden page betöltésekor meghívja az `ensure_user_dirs(user_id)`-t.

```
data/
└── users/
    └── {user_id}/
        ├── uploads/            ← feltöltött drónképek
        ├── datasets/           ← annotált adathalmazok
        ├── detection_results/  ← detekciós eredmények
        └── temp/               ← ideiglenes fájlok (map overlay cache stb.)
```

```python
# Fő függvények:
get_uploads_dir(user_id)            # → data/users/{user_id}/uploads/
get_datasets_dir(user_id)           # → data/users/{user_id}/datasets/
get_detection_results_dir(user_id)  # → data/users/{user_id}/detection_results/
get_temp_dir(user_id)               # → data/users/{user_id}/temp/
safe_join(base_dir, *parts)         # Path traversal védelem
ensure_user_dirs(user_id)           # Könyvtárak létrehozása
```

---

## Admin Panel engine (pages/10_Admin.py)

```
Admin Panel
├── Felhasználók listája
│     - Felhasználónév, Email, Regisztrált, Szerepkör
│     - Per-user: [🔑 Jelszó Reset] [🗑️ Törlés] [👑 Admin jog]
│
├── Rendszer statisztikák
│     - Összes felhasználó, Admin-ok száma
│
└── Audit log (utils/audit_log.get_recent_events())
      - Legutóbbi biztonsági események
      - Szűrés: user, action típus, eredmény
```

---

## Streamlit oldalvédelem

Minden protected oldal elején:

```python
from utils.auth_helpers import require_authentication

require_authentication()
# Mellékhatás: ensure_user_dirs(user_id) automatikusan lefut
```

```python
# 10_Admin.py – extra admin ellenőrzés
from utils.auth_helpers import require_admin

require_admin()  # Ha nem admin → st.stop()
```
