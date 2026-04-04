# L2 – Döntési Logika – Repülési Terv Generátor

**Modul:** M07
**Szint:** L2 – Döntési Logika
**Verzió:** v1.0.0
**Létrehozva:** 2026-03-16
**Státusz:** ✅ Implementálva (2026-03-18)

---

## 1. Modul ellenőrzés

```
Felhasználó megnyitja a Flight Planning oldalt
  │
  ▼
require_module("flight")  [utils/auth_helpers.py]
  │
  ├─ Nincs bejelentkezett felhasználó?
  │    → st.warning("⚠️ Bejelentkezés szükséges!") → st.stop()
  │
  ├─ role == "admin"?
  │    → folytatás (admin mindent lát)
  │
  ├─ "flight" IN user["modules"]?
  │    → folytatás
  │
  └─ "flight" NOT IN user["modules"]
       → st.warning("⚠️ Ez a funkció a ✈️ Repülési Terv modulban érhető el.")
       → st.info("Modul aktiváláshoz kérjük lépjen kapcsolatba az adminisztrátorral.")
       → st.stop()
```

---

## 2. GSD alapú repülési magasság számítás

A GSD képlet a drón kamera fizikai paraméterei alapján:

```
GSD (cm/px) = (H × sensor_width_mm) / (focal_length_mm × image_width_px) × 100

→ Átrendezve magasságra:
H (m) = (GSD_cm × focal_length_mm × image_width_px) / (sensor_width_mm × 100)
```

### Kamera profilok és számított értékek

| Drón | Szenzor szélesség | Fókusz (valós) | Kép szélesség | GSD 1.0 cm/px | GSD 1.5 cm/px | GSD 2.0 cm/px |
|------|-------------------|----------------|---------------|----------------|----------------|----------------|
| DJI Mini 3 | 6.3 mm | 4.5 mm | 4000 px | 32 m | 47 m | 63 m |
| DJI Mini 3 Pro | 9.6 mm | 8.4 mm | 4000 px | 35 m | 52 m | 70 m |
| DJI Mini 4 Pro | 9.6 mm | 8.4 mm | 4000 px | 35 m | 52 m | 70 m |

**Megjegyzés:** A Mini 3 Pro és Mini 4 Pro kamera paraméterei azonosak —
a különbség az akadályelkerülésben és a vezérlőben van, nem a GSD számításban.

---

## 3. Magasság határ ellenőrzések

```
Számított magasság > 120 m?
  │ IGEN → magasság = 120 m, GSD visszaszámítva és figyelmeztető üzenet:
  │        "Magyar légtér szabályok alapján max 120 m.
  │         Elérhető GSD ennél a drónnál: X cm/px"
  │ NEM → folytatás

Számított magasság < 15 m?
  │ IGEN → magasság = 15 m, figyelmeztetés:
  │        "15 m alatti repülés nem javasolt ütközési kockázat miatt.
  │         Kért GSD nem érhető el biztonsági okok miatt."
  │ NEM → folytatás
```

---

## 4. Sávközök és waypontok számítása

```python
# Sávszélesség a talajon (footprint)
footprint_width  = H * sensor_width_mm / focal_length_mm      # méterben
footprint_height = H * sensor_height_mm / focal_length_mm     # méterben

# Sávok közötti távolság (side overlap figyelembevételével)
line_spacing = footprint_width * (1 - side_overlap / 100)

# Képek közötti távolság (front overlap)
photo_spacing = footprint_height * (1 - front_overlap / 100)

# Sávok száma (parcella befoglaló téglalap alapján, merőleges irányban)
num_lines = ceil(parcel_width / line_spacing) + 1

# Képek száma soronként
photos_per_line = ceil(parcel_length / photo_spacing) + 1

# Összesen
total_photos = num_lines * photos_per_line
```

---

## 5. Repülési idő becslés

```python
# Teljes útvonal hossza
total_distance_m = num_lines * parcel_length + (num_lines - 1) * line_spacing

# Repülési idő (sebesség alapján) + fordulási idő soronként
flight_time_min = total_distance_m / (speed_ms * 60) + num_lines * 0.3

# Akkumulátor figyelmeztetés
if flight_time_min > 22:   # 34 perces max, 12 perc tartalék
    warning: "Több akkumulátor szükséges — becsült idő: X perc"
    show: ceil(flight_time_min / 22) akkumulátor szükséges
```

---

## 6. Grid generálás döntési logika

```
Parcella polygon elérhető és érvényes?
  │ NEM → "Parcellához nincs érvényes polygon. Rajzolj parcellát az M06-ban."
  │ IGEN
  ▼
Parcella terület < 0.1 ha?
  │ IGEN → figyelmeztetés: "Nagyon kis terület. Érdemes manuálisan repülni."
  │ NEM
  ▼
Parcella terület > 50 ha?
  │ IGEN → figyelmeztetés: "Nagy terület — becsült repülési idő: X perc (X akku).
  │         ODM feldolgozás ~X GB tárhelyet igényel."
  │ NEM (vagy felhasználó elfogadta)
  ▼
Optimális repülési irány meghatározása:
  → Parcella polygon leghosszabb tengelye (PCA vagy minimum bounding rectangle)
  → Ezzel párhuzamosan futnak a sávok (minimális fordulási kanyar)
  ▼
Grid generálás (Shapely alapú):
  → Befoglaló téglalap a rotált koordináta-rendszerben
  → Párhuzamos sávvonalak line_spacing távolsággal
  → Sávonkénti waypontok photo_spacing távolsággal
  → Visszaforgatás WGS84 koordinátarendszerbe
```

---

## 7. KMZ érvényesség döntés

```
Waypontok száma > 0?
  │ NEM → hiba: "Nem sikerült grid-et generálni a parcellára"
  │ IGEN
Waypontok száma > 99?  (DJI Fly limit egyes firmware verziókban)
  │ IGEN → figyelmeztetés: "Nagy misszió — DJI Fly max 99 waypontot kezel
  │         megbízhatóan. Fontold meg a terület feldarabolását."
  │         → lehetőség: "Parcella feldarabolása 2 részre" gomb
  │ NEM → KMZ generálható
```

---

## 8. Ajánlott GSD értékek növényfajtánként

| Növény | Optimális GSD | Megjegyzés |
|--------|--------------|------------|
| Kukorica (V3–V7) | 1.5–2.0 cm/px | Nagy tőtávolság, magasabb megengedett |
| Napraforgó | 1.0–1.5 cm/px | Kisebb tőátmérő, kisebb GSD kell |
| Cukorrépa | 0.8–1.2 cm/px | BBCH 16–18, kis rozetta |
| Szója | 0.8–1.2 cm/px | Soros vetés, sor szintén számít |
| Burgonya | 1.0–1.5 cm/px | Bakhátak segítik a detektálást |

→ Az ajánlott GSD megjelenik a UI-ban a kiválasztott növényfajta alapján
   (ha a parcellához növényfajta van rendelve az M06-ban)

---

## 9. Repülési terv újrahasználat döntés

```
Felhasználó kattint: [Újrafuttatás] egy korábbi terven
  │
  ▼
Parcella geometriája változott az utolsó futtatás óta?
  │ IGEN → "A parcella határa módosult. Az előző terv elavult lehet.
  │          Friss tervet generálsz, vagy az eredetit használod?"
  │          [Friss terv] | [Eredeti terv]
  │ NEM
  ▼
Azonos paraméterekkel KMZ újragenerálva → letöltés
```
