# L2 – Döntési Logika – Sűrű Rács (Alacsony Magasságú Mozaikoláshoz)

**Modul:** M10
**Szint:** L2 – Döntési Logika
**Verzió:** v1.0.0 — ✅ Implementálva (2026-07-03)
**Létrehozva:** 2026-07-03
**Utolsó módosítás:** 2026-07-03
**Státusz:** ✅ Implementálva, eszközön még nem tesztelve (ld. M10_L1 §7 — a felhasználó a megvalósítás mellett döntött)

---

## 1. Mód-javaslat döntése (M01 UI)

**Döntés:** a rendszer **automatikusan felajánlja** az M10 módot, ha a
beállított paraméterek a M02_L1 §8.3-ban dokumentált "súlyos" zónába esnek —
nem várja meg, hogy a felhasználó terepen fedezze fel a problémát.

```
altitudeM × (1 - frontlapPercent/100) < 2.0 × 2.0   // a CURVED mód
                                                      // effektív padlója
                                                      // (kamera 2mp × UI min
                                                      // sebesség 2 m/s)
  │
  ├─ IGEN (a "súlyos" zóna, ld. M02_L1 §8.3 küszöbtáblázat) →
  │     UI figyelmeztetés + javaslat:
  │     "⚠ Ezen a magasságon/átfedésen a normál mód nem éri el a beállított
  │      fedettséget (ld. M02_L1 §8). Váltás Sűrű rács (NORMAL) módra?"
  │     [Váltás] / [Marad CURVED, tudom a korlátot]
  │
  └─ NEM → nincs javaslat, CURVED mód változatlanul ajánlott (hatékonyabb)
```

**Miért csak javaslat, nem kényszerített váltás:** a felhasználónak lehet
olyan célja (pl. betanító adatkészlet egyedi képekből, ld. a 2026-07-03-i
beszélgetés — nem szükséges hozzá teljes mozaik), ahol a CURVED mód
"hiányos" átfedése **nem hiba**, csak nem alkalmas mozaikolásra. A döntést a
felhasználóra kell bízni, nem az app-ra.

---

## 2. Terület-méret alapú figyelmeztetés (kötelező, nem csak informatív)

**Döntés:** ellentétben az M07 blokk-felosztás "csak tájékoztató" akku-
becslésével (M07_L1 §7), az M10 módnál a becsült repülési idő/akkuszám
**blokkoló megerősítést** igényel, mert itt a szorzó (3–5×) sokkal nagyobb
meglepetést okozhat, mint egy szokásos misszióbecslés:

```
estimatedMinutes (M10) > 20 perc VAGY estimatedBatteryCount > 3?
  │ IGEN → kötelező megerősítő dialog a generálás UTÁN, feltöltés ELŐTT:
  │        "Ez a misszió kb. {N} percet és {M} akkumulátort igényel —
  │         jelentősen több, mint egy hasonló méretű CURVED misszió
  │         ({X} perc / {Y} akku lenne). Folytatod?"
  │        [Folytatás] / [Mégse — próbálj magasabb repülést]
  │ NEM → normál generálás, nincs extra megerősítés
```

---

## 3. Sűrű waypoint-generálás — geometriai döntés

**Döntés:** az M02 `GridMissionGenerator.runGridPass()` scanline-metszéspont
logikáját (sáv belépési/kilépési X koordináták) újra kell használni
VÁLTOZATLANUL — csak az utolsó lépés változik:

```
M02 (jelenlegi, CURVED):
  minden [xEnter, xExit] substripre → 2 waypoint (csak a két végpont)

M10 (tervezett, sűrű):
  minden [xEnter, xExit] substripre →
    N = ceil(|xExit - xEnter| / photoDistanceM) + 1 waypoint,
    photoDistanceM egyenletes lépésközzel xEnter és xExit között
```

**Miért nem önálló, teljesen új geometriai algoritmus:** a sáv-illesztés,
elforgatás, offset-kezelés és akadály-szűrés (M02_L2 §1–§8) mind
változatlanul alkalmazható — csak a substrip-waypoint-generálás lépése
(`GridMissionGenerator.java` `runGridPass()` metódusának vége) tér el.

**✅ Eldőlt (2026-07-03):** a `GridMissionGenerator`-be került egy
`config.denseGridMode: boolean` paraméterezés, NEM önálló
`DenseGridMissionGenerator` osztály — a részletes indoklást ld. M10_L3.

---

## 4. Waypoint-akció döntés (STAY hossz)

**Döntés:** minden sűrű rács-waypointon rövid `STAY` akció szükséges — **nem
azért, mert hosszú hoverre van szükség** (mint a mintavételi misszió
tőszámláláshoz optimalizált, hosszabb hoverénél), hanem mert a
`triggerSamplePhoto()` saját 500 ms stabilizációs késleltetést tartalmaz
(M04 §18) — a `STAY` időtartamának legalább ennyinek kell lennie, hogy a
drón ne induljon el a következő pont felé, mielőtt a fotó elkészül:

```
staySecondsPerWaypoint = max(1.0, triggerStabilizeDelayMs/1000 + becsültExpozíciósIdő)
  → alapértelmezés: 1.0–1.5 mp (nem a mintavételi misszió 2.5 mp-es
    alapértéke — itt nincs szükség hosszú hoverre, csak a trigger
    lefutásához elegendő időre)
```

---

## 5. Szegmentálás és akkucsere — újrahasznosított logika

**Döntés:** a meglévő M02_L2 §6 szegmentálási döntés (99 wp/szegmens,
resume/akkucsere flow) **változtatás nélkül** alkalmazható — a sűrű rács
csak TÖBB szegmenst eredményez ugyanarra a területre, a mechanizmus maga
azonos. Nincs szükség új resume-logikára.

---

## 6. Kapcsolódó modulok

| Modul | Kapcsolat típusa |
|-------|-----------------|
| M02 Grid Engine | Geometriai alaplogika újrahasznosítva (§3) |
| M02_L1 §8.3 | A mód-javaslat küszöbértékei innen származnak (§1) |
| M04 (`CameraConfigurator.triggerSamplePhoto`, `MissionUploader`) | A fotó-trigger és NORMAL-módú feltöltés újrahasznosítva változtatás nélkül |
| M07 Blokk-felosztás | Az akku-becslés "kötelező megerősítés" mintája (§2) analóg, de szigorúbb, mint az M07 "csak tájékoztató" megközelítése |
