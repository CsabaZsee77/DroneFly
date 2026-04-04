# L2 – Döntési Logika – Misszió Tervező

**Modul:** M01
**Szint:** L2 – Döntési Logika
**Verzió:** v1.0.0
**Létrehozva:** 2026-04-02
**Státusz:** ✅ Implementálva (v1.0.0)

---

## 1. SeekBar értékek leképzése

### GSD SeekBar

```
SeekBar max = 90, progress → gsdCm érték:
  gsdCm = 0.5 + progress * 0.1

  progress  0 → 0.5 cm/px
  progress 25 → 3.0 cm/px  (default)
  progress 90 → 9.5 cm/px

Label formátum: "GSD: 3.0 cm/px  →  magasság: 118 m"
  → magasság: GsdCalculator.altitudeFromGsd(gsdCm) kerekítve egészre
```

### Sidelap SeekBar

```
SeekBar max = 40, progress → sidelap érték:
  sidelap = 50 + progress  (50–90%)
  default progress = 25 → 75%

Label formátum: "Oldallefedés: 75%"
```

### Frontlap SeekBar

```
SeekBar max = 30, progress → frontlap érték:
  frontlap = 50 + progress  (50–80%)
  default progress = 30 → 80%

Label formátum: "Előrelefedés: 80%"
```

### Sebesség SeekBar

```
SeekBar max = 12, progress → sebesség:
  speedMs = 3 + progress  (3–15 m/s)

  GSD változásakor auto-frissítés:
    javasolt = GsdCalculator.recommendedSpeedMs(gsdCm)
    progress = (int)(javasolt - 3)  (0–12 értékre mappelve)

Label formátum: "Sebesség: 7 m/s  (javasolt GSD alapján)"
```

### Repülési irány SeekBar

```
SeekBar max = 179, progress → szög:
  angleDeg = progress  (0–179°)
  default = 0° (É–D irányú sávok)

Label formátum: "Repülési irány: 0°  (É–D)"
  Tájolás szöveg:
    0°   → "É–D"
    90°  → "K–Ny"
    45°  → "ÉK–DNy"
    135° → "ÉNy–DK"
    Egyéb → szám+°
```

---

## 2. Misszió generálás validáció

```
"Misszió generálása" gomb megnyomva
  │
  ▼
Polygon csúcspontok száma < 3?
  │ IGEN → Toast: "Rajzolj legalább 3 pontot a területhez!"
  │        → leáll
  │ NEM
  ▼
buildConfig() → MissionConfig létrehozása a SeekBar-ok alapján
  │
  ▼
GridMissionGenerator.generate(polygonPoints, config)
  │
  ▼
result.errorMessage != null?
  │ IGEN → Toast(result.errorMessage) → leáll
  │ NEM
  ▼
result.segments üres?
  │ IGEN → Toast: "Nem sikerült útvonalat generálni" → leáll
  │ NEM
  ▼
Térkép frissítés + statisztikák megjelenítése
  Összes waypoint = result.totalWaypoints
  Szegmensek = result.segments.size()
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
