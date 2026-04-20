# DroneFly — Fejlesztési Ötletek és Javaslatok

**Projekt:** DroneFly Android App
**Utolsó frissítés:** 2026-04-19
**Státusz:** Élő dokumentum — ide kerülnek a felmerülő ötletek, függetlenül attól, hogy megvalósíthatók-e

---

## 🚁 Drón kompatibilitás — hosszú távú kép

### Jelenlegi állapot
Az app DJI MSDK v4.18-ra épül, Crystal Sky + Phantom 4 Pro v1 kombóra optimalizálva.

### MSDK v4 kompatibilis drónok (jelenleg is működhetne)

| Drón | Megjegyzés |
|------|------------|
| Phantom 4 Pro v1/v2 | ✅ Referencia eszköz, tesztelve |
| Phantom 4 (alap, Advanced) | ✅ |
| Mavic 2 Pro / Mavic 2 Zoom | ✅ |
| Mavic Air 2 | ✅ (részleges MSDK v4) |
| Inspire 1 / Inspire 2 | ✅ |

### MSDK v5-re igénylő drónok (jövőbeli fejlesztés)

| Drón | Miért érdekes |
|------|--------------|
| Mini 3 Pro | EU A1 kategória — lakott terület felett is repülhető |
| Mini 4 Pro | EU A1 + legjobb képminőség a Mini kategóriában |
| Air 2S | 1"-es szenzor, kiváló survey képminőség |
| Mavic 3 Classic / Enterprise | Nagy szenzor, professzionális segment |
| Matrice 300/350 | Enterprise, multispektrális kamerák, OSDK is |

> **MSDK v5 korlát:** min. Android 6, Kotlin-alapú API, teljesen új osztálystruktúra.
> A DroneFly Java + Android 5.1 alapjai miatt v5 támogatáshoz a backend réteget
> (MissionUploader, DJIHelper, DroneVideoWidget) újra kell írni.
> A grid engine, térkép, UI réteg változatlan maradhat.

### RC kompatibilitás

| RC típus | Android eszköz csatlakoztatható? | Megjegyzés |
|----------|----------------------------------|------------|
| RC-N1 (Mini/Mavic/Air sorozat) | ✅ USB-C kábellel | DroneFly futhat a csatlakoztatott telefonon/tableten |
| RC-N2 | ✅ USB-C kábellel | RC-N1 utódja |
| RC 2 (beépített kijelző) | ❌ | Zárt Android, csak DJI Fly |
| RC Pro (Matrice) | ✅ (Android, nyílt) | Sideload engedélyezett |
| Crystal Sky | ✅ | Jelenlegi céleszköz |

### Hosszú távú stratégia — kockázatok és lehetőségek

**DJI-ra épülő irány (ajánlott rövid-közép távon):**
- Az MSDK-t a DJI aktívan fejleszti (v5 kiadva 2022, folyamatosan frissül)
- Enterprise drónok nyílt platformon maradnak (vállalati igény)
- Az EU U-Space szabályozás ösztönzi a harmadik féltől való GCS szoftvereket
- Kockázat: USA NDAA lista miatti DJI-ellenes szabályozás bizonytalanságot okoz

**MAVLink / ArduPilot irány (hardverfüggetlen, hosszú távú):**
- Nyílt protokoll, bármely ArduPilot-os drónt kezelné
- A DroneFly grid engine + térkép réteg változatlan maradhat
- Csak a MissionUploader backend cserélendő
- Lassabb fejlesztés, de DJI-tól független

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

## ⭐ Kiemelt prioritású fejlesztések — mezőgazdasági operatív szükséglet

### A. Terület (ha) folyamatos megjelenítése polygon rajzoláskor

**Felmerülés:** A pilóta polygon rajzolás közben azonnal tudni akarja, mekkora a tábla — a misszió tervezéséhez (akkumulátor, repülési idő) ez alapinformáció.

**Megvalósítás:**
- Minden `MapEventsReceiver.longPressHelper()` / polygon pont hozzáadás után újraszámolni a területet
- `SphericalUtil.computeArea()` (Google Maps SDK) vagy saját Shoelace-formula a koordinátákból
- Megjelenítés: a polygon belső részén lebegő szöveg (`MapOverlay`) vagy a státuszsávban: `Terület: 4.73 ha`
- Frissítés valós időben minden pont hozzáadásakor/mozgatásakor

**Prioritás:** **MAGAS** — agro pilóta alapigénye; más survey appaknál (DroneDeploy, Pix4D) mindig látható

---

### B. Átfedési arány vizuális visszajelző (képszám + repülési idő becslés)

**Felmerülés:** A frontlap (80%) és sidelap (70%) beállítása után a pilóta nem tudja, hány képet fog készíteni és mennyi ideig tart a repülés — ezek kritikusak az akkumulátor tervezéshez és megrendelői árajánlathoz.

**Megvalósítás:**
- A `GridMissionGenerator` már számolja a waypointokat és a sávok hosszát
- Képszám becslés: `képszám = (sávhossz_összesen / (GSD_m * (1 - frontlap))) * sávok_száma`
- Repülési idő: `idő = összes_útvonal_hossz / repülési_sebesség + képenként_N_mp_kamera_delay`
- P4P v1-nél: repülési sebesség CURVED módban kb. 8–10 m/s, kamera interval 2 mp
- Megjelenítés: a misszió paraméter panelen: `~240 fotó | ~18 perc | 2.1 akkumulátor`
- Frissítés: bármely paraméter (magasság, overlap, terület) változásakor azonnal

**Prioritás:** **MAGAS** — bérmunkánál árajánlat alapja; akkumulátor-tervezés alapja

---

### C. Traktor nyomvonal import → grid orientáció igazítás

**Felmerülés:** Ha ismert a vetési sorok iránya (traktor GPS track), a drón sávjait ezzel párhuzamosan kell tervezni → jobb NDVI eredmény, kevesebb mozgási artifakt.

**Importálható formátumok:** KML, GPX, GeoJSON (ISOXML közvetett úton, GIS szoftverből exportálva)

**Megvalósítás:**
- Fájl betöltés → track pontokból domináns bearing kiszámítása (átlagos irány)
- `GridMissionGenerator.gridAngle` automatikus beállítása erre az értékre
- Vizuális overlay: a traktornyomvonal halvány réteggé a térképen
- Manuális korrekció lehetősége (±5° csúszka)

**Prioritás:** **MAGAS** — precíziós agro differenciáló funkció; más GCS appban ritka

---

## 🔲 Nyitott ötletek — megvalósítható, de még nem priorizált

### 0. Indítóállás manuális magassági korrekció (SRTM-eltérés kompenzáció)

**Felmerülés:** Az SRTM domborzatkövetés a drón GPS-pozíciójának SRTM-magasságát veszi referenciaként. Ha a drón fizikailag magasabban van a talajtól (pl. autó tető +1,5 m, magas patakpart, platform), a barométer onnan nullázódik, de az SRTM nem tudja ezt. Az eredmény: minden waypoint szisztematikusan X méterrel alacsonyabban lesz, mint tervezett.

**Konkrét példa:** 18 m-es repülési magasságnál (+0,5 cm GSD) az autó tető 1,5 m-es eltérése 8%-os GSD hibát okoz.

**SRTM korlát:** A ~30 m felbontású SRTM modell kis kiterjedésű szintváltásokat (meredek patakpart, töltés, 1-2 m-es ugrás pár méteren) nem tartalmaz — ezek sem korrigálhatók SRTM alapon.

**Javasolt megoldás:** Egy `±X méter` beviteli mező az UI-ban ("indítóállás korrekció"), amelyet a pilóta ad meg becsléssel (pl. `-1.5` ha autótetőről indul). A képlet kiegészülne: `correctedAlt[wp] = baseAGL + (SRTM[wp] - SRTM[drón]) - indítóKorrekció`

**Miért nem implementáltuk most:** A pilótának kell ezt tudatosan kezelnie — a felszállási pont megválasztása a repüléstervezés része. A korrekció bevezetése UI komplexitást ad hozzá, és a becslési hiba nagyobb lehet, mint maga az eltérés.

**Prioritás:** Alacsony — tudatos felhasználói felelősség; RTK drón ezt automatikusan megoldja.

---

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

### 8. OSM közigazgatási határok + repülési kategória overlay

**Felmerülés:** OpenStreetMap térképen megjeleníteni a közigazgatási határokat (A1/A2/A3 kategóriák szerinti légtérzónák monitorozásához). A felhasználó a tervezett terület felett láthatná, hogy milyen EU Open kategóriában repül.

**Technikai megvalósítás:**
- OSM Overpass API lekérdezés: `admin_level=8` (városok/községek) határvonalak
- OSMDroid overlay rétegként megjeleníteni (Polyline/Polygon)
- Színkódolás: A1 zöld / A2 sárga / A3 narancs
- Offline esetben: előre letöltött GeoJSON/Shapefile a belső tárhelyen

**Korábban halasztva:** Ez a funkció felmerült, de a kamera feed PiP és tap-to-expose prioritást kapott.

**Prioritás:** Közepes — szabályozási szempontból értékes funkció EU-ban.

---

### 9. Fotó készítés a kamera feed ablakból

**Felmerülés:** A CAM gomb bekapcsolt kamera feed mellett lehetővé tenni egyszeri fotó készítést közvetlenül az appból (nem waypoint alapú, hanem operátor által kért).

**Technikai megvalósítás:** `Camera.startShootPhoto()` MSDK v4 reflection-alapú hívás, hasonlóan a tapToFocus megközelítéshez.

**Használati eset:** Repülés közben érdekesnek tűnő terület gyors dokumentálása.

**Prioritás:** Alacsony-közepes.

---

### 10. Misszió feltöltés timeout kezelés

**Felmerülés:** Az MSDK v4 nem garantál timeout-ot a `uploadMission()` hívásra. Ha a feltöltés nem fejeződik be (RC jel elvesztés, SDK belső hiba), a callback soha nem hívódik meg, az UI "befagyott" állapotban marad.

**Javaslat:** 30 másodperces Handler timeout: ha nem érkezik callback, `callback.onError("Feltöltési timeout")`.

**Megjegyzés:** Az M04_L4 dokumentációban már le van írva, implementáció még hiányzik.

**Prioritás:** Közepes — megbízhatóság szempontjából fontos.

---

### 11. MSDK v5 backend (Mini 3 Pro / Mini 4 Pro / Air 2S / Mavic 3 támogatás)

**Felmerülés:** Az MSDK v4 csak régebbi drónokat támogat teljes mértékben. A Mini 4 Pro, Mini 3 Pro, Air 2S és Mavic 3 Classic már csak MSDK v5-tel érhető el.

**Ami kell hozzá:**
- `MissionUploader` MSDK v5 implementáció (Kotlin vagy Java-kompatibilis wrapper)
- `DJIHelper` v5 verziója
- `DroneVideoWidget` v5 video feed API-val
- `minSdk` emelése legalább 24-re (Android 7) — Crystal Sky csak v4-et kap
- Build variant: `flavorDimensions "sdk"`, `productFlavors { msdk4 { ... } msdk5 { ... } }`

**Megjegyzés:** A grid engine (M02), export (M03), térkép UI (M01) teljesen változatlan maradna. Csak az M04 réteg cserélendő.

**Prioritás:** Magas — piaci elérhetőség szempontjából fontos (Mini sorozat a legelterjedtebb EU-ban).

---

### 12. Multi-spektrális kamera profil (P4 Multispectral)

**Felmerülés:** A Phantom 4 Multispectral ugyanolyan testű, mint a P4P v1, de 6 kamerával (RGB + 5 spektrális sáv: G/R/RE/NIR + panchro). Precíziós mezőgazdaságban (NDVI, növényegészség) ezt használják.

**Ami kell hozzá:**
- Új drón profil: `P4 Multispectral` a `DroneProfiles.java`-ban (szenzor: 4.8 × 3.6 mm, 2.08 MP / sáv, f/2.2, 5.74 mm)
- GSD számítás az RGB kamara alapján (a spektrális sávok azonos GSD-n repülnek)
- Esetleg: spektrális sávonkénti névjegy az exportált CSV-ben

**Prioritás:** Közepes — precíziós agro szegmens.

---

### 13. Területi misszió-jelentés (exportálható PDF/CSV)

**Felmerülés:** Repülés után a megrendelőnek átadható dokumentum: terület (ha), waypontok száma, becsült képszám, repülési idő, GSD, drón típus, dátum.

**Megvalósítás:** Android `PdfDocument` API vagy egyszerű HTML → WebView → print.

**Prioritás:** Alacsony — üzleti értéke van, de nem operációs funkció.

---

---

## 🔵 Dronelink / Litchi elemzésből azonosított új funkciók

> Forrás: 2026. április stratégiai elemzés.  
> Részletes elemzés: [`DRONELINK_OSSZEHASONLITAS.md`](DRONELINK_OSSZEHASONLITAS.md), [`LITCHI_OSSZEHASONLITAS.md`](LITCHI_OSSZEHASONLITAS.md)

### D. Terrain Follow — AGL magasság korrekció

**Felmerülés:** Dombos táblákon a fix magasságon repülő drón eltérő GSD-t produkál a domb tetején és a völgyben — az ortofotó nem egyenletes. Dronelink ezt AGL (Above Ground Level) módban kezeli: digitális domborzatmodell alapján waypointonként korrigálja a magasságot.

**Megvalósítás:**
- Az `ElevationProvider.java` (176 sor) infrastruktúra már megvan
- SRTM adatforrás (NASA, ingyenes, 30m felbontás) — offline csempékkel is működik
- A `GridMissionGenerator` minden waypointhoz az `ElevationProvider`-től kér magasságot
- UI: "Terep követés" toggle a misszió paramétereknél + magasságprofil diagram

**Prioritás:** Közepes–magas — dombos terepen kritikus, síkvidéken opcionális

---

### E. Multi-battery misszió tervezés + automatikus resume

**Felmerülés:** P4P v1 ~25 perc repülési idő. 20 ha felett 2+ akkumulátor szükséges. Ha a folytatás nem az utolsó befejezett sávtól indul, képhézag keletkezik az ortofotóban. Litchi v5.0.0 (2024 december) vezette be a battery recovery funkciót.

**Megvalósítás:**
- A misszió becslő (B pont) kiszámolja a becsült repülési időt → ha > 22 perc: figyelmeztetés
- `MissionUploader` `onWaypointReached` callback: mindig menteni az aktuális waypoint indexet
- "Folytatás" opció induláskor: ha van mentett index → a misszió onnan generálódik újra

**Prioritás:** Magas — nagy területen kötelező, hiánya képhézagot okoz

---

### F. KML / KMZ import táblahatárhoz

**Felmerülés:** MePAR (Magyar Parcella Azonosító Rendszer) és QGIS KML/KMZ formátumban exportál. Ha a gazda vagy az agronómus rendelkezik a tábla határával, ne kelljen újrarajzolni.

**Megvalósítás:**
- `KmlMissionParser.java` a `CsvMissionParser` mellé
- KML `<Placemark><Polygon>` tag olvasása → polygon koordináták betöltése a térképre
- A betöltött polygon azonnal misszió-határként használható

**Prioritás:** Magas — agro, MePAR integráció, Litchi Hub is tud KML-t importálni

---

### G. Crosshatch (kettős irányú grid) opció

**Felmerülés:** A szokásos egyirányú grid mellett 90°-ban elforgatott második ráfutás jobb 3D modellt és fotogrammetriai eredményt ad (WebODM, Agisoft feldolgozáshoz). Dronelink és Litchi Hub is támogatja.

**Megvalósítás:**
- `GridMissionGenerator`-ban `crosshatch: boolean` paraméter
- Ha bekapcsolt: a generált waypoint lista után hozzáfűzi ugyanazt 90°-ban elforgatva
- UI: "Keresztirányú ráfutás (3D modellhez)" toggle

**Prioritás:** Közepes — precíziós fotogrammetriához hasznos, sík NDVI survey-nél felesleges

---

### H. Image Asset Manifest (geotag napló)

**Felmerülés:** Repülés után a feldolgozó szoftvernek (WebODM, Agisoft) szüksége van a képek GPS koordinátáira. Dronelink automatikusan generál JSON asset manifest-et minden képhez.

**Megvalósítás:**
- A `Camera.setMediaFileCallback` (fotószámláló, 4. pont) kibővítve
- Minden képnél: időbélyeg + drón GPS (lat/lon/alt) + gimbal pitch → CSV vagy JSON fájlba
- Összevonható a waypoint-szintű telemetria naplózással (5. pont)

**Prioritás:** Közepes — fotogrammetria pipeline-hoz értékes

---

### I. Georektifikáció / Misszió eltolás terepi korrekció

**Felmerülés:** Ha a tábla sarokpontjai nem egyeznek a térképpel (GPS drift, elavult műholdfotó), a teljes misszió eltolható egy referenciapont alapján — Dronelink "Drone Offsets" funkcióként ismeri.

**Megvalósítás:**
- "Misszió igazítás" gomb → a pilóta a térképen megad egy referencia-nyilat (Δlat, Δlon)
- A teljes waypoint lista eltolódik — matematikailag egyszerű vektoros művelet

**Prioritás:** Alacsony–közepes — precíziós RTK nélküli repülésnél hasznos

---

### J. Korlátozási zóna rajzolás (No-Fly Zone overlay)

**Felmerülés:** A pilóta a térképen manuálisan jelöl tiltott területet (épület, fa, oszlop, légvezeték) — a misszió generáláskor a grid ezeket kihagyja. Dronelink "Restriction Zones" funkcióként ismeri.

**Megvalósítás:**
- A meglévő polygon-rajzoló logika újrahasználható: második polygon típus "tiltott zóna" jelöléssel
- A `GridMissionGenerator` obstacle logikája már részben kezeli a kivágást (sáv clipping)
- UI: a polygon rajzolóba "tiltott zóna" mód kapcsoló

**Prioritás:** Közepes — terepi akadályok kezelése, safety szempontból is értékes

---

### K. Per-waypoint gimbal UI + interpoláció szerkesztő

**Felmerülés:** A CURVED waypoint mód be van kapcsolva, de a gimbal szög waypointonként UI-ból nem állítható. Litchi ezt lineáris interpolációval, Dronelink görbe alapú ease-in/ease-out interpolációval kezeli.

**Megvalósítás:**
- Waypoint szerkesztőben gimbal pitch slider (pl. -90°-tól 0°-ig)
- "Interpolálás" toggle: lineáris vs. smooth görbe a szögátmenethez
- Elsősorban a Cine modulhoz szükséges — Agro modulban a nadir rögzített (-90°)

**Prioritás:** Közepes (Cine) / Alacsony (Agro)

---

### L. POI Orbit + Focus mód

**Felmerülés:** Megadott pont körül kör/félkör/ív pályán repül, a kamera folyamatosan a POI-ra néz. Litchi egyik legerősebb funkciója — agro-ban épület/fa dokumentáláshoz, Cine-ban alapfunkció.

**Megvalósítás:** MSDK v4 `HotpointMission` API — megvan az SDK-ban, UI hiányzik.

**Prioritás:** Magas (Cine modul) / Alacsony (Agro modul)

---

### M. Panoráma mód

**Felmerülés:** A drón adott pont felett megáll és szisztematikusan lefotózza a 360°-os képet (vízszintes sávokban). Litchi, Dronelink egyaránt támogatja.

**Megvalósítás:**
- Panoráma waypoint szekvencia generátor: egy koordinátánál megálló drón + gimbal szögek + forgatások sorozata
- `CameraConfigurator` + waypoint szekvencia kombinálva

**Prioritás:** Közepes (Cine) / Alacsony (Agro)

---

### N. DroneFly Cine modul — koncepció

**Felmerülés:** A filmes/content creator szegmens sokkal nagyobb piac mint az agro survey. A Litchi-nél jobb cine UX lehetséges — timeline-alapú, keyframe szerkesztővel.

**Ami más lenne mint a Litchi:**
- Timeline nézet (mint egy videóeditorban): X tengely = repülési idő, Y = gimbal szög, sebesség
- Ease-in/ease-out görbe szerkesztő a szögátmenetekhez
- POI Orbit + gimbal interpoláció kombinálva
- Mission Hub közösségi megosztás (látnivalók, ingatlan reveal sablonok)

**Fejlesztési feltétel:** A Cine modul külön APK vagy product flavor — az Agro UI nem terhelődik Cine funkciókkal.

**Prioritás:** Hosszú távú — 3. fázis (lásd STRATEGIA.md)

---

### O. On-the-fly misszió generálás

**Felmerülés:** A drón aktuális pozíciója alapján azonnal generál egy grid missziót — térkép-rajzolás nélkül, terepi gyors döntésnél. Dronelink egyik egyedi funkciója.

**Megvalósítás:**
- "Gyors misszió" gomb: a drón GPS pozíciója körül N×M méteres területre azonnal generál egy alapgridet
- A terület mérete és a magasság slider-rel állítható

**Prioritás:** Alacsony — hasznos, de nem alapfunkció

---

### P. Webes repüléstervező + Mission Hub

**Felmerülés:** A terepi pilóta nem mindig Crystal Sky-on tervez — böngészőből is kényelmes lenne. A Mission Hub a közösségi megosztás és az előfizetéses bevétel alapja.

**Architektúra:**
```
Web app (React + Leaflet) → FastAPI backend → DroneFly Android app (letölti a missziót)
```

**Mission Hub funkciók:**
- Misszió tárolás, szinkronizálás
- Közösségi megosztás (Litchi Mission Hub modell)
- Agro analytics: telemetria naplók, területi riportok PDF-ben
- Sablon könyvtár (régió + drón típus szerint)

**Bevételi modell:** Freemium — alapfunkciók ingyenes, prémium $6–20/hó  
**Prioritás:** 3. fázis (lásd STRATEGIA.md)

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
