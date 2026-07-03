# L4 – Tranzakciós és Párhuzamos Kezelés – Sűrű Rács (Alacsony Magasságú Mozaikoláshoz)

**Modul:** M10
**Szint:** L4 – Tranzakciós és Párhuzamos Kezelés
**Verzió:** v1.0.0 — ✅ Implementálva (2026-07-03)
**Létrehozva:** 2026-07-03
**Utolsó módosítás:** 2026-07-03
**Státusz:** ✅ Implementálva, eszközön még nem tesztelve

---

## Újrahasznosított mechanizmusok (megerősítve, nem csak tervezve)

Az implementáció megerősítette, hogy az M10 mód tranzakciós/párhuzamossági
kérdései **valóban** a már meglévő mechanizmusokra épülnek, önálló tervezés
nélkül:

| Kérdés | Megvalósítás |
|--------|--------------|
| Fotó-trigger retry, szálkezelés | `CameraConfigurator.triggerSamplePhoto()` (M04 §18) — változtatás nélkül |
| Részleges hiba (egy waypointon sikertelen fotó) nem blokkoló | `triggerDenseGridPhoto()` — `onPhotoFailed()` csak számlál + Toast, a misszió folytatódik |
| Szegmentálás, akkucsere, resume | `GridMissionGenerator` meglévő `MAX_WAYPOINTS_PER_MISSION=99` szegmentálása és a `MissionPlannerActivity` resume-logikája (`resumeWaypointIndex`/`resumeSegmentIndex`) — változtatás nélkül működik jóval több szegmensre is |
| Idempotencia (ismételt generálás/feltöltés) | Nincs M10-specifikus különbség — a `denseGridMode` flag determinisztikusan ugyanazt a waypoint-listát generálja azonos bemenetre |

---

## Kötelező megerősítés nagy becsült idő/akkuszám esetén

`uploadCurrentSegment()` a **legelső** szegmens feltöltésekor (nem minden
akkucserénél újra) ellenőrzi:

```java
if (lastResult.isDenseGridMission && currentSegmentIndex == 0
        && (lastResult.estimatedMinutes > 20 || lastResult.estimatedBatteryCount > 3)) {
    // a feltöltési megerősítő dialog szövege kiegészül a becsléssel
}
```

Ez **nem blokkol** — csak a meglévő "Misszió feltöltése?" megerősítő dialog
szövegét egészíti ki, a felhasználó [Feltöltés]/[Mégse] választása
változatlan marad. A `currentSegmentIndex == 0` feltétel biztosítja, hogy
ez a hosszabb figyelmeztetés csak egyszer jelenjen meg, ne minden
akkucserénél ismétlődjön.

---

## Szálkezelés

Nincs M10-specifikus szálkezelési kérdés — a `triggerDenseGridPhoto()`
ugyanazt a mintát követi, mint `triggerSamplePointPhoto()` (M04_L4 §10):
a `CameraConfigurator.triggerSamplePhoto()` callback-jei `runOnUiThread()`-
del térnek vissza a main threadre, mielőtt a `denseGridPhotosConfirmed`/
`denseGridPhotosFailed` számlálókat módosítanák.

---

## Nyitott kérdés — még validálandó, nem implementációs hiányosság

A `DENSE_GRID_OVERHEAD_SEC = 3.0` (waypointonkénti fékezés+gyorsítás
becslés) és a teljes repülésidő-becslés pontossága **terepi méréssel**
validálandó — ez nem blokkolja a funkciót (a becslés csak a UI-n
megjelenő tájékoztató szám és a kötelező megerősítés küszöbét
befolyásolja), de az első valódi M10-es repülés után érdemes összevetni a
tényleges idővel és pontosítani a konstanst, ha jelentősen eltér.
