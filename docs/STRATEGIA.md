# DroneFly — Termék- és Üzleti Stratégia

**Készült:** 2026-04-19  
**Státusz:** Élő dokumentum — a 2026. április folyamán lefolytatott stratégiai elemzés alapján.  
**Összefoglalás:** Ez a dokumentum rögzíti az üzleti modellre, piacra, versenytársakra és termékstratégiára vonatkozó döntéseket.

---

## 1. Termékpozicionálás

### A DroneFly nem általános célú drón app

A piac már rendelkezik általános GCS appokkal (Litchi, Dronelink). A DroneFly **specializált** termék:

> **"A legjobb agro survey app Crystal Sky + Phantom 4 kombinációra — és az egyetlen, ami offline, dedikált hardverre készült."**

Ez nem gyengeség — ez védett pozíció. Ahol a versenytársak gyengék:
- **Dronelink:** Crystal Sky-on összeomlik (Google Play Services hiba), internet-függő, előfizetéses
- **Litchi:** Agro grid funkciója 2024 végén jelent meg béta állapotban, GSD kalkulátor nélkül
- **DJI Go 4:** Nem tud profi agro survey-t — nincs polygon grid, GSD kalkulátor, overlap tervező

---

## 2. Termékcsalád

### 2.1 DroneFly Agro — Crystal Sky Edition (v1.x)

**Célcsoport:** Phantom 4 Pro / Advanced / v2 + DJI Crystal Sky kombót használó pilóták  
**Pozíció:** Prémium niche, monopol helyzet  
**Ár:** **$59–79** (egyszeri, Play Store / sideload)  
**Platform:** Crystal Sky (Android 5.1, API 22), MSDK v4

**Narratíva:** A DJI 2025 júniusában megszüntette a Phantom 4 sorozat támogatását. Ezek a pilóták $3 000+ beruházással rendelkeznek (Crystal Sky ~$1 500 + Phantom ~$1 500), és az egyetlen szoftverük (DJI Go 4) nem tud profi agro survey-t. A DroneFly ezt a hiányt tölti be — pontosan arra a hardverre, amire a felhasználó már költött.

**Megjegyzés a Play Store-ról:** Crystal Sky-ra a sideload természetes (a DJI ökoszisztémában elfogadott). Play Store publikálás is lehetséges (a Crystal Sky-on van Play Store hozzáférés), de a fő terjesztési csatorna a DroneFly weboldaláról való közvetlen APK letöltés is lehet.

---

### 2.2 DroneFly Agro — Mobile Edition (v2.x, jövőbeli)

**Célcsoport:** Modern DJI drón (Mavic 3, Air 2S, Mini 4 Pro) + RC-N1/N2 + saját Android telefon/tablet  
**Pozíció:** Szélesebb agro piac, fő növekedési motor  
**Ár:** **$29–39** (egyszeri, Play Store)  
**Platform:** Android 7+ (minSdk 24), MSDK v5, Kotlin backend réteg

**Fejlesztési feltétel:** MSDK v5 port — M04 réteg (MissionUploader, DJIHelper, DroneVideoWidget) újraírása. A grid engine (M02), export (M03), térkép UI (M01) változatlan marad.

**Piac mérete:** ~50 000–100 000 agro érdekelt pilóta EU-ban, akik modern drónnal dolgoznak.

---

### 2.3 DroneFly Cine (v3.x, hosszú távú)

**Célcsoport:** Filmesek, content creatorok, real estate videósok  
**Pozíció:** Litchi-nél jobb cine UX, timeline-alapú tervezés  
**Ár:** **$19–25** (egyszeri)  
**Platform:** Android 7+, MSDK v4 + v5

**Kulcsfunkciók:** Per-waypoint gimbal keyframe szerkesztő, spline görbe preview, POI Orbit, Panoráma, Mission Hub közösségi megosztás

---

### 2.4 Mission Hub (webes, jövőbeli)

**Célcsoport:** Mindkét felhasználói kör  
**Modell:** Freemium — alapfunkciók ingyenes, prémium előfizetés  
**Ár:** **$4–8/hó** (egyéni), **$15–30/hó** (agro analytics, team sharing)

**Funkciók:**
- Repülési tervek tárolása, szinkronizálás
- Közösségi misszió megosztás (Litchi Mission Hub modell)
- Agro analytics: telemetria naplók, területi riportok, PDF export
- Sablonkönyvtár régió és drón típus szerint

---

## 3. Árazási stratégia

| Termék | Ár | Modell | Indoklás |
|--------|-----|--------|----------|
| Agro Crystal Sky Edition | $69 | Egyszeri | Monopol pozíció, prémium niche |
| Agro Mobile Edition | $35 | Egyszeri | Litchi-vel versenyképes |
| Cine Edition | $22 | Egyszeri | Litchi árán (piaci referencia) |
| Pro Bundle (Agro + Cine) | $69 | Egyszeri | Bundle kedvezmény |
| Mission Hub Personal | $6/hó | Előfizetés | Ismétlődő bevétel |
| Mission Hub Agro Pro | $20/hó | Előfizetés | Professzionális agro |

**DJI EULA:** A standard Developer License engedélyezi a fizetős appokat — nincs Enterprise Partner kötelezettség. A Litchi, DroneDeploy, Pix4Dcapture mind standard licenccel értékesít.

---

## 4. Versenytárs összefoglaló

### Dronelink
- Előfizetéses ($20–100/hó), internet-függő
- Crystal Sky-on **nem működik** (Google Play Services crash)
- Komplex UI, meredek tanulási görbe
- Részletes elemzés: [`DRONELINK_OSSZEHASONLITAS.md`](DRONELINK_OSSZEHASONLITAS.md)

### Litchi
- Egyszeri vételár (~$25), Crystal Sky-on működik
- Agro grid funkciója 2024 végén jelent meg béta állapotban — GSD kalkulátor, overlap tervező nélkül
- Erős cine/POI/orbit funkciók — DroneFly Cine modul referencia
- ~360 000 letöltés, ~$9M+ becsült lifetime bevétel — az üzleti modell validált
- Részletes elemzés: [`LITCHI_OSSZEHASONLITAS.md`](LITCHI_OSSZEHASONLITAS.md)

### DJI Go 4
- Ingyenes, de nem tud profi agro survey-t
- Phantom 4 támogatás megszűnt 2025. június 1.

---

## 5. Piaci méretbecslés

| Szegmens | Becsült méret | DroneFly termék |
|----------|--------------|-----------------|
| Crystal Sky + P4 agro pilóták (globális) | 1 500–4 000 fő | Agro Crystal Sky Ed. |
| Modern drón agro EU | 50 000–100 000 fő | Agro Mobile Ed. |
| DJI drón cine/filmesek globális | 500 000+ fő | Cine Ed. |

**Bevételi szcenárió (5 év):**

| Forrás | Felhasználók | Ár | Bevétel |
|--------|-------------|-----|---------|
| Agro Crystal Sky | 2 000 | $69 | $138 000 |
| Agro Mobile | 20 000 | $35 | $700 000 |
| Cine | 30 000 | $22 | $660 000 |
| Mission Hub (avg 3 000 előfizető × $8 × 60 hó) | — | — | $1 440 000 |
| **Összesen** | | | **~$2 938 000** |

Ez önálló fejlesztőként kiemelkedő — a Litchi alapítója (Come de Montis, London, egyedül, befektető nélkül) hasonló modellen ~$1–3M éves bevételt termel.

---

## 6. Play Store publikálás feltételei

### Kritikus biztonsági javítások (publikálás előtt kötelező)

1. **Hardcoded DJI App Key eltávolítása** — `AndroidManifest.xml:41` → `secrets.properties` + `.gitignore`
2. **Hardcoded OpenAIP API kulcs** — `MissionPlannerActivity.java:180` → BuildConfig secret
3. **Cleartext HTTP → HTTPS** — `OverpassClient.java` HTTP Overpass endpoint, `usesCleartextTraffic` eltávolítása

### Közepes prioritású javítások
- ProGuard bekapcsolása (`minifyEnabled true` release buildnél)
- `allowBackup="false"` beállítása
- `network_security_config.xml` létrehozása

### Play Store fiók és folyamat
- Google Play Developer fiók: **$25** egyszeri díj
- Privacy Policy kötelező (nyilvánosan elérhető URL)
- App Bundle (`./gradlew bundleRelease`), nem APK
- DJI App Key regisztrálva `com.dronefly.app` bundle ID-hoz (developer.dji.com)
- Legalább 2 telefon + 1 tablet screenshot a Play Store listinghez

---

## 7. Fejlesztési fázisok

### 1. fázis — Play Store ready (Crystal Sky Edition)
**Cél:** Publikálható, biztonságos, Crystal Sky-ra optimalizált agro app

1. Biztonsági javítások (API kulcsok, HTTPS)
2. Kiemelt fejlesztések: terület ha, képszám/idő becslő, akkumulátor figyelmeztetés
3. KML import (MePAR kompatibilitás)
4. Multi-battery resume
5. Play Store publikálás — **DroneFly Agro Crystal Sky Edition, $69**

### 2. fázis — Mobile Edition (MSDK v5 port)
**Cél:** Modern drónokra, Android telefon/tablet, Play Store mainstream

1. MSDK v5 M04 réteg (Kotlin wrapper vagy Java-kompatibilis)
2. `minSdk` 24-re emelése
3. Telefon layout (sw600dp qualifier)
4. Play Store publikálás — **DroneFly Agro Mobile Edition, $35**

### 3. fázis — Mission Hub (web)
**Cél:** Ismétlődő bevétel, közösség, agro analytics

1. FastAPI backend (misszió tárolás, user auth)
2. React + Leaflet frontend (misszió böngésző, sablon könyvtár)
3. Android app ↔ Mission Hub szinkronizáció
4. Előfizetéses modell bevezetése

### 4. fázis — Cine modul
**Cél:** Szélesebb felhasználói kör, Litchi-nél jobb cine UX

1. Timeline-alapú waypoint szerkesztő
2. Per-waypoint gimbal keyframe + interpoláció görbe
3. POI Orbit komponens
4. Mission Hub cine közösség

---

## 8. Jogi megfelelőség

### DJI licenc
- Standard Developer License: fizetős app engedélyezett
- Tilos: SDK reverse engineering, No-Fly Zone megkerülés, SDK önálló értékesítése
- No-Fly Zone: firmware szinten kezeli a drón — az app nem implementál unlock hívást → automatikusan compliant

### EU drónszabályozás
- A3 kategóriás drónokhoz (P4P) EASA nyilvántartás kötelező — az app ezt nem helyettesíti
- EU légtér overlay (A1/A2/A3) megjelenítése értékes jogi tájékoztatási funkció (#8 nyitott ötlet)
- SORA-hoz szükséges telemetria log (misszió CSV naplózás #5) mint bizonyíték

### GDPR
- Ha az app csak lokálisan ment fájlokat (misszió JSON, fotók): egyszerű adatkezelés
- Mission Hub esetén: regisztráció, GPS track tárolás → Privacy Policy + cookie consent szükséges

---

## 9. Fejlesztési kontextus

A DroneFly ~6 900 sor Java kód, 25 fájl. Fejlesztési idő: **kb. 1 hét részmunkaidőben**, AI-asszisztált fejlesztéssel (specifikáció: fejlesztő, implementáció: Claude). Ez a tempó fenntartható — a 4 fázis realisztikusan 6–12 hónap alatt teljesíthető ugyanilyen módszerrel.
