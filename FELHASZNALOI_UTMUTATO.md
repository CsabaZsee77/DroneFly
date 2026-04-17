# ✈️ DroneFly GCS – Felhasználói Útmutató

**Verzió:** v1.9.7
**Utolsó frissítés:** 2026-04-17
**Céleszköz:** DJI Crystal Sky tablet (Android 5.1)
**Drón:** DJI Phantom 4 Pro v1 (és más DJI drónok)

---

## 📖 Tartalomjegyzék

1. [Bevezetés](#-bevezetés)
2. [Gyors Kezdés](#-gyors-kezdés)
3. [Felület Áttekintése](#-felület-áttekintése)
4. [Térkép Kezelése](#-térkép-kezelése)
5. [Terület Megrajzolása](#-terület-megrajzolása)
6. [Misszió Beállítások](#-misszió-beállítások)
7. [Offset – Túlrepülési Határ](#-offset--túlrepülési-határ)
8. [Akadályjelölés](#-akadályjelölés)
9. [Domborzatkövetés](#-domborzatkövetés)
10. [Start / Home Pont](#-start--home-pont)
11. [Misszió Feltöltése és Indítása](#-misszió-feltöltése-és-indítása)
12. [Misszió Közbeni Vezérlés](#-misszió-közbeni-vezérlés)
13. [Haladásjelző és Misszió Folytatása](#-haladásjelző-és-misszió-folytatása)
14. [Szimuláció](#-szimuláció)
15. [Térkép Rétegek](#-térkép-rétegek)
16. [Repülési Terv Mentése és Betöltése](#-repülési-terv-mentése-és-betöltése)
17. [Exportálás és Importálás](#-exportálás-és-importálás)
17. [Kamera Beállítások](#-kamera-beállítások)
18. [Élő Kamera Feed (CAM)](#-élő-kamera-feed-cam)
19. [Státuszsáv](#-státuszsáv)
20. [Drón Profilok](#-drón-profilok)
21. [Akkumulátorcsere – Nagy Területek](#-akkumulátorcsere--nagy-területek)
22. [Offline Térkép](#-offline-térkép)
23. [Képernyőkép és Videórögzítés (REC)](#-képernyőkép-és-videórögzítés-rec)
24. [Hibaelhárítás](#-hibaelhárítás)
25. [Tippek és Trükkök](#-tippek-és-trükkök)

---

## 🎯 Bevezetés

A **DroneFly GCS** egy mezőgazdasági survey drón missziókat tervező és irányító Android alkalmazás, amely közvetlenül a **DJI Crystal Sky** kijelzőn fut.

### Mit tud a rendszer?

✅ **Terület megrajzolása** – Térkép érintésével sokszög megjelölése, csúcspontok húzással szerkeszthetők
✅ **Automatikus misszió generálás** – GSD alapú repülési magasság, kígyózó (lawnmower) mintázat
✅ **Több drón profil** – P4P v1, Mavic 2 Pro, Mini 3 Pro, Air 2S és más DJI drónok kameraparaméterei
✅ **Offset (túlrepülési határ)** – A drón a megrajzolt területhatáron túl is átfed, mint a Map Pilot Pro-ban
✅ **Akadályjelölés** – Fa, torony, épület jelölése térképen; a generátor a sávokat az akadály körül vezeti
✅ **Domborzatkövetés** – SRTM DEM alapján waypontok magassága korrigálható
✅ **Státuszsáv** – Valós idejű drón állapot (kapcsolat, RC, GPS, akkumulátor)
✅ **Repülési terv mentése / betöltése** – Teljes projekt (poligon + beállítások + akadályok) elmentése és visszatöltése a tableten
✅ **Exportálás** – Litchi CSV és KMZ formátum
✅ **Feltöltés és indítás** – Közvetlen DJI MSDK v4 feltöltés a csatlakoztatott drónra
✅ **Folyamatos repülés** – A drón nem áll meg minden waypointnál; a kamera automatikusan, egyenletes időközönként fotóz (intervallum mód)
✅ **Gimbal auto-nadir** – A misszió indítása előtt az app automatikusan -90°-ra forgatja a gimbalt (lefele néző kamera)
✅ **SD kártya ellenőrzés** – Misszió indítás előtt az app ellenőrzi, hogy be van-e helyezve a memóriakártya; hiány esetén blokkoló figyelmeztetés
✅ **Akkumulátorcsere kezelés** – 99 waypontnál nagyobb misszió automatikus szegmensekre bontása
✅ **Haladásjelző** – Repülés közben a drón pozíciója megjelenik a térképen, a teljesített szakasz zölden kiemelve, WP számláló
✅ **Misszió folytatása** – Akkucsere után az app felajánlja a folytatást az utolsó elért waypointtól
✅ **Szimuláció** – Az útvonal 10x gyorsított animációja valós indítás előtt, drón ikonnal és haladásjelzővel
✅ **Élő kamera feed (PiP)** – A drón kameraképe bekapcsolható a bal alsó sarokban
✅ **Tap-to-expose** – A kamera képre érintve az expozíció az érintett pontra igazodik (mint a DJI Go 4-ben)
✅ **Térkép rétegek** – N2K (Natura 2000 védett területek), LGT (légtérosztályok), ZÓN (területhasználat) be/kikapcsolható rétegek
✅ **Légtér magassági szűrő** – Csak azok a légterek jelennek meg, amelyek ütköznek a tervezett repülési magassággal
✅ **Offline térkép** – Az app automatikusan offline módba vált WiFi nélkül; előre letölthető az aktuális nézet (zoom 14–17)
✅ **GPS pozícióra ugrás** – GPS gomb: a térkép drón GPS (ha csatlakoztatva) vagy tablet GPS alapján az aktuális pozícióra ugrik (~1 km-es látómező)
✅ **Képernyőkép / videórögzítés** – REC gomb: rövid nyomás = PNG screenshot, hosszú nyomás = MP4 videó; fájlok: /sdcard/Pictures/DroneFly/

### Munkafolyamat

```
Terület megrajzolása
        ↓
Beállítások módosítása (GSD, átfedés, irány, offset)
        ↓
[Opcionális] Akadályok jelölése
        ↓
[Opcionális] Domborzatkövetés bekapcsolása
        ↓
Feltöltés → START
        ↓
Pre-flight szekvencia (SD ellenőrzés → Gimbal nadir → Kamera → Intervallum fotózás)
        ↓
Misszió közbeni vezérlés (Szünet / Stop)
```

Az app **automatikusan újragenerálja** az útvonalat minden terület- vagy beállítás-változáskor (≥3 pont esetén), tehát a misszió mindig naprakész marad.

---

## 🚀 Gyors Kezdés

### 1. Az alkalmazás megnyitása

Keresd a **DroneFly** ikont a Crystal Sky home képernyőjén, és érintsd meg.

### 2. Első misszió – lépések

1. **Navigálj** a felmérendő területre (GPS gomb vagy kézzel húzd a térképet)
2. **Érintsd meg a területet** – minden érintés egy sarokpontot rak le
3. **Rajzolj legalább 3 sarokpontot** → az app azonnal generálja az útvonalat
4. **Ellenőrizd a statisztikákat** (panel alján): terület, magasság, idő
5. **Feltöltés** gomb → erősítsd meg → várj, amíg a feltöltési folyamatjelző eltűnik és a "Feltöltés sikeres" ablak megjelenik
6. **START** gomb → a drón elindul (csak akkor aktív, ha ≥6 GPS műhold van)

### 3. Gyors navigáció

| Gesztus | Hatás |
|---------|-------|
| Egy ujjal húzás | Térkép mozgatás |
| Két ujjal csippentés | Zoom be/ki |
| Két ujjal forgatás | Térkép forgatás |
| Egy ujjal koppintás | Sarokpont lerakás |
| Ujjal hosszan nyomva + húzás | Sarokpont mozgatás (szerkesztés) |
| Sarokpontra koppintás | Törlés dialog |

---

## 🗺️ Felület Áttekintése

```
┌─────────────────────────────────────────────────────┐
│ [GPS]  ┌──────── Státuszsáv ─────────────────┐ ║ P │
│ [SAT]  │ DRON: P4P│RC: OK 85%│SAT:14H│AKKU:92%│║ A │
│ [CAM]  └─────────────────────────────────────┘ ║ N │
│                                                  ║ E │
│         T É R K É P                              ║ L │
│                                                  ║   │
│    [Terület rajzolva]                            ║ » │
│    [Narancs útvonal]                             ║   │
│    [Piros akadály körök]                          │   │
│ ┌─── LIVE ✕ ───────────────┐                    │   │
│ │   [Élő kamera kép]       │                    │   │
│ └──────────────────────────┘                    │   │
└─────────────────────────────────────────────────────┘
```

**Bal felső sarok (fentről lefelé):**
- `GPS` – térkép az aktuális pozícióra ugrik (~1 km-es látómező, zoom 15)
  - Ha a drón csatlakoztatva van: drón GPS pozíciójára ugrik
  - Ha a drón nincs csatlakoztatva: tablet GPS-re ugrik (pl. „Tablet GPS (±8 m)")
- `SAT/MAP` – műhold ↔ utcatérkép váltás; **hosszú nyomás** = offline letöltés az aktuális nézetről
- `CAM` – élő kamera feed ablak be/kikapcsolása
- `REC` – képernyőkép / videórögzítés
  - **Rövid nyomás**: azonnali PNG képernyőkép → `/sdcard/Pictures/DroneFly/`
  - **Hosszú nyomás**: képernyővideó indítása (gomb pirosan villog) / leállítása → MP4 ugyanoda
- `N2K` – Natura 2000 / védett területek réteg
- `LGT` – légterek réteg (alatta: `ALT:∞` / `ALT:Xm` magassági szűrő)
- `ZÓN` – területhasználati zónák réteg

**Teteje (lebegő státuszsáv):** drón kapcsolat, RC, akkumulátorok, GPS

**Jobb oldal (csúsztatható panel):**
- `»` / `«` gomb – panel be/kihajtás; a gomb a panel bal alsó részén helyezkedik el (szándékosan alul, hogy ne nyíljék be véletlenül)

---

## ✏️ Terület Megrajzolása

### Sarokpontok lerakása

**Egyszerűen érintsd meg a térképet** – nincs külön "rajzolás mód" gomb. Minden érintés azonnal rak le egy sarokpontot.

- Az első érintéstől **kék körök** jelölik a sarokpontokat
- 3. ponttól **kék kitöltés** jelzi a területet
- 3. ponttól az app **azonnal generálja** a narancs útvonalat

### Sarokpontok szerkesztése

**Ujjal hosszan nyomva + húzás** – a sarokpont mozog, az útvonal valós időben frissül.

> ⚠️ Fontos: rövid érintés = új pont, hosszú érintés + mozgatás = szerkesztés.
> A kettőt ne keverd össze.

### Sarokpont törlése

Koppints rá a törölni kívánt **kék körre** → dialog jelenik meg:
```
P3 jelölő
47.12345, 19.12345

Töröljem ezt a pontot?
[Törlés]  [Mégse]
```

### Utolsó pont visszavonása

**"Utolsó pont törlése"** gomb a panel tetején.

### Összes törlése

**"Összes törlése"** gomb (piros) – törli a területet, az akadályokat, az útvonalat és a start pontot is.

---

## ⚙️ Misszió Beállítások

A jobb oldali panelen SeekBar csúszkákkal állítható minden paraméter.

### Drón profil

Válaszd ki a **Drón** legördülő listából a használt eszközt:

| Profil | Szenzor | Fókusztáv |
|--------|---------|-----------|
| P4P v1 | 13.2 × 8.8 mm | 8.8 mm |
| Mavic 2 Pro | 13.2 × 8.8 mm | 10.3 mm |
| Mini 3 Pro | 9.6 × 7.2 mm | 8.7 mm |
| Air 2S | 13.2 × 8.8 mm | 12.4 mm |
| *(és még több)* | | |

A profil meghatározza a GSD ↔ magasság számítást.

### GSD (Ground Sample Distance)

**Mit jelent:** 1 képpont hány centiméternek felel meg a talajon.

```
SeekBar: 0.5 – 10.0 cm/px (lépés: 0.1)
Default: 3.0 cm/px
```

| GSD | Magasság (P4P v1) | Alkalmazás |
|-----|------------------|------------|
| 1.0 cm/px | ~39 m | Precíziós (tőszámlálás) |
| 2.0 cm/px | ~78 m | Részletes survey |
| 3.0 cm/px | ~118 m | Általános mezőgazdaság |
| 5.0 cm/px | ~196 m | Nagy területek, gyors |

> ⚠️ Figyelem: 120 m felett az EU Open kategóriában engedély szükséges!
> Az app figyelmeztető üzenetet küld, ha a GSD ennél magasabb magasságot igényel.

**GSD változásakor** az ajánlott sebesség automatikusan frissül.

### Oldalsó átfedés (Sidelap)

```
SeekBar: 50 – 90%
Default: 75%
```

Szomszédos sávok közötti képátfedés. Magasabb érték = több kép, kisebb sávköz, hosszabb repülési idő.

| Sidelap | Ajánlott alkalmazás |
|---------|---------------------|
| 60–65% | Egyszerű ortomozaik |
| 70–75% | Szabványos survey |
| 80–85% | 3D modellezés, dombos terep |

### Menetirány átfedés (Frontlap)

```
SeekBar: 60 – 90%
Default: 80%
```

A haladás irányában egymást követő képek átfedése.

### Repülési sebesség

```
SeekBar: 3 – 15 m/s
Default: automatikus (GSD alapján)
```

GSD változásakor az app automatikusan ajánl sebességet a mozgáselmosódás minimalizálásához (1/800 s záridőhöz tervezett).

> Lassabb sebesség = élesebb képek, hosszabb idő.

### Repülési irány

```
SeekBar: 0 – 179°
Default: 0° (K–Ny irányú sávok)
```

A sávok tájolása. Állítsd be a terület hosszabb tengelyének irányára a legjobb hatékonysághoz.

**Tipikus értékek:**
- `0°` – kelet–nyugat irányú sávok (alapértelmezett)
- `90°` – észak–dél irányú sávok
- Tetszőleges szög, 1°-os pontossággal

---

## 📐 Offset – Túlrepülési Határ

```
SeekBar: 0 – 30 m
Default: 0 (kikapcsolva)
```

### Mi az offset?

Az offset meghatározza, hogy a generátor a megrajzolt területhatáron **túl** is kiterjeszti-e az útvonalat. Ez biztosítja, hogy a terület szélei is megfelelő átfedéssel legyenek lefedve.

```
Offset = 0:              Offset = 15 m:
┌───────────┐            ┌─────────────────┐
│ ═══ ═══  │            │   ═══ ═══ ═══  │
│ Terület   │            │   Megrajzolt    │
│           │            │   Terület       │
└───────────┘            └─────────────────┘
 Útvonal a határon belül  Útvonal ±15 m-rel kiterjesztve
```

### Miért van szükség offloadra?

A megrajzolt terület **széleinél** az átfedés kieshet, ha nincs offset. Fásított parcella szélénél: a drón a fák mellé kerülhet a területhatáron.

> **Fontos:** offset esetén a drón **kilép a megrajzolt területen kívülre**.
> Bizonyosodj meg, hogy nincs akadály a terület közelében!

### Ajánlott érték

A panel automatikusan mutatja az ajánlott értéket:
```
Túlrepülés: 10 m  (ajánl: 22 m)
```
Az ajánlott érték = (sávköz + fotóköz) / 2.

---

## 🚧 Akadályjelölés

Az akadályjelöléssel megjelölhetsz a térképen veszélyes tárgyakat (fa, torony, antenna, épület), és a generátor **kikerüli** azokat.

### Akadály hozzáadása

1. Nyomd meg a **"+ Akadály jelölése"** gombot
   → A gomb narancsvörösre vált: *"Akadály mód: BE (Kattints a térképen!)"*

2. **Koppints a térképen** az akadály helyére

3. Töltsd ki a megjelenő dialógot:

```
Pozíció: 47.12345, 19.12345

Biztonsági zóna sugara (m):
[ 15 ]   ← kötelező mezők, fehér háttér

Akadály magassága (m, talajtól):
[ 25 ]

[Hozzáad]  [Mégse]
```

| Mező | Ajánlott értékek |
|------|-----------------|
| Sugár | Fa: 10–20 m, Torony: 20–50 m, Épület: 15–30 m |
| Magasság | Fa: 15–35 m, Torony: 30–100+ m |

4. A térképen megjelenik egy **piros kör** az akadály körül

5. Az útvonal automatikusan újragenerálódik – a sávok **megszakadnak** az akadály határánál

### Hogyan kerüli ki az app az akadályt?

```
Akadály nélkül:     Akadállyal (15 m sugár):
═══════════════     ════════     ════════
═══════════════     ═══════   ○  ═══════
═══════════════     ════════     ════════
                              ↑
                         Akadály köre
                         (piros zóna)
```

A sávok az akadály körének **határáig** mennek, majd a másik oldalon folytatódnak. A drón egyenesen átrepül az akadály felett (ha a magasság > akadály magassága), vagy teljesen kihagyja a zónát (ha alacsonyabban repül).

### Mikor aktív az akadály?

Az akadály **csak akkor** aktív (veszélyes), ha a **repülési magasság ≤ akadály magassága**.

| Repülési magasság | Akadály magassága | Eredmény |
|------------------|-------------------|---------|
| 80 m | 25 m (fa) | ✅ Átrepülhet – akadály figyelmen kívül hagyva |
| 80 m | 100 m (torony) | 🚫 Veszélyes – útvonal az akadály körül kerül |
| 40 m | 25 m (fa) | 🚫 Veszélyes – útvonal az akadály körül kerül |

> **Tipp:** Ha survey módban (pl. 80 m) repülsz és fák vannak a területen
> (25–30 m), az app automatikusan átrepül felettük és nem kerüli ki.
> Torony vagy antenna esetén (>80 m) meg kell jelölni!

### Akadály információ és törlése

Koppints bármelyik **piros körre** → dialog:
```
Akadály #2
Pozíció: 47.12345, 19.12345
Biztonsági zóna: 15 m
Magasság: 100 m

Ha a repülési magasság <= 100 m, az akadály aktív...

[Törlés]  [Bezárás]
```

### Összes akadály törlése

**"Akadályok törlése"** gomb → megerősítő dialog.

---

## 🏔️ Domborzatkövetés

A domborzatkövetés az SRTM (90 m-es) digitális domborzati modell alapján korrigálja a waypontok magasságát, így a drón **konstans talajtól mért magasságon** repül dombos terepen is.

### Bekapcsolás

1. Kapcsold be a **"Domborzatkövetés"** kapcsolót a panelen
2. Nyomd meg az **"Újragenerálás (beállításokkal)"** gombot
3. Az app letölti az Open-Elevation API-ból a terepmagasságokat
4. A statisztika mutatja a min/max korrigált magasságokat:
   ```
   Domborzat korrigálva: 78 m – 95 m (takeoff: 142 m tszf)
   ```

### Mikor hasznos?

- Dombos, hullámos területek
- Völgyek, ahol a terep akár 30–50 m-t is változhat
- Erdőszélek melletti survey

### Mikor NEM szükséges?

- Sík Alföldön (< 5 m szintkülönbség a területen)
- Ha a terepen belül a szintkülönbség a sávköznél kisebb

> **Megjegyzés:** A domborzatkövetés **internet-kapcsolatot** igényel az Open-Elevation API-hoz.
> Offline módban nem elérhető.

---

## 📍 Start / Home Pont

A start/home pont a drón felszállási helyét jelöli. Ez befolyásolja:
- Az útvonal rajzát (a narancs vonal innen indul és ide tér vissza)
- A "Return to Home" (RTH) funkcióját

### Beállítása

1. Nyomd meg a **"Start/Home pont"** gombot
   → Gomb szövege: *"Kattints a start pontra..."*

2. Koppints a térképen a felszállási helyre

3. Zöld marker jelenik meg, a gomb szövege: *"Start/Home pont ✓"*

> **Tipp:** A start pontot a drón pontos állóhelyére helyezd!
> Ha nincs beállítva, az első wayponttól indul és oda tér vissza.

---

## 📤 Misszió Feltöltése és Indítása

### Előfeltételek

- ✅ Drón bekapcsolva és Crystal Sky-hoz csatlakozva
- ✅ Státuszsáv: "DRON: P4P" (zöld)
- ✅ GPS műholdak: min. 6 (SAT: 6+, ideálisan ≥10 és "H" = Home pont beállítva)
- ✅ Misszió generálva (narancs útvonal látható)

> **GPS ellenőrzés:** A **START** gomb csak ≥6 GPS műhold esetén aktív. Ha kevesebb van, a gomb szürke és felirata **"START (GPS!)"** – várd meg, amíg a drón elegendő műholdat lát.

### Feltöltés

1. Nyomd meg a **"Feltöltés"** gombot
2. Megerősítő dialog jelenik meg:
   ```
   Misszió feltöltése
   47 waypoint feltöltése a drónra?
   [Feltöltés]  [Mégse]
   ```
3. Megjelenik a **feltöltési folyamatjelző** ("Feltöltés folyamatban a drónra...") – ne kapcsold ki az appot!
4. Sikeres feltöltés után **"Feltöltés sikeres"** ablak jelenik meg – nyugtázd OK-val
5. Hiba esetén a hibaüzenet jelenik meg – ellenőrizd a drón csatlakozást és próbáld újra

### Start

1. Nyomd meg a **"START"** gombot (narancs, nagy) – csak akkor aktív, ha a feltöltés sikerült és ≥6 műhold van
2. Megerősítő dialog:
   ```
   Repülés indítása
   A drón elindítja a missziót. Biztos vagy benne?
   [START]  [Mégse]
   ```
3. Az app elvégzi a **pre-flight szekvenciát**:
   - **SD kártya ellenőrzés** – ha nincs kártya, blokkoló figyelmeztetés (lásd lejjebb)
   - **Gimbal beállítás** – automatikusan -90°-ra (lefele) forgatja a kamerát
   - **Kamera konfiguráció** – alkalmazza a beállított paramétereket
   - **Intervallum fotózás indítása** – a kamera automatikusan fotóz a repülés során
4. A **Szünet** és **Stop** gombok aktívvá válnak

> ⚠️ **Biztonsági ellenőrzőlista START előtt:**
> - **SD kártya behelyezve?** ← az app figyelmeztet, ha hiányzik, de ellenőrizd manuálisan is!
> - Propellerek rögzítve?
> - Légitér szabad?
> - Visual line of sight biztosított?
> - RC vezérlő kéznél?
> - SAT ≥10 és "H" látható a státuszsávban?

---

## 🎮 Misszió Közbeni Vezérlés

### Szünet

**"Szünet"** gomb → a drón az aktuális pozícióban lebeg, misszió szüneteltetett.
→ Gomb szövege *"Folytatás"*-ra vált.

**"Folytatás"** gomb → misszió folytatódik az előző waypointtól.

### Stop

**"Stop"** gomb → megerősítő dialog:
```
Misszió leállítása
Leállítod a missziót? A drón az aktuális pozícióban lebeg,
kézi vezérlés átvehető.
[Stop]  [Mégse]
```
→ Kézi vezérlés azonnal átvehető az RC-vel.

> **Vészhelyzet:** Az RC **bármikor** átveszi a kézi vezérlést, az app Stop gombja nélkül is.

---

## 📍 Haladásjelző és Misszió Folytatása

### Haladásjelző repülés közben

Aktív misszió alatt az app valós időben követi a drón haladását:

- **Drón marker** – Narancssárga drón ikon jelenik meg a térkép aktuális pozícióján
- **Teljesített útvonal** – A már megtett szakasz zöld vonallal emelkedik ki a narancssárga tervezett útvonalon
- **WP számláló** – A panel tetején megjelenik: `WP: 12 / 247` (aktuális / összes)
- **Progress bar** – Vízszintes sáv mutatja az előrehaladást százalékosan

A haladásjelző automatikusan eltűnik, amint a misszió befejezésre kerül vagy leállítják.

### Misszió folytatása akkucsere után

Nagy területeken akkumulátorcsere szükséges. A drón RTH-val leszáll, az akkut kicserélik, majd újraindítják.

**A folytatás menete:**

1. Drón visszatér és leszáll (RTH vagy kézi leszállás)
2. Akku csere → drón újraindul → Crystal Sky újra csatlakozik
3. Az app **felajánlja a folytatást:**

```
Folytatod a missziót?
Az előző repülés a(z) 45. waypontnál szakadt meg.
Onnan folytatod, vagy újrakezded?

[Folytatás (WP 44-től)]    [Újrakezdés]
```

4. **Folytatás:** Az app a megszakítás előtti waypointtól (−1 biztonsági átfedés) újra feltölti a maradék útvonalat és automatikusan elindítja
5. **Újrakezdés:** A teljes szegmens elejéről indul

> **Fontos:** A folytatási állapot **tablet újraindítás után is megmarad**, ha a terv el van mentve fájlba. Elég betölteni a tervet, az app automatikusan visszatölti a megszakítási pontot. Ha a terv nincs mentve (csak memóriában él), az állapot elvész tablet újraindítás esetén.

> **Ajánlott:** Repülés előtt mindig mentsd el a tervet (legalább egyszer), hogy akkucsere + tablet újraindítás után is folytatni tudd.

---

## 🎬 Szimuláció

A **Szimuláció** funkció lehetővé teszi a teljes repülési útvonal lejátszását valós indítás előtt – drón nélkül, beltérben is ellenőrizhető.

### Szimuláció indítása

1. Tölts be vagy generálj egy repülési tervet
2. Az oldalpanelen nyomd meg a **"Szimuláció"** gombot (zöld, feltöltés után aktív)
3. A drón ikon megjelenik az útvonal első pontján, és elkezd haladni

### Ami látható szimuláció közben

- **Drón marker** animálódik a waypontokon végig
- **Zöld vonal** követi a teljesített szakaszt
- **WP számláló** frissül (pl. `SIM  WP: 126 / 4666`)
- **Progress bar** mutatja az előrehaladást

### Sebesség

A szimuláció **10× gyorsított** – pl. 5 m/s beállított sebességnél a drón ikon 50 m/s-nak megfelelő ütemben halad. Kb. 5 perces repülés ~30 másodperc alatt látható végig.

### Leállítás

- Nyomd meg a **"Szimulació leállítása"** gombot (ugyanaz a gomb, szövege megváltozik)
- A szimuláció automatikusan megáll, ha az útvonal végére ér

> **Megjegyzés:** Nagy útvonalakon (4000+ WP) a szimuláció is lassan épülhet fel az első néhány másodpercben, de nem okoz lefagyást – a térkép csak 10 lépésenként frissül.

---

## 🗺️ Térkép Rétegek

A bal oldali gombsorban három opcionális térképréteg kapcsolható be. Minden réteg az aktuálisan látható területre tölt be adatot az internetről – ha nincs kapcsolat, a rétegek nem töltődnek be.

### Gombok és visszajelzés

| Gomb | Réteg | Tartalom | Forrás |
|------|-------|----------|--------|
| **N2K** | Natura 2000 / védett területek | Természetvédelmi területek, nemzeti parkok | OpenStreetMap Overpass API |
| **LGT** | Légtér | CTR, ATZ, Restricted, Prohibited, RMZ, TMZ zónák | OpenAIP Core API |
| **ZÓN** | Területhasználati zónák | Lakóterület, ipari, katonai, repülőtér | OpenStreetMap Overpass API |

**Betöltés jelzése:** Gomb megnyomásakor a szöveg `N2K...` / `LGT...` / `ZÓN...`-ra változik – ez jelzi, hogy a lekérés folyamatban van. Betöltés után a gomb visszaáll és teljesen világos lesz (nem halvány). Ha halvány marad → ki van kapcsolva.

**Kikapcsolás:** Ugyanarra a gombra kattintva az összes overlay eltűnik.

**Fontos:** Az Overpass szerver (OSM adatbázis) néha lassú – ha az első kattintásra nem tölt be, várj 30–60 másodpercet, majd próbáld újra. Ne kattints gyorsan többször egymás után.

### Zoom követelmény

Zoom szint < 10 esetén a lekérés nem indul el (a bounding box túl nagy lenne). Nagyíts be a területre, majd aktiváld a réteget.

---

### N2K – Natura 2000 / Védett területek

Természetvédelmi korlátozások alatt álló területek. **Drónos repüléshez hatósági engedély szükséges.**

> **Fontos:** Az N2K területek természetvédelmi tilalmat jelentenek (hatóság: Természetvédelem / NPI), de **nem automatikusan légtérkorlátozások** is. A CTR / Restricted légtér tiltást a LGT réteg mutatja. Egyes területeken mindkét korlátozás együtt van érvényben.

**Szín kód:**
- 🟢 **Zöld** – protect_class 2 (nemzeti park, pl. Hortobágy, Balaton-felvidék)
- 🟠 **Narancs** – protect_class 4 (Natura 2000 SAC/SCI jelölt terület)
- 🟫 **Barna** – protect_class 5–6 (egyéb védelemterület, tájvédelmi körzet)

A kitöltés a védett terület belsejét jelöli. Zárt határvonalú területek félátlátszó fill + körvonallal jelennek meg; nagyobb, összetett (relációs) területeknél körvonal jelenik meg.

> **Adatok teljessége:** Az OSM-alapú N2K réteg a valóság kb. 60–70%-át fedi le. A nagyobb nemzeti parkok és főbb SAC területek általában szerepelnek; kisebb, kevésbé ismert N2K jelölések hiányozhatnak. Hivatalos és teljes forráshoz a természetvédelem.hu adatait javasoljuk.

---

### LGT – Légtér (OpenAIP)

Az OpenAIP Core API-ból lekért, Magyarország felett aktív légtérosztályok. Ez a **legfontosabb réteg** a szabályszerű repülés tervezéséhez.

**Megjelenés:** Minden légtér típusonként eltérő **félátlátszó kitöltéssel** és körvonallal jelenik meg, így könnyen megkülönböztethetők egymástól.

**Szín kód:**

| Kitöltés / Körvonal | Légtér típus | Jelentés |
|---------------------|-------------|----------|
| 🔴 Piros (sötét) | CTR (Control Zone) | Repülőtér kontrollzóna – engedély szükséges |
| 🔴 Piros (mély) | Prohibited (P) | Tiltott légtér – repülés tilos |
| 🔴 Piros | Danger (D) | Veszélyes légtér – óvatosság szükséges |
| 🟠 Narancs | Restricted (R) | Korlátozott légtér – feltételek teljesülése esetén repülhető |
| 🟠 Narancs (halvány) | ATZ (Aerodrome Traffic Zone) | Repülőtér forgalmi zóna |
| 🟡 Sárga | TMZ (Transponder Mandatory Zone) | Transzponder kötelező |
| 🔵 Kék | RMZ (Radio Mandatory Zone) | Rádiókommunikáció kötelező |

> **TMA (Terminal Control Area)** típusú légtereket az app kizárja – ezek akkora polygonok (pl. Budapest TMA az egész ország felett), hogy megjelenítésük lefagyasztaná a rendszert.

#### Magassági szűrő – ALT gomb

Az LGT gomb alatt egy **`ALT:∞`** feliratú gomb jelenik meg. Koppintásra körkörösen lépteti a tervezett repülési magasságot, és az app **csak azokat a légtereket mutatja**, amelyekkel ütközésben vagy.

```
ALT:∞    → minden légtér látszik (alap)
ALT:40m  → csak azok a légterek látszanak, amelyek 0–40 m között érintkeznek
ALT:120m → EU Open kategória maximális magassága
```

**Lépések (koppintásra vált):** `ALT:∞` → `ALT:30m` → `ALT:40m` → `ALT:50m` → `ALT:60m` → `ALT:80m` → `ALT:100m` → `ALT:120m` → vissza `ALT:∞`

**Működési logika:**
- Ha egy légtér GND (talajszint) referenciájú és az **alsó határa > tervezett magasság** → eltűnik (nem ütközöl vele)
- Ha a légtér GND-től indul (pl. CTR 0–2500 ft), és 40 m-en repülsz → megjelenik (ütközés van)
- MSL (tengerszint) vagy FL (flight level) referenciájú légterek mindig látszanak – az AGL-konverzió terepmagasság nélkül nem megbízható

> **Példa – Budapest CTR:** A CTR GND-től indul. Ha 40 m-en tervezel repülni és a CTR-t látod, engedélyt kell kérned. Ha a szűrőt 40 m-re állítod és a CTR eltűnik, az azt jelenti, hogy annak alsó határa 40 m felett van – nem ütközöl vele. (A Budapest CTR esetén GND-ről indul, tehát 40 m-en is látszik.)

**A szűrő megmarad** a réteg ki-be kapcsolásakor – nem kell újra beállítani.

---

### ZÓN – Területhasználati zónák

Drónozás szempontjából releváns területbesorolásokat mutat. **Nem közigazgatási határ** – hanem a tényleges területhasználatot jelöli.

**Szín kód:**
- 🔴 **Piros** – lakóterület (residential), kereskedelmi (commercial/retail) → ⚠ lakott területi korlátozások vizsgálandók
- ⬛ **Szürke** – ipari terület (industrial)
- 🟣 **Lila** – repülőtér (aerodrome) → 🚫 CTR körzet, engedély nélkül tilos
- 🔴 **Sötétpiros** – katonai terület (military) → 🚫 tiltott zóna

#### A „lakott terület" jogi értelmezése (2024. dec. 18. óta)

Az 1995. évi XCVII. törvény (Lt.) módosítása szerint:

> **Lakott terület** = az UAS-műveletbe be nem vont személy vagy személyek **életvitelszerű tartózkodási helye** (lakás, ház).

Ez azt jelenti, hogy **eseti légtér** szükséges, ha a repülés érinti azt a helyszínt, ahol be nem vont személyek életvitelszerűen tartózkodnak. A ZÓN réteg lakóterületi jelölése jó kiindulás, de **a végső döntés a pilóta felelőssége** az adott helyszín alapján.

**Gyors referencia-táblázat:**

| Helyszín | Eseti légtér kell? |
|----------|--------------------|
| Lakóépületek felett / mellett | ✅ igen |
| Sétálóutca, belváros | ✅ igen |
| Üdülőövezet (vízi házak, nyaralók) | ✅ igen |
| Tóparti strand vízi házakkal és azok fölé repüléssel | ✅ igen |
| Közpark (lakóépület nélkül) | ❌ nem |
| Sportpálya, iskolaudvar | ❌ nem |
| Tanyavilági terület, ahol nincs lakóépület az útvonal alatt | ❌ nem |
| Zárt ipari létesítmény felmérése | ❌ nem |
| Belterületi erdő | ❌ nem |
| Közigazgatásilag lakott terület, ahol nincs ház | ❌ nem |

> **Megjegyzés:** A ZÓN réteg az OSM `landuse` tagjein alapul. A piros lakóterületi jelölés segít azonosítani a valószínűleg lakott területeket, de az OSM adat nem teljes, és a tényleges helyszínt az operátornak kell értékelnie. Bizonytalanság esetén szigorúbb értelmezést kell alkalmazni és eseti légteret kell kérvényezni.

---

## 💾 Repülési Terv Mentése és Betöltése

A DroneFly az elkészített repülési terveket a tableten helyben tudja menteni és visszatölteni. Ez az **offline, szerkeszthető mentési** funkció — nem a CSV/KMZ exportálás.

### Az aktuális terv neve

A panel tetején mindig látható, melyik terv van betöltve:
- **Dőlt szürke „Mentetlen terv"** – a terv még nincs fájlba mentve
- **Fehér szöveg** (pl. `Eszaki_tabla_2026-04-08`) – a betöltött/mentett terv neve

### Mit ment el?

| Adat | Mentve? |
|------|---------|
| Poligon csúcsai | ✅ igen |
| Start / Home pont | ✅ igen (ha meg van adva) |
| Összes beállítás (GSD, sidelap, frontlap, sebesség, szög, offset) | ✅ igen |
| Domborzatkövetés be/ki, drón profil | ✅ igen |
| Akadályok (pozíció, sugár, magasság) | ✅ igen |
| Generált waypontok | ❌ nem kell — betöltés után automatikusan újragenerálódik |

### Új terv

Az **„Új terv"** gomb teljesen nullázza az állapotot (rajz, akadályok, beállítások).

- **Ha nincs rajzolt terület:** azonnal töröl, dialog nélkül
- **Ha van mentett terv:** dialog jelenik meg:
  - `[Mentés és bezárás]` – elmenti, majd törli
  - `[Bezárás mentés nélkül]` – azonnal töröl
  - `[Mégse]` – visszalép
- **Ha mentetlen rajz van:** egyszerű megerősítő dialog (`[Igen, új terv]` / `[Mégse]`)

### Terv mentése (SAVE)

A **„Mentés"** gomb viselkedése az aktuális állapottól függ:

- **Ha a terv már el van mentve:** közvetlenül felülírja a fájlt (dialog nélkül)
- **Ha mentetlen terv:** SAVE AS dialóg nyílik (lásd alább)

### Mentés másként (SAVE AS)

A **„Mentés másként"** gomb mindig új nevet kér:

1. Írd be a terv nevét (pl. „Északi tábla 2026-04-08")
2. Nyomd meg a **„Mentés"** gombot

A fájl elmentődik a tablet tárhelyére:
```
/sdcard/Android/data/com.dronefly.app/files/missions/Eszaki_tabla_2026-04-08.dronefly.json
```

> **Tipp:** Feltöltés előtt érdemes elmenteni a tervet – így az akkucsere utáni folytatási pont is megmarad tablet újraindítás esetén (lásd: Misszió folytatása).

> **Automatikus mentés:** Ha feltöltöd a missziót és a terv még nincs mentve, az app automatikusan elmenti `auto_DÁTUM` névvel. A névtábla frissül, és utána a "Mentés" gomb már felülírja ezt a fájlt.

### Terv betöltése

1. Nyomd meg a **„Terv betöltése"** gombot
2. Megjelenik az elmentett tervek listája (legújabb elöl), mentési dátummal
3. Koppints a betölteni kívánt tervre
4. Ha van aktuálisan rajzolt terület, megerősítő kérdés jelenik meg
5. Betöltés után az app:
   - Visszaállítja a poligont és az összes beállítást
   - Visszahelyezi az akadályokat a térképre
   - **Automatikusan újragenerálja** a misszió útvonalát
   - Visszatölti az esetleges mentett folytatási pontot (akkucsere utánra)
   - A térkép a betöltött területre ugrik

> **Fontos:** A betöltés felülírja a jelenlegi rajzot és beállításokat!

### Különbség a CSV/KMZ exportálástól

| | Terv mentés (JSON) | CSV / KMZ export |
|---|---|---|
| Cél | Újraszerkesztés | Litchi / DJI Pilot kompatibilitás |
| Poligon mentve | ✅ | ❌ |
| Beállítások mentve | ✅ | ❌ |
| Akadályok mentve | ✅ | ❌ |
| Szerkeszthető visszatöltés | ✅ | ❌ |
| Más appban megnyitható | ❌ | ✅ |

---

## 💾 Exportálás és Importálás

### Exportálás

**"Exportálás (CSV / KMZ)"** gomb → válaszd ki a formátumot:

#### Litchi CSV

Kompatibilis a **Litchi app**-pal (iOS/Android). Egyszerű szövegfájl, waypontok koordinátákkal.

```
latitude,longitude,altitude(m),heading(deg),curvesize(m),rotationdir,...
47.123456,19.123456,80.0,-1,0,0,...
```

Megnyitható: Litchi Mission Hub (webes felület) → feltöltés a drónra

#### KMZ

Kompatibilis a **DJI Pilot** és **Google Earth** applikációkkal. ZIP-tömörített KML fájl.

### Importálás (Litchi CSV)

**"CSV importálása"** gomb → válaszd ki a fájlt → az app megjeleníti a waypontokat a térképen.

> Exportált CSV-t visszaimportálva ellenőrizheted az útvonalat, vagy tovább módosíthatod.

---

## 📷 Kamera Beállítások

A **"Kamera beállítások"** fejlécre koppintva kinyílik/összecsukódik a szekció (▼/▲).

### Auto mód (ajánlott)

Alapértelmezetten az app **Auto** módban hagyja a kamerát – a drón automatikusan optimalizálja a kamerabeállításokat.

> **Intervallum fotózás:** Survey repülésnél az app automatikusan beállítja a kamerát intervallum módra. A fotózási időköz: `talajtávolság / repülési sebesség` (minimum 2 másodperc). Ezt nem szükséges kézzel beállítani — az indításkor automatikusan aktiválódik.

### Manuális mód

Kapcsold ki az **Auto** kapcsolót a következő beállításokhoz:

| Beállítás | Leírás |
|-----------|--------|
| **Fotó mód** | Single / HDR / Burst |
| **ISO** | 100–3200 |
| **Záridő** | 1/100 – 1/2000 s |
| **Fehéregyensúly** | Auto / Felhős / Nap / stb. |
| **Fájlformátum** | JPEG / RAW / JPEG+RAW |

**Ajánlott manuális beállítás survey-hoz:**
- ISO: 100
- Záridő: 1/800 s (elmozdulás-mentes)
- Fehéregyensúly: Felhős (konzisztens szín)
- Fájlformátum: JPEG+RAW

---

## 📹 Élő Kamera Feed (CAM)

A DroneFly a DJI Go 4-höz hasonlóan megjeleníti a drón kamerájának élő képét közvetlenül az appban.

### Bekapcsolás

1. Nyomd meg a **`CAM`** gombot a bal felső sarokban
   → A gomb zöldre vált
   → A bal alsó sarokban megjelenik a kamera ablak egy **`● LIVE`** fejléccel

2. A kép automatikusan megjelenik, amint a drón csatlakoztatva van

### Kikapcsolás

- A **`CAM`** gomb újbóli megnyomásával
- Vagy az ablak jobb felső sarkában lévő **`✕`** gombbal

### Tap-to-Expose (expozíció beállítása)

A DJI Go 4-hez hasonlóan az expozíciót a kamera képre koppintva beállíthatod az érintett területre.

1. **Koppints a kamera képre** – a drón arra a területre állítja be az expozíciót
2. Egy **fehér fókuszgyűrű** jelenik meg az érintés helyén, majd 1 másodperc után eltűnik

```
Túlexponált ég esetén:
  → Koppints az égre → drón az égre exponál → talaj sötétebb lesz
  → Vagy koppints a növényzetre → talaj részletek megjelennek
```

> **Megjegyzés – P4P v1 lencse:** A Phantom 4 Pro v1 **fix fókuszú** (nem autofókusz).
> A koppintás az **expozíciós mérési pontot** változtatja, nem a fókuszélességet.
> Ezt a DJI szintén "tap-to-focus"-nak nevezi, de P4P-n valójában tap-to-expose.

### Az ablak elhelyezkedése

```
256 × 144 dp méretű ablak, bal alsó sarokba rögzítve.
Fejléc sáv (24 dp): "● LIVE" piros felirat + ✕ bezáró gomb
A kép mögött a térkép továbbra is aktív és érintéssel kezelhető.
```

### Pause / Destroy

- Ha az app háttérbe kerül (`onPause`): a feed **automatikusan leáll**
- Ha az app újra aktív: a `CAM` gombot újra meg kell nyomni

---

## 📊 Státuszsáv

A képernyő tetején lebegő sáv valós idejű adatokat mutat, 2 másodpercenként frissül.

```
┌──────────────────────────────────────────────────────────────┐
│ Phantom 4 Pro │ RC: OK 87% │ SAT: 14 H │ AKKU: 92% │ TAB: 78% │
└──────────────────────────────────────────────────────────────┘
```

| Mező | Leírás | Zöld | Narancs | Piros |
|------|--------|------|---------|-------|
| **DRON** | Drón neve (pl. "Phantom 4 Pro") | Csatlakozva | – | Nincs |
| **RC** | RC vezérlő kapcsolat | OK | – | Nincs |
| **RC %** | RC vezérlő akkumulátor töltöttség | ≥40% | 20–40% | <20% |
| **SAT** | GPS műholdak száma | ≥10 | 6–9 | <6 |
| **AKKU** | Drón akkumulátor töltöttség | ≥40% | 20–40% | <20% |
| **TAB** | Crystal Sky tablet akkumulátor | ≥40% | 20–40% | <20% |

**"H"** a SAT szám után = Home pont a drón GPS-be programozva (biztonságos RTH)

> **Megjegyzés:** Az RC és drón akkumulátor értékek DJI MSDK-n keresztül kérnek le
> (csak valódi csatlakozáskor aktívak). Az RC % az RC-ből csatlakoztatott tablet
> esetén jelenik meg; az RC saját kijelzőjén is látható a töltöttség.

---

## 🚁 Drón Profilok

A drón profil meghatározza a kamera paramétereit, amelyek alapján a GSD ↔ magasság konverzió, a sávköz és a fotóköz számítódik.

### Profil kiválasztása

A panel tetején lévő **"Drón"** legördülő listából választható.

### Elérhető profilok

| Profil | Szenzor (mm) | Fókusz (mm) | Felbontás |
|--------|-------------|-------------|-----------|
| P4P v1 | 13.2 × 8.8 | 8.8 | 20 MP |
| Mavic 2 Pro | 13.2 × 8.8 | 10.3 | 20 MP |
| Mini 3 Pro | 9.6 × 7.2 | 8.7 | 48 MP |
| Air 2S | 13.2 × 8.8 | 12.4 | 20 MP |
| Mini 4 Pro | 9.6 × 7.2 | 8.7 | 48 MP |
| Mavic 3 Classic | 17.3 × 13.0 | 12.3 | 20 MP |

> Ha a drónod nincs a listában: válaszd a legközelebbi szenzorméretű profilt,
> vagy a fejlesztőhöz fordulj a profil hozzáadásáért.

---

## 🔋 Akkumulátorcsere – Nagy Területek

A DJI MSDK v4 legfeljebb **99 waypointot** kezel egyszerre. Nagy területeken az app automatikusan szegmensekre bontja a missziót.

### Szegmensek kezelése

Ha a statisztikában `(2 szegmens)` vagy több látható:

```
Terület: 12.50 ha | 187 pont (2 szegmens)
```

**Munkafolyamat:**

1. **1. szegmens** → Feltöltés → START → drón repüli az 1. szegmenst → RTH
2. **Akkumulátorcsere**
3. **2. szegmens** → Feltöltés → START → drón repüli a 2. szegmenst → RTH
4. Ismételd a szükséges szegmensszámig

> Az app megjegyzi az aktuális szegmenset – feltöltéskor automatikusan a következőt tölti fel.

### Egy szegmens hány ha?

Sávköz (~44 m, 75% sidelap, 3 cm GSD esetén):

| Terület | Waypont szám | Szegmensek |
|---------|-------------|-----------|
| ~2 ha | ~60 | 1 |
| ~5 ha | ~150 | 2 |
| ~10 ha | ~300 | 4 |
| ~20 ha | ~600 | 7 |

---

## 🔧 Hibaelhárítás

### "Drón: nem csatlakoztatva" – státuszsáv piros

1. Ellenőrizd az USB-C kábelt (Crystal Sky ↔ RC)
2. Kapcsold ki és be a drónt
3. Nyomd meg a Crystal Sky Home gombját, majd nyisd újra az appot
4. Ha a DJI Go / Pilot app működik, az MSDK is fog

### A "Feltöltés" gomb szürke / nem aktív

- **Nincs generált misszió:** rajzolj legalább 3 pontot
- **Drón nincs csatlakoztatva:** ellenőrizd a kapcsolatot
- **Misszió fut:** előbb állítsd le

### GPS gomb nem működik / "GPS pozíció nem elérhető"

- A **GPS feliratú gomb a bal felső sarokban mindig látható** – nem csak GPS jelnél jelenik meg; ha koppintasz rá, az aktuális pozícióra ugrik a térkép
- Ha „GPS pozíció még nem elérhető" üzenetet kapsz: nincs még GPS fix → várj 30–60 másodpercet, lépj ki szabad területre (beltéren a GPS gyenge)
- Ellenőrizd, hogy a helymeghatározás engedélyezve van-e (Crystal Sky Settings → Location)
- Ha a drón csatlakoztatva van, a gomb a drón GPS pozícióját használja (pontosabb)
- A toast megmutatja a forrást: pl. „Drón GPS" vagy „Tablet GPS (±8 m)"

### Térkép nem tölt be (fehér képernyő / szürke csempék)

- Ellenőrizd a WiFi kapcsolatot (Crystal Sky Settings → Wi-Fi)
- ESRI műhold tiles-t használ az app (nem OpenStreetMap) → más szerver, de szintén online
- Ha offline kell: előre töltsd le az aktuális nézetet a **SAT/MAP gomb hosszú nyomásával** (zoom 14–17, ~2 000–3 000 csempe)

### Az útvonal nem fedi be a teljes területet

- Kevés a sarokpont (komplikált alaknál több pontot tegyél le)
- GSD túl nagy → sávköz is nagy → kevés sáv → lyukak a széleken
- Növeld az **Offset** értéket (5–15 m-rel)

### "Minden waypointot akadály blokkol!" hiba

- Az akadály sugara túl nagy
- Csökkentsd a sugarat, vagy töröld az akadályt
- Növeld a repülési magasságot (kisebb GSD → magasabb magasság → átrepül a fa felett)

### A kamera ablak fekete / nem jelenik meg a kép

1. Ellenőrizd, hogy a drón **ténylegesen csatlakoztatva** van-e (DRON: P4P a státuszsávban)
2. A CAM gomb megnyomása után várj 2–3 másodpercet — a feed inicializálás pár mp-et vesz igénybe
3. Ha továbbra is fekete: kapcsold ki a CAM gombot, várd meg a `● LIVE` felirat eltűnését, majd nyomj CAM-et újra
4. Ha az app az ADB kábellel van csatlakoztatva laptophoz: az RC kábel NEM csatlakoztatható egyszerre (egy USB port!) — húzd ki az ADB kábelt, csatlakoztasd az RC-t, indítsd újra az appot

### A réteg gomb megnyomása után nem jelenik meg semmi (N2K / ZÓN)

1. Ellenőrizd az internet-kapcsolatot (WiFi ikon a Crystal Sky tálcán)
2. Várj 30–60 másodpercet – az Overpass szerver olykor lassú
3. Győződj meg, hogy a zoom szint ≥ 10 (az app nem indít lekérést nagyon kiszoomolt nézeten)
4. Ne kattints többször gyorsan – a gomb le van tiltva betöltés közben, de ez látható: a gomb szövege `N2K...` / `ZÓN...`
5. Ha a gomb szövege visszavált és a réteg halvány → azon a területen nincs OSM-adat

### Az LGT réteg problémái

- Ha **nagyon nagy területen** aktiválod (egész ország kiszoomolva), csökkentsd a zoom szintet és kapcsold ki, majd nagyíts be a repülési területre és kapcsold be újra
- A **TMA** (Terminal Control Area) típusú légterek automatikusan ki vannak zárva – ezek hatalmas polygonok (pl. Budapest TMA az egész ország felett), megjelenítésük nem hasznos
- Ha az `ALT:Xm` szűrő aktív és kevés légtér jelenik meg: ez helyes működés – a szűrő kizárja az ütközést nem okozó légtereket
- Ha **minden légtér látszik** beállított szűrő esetén is: az azért van, mert azok GND-től indulnak (pl. CTR 0 m–762 m) – bármely magasságon ütközés van, ez helyes viselkedés

### „⚠️ Nincs SD kártya!" figyelmeztetés jelenik meg START után

Az app misszió indítás előtt automatikusan ellenőrzi az SD kártya jelenlétét.

- **Mit jelent:** A drón kamerájában nem található memóriakártya
- **Megoldás:** Kapcsold ki a drónt, helyezd be az SD kártyát a kameraházba, kapcsold vissza, majd nyomj START-ot újra
- **Ha kártya nélkül kell folytatni:** A dialog „Folytatás kártya nélkül" gombjával elindítható a misszió — de a fotók elvesznek (a P4P v1-nek nincs belső memóriája fotók számára)

> ⚠️ Az ellenőrzés MSDK-n keresztül történik, ezért csak csatlakoztatott drón esetén aktív. Mindig ellenőrizd fizikailag is a kártya jelenlétét felszállás előtt!

### A gimbal nem fordul le misszió indításkor

Az app automatikusan megpróbálja -90°-ra forgatni a gimbalt a misszió elindítása előtt.

- Ellenőrizd, hogy a gimbal nincs-e fizikailag blokkolva (leszállótalp, védőburkolat)
- Ha „Gimbal beállítása…" üzenet után sem fordul le: forgasd le kézzel a DJI Go 4-ben, majd indítsd el a missziót
- A gimbal hiba **nem blokkolja** a missziót — az indítás folytatódik gimbal hiba esetén is

### Az akadály beviteli mezőben a szám nem látszik

- Érintsd meg a mezőt → az összes tartalom kijelölt (kékkel) lesz
- Egyszerűen gépeld be az új értéket → felülírja az előzőt

---

## 🗺️ Offline Térkép

Az app **automatikusan offline módba vált**, ha a Crystal Sky-on nincs internet-kapcsolat. Ebben az esetben:
- Csak a korábban betöltött (gyorsítótárazott) csempék látszanak
- A réteg gombok (N2K, LGT, ZÓN) letiltódnak – koppintásra hibaüzenet jelenik meg, nem kísérel meg sikertelen API hívást
- A térkép nem fagy be várakozástól

### Terület előre letöltése (otthon, WiFi-n)

1. Navigálj a repülési területre (keresd meg a helyszínt)
2. Nyomj **hosszan** a `SAT` vagy `MAP` gombra
3. A dialog megmutatja a csempék számát (~2 000–3 000 a jelenlegi nézethez, zoom 14–17)
4. Nyomj **„Letöltés"** → az app a háttérben letölti a csempéket

> 💡 Zoom 14–17 elegendő terepi használathoz: zoom 14 = 10 m/px (áttekintő), zoom 17 = 1.2 m/px (tábla-szintű részlet). Zoom 18 szándékosan ki van hagyva – feleslegesen sok csempét igényelne.

> ⚠️ A letöltés WiFi-t igényel. Terepen (RSMA/4G nélkül) nem végezhető.

---

## 📸 Képernyőkép és Videórögzítés (REC)

A bal felső gombsorban a **`REC`** gomb (CAM alatt) az app képernyőjét rögzíti.

### Képernyőkép (rövid nyomás)

1. Koppints egyszer a `REC` gombra
2. A gomb röviden **zölden villan**, majd toast jelenik meg: _„📷 Mentve: screenshot_20260417_143022.png"_
3. A fájl helye: **`/sdcard/Pictures/DroneFly/`**

### Videórögzítés (hosszú nyomás)

1. Nyomj **hosszan** a `REC` gombra
2. Az Android engedélykérő ablak jelenik meg: _„DroneFly képernyőrögzítést szeretne megkezdeni"_ → **Engedélyezés**
   - Ez csak az első indításkor jelenik meg
3. A `REC` gomb **pirosan villogni kezd** (600 ms), szövege `■` → felvétel folyamatban
4. A rögzítés leállításához nyomj ismét **hosszan** a gombra
5. Toast jelenik meg: _„⏹ Mentve: video_20260417_143022.mp4"_
6. A fájl helye: **`/sdcard/Pictures/DroneFly/`**

### Fájlok megkeresése a tableten

**Crystal Sky fájlkezelőből:**
- Nyisd meg a Crystal Sky beépített fájlkezelőjét (ha van)
- Navigálj: `Belső tároló → Pictures → DroneFly`

**ADB-vel (USB kábel csatlakoztatva a laptophoz):**
```
adb.exe pull /sdcard/Pictures/DroneFly/ C:\Users\zsigm\Desktop\DroneFly_felvetelek\
```

**Galéria appból:**
- A `MediaScannerConnection` automatikusan indexeli a fájlokat → a galéria appban `DroneFly` albumként kell megjelennie

> ⚠️ Megjegyzés: A DJI kamera PiP élő képe (TextureView) DJI belső felület — a videórögzítésbe belekerül, de a statikus képernyőképbe esetleg nem. A térkép, gombok és telemetria adatok mindig belekerülnek mindkettőbe.

---

## 💡 Tippek és Trükkök

### Legjobb GSD mezőgazdasági célra

| Cél | Ajánlott GSD | Magasság (P4P v1) |
|-----|-------------|------------------|
| Növény egészség (NDVI) | 3–5 cm/px | 118–196 m |
| Tőszámlálás | 0.5–1.5 cm/px | 20–60 m |
| Terület felmérés | 2–3 cm/px | 78–118 m |
| Gyors áttekintés | 5–10 cm/px | 196–391 m |

### Repülési irány optimalizálása

Állítsd a repülési irányt a terület **hosszabb tengelyének** irányára – ezzel csökkented a fordulások számát és a repülési időt akár 20–30%-kal.

### Offset ajánlás

Mindig használj **legalább 5–10 m offset-et**, hogy a terület szélei is biztosan lefedésre kerüljenek. Az app ajánlott értéke általában 15–25 m körül van.

### Több akadály jelölése egymás után

Az akadály mód **bekapcsolva marad** minden koppintás után. Több fát egymás után is tudsz jelölni anélkül, hogy a gombot újra meg kellene nyomni. Nyomd meg ismét a gombot, ha végzel.

### Kamera + térkép együtt repülés közben

A kamera ablak (bal alsó sarok) és a térkép **egyszerre látható**. Repülés közben:
- A térképen követhető a drón pozíciója (narancs útvonal)
- A kamera ablakban látható, amit a drón kamerája lát
- A jobb oldali panel elrejthető a `»` gombbal, így mindkettő jobban látható

### Panel elrejtése repülés közben

A `»` gombbal rejtsd el a panelt – így **a teljes képernyő a térkép lesz**, és jobban látod a drón pozícióját az útvonal mentén.

### Manuális "Újragenerálás" mikor kell?

Automatikus újragenerálás minden pont-módosításkor fut. Az **"Újragenerálás (beállításokkal)"** gomb csak akkor szükséges:
- Ha **Domborzatkövetést** szeretnél alkalmazni (API hívás)
- Ha a statisztika nem frissült valamiért

### Szegmensek sorrendbe rakása

A missziógenerátor **balról jobbra, fentről le** halad. Ha különböző napokon repülsz szegmenseket, ellenőrizd a statisztikában, hogy melyik szegmens melyik területet fedi.

---

## 📞 Támogatás

**GitHub repository:** [github.com/CsabaZsee77/DroneFly](https://github.com/CsabaZsee77/DroneFly)

**Hibabejelentés:** GitHub Issues → részletes leírás + Crystal Sky firmware verzió + hibás viselkedés lépései

**Célplatform:** DJI Crystal Sky (Android 5.1, API 22)

---

*DroneFly GCS – Mezőgazdasági drón misszió tervező*
*Készítette: CsabaZsee77 | 2026*
