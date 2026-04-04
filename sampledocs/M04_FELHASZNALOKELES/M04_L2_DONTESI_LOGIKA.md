# L2 – Döntési Logika – Felhasználókezelés és Hitelesítés

**Modul:** M04
**Szint:** L2 – Döntési Logika
**Forrásdokumentumok:** utils/user_manager.py, utils/session_manager.py, ADMIN_GUIDE.md

---

## Validációs szabályok

### Regisztrációkor

| Mező | Szabály | Hibaüzenet |
|------|---------|------------|
| Felhasználónév | Min. 3 karakter, egyedi | "Felhasználónév már foglalt!" / "Legalább 3 karakter szükséges" |
| Email | Egyedi (nem validált formátum) | "Email cím már regisztrált!" |
| Teljes név | Nem üres | "Minden mező kitöltése kötelező!" |
| Jelszó | Min. 6 karakter | "Legalább 6 karakter szükséges" |
| Jelszó megerősítés | Egyezik a jelszóval | "A jelszavak nem egyeznek!" |

### Bejelentkezéskor

| Feltétel | Érvényes | Kezelés |
|----------|----------|---------|
| Felhasználónév létezik | Igen | Folytatás |
| Jelszó egyezik | Igen | Session generálás |
| Felhasználónév üres | Nem | "Minden mező kitöltése kötelező!" |
| Jelszó üres | Nem | "Minden mező kitöltése kötelező!" |
| Rate limit elérve | Nem | "🚫 Fiók zárolva X percre!" |

### Jelszó módosításkor

| Feltétel | Érvényes | Kezelés |
|----------|----------|---------|
| Jelenlegi jelszó helyes | Igen | Folytatás |
| Jelenlegi jelszó helytelen | Nem | "Hibás jelenlegi jelszó!" |
| Új jelszó min. 6 karakter | Igen | — |
| Új jelszavak egyeznek | Igen | — |

---

## Döntési logika – Jelszó hitelesítés

### bcrypt hitelesítési folyamat

```python
import bcrypt

def authenticate(username: str, password: str) -> Optional[dict]:
    users = load_users()

    IF username NOT IN users:
        check_rate_limit(username)  # Rate limit növelés
        RETURN None

    user = users[username]
    stored_hash = user["password_hash"]

    # bcrypt összehasonlítás (timing-attack resistant)
    IF bcrypt.checkpw(password.encode(), stored_hash.encode()):
        reset_rate_limit(username)
        RETURN user_dict
    ELSE:
        increment_rate_limit(username)
        RETURN None
```

### Fallback: SHA256 (ha bcrypt nem elérhető)

```python
IF bcrypt is None:
    import hashlib
    stored = user["password_hash"]  # SHA256 hex
    input_hash = hashlib.sha256(password.encode()).hexdigest()
    RETURN stored == input_hash
```

> **FIGYELMEZTETÉS:** SHA256 fallback biztonságilag gyengébb. Éles rendszeren `pip install bcrypt` kötelező.

---

## Döntési logika – Rate Limiting

### Brute force védelem szabályai

```python
MAX_FAILED_ATTEMPTS = 5
LOCKOUT_DURATION = timedelta(minutes=15)

def check_rate_limit(username: str) -> None:
    attempts = load_login_attempts()
    user_attempts = attempts.get(username, {})

    failed_count = user_attempts.get("failed_count", 0)
    locked_until = user_attempts.get("locked_until")

    IF locked_until AND datetime.now() < locked_until:
        remaining = (locked_until - datetime.now()).seconds // 60
        RAISE ValueError(f"Fiók zárolva. Próbálja újra {remaining} perc múlva.")

    IF failed_count >= MAX_FAILED_ATTEMPTS:
        lock_account(username, LOCKOUT_DURATION)
        RAISE ValueError("Fiók zárolva 15 percre.")
```

### Rate limit adatok tárolása

```
data/login_attempts.json:
{
  "zsigmond": {
    "failed_count": 3,
    "last_attempt": "2026-02-15T14:30:00Z",
    "locked_until": null
  }
}
```

---

## Döntési logika – Session token kezelés

### Session generálás

```python
import secrets

def create_session(user: dict) -> str:
    token = secrets.token_urlsafe(32)  # 256-bit random token

    session_data = {
        "token": token,
        "user_id": user["user_id"],
        "username": user["username"],
        "created_at": datetime.now().isoformat(),
        "expires_at": (datetime.now() + SESSION_DURATION).isoformat()
    }

    save_session(token, session_data)
    return token
```

### Cookie beállítás

```python
def set_session_cookie(token: str) -> None:
    # JavaScript cookie beállítás Streamlit-ben
    # HttpOnly: False (Streamlit nem tud httponly cookie-t kezelni)
    # Secure: False (HTTP-n is működik dev környezetben)
    # SameSite: Strict
    expires = (datetime.now() + SESSION_DURATION).strftime(...)
    js_code = f"""
    document.cookie = "session_token={token}; expires={expires}; path=/; SameSite=Strict";
    """
    st.markdown(f"<script>{js_code}</script>", unsafe_allow_html=True)
```

### Session validálás

```python
def initialize_session_from_cookie() -> bool:
    token = get_session_cookie()
    IF NOT token:
        RETURN False

    session = load_session(token)
    IF NOT session:
        RETURN False

    IF datetime.now() > session["expires_at"]:
        delete_session(token)
        RETURN False

    user = user_manager.get_user_by_id(session["user_id"])
    st.session_state.user = user
    RETURN True
```

---

## Döntési logika – Admin jogosultságok

### Admin ellenőrzés

```python
def require_admin():
    user = st.session_state.get("user")
    IF NOT user:
        st.error("❌ Bejelentkezés szükséges!")
        st.stop()
    IF user.get("role") != "admin":
        st.error("❌ Admin jogosultság szükséges!")
        st.stop()
```

---

## Döntési logika – Modul-alapú hozzáférés

### Elérhető modul kulcsok

```python
VALID_MODULES = ["flight", "odm", "counting", "spectral", "model"]

MODULE_BUNDLES = {
    "alap":      ["counting"],
    "elemzo":    ["counting", "spectral"],
    "felmero":   ["flight", "odm", "counting", "spectral"],
    "fejleszto": ["model"],
    "teljes":    ["flight", "odm", "counting", "spectral", "model"],
}
```

### Modul ellenőrzés (utils/auth_helpers.py)

```python
def require_module(module_key: str) -> None:
    """Oldal tetején hívandó, ha a funkció modul-kapuzott."""
    user = st.session_state.get("user")

    IF NOT user:
        st.warning("⚠️ Bejelentkezés szükséges!")
        st.stop()

    # Admin mindent lát, modul-ellenőrzés nélkül
    IF user.get("role") == "admin":
        RETURN

    user_modules = user.get("modules", [])

    IF module_key NOT IN user_modules:
        st.warning(f"⚠️ Ez a funkció a **{MODULE_DISPLAY[module_key]}** modulban érhető el.")
        st.info("Modul aktiváláshoz kérjük lépjen kapcsolatba az adminisztrátorral.")
        st.stop()

MODULE_DISPLAY = {
    "flight":   "✈️ Repülési Terv",
    "odm":      "🛠️ Ortomozaik",
    "counting": "🌾 Tőszámlálás",
    "spectral": "📊 Spektrális Elemzés",
    "model":    "🔬 Modell Fejlesztés",
}
```

### Navigáció szűrése modulok alapján (Home.py)

```python
def build_navigation(user: dict) -> dict:
    modules = user.get("modules", [])
    is_admin = user.get("role") == "admin"

    # Alap oldalak — mindig látható
    felmeres = [parcellak_page, eredmenyek_page, terkep_page]

    if is_admin or "flight" in modules:
        felmeres.insert(1, repulesi_terv_page)
    if is_admin or "odm" in modules:
        felmeres.insert(2, ortomozaik_page)
    if is_admin or "counting" in modules:
        felmeres.append(toszamlales_page)
    if is_admin or "spectral" in modules:
        felmeres.append(parcella_elemzes_page)

    nav = {"Felmérés": felmeres}

    if is_admin or "model" in modules:
        nav["Modell"] = [annotacias_page, betanitas_page, modellek_page]

    if is_admin:
        nav["Rendszer"] = [admin_page]

    return nav
```

### Admin: modul hozzárendelés (10_Admin.py)

```python
def render_module_editor(target_user: dict) -> None:
    current_modules = target_user.get("modules", [])

    st.subheader("Modul hozzáférés")

    # Bundle gyorsgombok
    col1, col2, col3 = st.columns(3)
    if col1.button("Alap"):    new_modules = MODULE_BUNDLES["alap"]
    if col2.button("Felmérő"): new_modules = MODULE_BUNDLES["felmero"]
    if col3.button("Teljes"):  new_modules = MODULE_BUNDLES["teljes"]

    # Egyedi checkboxok
    new_modules = []
    for key in VALID_MODULES:
        if st.checkbox(MODULE_DISPLAY[key], value=(key in current_modules)):
            new_modules.append(key)

    if st.button("💾 Mentés"):
        user_manager.update_modules(target_user["user_id"], new_modules)
        st.success("✅ Modul hozzáférés frissítve!")
```

### Admin felhasználó beállítása (set_admin_user.py)

```bash
python set_admin_user.py zsigmond
```

Ez a script közvetlenül a `data/users.json`-ban állítja a `role` mezőt `"admin"`-ra.

---

## Döntési logika – Authentikáció minden oldalon

### require_authentication() (utils/auth_helpers.py)

Minden protected oldal (Annotation, Training, Counting, Admin) elején hívódik:

```python
def require_authentication():
    if 'user' not in st.session_state:
        st.session_state.user = None

    if not st.session_state.user:
        # Próbálja cookie-ból helyreállítani
        session_manager.initialize_session_from_cookie()

    if not st.session_state.user:
        st.warning("⚠️ Bejelentkezés szükséges!")
        st.stop()  # Oldal renderelés megáll
```

---

## Felhasználói adatmodell (users.json)

```json
{
  "zsigmond": {
    "user_id": "uuid-v4",
    "username": "zsigmond",
    "email": "sajat@email.hu",
    "full_name": "Zsigmond Csaba",
    "password_hash": "$2b$12$bcrypt_hash_here",
    "role": "admin",
    "created_at": "2026-01-01T10:00:00Z",
    "last_login": "2026-02-15T09:30:00Z"
  }
}
```

---

## Biztonsági szempontok

| Terület | Implementáció | Megjegyzés |
|---------|---------------|------------|
| Jelszó tárolás | bcrypt (per-user salt) | Biztonságos |
| Session token | 256-bit random (secrets.token_urlsafe) | Biztonságos |
| Cookie | SameSite=Strict | CSRF-védett |
| Rate limiting | 5 kísérlet / 15 perc | Brute force ellen |
| Admin ellenőrzés | Minden admin oldalon | Szerveroldali |
| HTTPS | Nem kötelező (Streamlit) | HTTPS ajánlott prod-ban |
