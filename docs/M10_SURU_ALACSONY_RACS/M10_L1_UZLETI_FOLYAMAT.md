# L1 – Üzleti Folyamat – Sűrű Rács (Alacsony Magasságú Mozaikoláshoz)

**Modul:** M10
**Szint:** L1 – Üzleti Folyamat
**Verzió:** v1.0.0 — ✅ Implementálva (2026-07-03), eszközön még nem tesztelve
**Létrehozva:** 2026-07-03
**Utolsó módosítás:** 2026-07-03
**Státusz:** ✅ Implementálva (`./gradlew assembleDebug` sikeres) — a felhasználó
2026-07-03-án a §7 nyitott kérdései tisztázása után a megvalósítás mellett
döntött ("ez akkor lehet fontos, ha igazán nagyfelbontású ortomozaikot akarok
kísérleti jelleggel készíteni")

---

## 1. Modul célja

Az M10 modul egy **alternatív rács-misszió generálási és végrehajtási módot**
ad a meglévő, CURVED-módú, kamera-intervallum-alapú Grid Engine (M02) mellé —
kifejezetten **alacsony (kb. 20 m alatti) magasságú, kis területű, teljes
ortomozaikra szánt** felmérésekhez, ahol a M02_L1 §8.3-ban dokumentált
hardveres korlát miatt a jelenlegi mód **strukturálisan nem képes** elérni a
beállított átfedést.

**Miért nem elég a becslés-javítás (M02 §8) egyedül:** a 2026-07-03-i terepi
teszt (M02_L1 §8, M04_L1 §18) két, egymásra épülő **hardveres/SDK korlátot**
azonosított:

1. A P4P v1 kamera nem tud megbízhatóan 2 mp-nél gyakrabban fotózni
   (mechanikus zár + puffer-írás ideje).
2. Az MSDK waypoint-misszió `maxFlightSpeed`-je nem mehet 2 m/s alá
   (feltöltési hiba — ld. v1.9.10 fix).

E két korlát **együttesen** azt jelenti, hogy 10 m magasságon, 80% frontlap
mellett **semmilyen beállítás-kombinációval** nem érhető el a kért fotósűrűség
a jelenlegi CURVED+intervallum móddal (ld. a részletes levezetést M02_L1
§8.3-ban) — ez nem hangolási kérdés, hanem a mechanizmus alapvető korlátja.

**A megoldás iránya:** a fotó-triggert **le kell választani a repülési
sebességről és a kamera belső időzítőjéről** — pontosan úgy, ahogy a
mintavételi misszió (M01 §10 / M02 §7 / M04 §15, §18) már megoldja ezt egyetlen
ponton. Az M10 ezt a **teljes rácsra** általánosítja: minden fotópozíció saját
waypoint, NORMAL flightPathMode, és a már megbízható, visszaigazolt
`CameraConfigurator.triggerSamplePhoto()` hívás (M04 §18) minden ponton.

---

## 2. Bemenetek és kimenetek

```
[Bemenet]
  polygonPoints: List<GeoPoint>   (≥ 3 pont, mint az M02 Grid Engine-nél)
  config: MissionConfig
    → GSD/magasság, sidelap, frontlap, irány (mint M02-nél)
    → droneProfile
    → (nincs sebesség-paraméter — a repülési sebesség ebben a módban nem
       befolyásolja a fotósűrűséget, ld. 4. fejezet)

      │
      ▼
[Generálás] DenseGridMissionGenerator.generate(polygon, config)
  (tervezett osztály — újrahasznosítja a GsdCalculator/GridMissionGenerator
   geometriai logikáját: sávköz, sáv-metszéspontok, kígyózó sorrend)

      │
      ▼
[Kimenet]
  GeneratorResult (a meglévő típus bővítve, vagy önálló, hasonló struktúra):
    segments:          List<List<WaypointData>>  (minden fotópozíció saját wp)
    totalWaypoints:     int  (jóval nagyobb, mint az M02 CURVED módnál)
    estimatedPhotoCount: int  (= totalWaypoints, PONTOS — nincs kamera-időzítési
                                bizonytalanság, mert minden wp = 1 fotó)
    estimatedMinutes:   double (a NORMAL-módú megállás/gyorsítás miatt jóval
                                magasabb, mint a CURVED becslés — ld. 5. fejezet)
    estimatedBatteryCount: int (ÚJ mező — a megnövekedett repülési idő miatt
                                fontosabb előre jelezni, mint a CURVED módnál)
    isDenseGridMission: boolean (M04 ez alapján dönt uploadMission() vs
                                 uploadDenseGridMission() között)
```

---

## 3. Teljes folyamat — rajzolástól a repülésig

```
1. Terület rajzolása (M01 meglévő UI, változatlan)
      │
      ▼
2. Paraméterek beállítása (GSD, sidelap, frontlap — mint M02-nél)
      │  A UI a M02_L1 §8.3 küszöbtáblázata alapján AJÁNLJA ezt a módot,
      │  ha a beállított magasság/frontlap kombináció a "súlyos" zónába esik
      │  (ld. M10_L2 §1 — automatikus mód-javaslat döntési logika)
      ▼
3. Mód választás: "Sűrű rács (NORMAL)" kapcsoló/opció
      │  Info-szöveg: "Ezen a magasságon a normál (folyamatos) mód nem éri el
      │  a beállított átfedést — ez a mód lassabb, de garantáltan eléri."
      ▼
4. DenseGridMissionGenerator.generate()
      │  Minden sávon belül photoDistanceM-enként egy waypoint (nem csak a
      │  sáv két vége, mint az M02 CURVED módnál)
      │  Sávköz (stripSpacingM) VÁLTOZATLAN logika (M02-ből újrahasznosítva)
      ▼
5. Előzetes összegzés (kötelező, nem csak informatív — ld. M10_L2 §2)
      │  "⚠ 342 waypoint, becsült idő: 38 perc, ~3 akkumulátor szükséges
      │   (a szokásos CURVED módnál ez a terület kb. 8 perc / 1 akku lenne)"
      │  [Folytatás] / [Mégse — válassz magasabb repülést inkább]
      ▼
6. Feltöltés — MissionUploader.uploadDenseGridMission()
      │  NORMAL flightPathMode (mint a mintavételi misszió), STAY akció
      │  minden waypointon (rövid, csak stabilizációhoz — nem hosszú hover)
      ▼
7. Végrehajtás — onWaypointReached() minden ponton
      │  → CameraConfigurator.triggerSamplePhoto() (UGYANAZ a metódus, mint
      │    a mintavételi misszióban — nincs új trigger-mechanizmus)
      │  → siker/hiba visszaigazolás, retry (M04 §18 mintája szerint)
      ▼
8. Akkucsere / szegmensváltás (gyakoribb, mint CURVED módnál)
      │  A meglévő resume/szegmens-logika (M02_L2 §6) VÁLTOZATLANUL
      │  újrahasznosítható — a szegmentálás (99 wp/misszió) ugyanúgy működik
      ▼
9. Befejezés — a fotók garantáltan a beállított átfedéssel készülnek,
   ortomozaik-feldolgozásra alkalmas nyersanyag még alacsony magasságon is
```

---

## 4. Miért független a fotósűrűség a sebességtől ebben a módban

```
CURVED + intervallum-fotózás (M02, jelenlegi):
  fotóköz = repülési_sebesség × kamera_intervallum
            └─ mindkettő alsó korláttal rendelkezik → minimum fotóköz létezik

NORMAL + waypoint-trigger (M10, tervezett):
  fotóköz = a KÉT WAYPOINT KÖZTI TÁVOLSÁG (tisztán geometriai érték)
            └─ a fotó akkor készül, amikor a drón MEGÉRKEZIK a waypointra —
               nem attól függ, mennyi idő alatt ért oda
            └─ tetszőlegesen sűrű lehet, akár < 1 méteres fotóköz is,
               FÜGGETLENÜL a repülési sebességtől és a kamera időzítőjétől
```

Ez a lényegi strukturális különbség — a sebesség ebben a módban csak a
repülési IDŐT befolyásolja, a fotósűrűséget nem.

---

## 5. Ár — repülési idő és akkumulátor-szükséglet

**Becsült szorzó a CURVED módhoz képest:** minden waypointnál fékezés +
rövid stabilizáció + gyorsulás szükséges, szemben a CURVED mód folyamatos
repülésével. Durva becslés (terepi validáció szükséges):

```
denseFlightMinutes ≈ curvedFlightMinutes × (3 to 5)
                      (a szorzó a wp-sűrűségtől és a drón gyorsulási
                       karakterisztikájától függ — pontosítandó mérésekkel)
```

**Gyakorlati következmény:** egy P4P v1 akkumulátor ~16 perc hasznos
repülésre elég (ld. M07_L1 §1) — ez a mód egy 0,15–0,5 ha-os területnél is
több szegmenst/akkucserét igényelhet, miközben CURVED módban ugyanez a
terület egyetlen akkuval, percek alatt lerepülhető lenne (ha a magasság ezt
engedné).

**Ebből következő méretkorlát:** az M10 mód gyakorlatilag csak **kis
(< 1 ha) területekhez** praktikus — nagyobb táblánál a repülési idő és az
akkucsere-szám elfogadhatatlanná válik. Nagy táblánál a helyes válasz a
magasabb repülés (ahol a CURVED mód működik) vagy a mintavételes megközelítés
(M09-terv), nem az M10 mód erőltetése.

---

## 6. Kapcsolódó modulok

| Modul | Kapcsolat típusa |
|-------|-----------------|
| M02 Grid Engine | **Geometria-forrás** — sávköz/metszéspont/kígyózó-sorrend logika újrahasznosítva; `photoDistanceM` most WAYPOINT-TÁVOLSÁGKÉNT használva, nem kamera-időzítésként |
| M04 DJI Integráció (`CameraConfigurator.triggerSamplePhoto`) | **Újrahasznosított trigger** — a mintavételi misszióhoz (M04 §18) épített, megbízható, visszaigazolt fotó-trigger változtatás nélkül alkalmazható itt is |
| M04 DJI Integráció (`MissionUploader`) | **Új feltöltési útvonal** — `uploadDenseGridMission()`, NORMAL flightPathMode, hasonlóan a `uploadSamplingMission()`-höz |
| M01 Misszió Tervező | **UI-döntés** — mód-javaslat/választás a M02_L1 §8.3 küszöbtáblázat alapján |
| M07 Blokk-felosztás | **Kiegészítő, nagy területnél** — ha mégis nagy táblán kellene alacsony magasságú mozaik, az M07 blokkolás + M10 blokkonkénti alkalmazása kombinálható (jövőbeli, nem v1 hatókör) |
| M09 Edge AI Tőszámlálás | **Alternatíva** — ha a cél statisztikai tőszámlálás (nem teljes mozaik), az M09 mintavételes útja hatékonyabb, mint egy teljes M10 sűrű rács |

---

## 7. Döntés — 2026-07-03: implementáció megtörtént

**A felhasználó tisztázta a szükségletet:** a modul kifejezetten **kísérleti
jellegű, igazán nagyfelbontású ortomozaikok** készítéséhez fontos, amikor a
mintavételes (M09) statisztikai megközelítés nem elég — teljes, folytonos
mozaik kell, akár alacsony magasságon is. Ez elég gyakori, indokolt eset
ahhoz, hogy megérje a repülésidő-szorzó árát egy kisebb, célzott területen.

A korábbi nyitott kérdések közül:
- **Repülésidő-szorzó elfogadhatósága:** a felhasználó döntése alapján igen,
  kis (kísérleti célú) területen elfogadható.
- **Egyszerűbb köztes megoldás** (csak sorok közti sűrítés): **nem
  vizsgáltuk tovább** — a teljes sűrítés (minden fotópozíció saját waypoint)
  valósult meg, mert ez garantálja egyértelműen a beállított átfedést mindkét
  irányban, és a meglévő trigger-mechanizmus (M04 §18) változtatás nélkül
  újrahasznosítható rá.

**Implementáció:** ✅ kész, `./gradlew assembleDebug` sikeres (2026-07-03).
Részletek a ténylegesen megvalósult formában: M10_L2, M10_L3, M10_L4.
**Terepi teszt még hátravan** — Crystal Sky + Phantom 4 Pro v1 eszközön nincs
kipróbálva.
