# DroneFly — Fejlesztési Ötletek és Javaslatok

**Projekt:** DroneFly Android App
**Utolsó frissítés:** 2026-04-17
**Státusz:** Élő dokumentum — ide kerülnek a felmerülő ötletek, függetlenül attól, hogy megvalósíthatók-e

---

## ✅ Megvalósított (ebben a fejlesztési körben)

| Funkció | Verzió | Megjegyzés |
|---------|--------|------------|
| Folyamatos repülés (CURVED mód) | v1.9.4 | Drón nem áll meg waypointnál; kamera intervallum triggerel |
| Gimbal auto-nadir (-90°) | v1.9.4 | Mission start előtt automatikus beállítás |
| SD kártya ellenőrzés misszió előtt | v1.9.4 | Blokkoló figyelmeztetés ha nincs kártya |
| Offline térkép (automatikus mód) | v1.9.5 | WiFi nélkül setUseDataConnection(false); réteg guard |
| Térkép offline letöltés gomb | v1.9.5 | SAT/MAP gomb hosszú nyomása, zoom 14–17 |
| GPS gomb — pozícióra ugrás | v1.9.6 | Drón GPS preferált, tablet GPS fallback, zoom 15 |
| REC gomb — képernyőkép | v1.9.7–v1.9.8 | PNG → /sdcard/Pictures/DroneFly/; zöld villanás visszajelzés |
| REC gomb — képernyővideó | v1.9.7–v1.9.9 | MediaProjection, 720p/25fps/H264; stopRecording háttérszálon |

---

## 🔲 Nyitott ötletek — megvalósítható, de még nem priorizált

### 1. REC gomb — csak videóra egyszerűsítés
**Felmerülés:** A tablet főmenüjéből készített rendszer képernyőkép gyorsabb (azonnali) az in-app screenshotnál (1–2 mp késés, mert `getDrawingCache()` újrarajzol).  
**Javaslat:** A rövid nyomás screenshottot eltávolítani; a REC gomb legyen csak videórögzítő (rövid nyomás = indít/állít le).  
**Prioritás:** Alacsony — a rendszer screenshot megoldja a problémát.

---

### 2. Műholdszám megjelenítés hibakeresése
**Felmerülés:** A SAT: szám a státuszsávban nem mindig jelenik meg, vagy 0-t mutat csatlakoztatott drón esetén is.  
**Állapot:** Terepi tesztelésen ADB logcattal kell diagnosztizálni (`adb logcat | grep -i satellite`).  
**Teendő:** Valódi repülés közben ellenőrizni, hogy a `FlightController.StateCallback` érkezik-e, és a `getSatelliteCount()` milyen értéket ad.  
**Prioritás:** Közepes — státuszjelzőként fontos, de a misszió ettől még működik.

---

### 3. Laptopról indítható ADB videófelvétel minőség javítása
**Felmerülés:** A `DroneFly_videofelvtel.bat` 20 Mbps-en rögzít, de tömörítési artefaktok látszanak UI tartalmon.  
**Korlát:** Crystal Sky Android 5.1, H264 encoder, UI tartalom (éles szélek, szöveg) H264-gyel eleve artefaktos.  
**Lehetőség:** Ha a Crystal Sky firmware-e támogatja, kipróbálni `--size 1280x720 --bit-rate 20000000` kombinációt (720p @ 20 Mbps) — esetleg kisebb artefakt a natív 1080p-nél.  
**Prioritás:** Alacsony — elfogadott korlát.

---

### 4. Misszió közbeni automatikus fotószám számláló
**Felmerülés:** A kamera intervallum módban fotóz, de az app nem mutatja, hány fotó készült eddig.  
**Megvalósítás:** `Camera.setMediaFileCallback` figyelése — minden elkészült fotónál növelni egy számlálót, megjeleníteni a státuszsávban vagy a misszió haladásjelzőn.  
**Prioritás:** Közepes — survey repülésnél hasznos visszajelzés.

---

### 5. Waypoint-szintű telemetria naplózás
**Felmerülés:** Repülés után nem lehet tudni, hogy minden tervezett sávot lefedett-e a drón (pl. szélben eltért-e).  
**Javaslat:** Minden `onWaypointReached` callbacknél az aktuális GPS koordinátát menteni egy CSV-be → repülés utáni ellenőrzés.  
**Prioritás:** Alacsony–közepes.

---

### 6. Akkumulátor szint figyelmeztetés repülés közben
**Felmerülés:** A státuszsáv mutatja az akkuszintet, de aktív misszió közben nem jelez, ha kritikusan alacsony.  
**Javaslat:** Ha `droneBatteryPercent < 20%` és misszió fut → sárga figyelmeztető toast + hangjelzés.  
**Prioritás:** Közepes — biztonsági funkció.

---

### 7. Zoom lefagyás enyhítése
**Felmerülés:** Crystal Sky + OSMDroid 6.1.17 — gyors nagyításkor (~zoom 10-ről zoom 18-ra) az app lefagy 1–3 másodpercre, miközben a tile cache töltődik.  
**Jelenlegi megoldás:** `setZoom()` (azonnali, egy tile-batch) + `animateTo()` (pan, nem indít új tile-kérést).  
**Korlát:** OSMDroid 6.1.17-ben nincs `setHttpConnectionTimeout` vagy `setTileRetryDelay` API — nem tudjuk lassítani a tile fetch-et.  
**Lehetséges javítás:** Zoom léptetés korlátozása (pl. egyszerre max 3 zoom szint ugrás), vagy tile source HTTP timeout csökkentése egyéni `OkHttpClient`-tel.  
**Prioritás:** Alacsony — elfogadott korlát Crystal Sky hardveren.

---

## ❌ Elvetett ötletek

### MyDroneSpace integráció
**Felmerülés:** A DJI Go 4 app figyelmeztet firmware-szintű geofencing korlátozásokra (pl. repülési tilalmi zónák). Jó lenne az appban is megjeleníteni a HungaroControl MyDroneSpace adatait.  
**Elvetés oka:**
- A MyDroneSpace-nek nincs nyilvános REST API-ja
- Az adatok csak a webes felületen és a hivatalos mobil appban érhetők el
- Integráció csak HungaroControl partnerséggel lehetséges (nem elérhető)
- Az OpenAIP LGT réteg részben lefedi ezt az igényt (légtérosztályok megjelenítése)  
**Alternatíva:** OpenAIP LGT réteg (már implementálva, v1.8.0)

---

### Firmware szintű geo-korlátozások megkerülése
**Felmerülés:** A DJI firmware tilthat repülési zónákat (No-Fly Zone), amit az app nem tud felülírni.  
**Elvetés oka:** DJI szándékos biztonsági funkció, nem megkerülhető és nem is kívánatos megkerülni. Az operátornak a hatályos légtérszabályok szerint kell repülni.

---

### In-app GPS lock vizualizáció (körök animáció)
**Felmerülés:** Más navigációs appokban animált körök jelzik, amíg a GPS fix nincs kész.  
**Elvetés oka:** A státuszsáv `SAT: N` számlálója elegendő visszajelzést ad; vizuális animáció Crystal Sky-on CPU terhelést okozhat.

---

## 📝 Megjegyzések terepi tapasztalatokból

- **SD kártya eltűnt felvételek:** Nem app-bug — a DJI Go 4 app adott esetben törölheti vagy áthelyezheti a felvételeket. Mindig az app-független DCIM mappát ellenőrizd.
- **Videóminőség korlát:** Crystal Sky H264 encoder UI tartalomhoz (~720p max); a `DroneFly_videofelvtel.bat` laptopról 1080p/20Mbps-sel jobb eredményt ad, de az artefakt H264-nél UI tartalmon elkerülhetetlen.
- **Képernyőkép:** A tablet főmenüjéből (rendszer szintű) gyorsabb és élesebb az in-app megoldásnál. Az in-app REC screenshot mentési helye: `/sdcard/Pictures/DroneFly/`.
