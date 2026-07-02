# L2 – Döntési Logika – Blokk-felosztás

**Modul:** M07
**Szint:** L2 – Döntési Logika
**Verzió:** v0.1.1
**Létrehozva:** 2026-06-19
**Utolsó módosítás:** 2026-07-02
**Státusz:** ✅ Kézi UI-verifikáció OK Crystal Sky-n (2026-07-02)

---

## 1. Bemenetek validációja

```
aoiPoints.size() < 3?
  │ IGEN → errorMessage = "Legalább 3 AOI pont szükséges"
  │ NEM → folytatás

aoiAreaM2 < 1000 m²?  (kb. egy fél rácscella)
  │ IGEN → errorMessage = "A terület túl kicsi blokkokra bontáshoz —
  │         használd a sima rácsgenerátort (M02)"
  │ NEM → folytatás

cellWidthM < 30 vagy cellHeightM < 30?
  │ IGEN → errorMessage = "Cella mérete legalább 30 m kell legyen"
  │ NEM → folytatás

cellWidthM > 500 vagy cellHeightM > 500?
  │ IGEN → errorMessage = "Cella mérete legfeljebb 500 m lehet (VLOS)"
  │ NEM → folytatás

overlapBufferM < 10?
  │ IGEN → figyelmeztetés (nem hiba): "Kis átfedés — a fotogrammetria
  │         szoftverek 20-50 m-t igényelnek blokkok között"
  │ NEM → folytatás

overlapBufferM > cellWidthM / 2 vagy > cellHeightM / 2?
  │ IGEN → errorMessage = "Az átfedési puffer nem lehet nagyobb a cella
  │         felénél (a misszió-poligonok teljesen elnyelnék a szomszédot)"
  │ NEM → folytatás

minCoveragePercent < 0 vagy > 100?
  │ IGEN → errorMessage = "Min. lefedettség: 0–100% között"
  │ NEM → folytatás
```

---

## 2. Koordinátarendszer és origó választás

```
Helyi sík XY (méter) — M02-vel azonos közelítés:
  metersPerDegreeLat = 111_320.0
  metersPerDegreeLon = 111_320.0 * Math.cos(Math.toRadians(originLat))

Origó (a rács referenciapontja):
  config.originMode = "centroid":
    originLat = average(aoiPoints.lat)
    originLon = average(aoiPoints.lon)
    → szimmetrikus rácsillesztés, kiegyensúlyozott cellaelosztás

  config.originMode = "first_vertex":
    originLat = aoiPoints[0].lat
    originLon = aoiPoints[0].lon
    → a rács első cellája az első csúcsnál kezdődik
    → akkor hasznos, ha a felhasználó tudatosan akar egy sarokpontot

  config.originMode = "manual":
    originLat = config.originPoint.lat
    originLon = config.originPoint.lon
    → későbbi fejlesztés: kattintással adott origó
    → MVP-ben opcionális, default = "centroid"

Megjegyzés:
  Az origó NEM esik szükségszerűen cellahatárra — a (col, row) = (0, 0)
  index egy cellát jelöl, amelynek bal-alsó sarka az origóhoz képest:
    x = col * cellWidthM
    y = row * cellHeightM
  Negatív indexek (col<0, row<0) megengedettek — az AOI az origótól
  bármelyik irányba terjeszkedhet.
```

---

## 3. Rács generálása — algoritmus

```
1. AOI pontok GPS → helyi XY transzformációja az origó körül:
     x[i] = (lon[i] - originLon) * metersPerDegreeLon
     y[i] = (lat[i] - originLat) * metersPerDegreeLat

2. AOI pontok rotációja -rotationDeg fokkal:
     a = Math.toRadians(-rotationDeg)
     xR[i] = x[i] * cos(a) - y[i] * sin(a)
     yR[i] = x[i] * sin(a) + y[i] * cos(a)
   → a rotált AOI most a rács tengelyeivel párhuzamos

3. Befoglaló téglalap a rotált AOI-ra:
     minXR = min(xR), maxXR = max(xR)
     minYR = min(yR), maxYR = max(yR)

4. Cella-index tartomány meghatározása:
     colStart = floor(minXR / cellWidthM)
     colEnd   = floor(maxXR / cellWidthM)
     rowStart = floor(minYR / cellHeightM)
     rowEnd   = floor(maxYR / cellHeightM)
   → ezek a befoglaló rács cella-indexei (mindkét végpont inkluzív)

5. Minden (row, col) párra:
     a. Rotált cella sarkok (lokális koordinátákban):
        x0 = col * cellWidthM
        y0 = row * cellHeightM
        cellRotated = [(x0, y0), (x0+W, y0), (x0+W, y0+H), (x0, y0+H)]

     b. Cella visszaforgatása +rotationDeg fokkal (helyi XY-ra)
     c. Cella visszatranszformálása GPS-re → cellPolygon

     d. Misszió-poligon számítása:
        bufferedCellRotated = cella minden oldalán kibővítve overlapBufferM-mel
            = [(x0-B, y0-B), (x0+W+B, y0-B), (x0+W+B, y0+H+B), (x0-B, y0+H+B)]
        clippedRotated = aoiClipPolygon ∩ bufferedCellRotated
            (Sutherland-Hodgman algoritmus a rotált koordinátákon)
        Visszaforgatás → GPS → missionPolygon

     e. Területszámítás (Shoelace) → cellAreaM2, missionAreaM2
        coverageRatio = missionAreaM2 / cellAreaM2

     f. Szűrés:
        coverageRatio * 100 < minCoveragePercent?
          │ IGEN → cella eldobva (nem kerül be a blocks listába)
          │ NEM  → block hozzáadva

6. blocks.isEmpty()?
     │ IGEN → errorMessage = "Nincs érvényes blokk — növeld a
     │         cellaméretet vagy csökkentsd a min. lefedettséget"
     │ NEM  → folytatás
```

---

## 4. Sutherland-Hodgman polygon-kivágás

```
Bemenet:
  subject:  List<double[2]>  (az AOI a rotált koordinátarendszerben)
  clip:     List<double[2]>  (a kibővített cella téglalap, konvex)

Kimenet:
  result:   List<double[2]>  (subject ∩ clip)

Algoritmus:
  output = subject
  for each edge in clip (clip konvex, ezért egyenként vágható):
    input  = output
    output = []
    if input.isEmpty(): return []
    S = input[input.size() - 1]
    for each E in input:
      if isInside(E, edge):
        if not isInside(S, edge):
          output.add(intersect(S, E, edge))
        output.add(E)
      else if isInside(S, edge):
        output.add(intersect(S, E, edge))
      S = E
  return output

isInside(P, edge): edge balra-pozitív félsíkjában van-e P?
  → cross product alapján: (edge.end - edge.start) × (P - edge.start) ≥ 0

intersect(S, E, edge): szakaszmetszéspont számítása
  → paraméteres szakasz-szakasz metszéspont

Megjegyzés:
  A subject AOI lehet KONKÁV — a Sutherland-Hodgman konvex clip-pel
  konkáv subject-en is helyesen működik.
  A clip (kibővített cella) MINDIG konvex (téglalap) → ez garantált.

  Önmetsző AOI: definíciósan nem támogatott (mint az M02-ben sem) —
  a felhasználói rajzolás során sem megakadályozzák, az operátor felelőssége.
```

---

## 5. Cella-tap detekció — a kulcsmatek

A felhasználói koppintás GPS pontja → cella (col, row) számítás.
**Nincs szükség polygon-in-point tesztre minden cellára.**

```
Bemenet:
  tapLat, tapLon          (felhasználó koppintási pontja)
  originLat, originLon    (a rács origója)
  rotationDeg             (rács dőlésszöge)
  cellWidthM, cellHeightM (cellaméret)
  blocks                  (érvényes blokkok listája)

Számítás:
1. GPS → helyi XY:
     tapX = (tapLon - originLon) * metersPerDegreeLon
     tapY = (tapLat - originLat) * metersPerDegreeLat

2. Rotáció -rotationDeg fokkal:
     a = Math.toRadians(-rotationDeg)
     tapXR = tapX * cos(a) - tapY * sin(a)
     tapYR = tapX * sin(a) + tapY * cos(a)

3. Cella-index:
     col = floor(tapXR / cellWidthM)
     row = floor(tapYR / cellHeightM)

4. Blokk keresés:
     for each b in blocks:
       if b.row == row and b.col == col:
         return b
     return null  (kívülre koppintott, vagy a cella szűrve lett)
```

**Komplexitás:** O(1) számítás + O(N) lineáris keresés a blocks-on
(maxim. néhány tíz blokk → elhanyagolható).

**Alternatíva (sokkal több cella esetén):** HashMap<Long, Block>, ahol
a kulcs `(row << 32) | (col & 0xFFFFFFFFL)` — O(1) keresés. **MVP-ben
nem szükséges**, mivel 100 ha alatt < 50 cella az általános.

---

## 6. Blokk állapot átmenetek

```
                            [Felosztás indítás]
                                    │
                                    ▼
                              NOT_STARTED
                              (szürke)
                                    │
                            [Feltöltés + Start]
                                    │
                                    ▼
                              IN_PROGRESS
                              (narancs)
                                    │
                          ┌─────────┴────────┐
                          │                  │
                  [Megszakít / Pause]   [Kész gomb]
                  ↓                         ↓
                  IN_PROGRESS               DONE
                  (folytatható)             (zöld)
                                            │
                                  [Visszaállít gomb]
                                            │
                                            ▼
                                       NOT_STARTED

Megjegyzések:
- A [Kész] állapot manuális — a drón "land at last waypoint" eseményt
  nem értelmezi automatikusan kész-állapotnak, mert a műveletet
  megszakíthatja akkucsere is.
- IN_PROGRESS → IN_PROGRESS átmenet jelzi a megszakított és újra
  feltöltött helyzetet. A meglévő M04 resume-from-waypoint logika
  blokk-szinten teljesen átveszi a feladatot — M07 csak az állapotot
  tárolja, nem beavatkozik.
- A DONE állapot visszaállítható (visszaállít gomb), ha az operátor
  felismeri, hogy a blokkot újra kell repülni (rossz expozíció, stb.)
```

---

## 7. Misszió generálás döntés a kiválasztott blokkra

```
selectedBlock != null?
  │ NEM → [Generate] gomb letiltva (rács mód aktív → blokk kell)
  │ IGEN
  ▼
polygonPoints = selectedBlock.missionPolygon
config = currentMissionConfig  (a meglévő M01 állapot, GSD, sidelap stb.)

Megjegyzés az M02 offsetM mezőről:
  A MissionConfig.offsetM (AOI túlrepülési határ) és az M07 overlapBufferM
  KÜLÖNBÖZŐ CÉLT szolgálnak, és párhuzamosan érvényesülhetnek:
    - overlapBufferM: blokk-blokk átfedés a fotogrammetriai illesztéshez,
                       a misszió-poligonban már beépítve
    - offsetM:        a kapott misszió-poligonon kívülre lépés (pl. lassú
                       kanyarodáshoz extra biztonsági sáv)
  Ezek összeadódnak — ha mindkettőt használja a felhasználó, a hatás kumulatív.
  Külön figyelmeztetés NEM szükséges, csak az UI tooltipek magyarázzák.

GridMissionGenerator.generate(polygonPoints, config)
  → szokásos kígyózó útvonal
  → szegmentálás (98 wp/szegmens) — egy blokk általában 1 szegmens
    (40 ha ⇒ ~1500 wp; egy 1.5 ha blokk ⇒ ~50 wp)

Felhasználói visszajelzés:
  "B-2-3: 47 waypoint, becsült idő 8.3 perc"

Megjegyzés:
  A blokk és a misszió-konfig FÜGGETLEN — ha a felhasználó középúton
  módosítja a GSD-t, az új paraméterekkel újragenerálódik a misszió,
  de a blokk-rács változatlan marad.
```

---

## 8. Origó-stabilitás projekt mentésnél

```
Probléma:
  Ha az origó "centroid" mód, és a felhasználó utólag módosítja az AOI-t
  (új csúcs hozzáadása), a centroid eltolódik → a rács elmozdul →
  a már lerepült blokkok "elcsúsznak".

Megoldás:
  A rács generálásakor a kiszámított origó (gridOriginLat, gridOriginLon)
  + rotationDeg az AOI-tól FÜGGETLENÜL eltárolódik a projektben.
  Az újragenerálás ugyanazokat a fix értékeket használja, kivéve ha
  a felhasználó explicit "Új rács" gombot nyom.

Ennek következménye:
  - Egyszer beállított rács → AOI módosítás után is ugyanazok a cellák
  - Új cellák bukkanhatnak fel (ha az AOI kiterjedt) → új blokk-ID-k
  - Régi cellák eltűnhetnek (ha az AOI zsugorodott) → orphan állapot:
      ha egy DONE blokkot eltüntet a szűrés, ne tűnjön el csendben
      → figyelmeztetés: "B-2-3 (DONE) már nincs az AOI-n belül.
         Tartsd meg jelentésként? [Igen] [Eltávolít]"
```

---

## 9. Hibakezelés — összegzés

| Eset | Reakció |
|------|---------|
| AOI < 3 pont | errorMessage, dialog nem nyílik |
| Cella mérete tartományon kívül | Toast: "Cella 30–500 m között" |
| Nincs érvényes blokk (minden szűrve) | errorMessage, javasolt korrekció |
| AOI módosult, fix origó megmaradt | Csendes — az új rács cellaszámlálása változhat |
| DONE blokk a szűrés után eltűnik | Figyelmeztetés, döntés megtartásról |
| Tap detekcióhoz nem található blokk | Hangtalan — koppintás kívülre |
| Tap detekcióhoz több blokk illeszkedik | NEM lehetséges (egyértelmű col/row) |
| Misszió-poligon < 3 pont az SH után | Block eldobva (cellAreaM2 < 0 védelem) |
