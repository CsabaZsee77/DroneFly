# L4 – Tranzakciós és Párhuzamos Kezelés – Edge AI Tőszámlálás

**Modul:** M09
**Szint:** L4 – Tranzakciós és Párhuzamos Kezelés
**Verzió:** v0.1.0 (terv)
**Létrehozva:** 2026-07-02
**Utolsó módosítás:** 2026-07-02
**Státusz:** 🔲 Tervezve

---

## 1. Szálkezelés

- **UI thread:** csak a felhasználói interakció (session/modell választás,
  gombnyomás) és a progress-frissítés megjelenítése.
- **1 db háttér worker thread** (`ExecutorService`, `newSingleThreadExecutor()`):
  a teljes `runOnSession()` ciklus ezen fut — fájl beolvasás, bitmap
  dekódolás, TFLite inferencia, NMS.
- **TFLite `Interpreter.setNumThreads()`:** a worker szálon belül az
  interpreter maga több CPU magot használhat egyetlen inferencián belül —
  ez különbözik a "több kép párhuzamosan" mintától (ld. M09_L2 §4 döntés
  indoklása).
- Minden progress/eredmény callback `runOnUiThread()` / `Handler.post()`
  hívással jut vissza a UI-ra — soha nem közvetlenül a worker szálról
  módosítunk View-t.

---

## 2. Megszakítás (cancellation)

- `AtomicBoolean cancelFlag` — a `[Mégse]` gomb `true`-ra állítja.
- A ciklus **csak képek között** ellenőrzi a flaget (nem szakítja meg
  a folyamatban lévő `interpreter.run()` hívást — a TFLite Java API nem
  ad biztonságos megszakítási pontot futás közben, és egy inferencia
  legfeljebb pár másodperc, elfogadható várakozás).
- Megszakítás után: a már kész `PointResult`-ok megmaradnak, a
  `SamplingResultCalculator` a rendelkezésre álló (N < teljes) pontokból
  számol — a UI jelzi: **"Részleges eredmény (18/34 pont feldolgozva)"**,
  és a CV%/CI95 számítás ugyanúgy fut, csak kisebb mintaszámmal (ami
  szélesebb konfidencia-intervallumot eredményez — ez helyesen tükrözi a
  csökkent megbízhatóságot, nem kell külön kezelni).

---

## 3. Memóriakezelés

- **Egyszerre 1 bitmap** van dekódolva a memóriában — minden kép
  feldolgozása után explicit `bitmap.recycle()`.
- **Bitmap előfeldolgozás:** ha a mintaponti fotó natív felbontása (pl.
  P4P v1: 5472×3648) nagyságrendekkel nagyobb, mint a modell bemenete
  (320×320 vagy 640×640), a dekódolásnál `BitmapFactory.Options.inSampleSize`
  használandó a felesleges nagy felbontású dekódolás elkerülésére —
  **nem** a teljes felbontású bitmapot dekódoljuk, majd utólag kicsinyítjük.
- **Egyetlen `Interpreter` példány** a teljes session feldolgozása alatt
  (nem minden képhez újat létrehozni — a modell betöltése/parseolása
  maga is időt/memóriát igényel).
- **`ByteBuffer` újrafelhasználás:** a TFLite bemeneti buffer egyszer
  allokálva, minden képnél felülírva (nem újraallokálva).

---

## 4. Részleges hiba kezelése (nem blokkoló)

| Hiba | Kezelés |
|------|---------|
| Egy `point_XXX.jpg` nem dekódolható (sérült fájl) | `onImageError()` → a pont `warning` mezővel kerül a `results.json`-ba, `count = null`, kimarad az átlagszámításból (nem 0-ként számoljuk — a hiányzó adat nem "nulla tő") |
| Az interpreter `run()` kivételt dob egy adott képen | Ugyanúgy kezelve, mint a dekódolási hiba — a ciklus folytatódik a következő képpel |
| Minden kép hibás (pl. rossz modell-formátum már az első képnél kiderül) | Az első 2-3 hiba után a ciklus **leáll** és `ERROR` állapotba vált (nem futtatja végig hiába az összes képet) — küszöb: ha az első 3 kép mindegyike hibázik, feltételezzük, hogy a modell/formátum hibás, nem egyedi képhiba |

---

## 5. Idempotencia és újrafuttathatóság

- A `runOnSession()` **nem módosítja** a `point_*.jpg` fájlokat vagy a
  `session.json`-t — csak olvas, majd a `results.json`-t írja/felülírja.
- Ez lehetővé teszi, hogy ugyanazt a session-t **többször, más modellel**
  is le lehessen futtatni (pl. összehasonlítás céljából) — minden futás
  felülírja az előző `results.json`-t (nincs verziózás v1-ben; ha ez
  igény lesz, a fájlnév kiegészíthető `results_{modelName}.json` mintára).

---

## 6. Konzisztencia a session adatokkal

- A `SamplingResultCalculator` **nem** fogadja el a session.json-t, ha
  a `sample_count` mező (M04-ből) nem egyezik a ténylegesen feldolgozott
  `point_*.jpg` fájlok számával — ez jelezné, hogy a médialetöltés
  hiányos volt (ld. M04 §16 `onSessionWarning` eset), és a torzított
  extrapoláció helyett inkább figyelmeztetést ad: *"A session csak
  {N}/{expected} fotót tartalmaz — az eredmény ennek megfelelően
  torzulhat."*
