# ✈️ DroneFly GCS – Felhasználói Útmutató

**Verzió:** v1.2.0
**Utolsó frissítés:** 2026-04-05
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
13. [Exportálás és Importálás](#-exportálás-és-importálás)
14. [Kamera Beállítások](#-kamera-beállítások)
15. [Státuszsáv](#-státuszsáv)
16. [Drón Profilok](#-drón-profilok)
17. [Akkumulátorcsere – Nagy Területek](#-akkumulátorcsere--nagy-területek)
18. [Hibaelhárítás](#-hibaelhárítás)
19. [Tippek és Trükkök](#-tippek-és-trükkök)

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
✅ **Exportálás** – Litchi CSV és KMZ formátum
✅ **Feltöltés és indítás** – Közvetlen DJI MSDK v4 feltöltés a csatlakoztatott drónra
✅ **Akkumulátorcsere kezelés** – 99 waypontnál nagyobb misszió automatikus szegmensekre bontása

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
5. **Feltöltés** gomb → várd meg a megerősítő üzenetet
6. **START** gomb → a drón elindul

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
│ [GPS] [MAP]  ┌──── Státuszsáv ────────────────┐ ║ P │
│              │ DRON: P4P│RC: OK 85%│SAT:14H│...│ ║ A │
│              └───────────────────────────────┘  ║ N │
│                                                  ║ E │
│                                                  ║ L │
│         T É R K É P                              ║   │
│                                                  ║ » │
│    [Terület rajzolva]                             ║   │
│    [Narancs útvonal]                              │   │
│    [Piros akadály körök]                          │   │
└─────────────────────────────────────────────────────┘
```

**Bal felső sarok:**
- `GPS` – térkép a GPS pozícióra ugrik
- `SAT/MAP` – műhold ↔ utcatérkép váltás

**Teteje (lebegő státuszsáv):** drón kapcsolat, RC, akkumulátorok, GPS

**Jobb oldal (csúsztatható panel):**
- `»` gomb – panel elcsúszik (több hely a térképnek)
- `«` gomb – panel visszanyílik

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
- ✅ GPS műholdak: min. 10 (SAT: 10 H – a "H" = Home pont beállítva)
- ✅ Misszió generálva (narancs útvonal látható)

### Feltöltés

1. Nyomd meg a **"Feltöltés"** gombot
2. Megerősítő dialog jelenik meg:
   ```
   Misszió feltöltése
   47 waypoint feltöltése a drónra?
   [Feltöltés]  [Mégse]
   ```
3. Várd meg: *"Feltöltve! Ellenőrizd a drónt, majd nyomj START-ot."*

### Start

1. Nyomd meg a **"START"** gombot (narancs, nagy)
2. Megerősítő dialog:
   ```
   Repülés indítása
   A drón elindítja a missziót. Biztos vagy benne?
   [START]  [Mégse]
   ```
3. Az app először alkalmazza a kamera beállításokat, majd indítja a missziót
4. A **Szünet** és **Stop** gombok aktívvá válnak

> ⚠️ **Biztonsági ellenőrzőlista START előtt:**
> - Propellerek rögzítve?
> - Légitér szabad?
> - Visual line of sight biztosított?
> - RC vezérlő kéznél?

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

## 📊 Státuszsáv

A képernyő tetején lebegő sáv valós idejű adatokat mutat, 2 másodpercenként frissül.

```
┌─────────────────────────────────────────────────────────┐
│ P4P v1 │ RC: OK 85% │ SAT: 14 H │ AKKU: 92% │ TAB: 78% │
└─────────────────────────────────────────────────────────┘
```

| Mező | Zöld | Narancs | Piros |
|------|------|---------|-------|
| **DRON** | Csatlakozva | – | Nincs csatlakozva |
| **RC** | OK | – | Nincs |
| **RC akku %** | ≥40% | 20–40% | <20% |
| **SAT** | ≥10 műhold | 6–9 | <6 |
| **AKKU (drón)** | ≥40% | 20–40% | <20% |
| **TAB (tablet)** | ≥40% | 20–40% | <20% |

**"H"** a SAT szám után = Home pont a drón GPS-be programozva (biztonságos RTH)

> **Fontos:** A drón akkumulátor értékek DJI MSDK-n keresztül kérnek le;
> csak valódi drón csatlakozáskor aktívak.

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

- Ellenőrizd, hogy a helymeghatározás engedélyezve van-e a Crystal Sky beállításokban
- Lépj ki szabad területre (beltéren a GPS gyenge)
- Várj 30–60 másodpercet, amíg a tablet GPS-e lock-ol

### Térkép nem tölt be (fehér képernyő / szürke csempék)

- Ellenőrizd a WiFi kapcsolatot (Crystal Sky Settings → Wi-Fi)
- ESRI műhold tiles-t használ az app (nem OpenStreetMap) → más szerver, de szintén online
- Ha offline kell: előre gyorsítótárazd az adott területet

### Az útvonal nem fedi be a teljes területet

- Kevés a sarokpont (komplikált alaknál több pontot tegyél le)
- GSD túl nagy → sávköz is nagy → kevés sáv → lyukak a széleken
- Növeld az **Offset** értéket (5–15 m-rel)

### "Minden waypointot akadály blokkol!" hiba

- Az akadály sugara túl nagy
- Csökkentsd a sugarat, vagy töröld az akadályt
- Növeld a repülési magasságot (kisebb GSD → magasabb magasság → átrepül a fa felett)

### Az akadály beviteli mezőben a szám nem látszik

- Érintsd meg a mezőt → az összes tartalom kijelölt (kékkel) lesz
- Egyszerűen gépeld be az új értéket → felülírja az előzőt

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
