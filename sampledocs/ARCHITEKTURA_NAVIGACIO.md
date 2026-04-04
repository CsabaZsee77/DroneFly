# Navigációs architektúra — DrónTerápia

**Verzió:** 1.0
**Utolsó módosítás:** 2026-03-17
**Érintett fájl:** `Home.py`

---

## Probléma (megoldott)

A Streamlit 1.36 előtti MPA (Multi-Page App) rendszer `pages/` könyvtár alapú oldal-felfedezést használ. A Streamlit 1.36+ verzióban bevezetett `st.navigation()` API mellett a régi rendszer (`_mpa_v1` kompatibilitási réteg) is aktív marad, ha a `pages/` könyvtár létezik.

Ez **kettős navigációs adatot** küld a frontendnek:
1. `new_session.app_pages` — régi rendszer, `pages/` könyvtár alapján
2. `navigation` proto — új rendszer, `_mpa_v1()` által

A frontend mindkettőt rendereli → **duplikált sidebar navigációs elemek** subpage-eken.

A jelenség mintája (példa):
- Home oldal: ✅ rendben (Home.py fut, `_mpa_v1` nem hívódik)
- Map oldal: ❌ Annotation–Map tartomány duplázódik
- Parcels oldal: ❌ Annotation–Parcels tartomány duplázódik

---

## Megoldás

Az `st.navigation()` explicit hívása a `Home.py`-ban **felülírja** a Streamlit automatikus `pages/` könyvtár szkennelését. Innentől a Streamlit kizárólag az explicit módon megadott oldallista alapján navigál.

**Dokumentált Streamlit viselkedés (1.36+):**
> Ha az `st.navigation()` a főscriptből kerül meghívásra, a Streamlit az explicit oldaldefiníciót használja a `pages/` könyvtár automatikus szkennelése helyett.

---

## Implementáció

### Home.py struktúra

```
Home.py (router + minden oldalon futó kód)
│
├── _render_dashboard()          ← Főoldal tartalom (függvény)
│
├── st.set_page_config()
├── UserManager / SessionManager init
├── CSS + JS
├── Login / Register UI  →  st.stop() ha nincs bejelentkezve
│
├── with st.sidebar:             ← Felhasználói menü (minden oldalon)
│     user info, jelszóváltás, email, newsletter, kijelentkezés
│
├── with st.sidebar:             ← Verzió / PDF / Kapcsolat (minden oldalon)
│
└── st.navigation(_pages)        ← Explicit router
    └── _pg.run()                ← Aktuális oldal végrehajtása
```

### Oldallista felépítése (`_pages`)

A navigációs lista dinamikusan épül a felhasználó szerepköre és moduljai alapján:

| Oldal | Fájl | Feltétel |
|-------|------|----------|
| 🏠 Főoldal | `_render_dashboard()` | mindig |
| 🎨 Annotation | `1_🎨_Annotation.py` | admin / legacy / `model` modul |
| 🎓 Training | `2_🎓_Training.py` | admin / legacy / `model` modul |
| 🤖 Models | `5_🤖_Models.py` | admin / legacy / `model` modul |
| 🔍 Counting | `3_🔍_Counting.py` | admin / legacy / `counting` modul |
| 📈 Results | `4_📈_Results.py` | admin / legacy / `counting` modul |
| 🗺️ Map | `6_🗺️_Map.py` | mindig (alapfunkció) |
| 📋 Parcels | `7_📋_Parcels.py` | mindig (alapfunkció) |
| 🛰️ Parcel Analysis | `8_🛰️_Parcel_Analysis.py` | admin / legacy / `spectral` modul |
| 🚁 Drones | `9_🚁_Drones.py` | admin / legacy / `flight` modul |
| ✈️ Flight Planning | `11_✈️_Flight_Planning.py` | admin / legacy / `flight` modul |
| 🛠️ ODM Processing | `12_🛠️_ODM_Processing.py` | admin / legacy / `odm` modul |
| ⚙️ Admin | `13_⚙️_Admin.py` | kizárólag admin |

**Legacy user:** ha a `users.json`-ban nincs `modules` mező (régi felhasználók), az összes modul elérhető (backward compatibility).

---

## Sidebar viselkedés változása

Az `st.navigation()` + `_pg.run()` mintával a `Home.py` **minden oldalbetöltéskor fut** (`_pg.run()` előtti kód). Ennek következménye:

- **A sidebar felhasználói menü és verzió/kontakt blokk minden oldalon megjelenik** — ez szándékos, egységes UX.
- A subpage-ek saját `st.sidebar` tartalma összeadódik a Home.py-ban definiáltakkal.

---

## auth_helpers.py viszonya

Az `apply_role_based_nav_visibility()` függvény CSS-alapú navigáció-szűrése **megtartva**:
- Python szinten a modul-kapuzás az oldallista összeállításakor történik (oldal nem kerül a listába → nem is jelenik meg).
- A CSS-réteg defense-in-depth: a `show_locked_features=True` esetén a halvány/dőlt megjelenítés CSS-sel kezelt.
- Az `href*=` selectorok továbbra is működnek, mert az URL-ek (`/Drones`, `/Annotation` stb.) az oldal title-jéből képződnek és nem változtak.

---

## URL-ek

Az `st.Page(path, title=...)` esetén a Streamlit a `title` paraméterből képezi az URL-t (szóközök → `_`, lowercase). Mivel a title-ök azonosak a korábban használt fájlnév-alapú URL-ekkel, minden meglévő link és CSS selector érvényes marad.

| Title | URL |
|-------|-----|
| Annotation | `/Annotation` |
| Drones | `/Drones` |
| Flight_Planning | `/Flight_Planning` |
| Admin | `/Admin` |

---

## Rollback

Ha valamilyen okból vissza kell térni a régi navigációs rendszerhez:

```bash
git revert <commit-hash>   # a navigáció-fix commitja
docker compose up --build -d
```

A régi állapotban a sidebar ikonok egy részénél duplázódás tapasztalható subpage-eken (ismert, megoldott bug).
