# L4 – Tranzakciós és Párhuzamos Kezelés – Dronterapia Szinkron

**Modul:** M06
**Szint:** L4 – Tranzakciós és Párhuzamos Kezelés
**Verzió:** v2.1.0
**Létrehozva:** 2026-04-21
**Utolsó módosítás:** 2026-04-21
**Státusz:** ✅ Implementálva (v2.1.0)

---

## 1. Szálmodell — Single-thread ExecutorService

Mindhárom M06 manager osztály egyetlen dedikált háttérszálat használ:

```java
// NetworkMonitor.java, AuthManager.java, SyncManager.java mindegyikben:
private final ExecutorService executor = Executors.newSingleThreadExecutor();
```

**Miért single-thread és nem thread pool?**

- `AuthManager`: a login/logout műveletek sorban hajtódnak végre, párhuzamos login
  kísérletek nem kívánatosak (token felülírási versenyhelyzet)
- `SyncManager`: a `syncAll()` szekvenciális: lista lekérés → upload → download.
  Párhuzamos szinkronizálás ugyanazon fájlrendszerre kiolvasási/írási versenyhelyzetet
  okozna. Ha a felhasználó kétszer nyomja meg a Sync gombot, a második hívás sorba áll
  az elsőnél és annak befejezése után fut le.
- `NetworkMonitor`: a callback regisztráció nem igényel külön szálat — csak a belső
  állapotváltáshoz használja (setState dispatch)

**Thread safety — fájlrendszer:**
A `missionsDir`-ben lévő `.flightprogram.json` fájlokat kizárólag a következő szálak
érintik:
1. UI szál: `ProjectManager.saveProject()` szinkron fájlírás
2. Sync thread: `uploadDirtyFiles()` olvasás + `downloadNewFiles()` írás

Potenciális versenyhelyzet: ha a felhasználó éppen ment egy fájlt (`saveProject()`),
miközben a sync thread olvassa ugyanazt a fájlt. Az aktuális implementáció ezt nem
kezeli explicit lock-kal — a Crystal Sky-on a szinkronizálás ritkán fut párhuzamosan
mentéssel (a felhasználói workflow természetes sorban zajlik). Jövőbeli fejlesztésként
`synchronized (missionsDir)` vagy `ReentrantLock` alkalmazható.

---

## 2. Main thread callback — Handler + Looper

Minden M06 manager aszinkron hívása `ExecutorService` háttérszálon fut. Az eredmény
visszaközlése a UI szálra a következő mintával történik:

```java
// Minden callback visszatér a main thread-re
new Handler(Looper.getMainLooper()).post(() -> {
    callback.onSuccess(result);
    // VAGY
    callback.onError(message);
});
```

**AuthManager példa:**
```java
// executor thread-en fut:
int code = conn.getResponseCode();
if (code == 200) {
    // ... token parse ...
    new Handler(Looper.getMainLooper()).post(() -> callback.onSuccess(user));
} else {
    new Handler(Looper.getMainLooper()).post(() -> callback.onError("HTTP " + code));
}
```

**SyncManager példa:**
```java
// executor thread-en fut:
private void postComplete(SyncCallback cb, String msg) {
    new Handler(Looper.getMainLooper()).post(() -> cb.onComplete(msg));
}
private void postError(SyncCallback cb, String msg) {
    new Handler(Looper.getMainLooper()).post(() -> cb.onError(msg));
}
```

**NetworkMonitor példa:**
```java
// ConnectivityManager.NetworkCallback — az Android rendszer saját szálán hívja
// Ezért a listener értesítés explicit main thread-re kerül:
private void setState(State newState) {
    if (currentState != newState) {
        currentState = newState;
        new Handler(Looper.getMainLooper()).post(() ->
            listener.onNetworkStateChanged(newState));
    }
}
```

Az `MissionPlannerActivity`-ben a callback fogadó kód így mindig a UI szálán fut,
tehát közvetlenül módosíthatja a View-kat (`tvSyncStatus.setText()`, `btnSync.setEnabled()`).

---

## 3. syncAll() sorrend — egy HTTP munkamenetben

A szinkronizálás szekvenciális egy `executor.execute()` hívásban fut le:

```
executor.execute():
  │
  ▼
1. fetchServerList()
   GET /api/flight-programs
   → JSONArray serverList
   → Set<String> serverIds
   → Map<String, String> serverUpdatedAt
   │
   ▼
2. uploadDirtyFiles(token, serverIds)
   Minden dirty .flightprogram.json fájlra:
     → pushToServer(token, id, json, isOnServer)
       → POST VAGY PUT (szerveres lista alapján)
     → Ha sikeres: sync_pending = false, fájl újramentve
   │
   ▼
3. downloadNewFiles(token, serverList, serverUpdatedAt)
   Minden szerveres rekordra:
     → helyi fájl keresése id alapján
     → updated_at összehasonlítás
     → Ha szerveres frissebb: GET /api/flight-programs/{id} → helyi fájl
   │
   ▼
postComplete(callback, "Utolsó sync: HH:mm:ss")
```

**Miért upload előbb, download utóbb?**
Ha a szerveren módosítottak egy fájlt, amelyet a tableten is módosítottak (dirty flag),
akkor az upload elsőbbsége a helyi (terepi) verziót tölti fel. Ezt követi a szerveres
letöltés — de mivel az imént feltöltött verzió already up-to-date a szerveren (updated_at
frissítve), a download fázis nem írja felül azt. Ezzel a sorrend biztosítja, hogy
a helyi módosítások ne veszjenek el.

---

## 4. SSL workaround és thread safety

Az SSL `TrustManager` létrehozása az `AuthManager` és `SyncManager` példányaiban
történik — mindkettőben a `getTrustAllSSLSocketFactory()` helper metódus.

```java
private SSLSocketFactory getTrustAllSSLSocketFactory() throws Exception {
    TrustManager[] trustAllCerts = new TrustManager[] {
        new X509TrustManager() {
            public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            public void checkClientTrusted(X509Certificate[] c, String a) {}
            public void checkServerTrusted(X509Certificate[] c, String a) {}
        }
    };
    SSLContext sc = SSLContext.getInstance("TLS");
    sc.init(null, trustAllCerts, new java.security.SecureRandom());
    return sc.getSocketFactory();
}
```

**Thread safety:** az `SSLContext` és `SSLSocketFactory` példányok minden HTTP
hívás előtt újra létrehozódnak (nem megosztott statikus példány). Ez enyhe teljesítményi
veszteséget jelent, de versenyhelyzet-mentes és egyszerű. Crystal Sky-on a sync
ritkán indul (manuális trigger), ezért ez elfogadható.

**Biztonsági megfontolás:**
A trust-all TrustManager MITM (man-in-the-middle) támadásra sebezhetővé teszi
a kapcsolatot. A Crystal Sky zárt, DJI-vezérelt hálózati környezetben működik
(általában WPA2 WiFi vagy mobilnet), ahol a kockázat alacsony. Jövőbeli javítás:
certificate pinning vagy a Let's Encrypt CA bundle explicit bekötése az app-ba.

---

## 5. SharedPreferences apply() — aszinkron írás

A token tárolása és törlése `SharedPreferences.apply()` hívással történik:

```java
prefs.edit()
    .putString(KEY_TOKEN, token)
    .putString(KEY_USER, user)
    .apply();  // async írás — nem blocking
```

**apply() vs commit():**
- `apply()`: aszinkron, azonnal visszatér, a háttérben írja lemezre a változásokat
- `commit()`: szinkron, blokkolja a hívó szálat az írás befejezéséig

Az M06-ban minden SharedPreferences írás executor thread-en történik, ahol a blokkolás
nem okoz UI freeze-t. Ennek ellenére `apply()` kerül alkalmazásra — az aszinkron írás
gyorsabban tér vissza, és a token az in-memory SharedPreferences-ből azonnal elérhető,
még mielőtt a lemezre kerülne.

**Kiolvasás thread safety:**
`isAuthenticated()` és `getToken()` az UI szálról is hívható — a `SharedPreferences`
`getString()` művelete thread-safe (az Android implementáció szinkronizált olvasást
biztosít).

---

## 6. Hibakezelés — HTTP 40x/50x → log + skip

```
HTTP hívás eredménye:
  │
  ├─ IOException / SocketTimeoutException:
  │    → Log.e("SYNC", "error: " + e.getMessage())
  │    → dirty flag megmarad (következő sync újrapróbálja)
  │    → postError() → tvSyncStatus frissítve
  │
  ├─ HTTP 200 / 201 / 204:
  │    → Sikeres → dirty flag törlés / fájl írás
  │
  ├─ HTTP 401 (Unauthorized):
  │    → Érvénytelen vagy lejárt token
  │    → F-S13 NYILVÁNos nyitott feladat
  │    → Jelenlegi kezelés: Log.e() + skip (dirty flag megmarad)
  │    → Következmény: a sync csendben meghiúsul, felhasználó nem kap értesítést
  │    → Jövőbeli fix: AuthManager.logout() + toast + updateSyncStatus()
  │
  ├─ HTTP 40x (400, 403, 404, 409):
  │    → Log.e("SYNC", method + " HTTP " + code + " for id=" + id)
  │    → skip — dirty flag megmarad
  │    → Nincs automatikus retry
  │
  └─ HTTP 50x:
       → Log.e() + skip — dirty flag megmarad
       → Következő manuális sync újrapróbálja
```

**Fájl írási hiba kezelése:**
Ha a letöltött JSON fájlba írása meghiúsul (pl. teli lemez), az `IOException`
elkapásra kerül, logolódik, és a sync folytatódik a következő fájlra. A helyi
fájlrendszer nem marad inkonzisztens állapotban, mert a letöltött tartalom új fájlba
vagy a meglévő fájl felülírásával kerül lemezre (atomikus a Java File IO szempontjából
— de Crystal Sky Android 5.1-en a tényleges atomicitás nem garantált).

---

## 7. Életciklus és erőforrás kezelés

### NetworkMonitor életciklus

```java
// onResume(): regisztráció
networkMonitor.start(state -> updateSyncStatus());

// onPause(): leiratkozás
networkMonitor.stop();
// → cm.unregisterNetworkCallback(networkCallback)
// → Memória szivárgás elkerülve: nincs referencia az Activity-re

// onDestroy(): szintén stop() — kettős védelem
networkMonitor.stop();
```

**Miért onPause()-ban leáll a monitor?**
Ha az Activity háttérbe kerül (Home gomb), a `NetworkMonitor` callback-jei
nem kívánatos UI frissítéseket okoznának egy nem aktív Activity-n. A Crystal Sky
appot repülés közben nem küldik háttérbe, de a defensive coding megköveteli a cleanup-ot.

### ExecutorService és executor thread

```java
// Az ExecutorService nincs explicit shutdown()-nal leállítva.
// Indoklás: a 3 manager példány az Activity lifecycle-jéhez kötött;
// az Activity destroy-akor a GC felszabadítja őket.
// A single-thread executor daemon thread-et használ — JVM shutdown esetén
// nem akadályozza a folyamat kilépését.
//
// Jövőbeli javítás: onDestroy()-ban executor.shutdownNow()
```

### Sync lock — repülés blokkolás

```java
// MissionPlannerActivity.triggerSync():
void triggerSync() {
    if (missionRunning) {
        Toast.makeText(this, "Repülés folyamatban...", Toast.LENGTH_SHORT).show();
        return;
    }
    // sync indul
}

// SyncManager.syncAll()-ban nincs külön missionRunning ellenőrzés
// → Az ellenőrzés az Activity szintjén történik (UI szál)
// → Ha a repülés syncAll() futása közben indul el (race condition):
//   → a sync már fut az executor thread-en → zavartalanul befejezi magát
//   → a repülés UI szálra hat (DJI callback)
//   → Nincs valódi konfliktus: a sync HTTP hálózati IO-t végez,
//     a DJI SDK saját socket-jein kommunikál
```

---

## 8. Kapcsolat-figyelés részletek — API 22 compat

```java
// NetworkMonitor.start() — API level-alapú elágazás

if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
    // API 21+ (LOLLIPOP) → NetworkCallback elérhető
    networkCallback = new ConnectivityManager.NetworkCallback() {
        @Override
        public void onAvailable(Network network) {
            setState(State.ONLINE);
        }
        @Override
        public void onLost(Network network) {
            // Ellenőrzés: van-e még más aktív hálózat?
            // (pl. WiFi disconnect, de mobilnet aktív marad)
            if (!hasActiveNetwork()) {
                setState(State.OFFLINE);
            }
        }
    };
    // API 24+: registerDefaultNetworkCallback()
    // API 21–23: registerNetworkCallback() NetworkRequest-tel
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        cm.registerDefaultNetworkCallback(networkCallback);
    } else {
        NetworkRequest request = new NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build();
        cm.registerNetworkCallback(request, networkCallback);
    }
}

// hasActiveNetwork() — API 22 kompatibilis
// Lásd L2 (M06_L2_DONTESI_LOGIKA.md) 7. szekció
```

**Crystal Sky Android 5.1 = API 22:**
- `registerNetworkCallback(NetworkRequest, NetworkCallback)` → elérhető (API 21+) ✅
- `registerDefaultNetworkCallback(NetworkCallback)` → API 24+, Crystal Sky-on NINCS ✅ (ágban kezelt)
- `getActiveNetwork()` → API 23+, Crystal Sky-on NINCS → `getActiveNetworkInfo()` fallback ✅

---

## 9. Teljesítmény és memória

| Komponens | Memória | Megjegyzés |
|-----------|---------|-----------|
| `NetworkMonitor` | ~1 KB | Csak állapot + referenciák |
| `AuthManager` | ~2 KB | SharedPreferences referencia + executor |
| `SyncManager` | ~5 KB | missionsDir referencia + executor |
| Egy `.flightprogram.json` | 2–20 KB | JSON struktúra, obstacles nélkül ~3 KB |
| JSON parse memória (sync alatt) | ~100 KB | 10–50 fájl egyszerre olvasva |

A Crystal Sky 4 GB RAM-mal rendelkezik — az M06 memóriaigénye elhanyagolható.

**HTTP kapcsolat idők (orientatív, Crystal Sky 4G/WiFi):**

| Művelet | Várható idő |
|---------|------------|
| POST /api/auth/login | 0.5–2 s |
| GET /api/flight-programs (lista) | 0.3–1 s |
| POST/PUT egy program (~5 KB) | 0.5–2 s |
| GET egy program (~5 KB) | 0.3–1 s |
| Teljes sync (10 fájl) | 5–15 s |

A sync UI-n a "Szinkronizálás folyamatban..." szöveg megjelenik a sync indításakor
és megmarad, amíg az `onComplete()` vagy `onError()` callback meg nem érkezik.
A `btnSync` a sync ideje alatt disabled — kettős kattintás nem lehetséges.
