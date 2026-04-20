# L2 – Döntési Logika – Kamera Konfigurátor

**Modul:** M05  
**Szint:** L2 – Döntési Logika  
**Verzió:** v1.0.0  
**Létrehozva:** 2026-04-20  
**Utolsó módosítás:** 2026-04-20  
**Státusz:** 🔲 Tervezve

---

## 1. Hisztogram kiértékelés

### 1.1 Adatstruktúra

A DJI MSDK v4 a hisztogramot `int[256]` tömbként adja vissza — minden elem
az adott fényességszintű (0=fekete, 255=fehér) pixelek száma.

```java
// MSDK callback
camera.setHistogramDisplayEnabled(true, error -> {});
camera.setHistogramCallback(histogramData -> {
    // histogramData: int[] mérete 256
    runOnUiThread(() -> updateHistogramView(histogramData));
});
```

### 1.2 EV-eltérés számítása

Az EV-eltérést a hisztogram súlypontjából számítjuk:

```
totalPixels = sum(histogramData[0..255])
weightedSum = sum(i * histogramData[i])  ahol i = 0..255

mean = weightedSum / totalPixels         → 0..255 értékkészlet

evOffset (EV egységben):
  mean < 80  → alulexponált: evOffset = (mean - 128) / 42.7  → negatív (−1.1 .. −3.0)
  80..175    → helyes zóna: evOffset = (mean - 128) / 42.7   → közel 0
  mean > 175 → túlexponált: evOffset = (mean - 128) / 42.7  → pozitív (+1.1 .. +3.0)

Skálázás: 42.7 = 128/3 → ±3 EV a teljes tartomány
```

Az EV csúszka értéke mindig a `evOffset` aktuális értékét tükrözi.
Ha az operátor kézzel húzza a csúszkát, a `targetEvOffset` változik és
az `applyEvOffset()` fut le.

---

## 2. EV csúszka → paraméter módosítás döntési fa

### 2.1 EV csökkentés (túlexponált, csúszka balra)

```
applyEvStep(direction = DECREASE):
  │
  ├─ Lépés 1: Zársebesség növelhető?
  │     currentSs < SS_MAX (1/2000s)?
  │     │ IGEN → ss = nextSsStep(currentSs, UP)
  │     │        setCameraShutterSpeed(ss) → return
  │     │ NEM → tovább
  │
  ├─ Lépés 2: ISO csökkenthető?
  │     currentIso > ISO_MIN (100)?
  │     │ IGEN → iso = prevIsoStep(currentIso)   (800→400→200→100)
  │     │        setCameraISO(iso) → return
  │     │ NEM → tovább
  │
  ├─ Lépés 3: Rekesz szűkíthető?
  │     currentAperture < AP_MAX (f/11)?
  │     │ IGEN → ap = nextApertureStep(currentAperture, CLOSE)
  │     │        setCameraAperture(ap) → return
  │     │ NEM → tovább
  │
  └─ Minden határon: Toast "Fényviszonyok túl erősek — árnyékba kell menni"
```

### 2.2 EV növelés (alulexponált, csúszka jobbra)

```
applyEvStep(direction = INCREASE):
  │
  ├─ Lépés 1: Zársebesség csökkenthető?
  │     currentSs > SS_MIN (1/400s)?
  │     │ IGEN → ss = nextSsStep(currentSs, DOWN)
  │     │        setCameraShutterSpeed(ss) → return
  │     │ NEM → tovább
  │
  ├─ Lépés 2: ISO növelhető?
  │     currentIso < ISO_MAX_SURVEY (800)?
  │     │ IGEN → iso = nextIsoStep(currentIso)   (100→200→400→800)
  │     │        setCameraISO(iso) → return
  │     │ NEM → tovább
  │
  ├─ Lépés 3: Rekesz nyitható?
  │     currentAperture > AP_MIN (f/5.6)?
  │     │ IGEN → ap = nextApertureStep(currentAperture, OPEN)
  │     │        setCameraAperture(ap) → return
  │     │ NEM → tovább
  │
  └─ Minden határon: Toast "Fényviszonyok túl gyengék — várj erősebb fényre"
```

---

## 3. Paraméter lépéssorozatok (P4P v1)

### Zársebesség lépések (SS_STEPS)

```java
// MSDK CameraShutterSpeed enum értékek — P4P v1 mechanikus zár
static final CameraShutterSpeed[] SS_STEPS = {
    SHUTTER_SPEED_1_400,   // 1/400s  — survey minimum (elmosódás határ)
    SHUTTER_SPEED_1_500,
    SHUTTER_SPEED_1_640,
    SHUTTER_SPEED_1_800,   // default
    SHUTTER_SPEED_1_1000,
    SHUTTER_SPEED_1_1250,
    SHUTTER_SPEED_1_1600,
    SHUTTER_SPEED_1_2000   // maximum (fényes nap)
};
```

### ISO lépések (ISO_STEPS)

```java
static final CameraISO[] ISO_STEPS = {
    ISO_100,   // default, minimum survey
    ISO_200,
    ISO_400,
    ISO_800    // survey maximum (ISO 800 felett szemcse agro képen zavaró)
};
// ISO_1600 és ISO_3200: NEM engedélyezett automatikus EV-módban
```

### Rekesz lépések (AP_STEPS)

```java
static final CameraAperture[] AP_STEPS = {
    APERTURE_F_5_DOT_6,   // legnyitottabb (EV növelés utolsó lépése)
    APERTURE_F_8,          // default (élesség + diffrakció optimum)
    APERTURE_F_11          // legszűkebb (EV csökkentés utolsó lépése)
};
// f/2.8, f/4: NEM szerepel — mélységélesség survey-nél nem megfelelő
```

---

## 4. Fehéregyensúly döntés

A WB csúszka 4000K–7000K között mozog. Nincs automata lépés — az operátor
manuálisan állítja az égbolt állapota alapján.

```
Ajánlott értékek (informatív tooltip a csúszkán):
  4000–4500K → izzólámpa / napkelte (nem survey)
  5000–5600K → napsütéses délelőtt      ← agro tipikus
  5600–6200K → borult ég
  6500–7000K → erősen felhős / árnyék

WB forrás a MSDK-ban:
  camera.setWhiteBalance(new WhiteBalance(SettingsDefinitions.WhiteBalancePreset.CUSTOM, colorTemperature))
  colorTemperature: 20–100 egység → Kelvin = colorTemperature * 100
  → 4000K = 40, 5600K = 56, 7000K = 70
```

---

## 5. Fókuszdöntés

```
[AUTO → Rögzít] megnyomva:
  │
  ▼
camera.setFocusMode(SettingsDefinitions.FocusMode.AUTO)
  │
  ▼ (AF lefut a kamera képén)
camera.setFocusTarget(PointF(0.5f, 0.5f))  ← képernyő közepére fókuszál
  │
  ▼ (500ms várakozás az AF végéhez)
camera.setFocusMode(SettingsDefinitions.FocusMode.MANUAL)  ← rögzít
  │
  ▼
btnFocusAuto szövege: "Fókusz: rögzítve ✓"
focusLocked = true

[Inf.] megnyomva:
  │
  ▼
camera.setFocusMode(SettingsDefinitions.FocusMode.MANUAL)
camera.setFocusRingValue(maxFocusRingValue)  ← végtelenre állít
  │
  ▼
btnFocusInf szövege: "∞ rögzítve ✓"
focusLocked = true

Misszió START előtt focusLocked == false?
  → Toast figyelmeztetés: "Fókusz nincs rögzítve — a kamera repülés közben
     fókuszálhat, ami elmosódott képeket okozhat."
  → Nem tiltja a startot, csak figyelmezteti
```

---

## 6. [Alkalmaz] gomb döntési logikája

```
[Alkalmaz ✓] megnyomva:
  │
  ▼
DJI drón csatlakoztatva? (DJIHelper.isAircraftConnected())
  │ NEM → Toast: "Drón nincs csatlakoztatva — beállítások csak menthetők"
  │       → profilmentés felkínálása (igen/nem)
  │       → leáll
  │ IGEN
  ▼
camera.setExposureMode(SettingsDefinitions.ExposureMode.MANUAL)
  │
  ├─ error → Toast("Expozíciós mód váltás sikertelen: " + error)
  │           → leáll
  │
  └─ success →
       camera.setISO(selectedISO)
       camera.setAperture(selectedAperture)
       camera.setShutterSpeed(selectedShutterSpeed)
       camera.setWhiteBalance(selectedWB)
       → Ha focusLocked == false: fókusz nem írja felül
       │
       ▼
       Minden parancs sikerült?
         │ IGEN → Toast "Kamera beállítva ✓"
         │        settingsApplied = true
         │ NEM  → Toast "Részleges hiba: [paraméternév] nem sikerült"
```

---

## 7. Profilmentés döntés

```
[Ment...] gomb megnyomva:
  │
  ▼
EditText dialog: "Profil neve" (pl. "napsütés 100m")
  │
  ├─ Üres név → Toast "A profilnévnek legalább 3 karakter kell"
  │
  └─ Valid név →
       Már létezik ilyen nevű profil?
         │ IGEN → AlertDialog: "Felülírod a meglévő 'napsütés 100m' profilt?"
         │         [Mégse] | [Felülír]
         │ NEM → direkt mentés
         ▼
       SharedPreferences kulcs: "camera_profile_" + névHash
       Érték: JSON { iso, aperture, shutterSpeed, whiteBalance, focusMode }
       profileList frissítés → Betölt gomb dropdown frissül
       Toast: "Profil elmentve ✓"
```

---

## 8. Hisztogram megjelenítés döntés

```
updateHistogramView(int[] data):
  │
  ▼
256 bin → 64 csoportba összevon (binSize = 4)
  aggregated[i] = sum(data[i*4 .. i*4+3])
  │
  ▼
maxValue = max(aggregated[])
  │
  ▼
Minden oszlop magassága: (aggregated[i] / maxValue) * VIEW_HEIGHT
  → View.invalidate() → onDraw() újrarajzolás
  │
  ▼
Túlexponált régió (utolsó 8 bin, 225–255 értékek):
  aggregated[56..63] pixel aránya > 2%?
  │ IGEN → ezeket piros oszloppal jelöli ("clipping")
  │ NEM  → narancssárga

Alulexponált régió (első 8 bin, 0–31 értékek):
  aggregated[0..7] pixel aránya > 2%?
  │ IGEN → ezeket sötétkék oszloppal jelöli ("crushing")
  │ NEM  → narancs
```
