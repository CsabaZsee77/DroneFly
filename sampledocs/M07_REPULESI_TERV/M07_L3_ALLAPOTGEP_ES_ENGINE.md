# L3 – Állapotgép és Engine – Repülési Terv Generátor

**Modul:** M07
**Szint:** L3 – Állapotgép és Engine
**Verzió:** v1.2.0
**Létrehozva:** 2026-03-16
**Utolsó frissítés:** 2026-03-31
**Státusz:** ✅ Implementálva (v1.2.0)

---

## Forrásfájlok

| Fájl | Szerepkör | Státusz |
|------|-----------|---------|
| `utils/flight_planner.py` | GSD kalkulátor, grid generátor, footprint/spacing számítás + split_waypoints_by_battery() | ✅ Implementálva |
| `utils/kmz_generator.py` | KMZ/KML fájl előállítás DJI Fly WPML formátumban + generate_kmz_zip() | ✅ Implementálva |
| `utils/litchi_exporter.py` | Litchi Mission Hub kompatibilis CSV export (v1.2.0) | ✅ Implementálva |
| `utils/drone_manager.py` | Drón regisztráció, kamera profil kezelés (CRUD) | ✅ Implementálva |
| `_pages/11_✈️_Flight_Planning.py` | Streamlit UI — terület rajzolás, grid preview, KMZ + Litchi CSV letöltés, Terv mentés/betöltés UI (v1.2.0) | ✅ Implementálva |
| `_pages/9_🚁_Drones.py` | Drón nyilvántartás UI — profil böngészés, regisztráció | ✅ Implementálva |
| `data/camera_profiles.json` | Drón kamera profilok adatbázisa (6 profil) | ✅ Implementálva |
| `data/user_drones.json` | Felhasználói drón hozzárendelések | ✅ Implementálva |
| `data/users/{user_id}/flight_presets.json` | Repülési paraméter presetek tárolása | ✅ Implementálva |
| `data/users/{user_id}/flight_plans.json` | Teljes repülési tervek (poligon + paraméterek) user-scoped tárolása (v1.2.0) | ✅ Implementálva |

**Függőségek (már meglévő):**
- `utils/parcel_manager.py` — parcella polygon forrása
- `utils/path_manager.py` — felhasználói könyvtár kezelés
- `utils/user_manager.py` — előfizetési csomag ellenőrzés
- `shapely` — polygon műveletek, grid generálás
- `pyproj` — koordináta transzformáció (WGS84 ↔ metrikus)

---

## Kamera profil adatbázis

```json
// data/camera_profiles.json
{
  "DJI Mini 3": {
    "sensor_width_mm": 6.3,
    "sensor_height_mm": 4.7,
    "focal_length_mm": 4.5,
    "image_width_px": 4000,
    "image_height_px": 3000,
    "fov_h_deg": 82.1,
    "max_flight_time_min": 38,
    "notes": "1/1.3\" CMOS, 12MP standard mód"
  },
  "DJI Mini 3 Pro": {
    "sensor_width_mm": 9.6,
    "sensor_height_mm": 7.2,
    "focal_length_mm": 8.4,
    "image_width_px": 4000,
    "image_height_px": 3000,
    "fov_h_deg": 82.1,
    "max_flight_time_min": 34,
    "notes": "1/1.3\" CMOS, 12MP standard mód (48MP is elérhető)"
  },
  "DJI Mini 4 Pro": {
    "sensor_width_mm": 9.6,
    "sensor_height_mm": 7.2,
    "focal_length_mm": 8.4,
    "image_width_px": 4000,
    "image_height_px": 3000,
    "fov_h_deg": 82.1,
    "max_flight_time_min": 34,
    "notes": "1/1.3\" CMOS, 12MP standard mód"
  }
}
```

---

## Repülési terv adatmodell (v1.2.0)

```json
// data/users/{user_id}/flight_plans.json – egy terv eleme
{
  "name": "Keleti tábla 80m",
  "created_at": "2026-03-31",
  "polygon": {
    "type": "Polygon",
    "coordinates": [[[19.123, 47.456], [19.124, 47.456], ...]]
  },
  "altitude": 80,
  "speed": 7,
  "front_overlap": 80,
  "side_overlap": 75,
  "heading_deg": 0,
  "drone_name": "Phantom 4 Pro V2",
  "area_ha": 5.2
}
```

**Megjegyzés:**
- A `polygon` mező null lehet — ha a felhasználó poligon nélkül ment (csak paraméterek)
- A tervek user-scoped JSON tömbben tárolódnak (névütközésnél felülírás)
- A waypontok nem tárolódnak — mindig újragenerálódnak betöltéskor

### Repülési terv állapotok

```
[draft] → (KMZ letöltés) → [exported] → (felhasználó visszajelez) → [flown]
                                                                         │
                                                              (képek feltöltve M08-ba)
                                                                         ↓
                                                                    [processed]
```

---

## FlightPlanner engine (utils/flight_planner.py)

```python
class FlightPlanner:

    def calculate_altitude(self, gsd_cm, camera_profile) -> float:
        """GSD alapján repülési magasság számítás."""
        H = (gsd_cm * camera_profile["focal_length_mm"]
             * camera_profile["image_width_px"]) / (camera_profile["sensor_width_mm"] * 100)
        return round(H, 1)

    def calculate_footprint(self, altitude_m, camera_profile) -> tuple[float, float]:
        """Talajon látható terület (width, height) méterben."""
        w = altitude_m * camera_profile["sensor_width_mm"] / camera_profile["focal_length_mm"]
        h = altitude_m * camera_profile["sensor_height_mm"] / camera_profile["focal_length_mm"]
        return w, h

    def calculate_spacing(self, footprint_w, footprint_h,
                          front_overlap_pct, side_overlap_pct) -> tuple[float, float]:
        """Sávköz és képköz méterben."""
        line_spacing  = footprint_w * (1 - side_overlap_pct / 100)
        photo_spacing = footprint_h * (1 - front_overlap_pct / 100)
        return line_spacing, photo_spacing

    def generate_grid(self, parcel_geojson, altitude_m,
                      line_spacing_m, photo_spacing_m) -> list[dict]:
        """
        Waypontlista generálása a parcella polygonjából.
        1. Shapely polygon létrehozása WGS84-ből
        2. Helyi metrikus CRS-re vetítés (UTM zone 34N Magyarországhoz)
        3. Minimum bounding rectangle → optimális repülési irány
        4. Párhuzamos sávvonalak generálása
        5. Waypontok elhelyezése photo_spacing_m-enként
        6. Visszavetítés WGS84-be
        7. Kígyózó (boustrophedon) sorrend: páros sávok fordítva
        """
        ...
        return waypoints  # [{"lat": ..., "lon": ..., "alt": ..., "action": "takePhoto"}, ...]

    def estimate_flight_time(self, waypoints, speed_ms) -> float:
        """Becsült repülési idő percben."""
        ...
```

---

## KMZGenerator engine (utils/kmz_generator.py)

```python
class KMZGenerator:

    def generate(self, waypoints, plan_metadata) -> bytes:
        """
        DJI Fly kompatibilis KMZ fájl generálása.

        KMZ struktúra:
          archive.kmz (ZIP)
          └── doc.kml (KML 2.2)

        DJI Fly specifikus mezők (ExtendedData):
          - autoFlightSpeed: m/s
          - actionType: "takePhoto" | "hover" | "startRecord"
          - altitude: AGL méterben
          - gimbalPitchAngle: -90 (nadir)
          - wpml:waypointHeadingMode: "followWayline"

        Formátum forrása: DJI Fly által exportált KMZ visszafejtésével
        """
        kml = self._build_kml(waypoints, plan_metadata)
        return self._pack_kmz(kml)

    def _build_kml(self, waypoints, meta) -> str:
        """KML XML string előállítása."""
        ...

    def _pack_kmz(self, kml_str) -> bytes:
        """KML becsomagolása ZIP-be (KMZ formátum)."""
        import zipfile, io
        buf = io.BytesIO()
        with zipfile.ZipFile(buf, 'w') as zf:
            zf.writestr("doc.kml", kml_str)
        return buf.getvalue()
```

---

## LitchiExporter engine (utils/litchi_exporter.py) — v1.2.0

```python
def generate_litchi_csv(
    waypoints: List[Dict],
    altitude_m: float,
    speed_mps: float,
    heading_deg: float = -1.0,
) -> bytes:
    """
    Litchi Mission Hub kompatibilis CSV generálása.
    Minden waypointnál: kamera -90° (nadir), take photo akció.
    Speed: int, [0,15] m/s clamp. 0 = use cruise speed.
    """
```

**CSV formátum (46 oszlop, Litchi specifikáció szerint):**
```
latitude, longitude, altitude(m), heading(deg), curvesize(m), rotationdir,
gimbalmode, gimbalpitchangle,
actiontype1, actionparam1, actiontype2, actionparam2, ..., actiontype15, actionparam15,
altitudemode, speed(m/s),
poi_latitude, poi_longitude, poi_altitude(m), poi_altitudemode,
photo_timeinterval, photo_distinterval
```

**Kulcs beállítások mapping misszióhoz:**
| Mező | Érték | Leírás |
|------|-------|--------|
| `gimbalmode` | 2 | Interpolate — minden waypointnál explicit szög |
| `gimbalpitchangle` | -90 | Nadir (egyenesen lefelé) |
| `actiontype1` | 1 | Take photo minden waypointnál |
| `altitudemode` | 0 | AGL (terep felett) |
| `speed(m/s)` | int, [0,15] | Per-waypoint sebesség; 0 = cruise speed |
| `photo_timeinterval` | -1 | Kikapcsolt (waypointok triggerelnek) |
| `photo_distinterval` | -1 | Kikapcsolt |

**Import folyamat (Phantom 4 Pro V2):**
1. CSV letöltés az appból
2. Litchi Mission Hub (flylitchi.com/hub) → Import → CSV
3. Ellenőrzés térképen → Mentés
4. Litchi app szinkronizálás → Repülés indítása

---

## Repülési terv mentési rendszer (v1.2.0)

### Adatmodell: `data/users/{user_id}/flight_plans.json`
(ld. "Repülési terv adatmodell" szekció)

### Plan műveletek (flight_planning.py inline)
```python
def _plan_file(user_id) -> Path          # data/users/{user_id}/flight_plans.json
def _load_plans(user_id) -> list         # JSON betöltés
def _save_plan(user_id, plan: dict)      # Mentés (névütközésnél felülír)
def _delete_plan(user_id, name: str)     # Törlés név alapján
```

### UI sorrend
1. **"📂 Mentett repülési tervek"** expander — az oldal TETEJÉN (title alatt, csak ha van ≥1 terv)
   - selectbox + előnézet caption (dátum, méret, paraméterek)
   - "⬇️ Betöltés": session_state-be tölti a paramétereket + poligont → `st.rerun()`
   - "🗑️ Törlés": eltávolítja a tervet
2. Normál UI (drón, paraméterek, terület, generálás)
3. **"💾 Repülési terv mentése"** expander — az export gombok UTÁN
   - Text input a terv nevének
   - Ha nincs poligon: figyelmeztető caption (de menti a paramétereket)
   - Ha van poligon: "X ha terület | Y m | Z m/s" caption
   - "💾 Terv mentése" gomb → névütközésnél felülír

---

## Preset rendszer (v1.1.0)

### Adatmodell
```json
// data/users/{user_id}/flight_presets.json
[
  {
    "name": "Kukorica 80m",
    "altitude": 80,
    "speed": 7,
    "front_overlap": 80,
    "side_overlap": 75,
    "heading_deg": 0,
    "drone_name": "DJI Mini 4 Pro",
    "created_at": "2026-03-28"
  }
]
```

### Preset műveletek (flight_planning.py inline)
```python
def _load_presets(user_id) -> list          # JSON betöltés
def _save_preset(user_id, preset: dict)     # Mentés (névütközésnél felülír)
def _delete_preset(user_id, name: str)      # Törlés név alapján
```

### UI sorrend
1. "📂 Preset betöltése" expander — sliders FELETT (csak ha van ≥1 preset)
   - selectbox + előnézet caption + "⬇️ Betöltés" + "🗑️ Törlés"
   - Betöltés: session_state["altitude"], ["speed"], ["front_ov"], ["side_ov"], ["heading_deg"] → st.rerun()
2. Sliders (meglévő, változatlan)
3. "💾 Preset mentése" expander — sliders ALATT

---

## Akkumulátor felosztás (v1.1.0)

### split_waypoints_by_battery() — utils/flight_planner.py

```python
def split_waypoints_by_battery(
    waypoints: List[Dict],
    speed_mps: float,
    battery_min: float,        # akkumulátor repülési ideje (perc)
    use_pct: float = 0.80,     # felhasználható kapacitás aránya
    photo_pause_s: float = 1.5
) -> List[List[Dict]]:
    """
    Akkumulált repülési idő alapján osztja fel a waypointokat.
    Visszatér: lista szegmensekből, minden szegmens újra-indexelt waypoint lista.
    Ha belefér 1 akkumulátorba → egyelemű lista.
    """
```

**Felosztási logika:**
1. Kumulatív repülési idő kiszámítása minden waypointnál (táv/sebesség + foto_pause)
2. Effektív idő/akksi = `battery_min × use_pct × 60` (másodpercben)
3. Ha `total_time ≤ effective_s` → 1 szegmens
4. Különben: szegmens határt az effektív idő küszöbénél helyezi el
5. Minden szegmens waypointjai 0-tól újra-indexelve

### generate_kmz_zip() — utils/kmz_generator.py

```python
def generate_kmz_zip(
    segments: List[List[Dict]],
    altitude_m, speed_mps,
    base_mission_name: str,
    survey_polygon=None
) -> bytes:
    """
    N db KMZ fájlt tartalmaz ZIP archívumban.
    Fájlnév: {base_mission_name}_01of3.kmz, _02of3.kmz, ...
    """
```

### UI (KMZ export szekció alatt)

```
"🔋 Felosztás akkumulátoronként" expander:
  - Akkumulátor repülési ideje: number_input (default 25 perc)
  - Felhasználható kapacitás: slider 50–95% (default 80%)
  - Effektív idő/akksi caption
  - Ha 1 akkumulátor elég: ✅ üzenet
  - Ha több kell:
    - Szegmens táblázat: Szegmens | Waypontok | Távolság | Becsült idő
    - "⬇️ ZIP letöltése (N×KMZ)" gomb
```

---

## Streamlit UI (pages/9_Flight_Planning.py)

```
Sidebar:
  - Parcella selectbox (user parcelláiból)
  - Drón profil selectbox (camera_profiles.json-ból)
  - GSD csúszka (0.5–3.0 cm/px, step 0.1)
  - Front overlap slider (60–90%)
  - Side overlap slider (55–80%)
  - Sebesség slider (3–8 m/s)

Fő terület — 2 oszlop:
  Bal (paraméterek):
    - Számított magasság (nagy, kiemelve)
    - Sávszélesség, sávok száma
    - Becsült képszám, repülési idő
    - Akkumulátor igény (badge)
    - GSD ajánlás a parcella növényfajtájához

  Jobb (térkép preview):
    - Folium térkép: parcella polygon + sávvonalak overlay
    - Indulási pont jelölve

Alul:
  [⬇ KMZ Letöltés] — primary gomb
  Repülési előzmények expander:
    - Táblázat: dátum, drón, GSD, status
    - [Újrafuttatás] gomb soronként
```

---

## Növényfajta alapú GSD ajánlás integráció

```python
# Ha a parcellán van növényfajta (parcel["crop_type"]):
GSD_RECOMMENDATIONS = {
    "Kukorica":   {"min": 1.2, "max": 2.0, "optimal": 1.5},
    "Napraforgó": {"min": 0.8, "max": 1.5, "optimal": 1.2},
    "Cukorrépa":  {"min": 0.6, "max": 1.2, "optimal": 0.9},
    "Szója":      {"min": 0.6, "max": 1.2, "optimal": 1.0},
    "Burgonya":   {"min": 0.8, "max": 1.5, "optimal": 1.2},
    "Hagyma":     {"min": 0.5, "max": 1.0, "optimal": 0.7},
}
# UI: st.info(f"Ajánlott GSD {crop_type}-hoz: {rec['optimal']} cm/px")
```
