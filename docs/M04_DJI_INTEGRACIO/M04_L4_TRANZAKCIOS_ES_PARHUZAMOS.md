# L4 – Tranzakciós és Párhuzamos Kezelés – DJI Integráció

**Modul:** M04
**Szint:** L4 – Tranzakciós és Párhuzamos Kezelés
**Verzió:** v1.6.0
**Létrehozva:** 2026-04-02
**Utolsó módosítás:** 2026-04-13
**Státusz:** ✅ Implementálva — misszió szálkezelés, volatile isFlying cache, kamera feed és tap-to-expose szálkezelés

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

## 5. isFlying volatile cache — szálbiztonsági magyarázat

```
DJI FlightController StateCallback:
  → DJI SDK belső szálon érkezik (NEM main thread)
  → droneIsFlying = state.isFlying()  [volatile write, SDK szálon]

MissionPlannerActivity.startMission():
  → Main thread-en fut (gomb click handler)
  → DJIHelper.isFlying()  [volatile read, main thread]

volatile garantálja:
  → A main thread mindig a legfrissebb értéket olvassa
  → Nincs szükség synchronized blokkra (egyszerű boolean, atomi read/write)
  → Ha a drón épp felszállás közben van és a callback még nem érkezett meg,
    az isFlying() false-t adhat vissza — ez biztonságos: a "földi" dialog
    jelenik meg, de a startMission() MSDK hívás levegőben is működik
```

**Következmény:** Az isFlying() nem garantál 100%-os pontosságot a felszállás
első pillanatában, de ez nem jelent funkcionális hibát — mindkét dialog-ág
ugyanazt a startMission() hívást végzi el.

---

## 6. Párhuzamossági korlátok

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

## 7. SDK verzió kompatibilitás

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

---

## 8. Kamera feed szálkezelés (DroneVideoWidget)

```
VideoFeeder.VideoDataListener.onReceive(byte[], int):
  → DJI SDK belső szálon érkezik (NEM main thread)
  → sendDataToDecoder() hívás: DJICodecManager belső szálán fut
  → UI interakció itt tilos

SurfaceTextureListener callbackek:
  → onSurfaceTextureAvailable, onSurfaceTextureDestroyed: főszálon érkeznek
  → attachCodecAndFeed() / releaseCodec() főszálon fut (OK)

TextureView touch listener:
  → Mindig main thread → showFocusRing() közvetlenül hívható

tapToFocus() reflection hívások:
  → DJISDKManager.getProduct() thread-safe
  → setFocusMode() callback: DJI SDK belső szálon
  → setFocusTarget() hívás a setFocusMode callback-ből: OK
    (nincs UI frissítés itt — az animáció a touch listenerből már indult)
```

**Fókuszgyűrű animáció szálbiztonsága:**
```java
// showFocusRing() mindig main thread-en hívódik (touch event)
// Animator.start() main thread-en biztonságos
// postDelayed() szintén OK
focusRing.animate().scaleX(1f).scaleY(1f).setDuration(200)
    .withEndAction(() -> focusRing.postDelayed(() ->
        focusRing.animate().alpha(0f)...start(), 700))
    .start();
// → Ha a kamera ablak közben bezárul: focusRing.setVisibility(INVISIBLE) a stop()-ban
//   nem szükséges explicit kezelés (az ablak GONE lesz, animáció ártalmatlan)
```

---

## 9. Proxy NPE bugfix — tanulság

```
Tünet: DJI SDK belső NPE, VideoFeed fekete, nincs crash az appban
Logcat: "Expected to unbox a 'int' primitive type but was returned null"

Ok: Az MSDK Set<Listener> gyűjteménybe teszi a proxy callbackeket.
    A Set.add() hívja a hashCode()-ot.
    A reflection proxy alapértelmezetten null-t ad vissza minden metódusra.
    null.intValue() → NullPointerException az MSDK belső kódjában.

Fix (MINDEN proxy InvocationHandlerben kötelező):
    case "hashCode": return System.identityHashCode(proxy);  // → int, sosem null
    case "equals":   return proxy == (args != null ? args[0] : null);
    case "toString": return "ClassName$CallbackType";

Érintett osztályok:
  DroneVideoWidget$VideoDataListener
  DroneVideoWidget$FocusModeCallback
  DroneVideoWidget$FocusTargetCallback
  DJIHelper$RcBatteryCallback
  DJIHelper$DroneBatteryCallback
  DJIHelper$FlightStateCallback
```

---

## 10. Mintavételi fotó-trigger retry és megerősítés — ✅ Implementálva (2026-07-03), eszközön még nem tesztelve

**Kontextus:** M04_L1 §18, M04_L2 §10, M04_L3 (`triggerSamplePhoto()`) — a
terepi tesztben megbízhatatlannak bizonyult `START_TAKE_PHOTO` WaypointAction
kiváltása app-vezérelt, visszaigazolt hívással.

**Nem szükséges dinamikus proxy (9. pont mintája nem alkalmazandó itt):** a
`Camera.startShootPhoto(CommonCallbacks.CompletionCallback)` egy **publikus,
rendes Java interfész** — közvetlen lambdával implementálható, **nincs
szükség** a §9-ben leírt `InvocationHandler`-alapú dinamikus proxyra (az csak
ott kell, ahol a metódus/interfész maga is csak reflexióval érhető el). Itt a
reflexió kizárólag a `startShootPhoto` metódus felkeresésére szolgál (mert a
projekt konvenciója szerint a build-idejű MSDK API-verzió-eltérések elleni
védelemhez mindenhol reflexióval hívjuk a kamera metódusait — ld. M04_L3).

**Újrapróbálkozási szabály:**

```
attemptCount = 0 (első kísérlet), max 1 újrapróbálkozás (attemptCount < 2 → retry)
Késleltetés próbálkozások között: 1000 ms (elegendő idő, hogy egy esetleges
  "kamera foglalt" (előző fotó még mentődik) állapot lezáruljon)
2 sikertelen kísérlet után: FAILED — nem blokkoló, csak naplózott állapot
```

**Miért csak 1 újrapróbálkozás, nem több?** A mintavételi misszió maga is
véges idejű (a drón a következő waypointra vár) — túl sok újrapróbálkozás
feleslegesen késleltetné a misszió folytatását. Egy (összesen 2 kísérlet)
a tapasztalat szerint (más MSDK kameravezérlési hibáknál, pl. `CameraConfigurator`
kameramód-váltás) elegendő a tranziens hibák (pl. előző írás még folyamatban)
lefedésére, anélkül hogy a hover-időt jelentősen megnyújtaná.

**Szálkezelés:** a `triggerSamplePhoto()` és a retry `Handler.postDelayed()`
hívásai a **main thread-en** futnak (konzisztensen a projekt többi
MSDK-callback kezelésével, ld. 1. és 4. pont) — nincs szükség külön
worker threadre, mert a kamera-parancs maga is aszinkron (SDK belső szálon
fut, a callback a main threadre érkezik vissza).

**Részleges hiba nem blokkolja a missziót:** ha egy mintapont fotója
véglegesen (2 kísérlet után is) sikertelen, a misszió **folytatódik** a
következő waypointra — ez konzisztens a §3 "Misszió atomiság" elvével
(egy komponens hibája nem szakítja meg az egész folyamatot feleslegesen),
és a `session.json` `trigger_confirmed: false` mezője (M04_L3) utólag
jelzi, mely pontot érdemes esetleg kézzel pótolni.
