# L1 – Üzleti Folyamat – Blokk-felosztás

**Modul:** M07
**Szint:** L1 – Üzleti Folyamat
**Verzió:** v0.1.1
**Létrehozva:** 2026-06-19
**Utolsó módosítás:** 2026-07-02
**Státusz:** ✅ Kézi UI-verifikáció OK Crystal Sky-n (2026-07-02)

---

## 1. Modul célja

Az M07 modul nagy területű (>10 ha) felmérések kezelésére szolgál, amelyek
egyetlen menetben **VLOS** (Visual Line Of Sight) szempontból nem repülhetők
biztonságosan egyetlen felszállási pontból.

A felhasználó egyetlen AOI poligont rajzol, megadja a blokk-rács paramétereit
(cellaméret, dőlésszög, blokk-átfedés), majd a rendszer a poligont **rácscellákra
bontja**. A felhasználó kattintással választja ki, melyik blokkot kívánja
éppen feltölteni a drónra (a megfelelő felszállási pontról); a blokkok állapota
(még nem repült / folyamatban / kész) perzisztens és színkódolva jelenik meg
a térképen.

Minden blokk önálló misszióként generálódik (az M02 Grid Engine-en keresztül),
és a **szomszédos blokkok között garantáltan van átfedés** a fotogrammetriai
összeillesztéshez (Pix4D, Metashape, WebODM).

**Üzleti motiváció:**

- **VLOS korlát (elsődleges):** Phantom 4 Pro 100 m felett már szabad szemmel
  rosszul látható; a teljes terület egyetlen felszállási ponttól is gyakran
  kívül esik a vizuális látótávolságon. **A blokkokra bontás célja, hogy minden
  blokkhoz egyedi felszállási hely választható legyen,** ahonnan az operátor
  szemmel követheti a drónt. Egy 600×400 m-es területnél jellemzően 6–10
  felszállási pont szükséges.
- **Építészeti / földmérési piac:** 20–50 ha-os munkák GCP-vel pozicionált
  ortomozaikkal — több felszállási hely + tartós VLOS garancia szükséges.
- **Mezőgazdasági nagytáblák:** Magyar gabonatáblák gyakran 40–100 ha közöttiek,
  szintén VLOS-korláttal.
- **Akkumulátor (másodlagos):** Egy P4P akkumulátor ~16 perc hasznos repülésre
  elég. Egy blokk lehet kisebb vagy nagyobb, mint amit egy akku lerepül —
  **nem osztunk akkuhatáron**, hanem dinamikusan kezeljük: ha az akku lemerül
  a blokk közepén, a meglévő pause / akkucsere / resume flow folytatja a
  blokkot onnan, ahol abbamaradt. (Az M08 akku-statisztika modul előrejelzést
  is fog adni, hogy melyik akkuval melyik blokk biztonságosan lerepülhető.)
- **MSDK v4 waypoint limit:** misszióként 99 waypoint — nagy területen
  szegmentálás kötelező, de az M02 belső szegmentálása nem ad VLOS-előnyt
  (a drón mindvégig a teljes területen mozog).

---

## 2. Bemenetek és kimenetek

```
[Bemenet]
  aoiPoints: List<GeoPoint>      (AOI poligon, ≥ 3 pont)
  config: BlockGridConfig
    → cellWidthM       (cella szélessége méterben, pl. 120)
    → cellHeightM      (cella magassága méterben, pl. 120)
    → rotationDeg      (rács dőlésszöge fokban, 0–179)
    → overlapBufferM   (szomszédos blokkok közti átfedés méterben, pl. 40)
    → minCoveragePercent (cellán belüli AOI lefedettség küszöb, pl. 15%)
    → originMode       ("centroid" | "first_vertex" | "manual")
    → originPoint      (csak originMode = "manual" esetén)

      │
      ▼
[Generálás] BlockGridGenerator.generate(aoiPoints, config)

      │
      ▼
[Kimenet]
  GridResult:
    blocks:          List<Block>
    totalBlocks:     int
    aoiAreaM2:       double
    gridOriginLat:   double
    gridOriginLon:   double
    gridRotationDeg: double
    errorMessage:    String  (null ha sikeres)

  Block:
    id:              String      (pl. "B-2-3" — sor-oszlop)
    row:             int
    col:             int
    cellPolygon:     List<GeoPoint>  (4-pontos cella téglalap, GPS-ben)
    missionPolygon:  List<GeoPoint>  ((cella+puffer) ∩ AOI, GPS-ben)
    cellCenter:      GeoPoint        (cella geometriai közepe)
    cellAreaM2:      double          (a teljes cella területe)
    missionAreaM2:   double          (a misszió-poligon területe)
    coverageRatio:   double          (missionAreaM2 / cellAreaM2)
    status:          BlockStatus     (NOT_STARTED | IN_PROGRESS | DONE)
```

---

## 3. Teljes folyamat — rajzolástól a repülésig

```
1. AOI rajzolás (M01 meglévő UI)
      │  Polygon Draw mód → érintéssel csúcsok hozzáadása
      ▼
2. Felosztás indítása ([Felosztás] gomb az M01 panelen)
      │  Dialog megnyitása: cella W×H, dőlésszög, átfedés-puffer
      │  + min. lefedettség %, + origó mód
      │  + info-szöveg: becsült összes repülési idő és akkuszám
      ▼
3. BlockGridGenerator.generate() futtatása
      │  Rács illesztése az AOI fölé
      │  → minden cella: cellPolygon (rotált téglalap)
      │  → minden cella: missionPolygon = (cellPuffer ∩ AOI)
      │  → cella eldobása ha coverageRatio < minCoveragePercent
      ▼
4. Térképi vizualizáció
      │  Minden blokk: kitöltött, áttetsző poligon a térképen
      │  Színkód:
      │    NOT_STARTED → szürke (#80808080)
      │    IN_PROGRESS → narancs (#FFA50080)
      │    DONE        → zöld   (#00C85080)
      │  Blokk-felirat középre: "B-2-3"
      ▼
5. Blokk kiválasztása (érintéssel)
      │  Felhasználó megérinti a térképet → koordináta inverz-transzformálva
      │  → (col, row) számítva → blokk megkeresve
      │  → kiválasztott blokk vastagabb kerettel kiemelve
      │  → panelen: "Kiválasztva: B-2-3 (1.42 ha, 80% lefedettség)"
      ▼
6. Misszió generálása a kiválasztott blokkra
      │  [Generate] gomb az M01 panelen
      │  → polygonPoints = block.missionPolygon
      │  → GridMissionGenerator.generate(polygonPoints, missionConfig)
      │  → eredmény: szokásos kígyózó útvonal, max 99 wp / szegmens
      ▼
7. Feltöltés és repülés (M04 meglévő flow)
      │  [Feltöltés + Start] → block.status = IN_PROGRESS
      │  → blokk színe narancsra vált
      ▼
8. Megszakítás vagy befejezés
      │  Megszakítás (akkucsere, alacsony töltöttség, operátori döntés):
      │    → block.status IN_PROGRESS marad
      │    → következő alkalommal újraválasztható, M04 onnan folytat
      │  Befejezés (összes wp lerepült):
      │    [Kész] gomb → block.status = DONE
      │    → blokk színe zöldre vált
      ▼
9. Következő blokk
      │  Visszalépés az 5. lépéshez egy másik blokkal
      │  Operátor új felszállási helyre megy + akkucsere VLOS-ben
      ▼
10. Projekt mentés / betöltés
      │  ProjectManager.saveProject() — blokk-állapotok a .flightprogram.json-ban
      │  Betöltéskor a teljes rács + állapotok visszaállnak
```

---

## 4. Rácsillesztés szemléltetve

```
AOI (amorf, dőlt tábla):

         . . . . . . . .
       .                .
     .                    .
   .                       .
   .                       .
     .                    .
       .                .
         . . . . . . . .

Rács paraméterek:
  cellWidthM    = 120 m
  cellHeightM   = 120 m
  rotationDeg   = 15°
  overlapBufferM = 40 m

Rácsillesztés (rotált, AOI centroidjából):

     ┌─────┬─────┬─────┐
     │ 0,0 │ 0,1 │ 0,2 │     ← cellák a rotált rácsban
     ├─────┼─────┼─────┤
     │ 1,0 │ 1,1 │ 1,2 │
     ├─────┼─────┼─────┤
     │ 2,0 │ 2,1 │ 2,2 │
     └─────┴─────┴─────┘

Lefedettség szűrés (minCoveragePercent = 15%):

     ╭─────┬─────┬─────╮
     │ 0,0 │ 0,1 │ 0,2 │
     ├─────┼─────┼─────┤
     │ 1,0 │ 1,1 │ 1,2 │   ← 0,0 és 2,2 sarkok kicsi
     ├─────┼─────┼─────┤      lefedettség miatt eldobva
     │ 2,0 │ 2,1 │ 2,2 │
     ╰─────┴─────┴─────╯

  → 7 érvényes blokk (a 9-ből)

Misszió-poligon (a 1,1 blokkra):

       cella  (120×120m)
   ┌─────────────────┐
   │   +40m puffer   │
   │  ┌───────────┐  │
   │  │           │  │       ← (cell + 40m) ∩ AOI
   │  │  AOI rész │  │         a sárga: misszió-poligon
   │  │           │  │
   │  └───────────┘  │
   │                 │
   └─────────────────┘

Eredmény: a szomszédos blokkok 80 m sávban átfedik egymást
         (mindkét oldal +40m puffert ad a cella szélén túl)
```

---

## 5. Blokk-átfedés stratégia (a kulcs!)

```
Cellák kiterjedése:    120 m × 120 m
overlapBufferM:        40 m

Misszió-poligonok:     (cella + 40m minden oldalon) ∩ AOI
                       → 200 m × 200 m kiterjedésű potenciálisan

Két szomszédos blokk átfedési zónája:
  cella A:  x ∈ [0, 120]
  cella A misszió: x ∈ [-40, 160]   (40 m túllép mindkét oldalon)
  cella B:  x ∈ [120, 240]
  cella B misszió: x ∈ [80, 280]   (40 m túllép mindkét oldalon)

  → ÁTFEDÉS: x ∈ [80, 160] → 80 m széles átfedési sáv

Ez a 80 m sáv 75% sidelap mellett (44 m sávköz GSD=3 cm/px-nél):
  → kb. 2 teljes repülési sáv mindkét blokkban
  → bőséges tie-point a fotogrammetriához

Ajánlott overlapBufferM értékek:
  GSD 2 cm/px  → 30 m (footprint ~70 m, 2-3 sáv átfedés)
  GSD 3 cm/px  → 40 m (footprint ~105 m, 2-3 sáv átfedés)
  GSD 5 cm/px  → 60 m (footprint ~175 m, 2-3 sáv átfedés)
```

---

## 6. Blokk-azonosítás és felirat

```
Cella indexelés: (row, col) — 0-tól indul, balról-jobbra, fentről-lefelé
                              a forgatott rács lokális koordinátarendszerében

Megjelenített ID:  "B-{row+1}-{col+1}"  (1-től indexelve a felhasználónak)
                   pl. "B-2-3" = 2. sor, 3. oszlop

Térképi felirat:
  ┌─────────────┐
  │             │
  │   B-2-3     │   ← középre, fehér szöveg fekete háttérrel
  │   1.42 ha   │   ← csak hosszú nyomásnál: státusz, lefedettség, terület
  │             │
  └─────────────┘
```

---

## 7. Dialog információs összegzés (akkuszám-becslés)

A `[Felosztás]` dialog alján megjelenik egy **informatív** összegzés a generált
rács paramétereiből származó becslésekkel. **Ez tájékoztató, nem korlátoz:**

```
📊 Összegzés:
   8 blokk · 23.4 ha összesen
   Becsült repülési idő: ~47 perc (összes blokk)
   Akkumulátor szükséglet: ~3 db (16 perc / akku tipikus hasznos kapacitás)
   ⓘ A valós igény szél, hőmérséklet és blokk-újraindítások miatt eltérhet
```

**Az akkuszám-becslés célja:**
- Logisztika: a felhasználó tudja, hány töltött akkut vigyen ki a helyszínre
- Időbecslés: körülbelül mennyit fog tartani a teljes munka

**A becslés NEM:**
- Kötelező felosztási kényszer (blokkok mérete VLOS-alapú, nem akku-alapú)
- Pontos időjárás-jelzés (szél/hőmérséklet hatása csak az M08 modulban kerül be)

**Későbbi M08 integráció:** ha a felhasználónak vannak felismert akkumulátorai
(pl. "Matyi 1", "Matyi 2"), az összegzés finomulhat:
```
📊 Akkumulátor-allokáció (M08 alapú, opcionális):
   Matyi 1 (95%, eg. 92%) → B-1-1, B-1-2 (15 perc, biztonságos)
   Matyi 2 (88%, eg. 87%) → B-2-1, B-2-2 (14 perc, biztonságos)
   Matyi 3 (75%, eg. 78%) → B-2-3, B-3-2 (11 perc, ⚠ szoros)
```

---

## 8. Kapcsolódó modulok

| Modul | Kapcsolat típusa |
|-------|-----------------|
| M01 Misszió Tervező | **Közvetlen felhasználó** — új [Felosztás] gomb, blokk-overlay, blokk kiválasztás |
| M02 Grid Engine | **Kimenet fogyasztó** — block.missionPolygon → GridMissionGenerator bemenete |
| M03 Export / Import | **Perzisztencia** — ProjectManager kibővítve blokk-állapotokkal |
| M04 DJI Integráció | **Indirekt** — egy blokk feltöltése = szokásos M04 flow, csak több iterációban |
| M06 Dronterapia Sync | **Kompatibilitás** — .flightprogram.json séma kibővül, sync_pending logika változatlan |
| M08 Akku-statisztika | **Jövőbeli kapcsolat** — pre-flight ellenőrzés (akku elég-e az aktuális blokkhoz), per-blokk akku-allokáció becslés |
