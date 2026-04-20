# L4 – Tranzakciós és Párhuzamos Kezelés – Kamera Konfigurátor

**Modul:** M05  
**Szint:** L4 – Tranzakciós és Párhuzamos Kezelés  
**Verzió:** v1.1.0  
**Létrehozva:** 2026-04-20  
**Utolsó módosítás:** 2026-04-20  
**Státusz:** ✅ Megvalósítva

---

## 1. Hisztogram polling szálkezelés

### Miért nem DJI SDK callback?

A DJI MSDK v4 a P4P v1 esetén `setHistogramEnabled(bool, CompletionCallback)` metódust
biztosít, de **nincs `setHistogramCallback`** — az SDK csak a saját widgetjéhez jeleníti
meg a hisztogramot, alkalmazásnak nem adja ki az adatokat.

### Frame-alapú megközelítés

A hisztogramot a dekódolt videó frame-ből számítjuk, a `TextureView.getBitmap()` segítségével.

```
HandlerThread("HistPoll") — dedikált háttér szál
  │
  └─ Handler(histThread.getLooper())
       │
       └─ histRunnable (800ms-onként)
            │
            ├─ TextureView.getBitmap()        ← háttér szálon (nem UI thread!)
            ├─ createScaledBitmap(128×72)     ← GPU readback kis méretben
            ├─ getPixels() → int[9216]        ← egyetlen memcpy, nem pixel-loop
            ├─ BT.601 luminance számítás      ← pure CPU, ~0.5ms
            └─ histogramView.update(luma256)  ← post()-on keresztül UI thread
```

**Kritikus döntés: HandlerThread, nem `Handler(getMainLooper()`)**

Az előző megközelítés (`Handler(getMainLooper())`) a `getBitmap()` GPU readback-et
az UI thread-en futtatta. Zoom gesture közben ez ~10–50ms blokkolást okozott,
ami Crystal Sky-on (Android 5.1) térképfagyást eredményezett.

A `HandlerThread` a teljes pipeline-t kiveszi az UI thread-ről. Az UI thread
csak a `HistogramView.invalidate()` rajzolást végzi (paint + 64 drawRect = <1ms).

### Videó feed auto-start

Az EXP panel megnyitásakor, ha a kamera feed nem fut:

```java
// HELYES: alpha=0 + VISIBLE — a TextureView SurfaceTexture csak VISIBLE állapotban
// jön létre (hardware compositor nem renderi az INVISIBLE view-t)
cameraWindow.setAlpha(0f);
cameraWindow.setVisibility(View.VISIBLE);
videoWidget.start();
histogramStartedVideo = true;

// HELYTELEN lett volna: setVisibility(View.INVISIBLE)
// → SurfaceTexture nem jön létre → getBitmap() null-t ad
```

Bezáráskor: ha `histogramStartedVideo`, `videoWidget.stop()` + `setVisibility(GONE)` + `setAlpha(1f)`.

### Életciklus

| Esemény | Hatás |
|---------|-------|
| EXP panel nyílik | `histThread` létrehozás, `histHandler.postDelayed(300ms)` |
| EXP panel csukódik | `histHandler.removeCallbacks()`, `histThread.quitSafely()` |
| `onPause()` | `stopHistogramPolling()` — Crystal Sky képernyő kikapcs. |

---

## 2. MSDK Camera API hívások sorrendje

A DJI MSDK Camera API hívások **szekvenciálisan** hajtandók végre.
`CameraConfigurator.applySettings()` callback lánca:

```
setMode(SHOOT_PHOTO)
  → setExposureMode(MANUAL/PROGRAM/SHUTTER_PRIORITY)
    → setAperture(f/8)
      → setISO(100/200/400/800 vagy skip ha AUTO)
        → setShutterSpeed(1/800 vagy skip ha AUTO)
          → setWhiteBalance(SUNNY/CLOUDY/CUSTOM_kelvin)
            → setPhotoFileFormat(RAW_AND_JPEG)
              → setShootPhotoMode(SINGLE)
                → callback.onComplete(true, "Alkalmazva")
```

Minden lépés hibánál: `Log.w` + folytatás (nem blokkol) — a survey kritikus
paraméterei (SS, ISO) általában sikeresek; AP hiba P4P v1-en előfordulhat
ha a kamera nem SHOOT_PHOTO módban volt.

---

## 3. Auto Lock — reflection-alapú értékolvasás

A DJI MSDK auto expozíció aktuális értékeit nem adja ki közvetlen getterrel.
`lockAutoToManual()` reflection-nal keresi a `setCurrentExposureValuesCallback`
(vagy hasonló nevű) metódust:

```java
// 1. Callback setter keresés futásidőben
for (Method m : camera.getClass().getMethods()) {
    if (m.getName().contains("xposure") && m.getName().contains("allback")
            && m.getParameterTypes().length == 1) { ... }
}

// 2. Proxy interfész a callback fogadásához
Object proxy = Proxy.newProxyInstance(iface.getClassLoader(), new Class[]{iface},
    (p, method, args) -> {
        // getISO() / getShutterSpeed() / getAperture() → mapDjiXxx() → CameraSettings
        // Callback törlése: finalSetter.invoke(camera, null)
        callback.onReadback(true, settings, "OK");
        return null;
    });
setter.invoke(camera, proxy);
```

Ha a metódus nem található (SDK verzió különbség):
→ `onReadback(false, null, "Értékek nem olvashatók vissza")` — manuális beállítás szükséges.

---

## 4. Azonnali apply — versenyhelyzet kezelés

A manuális mode minden változásnál (`applyIfManual()`) azonnal alkalmaz.
Gyors egymás utáni nyomásoknál (pl. ISO 100→200→400) az MSDK callback láncok
párhuzamosan futhatnak:

**Kezelés:** az MSDK belső sorba állítja a hívásokat — a kamera mindig az
utolsó teljes beállítás csomagot fogja alkalmazni. Nincs explicit zárolás,
mert az MSDK garantálja a szekvenciális végrehajtást.

Vizuális állapot: `tvCameraStatus` mutatja az utolsó `applyCurrentCameraSettings()`
eredményét ("✓ Alkalmazva" vagy "✗ hiba").

---

## 5. Bitmap memóriakezelés

```
TextureView.getBitmap()         →  ~720p bitmap allokáció (~3MB ARGB)
createScaledBitmap(128×72)      →  ~36KB allokáció
original bmp.recycle()          →  azonnal felszabadítva
getPixels() → int[9216]         →  ~36KB stack-közeli allokáció
small.recycle()                 →  felszabadítva a számítás után
```

800ms-onként: ~3MB allokáció + azonnal felszabadítva. Crystal Sky (3GB RAM)
esetén ez elfogadható; a GC ritkán aktiválódik ekkora allokáció esetén, és
ha igen, a háttér szálat érinti, nem az UI thread-et.

---

## 6. Crystal Sky speciális korlátok

| Korlát | Megoldás |
|--------|----------|
| Android 5.1 (API 22) — nincs `Method.isDefault()` | Eltávolítva, `getDeclaringClass() != Object.class` |
| `TextureView` INVISIBLE → nincs SurfaceTexture | `alpha=0` + `VISIBLE` helyett |
| DJI histogram callback hiányzik az SDK-ból | Frame-alapú saját számítás |
| UI thread blokkolás zoom közben | HandlerThread — teljes pipeline háttérben |
| MSDK reflection: abstract class Proxy nem működik | `isInterface()` check + skip |
