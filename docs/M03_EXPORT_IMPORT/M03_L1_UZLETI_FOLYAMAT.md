# L1 – Üzleti Folyamat – Export / Import

**Modul:** M03
**Szint:** L1 – Üzleti Folyamat
**Verzió:** v1.3.0
**Létrehozva:** 2026-04-02
**Utolsó módosítás:** 2026-04-05
**Státusz:** ✅ Implementálva (v1.3.0)

---

## 1. Modul célja

> **⚠ Dokumentáció-frissítési megjegyzés (2026-07-03):** ez a fejezet és a
> `M03_L3` "ProjectManager — teljes API" szakasza a **régi, v1 `.dronefly.json`**
> sémát írja le. A `ProjectManager.java` időközben (M06/M07 fejlesztések során)
> egy jóval bővebb **`.flightprogram.json`** sémára állt át (GeoJSON `parcel`,
> `metadata` blokk sync-állapottal, `flight_settings`, `block_grid` stb. —
> ld. `ProjectManager.buildJson()`/`loadNew()`). A teljes doc-frissítés a régi
> sémáról az újra külön feladat; ez a fejezet a **7. pontban** csak a most
> (2026-07-03) azonosított hiányosságot dokumentálja, a **valós, jelenlegi**
> séma alapján.

Az M03 modul kétféle adatkezelési igényt lát el:

### 1a. Projekt mentés / betöltés (offline, belső)

Az app saját `.dronefly.json` formátumban teljes projektet ment:
- Poligon csúcsai, start pont
- Összes beállítás (GSD, sidelap, frontlap, sebesség, szög, offset, domborzatkövetés, drón profil)
- Akadályok listája (lat, lon, sugár, magasság)

A waypontok **nem** kerülnek mentésre — betöltés után az app automatikusan újragenerálja.

### 1b. Külső formátum export / import (CSV, KMZ)

A DroneFly app és külső szoftverek (Litchi, DJI Pilot) közötti adatcsere:

**Export:**
- **Litchi CSV** — 48 oszlopos Litchi Mission Hub kompatibilis formátum,
  importálható a flylitchi.com/hub weboldalra, majd a Litchi app-on
  keresztül P4P v1-el (és más régebbi DJI drónokkal) futtatható
- **KMZ** — DJI WPML alapú KML+ZIP formátum, DJI Pilot appban megnyitható

**Import:**
- **Litchi CSV** — a webes repüléstervező appból exportált missziók
  beolvasása, megjelenítése és feltöltése a drónra

> **Fontos különbség:** A CSV/KMZ export csak waypontokat tartalmaz (nem a poligont
> és a beállításokat), ezért szerkesztésre nem alkalmas. A `projekt mentés` (JSON)
> tartalmazza az összes adatot és teljes mértékben újraszerkeszthető.

---

## 2. Litchi CSV export folyamat

```
[Exportálás] gomb → CSV választva
      │
      ▼
currentWaypoints lista feltöltve?
  │ NEM → Toast: "Nincs generált misszió"
  │ IGEN
  ▼
MissionExporter.toLitchiCsv(waypoints, speedMs) → String (CSV tartalom)
  - 48 oszlopos fejléc
  - Minden waypointhoz 1 sor:
      lat, lon, alt, heading=-1, curvesize=0, rotationdir=0,
      gimbalmode=2, gimbalpitchangle=-90,
      actiontype1=1, actionparam1=0,  (take photo)
      actiontype2=-1 ... actiontype15=-1 (többi: inaktív)
      altitudemode=0 (AGL),
      speed=int(speedMs),
      poi_*=0, photo_timeinterval=-1, photo_distinterval=-1
      │
      ▼
Fájl írása:
  Név: dronefly_mission_YYYYMMDD_HHmmss.csv
  Helye: getExternalFilesDir(null)
  Kódolás: UTF-8
      │
      ▼
FileProvider URI létrehozása
  → Intent.ACTION_SEND
  → type: "text/csv"
  → Megosztás: fájlkezelő, email, AirDroid stb.
```

---

## 3. KMZ export folyamat

```
[Exportálás] gomb → KMZ választva
      │
      ▼
MissionExporter.toKmz(waypoints, missionName) → byte[]
  KMZ struktúra:
    archive.kmz (ZIP)
    └── doc.kml (KML 2.2)

  KML tartalom:
    - Document: misszió neve, leírás
    - Placemark (LineString): teljes útvonal — térkép előnézet
    - Placemark (Point): minden waypoint egyenként
        → name: "WP001", "WP002", ...
        → ExtendedData: altitude, speed, gimbalPitch=-90, shootPhoto=true
      │
      ▼
Fájl írása:
  Név: dronefly_mission_YYYYMMDD_HHmmss.kmz
  Helye: getExternalFilesDir(null)
      │
      ▼
FileProvider URI → Intent.ACTION_SEND → type: "application/zip"
```

---

## 4. CSV import folyamat

```
"CSV importálása" gomb megnyomva
      │
      ▼
Intent.ACTION_GET_CONTENT → fájlválasztó dialóg
  type: "text/*"
      │
      ▼  (fájl kiválasztva, onActivityResult)
CsvMissionParser.parse(context, uri) → List<WaypointData>
  - Fejlécsor felismerése ("latitude" mező)
  - Soronként: lat, lon, altitude(m), heading, gimbalPitchAngle, actiontype1
  - actiontype1 == 1 → shootPhoto = true
      │
      ▼
Parsed waypoints üres?
  │ IGEN → Toast: "Nem sikerült waypointokat beolvasni"
  │ NEM
  ▼
currentWaypoints = parsedWaypoints
Térkép: waypontok megjelenítése kék markerekkel
Toast: "X waypoint importálva"
```

---

## 5. Projekt mentés / betöltés folyamat

### Mentés

```
"Terv mentése" gomb megnyomva
      │
      ▼
AlertDialog: névbeviteli EditText
  - Alapértelmezett: előző betöltött terv neve, vagy aktuális dátum
  - setSelectAllOnFocus(true): egyszeri érintéssel felülírható
      │
      ▼
polygonPoints üres?
  │ IGEN → Toast: "Nincs rajzolt terület — nincs mit menteni!"
  │ NEM
  ▼
ProjectManager.saveProject(ctx, name, polygon, startPoint, config)
  - JSON összeállítása (version, name, savedAt, polygon, startPoint, config, obstacles)
  - Fájlnév: name.replace(' ','_').replaceAll("[/\\:*?\"<>|]", "_") + ".dronefly.json"
  - Írás: <external files>/missions/<fájlnév>
      │
      ▼
Toast: "Mentve: <fájlnév>"
lastLoadedProjectName = name
```

### Betöltés

```
"Terv betöltése" gomb megnyomva
      │
      ▼
ProjectManager.listProjects(ctx) → List<File> (legújabb elöl, bubble sort)
  Üres lista? → Toast: "Nincs elmentett repülési terv."
      │
      ▼
AlertDialog fájllista:
  - Megjelenítés: "terv neve\nyyyy-MM-dd HH:mm" soronként
  - Kiválaszt egyet
      │
      ▼ (ha polygonPoints nem üres)
Megerősítő dialog: "Az aktuális rajzolt terület elvész. Biztosan betöltöd?"
      │
      ▼
clearAll() → minden overlay, marker, lista törlése
      │
      ▼
ProjectManager.loadProject(file) → ProjectData
  - polygon → addPolygonPoint() minden csúcsra
  - startPoint → setStartPoint() ha nem null
  - config mezők → restoreConfigToUI() (seekbar fordított mapping)
  - obstacles → addObstacle() minden akadályra
      │
      ▼
polygonPoints.size() >= 3 → generateMission() automatikus generálás
mapView.animateTo(polygon[0]) → térkép a betöltött területre ugrik
Toast: "Betöltve: <terv neve>"
lastLoadedProjectName = data.name
```

### SeekBar fordított mapping (restoreConfigToUI)

| SeekBar | Leolvasás (getX) | Visszaállítás (setProgress) |
|---------|-----------------|----------------------------|
| sbGsd | `0.5 + progress * 0.1` | `round((gsd - 0.5) / 0.1)` |
| sbSidelap | `50 + progress` | `round(sidelap - 50)` |
| sbFrontlap | `60 + progress` | `round(frontlap - 60)` |
| sbSpeed | `3 + progress` | `round(speed - 3)` |
| sbAngle | `progress` | `round(angle)` |
| sbOffset | `progress` | `round(offsetM)` |

---

## 6. Export fájlok elérési útja

```
Eszközön:
  /sdcard/Android/data/com.dronefly.app/files/
  → App-specifikus external storage
  → Nem igényel READ/WRITE_EXTERNAL_STORAGE jogosultságot (Android 4.4+)
  → FileProvider-en keresztül megosztható más appokkal

Crystal Sky-on:
  Az AirDroid, Bluetooth, USB-n keresztüli fájlátvitel elérheti
  a FileProvider által megosztott URI-t
```

---

## 6. Kapcsolódó modulok

| Modul | Kapcsolat típusa |
|-------|-----------------|
| M01 Misszió Tervező | **Közvetlen hívó** — exportCsv(), exportKmz(), importCsv() |
| M02 Grid Engine | **Adat forrás** — WaypointData lista input |
| M04 DJI Integráció | **Alternatív út** — exportált fájl helyett közvetlen feltöltés |

---

## 7. Mintavételi beállítások és kamera-formátum mentése — hiányosság — ✅ Javítás implementálva (2026-07-03), eszközön még nem tesztelve

**Terepi megfigyelés (2026-07-03):** a felhasználó jelezte, hogy egy
mintavételi misszió elmentése után a mintavételi beállítások (mintapontok
száma, elosztási algoritmus, hover-idő, repülési magasságok) és a kamera
fájlformátum (JPEG/RAW) **nem** állnak vissza betöltéskor.

**Megerősítve:** a `ProjectManager.buildJson()`/`loadNew()` jelenleg
kizárólag a hagyományos grid-beállításokat (GSD, overlap, sebesség stb.)
menti — a `MissionConfig` mintavételi mezői és a teljes `CameraSettings`
objektum kimarad. Részletes hibaleírás, tervezett JSON-séma és a
visszafelé-kompatibilitási garancia: **`M03_L3_ALLAPOTGEP_ES_ENGINE.md`**
→ "Séma-hiányosság — mintavételi beállítások és kamera-formátum nem
mentődik" szakasz.

**Röviden:** a bővítés **additív** (új `sampling` és `camera_settings` JSON
blokk), a meglévő `opt*()`-alapú betöltési minta miatt a régi tervek
módosítás/migráció nélkül továbbra is betölthetők maradnak, és a Hetzner
szinkron (M06) automatikusan magával viszi az új mezőket is.
