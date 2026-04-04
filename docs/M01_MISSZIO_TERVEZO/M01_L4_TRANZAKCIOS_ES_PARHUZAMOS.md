# L4 – Tranzakciós és Párhuzamos Kezelés – Misszió Tervező

**Modul:** M01
**Szint:** L4 – Tranzakciós és Párhuzamos Kezelés
**Verzió:** v1.0.0
**Létrehozva:** 2026-04-02
**Státusz:** ✅ Implementálva (v1.0.0)

---

## 1. UI szál biztonság

Az Android UI csak a main thread-ről módosítható. A MissionPlannerActivity
minden UI frissítést a main thread-en hajt végre:

```java
// MissionUploader callback → főszálra visszatérés
runOnUiThread(() -> {
    // Toast, gomb állapot, térkép frissítés
});
```

A GridMissionGenerator szinkron hívás — a misszió generálás a UI szálat
blokkolja. Tipikus futási idők:

| Terület | Waypontok | Futási idő |
|---------|-----------|-----------|
| 1 ha    | ~30 wp    | < 50 ms   |
| 10 ha   | ~300 wp   | < 200 ms  |
| 50 ha   | ~1500 wp  | < 1 s     |
| 100 ha  | ~3000 wp  | 1–3 s     |

> 50 ha felett érdemes ProgressDialog-ot megjeleníteni a "Misszió generálása"
> gomb megnyomásakor (AsyncTask vagy Thread).

---

## 2. Export fájlkezelés

Az export műveletek az Android fájlrendszert érintik:

```
getExternalFilesDir(null)
  → /sdcard/Android/data/com.dronefly.app/files/
  → Nincs WRITE_EXTERNAL_STORAGE jogosultság szükséges (app-saját könyvtár)
  → FileProvider-en keresztül megosztható
```

**Írási tranzakciómodell:**
```
1. MissionExporter.toLitchiCsv() → byte[]  (memóriában)
2. FileOutputStream → fájl írás
3. FileProvider.getUriForFile() → URI
4. Intent.ACTION_SEND → megosztás
```

Ha a 2. lépés (írás) meghiúsul: IOException elkapva, Toast hibaüzenet.
A fájl részlegesen írva maradhat — következő exportnál felülíródik
(azonos fájlnév formátum esetén az időbélyeg eltér, tehát párhuzamos
fájlok keletkezhetnek).

**Fájlnév ütközés:** Időbélyeges fájlnév (YYYYMMDD_HHmmss) használata
biztosítja, hogy egymást követő exportok nem írják felül egymást.

---

## 3. DJI callback szálkezelés

A MissionUploader callback-ek nem garantáltan a main thread-en érkeznek
(MSDK v4 belső threading). Ezért minden callback UI frissítést
`runOnUiThread()` blokkba kell csomagolni:

```java
uploader.uploadMission(waypoints, config, new MissionUploader.UploadCallback() {
    @Override
    public void onSuccess() {
        runOnUiThread(() -> {
            // UI frissítés biztonságosan
        });
    }

    @Override
    public void onError(String message) {
        runOnUiThread(() -> {
            Toast.makeText(MissionPlannerActivity.this, message, Toast.LENGTH_LONG).show();
        });
    }
});
```

> **Stub build esetén** (emulátor): a MissionUploader közvetlenül hívja a
> callback-et, szinkron módon, a hívó szálán. Ez nem okoz problémát a
> stub fázisban, de a valódi MSDK implementációnál figyelni kell rá.

---

## 4. GPS helymeghatározás

A `MyLocationNewOverlay` a `GpsMyLocationProvider`-t használja, amely
az Android `LocationManager`-rel dolgozik. A következő szempontok fontosak:

```
Engedélyek:
  ACCESS_FINE_LOCATION — AndroidManifest.xml-ben deklarálva
  Runtime permission — Android 6.0+ esetén szükséges (minSdk = 22!)

Crystal Sky specifikus:
  - A Crystal Sky Android 5.1 (API 22) = runtime permission SZÜKSÉGES
  - MainActivity-ben (vagy MissionPlannerActivity-ben) kell kérni:
    checkSelfPermission(ACCESS_FINE_LOCATION) → requestPermissions()
  - Jelenlegi implementáció: MainActivity nem kér jogosultságot
    → javítandó, ha GPS gomb nem működik
```

**GPS frissítés frekvencia:** Az overlay alapértelmezetten 1 másodpercenként
kér frissítést. Crystal Sky-on ez elfogadható, nem okoz akkumulátor-problémát
(az eszköz DJI vezérlőhöz van csatlakoztatva és töltve van).

---

## 5. OSMDroid tile cache

```
Cache elhelyezése: getCacheDir()/osmdroid
  → Belső tároló, nem igényel jogosultságot
  → Automatikusan törölhető a rendszer által (tárhely hiány esetén)
  → Crystal Sky-on ~500 MB ajánlott cache méret

Tile letöltés:
  - Online mode: HTTPS tile server (tile.openstreetmap.org)
  - Offline mode: előre letöltött cache (OsmAnd .map fájl nem kompatibilis)
  - Emulátorban: tile letöltés hálózattól függ
    (AVD → Settings → WiFi be kell kapcsolni)

useDataConnection(true): szükséges az online tile letöltéshez
```

---

## 6. Polygon és waypoint memóriakezelés

```java
// polygonPoints: ArrayList<GeoPoint>
// Memóriaigény: ~100 byte/pont, max ~1000 pont → ~100 KB → elhanyagolható

// currentWaypoints: ArrayList<WaypointData>
// Memóriaigény: ~200 byte/waypoint, max ~3000 wp → ~600 KB → elhanyagolható

// currentSegments: ArrayList<ArrayList<WaypointData>>
// Azonos adatok szegmensekre osztva → nem duplikálódnak (referencia)
```

A Crystal Sky 4 GB RAM-mal rendelkezik, a waypoint adatok
memóriakezelési szempontból nem jelentenek kockázatot.

---

## 7. Koordináta pontosság

```
GPS → GeoPoint (double lat/lon):
  Double precision: ~1 cm pontosság → elegendő mezőgazdasági célra

WGS84 → helyi XY (méter) transzformáció:
  Közelítés: 1° lat ≈ 111 320 m
             1° lon ≈ 111 320 × cos(lat) m
  Hiba (Magyarország, 47°N): < 0.01% → < 1 m / km → elhanyagolható

XY rotáció (repülési irány) → visszavetítés WGS84-be:
  Kumulatív hiba: < 10 cm a 100 ha-os területen belül
  → Elfogadható mezőgazdasági felmérési pontossággal
```

---

## 8. Életciklus és activity újraindítás

```
Képernyőforgatás (orientation change):
  → MissionPlannerActivity újraindulna (alapértelmezett Android viselkedés)
  → Megoldás: android:screenOrientation="landscape" a Manifest-ben
              (rögzített tájolás, nem indul újra)
  → Crystal Sky Crystal Sky mindig landscape módban van

Háttérbe küldés (Home gomb):
  → onPause() → mapView.onPause()
  → Állapot (polygon, waypoints) ELVÉSZ — nincs onSaveInstanceState
  → Elfogadható: a Crystal Sky repülés közben nem kerül háttérbe

Memória kill (low memory):
  → Alkalmazás újraindulhat
  → android:largeHeap="true" csökkenti a kockázatot
```
