# DroneFly vs. Litchi — Összehasonlító elemzés

**Készült:** 2026-04-17  
**Cél:** Litchi-ből átemelhető jó gondolatok azonosítása — mezőgazdasági és általános szempontból.  
**Litchi verzió:** v5.0.0 (2024. december), Litchi Hub (beta)

> **Kontextus:** A Litchi a DroneFly legközelebbi versenytársa: MSDK v4, Crystal Sky-on működik,
> egyszeri vételár (~$25), nagy közösség. Az agro survey területen viszont a DroneFly
> strukturálisan erősebb — a Litchi grid funkciója 2024 végén jelent meg béta állapotban.

---

## 1. Mezőgazdasági survey szempontból

### 1.1 DroneFly agro előnyök a Litchivel szemben (megtartandó!)

Ez az a területet ahol a DroneFly **jelenleg jobb** — ezt kell megőrizni és mélyíteni:

| Funkció | DroneFly | Litchi |
|---------|----------|--------|
| GSD kalkulátor (kamera szenzor alapján) | ✅ Beépített | ❌ Nincs |
| Terület (ha) megjelenítés | 🔲 Fejlesztés alatt | ❌ Nincs |
| Frontlap/sidelap overlap beállítás | ✅ Van | ⚠️ Korlátozott (beta) |
| Fotószám + repülési idő becslés | 🔲 Fejlesztés alatt | ❌ Nincs |
| Traktor nyomvonal import (grid szög) | 🔲 Tervezett | ❌ Nincs |
| Crystal Sky natív támogatás | ✅ Optimalizált | ✅ Működik |
| Offline teljesen | ✅ | ✅ |
| P4P v1 optimalizált profilok | ✅ | ⚠️ Általános |

**Következtetés:** Az agro survey területen a DroneFly-nak jelenleg nincs valódi versenytársa Crystal Sky-on. A Litchi grid funkciója 2024 végén jelent meg beta állapotban, GSD kalkulátor és overlap tervező nélkül — egy amatőr funkció, nem professzionális survey eszköz.

---

### 1.2 Amit a Litchi agro oldalon jobban csinál, és átemelendő

#### Multi-battery recovery (v5.0.0 újítás) ⭐⭐⭐

**Mit csinál Litchi-ben (v5-től):** Akkucsere után a misszió folytatható az utolsó befejezett wayponttól. Az előző verzióban ez nem volt — a misszió újraindult az elejéről.

**DroneFly átemelési javaslat:**  
- Az `MissionUploader` waypoint index callbackjét menteni (utolsó `onWaypointReached` index)
- Repülés utáni mentés: melyik waypointig jutott el a drón → CSV-be
- "Folytatás innen" opció: a misszió a mentett indextől generálódik újra

---

#### KML import táblahatárhoz ⭐⭐⭐

**Mit csinál Litchi Hub-ban:** KML/KMZ fájl importálható határként, a grid erre illeszkedik.

**Miért kritikus agro-ban:**  
MePAR (Magyar Parcella Azonosító Rendszer) KML/Shapefile formátumban exportál — ha a gazda már rendelkezik a tábla határával, ne kelljen újrarajzolni.

**DroneFly átemelési javaslat:** `KmlMissionParser.java` — a `CsvMissionParser` mellé.

---

### 1.3 Litchi Hub Area Mapping — figyelendő fejlesztés

A Litchi 2024 végén indított Litchi Hub-ot (beta), amiben megjelent az **Area Mapping** (grid misszió) funkció. Ez közvetlen verseny a DroneFly agro magjával.

**Jelenlegi állapot (2025 eleje):**
- Téglalap alakú grid — nem szabad polygon
- Nincs GSD kalkulátor
- Nincs ha-megjelenítés
- Nincs overlap becslő (képszám/idő)
- Google Earth 3D vizualizáció ✅ (előny)

**Stratégiai következtetés:** A Litchi elindult az agro irányba, de még gyenge. A DroneFly-nak 12–18 hónap előnye van funkcionalitásban — ezt ki kell használni.

---

## 2. Általánosan átemelendő funkciók a Litchi-ből

### 2.1 Görbe út interpoláció (CURVED + gimbal smooth) ⭐⭐⭐

**Mit csinál Litchi-ben:** A waypontok között nem éles szögben, hanem Bezier-görbével halad a drón. A gimbal szög is lineárisan (vagy DJI-stílusban: ease-in/ease-out) interpolálódik két waypoint között — nem ugrik, hanem folyamatosan változik.

**DroneFly jelenlegi állapot:** A CURVED waypoint mód be van kapcsolva (`WaypointMissionFlightPathMode.CURVED`), de a gimbal interpoláció szöge waypointonként nincs UI-ból beállítható.

**Átemelési javaslat (Cine modulhoz különösen fontos):**
- Per-waypoint gimbal pitch slider a waypoint szerkesztőben
- "Interpolálás" toggle: lineáris vs. DJI-smooth görbe
- Vizuális preview: a gimbal szög változása az útvonalon

---

### 2.2 POI (Point of Interest) Orbit + Focus mód ⭐⭐⭐

**Mit csinál Litchi-ben:**  
- **Orbit:** Megadott pont körül kör/félkör/ív pályán repül, kamera automatikusan a POI-ra néz
- **Focus Mode:** Litchi kezeli a gimbal pitchet és a drón yaw-ját (mindig a POI-ra néz), a pilóta csak a vízszintes mozgást irányítja RC-vel — így párhuzamos sín-shot mozgás közben a kamera végig a tárgyra fókuszál

**DroneFly jelenlegi állapot:** Nincs. MSDK v4-ben `HotpointMission` API-val megvalósítható.

**Átemelési javaslat:**
- Cine modulban alapfunkció
- Agro-ban: szélső fa, víztározó, épület gyors dokumentálásához opcionálisan

---

### 2.3 Waypoint akciók részletes rendszere ⭐⭐

**Mit csinál Litchi-ben:** Waypointonként legfeljebb 15 akció (Litchi Hub-ban korlátlan):
- Fotó, videó start/stop
- Zoom (folyamatos, értékre)
- Drón forgatás (yaw szögre)
- Gimbal tilt
- Kamera mód váltás
- Fókusz

**DroneFly jelenlegi állapot:** Az MSDK v4 `WaypointAction` API támogatja ezeket — UI nincs hozzá.

**Átemelési javaslat:**  
- Cine modulban: waypoint szerkesztőben "Akciók" szekció
- Agro modulban: automatikus fotózás indítás/leállítás a sávok elején/végén (megtakarít felesleges képeket)

---

### 2.4 Litchi Mission Hub — közösségi megosztás modell ⭐⭐⭐

**Mit csinál Litchi-ben:**
- Bárki feltöltheti a repülési tervét (publikus/privát)
- Böngészhető térkép nézetben (hol vannak megosztott missziók)
- Letölthető és azonnal futtatható
- Közösségi rating, kommentek

**Miért értékes a DroneFly számára:**
- Agro oldal: megosztott survey sablonok régió szerint ("ez a szőlőhegy-típus survey-je")
- Cine oldal: "látnivalók" missziói — pontosan a Litchi modell
- Fizetős tier: privát misszió-tár (team sharing)

**Átemelési javaslat:** A DroneFly webes Mission Hub pontosan ezt kell hogy nyújtsa — és arra kell építeni a freemium üzleti modellt.

---

### 2.5 Panoráma mód ⭐⭐

**Mit csinál Litchi-ben:** Automatikus panoráma fotózás — a drón adott pont felett megáll, és szisztematikusan lefotózza a 360°-os képet (vízszintes sávokban, beállítható szögközzel).

**DroneFly átemelési javaslat:**
- A `CameraConfigurator` + egy egyszerű panoráma waypoint szekvencia generátor
- Agro-ban: helyszín dokumentálás misszió előtt/után
- Cine-ban: alapfunkció

---

### 2.6 VR / FPV mód ⭐

**Mit csinál Litchi-ben:** A kamerakép teljes képernyőn, FPV módban jelenik meg — karton VR szemüveggel immerzív élmény. Autonóm misszió közben a drón "pilótaként" repülhető virtuálisan.

**Megjegyzés:** Crystal Sky-on a teljes képernyős kamera feed már van (`DroneVideoWidget`). VR mód inkább fogyasztói/Cine szegmens funkció.

---

### 2.7 Follow Me / Track mód ⭐

**Mit csinál Litchi-ben:**  
- **Follow Me:** A mobiltelefon GPS pozícióját követi a drón (pl. traktor követése)
- **Track:** Computer vision alapú alany követés (személy, jármű)

**Agro relevanciája:**  
Traktor követése — a drón folyamatosan felülről filmi a munkagép haladását. Speciális, de van igény rá precíziós agro demókhoz.

**Átemelési javaslat:** Follow Me MSDK v4-ben elérhető (`ActiveTrackMission`/`FollowMeMission`) — nem alapfunkció, de Cine modulban értékes.

---

### 2.8 Misszió sablonok és importálás ⭐⭐

**Mit csinál Litchi Hub-ban:**
- Elmentett missziók másolhatók, átnevezhetők, mappákba rendezhetők
- Import: KML/KMZ, CSV (Litchi formátum), más Litchi missziók
- A DroneFly már tud Litchi CSV-t olvasni — ez kompatibilitási előny

**Átemelési javaslat:**
- Misszió könyvtár UI (`ProjectManager` alapján már részben megvan)
- "Mentés sablonként" opció
- A Litchi CSV export/import kompatibilitás megtartandó — ez a felhasználói átállást segíti Litchi-ről DroneFly-ra

---

## Összefoglalás — prioritási mátrix (Litchi specifikus)

| Funkció | Agro érték | Általános/Cine érték | Fejlesztési nehézség | Prioritás |
|---------|-----------|----------------------|----------------------|-----------|
| Multi-battery resume | ⭐⭐⭐ | ⭐⭐ | Közepes | **Magas** |
| KML import | ⭐⭐⭐ | ⭐⭐ | Alacsony | **Magas** |
| Görbe út + gimbal interpoláció UI | ⭐ | ⭐⭐⭐ | Közepes | **Magas** (Cine modul) |
| POI Orbit + Focus mód | ⭐ | ⭐⭐⭐ | Közepes | **Magas** (Cine modul) |
| Mission Hub közösségi megosztás | ⭐⭐ | ⭐⭐⭐ | Magas | **Közepes** (web app) |
| Waypoint akciók UI | ⭐⭐ | ⭐⭐⭐ | Közepes | **Közepes** |
| Panoráma mód | ⭐ | ⭐⭐ | Alacsony | **Közepes** |
| Follow Me / Track | ⭐ | ⭐⭐ | Közepes | **Alacsony** |
| VR / FPV mód | ❌ | ⭐ | Magas | **Alacsony** |

---

## Amit a DroneFly tud, amit a Litchi nem (megvédendő előnyök)

| DroneFly előny | Megjegyzés |
|---------------|-----------|
| GSD kalkulátor | Litchi-ben nincs — 2025-ben sem |
| Terület (ha) display | Litchi-ben nincs |
| Frontlap/sidelap + képszám becslő | Litchi-ben nincs |
| P4P v1 optimalizált kamera profilok | Litchi általános |
| Traktor nyomvonal import | Litchi-ben nincs, máshol sincs |
| Agro-fókuszált UI (kevesebb zaj) | Litchi általános célú |
| Magyar lokalizáció + EU agro szabályozás | Litchi angolcentrikus |

---

## Stratégiai pozicionálás a két elemzés alapján

```
                    AGRO SURVEY
                         ↑
              DroneFly   |
              (Crystal   |   Dronelink
               Sky,      |   (internet-
               offline)  |   függő, lassú
                         |   Crystal Sky-on)
    ←─────────────────────────────────────→
    EGYSZERŰ                        KOMPLEX
    (pilóta-barát)              (sok funkció)
                         |
              Litchi      |
              (Cine,      |
               POI,       |
               orbit)     |
                         ↓
                    CINE / ÁLTALÁNOS
```

A DroneFly természetes pozíciója: **agro survey, Crystal Sky, offline, pilóta-barát** — ahol sem a Litchi, sem a Dronelink nem erős.
