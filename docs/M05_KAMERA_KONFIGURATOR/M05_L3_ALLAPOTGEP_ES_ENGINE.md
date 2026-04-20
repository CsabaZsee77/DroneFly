# L3 – Állapotgép és Engine – Kamera Konfigurátor

**Modul:** M05  
**Szint:** L3 – Állapotgép és Engine  
**Verzió:** v1.1.0  
**Létrehozva:** 2026-04-20  
**Utolsó módosítás:** 2026-04-20  
**Státusz:** ✅ Megvalósítva

---

## Forrásfájlok (implementált)

| Fájl | Szerepkör | Státusz |
|------|-----------|---------|
| `camera/HistogramView.java` | Custom View — 64 oszlopos luminance hisztogram | ✅ Kész |
| `dji/CameraConfigurator.java` | MSDK Camera API hívások, Auto Lock, EV mód | ✅ Kész |
| `model/CameraSettings.java` | Kamera beállítások adatmodell (ISO, AP, SS, WB, fájlformátum) | ✅ Kész |
| `res/layout/panel_camera_config.xml` | Kamera Panel UI layout (slide-up overlay) | ✅ Kész |

**Gazdamodul:**
- `MissionPlannerActivity.java` (M01) — a teljes panel logika itt van megvalósítva
  (`initCameraConfigPanel()`, `applyEvEngine()`, `startHistogramPolling()` stb.)

> Megjegyzés: a tervezett `CameraConfigPanel.java`, `CameraProfile.java`,
> `CameraProfileManager.java` önálló osztályok helyett az M01 gazdamodulba lett
> integrálva az implementáció során.

---

## Panel állapotgép (MissionPlannerActivity-ben)

```
              ┌──────────────┐
              │    CLOSED    │ ← alap, cameraPanelOverlay GONE
              └──────┬───────┘
                     │ [EXP] gomb
                     ▼
              ┌──────────────┐
              │     OPEN     │ ← overlay VISIBLE, HandlerThread fut
              │  AUTO mód    │   videó feed auto-start ha nem futott
              └──────┬───────┘
          ┌──────────┴──────────┐
          │                     │
    [MANUÁLIS] gomb       [🔒 Lock AUTO]
          │                     │
          ▼                     ▼
   ┌─────────────┐    ┌──────────────────┐
   │   MANUAL    │    │  LOCK_READBACK   │ ← reflection: current auto values
   │  cameraParams│   └────────┬─────────┘
   │  engedélyezve│            │ onReadback(success)
   └──────┬──────┘            ▼
          │             ┌─────────────┐
          │             │   MANUAL    │ ← UI értékei feltöltve
          └──────────────────┘
                 │
      ISO/AP/SS/WB változás
      (gomb, csúszka) → azonnali applyCurrentCameraSettings()
      EV ◄ ► → applyEvEngine() → azonnali apply
                 │
                 ▼
          ┌────────────┐
          │  APPLYING  │ ← MSDK async callback lánc
          └─────┬──────┘
        ┌────────┴────────┐
        │                 │
     success            error
        │                 │
   tvCameraStatus    Toast + status
   "✓ Alkalmazva"   "✗ hiba szöveg"
```

---

## MissionPlannerActivity — kamera panel mezők

```java
// Panel komponensek
private com.dronefly.app.camera.HistogramView histogramView;
private SeekBar     seekbarEv, seekbarShutter5, seekbarWb;
private Button      btnIso100, btnIso200, btnIso400, btnIso800;
private Button      btnApF56, btnApF8, btnApF11;
private Button      btnFocusAuto5, btnFocusInf5, btnApplyCamera5;
private TextView    tvShutterValue5, tvWbValue5, tvEvValue5, tvCameraStatus5;
private View        cameraPanelOverlay, cameraParamsGroup;
private Button      btnModeAuto, btnModeManual, btnAutoLock;

// Állapot
private int     isoIdx = 0;          // 0=ISO100 1=200 2=400 3=800
private int     apIdx  = 1;          // 0=f/5.6 1=f/8 2=f/11
private boolean focusLocked    = false;
private boolean cameraAutoMode = true;

// Hisztogram
private TextureView              cameraTextureView;  // megosztva DroneVideoWidget-tel
private android.os.HandlerThread histThread;         // "HistPoll" — dedikált háttér szál
private android.os.Handler       histHandler;
private Runnable                 histRunnable;       // 800ms-onként fut
private int[]                    lastHistData;       // 256 bin, computeHistCentroid()-hoz
private boolean                  histogramStartedVideo = false; // EXP auto-indított feedet?

// Zársebesség lépcsők (seekbarShutter5 index → felirat)
private static final String[] SS_LABELS =
    {"1/400","1/500","1/640","1/800","1/1000","1/1250","1/1600","1/2000"};
```

---

## CameraConfigurator — implementált metódusok

```java
public class CameraConfigurator {

    // Teljes beállítás csomag alkalmazása (callback lánc: mode→aperture→iso→shutter→wb→format→photoMode)
    public static void applySettings(CameraSettings settings, ConfigCallback callback);

    // AUTO módra visszakapcsolás (PROGRAM exposure mode)
    public static void setAutoExposureMode(ConfigCallback callback);

    // Auto Lock: jelenlegi auto értékek visszaolvasása reflectionnel, majd manual mód
    public static void lockAutoToManual(ReadbackCallback callback);

    // Intervallum fotózás (survey repüléshez)
    public static void startIntervalShooting(float photoDistM, float speedMs, ConfigCallback cb);
    public static void stopIntervalShooting();

    // Gimbal nadir (-90°)
    public static void setNadirPitch(ConfigCallback callback);

    // SD kártya ellenőrzés
    public static void checkSDCard(ConfigCallback callback);

    // Diagnosztika — kamera osztálynév + histogram metódusok (UI-ba írható)
    public static String getHistogramDiagnostic();

    // Histogram API (jelenleg nem használt — frame-alapú megközelítés váltotta fel)
    public static void startHistogram(HistogramListener listener);
    public static void stopHistogram();
}
```

---

## HistogramView — custom View

```java
public class HistogramView extends View {
    // 64 aggregált bin (256 → 64, minden bin = 4 raw érték összege)
    private int[] bins   = new int[64];
    private int   maxBin = 1;

    // Festékek (inicializáláskor létrehozva, onDraw-ban nincs allokáció)
    private Paint paintNormal;    // #FF6600 narancs
    private Paint paintClipping;  // #FF2200 piros — bins 56–63 ha magas
    private Paint paintCrushing;  // #2244AA kék  — bins 0–7 ha magas
    private Paint paintBg;        // #0D0D1A sötét háttér

    // Háttér szálon hívható — post()-on keresztül invalidate()
    public void update(int[] raw256);
}
```

---

## Survey EV Engine (applyEvEngine)

```
Bemenet: increase (boolean) — + vagy − gomb
Hisztogram centroid (lastHistData) → evError = (centroid − 128) / 42.7

Prioritás — NÖVELÉS (alulexponált):
  1. SS csökkentés: seekbarShutter5.progress > 3 (survey min: index 3 = 1/800s)?
       → progress−1, azonnali apply
  2. ISO növelés: isoIdx < 2 (max ISO 400)?
       → selectIso(isoIdx+1), azonnali apply
  3. Rekesz nyitás: apIdx > 0?
       → selectAperture(apIdx−1), azonnali apply
  4. Határon: Toast("Fényviszonyok túl gyengék survey-hez")

Prioritás — CSÖKKENTÉS (túlexponált):
  1. SS növelés: progress < 7 (max 1/2000s)?
  2. ISO csökkentés: isoIdx > 0?
  3. Rekesz szűkítés: apIdx < 2 (min f/11)?
  4. Határon: Toast("Fényviszonyok túl erősek")

Minden lépés után: applyCurrentCameraSettings() — azonnali drón alkalmazás
```

---

## Állapot-átmenetek eseménytáblázat (implementált)

| Esemény | Következmény |
|---------|-------------|
| [EXP] gomb | Panel slide-up, videó feed auto-start (alpha=0), HandlerThread indul |
| [✕] bezárás | Panel slide-down, HandlerThread leáll, videó feed leáll ha EXP indította |
| [AUTO] gomb | setAutoExposureMode(), cameraParamsGroup dimmed, btnAutoLock visible |
| [MANUÁLIS] gomb | cameraParamsGroup enabled, btnApplyCamera visible |
| [🔒 Lock AUTO] | lockAutoToManual() → applyReadbackToUI() → selectCameraMode(false) |
| ISO gomb | selectIso(idx), applyIfManual() |
| Rekesz gomb | selectAperture(idx), applyIfManual() |
| SS csúszka elengedve | applyIfManual() |
| WB csúszka elengedve | applyIfManual() |
| EV ◄ ► | applyEvEngine(±), applyCurrentCameraSettings() |
| [Alkalmaz ✓] | buildCameraSettingsForPanel() → CameraConfigurator.applySettings() |
| onPause() | stopHistogramPolling(), videoWidget.stop() |
