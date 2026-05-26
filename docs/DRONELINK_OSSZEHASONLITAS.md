# DroneFly vs. Dronelink — Összehasonlító elemzés

**Készült:** 2026-04-17  
**Cél:** Dronelink-ből átemelhető jó gondolatok azonosítása — mezőgazdasági és általános szempontból.  
**Forrás:** Dronelink hivatalos dokumentáció, support hub, termékoldal.

> **Kontextus:** A Dronelink Crystal Sky-on gyakorlatilag használhatatlan (Google Play Services crash),
> előfizetéses modell ($20–100/hó), internet-függő. A DroneFly ezért nem közvetlen versenytárs —
> de a Dronelink funkciói jelzik, hogy a professzionális piac mit vár el egy GCS apptól.

---

## 1. Mezőgazdasági survey szempontból átemelendő funkciók

### 1.1 Terrain Follow (AGL — Above Ground Level) ⭐⭐⭐

**Mit csinál Dronelink-ben:** A drón a tényleges talajfelszín felett tartja a megadott magasságot, nem a felszállóponthoz képesti relatív magasságon repül. Digitális domborzatmodell (DEM) alapján automatikusan számítja a waypontok magasságát.

**Miért kritikus agro survey-nél:**  
Dombos táblákon (Tokaj-hegyalja, Balaton-felvidék stb.) ha a drón fix magasságon repül, a domb tetején a GSD kisebb, a völgyben nagyobb lesz — az ortofotó nem egyenletes. Terrain follow nélkül precíziós agro survey dombos terepen nem végezhető.

**DroneFly jelenlegi állapot:** Az `ElevationProvider.java` létezik (176 sor) — az infrastruktúra részben megvan.

**Átemelési javaslat:**  
- A `GridMissionGenerator` waypont-magasságait az `ElevationProvider` által visszaadott DEM értékkel korrigálni
- Adatforrás: SRTM (NASA, ingyenes, 30m felbontás) — offline is működik letöltött csempékkel
- Vizuális visszajelzés: a magasság-profil diagramként a misszió paraméter panelen

---

### 1.2 Multi-battery misszió tervezés + resume ⭐⭐⭐

**Mit csinál Dronelink-ben:** Nagy terület esetén automatikusan jelzi, hány akkumulátor szükséges, hol kell leszállni a csere miatt, és a folytatáskor pontosan onnan veszi fel a missziót, ahol abbahagyta (utolsó fotó koordinátájától).

**Miért kritikus agro survey-nél:**  
P4P v1 akkumulátora ~25 perc repülési idő. 20 ha felett ez már 2+ akkumulátor. Ha a folytatás nem pontos, képhézag keletkezik az ortofotóban.

**DroneFly jelenlegi állapot:** Nincs implementálva.

**Átemelési javaslat:**  
- A misszió becslő (⭐ már kiemelt fejlesztési pont) kiszámolja a becsült repülési időt
- Ha az idő > 22 perc: figyelmeztetés + javasolt leszállási pont a sáv végén
- `MissionUploader` bővítése: utolsó lefutott waypoint koordinátájának mentése → újraindításkor innen folytatja

---

### 1.3 Crosshatch (kettős irányú grid) minta ⭐⭐

**Mit csinál Dronelink-ben:** A szokásos egyirányú grid mellett 90°-ban elforgatott második ráfutást is repül ugyanazon a területen. Ez jobb 3D modellt és pontosabb ortofotót eredményez, különösen egyenetlen talajon.

**Miért értékes agro survey-nél:**  
Precíziós agro fotogrammetriánál (WebODM/Agisoft feldolgozás) a crosshatch szignifikánsan jobb pont-felhőt ad. Nem minden esetben kell (sík tábla, NDVI), de opcionálisként értékes.

**Átemelési javaslat:**  
- A `GridMissionGenerator`-ban opcionális `crosshatch: boolean` paraméter
- Ha bekapcsolt: a generált waypoint lista után hozzáfűzi ugyanazt 90°-ban elforgatva
- UI: egy toggle a misszió paraméter panelen — "Keresztirányú ráfutás (3D modellhez)"

---

### 1.4 Mission Estimate — részletes becslő ⭐⭐⭐

**Mit csinál Dronelink-ben:** Megmutatja a várható repülési időt, képszámot, akkumulátor-felhasználást komponensenként. Másodpercek alatt frissül bármely paraméter változásakor.

**DroneFly jelenlegi állapot:** Ez már a **kiemelt fejlesztési B pont** — de a Dronelink implementációból átemelendő részlet:
- Komponensenkénti bontás (ha több sáv-csoportra van osztva a misszió)
- Akkumulátor-szám külön kiemelve (nem csak percben, hanem "2.1 akkumulátor")

---

### 1.5 Georektifikáció / Drone Offsets (mission alignment) ⭐⭐

**Mit csinál Dronelink-ben:** Terepi referenciapont alapján a teljes misszió eltolható X/Y irányban, ha a GPS hibás vagy a műholdfotó nem pontos. Percekig tart, nem kell újratervezni.

**Miért értékes agro survey-nél:**  
Ha a tábla sarka nem egyezik a térképpel (GPS drift, elavult ortofotó), az egész misszió eltolható a valós táblahatárhoz igazítva — a terepi pilóta manuális korrekció nélkül elvégezheti.

**Átemelési javaslat:**  
- A waypoint lista teljes eltolása egy (Δlat, Δlon) vektorral — matematikailag egyszerű
- UI: "Misszió igazítás" gomb → a pilóta a térképen húz egy referencia-nyilat

---

### 1.6 Image Asset Manifest / Geotag napló ⭐⭐

**Mit csinál Dronelink-ben:** Repülés közben JSON formátumban rögzíti a felvett képek fájlnevét, GPS koordinátáját, magasságát, gimbal szögét — feldolgozó szoftver (WebODM, Agisoft) számára.

**Átemelési javaslat:**  
- A `Camera.setMediaFileCallback` (már tervezett fotószámláló, #4 nyitott ötlet) kibővítve
- Minden képnél: időbélyeg + drón GPS + gimbal pitch → CSV vagy JSON fájlba
- A telemetria-naplózás (#5 nyitott ötlet) és ez összeolvasztható egy funkcióba

---

## 2. Általánosan átemelendő funkciók (Agro + Cine + általános)

### 2.1 Komponens-alapú misszió architektúra ⭐⭐⭐

**Mit csinál Dronelink-ben:** A misszió nem egyetlen repülési terv, hanem újrahasználható *komponensek* láncolata: grid + orbit + waypoint + hover — egymás után automatikusan végrehajtva.

**Miért értékes:**  
- Agro: grid survey + leszállópont hover + hazatérés mint egyetlen egybefüggő misszió
- Cine: reveal shot (közelítés) + orbit + távolodás — egy gombnyomással

**Átemelési javaslat:**  
Ez a DroneFly Cine modul architektúrájának alapja lenne — a jelenlegi egységes misszió-modell helyett `MissionComponent[]` lista.

---

### 2.2 Orbit misszió komponens ⭐⭐

**Mit csinál Dronelink-ben:** Megadott pont körül kör, félkör, ív vagy spirál pályán repül; a kamera folyamatosan a POI-ra néz; sugár és magasság valós időben állítható RC karokkal.

**DroneFly jelenlegi állapot:** Nincs, a DJI SDK `HotpointMission` API-val megvalósítható MSDK v4-ben.

**Átemelési javaslat:**  
- Cine modulban: "POI Orbit" mint alapfunkció
- Agro modulban: szélső fa, vízügyi létesítmény gyors dokumentálásához opcionálisan

---

### 2.3 Korlátozási zónák rajzolása a térképen ⭐⭐

**Mit csinál Dronelink-ben:** A pilóta a térképen manuálisan rajzol tiltott zónákat (épület, fa, oszlop) — a misszió automatikusan megkerüli.

**DroneFly jelenlegi állapot:** A `GridMissionGenerator` tartalmaz obstacle logikát (sáv clipping), de a UI-ból nem rajzolható.

**Átemelési javaslat:**  
- A meglévő polygon-rajzoló logika (már implementált) újrahasználható: második "tiltott zóna" polygon típus
- A grid generátor már tudja kezelni — csak az UI kell hozzá

---

### 2.4 3D repülési útvonal előnézet ⭐⭐

**Mit csinál Dronelink-ben:** A misszió a webes felületen 3D-ben szimulálható, a kamera látószöge is látható — mielőtt a drón felemelkedne.

**Átemelési javaslat (reális szint):**  
- Teljes 3D nézet: webes Mission Hub feladata (nem az Android app)
- Android appon belül: 2D-s magasságprofil diagram (útvonal oldalnézete) — egyszerűbb, de hasznos

---

### 2.5 Misszió sablonok / újrahasználható tervek ⭐⭐

**Mit csinál Dronelink-ben:** Elmentett misszió-sablonok — pl. "standard 80/70 survey 100m magasságon" — betölthetők és csak a terület polygon-ja módosítandó.

**Átemelési javaslat:**  
- A `ProjectManager` már menti a missziókat — sablon-jelölés (is_template flag) hozzáadása
- "Sablon alapján új misszió" opció: betölti a paramétereket, a polygon üres marad
- Ez a webes Mission Hub egyik alapfunkciója is lehet

---

### 2.6 Hibrid manuális/automatikus repülés ⭐⭐

**Mit csinál Dronelink-ben:** A misszió futása közben az RC karokkal valós időben módosítható a drón pozíciója (pl. sáv közbeni kisebb kiigazítás) — a misszió nem szakad meg.

**Megjegyzés:** MSDK v4-ben ez korlátozottan elérhető (a waypoint misszió feltöltése után az SDK átveszi az irányítást). Részleges megvalósítás: pause + manuális korrekció + resume.

---

### 2.7 Facade misszió (vertikális felmérés) ⭐

**Mit csinál Dronelink-ben:** Épület homlokzatát, siló falát, tornyot vertikálisan fotóz — zig-zag mintával, beállítható távolságból és átfedéssel.

**Átemelési javaslat:**  
- Agro-ban: szélmalom, tározó gát, tároló épület gyors dokumentálásához
- Cine-ban: épület reveal, architectural shot
- Külön modul — nem alapfunkció, de egy Facade komponens értékes lenne

---

### 2.8 On-the-fly misszió generálás ⭐

**Mit csinál Dronelink-ben:** A drón aktuális pozíciója alapján azonnal generál egy grid/orbit missziót — térkép-rajzolás nélkül, terepi gyors döntés esetén.

**Átemelési javaslat:**  
- "Gyors misszió" gomb: a drón pozíciója körül N×M méteres területre azonnal generál egy alapgridet
- Agro-ban: kis folt gyors dokumentálásához (kártevőgóc, dőlt növényzet)

---

### 2.9 RTMP élő stream ⭐

**Mit csinál Dronelink-ben:** A kamera feed RTMP protokollon streamelhető külső rendszerbe (YouTube Live, egyedi szerver).

**Átemelési javaslat:**  
- A `DroneVideoWidget` jelenlegi implementációja csak lokálisan jeleníti meg a feedet
- RTMP stream: agro-ban agrárszakértő táv-megfigyeléséhez, cine-ban kliensnek való élő preview
- MSDK v4-ben elérhető: `LiveStreamManager`

---

### 2.10 KML/KMZ import misszió határokhoz ⭐⭐

**Mit csinál Dronelink-ben:** Külső GIS eszközből (QGIS, Google Earth) exportált KML/KMZ fájl importálható, a polygon automatikusan betöltődik misszió-határként.

**DroneFly jelenlegi állapot:** CSV import van (Litchi formátum), KML nincs.

**Átemelési javaslat:**  
- A `CsvMissionParser` mellé `KmlMissionParser` — táblahatár polygon KML-ből
- Különösen értékes: mezőgazdasági nyilvántartó rendszerek (MePAR, QGIS) KML-t exportálnak

---

## Összefoglalás — prioritási mátrix

| Funkció | Agro érték | Általános érték | Fejlesztési nehézség | Prioritás |
|---------|-----------|-----------------|----------------------|-----------|
| Terrain Follow (AGL) | ⭐⭐⭐ | ⭐⭐ | Közepes | **Magas** |
| Multi-battery resume | ⭐⭐⭐ | ⭐⭐ | Közepes | **Magas** |
| Mission Estimate (részletes) | ⭐⭐⭐ | ⭐⭐⭐ | Alacsony | **Magas** (már folyamatban) |
| KML import | ⭐⭐⭐ | ⭐⭐ | Alacsony | **Magas** |
| Korlátozási zóna rajzolás | ⭐⭐ | ⭐⭐⭐ | Alacsony | **Közepes** |
| Crosshatch grid | ⭐⭐ | ⭐ | Alacsony | **Közepes** |
| Georektifikáció | ⭐⭐ | ⭐ | Közepes | **Közepes** |
| Image asset manifest | ⭐⭐ | ⭐ | Alacsony | **Közepes** |
| Misszió sablonok | ⭐⭐ | ⭐⭐ | Alacsony | **Közepes** |
| Orbit komponens | ⭐ | ⭐⭐⭐ | Közepes | **Közepes** (Cine modul) |
| 3D előnézet | ⭐ | ⭐⭐⭐ | Magas | **Alacsony** (web app feladata) |
| RTMP stream | ⭐ | ⭐⭐ | Közepes | **Alacsony** |
| Facade misszió | ⭐ | ⭐⭐ | Magas | **Alacsony** |
| On-the-fly generálás | ⭐⭐ | ⭐ | Közepes | **Alacsony** |

---

## Amit a Dronelink NEM tud, amit a DroneFly igen

| DroneFly előny | Megjegyzés |
|---------------|-----------|
| Crystal Sky natív támogatás | Dronelink ott összeomlik |
| Teljesen offline működés | Dronelink internet-függő |
| Agro-specifikus UI | Dronelink általános, nem agro-optimalizált |
| Egyszeri vételár | Dronelink $20–100/hó előfizetés |
| Traktor nyomvonal → grid szög | Dronelink-ben nincs |
| MePAR / hazai szabályozás | Dronelink EU-specifikus lokalitás nélkül |
