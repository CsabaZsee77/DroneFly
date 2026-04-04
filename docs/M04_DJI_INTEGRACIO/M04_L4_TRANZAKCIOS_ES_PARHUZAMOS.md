# L4 – Tranzakciós és Párhuzamos Kezelés – DJI Integráció

**Modul:** M04
**Szint:** L4 – Tranzakciós és Párhuzamos Kezelés
**Verzió:** v1.0.0
**Létrehozva:** 2026-04-02
**Státusz:** 🔧 Stub (emulátor) — dokumentálva a valódi eszköz implementációhoz

---

## 1. MSDK callback szálkezelés

Az MSDK v4 callback-ek a DJI SDK belső szálain érkeznek — NEM a main thread-en.
Ezért minden UI frissítést `runOnUiThread()` blokkba kell csomagolni az M01-ben:

```java
uploader.uploadMission(waypoints, config, new MissionUploader.UploadCallback() {
    @Override
    public void onSuccess() {
        runOnUiThread(() -> {
            setMissionRunning(true);
            Toast.makeText(MissionPlannerActivity.this,
                           "Misszió elindítva", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onError(String message) {
        runOnUiThread(() -> {
            Toast.makeText(MissionPlannerActivity.this,
                           "Hiba: " + message, Toast.LENGTH_LONG).show();
        });
    }
});
```

> **Stub buildnél:** A callback szinkron módon, a hívó szálán érkezik.
> Ez emulátorban nem okoz gondot, de a valódi MSDK-nál kötelező a
> `runOnUiThread()` wrapper.

---

## 2. Feltöltés tranzakciómodell

```
uploadMission() hívás:
  1. loadMission()  → szinkron (azonnal visszatér DJIError-ral)
  2. uploadMission() → aszinkron (callback-en érkezik az eredmény)

Timeout:
  Az MSDK v4 nem garantál timeout-ot.
  Ha a feltöltés nem fejeződik be (pl. WiFi/RC jel elvesztés):
    → A callback nem hívódik meg
    → UI "befagyott" állapotban marad

  Ajánlott megoldás (v1.1.0-ra):
    Handler timeout: 30 másodperc után callback.onError("Feltöltési timeout")

Sikertelen feltöltés után:
  → A loadMission() újrahívható (töröl és újratölt)
  → Nincs szükség activity újraindítására
```

---

## 3. Misszió atomiság

```
A waypoint misszió feltöltése atomikus a DJI eszközön:
  → Vagy teljesen sikerül (mind a N waypoint megérkezik a drónra)
  → Vagy hibaállapot keletkezik (részleges feltöltés nem lehetséges)

loadMission() → uploadMission() sorrend kötelező:
  → loadMission() = client-side validáció és misszió-objektum előkészítés
  → uploadMission() = tényleges RF átvitel a drón felé

Ha a drón lecsatlakozik feltöltés közben:
  → onError callback: "COMMON_NO_PRODUCT_CONNECTED"
  → Újracsatlakozás után: loadMission() + uploadMission() újra szükséges
```

---

## 4. Crystal Sky specifikus szálkezelés

```
Crystal Sky hardver: Qualcomm Snapdragon 821 (4 mag, 2.15 GHz)
→ Main thread overhead minimális
→ runOnUiThread() végrehajtása < 1 ms késéssel

DJI RC + Crystal Sky kapcsolat: OcuSync / Lightning kábel
→ SDK kommunikáció USB-n keresztül (nem WiFi)
→ Alacsony latencia, megbízható kapcsolat

Misszió feltöltési idő (P4P v1, ~99 waypoint):
  Becsült: 3–10 másodperc
  Függvényében: RC jelerősség, waypoint szám
```

---

## 5. Párhuzamossági korlátok

```
Egyszerre csak 1 misszió futhat a drónonn:
  → MSDK nem enged 2 egyidejű waypoint missziót
  → uploadMission() EXECUTING állapotban hibával tér vissza

Az app NEM próbál párhuzamosan 2 szegmenst feltölteni:
  → Szekvenciális sorrend: 1. szegmens kész → 2. szegmens upload
  → currentSegmentIndex = 0, 1, 2, ... manuálisan növelt

App lifecycle párhuzamosság:
  → setMissionRunning(true) megakadályozza a "Feltöltés" újrahívását
    (gomb disabled futó misszió közben)
```

---

## 6. SDK verzió kompatibilitás

```
MSDK v4.18 + Phantom 4 Pro v1:
  → Teljes kompatibilitás (P4P v1 = MSDK v4 referencia eszköz)
  → WaypointMissionOperator: minden metódus elérhető
  → WaypointActionType.START_TAKE_PHOTO: v4.6.1+ szükséges (v4.18-ban OK)
    (Korábbi névváltoztatás: SHOOT_PHOTO → START_TAKE_PHOTO)

Crystal Sky firmware:
  → Crystal Sky v1.4+ ajánlott MSDK v4.18-hoz
  → DJI Go 4 app előre telepítve — nem kell eltávolítani
  → DroneFly párhuzamosan futhat a DJI Go 4-gyel (de ne egyszerre!)
```
