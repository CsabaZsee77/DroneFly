# L2 – Döntési Logika – Dronterapia Szinkron

**Modul:** M06
**Szint:** L2 – Döntési Logika
**Verzió:** v2.2.3
**Létrehozva:** 2026-04-21
**Utolsó módosítás:** 2026-04-30
**Státusz:** ✅ Implementálva (v2.2.3)

---

## 1. Mikor kell szinkronizálni? — Precondition döntési fa

```
syncAll() meghívva (gombnyomás vagy programból)
  │
  ▼
NetworkMonitor.getState() == ONLINE?
  │ NEM → tvSyncStatus: "Nincs hálózat" → return
  │ IGEN
  ▼
AuthManager.isAuthenticated()?
  │ NEM → tvSyncStatus: "Nincs bejelentkezve" → return
  │ IGEN
  ▼
MissionPlannerActivity.missionRunning == true?
  │ IGEN → tvSyncStatus: "Repülés folyamatban — sync szünetel" → return
  │ NEM
  ▼
Sync indul: serverList lekérés → upload → download
```

**Indoklás a missionRunning feltételre:**
A repülés aktív DJI SDK kapcsolatot, telemetria-polling-ot és valós idejű misszió-vezérlést
jelent. Párhuzamos HTTP forgalom Crystal Sky-on interferálhat a DJI SDK kommunikációjával,
és hálózati késleltetést okozhat a vezérlőfolyamatban. Ezért sync közben sosem fut misszió,
és misszió közben sosem fut sync.

---

## 2. POST vs PUT döntés

A feltöltési irány (új feltöltés vs. frissítés) a szerveres ID lista alapján dől el —
nem a helyi fájl belső állapota (pl. `metadata.id` megléte) alapján.

```
uploadDirtyFiles(serverIds: Set<String>) meghívva
  │
  Minden helyi .flightprogram.json fájlra:
    │
    ▼
  fájl.metadata.sync_pending == true
    VAGY fájl.metadata.source_system == "dronefly"?
    │ NEM → kihagyva (nem kell feltölteni)
    │ IGEN
    ▼
  fájl.metadata.id szerepel a serverIds listában?
    │ IGEN → PUT /api/flight-programs/{id}  (szerver ismeri, frissítjük)
    │ NEM  → POST /api/flight-programs       (szerver nem ismeri, létrehozzuk)
    │
    ▼
  HTTP 200/201 válasz?
    │ IGEN → fájl metaadatai frissítve:
    │          sync_pending = false
    │          last_synced_at = most
    │          fájl újramentve lemezre
    │ NEM  → log + skip (dirty flag megmarad, következő sync újrapróbálja)
```

**Miért szerveres lista alapján és nem helyi ID alapján?**
A helyi fájl mindig rendelkezik `metadata.id`-vel (UUID, mentéskor generálva). Ezért
az ID meglétéből nem derül ki, hogy a szerver ismeri-e már ezt az ID-t. A szerveres
lista az egyetlen megbízható forrás.

---

## 3. Dirty flag logika

A `sync_pending: true` flag jelzi, hogy a helyi fájl módosult és a szerverre még
nem lett feltöltve (vagy a szerveren régebbi verzió van).

```
Dirty flag beállítása:
  ProjectManager.saveProject() hívódik
    → JSON mentésekor: metadata.sync_pending = true
    → metadata.updated_at = mostani időbélyeg (UTC ISO 8601)
  
  ProjectManager.saveProjectToFile() (közvetlen fájlírás)
    → szintén: metadata.sync_pending = true

Dirty flag törlése:
  SyncManager: sikeres POST vagy PUT után
    → metadata.sync_pending = false
    → metadata.last_synced_at = mostani időbélyeg
    → fájl újramentve a frissített metaadatokkal

Dirty fájlok azonosítása upload előtt:
  for (File f : missionsDir.listFiles()) {
    if (f.getName().endsWith(".flightprogram.json")) {
      JSONObject metadata = parse(f).getJSONObject("metadata");
      boolean pending = metadata.optBoolean("sync_pending", false);
      String src = metadata.optString("source_system", "");
      if (pending || "dronefly".equals(src)) {
        → feltöltendő
      }
    }
  }
```

**Megjegyzés:** az `source_system == "dronefly"` feltétel azért szerepel, mert
az első mentésnél a fájl még nem rendelkezik `sync_pending: true` értékkel (csak
akkor, ha az újabb verziójú `saveProject()` futott). A `source_system` biztonsági
háló az első szinkronizáláshoz.

---

## 4. Letöltési feltételek

```
downloadNewFiles(serverList) meghívva
  │
  Minden szerveres program rekordra ({id, name, updated_at}):
    │
    ▼
  Létezik helyi fájl ugyanezzel az id-vel?
    │ NEM → letöltés (GET /api/flight-programs/{id}) → helyi fájlba mentés
    │ IGEN
    ▼
  serverRecord.updated_at > localFile.metadata.updated_at?
    │ IGEN → letöltés → helyi fájl felülírása
    │ NEM  → helyi verzió frissebb vagy azonos → kihagyva
```

**Időbélyeg összehasonlítás:**
Mindkét oldal UTC ISO 8601 formátumot használ (`2026-04-21T09:15:00Z`).
String-összehasonlítás elegendő (lexikografikusan helyes az ISO 8601 formátum).

---

## 5. Konfliktusstratégia

Az M06 implementáció nem tartalmaz merge logikát. A konfliktuskezelés elve:

**Szerver az igazság, ha a szerveres verzió frissebb.**

```
Lehetséges konfliktus-szcenáriók:

1. Offline szerkesztés + szerveres szerkesztés ugyanazon a fájlon:
   → Szerver updated_at > helyi updated_at?
     → Letöltés felülírja a helyi verziót
     → A helyi offline módosítások elvesznek
   → Szerver updated_at < helyi updated_at?
     → Helyi verzió feltöltődik → szerveres verzió felülíródik

2. Szerveren törölt fájl:
   → Nem jelenik meg a GET /api/flight-programs listában
   → Helyi fájl megmarad (törlés-szinkron nincs implementálva)

3. Helyi fájl id nélkül (régi formátum):
   → source_system == "dronefly" → POST mint új fájl
   → Szerver generál új id-t → helyi fájl frissítve a szerveres id-vel
```

**Indoklás:** A terepi tabletnek nincs "tulajdonosi" jogköre — a Dronterapia platform
az elsődleges adatforrás. A kezelő a tableten tervez, a szerverre tölt fel; ha valaki
a webfelületen módosítja ugyanazt a programot, az a szerveres verzió válik mérvadóvá.

---

## 6. Hálózati hiba döntési logika

```
HTTP hívás eredménye:
  │
  ├─ IOException (timeout, nincs kapcsolat):
  │    → log.e("SYNC", "Network error: " + e.getMessage())
  │    → dirty flag megmarad
  │    → tvSyncStatus: "Szinkronizálási hiba"
  │
  ├─ HTTP 200 / 201 / 204:
  │    → Sikeres → dirty flag törlés / fájl írás
  │
  ├─ HTTP 401:
  │    → Érvénytelen token (lejárt vagy visszavont)
  │    → authManager.logout() — token törlése SharedPreferences-ből
  │    → uploadDirtyFiles() visszatér -1-gyel
  │    → syncAll() / uploadPending() → SKIPPED_NO_AUTH eredmény
  │    → UI: "Lejárt token – jelentkezz be újra" (piros)
  │    → Dirty flag megmarad (következő bejelentkezés után szinkronizálható)
  │
  ├─ HTTP 40x (400, 403, 404, ...):
  │    → Log + skip
  │    → Dirty flag megmarad (nem próbáljuk újra automatikusan)
  │
  └─ HTTP 50x:
       → Log + skip
       → Dirty flag megmarad (következő manuális sync újrapróbálja)
```

**Retry stratégia:** Nincs automatikus retry az aktuális implementációban. Ha a sync
meghiúsul, a dirty flag megmarad és a következő manuális szinkronizáláskor újra megkísérli.

---

## 8. Terv törlési logika

A törlés a tervlista dialógból hosszú nyomással kezdeményezhető. A törlés azonnali —
nem sync-alapú, nincs szükség szinkronizálásra utána.

```
deleteProject(file) hívódik
  │
  ├─ ProjectManager.loadProject(file) → id kiolvasása
  │
  ├─ Ha file == currentPlanFile:
  │    → clearAll(), currentPlanFile = null, clearResumeState()
  │
  ├─ file.delete() — helyi fájl törlése
  │
  └─ id nem null?
       │ IGEN → SyncManager.deleteFromServer(id, callback)
       │          DELETE /api/flight-programs/{id}
       │          Authorization: Bearer <token>
       │          HTTP 200 / 204 → callback.onResult(true)
       │          Egyéb / Exception → callback.onResult(false)
       │
       │ NEM  → Toast: "Terv törölve"
       │
       └─ UI toast:
            true  → "Terv törölve (helyi + szerver)"
            false → "Helyi terv törölve (szerverről nem sikerült)"
```

**Szerver oldalon törölt tervek:** Ha a szerveren törlődik egy terv de a tableten megvan,
a szinkronizálás nem törli a helyi fájlt (törlés-propagálás nincs implementálva). A helyi
fájl megmarad és a következő sync újra feltölti, ha `sync_pending: true`.

---

## 7. Hálózati állapot döntési logika (NetworkMonitor)

```
API 23+ eszköz:
  ConnectivityManager.registerDefaultNetworkCallback(callback)
  onAvailable() → State = ONLINE
  onLost()      → State = OFFLINE

API 22 (Crystal Sky Android 5.1):
  ConnectivityManager.registerNetworkCallback() elérhető API 21-től,
  de getActiveNetwork() csak API 23+.
  
  hasActiveNetwork() metódus:
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      Network network = cm.getActiveNetwork();
      NetworkCapabilities nc = cm.getNetworkCapabilities(network);
      return nc != null && nc.hasCapability(NET_CAPABILITY_INTERNET);
    } else {
      // API 22 fallback — deprecated, de Crystal Sky-on ez az egyetlen lehetőség
      NetworkInfo ni = cm.getActiveNetworkInfo();
      return ni != null && ni.isConnected();
    }
```

**Megjegyzés az API 22 workaround-hoz:**
`ConnectivityManager.getActiveNetworkInfo()` deprecated API 28-ban, de Android 5.1-en
(API 22) ez az egyetlen megbízható módszer az aktív hálózati kapcsolat ellenőrzésére.
A `getActiveNetwork()` API 23 előtt nem létezik.
