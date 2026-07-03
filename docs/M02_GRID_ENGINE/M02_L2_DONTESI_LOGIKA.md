# L2 – Döntési Logika – Grid Engine

**Modul:** M02
**Szint:** L2 – Döntési Logika
**Verzió:** v1.2.0
**Létrehozva:** 2026-04-02
**Utolsó módosítás:** 2026-04-05
**Státusz:** ✅ Implementálva (v1.2.0)

---

## 1. Magasság validáció

```
altitudeM = GsdCalculator.altitudeFromGsd(gsdCm)

altitudeM < 10 m?
  │ IGEN → errorMessage = "A GSD érték túl kicsi: a repülési magasság 10 m alatt lenne.
  │         Minimum ajánlott GSD: X cm/px"
  │        → GeneratorResult.errorMessage != null → M01 Toast hibaüzenet
  │ NEM → folytatás

altitudeM > 300 m?
  │ IGEN → errorMessage = "A GSD érték túl nagy: a repülési magasság 300 m felett lenne."
  │ NEM → folytatás

Megjegyzés: Magyar légtér szabályok szerint 120 m az engedély nélküli repülési határ.
Az app ezt NEM kényszeríti ki automatikusan — az operátor felelőssége.
(Mezőgazdasági survey általában 40–150 m magasságon történik.)
```

---

## 2. Polygon validáció

```
polygonPoints.size() < 3?
  │ IGEN → errorMessage = "Legalább 3 polygon pont szükséges"
  │ NEM → folytatás

Terület < 100 m²?  (kb. 10×10 m)
  │ IGEN → errorMessage = "A terület túl kicsi a misszió generáláshoz"
  │ NEM → folytatás

Scanline intersections lista üres?
  (előfordulhat konkáv polygon esetén egyes sávoknál)
  │ IGEN az adott scanline-ra → scanline kihagyva (nem hibaállapot)
  │
  ▼
Összes waypoint = 0?
  │ IGEN → errorMessage = "Nem sikerült waypointokat generálni a területre"
  │ NEM → folytatás
```

---

## 3. Sávköz és fotóköz számítás

```java
// GsdCalculator.java

// Footprint (talajon látható képméret)
footprintWidth  = altitudeM * SENSOR_WIDTH_MM / FOCAL_LENGTH_MM   // méter
footprintHeight = altitudeM * SENSOR_HEIGHT_MM / FOCAL_LENGTH_MM  // méter

// Sávköz (side overlap figyelembevételével)
stripSpacingM = footprintWidth * (1.0 - sidelapPercent / 100.0)

// Fotóköz (front overlap figyelembevételével)
photoDistanceM = footprintHeight * (1.0 - frontlapPercent / 100.0)
```

**Példa (GSD = 3.0 cm/px, sidelap 75%, frontlap 80%):**
```
altitudeM      = 117.3 m
footprintWidth  = 117.3 × 13.2 / 8.8 = 175.9 m
footprintHeight = 117.3 × 8.8 / 8.8  = 117.3 m
stripSpacingM  = 175.9 × (1 - 0.75)  = 44.0 m
photoDistanceM = 117.3 × (1 - 0.80)  = 23.5 m
```

---

## 4. Koordináta transzformáció

```java
// GPS → helyi XY (méter, centroid körül)
centroidLat = average(polygonPoints.lat)
centroidLon = average(polygonPoints.lon)

// Közelítés (sík felület, kis terület esetén pontos)
metersPerDegreeLat = 111_320.0
metersPerDegreeLon = 111_320.0 * Math.cos(Math.toRadians(centroidLat))

x[i] = (lon[i] - centroidLon) * metersPerDegreeLon
y[i] = (lat[i] - centroidLat) * metersPerDegreeLat

// Rotáció -flightAngleDeg fokkal
double angleRad = Math.toRadians(-flightAngleDeg)
xRot = x * cos(angleRad) - y * sin(angleRad)
yRot = x * sin(angleRad) + y * cos(angleRad)

// Visszaforgatás (waypontoknál):
x = xRot * cos(-angleRad) - yRot * sin(-angleRad)
y = xRot * sin(-angleRad) + yRot * cos(-angleRad)

// XY → GPS
lat = centroidLat + y / metersPerDegreeLat
lon = centroidLon + x / metersPerDegreeLon
```

---

## 5. Polygon-scanline metszéspontok meghatározása

```
Minden scanline Y értékre:
  intersections = []
  Polygon élein végigmenve (Pi → Pi+1):
    Ha az él átlépi a scanline Y értékét:
      X metszéspont kiszámítva lineáris interpolációval
      intersections.add(X)
  intersections rendezve (növekvő X)
  Páronként: [X_be, X_ki] → sáv

Páratlan számú metszéspont:
  → Ez számítási hiba (pontosan érintett csúcsnál fordulhat elő)
  → Megoldás: a scanline Y értéke minimálisan (1e-9) eltolva
```

---

## 6. Szegmentálás döntés

```
totalWaypoints ≤ 99?
  │ IGEN → 1 szegmens, nincs felosztás
  │ NEM
  ▼
Szegmens határ meghatározása:
  maxPerSegment = 98  (1 tartalék a Landing/RTH-hoz)
  Szegmensek: [0..97], [98..195], [196..293], ...
  Minden szegmens: önálló misszió
  Az utolsó waypoint minden szegmensben: returnHome = true

Szegmens váltás logika (M01 oldali kezelés):
  currentSegmentIndex = 0
  [Feltöltés + Start] → segments[currentSegmentIndex] feltöltve
  1. szegmens vége (drón hazatér/land)
  Operátor: akku csere → [Feltöltés + Start] → segments[1] feltöltve
  ...
```

---

## 7. Offset döntés

```
config.offsetM > 0?
  │ NEM → polygon változatlan (rxOff = rx, ryOff = ry)
  │ IGEN
  ▼
Centroid a forgatott koordinátarendszerben:
  cxR = avg(rx), cyR = avg(ry)

Minden csúcspontra:
  dx = rx[i] - cxR
  dy = ry[i] - cyR
  dist = sqrt(dx² + dy²)
  dist > 0?
    │ IGEN → rxOff[i] = rx[i] + dx/dist * offsetM
    │        ryOff[i] = ry[i] + dy/dist * offsetM
    │ NEM  → rxOff[i] = rx[i]  (centroid pont: nem tolható)

Bővített bounding box újraszámítása rxOff, ryOff alapján.
Scanline metszéspontok a bővített polygonon számítódnak.

Megjegyzés:
  Konvex polygon esetén minden csúcspont pontosan offsetM méterrel
  tolódik ki → pontos offset.
  Konkáv polygon esetén a bővítés közelítő — egyes beugró sarkoknál
  a csúcspont esetleg befelé is tolódhat.
  Mezőgazdasági területeknél (jellemzően konvex sokszögek) ez elfogadható.
```

---

## 8. Akadály szűrés döntés

```
config.obstacles üres?
  │ IGEN → filtered = allWaypoints (szűrés kihagyva)
  │ NEM
  ▼
Minden waypointra:
  blocked = false
  Minden obs-ra a config.obstacles listában:
    obs.isDangerousAt(altM)?
      → altM <= obs.heightM?
          │ NEM  → nem veszélyes (magasabban repülünk) → következő obs
          │ IGEN
          ▼
        obs.containsPoint(wp.lat, wp.lon)?
          → dist = sqrt(dLat² + dLon²)  [2D, méterben]
          → dist <= obs.radiusM?
              │ IGEN → blocked = true, skippedByObstacle++, break
              │ NEM  → folytatás

  blocked = false? → filtered.add(wp)

filtered üres?
  │ IGEN → errorMessage = "Minden waypointot akadaly blokkol!..."
  │ NEM  → folytatás normálisan

Statisztikában jelzés: skippedByObstacle > 0 → "⚠ Akadaly miatt kihagyva: N wp"
```

---

## 9. Repülési idő becslés

```java
// GsdCalculator.estimatedFlightMinutes()

double totalDistanceM = 0;
for (int i = 1; i < waypoints.size(); i++) {
    totalDistanceM += distance(waypoints[i-1], waypoints[i]);
}

double flightTimeMin = totalDistanceM / (speedMs * 60.0);

// Fordulási idő hozzáadása (minden sávváltásnál ~5 másodperc)
int numStrips = (int)(areaWidth / stripSpacingM) + 1;
double turnTimeMin = numStrips * 5.0 / 60.0;

return flightTimeMin + turnTimeMin;
```

---

## 10. Fotószám-becslés — kamera időintervallum-korlát figyelembevétele — ✅ Implementálva (2026-07-03), eszközön még nem tesztelve

**Ok:** terepi teszt (M02_L1 §8) — a jelenlegi becslés távolság-alapú, a
tényleges triggerelés időalapú 2 mp-es padlóval (`CameraConfigurator.java:235`).

```
Jelenlegi (HIBÁS) döntés:
  estimatedPhotoCount = areaM2 / stripSpacing / photoDistanceM
  → hallgatólagosan feltételezi, hogy a kamera minden photoDistanceM
    méterenként pontosan egyszer exponál — FIGYELMEN KÍVÜL HAGYJA a
    kamera 2 mp-es minimum intervallumát

Javított döntés:
  effektívPhotoDistM = MAX(photoDistanceM, speedMs × 2.0)
  estimatedPhotoCount = areaM2 / stripSpacing / effektívPhotoDistM

  IF photoDistanceM < speedMs × 2.0:
    → a 2 mp-es kameракorlát aktív (a tervezettnél ritkábban fotózik)
    → UI figyelmeztetés megjelenítése (M02_L1 §8.2 szövege)
    → az estimatedMinutes NEM változik (a repülési idő a sebességtől/
      útvonalhossztól függ, nem a fotószámtól) — csak a fotószám-becslés
      és a hozzá tartozó figyelmeztetés érintett

  ELSE:
    → a tervezett sűrűség ténylegesen elérhető, nincs figyelmeztetés
```

**Példa (a terepi esethez hasonló beállítás — illusztráció, a pontos
0,15 ha-s repülés valós GSD/sebesség/frontlap értékei terepi validációval
pontosíthatók):**

```
Ha altM = 8 m, frontlap = 85%, speedMs = 3 m/s (alacsony repülés, tőszámláláshoz):
  photoDistanceM = footprintHeight × (1 - 0.85) ≈ 1.2 m  (kis GSD → kis footprint)
  photoDistanceM / speedMs = 1.2 / 3 ≈ 0.4 mp  ≪ 2.0 mp  → a korlát AKTÍV

  effektívPhotoDistM = MAX(1.2, 3 × 2.0) = 6.0 m
  → az effektív fotótávolság 5×-öse a tervezettnek → a becsült fotószám is
    kb. 5×-ösére csökken a javítás után — nagyságrendileg egyezik a
    terepen tapasztalt kb. 4×-es eltéréssel
```

**Kapcsolódó:** M02_L1 §8 (business-folyamat leírás), M04_L2 §... — a
`CameraConfigurator.startIntervalShooting()` maga NEM változik (a 2 mp-es
korlát hardveres/firmware-szintű, nem kerülhető meg) — csak a **becslés**
igazodik hozzá.
