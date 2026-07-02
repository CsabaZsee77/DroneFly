# L1 – Üzleti Folyamat – Grid Engine

**Modul:** M02
**Szint:** L1 – Üzleti Folyamat
**Verzió:** v1.2.0
**Létrehozva:** 2026-04-02
**Utolsó módosítás:** 2026-04-05
**Státusz:** ✅ Implementálva (v1.2.0)

---

## 1. Modul célja

Az M02 modul két engine-t tartalmaz:

1. **GsdCalculator** — a Phantom 4 Pro v1 kameraparaméterei alapján kiszámítja
   a repülési magasságot, a talajon látható képméretet (footprint), a sávközöket
   és a javasolt repülési sebességet a kívánt GSD értékből.

2. **GridMissionGenerator** — egy tetszőleges GPS polygon és a MissionConfig
   alapján kígyózó (lawnmower / boustrophedon) waypoint útvonalat generál,
   99 waypontos szegmensekre osztva.

---

## 2. GSD kalkulátor — P4P v1 kameraparaméterek

| Paraméter | Érték |
|-----------|-------|
| `SENSOR_WIDTH_MM` | 13.2 mm |
| `SENSOR_HEIGHT_MM` | 8.8 mm |
| `FOCAL_LENGTH_MM` | 8.8 mm |
| `IMAGE_WIDTH_PX` | 5472 px |
| `IMAGE_HEIGHT_PX` | 3648 px |

**GSD formula:**

```
GSD (cm/px) = (H_m × sensor_width_mm × 100) / (focal_length_mm × image_width_px)

→ Magasságra átrendezve:
H_m = (GSD_cm × focal_length_mm × image_width_px) / (sensor_width_mm × 100)
```

**Példaértékek (P4P v1):**

| GSD (cm/px) | Magasság (m) | Footprint szélesség | Footprint magasság |
|-------------|-------------|--------------------|--------------------|
| 0.5         | 19.5 m      | 25.7 m             | 17.1 m             |
| 1.0         | 39.1 m      | 51.5 m             | 34.3 m             |
| 2.0         | 78.2 m      | 103.0 m            | 68.6 m             |
| 3.0         | 117.3 m     | 154.5 m            | 103.0 m            |
| 5.0         | 195.5 m     | 257.5 m            | 171.5 m            |

---

## 3. Javasolt sebesség számítás

A mechanikus záridő alapján javasolt maximális sebesség, amelynél
a mozgásból eredő elmosódás (motion blur) még 1 pixelnél kisebb:

```
javasolt_sebesség = 0.5 × (GSD_m) × záridő_szorzó
záridő_szorzó = 800  (1/800 s-os mechanikus záridőhöz)

→ javasolt_sebesség = 0.5 × (gsdCm / 100) × 800

Minimum: 3 m/s
Maximum: 12 m/s (MSDK v4 waypoint limit)
```

| GSD (cm/px) | Javasolt sebesség |
|-------------|------------------|
| 0.5         | 2 m/s → **3 m/s** (min) |
| 1.0         | 4 m/s |
| 2.0         | 8 m/s |
| 3.0         | 12 m/s (max) |
| 5.0+        | 20 m/s → **12 m/s** (max) |

---

## 4. Grid generálás folyamata

```
[Bemenet]
  polygonPoints: List<GeoPoint>   (≥ 3 pont)
  config: MissionConfig
    → GSD, sidelap, frontlap, sebesség, irány
    → droneProfile (kamera paraméterek)
    → offsetM  (0 = nincs, >0 = túlrepülési határ méterben)
    → obstacles (akadályok listája)

      │
      ▼
1. Magasság számítás GSD-ből (drón profil alapján)
   altM = GsdCalculator.altitudeFromGsd(gsdCm, droneProfile)
   Korlát: 10–300 m
      │
      ▼
2. Centroid számítás (GPS → referenciapont)
      │
      ▼
3. Koordináta transzformáció:
   GPS (lat/lon) → helyi XY méter (centroid körüli)
      │
      ▼
4. Koordináta rotáció:
   -flightAngleDeg fokkal elforgatva
   (a sávok vízszintesek lesznek a forgatott rendszerben)
      │
      ▼
5. Befoglaló téglalap meghatározása (min/max X, Y)
   Terület számítás (Gauss-képlet, m²)
      │
      ▼
6. Offset alkalmazása (ha config.offsetM > 0):
   Minden csúcspontot eltolunk a centroidtól kifelé offsetM méterrel
   → bővített polygon és bővített bounding box
   → konvex területeken pontos; konkáv polygon esetén közelítés
      │
      ▼
7. Párhuzamos scanline-ok generálása:
   Y irányban, lépésköz = stripSpacingM
   Első sáv: yMin + stripSpacing / 2
      │
      ▼
8. Polygon-scanline metszéspontok meghatározása
   (minden scanline-ra: belépési és kilépési X koordináta a bővített polygonon)
      │
      ▼
9. Kígyózó (serpentine) sorrend:
   Páros sávok: balról jobbra
   Páratlan sávok: jobbra balra
      │
      ▼
10. Fotó waypontok elhelyezése sávokon belül:
    X irányban, lépésköz = photoDistanceM
    (minden waypoint shootPhoto = true)
      │
      ▼
11. Visszaforgatás + visszatranszformáció:
    helyi XY → GPS (lat/lon)
      │
      ▼
12. Akadály szűrés (ha config.obstacles nem üres):
    Minden waypointra:
      obs.isDangerousAt(altM)?  (flightAlt ≤ obs.heightM)
        ÉS obs.containsPoint(lat, lon)?  (2D körzetben van?)
      → IGEN: waypoint kihagyva, skippedByObstacle++
      → NEM: waypoint megtartva
      │
      ▼
13. Szegmentálás:
    Ha totalWaypoints > 99 → automatikus felosztás (99 wp/szegmens)
      │
      ▼
[Kimenet]
  GeneratorResult:
    segments:          List<List<WaypointData>>
    totalWaypoints:    int
    areaM2:            double
    estimatedMinutes:  double
    altitudeM:         double
    stripSpacingM:     double
    photoDistM:        double
    skippedByObstacle: int     (akadály miatt kihagyott wp-ok száma)
    terrainCorrected:  boolean (domborzatkövetés alkalmazva?)
    terrainMinAlt:     float   (legkisebb korrigált magasság)
    terrainMaxAlt:     float   (legnagyobb korrigált magasság)
    errorMessage:      String  (null ha sikeres)
```

---

## 5. Kígyózó minta illusztrálva

```
Terület felülnézetből:
  ┌─────────────────────────────────┐
  │  →  →  →  →  →  →  →  →  1. sáv│
  │                            ↓    │
  │  ←  ←  ←  ←  ←  ←  ←  ←  2. sáv│
  │  ↓                              │
  │  →  →  →  →  →  →  →  →  3. sáv│
  │                            ↓    │
  │  ←  ←  ←  ←  ←  ←  ←  ←  4. sáv│
  └─────────────────────────────────┘

● = fotó waypoint (photoDistanceM távolságonként)
→ = haladási irány

Előny: minimális fordulási szám, egyenletes lefedettség
```

---

## 6. Kapcsolódó modulok

| Modul | Kapcsolat típusa |
|-------|-----------------|
| M01 Misszió Tervező | **Közvetlen felhasználó** — generateMission() hívja |
| M03 Export | **Kimenet** — WaypointData lista exportra kerül |
| M04 DJI | **Kimenet** — WaypointData lista feltöltésre kerül |

---

## 7. Mintavételi misszió generátor (SamplingMissionGenerator) — 🔲 Tervezve (2026-07-02)

**Üzleti kontextus:** M01 §10. Az engine két lépésből áll: mintapontok kijelölése
a polygonon belül, majd minden ponthoz egy 3-waypointos "süllyedés-fotó-emelkedés"
szekvencia felépítése.

### 7.1 SamplingPointGenerator — mintapont-kijelölés

A Dronterapia `utils/sampling_plan.py` Python logikájának Java portja (offline
működés — nincs függőség a Dronterapia-szinkronra a misszió generálásához,
csak a polygon geometria kell, ami már helyben, a térképen rajzolva rendelkezésre
áll).

```
[Bemenet]
  polygonPoints: List<GeoPoint>  (a már meglévő, GridMissionGenerator-rel közös
                                   polygon-rajzolási mechanizmusból)
  nPoints: int
  method: "stratified" | "halton" | "random"
  seed: long  (reprodukálhatóság)

      │
      ▼
1. Terület számítás — a GridMissionGenerator MEGLÉVŐ helyi XY transzformációját
   és Shoelace-formuláját használja fel (nincs duplikált geometria-kód)
      │
      ▼
2. Mintapontok generálása a polygon bounding box-án belül, elutasításos mintavétellel
   (rejection sampling — a polygonon kívül eső jelöltek eldobása), a választott
   módszer szerint:
   - "stratified": rács (√n × √n) + jitter minden cellán belül
   - "halton": kvázi-véletlen Halton-szekvencia (bázis 2, 3)
   - "random": egyenletes véletlen
      │
      ▼
[Kimenet]
  List<GeoPoint>  — n db pont (lat, lon), mind a polygonon belül
```

### 7.2 Ajánlott mintapontszám

Ugyanaz a statisztikai ökölszabály, mint a Dronterapia oldalon
(`recommended_n_points()`), hogy a két rendszer konzisztens ajánlást adjon:

```
n = ceil((1.96 × CV / 0.1)²)      CV alapértelmezett: 0.3
n_terulet = clamp(area_ha × 3, 15, 60)
ajánlott = max(n, n_terulet)
```

### 7.3 SamplingMissionGenerator — waypoint-szekvencia építése

```
[Bemenet]
  samplePoints: List<GeoPoint>
  transitAltitudeM: double   (akadálybiztos, magasabb)
  sampleAltitudeM: double    (GSD-hez szükséges, alacsonyabb)
  hoverSeconds: float        (alapért.: 2–3 mp)

      │
      ▼
MINDEN mintaponthoz (i = 0..n-1) HÁROM WaypointData generálása:

  1. Érkezés (transit):    (lat_i, lon_i, transitAltitudeM), shootPhoto=false
  2. Mintavétel (sample):  (lat_i, lon_i, sampleAltitudeM),  shootPhoto=true
                           + hoverSeconds mező (ÚJ mező a WaypointData-ban,
                             ld. lentebb)
  3. Emelkedés (transit):  (lat_i, lon_i, transitAltitudeM), shootPhoto=false

      │
      ▼
[Kimenet]
  GeneratorResult (a GridMissionGenerator.GeneratorResult-hoz hasonló szerkezet):
    segments: List<List<WaypointData>>   (99-es szegmensekre osztva —
                                           MEGLÉVŐ szegmentáló logika újrahasznosítva)
    totalWaypoints: int   (= 3 × n mintapont)
    areaM2, estimatedMinutes, errorMessage: mint a Grid Engine-nél
```

**Waypoint-szám becslés:** 20–50 mintapont esetén 60–150 waypoint — a meglévő
99-es szegmens-limit és automatikus akkumulátorcsere-kezelés (M01 §8) változtatás
nélkül újrahasznosítható.

### 7.4 WaypointData bővítés (tervezett mező)

```java
public class WaypointData {
    // ... meglévő mezők változatlanul ...
    public float hoverSeconds = 0f;  // ÚJ: 0 = nincs megállás (grid misszió),
                                       // >0 = STAY akció ennyi másodpercig
                                       // (mintavételi misszió alacsony waypontjain)
}
```

### 7.5 Kapcsolódó modulok

| Modul | Kapcsolat típusa |
|-------|-----------------|
| M01 Misszió Tervező | **Közvetlen felhasználó** — új "Mintavételi misszió" mód hívja |
| M04 DJI | **Kimenet** — a `hoverSeconds > 0` waypontokhoz NORMAL-módú, akció-alapú feltöltés szükséges (nem a meglévő CURVED útvonal) |
| Dronterapia `utils/sampling_plan.py` | **Referencia-implementáció** — azonos algoritmus, Python↔Java portolás, nem futásidejű függőség |
