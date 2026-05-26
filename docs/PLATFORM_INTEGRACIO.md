# DroneFly — Platform Integráció és Szinkronizáció

**Utolsó frissítés:** 2026-04-22
**Kapcsolódó rendszer:** [Dronterapia](https://app.dronterapia.hu) — lásd `Dronterapia/docs/PLATFORM_INTEGRACIO.md`
**Jövőbeli terv:** Mission Hub (`hub.dronterapia.hu`)

---

## 1. Áttekintés

A DroneFly Android tablet app és a Dronterapia webes platform repüléstervező moduljai (M07) összehangoltan működnek. A felhasználók mindkét felületen tervezhetnek, és a terveket szinkronizálhatják egymás között. A Crystal Sky tableten a tényleges terepi tervezés és a DJI drón közvetlen vezérlése történik; a webes platformon a parcellaalapú, irodai előtervezés, valamint az elemzési workflow (ODM, spektrális analízis) fut.

A szinkronizáció kétirányú: web → tablet és tablet → web egyaránt lehetséges.

---

## 2. Háromrétegű repülési terv rendszer

```
Réteg 0 — Terület (Polygon)
  Csak a határvonal, semmilyen repülési beállítás nélkül.
  Forrás: Dronterapia M06 parcella, MePAR import, kézi rajzolás.
  Formátum: GeoJSON Polygon (szabványos, bármilyen GIS eszköz olvassa).
  A DroneFly-ban: kézzel rajzolt polygon a térképen; opcionálisan
  szinkronizált Dronterapia parcella.

Réteg 1 — Repülési Program (.flightprogram.json)
  A polygon + az összes repülési beállítás (GSD, átfedések, sebesség,
  irány, rács mód, akadályok, stb.).
  Ez az elsődleges mentési, szerkesztési és szinkronizálási egység.
  A waypointok NEM tárolódnak — betöltéskor a rendszer újragenerálja.

Réteg 2 — Repülési Útvonal (Mission)
  A generált waypointok listája.
  Formátum: Litchi CSV (48 oszlop), WPML KMZ, DJI RC.
  Nem mentjük tartósan — a Repülési Programból bármikor újraállítható.
  A DJI SDK feltöltése előtt generálódik.
```

---

## 3. A `.flightprogram.json` séma

A `.dronefly.json` formátumot ez az egységes séma váltja fel. A fájlkiterjesztés `.flightprogram.json`.

```json
{
  "version": "1.0",
  "format": "dronterapia_flight_program",

  "metadata": {
    "id": "uuid-v4",
    "name": "Búza tábla észak — 2026-04-21",
    "created_at": "2026-04-21T08:00:00Z",
    "updated_at": "2026-04-21T09:15:00Z",
    "source_system": "dronefly",
    "user_id": "string vagy null"
  },

  "parcel": {
    "parcel_id": "uuid vagy null",
    "name": "string vagy null",
    "geometry": {
      "type": "Polygon",
      "coordinates": [[[lon, lat], [lon, lat], "..."]]
    }
  },

  "drone": {
    "drone_id": "phantom_4_pro_v1",
    "name": "Phantom 4 Pro v1",
    "camera": {
      "sensor_width_mm": 13.2,
      "sensor_height_mm": 8.8,
      "focal_length_mm": 8.8,
      "image_width_px": 5472,
      "image_height_px": 3648
    }
  },

  "flight_settings": {
    "gsd_cm": 3.0,
    "altitude_m": 80.0,
    "speed_ms": 7.0,
    "front_overlap_percent": 80.0,
    "side_overlap_percent": 75.0,
    "heading_deg": 0.0,
    "grid_mode": "single",
    "crosshatch_heading_deg": 90.0,
    "terrain_following": false,
    "offset_m": 0.0,
    "return_home": true,
    "start_corner": "auto"
  },

  "obstacles": [
    {
      "id": "obst1",
      "label": "obst1",
      "center": { "latitude": 47.124, "longitude": 19.655 },
      "radius_m": 10.0,
      "height_m": 8.0
    }
  ]
}
```

### Séma mezők leírása

#### `parcel`
| Mező | Típus | Leírás |
|------|-------|--------|
| `parcel_id` | string \| null | Dronterapia parcella UUID. Null, ha a tableten kézzel rajzolt polygon. |
| `name` | string \| null | Emberi olvasható név. Null esetén import során Dronterapia auto-generálja. |
| `geometry` | GeoJSON Polygon | Mindig beágyazva tárolódik — offline működéshez és konzisztenciához. Ha a parcellahatár megváltozik a weben, a régi repülési program saját geometriáját őrzi. |

#### `flight_settings`
| Mező | Típus | Értékek / Leírás |
|------|-------|-----------------|
| `gsd_cm` | float | Talalfelbontás cm/pixel |
| `altitude_m` | float | GSD-ből számítva, de mentéskor explicit tárolódik |
| `speed_ms` | float | 3–12 m/s (DroneFly limit) |
| `front_overlap_percent` | float | Menetirány szerinti átfedés % |
| `side_overlap_percent` | float | Keresztirányú átfedés % |
| `heading_deg` | float | Rács iránya 0–360° |
| `grid_mode` | string | `"single"` egyszeres rács \| `"crosshatch"` kettős rács |
| `crosshatch_heading_deg` | float | Második rács iránya. Alap: `heading_deg + 90`. Felülírható (pl. 45° esetén). |
| `terrain_following` | bool | SRTM alapú terep-követés be/ki |
| `offset_m` | float | Polygon-túlrepülési buffer méterben |
| `return_home` | bool | Hazatérés misszió végeztével |
| `start_corner` | string \| int | Lásd alább |

#### `start_corner` — indulási sarok optimalizálás

A serpentine rács 4 természetes belépési sarokkal rendelkezik, a rács koordinátarendszeréhez képest relatívan (nem égtáj szerint — forgó rácsokhoz is helyes):

```
Sarok 0 ──────────────→ Sarok 1
  │    (heading irány)         │
  ▼                            │
Sarok 3 ←────────────── Sarok 2
```

| Érték | Leírás |
|-------|--------|
| `"auto"` | DJI SDK drón GPS alapján a legközelebbi sarok választódik feltöltéskor. Tervezéskor a UI mind a 4 lehetőséget megmutatja preview módban. |
| `0`, `1`, `2`, `3` | Manuálisan rögzített sarok (0 = rács bal-felső sarka). |

Feltöltési logika `"auto"` esetén: `MissionUploader.java` lekéri a drón GPS-t a DJI SDK-tól, kiszámolja a 4 sarokpont távolságát, a legközelebbivel hívja meg a `GridMissionGenerator`-t.

#### `obstacles`
| Mező | Típus | Leírás |
|------|-------|--------|
| `id` | string | Rendszer által generált, sorszámozott (`obst1`, `obst2`, ...). Nem szerkeszthető, belső hivatkozáshoz. |
| `label` | string | Felhasználó által szerkeszthető megnevezés. Alap: azonos az `id`-vel. |
| `center` | lat/lon | Akadály középpontja |
| `radius_m` | float | Kizárási zóna sugara méterben |
| `height_m` | float | Akadály magassága méterben |

---

## 4. DroneFly-specifikus változtatási terv

### 4.1 Fájlformátum migráció

| Régi | Új |
|------|----|
| `.dronefly.json` | `.flightprogram.json` |
| `ProjectManager.java` | Sémaváltás az új struktúrára |

A meglévő `.dronefly.json` fájlok betöltéskor automatikusan konvertálódnak az új sémára. A konverzió:
- `polygon` → `parcel.geometry`
- `startPoint` → `flight_settings.start_corner = 0` (rögzített sarok, legközelebbi a start ponthoz)
- `config.*` → `flight_settings.*` (mezőnév-leképzés)
- `obstacles` → akadályok; `id` = `"obst" + sorszám`, `label` = `id`
- `parcel_id = null`, `parcel.name = null`
- `source_system = "dronefly"`

Az elmentett fájlok az új kiterjesztéssel kerülnek ki: `<external-files>/missions/*.flightprogram.json`.

### 4.2 Crosshatch rács mód

- Az M01 UI-ban új toggle kapcsoló: **Kettős rács** (`grid_mode`)
- Ha be van kapcsolva, megjelenik a `crosshatch_heading_deg` csúszka (alap: `heading_deg + 90`)
- Az M02 `GridMissionGenerator` két egymástól független waypointlistát generál
- Crosshatch módban a waypointok fűzése: 1. rács teljes útvonala → 2. rács teljes útvonala
- A két rács `start_corner`-je egymástól független (mindkettő `"auto"` alap)
- Az M04 feltöltésnél: ha a waypontok száma meghaladja a 99-es MSDK v4 limitet, a második rács külön misszióként töltendő fel

### 4.3 Akadályok UI bővítése

- Akadály hozzáadásakor az `id` automatikusan `"obst" + (lista mérete + 1)`
- A `label` szerkeszthető mező az akadály panelben (kezdőértéke az `id`)
- Az `id` nem változtatható (belső azonosító)

### 4.4 Szinkronizáció — online mód

Ha a tablet internet-kapcsolattal rendelkezik, a felhasználó bejelentkezhet a Dronterapia / Mission Hub platformba.

#### Bejelentkezési folyamat (Username + Jelszó)

```
1. Felhasználó: "Bejelentkezés" gomb DroneFly-ban
2. Megjelenik egy AlertDialog: felhasználónév + jelszó beviteli mezők
3. DroneFly POST https://app.dronterapia.hu/api/auth/login
   Body: {"username": "...", "password": "..."}
4. Sikeres válasz: {"access_token": "...", "username": "..."}
5. Token tárolva: Android SharedPreferences
6. Token érvényes marad offline módban is — nem kell újra bejelentkezni
```

A Crystal Sky tabletnek nincs kamerája, QR-kód nem olvasható — ezért közvetlen username/password alapú bejelentkezés kerül alkalmazásra.

#### Mit szinkronizál a DroneFly?

| Irány | Adat | Leírás |
|-------|------|--------|
| Web → Tablet | Parcellák (Réteg 0) | A felhasználó Dronterapia parcelláinak geometriái offline cache-be kerülnek |
| Web → Tablet | Repülési programok (Réteg 1) | Webes felületen létrehozott `.flightprogram.json` fájlok letöltése |
| Tablet → Web | Repülési programok (Réteg 1) | Tableten tervezett missziók feltöltése a Dronterapia / Hub fiókba |
| Tablet → Web | Polygon (Réteg 0) | Ha a tableten kézzel rajzolt polygon nem kapcsolódik meglévő parcellához, feltöltéskor Dronterapia új parcellát hoz létre (`parcel_id = null` importálásával) |

#### Offline működés

A szinkronizált adatok helyi JSON fájlokban tárolódnak az eszközön:
- `<external-files>/sync/parcels.json` — letöltött parcellák listája
- `<external-files>/missions/` — helyi repülési programok (`.flightprogram.json`)

Offline módban minden funkció elérhető, a szinkronizáció csak online esetén fut.

---

## 5. Offline-first architektúra és kapcsolatkezelés

### 5.1 Alapelv

A DroneFly terepi eszköz — az alkalmazásnak internet-kapcsolat nélkül is teljes funkcionalitással kell működnie. Az online funkciók (szinkronizálás) csak ráadás lehetőségek, nem feltételek. Semmilyen hálózati hívás nem blokkolhatja a UI-t, és kapcsolatvesztés esetén az app folytatja a munkát.

```
Kapcsolat nélkül:  Minden helyi funkció elérhető (térkép, tervezés, DJI feltöltés)
Bejelentkezve + online:  Szinkronizálás elérhető
Bejelentkezve + offline:  Helyi munka folytatható, szinkronizálás szünetel
Kijelentkezve:  Csak helyi funkciók, nincs sync UI
```

### 5.2 Kapcsolat állapotgép

Implementáció: `NetworkMonitor.State` enum (OFFLINE, ONLINE), `AuthManager.isAuthenticated()` SharedPreferences-ellenőrzés.

```
         ┌─────────────────────────────────────────┐
         │              OFFLINE                    │
         │  - Teljes lokális funkció               │
         │  - Sync UI disabled                     │
         │  - Bejelentkezési kísérlet nem indul    │
         └──────────────┬──────────────────────────┘
                        │ ConnectivityManager: hálózat megjelent
                        ▼
         ┌─────────────────────────────────────────┐
         │         ONLINE, NEM BEJELENTKEZETT       │
         │  - Sync UI nem látható                  │
         │  - "Bejelentkezés" gomb aktív            │
         └──────────────┬──────────────────────────┘
                        │ username/password login sikeres
                        ▼
         ┌─────────────────────────────────────────┐
         │         ONLINE, BEJELENTKEZVE            │◄──┐
         │  - Sync gomb aktív                      │   │ hálózat visszatér
         │  - Manuális szinkronizálás              │   │ (token megmarad)
         └──────────────┬──────────────────────────┘   │
                        │ hálózat megszakad             │
                        ▼                               │
         ┌─────────────────────────────────────────┐   │
         │     OFFLINE, BEJELENTKEZVE (cached)      │───┘
         │  - Token SharedPreferences-ből elérhető │
         │  - Bejelentkezés megmarad offline után  │
         │  - Sync UI disabled, toast: "Offline"   │
         │  - Helyi munkavégzés zavartalanul megy  │
         │  - Dirty flag: sync_pending: true        │
         └─────────────────────────────────────────┘
```

### 5.3 Kapcsolat-figyelés (Android)

`NetworkMonitor.java` — `ConnectivityManager.NetworkCallback` alapon (API 23+) és `getActiveNetworkInfo()` fallback (API 22):

- `onAvailable()` → State = ONLINE; listener értesítése
- `onLost()` → State = OFFLINE; listener értesítése
- `hasActiveNetwork()` — API 22 workaround: `ConnectivityManager.getActiveNetwork()` API 23+, ezért `getActiveNetworkInfo()` deprecated API-t használjuk Crystal Sky Android 5.1-en

Nincs LiveData — közvetlen `NetworkMonitor.Listener` interface callback. Nincs polling, nincs sleep loop.

### 5.4 Token és session kezelés

| Állapot | Tárolás |
|---------|---------|
| Bearer access token | `SharedPreferences` (sima, nem titkosított — Crystal Sky eszközspecifikus) |
| Bejelentkezett felhasználónév | `SharedPreferences` |
| Repülési programok | `<external-files>/missions/*.flightprogram.json` |

**Token megmaradása:** a token offline állapotban is megmarad a SharedPreferences-ben; bejelentkezés után nem szükséges újra belépni, amíg a token érvényes. Token lejárat kezelés (F-S13) egyelőre nincs implementálva — HTTP 401 esetén a sync csendben meghiúsul, a dirty flag megmarad.

**Dirty flag:** ha offline módban (bejelentkezve) a felhasználó módosít vagy létrehoz repülési programot, a fájl `metadata` szekciójában `"sync_pending": true` kerül. Kapcsolat visszatérésekor a `SyncManager` automatikusan feltölti a dirty fájlokat.

```json
"metadata": {
  "id": "...",
  "sync_pending": true,
  "last_synced_at": "2026-04-21T08:00:00Z"
}
```

### 5.5 Szinkronizálás — csak élő kapcsolat esetén

A szinkronizálás feltételei mindegyiknek teljesülnie kell:

1. `NetworkState == ONLINE`
2. Érvényes, nem lejárt JWT token
3. Nincs folyamatban lévő DJI misszió (biztonság: repülés közben nem fut háttérhálózati terhelés)

**Szinkronizálási trigger pontok:**
- App indulás (ha 1+2+3 teljesül)
- Kapcsolat visszatér (ha 1+2+3 teljesül)
- Felhasználó explicit "Szinkronizálás" gomb
- Repülési program mentésekor (ha 1+2 teljesül és nincs misszió)

**Szinkronizálás mindig aszinkron** — `AsyncTask` vagy `ExecutorService` háttérszálon, soha nem UI szálon. A UI soha nem vár hálózati hívásra.

### 5.6 UI viselkedés kapcsolat szerint

| Elem | Offline, nem bejelentkezett | Offline, bejelentkezett | Online, bejelentkezett |
|------|---------------------------|------------------------|----------------------|
| Sync gomb | Rejtve | Disabled + "Offline" ikon | Aktív |
| Bejelentkezés gomb | Disabled + tooltip | Rejtve (már be van lépve) | Rejtve |
| Parcella lista (szinkronizált) | Üres vagy cache | Cache-ből töltve | Szerver + cache frissítve |
| Repülési terv mentés | Helyi, `sync_pending: true` | Helyi, `sync_pending: true` | Helyi + háttérben feltölt |
| Token lejárat értesítés | — | Toast + belépési felszólítás (ha online lesz) | Automatikus refresh |

### 5.7 Hibakezelés hálózati hívásoknál

Minden API hívás:
- **Timeout:** 10 s kapcsolódási timeout, 30 s olvasási timeout
- **Retry:** exponenciális visszalépéssel, max 3 kísérlet (csak szinkronizáló hívásokra, nem auth-ra)
- **Hiba esetén:** silent fail a háttérben; a dirty flag megmarad; toast csak ha felhasználó explicit triggerelte a syncet
- **HTTP 401:** token érvénytelen → kijelentkeztetés + értesítés → offline mód (token törlés)
- **HTTP 5xx:** szerver hiba → retry + dirty flag megőrzés

---

## 6. Kapcsolódó rendszerek

### Dronterapia (app.dronterapia.hu)
Mezőgazdasági célú drón platform. Parcellakezelés (M06), repüléstervezés (M07), ODM ortomozaik feldolgozás (M08), spektrális analízis, tőszámlálás. A repüléstervező modul változtatási tervét lásd: `Dronterapia/docs/PLATFORM_INTEGRACIO.md`.

### Mission Hub (hub.dronterapia.hu) — jövőbeli
Általános repüléstervező platform, nem mezőgazdasági célú. Kezdetben azonos repüléstervező funkcióval, mint a Dronterapia M07. Kibővített jövőbeli funkciók: videós/cinematic útvonaltípusok, dinamikus pozíció-alapú útvonalsablonok. A DroneFly mindkét platformhoz csatlakozhat — ugyanazon az autentikációs rétegen keresztül.

---

## 6. Exportálható formátumok (változatlan)

| Formátum | Irány | Leírás |
|----------|-------|--------|
| Litchi CSV (48 oszlop) | Export | Phantom 4 Pro v1 + régebbi DJI drónok |
| WPML KMZ | Export | Mini 3/4 Pro, Air 3, Mavic 3 (Dronterapia-tól örökölt) |
| `.flightprogram.json` | Export / Sync | Repülési program (platform-független) |
| GeoJSON | Export | Polygon / parcella határok |

---

## 7. Fejlesztési feladatok állapota

| # | Feladat | Modul | Státusz |
|---|---------|-------|---------|
| F-S01 | `.dronefly.json` → `.flightprogram.json` formátum, `source_system: "dronefly"` mező (`ProjectManager.java`) | M03 | ✅ IMPLEMENTÁLVA (részben) |
| F-S02 | Crosshatch rács mód UI + generátor (`GridMissionGenerator.java`, `switchCrosshatch`, `sbCrosshatchAngle`) | M01, M02 | ✅ IMPLEMENTÁLVA |
| F-S03 | `start_corner = "auto"` logika feltöltésnél (`MissionUploader.java`) | M04 | 🔲 Nyitott |
| F-S04 | Akadály `label` szerkesztés teljes UI | M01 | 🔲 Nyitott |
| F-S05 | Bejelentkezés: username/password AlertDialog, `POST /api/auth/login`, Bearer token SharedPreferences-be | M06 AuthManager | ✅ IMPLEMENTÁLVA (username/password, nem device code) |
| F-S06 | Parcellaadatok letöltése és offline cache (`sync/parcels.json`) | M06 SyncManager | 🔲 Nyitott |
| F-S07 | Repülési programok feltöltése (POST/PUT döntés szerveres ID lista alapján) | M06 SyncManager | ✅ IMPLEMENTÁLVA |
| F-S08 | Parcella-kiválasztó UI az M01-ben (szinkronizált parcellákból polygon betöltés) | M01 | 🔲 Nyitott |
| F-S09 | `NetworkMonitor.java` — `ConnectivityManager.NetworkCallback`, OFFLINE/ONLINE állapot, API 22 workaround | M06 NetworkMonitor | ✅ IMPLEMENTÁLVA |
| F-S10 | Offline-first állapotgép — precondition ellenőrzés (online + auth + !flightActive) | M06 SyncManager | ✅ IMPLEMENTÁLVA (részben — 2 állapot: OFFLINE/ONLINE) |
| F-S11 | `sync_pending: true` dirty flag mentéskor (`ProjectManager.saveProject()`, `saveProjectToFile()`) | M06 SyncManager, ProjectManager | ✅ IMPLEMENTÁLVA |
| F-S12 | UI réteg: `btnLogin`, `btnSync`, `tvSyncStatus`, `networkListener` a `MissionPlannerActivity`-ben | M01 UI | ✅ IMPLEMENTÁLVA |
| F-S13 | Token lejárat ellenőrzés induláskor + HTTP 401 kezelés (kijelentkeztetés) | M06 SyncManager | 🔲 Nyitott |

### Technikai megjegyzések az implementációhoz

**Crystal Sky Android 5.1 (API 22) kompatibilitás:**
- `ConnectivityManager.getActiveNetwork()` API 23+ → ezért `getActiveNetworkInfo()` deprecated API-t használunk a `NetworkMonitor.hasActiveNetwork()` metódusban.

**SSL trust-all workaround:**
- A Let's Encrypt gyökértanúsítvány nem ismert a Crystal Sky Android 5.1 rendszerén → custom `TrustManager` (trust-all X509) szükséges az HTTPS kapcsolatokhoz (`AuthManager`, `SyncManager`).

**Szinkron hatókör:**
- A szinkronizáció kizárólag `.flightprogram.json` fájlokat kezeli. Parcellák szinkronizálása (F-S06) nyitott feladat.

**POST vs PUT döntés:**
- A `SyncManager` az upload előtt lekéri a szerver ID listáját (`GET /api/flight-programs`). Ha a helyi fájl `metadata.id`-je szerepel a szerveres listában → `PUT /api/flight-programs/{id}`. Ha nem → `POST /api/flight-programs`. A döntés alapja a szerveres ID lista, nem a helyi fájlban tárolt `id` mező megléte.
