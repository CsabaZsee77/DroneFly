# L1 – Üzleti Folyamat – Export / Import

**Modul:** M03
**Szint:** L1 – Üzleti Folyamat
**Verzió:** v1.0.0
**Létrehozva:** 2026-04-02
**Státusz:** ✅ Implementálva (v1.0.0)

---

## 1. Modul célja

Az M03 modul a DroneFly app és a külső szoftverek (Litchi, DJI Pilot) közötti
adatcserét valósítja meg:

**Export:**
- **Litchi CSV** — 48 oszlopos Litchi Mission Hub kompatibilis formátum,
  importálható a flylitchi.com/hub weboldalra, majd a Litchi app-on
  keresztül P4P v1-el (és más régebbi DJI drónokkal) futtatható
- **KMZ** — DJI WPML alapú KML+ZIP formátum, DJI Pilot appban megnyitható

**Import:**
- **Litchi CSV** — a webes repüléstervező appból exportált missziók
  beolvasása, megjelenítése és feltöltése a drónra

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

## 5. Export fájlok elérési útja

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
