# L2 – Döntési Logika – Misszió Tervező

**Modul:** M01
**Szint:** L2 – Döntési Logika
**Verzió:** v1.2.0
**Létrehozva:** 2026-04-02
**Utolsó módosítás:** 2026-04-05
**Státusz:** ✅ Implementálva (v1.2.0)

---

## 1. SeekBar értékek leképzése

### GSD SeekBar

```
SeekBar max = 95, progress → gsdCm érték:
  gsdCm = 0.5 + progress * 0.1

  progress  0 → 0.5 cm/px
  progress 25 → 3.0 cm/px  (default)
  progress 95 → 10.0 cm/px

Label formátum: "GSD: 3.0 cm/px  →  magasság: 118 m  (ajanl. v: 12.0 m/s)"
  → magasság: GsdCalculator.altitudeFromGsd(gsdCm, droneProfile) kerekítve egészre
  → ajánlott sebesség: GsdCalculator.recommendedSpeedMs(gsdCm, droneProfile)
```

### Sidelap SeekBar

```
SeekBar max = 40, progress → sidelap érték:
  sidelap = 50 + progress  (50–90%)
  default progress = 25 → 75%

Label formátum: "Oldalsó átfedés: 75%"
```

### Frontlap SeekBar

```
SeekBar max = 30, progress → frontlap érték:
  frontlap = 60 + progress  (60–90%)
  default progress = 20 → 80%

Label formátum: "Menetirány átfedés: 80%"
```

### Sebesség SeekBar

```
SeekBar max = 12, progress → sebesség:
  speedMs = 3 + progress  (3–15 m/s)
  default progress = 4 → 7 m/s

  GSD vagy drón profil változásakor auto-frissítés:
    javasolt = GsdCalculator.recommendedSpeedMs(gsdCm, droneProfile)
    progress = round(javasolt) - 3  (0–12 értékre clampelve)

Label formátum: "Sebesség: 7 m/s"
```

### Repülési irány SeekBar

```
SeekBar max = 179, progress → szög:
  angleDeg = progress  (0–179°)
  default = 0° (K–Ny sávok, É–D irányban haladás)

Label formátum: "Repülési irány: 0°"
```

### Offset SeekBar

```
SeekBar max = 30, progress → offset méter:
  offsetM = progress  (0–30 m)
  default progress = 0 → kikapcsolva

Label formátum (progress = 0):
  "Túlrepülés (offset): kikapcsolva"

Label formátum (progress > 0):
  "Túlrepülés: 10 m  (ajánl: 22 m)"
  → ajánlott: (stripSpacingM + photoDistM) / 2.0
  → ez az a távolság, ami biztosítja hogy a terület szélei is lefedésre kerüljenek
```

---

## 2. Misszió generálás döntési logika

### Auto-generálás (autoGenerateIfReady)

```
Minden pont lerakás / húzás / törlés után automatikusan hívódik
  │
  ▼
polygonPoints.size() < 3?
  │ IGEN → régi útvonal törlése, "N pont – még X kell" megjelenítve
  │        → leáll
  │ NEM
  ▼
buildConfig() → MissionConfig (terrainFollowing = false)
  │
  ▼
GridMissionGenerator.generate(polygonPoints, config)
  → offset alkalmazva (ha offsetM > 0)
  → akadályok szűrve (ha obstacles nem üres)
  │
  ▼
result.errorMessage != null?
  │ IGEN → tvStats.setText("Hiba: " + message) → leáll
  │ NEM
  ▼
Térkép: narancs polyline, statisztika szöveg frissítés
result.altitudeM > 120m? → Toast figyelmeztetés
```

### Manuális újragenerálás ("Újragenerálás" gomb)

```
"Újragenerálás (beallitasokkal)" gomb megnyomva
  │
  ▼
polygonPoints.size() < 3?
  │ IGEN → Toast: "Legalabb 3 pont szukseges!" → leáll
  │ NEM
  ▼
buildConfig() → MissionConfig (terrainFollowing = switchTerrain.isChecked())
  │
  ▼
GridMissionGenerator.generate() → eredmény
  │
  ▼
result.altitudeM > 120m? → Toast EU határ figyelmeztetés
config.terrainFollowing = true?
  │ IGEN → applyTerrainFollowing(result, altitudeM)
  │         Open-Elevation API → DEM korrekció
  │ NEM → kész
```

### Akadály dialog döntés

```
Akadály mód aktív + térkép kattintás
  │
  ▼
showAddObstacleDialog(GeoPoint p):
  Dialog: sugár (m) + magasság (m) bevitel
  │
  ▼
radius <= 0 || height <= 0?
  │ IGEN → Toast: "Értékeknek pozitívnak kell lenniük!" → leáll
  │ NEM
  ▼
addObstacle(p, radius, height)
  → ObstacleData létrehozás
  → piros kör overlay rajzolás (36 szegmens)
  → Marker elhelyezés (kattintásra: info + törlés)
  → autoGenerateIfReady()
```

---

## 3. GPS pozíció lekérés döntés

```
📍 gomb megnyomva
  │
  ▼
locationOverlay engedélyezve?
  │ NEM → locationOverlay.enableMyLocation() hívva először
  │ IGEN
  ▼
locationOverlay.getLastFix() != null?
  │ NEM → Toast: "GPS pozíció nem elérhető"
  │       (esetleg helyszolgáltatás ki van kapcsolva)
  │ IGEN
  ▼
mapController.animateTo(lastFix lat/lon)
mapController.setZoom(16)
```

---

## 4. Feltöltés + Start döntés

```
"Feltöltés + Start" gomb megnyomva
  │
  ▼
currentSegments lista üres?
  │ IGEN → Toast: "Először generáld a missziót!"
  │        → leáll
  │ NEM
  ▼
uploader.uploadMission(currentSegments[0], config, callback)
  │
  ├─ onError(message) →
  │    Toast("Feltöltési hiba: " + message)
  │    → leáll
  │
  └─ onSuccess() →
       uploader.startMission(callback)
         ├─ onError(message) → Toast("Start hiba: " + message)
         └─ onSuccess() → setMissionRunning(true)
                          currentSegmentIndex = 0
                          Toast: "Misszió elindítva"
```

---

## 5. Szünet / Folytatás döntés

```
"⏸ Szünet" gomb megnyomva (isPaused = false)
  │
  ▼
uploader.pauseMission(callback)
  ├─ onError(message) → Toast("Szünet hiba: " + message)
  └─ onSuccess() →
       isPaused = true
       btnPauseMission.setText("▶ Folytatás")
       Toast: "Misszió szüneteltetve — drón lebeg"

"▶ Folytatás" gomb megnyomva (isPaused = true)
  │
  ▼
uploader.resumeMission(callback)
  ├─ onError(message) → Toast("Folytatás hiba: " + message)
  └─ onSuccess() →
       isPaused = false
       btnPauseMission.setText("⏸ Szünet")
       Toast: "Misszió folytatva"
```

---

## 6. Stop döntés

```
"⏹ Stop" gomb megnyomva
  │
  ▼
AlertDialog megjelenítése:
  Cím: "Misszió leállítása"
  Szöveg: "Biztosan leállítod a missziót? A drón lebeg és kézi
           vezérlésre vált. RTH-hoz használd a kontrollert."
  [Mégse] | [Leállítás]
  │
  ▼  [Leállítás] megnyomva
uploader.stopMission(callback)
  ├─ onError(message) → Toast("Stop hiba: " + message)
  └─ onSuccess() →
       setMissionRunning(false)
       Toast: "Misszió leállítva"
```

---

## 7. Export döntés

```
"Exportálás" gomb megnyomva
  │
  ▼
currentWaypoints üres?
  │ IGEN → Toast: "Nincs generált misszió az exporthoz!"
  │        → leáll
  │ NEM
  ▼
AlertDialog: "Export formátum"
  [CSV (Litchi)] | [KMZ (DJI Pilot)] | [Mégse]
  │
  ├─ CSV → exportCsv()
  │         MissionExporter.toLitchiCsv()
  │         Fájl: dronefly_mission_YYYYMMDD_HHmmss.csv
  │         Elhelyezés: getExternalFilesDir(null)
  │         Megosztás: FileProvider intent
  │
  └─ KMZ → exportKmz()
            MissionExporter.toKmz()
            Fájl: dronefly_mission_YYYYMMDD_HHmmss.kmz
            Elhelyezés: getExternalFilesDir(null)
            Megosztás: FileProvider intent
```

---

## 8. CSV import döntés

```
"CSV importálása" gomb megnyomva
  │
  ▼
Intent.ACTION_GET_CONTENT → fájlválasztó (*.csv)
  │
  ▼  (fájl kiválasztva)
onActivityResult → uri != null?
  │ NEM → leáll (felhasználó visszalépett)
  │ IGEN
  ▼
CsvMissionParser.parse(uri)
  │
  ├─ Üres lista → Toast: "A CSV fájl nem tartalmaz érvényes waypointokat"
  │
  └─ Nem üres lista →
       currentWaypoints = parsedWaypoints
       Térkép: waypontok megjelenítése kék markerekkel
       polygon = null (CSV import esetén polygon nem rajzolható)
       Toast: "X waypoint importálva"
```

---

## 9. Home pont döntés

```
Hosszú érintés a térképen (bármikor)
  │
  ▼
Draw mód aktív?
  │ IGEN → Home pont beállítása ÉS polygon csúcspont hozzáadás elmarad
  │         (hosszú érintés nem ad hozzá csúcspontot)
  │ NEM → Home pont beállítása
  ▼
startPoint = touched GeoPoint
Régi Home marker eltávolítása a térképről
Új zöld marker elhelyezése
Toast: "Start/Home pont beállítva"

Ha misszió már generálva volt:
  → drawMissionPath() újrahívva (Start pont bekerül az útvonalba)
```
