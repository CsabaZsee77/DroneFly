# L2 – Döntési Logika – Export / Import

**Modul:** M03
**Szint:** L2 – Döntési Logika
**Verzió:** v1.0.0
**Létrehozva:** 2026-04-02
**Státusz:** ✅ Implementálva (v1.0.0)

---

## 1. Litchi CSV oszlopok döntési logikája

A 48 oszlopos Litchi CSV minden mezőjéhez explicit értéket kell rendelni.

```
Kötelező mezők (minden waypointnál):
  latitude          = wp.lat
  longitude         = wp.lon
  altitude(m)       = wp.altitudeM
  heading(deg)      = wp.heading  (-1 = útvonal alapján automatikus)
  curvesize(m)      = 0.0         (éles fordulók)
  rotationdir       = 0           (alapértelmezett)
  gimbalmode        = 2           (interpolate — explicit szög)
  gimbalpitchangle  = -90         (nadir)

  actiontype1  = 1    (take photo)
  actionparam1 = 0
  actiontype2  = -1   (nincs más akció)
  actionparam2 = 0
  ... (actiontype3–15 = -1, actionparam3–15 = 0)

  altitudemode      = 0    (AGL)
  speed(m/s)        = int(speedMs)   [Litchi egész értéket vár]
  poi_latitude      = 0.0
  poi_longitude     = 0.0
  poi_altitude(m)   = 0.0
  poi_altitudemode  = 0
  photo_timeinterval = -1  (nem időközönkénti)
  photo_distinterval = -1  (nem távolságonkénti — waypoint triggel)
```

---

## 2. Sebesség leképzés döntés

```
Litchi CSV speed(m/s) mező egész szám kell:
  litchiSpeed = (int) Math.round(speedMs)

  Minimum: 1 (Litchi nem fogad el 0-t — az "use cruise speed"-et jelent)
  Maximum: 15 (Litchi max)

  Ha speedMs < 1: litchiSpeed = 1
  Ha speedMs > 15: litchiSpeed = 15
```

---

## 3. KMZ formátum döntés

```
DJI Pilot KMZ elvárásai (visszafejtett formátum alapján):

Document/name: misszió neve
Placemark (misszió overview):
  LineString: összes waypoint koordináta egyetlen vonallal
  → Térkép előnézet a DJI Pilot-ban

Minden waypointhoz külön Placemark:
  name: "WP001", "WP002", ... (nullával kitöltött, 3 jegyű index)
  Point: coordinates = lon,lat,altM
  ExtendedData:
    altitude:      wp.altitudeM
    speed:         config.speedMs
    gimbalPitch:   -90
    shootPhoto:    1
    heading:       wp.heading (-1 = auto)
```

---

## 4. CSV import validáció

```
Uri megnyitva → InputStreamReader
  │
  ▼
Első sor fejlécsor?
  │ NEM → hibás CSV, visszatér üres listával
  │       (fejléc detekció: "latitude" szó szerepel az első sorban)
  │ IGEN
  ▼
Oszlopindexek felkeresése fejlécből:
  latIdx  = indexOf("latitude")
  lonIdx  = indexOf("longitude")
  altIdx  = indexOf("altitude(m)")
  hdgIdx  = indexOf("heading(deg)")
  gitIdx  = indexOf("gimbalpitchangle")
  act1Idx = indexOf("actiontype1")

  Ha bármelyik hiányzik: Toast figyelmeztetés, de folytatás a meglévőkkel

Soronként:
  Tokenizálás vesszőre (,,  idézőjelek nélkül — Litchi formátum)
  lat = parseDouble(tokens[latIdx])
  lon = parseDouble(tokens[lonIdx])
  alt = parseDouble(tokens[altIdx])

  Érvényes koordináta?
    lat: [-90, 90]
    lon: [-180, 180]
    │ Érvénytelen → sor kihagyva
    │ Érvényes → WaypointData létrehozva

  shootPhoto = (actiontype1 == 1)
```

---

## 5. FileProvider URI döntés

```
Android 7.0+ (API 24+):
  File URI tiltott direkt megosztásnál
  → FileProvider.getUriForFile() szükséges

  Provider authority: "com.dronefly.app.provider"
  file_paths.xml: <external-files-path name="exports" path="." />

Crystal Sky (Android 5.1, API 22):
  File URI közvetlenül is használható
  → De FileProvider-rel való megközelítés visszafelé kompatibilis
  → Mindkét API szinten működik
  → Egységes megközelítés: mindig FileProvider-t használunk

Intent flag:
  intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
  → szükséges a fogadó app számára
```
