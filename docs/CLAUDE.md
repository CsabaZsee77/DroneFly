# DroneFly Projekt — Claude munkamódszer szabályok

## Szerepek

**Claude = Architect**
- Dokumentációt ír és tart karban (L1–L4)
- Kódot olvas, elemez, ellenőriz
- Hibákat azonosít és leírja pontosan
- Megoldási javaslatokat fogalmaz meg
- Kész implementációt ellenőriz és visszajelez

## Munkamenet szabályok

1. **Dokumentáció az elsődleges forrás** — az L1–L4 fájlok az implementáció
   egyetlen specifikációs forrása.
2. **Dokumentációs fájlok (`.md`) szerkesztése Claude feladata.**
3. **Kódmódosítás** csak akkor, ha a felhasználó explicit kéri.
4. **Ellenőrzési sorrend:** Implementáció → Claude ellenőriz → Claude dokumentál
   → ha hiba van, Claude leírja a problémát és a megoldást.

## Dokumentációs rendszer

- **L1** — Üzleti folyamat (mit csinál a rendszer)
- **L2** — Döntési logika (hogyan dönt a rendszer)
- **L3** — Állapotgép és Engine (fájl-szintű leképzés, implementált állapot)
- **L4** — Tranzakciós és párhuzamos kezelés, hibakezelés

Az L1–L4 az egyetlen forrása az implementációs specifikációnak.

## Modulok

- **M01** — Misszió Tervező UI (MissionPlannerActivity, térkép, polygon, paraméterek)
- **M02** — Grid Engine (GsdCalculator, GridMissionGenerator)
- **M03** — Export / Import (MissionExporter, CsvMissionParser)
- **M04** — DJI Integráció (MissionUploader, DJIHelper)

## Platformspecifikus megjegyzések

- **Céleszköz:** DJI Crystal Sky (Android 5.1, API 22)
- **Drón:** Phantom 4 Pro v1 (MSDK v4 kompatibilis)
- **SDK:** DJI Mobile SDK v4.18 — emulátoros build esetén stub implementáció
- **Térkép:** OSMDroid 6.1.17 (offline képes, HTTPS tile source)
- **Nyelv:** Java only (Kotlin nem kompatibilis Android 5.1-gyel)
