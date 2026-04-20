# L1 – Üzleti Folyamat – Kamera Konfigurátor

**Modul:** M05  
**Szint:** L1 – Üzleti Folyamat  
**Verzió:** v1.1.0  
**Létrehozva:** 2026-04-20  
**Utolsó módosítás:** 2026-04-20  
**Státusz:** ✅ Megvalósítva

---

## 1. Modul célja

Az M05 modul a **drón kamerájának manuális konfigurálását** teszi lehetővé közvetlenül
a DroneFly appból, misszió indítása előtt. Elsődleges cél: mezőgazdasági tőszámláláshoz
alkalmas, **konzisztens, reprodukálható** képek készítése.

Az auto expozíció a kép tartalmától függően változtatja a beállításokat —
ez betanítóadat-gyűjtésnél elfogadhatatlan, mert különböző képek más-más
expozíciós feltételek mellett készülnének. A manuális beállítás és a profilmentés
garantálja, hogy azonos körülmények között azonos paraméterekkel repüljünk.

**A modul értékajánlata:**
- A pilótának nem kell értenie a fényképezés összefüggéseihez — az app vezeti
- A hisztogram valós visszajelzést ad az expozíció helyességéről
- Az EV-csúszka egyetlen mozdulattal korrigál, a helyes prioritássorrendben
- Mentett profilok: "napsütés 100m", "felhős 80m" — egy kattintás az indulás előtt
- Konzisztens betanítóadatok: minden repülés azonos képi jellemzőkkel

---

## 2. Szereplők

| Szereplő | Szerepkör |
|----------|-----------|
| Drónos operátor | Kamera panelt megnyitja, EV-t beállítja, profilt alkalmaz |
| DroneFly app | Hisztogramot számolja, EV-prioritást kezeli, MSDK-n keresztül alkalmaz |
| DJI MSDK v4 | Camera API-n keresztül ISO / rekesz / zársebesség / WB / fókusz beállítás |
| P4P v1 kamera | 20 MP, 1" szenzor, mechanikus zár, f/2.8–f/11, ISO 100–3200 |

---

## 3. Mikor nyílik meg a Kamera Panel?

A Kamera Panel a MissionPlannerActivity-n belül egy **lebegő gomb** mögött él.
A gomb a képernyő jobb alsó sarkában jelenik meg, a Feltöltés + Start gombok felett.

```
[EXP] gomb megnyomva
  → animált slide-up: Kamera Panel megjelenik (félig átlátszó overlay)
  → ha a videó feed még nem fut: automatikusan elindul (alpha=0, nem látható)
  → hisztogram polling elindul (HandlerThread, 800 ms-onként)
  → AUTO mód alapértelmezett: kamera PROGRAM expozíció

[✕ Bezárás]
  → panel eltűnik, hisztogram poll leáll
  → ha az EXP gomb indította a feedet: videó feed is leáll
  → beállítások a kamerán maradnak
```

---

## 4. Kamera Panel felépítése

```
┌─────────────────────────────────────┐
│  📷 Kamera beállítások          [✕] │
├─────────────────────────────────────┤
│  HISZTOGRAM                         │
│  ▓▓▓▓▓░░░░░░░░░░░░░░░░░░░░░░░░░    │  ← live bar chart (256 bin → 64 oszlop)
│  sötét ◄──────────────────► világos │
│                                     │
│  Expozíció:  [◄] [─────●────] [►]  │  ← EV csúszka (−3 … +3, lépés: 0.5)
│              alul          túl      │
├─────────────────────────────────────┤
│  ISO        [ 100 | 200 | 400 | 800]│  ← gombok (radio style)
│  Rekesz     [f/5.6 | f/8 | f/11   ]│
│  Zársebesség  [────●────────────]   │  ← csúszka 1/400–1/2000 s
│  Színhőmérséklet  [─────●──────]   │  ← csúszka 4000–7000 K
│  Fókusz     [AUTO → Rögzít] [Inf.] │
├─────────────────────────────────────┤
│  Profil:  [ Betölt ▼ ]  [ Ment... ] │
│                      [ Alkalmaz ✓ ] │
└─────────────────────────────────────┘
```

---

## 5. Fő folyamat — misszió előtti beállítás

```
Operátor megnyitja a Kamera Panelt ([EXP] gomb)
  │
  ├─ Videó feed auto-start (ha még nem futott) → TextureView élő kép
  │
  ▼
Hisztogram megjelenik (800ms-onként, a videó frame luminance-ából számítva)
  │
  ▼
AUTO mód: kamera PROGRAM módban → hisztogram mutatja az auto expozíciót
  │
  ├── A) AUTO-LOCK: [🔒 Lock AUTO] gomb
  │       → jelenlegi auto értékek visszaolvasása (reflection)
  │       → MANUÁLIS módra vált, UI értékei feltöltve
  │
  └── B) Váltás MANUÁLIS módra:
         → [MANUÁLIS] gomb megnyomva
         → cameraParamsGroup engedélyezve
         → [Alkalmaz ✓] látható, [🔒 Lock] elrejtve

MANUÁLIS módban:
  │
  ├── EV ◄ ► gombokkal → Survey EV Engine fut → azonnali drón alkalmazás
  │       SS prioritás (min 1/800s) → ISO max 400 → rekesz utolsó
  │
  ├── ISO / rekesz gomb → azonnali drón alkalmazás
  │
  ├── Zársebesség / WB csúszka elengedésekor → azonnali drón alkalmazás
  │
  └── [Alkalmaz ✓] gomb → teljes beállítás csomag újraküldése
  │
  ▼
Hisztogram vízszintes közép-csúcs → expozíció helyes
  │
  ▼
Kamera Panel bezárása → misszió indítható
```

---

## 6. EV-csúszka logika (smart exposure)

Az operátor nem tudja, melyik paramétert kell állítani — az app dönti el.

**Prioritásrend — túlexponált (EV csökkentés szükséges):**

| Sorrend | Paraméter | Határok | Miért ez az első? |
|---------|-----------|---------|-------------------|
| 1 | Zársebesség növelése | max: 1/2000s | Képminőséget nem ront |
| 2 | ISO csökkentése | min: ISO 100 | Csökkentés javít, nem ront |
| 3 | Rekesz szűkítése | max: f/11 | Diffrakció miatt utolsó |

**Prioritásrend — alulexponált (EV növelés szükséges):**

| Sorrend | Paraméter | Határok | Miért ez az első? |
|---------|-----------|---------|-------------------|
| 1 | Zársebesség csökkentése | min: **1/800s** (survey motion blur limit) | Éles kép garantált |
| 2 | ISO növelése | max: **ISO 400** (survey minőségi korlát) | Szemcsés, de használható |
| 3 | Rekesz nyitása | min: f/5.6 | Mélységélesség romlik |

> **Survey korlátok:** A rendszer szándékosan nem enged 1/800s alá (mozgáselmosódás survey sebességnél)
> és ISO 400 fölé (szemcsézettség határ ML betanításhoz). Sötét beltérben elér a határra —
> ez helyes viselkedés, a rendszer mezőgazdasági kültéri repülésre van optimalizálva.

Ha az összes határt elérte és az expozíció még mindig helytelen:
→ Toast figyelmeztetés: "Fényviszonyok túl gyengék survey-hez"

---

## 7. Profilkezelés

A kamera profil az aktuális 5 paraméter (ISO, rekesz, SS, WB, fókusz) mentett kombinációja.

```
Mentés:
  [Ment...] gomb → névbeviteli dialog (pl. "napsütés 100m")
  → SharedPreferences-be JSON string
  → max 10 profil tárolható

Betöltés:
  [Betölt ▼] → legördülő lista a mentett profilokból
  → kiválasztás → paraméterek kitöltve a panelen
  → [Alkalmaz] szükséges a tényleges kameraalkalmazáshoz

Alapértelmezett értékek (profil nélkül, P4P v1):
  ISO:    100
  Rekesz: f/8
  Zár:    1/800s
  WB:     5600K (napsütés)
  Fókusz: Auto → INF rögzítés
```

---

## 8. Fókuszkezelés

A survey repülés alatt a fókusznak rögzítettnek kell lennie — az auto AF
ingadozást okoz, ha a kép tartalom változik (ég, talaj határa).

```
[AUTO → Rögzít] gomb:
  → Camera.setFocusMode(MANUAL)
  → Camera.setFocusTarget(FULL_SCREEN_CENTER)  ← AF egyszer lefut
  → AF lefutása után: Camera.setFocusMode(MANUAL) rögzítve
  → Gomb szövege: "Fókusz: rögzítve ✓"

[Inf.] gomb:
  → Camera.setFocusMode(MANUAL)
  → Fókusz végtelen távolságra állítva
  → Ajánlott: 80m+ repülési magasságnál (hiperfokal távolság felett)
  → Gomb szövege: "Fókusz: ∞ ✓"
```

---

## 9. Végállapotok

| Állapot | Leírás |
|---------|--------|
| `panel_closed` | Panel nem látható, kamera az utolsó beállításon |
| `panel_open_live` | Panel nyitva, hisztogram frissül, változtatás folyamatban |
| `settings_applied` | [Alkalmaz] megnyomva, MSDK API hívások lefutottak |
| `focus_locked` | Fókusz manuálisan rögzítve, survey-kész állapot |
| `profile_saved` | Profil névvel elmentve |

---

## 10. Kapcsolódó modulok

| Modul | Kapcsolat típusa |
|-------|-----------------|
| M01 Misszió Tervező | **Gazdamodul** — a Kamera Panel itt él, [📷 Kamera] gomb itt van |
| M04 DJI Integráció | **Közvetlen hívás** — MSDK Camera API-n keresztül alkalmaz |
