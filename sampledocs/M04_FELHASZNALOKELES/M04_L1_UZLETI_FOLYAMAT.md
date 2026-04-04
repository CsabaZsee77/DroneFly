# L1 – Üzleti Folyamat – Felhasználókezelés és Hitelesítés

**Modul:** M04
**Szint:** L1 – Üzleti Folyamat
**Forrásdokumentumok:** ADMIN_GUIDE.md, FELHASZNALOI_UTMUTATO.md, Home.py, utils/user_manager.py, utils/session_manager.py, utils/request_info.py, data/audit_log.json

---

## Fő cél

Felhasználók regisztrálása, hitelesítése és session kezelése, hogy a rendszer minden adatot a megfelelő felhasználóhoz kössön és az oldalak védelme biztosítva legyen. Az admin panel lehetővé teszi a felhasználók kezelését, jelszó visszaállítást és **modul-alapú hozzáférés kezelést**.

---

## Szereplők

| Szereplő | Szerepkör |
|----------|-----------|
| **Vendég (nem bejelentkezett)** | Csak a bejelentkezési/regisztrációs oldalhoz fér hozzá |
| **Felhasználó** | Bejelentkezett; csak az engedélyezett moduljaihoz tartozó oldalakat látja |
| **Admin** | Felhasználó + Admin panel (felhasználók kezelése, jelszó reset, modulok kezelése) |
| **UserManager** | Felhasználói adatok kezelése (data/users.json) |
| **SessionManager** | Cookie-alapú session token kezelése |
| **AuditLogger** | Login események és IP naplózása (`data/audit_log.json`) |
| **RequestInfo (utils/request_info.py)** | HTTP kérésből IP cím kinyerése |

---

## Képernyők

### Bejelentkezés/Regisztráció (Home.py – nem bejelentkezett állapot)

```
Home oldal (nem bejelentkezett)
├── Tab: "🔑 Bejelentkezés"
│     Felhasználónév + Jelszó mezők
│     [🔓 Bejelentkezés] gomb
│     "💡 Első látogatás? Regisztrálj!"
└── Tab: "📝 Regisztráció"
      Felhasználónév + Email + Teljes név + Jelszó + Jelszó megerősítés
      [📝 Regisztráció] gomb
```

### Bejelentkezett felhasználó Sidebar

```
Sidebar (minden oldalon)
├── 👤 Felhasználó szekció
│     Teljes név, Email, @username
│     (Admin badge ha admin)
├── 🔑 Jelszó Módosítás (összecsukható)
│     Jelenlegi + Új jelszó + Megerősítés
│     [💾 Módosítás] gomb
├── 📧 Email Módosítás (összecsukható)
│     Jelenlegi email, Új email + Jelszó megerősítés
│     [💾 Módosítás] gomb
├── 📧 Feliratkozás hírlevélre (checkbox)
│     Be: opt-in a hírlevél listára
│     Ki: leiratkozás (bármikor módosítható)
├── 🗑️ Fiók Törlése (összecsukható)
│     Figyelmeztetés (privát adatok törlése, publikus tartalmak maradnak)
│     [x] "Megértettem..." megerősítő checkbox
│     [🗑️ Fiók Törlés Kérelmezése] gomb
├── [🚪 Kijelentkezés] gomb
└── (Verziószám, Rendszer info, Kapcsolat)
```

---

## Üzleti események

### 1. Regisztráció

1. Vendég kitölti a regisztrációs formot:
   - Felhasználónév (min. 3 karakter)
   - Email cím
   - Teljes név
   - Jelszó (min. 6 karakter)
   - Jelszó megerősítés
2. Validáció (kliens oldal): jelszavak egyeznek?
3. `UserManager.register()` hívás:
   - Duplikált felhasználónév ellenőrzés
   - Duplikált email ellenőrzés
   - bcrypt jelszó hash generálás
   - User mentés `data/users.json`-ba
4. Admin értesítő email küldése (ha EMAIL_ENABLED)
5. "✅ Sikeres regisztráció!" üzenet → Átváltás bejelentkezés fülre

### 2. Bejelentkezés

1. Felhasználó megadja felhasználónevét és jelszavát
2. Rate limiting ellenőrzés (5 sikertelen kísérlet → 15 perces lock)
3. `UserManager.authenticate()` hívás:
   - Felhasználó keresése users.json-ban
   - bcrypt jelszó ellenőrzés
4. IP cím naplózása: `RequestInfo.get_client_ip()` → `data/audit_log.json`-ba mentés (user_id, ip, timestamp, event="login_success")
5. Session token generálása és cookie mentése
6. `st.session_state.user` beállítása
7. "✅ Szia, {teljes_név}!" üzenet + balloons animáció
8. Oldal újratöltése (rerun) → Bejelentkezett állapot

> **Megjegyzés:** Sikertelen bejelentkezési kísérlet esetén szintén naplózódik (event="login_failed", ip cím).

### 3. Automatikus session helyreállítás

Ha a felhasználó visszatér az oldalra (cookie-val):
1. Cookie olvasás (`SessionManager.get_session_cookie()`)
2. Token validáció (`SessionManager.initialize_session_from_cookie()`)
3. Ha érvényes token: `st.session_state.user` beállítása → Bejelentkezett
4. Ha lejárt/érvénytelen: Bejelentkezési form megjelenítése

### 4. Jelszó módosítás

1. Felhasználó kitölti a sidebar "Jelszó Módosítás" panel formját:
   - Jelenlegi jelszó
   - Új jelszó
   - Új jelszó megerősítése
2. `UserManager.change_password()` hívás:
   - Jelenlegi jelszó hitelesítése
   - Új jelszó bcrypt hash
   - users.json frissítés
3. "✅ Jelszó sikeresen módosítva!" üzenet

### 5. Kijelentkezés

1. Session token törlése (szerver oldalon: `SessionManager.delete_session()`)
2. Cookie törlése (böngészőből: `SessionManager.clear_session_cookie()`)
3. `st.session_state.user = None`
4. "👋 Sikeresen kijelentkeztél!" üzenet
5. Oldal újratöltése → Bejelentkezési form

### 6. Fiók törlés kérelmezése

1. Felhasználó a sidebar "🗑️ Fiók Törlése" expanderben elolvassa a figyelmeztetést:
   - Privát képek, modellek, datasetek törlésre kerülnek
   - Kreditek elvesznek
   - Publikus tartalmak (megosztott modellek, datasetek) megmaradnak
2. Felhasználó megerősíti a checkbox bepipálásával: "Megértettem, véglegesen törölni szeretném a fiókomat"
3. "🗑️ Fiók Törlés Kérelmezése" gomb megnyomása
4. `UserManager.request_deletion(user_id)` hívás:
   - `active = False` (azonnali kizárás)
   - `deletion_requested = True`
   - `deletion_requested_at = timestamp`
5. Admin értesítő email küldése: `EmailNotifier.send_deletion_request()`
6. Audit log: `DELETION_REQUESTED` esemény
7. Automatikus kijelentkezés (session + cookie törlés)
8. "A fiók törlés kérelmed fogadva" üzenet

> **Megjegyzés:** A törlés nem azonnali — az admin az Admin Panelben hajtja végre a végleges adattörlést.

### 7. Admin: Felhasználói adatok végleges törlése

1. Admin az Admin Panelben szűrhet "Törlésre vár" státuszra
2. A törlést kérő felhasználók 🗑️ badge-dzsel jelölve az expanderben
3. Admin rákattint a "🗑️ Felhasználói Adatok Végleges Törlése" gombra (megerősítő checkbox szükséges)
4. Végrehajtott műveletek:
   a. **Publikus tartalom archiválása:** publikus datasetek átmásolása → `data/public_archive/{user_id}/datasets/`
   b. **Publikus modellek anonimizálása:** owner átírása `deleted_user`-re (a modell elérhető marad)
   c. **Privát modellek törlése:** soft-delete + hard-delete (fájlok eltávolítva)
   d. **User könyvtár törlése:** `data/users/{user_id}/` teljes törlése
   e. **Kredit bejegyzés törlése:** `data/credits.json`-ból eltávolítás
   f. **User végleges törlése:** `data/users.json`-ból eltávolítás (hard delete)
5. Audit log: `USER_HARD_DELETED` esemény (admin username-mel)

### 8. Bejelentkezési értesítés (admin)

1. Sikeres bejelentkezés után (`authenticate()` → siker)
2. `EmailNotifier.send_login_notification()` hívás az admin email címére
3. Az értesítés tartalmazza: felhasználónév, teljes név, email, IP cím, időpont
4. **Minden** belépésnél küldünk emailt, beleértve az admin saját belépéseit is

> **Megjegyzés:** Alacsony forgalmi időszakra optimalizált — nagy felhasználószámnál érdemes összesítőre váltani.

### 9. Hírlevél feliratkozás kezelése

1. Felhasználó a sidebar "📧 Feliratkozás hírlevélre" checkboxot bepipálja
2. `UserManager.set_newsletter_opt_in(user_id, True)` hívás → `users.json` frissítés
3. "✅ Feliratkoztál a hírlevélre!" visszajelzés
4. Leiratkozás: checkbox kikapcsolása → `set_newsletter_opt_in(user_id, False)` → "ℹ️ Leiratkoztál"
5. Az adatok megmaradnak az összes többi felhasználói adat mellett (`users.json`)

### 10. Admin: Felhasználók kezelése (13_Admin.py)

**Elérhető csak adminnak:**
- Összes felhasználó listázása (felhasználónév, email, regisztráció dátuma, szerepkör)
- Felhasználó jelszó visszaállítása (admin megad új jelszót)
- Felhasználó törlése (fiók deaktiválása)
- **Felhasználói adatok végleges törlése** (hard delete — publikus tartalom archiválás + teljes adattörlés)
- Törlésre váró felhasználók szűrése és jelölése (🗑️ badge)
- Admin jogosultság adása
- **GeoTIFF követelmény kapcsoló** (`require_geotiff` per-user beállítás):
  - Alapértelmezés: `True` (minden felhasználónál kötelező GeoTIFF)
  - Admin kikapcsolhatja egyes felhasználóknál (pl. fejlesztő/tesztelő)
  - Ha kikapcsolt: a felhasználó nem-georeferált képpel is futtathat detekciót (1 kredit/futtatás fix)
- **Hírlevél feliratkozók CSV export:**
  - `UserManager.get_newsletter_subscribers()` → aktív, opt-in felhasználók listája
  - CSV: email, full_name, username, created_at (BOM-os UTF-8, Excel kompatibilis)
  - Feliratkozott szám megjelenítve, opcionális listanézet (expanderben)
- **Modul hozzáférés kezelés** (per-user checkboxok):
  - Minden modul külön engedélyezhető / tiltható
  - Előre definiált bundle-k gyors beállításhoz (lásd lent)
  - Változás azonnal életbe lép (következő oldalbetöltésnél)
- **Audit log megtekintése:**
  - Táblázatos nézet: felhasználónév, IP cím, esemény típusa, időbélyeg
  - Szűrhető esemény típusonként (login_success, login_failed)
  - Forrás: `data/audit_log.json`

---

## Modul rendszer

### Elérhető modulok

| Modul kulcs | Megjelenített név | Ikon | Tartalom |
|-------------|------------------|------|---------|
| *(alap)* | Parcellák | 🗺️ | Parcella kezelés, térkép, eredmények — minden bejelentkezett felhasználónak |
| `flight` | Repülési Terv | ✈️ | KMZ generálás, GSD kalkulátor, repülési előzmények (M07) |
| `odm` | Ortomozaik | 🛠️ | Nyers drónképekből GeoTIFF előállítás NodeODM-mel (M08) |
| `counting` | Tőszámlálás | 🌾 | AI alapú növényszámlálás, sliding window, export (M03) |
| `spectral` | Spektrális Elemzés | 📊 | NDVI, VARI, stressz térkép, idősor (M06) |
| `model` | Modell Fejlesztés | 🔬 | Annotálás, betanítás, modell registry (M01+M02+M05) |

### Előre definiált bundle-k (admin gyorsbillentyű)

| Bundle neve | Modulok |
|-------------|---------|
| **Alap** | `counting` |
| **Elemző** | `counting`, `spectral` |
| **Felmérő** | `flight`, `odm`, `counting`, `spectral` |
| **Fejlesztő** | `model` |
| **Teljes** | `flight`, `odm`, `counting`, `spectral`, `model` |

> **Megjegyzés:** A bundle csak admin gyorsbillentyű — a tényleges tárolás mindig az egyedi `modules` lista a users.json-ban. Bármilyen egyedi kombináció beállítható.

---

## Állapotok

### Felhasználói session állapotok

| Állapot | Feltétel | Teendő |
|---------|----------|--------|
| Nem bejelentkezett | Nincs érvényes session | Bejelentkezés vagy regisztráció |
| Bejelentkezett | Érvényes session token | Engedélyezett modulok elérhetők |
| Zárolt (rate limit) | 5 sikertelen kísérlet | 15 perces várakozás |
| Admin | Bejelentkezett + role="admin" | Admin panel + minden modul elérhető |

### Felhasználó adatok állapotai

| Állapot | Leírás |
|---------|--------|
| active | Normál felhasználó, modulok szerint korlátozott |
| admin | Teljes hozzáférés, modulok kezelése |
| locked | Rate limit miatt zárolva (ideiglenes) |

### Felhasználó beállítások (users.json mezők)

| Mező | Típus | Alapértelmezés | Leírás |
|------|-------|----------------|--------|
| `require_geotiff` | bool | `True` | Ha True: detekció csak GeoTIFF képen futtatható. Ha False: nem-georeferált képpel is engedélyezett (admin állítja). |
| `modules` | list | `[]` | Engedélyezett modulok listája. Üres lista = csak alap parcella oldal elérhető. Admin beállítja. |
| `newsletter_opt_in` | bool | `True` | Ha True: a felhasználó feliratkozott a hírlevélre. Mező hiánya esetén is True (opt-out alapú: meglévő userek automatikusan feliratkozottak). Felhasználó a sidebar checkboxával állítja. |
| `deletion_requested` | bool | `False` | Ha True: a felhasználó kérte fiókja törlését. Admin az Admin Panelben hajtja végre a végleges törlést. |
| `deletion_requested_at` | str | `None` | ISO timestamp: a törlés kérelmezésének időpontja. |

---

## Végállapotok

1. **Sikeres bejelentkezés:** Session létrejön, cookie mentve, oldalak elérhetők, admin email értesítés küldve
2. **Sikertelen bejelentkezés:** Hibaüzenet, rate limit counter növel
3. **Zárolt account:** 15 perc várakozás után feloldódik automatikusan
4. **Sikeres regisztráció:** Fiók létrejön, admin értesítő email küldve
5. **Duplikált regisztráció:** ValueError: "Felhasználónév/Email már foglalt"
6. **Fiók törlés kérelmezve:** Fiók inaktívra állítva, admin email értesítés küldve, felhasználó kijelentkeztetve
7. **Felhasználó véglegesen törölve:** Publikus tartalom archiválva/anonimizálva, privát adatok törölve, users.json-ból eltávolítva

---

## Kapcsolódó modulok

- → **M01 Annotation:** Felhasználóhoz kötött projektek — `model` modul szükséges
- → **M02 Training:** Felhasználóhoz kötött training job-ok — `model` modul szükséges
- → **M03 Counting:** Kredit egyenleg, modell láthatóság — `counting` modul szükséges
- → **M05 Modell Nyilvántartás:** Modell tulajdonos (owner_id) — `model` modul szükséges
- → **M06 Parcella Elemzés:** Spektrális indexek, NDVI — `spectral` modul szükséges
- → **M07 Repülési Terv:** KMZ generálás — `flight` modul szükséges
- → **M08 ODM Ortomozaik:** Képfeldolgozás — `odm` modul szükséges
