# L4 – Tranzakciós és Párhuzamos Kezelés – Repülési Terv Generátor

**Modul:** M07
**Szint:** L4 – Tranzakciós és Párhuzamos Kezelés
**Verzió:** v1.0.0
**Létrehozva:** 2026-03-16
**Státusz:** ✅ Implementálva (2026-03-18)

---

## 1. Adatperzisztencia

### flight_plans.json elhelyezése

A repülési tervek felhasználói könyvtárban tárolódnak, a path_manager.py által
kezelt útvonalakon:

```
data/users/{user_id}/flight_plans.json
```

Ez biztosítja, hogy a tervek felhasználónként izoláltak — más felhasználó nem
látja a terveket.

### Írási műveletek

| Művelet | Trigger | Atomiság |
|---------|---------|---------|
| Terv mentése | KMZ letöltés gomb | JSON teljes újraírás (atomic replace) |
| Státusz frissítés (`flown`) | Felhasználó visszajelzés | Adott plan_id rekord in-place frissítés |
| ODM job ID csatolás | M08 job indítás után | Adott plan_id rekord frissítés |

### Atomiság biztosítása

```python
# Atomic JSON write (temp fájl + rename pattern — már használatos a rendszerben)
def save_flight_plans(user_id, plans):
    path = get_flight_plans_path(user_id)
    tmp = path + ".tmp"
    with open(tmp, 'w') as f:
        json.dump(plans, f, indent=2, ensure_ascii=False)
    os.replace(tmp, path)  # atomic on POSIX, best-effort on Windows
```

---

## 2. KMZ generálás tranzakciómodell

A KMZ generálás **állapotmentes, tiszta számítás** — nem módosít adatbázist.
Sorrend:

```
1. Parcella polygon olvasása (read-only)
2. FlightPlanner.generate_grid() → waypoint lista (memóriában)
3. KMZGenerator.generate() → bytes (memóriában)
4. st.download_button() → böngésző letöltés
5. FlightPlanManager.save() → flight_plans.json írás
```

Ha a 4. lépés sikeres (letöltés) de az 5. meghiúsul (pl. írási hiba):
→ A KMZ letöltve van, a terv nincs elmentve az előzményekbe.
→ Nem kritikus hiba: a felhasználó újra letöltheti, a rendszer újra menti.

---

## 3. Párhuzamossági szempontok

### Streamlit session izoláció

A Streamlit minden felhasználói session külön Python szálban fut.
Párhuzamos hozzáférés a `flight_plans.json`-hoz:

```
Kockázat: 2 session egyidejű írása → JSON korrupció
Valószínűség: nagyon alacsony (1 felhasználó, 1 böngészőlap tipikusan)
Megoldás: file-level lock (threading.Lock vagy filelock könyvtár)
```

A meglévő rendszer más JSON fájloknál (users.json, parcels.json) ugyanezt a
pattern-t alkalmazza — M07 követi azt.

### Grid generálás teljesítmény

A Shapely alapú grid generálás szinkron, a Streamlit UI blokkolódik alatta.
Tipikus futási idő:

| Terület | Képszám | Futási idő |
|---------|---------|-----------|
| 1 ha | ~50 waypoint | < 0.1 s |
| 10 ha | ~500 waypoint | < 0.5 s |
| 50 ha | ~2500 waypoint | 1–3 s |

50 ha felett `st.spinner()` megjelenítése ajánlott.

---

## 4. Session state kezelés

```python
# Streamlit session state kulcsok (M07 specifikus)
st.session_state["fp_selected_parcel_id"]    # kiválasztott parcella
st.session_state["fp_drone_profile"]          # kiválasztott drón
st.session_state["fp_gsd"]                   # aktuális GSD érték
st.session_state["fp_waypoints"]             # utoljára generált waypontok
st.session_state["fp_plan_saved"]            # utoljára mentett terv ID
```

A waypontok session state-ben cache-elve vannak — ha a paraméterek nem
változtak, a grid nem generálódik újra (teljesítmény optimalizálás).

---

## 5. Koordináta transzformáció integritás

A grid generálás WGS84 → UTM → WGS84 körös transzformációt végez:

```
Bemeneti hiba forrás:    Parcella polygon pontossága (M06-ból)
Transzformációs hiba:    < 1 cm (pyproj UTM, elhanyagolható)
Visszaalakítási hiba:    < 1 cm

Összesített hiba:        < 10 cm (terepi GPS felbontásnál kisebb)
→ Elfogadható mezőgazdasági felmérési célra
```

---

## 6. KMZ formátum kompatibilitás

A DJI Fly app KMZ formátuma nem nyilvánosan dokumentált. A generátor a
**DJI Fly által exportált KMZ visszafejtésén** alapul.

**Kockázat:** DJI Fly firmware frissítés megváltoztathatja az elvárt KMZ struktúrát.

**Mitigáció:**
- A `camera_profiles.json` és a KMZ sablon külön fájlokban tárolva
  → frissítés kódmódosítás nélkül lehetséges
- Tesztelési eljárás dokumentálva (L3-ban): minden firmware frissítés után
  egy teszt KMZ generálva és DJI Fly-ban ellenőrizve

---

## 7. Előfizetés ellenőrzés integritás

```
Az előfizetési szint ellenőrzés kizárólag szerver oldalon (Python) történik.
Kliens oldali (JavaScript/Streamlit) ellenőrzés nem megbízható.

Streamlit server-side check:
  user = UserManager.get_user(session["user_id"])
  if user["subscription_tier"] not in ["survey", "admin"]:
      → UI nem renderelődik, flight_planner.py nem hívódik
```

---

## 8. Tárhelygazdálkodás

A flight_plans.json mérete:
- ~2 KB / terv (waypontokkal együtt, 50 ha-ig)
- 100 terv = ~200 KB → elhanyagolható

A KMZ fájlok **nem tárolódnak szerveren** — csak generálódnak és azonnal
a böngészőbe streamelnek. Nincs tárhely igény a KMZ-ekre.
