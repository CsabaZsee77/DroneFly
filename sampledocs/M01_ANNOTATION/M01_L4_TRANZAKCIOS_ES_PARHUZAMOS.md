# L4 – Tranzakciós és Párhuzamos – Annotation (Képcímkézés és Dataset Kezelés)

**Modul:** M01
**Szint:** L4 – Tranzakciós és Párhuzamos
**Forrásdokumentumok:** MULTI_DATASET_SYSTEM.md, DATASET_MANAGER_V3_CHANGES.md, INTEGRITY_CHECKER_V2_GUIDE.md, CLEANUP_GUIDE.md

---

## Adatperzisztencia

### Fájlrendszer-alapú tárolás

Az annotációs adatok kizárólag fájlrendszeren tárolódnak (nincs adatbázis):

| Adat típus | Tárolási forma | Elérési út |
|------------|----------------|------------|
| Projekt metaadatok | JSON | `data/datasets/{project_id}/metadata.json` |
| Tile képek | PNG/TIFF | `data/datasets/{project_id}/tiles/` |
| YOLO annotációk | TXT | `data/datasets/{project_id}/labels/` |
| Forrásképek | TIFF | `data/datasets/{project_id}/source_images/` |
| Training verziók | Könyvtár + JSON | `data/datasets/{project_id}/versions/` |

### Mentés atomicitása

**Annotáció mentése** (`save_annotation()`):
```
1. Koordináták konvertálása YOLO formátumra
2. Fájl írása (overwrites existing):
   with open(label_path, 'w') as f:
       f.write(yolo_content)
3. Metadata updated_at timestamp frissítés
```

**Kockázat:** Ha az alkalmazás a fájlírás közben omlik meg, a .txt fájl korrupt maradhat.
**Megoldás:** A .txt fájl általában kicsi, az írás szinte atomi. Korrupt esetén: integrity checker.

### Training version létrehozás atomicitása

```
1. Version könyvtár létrehozása (ideiglenes névvel: versions/{version_id}_tmp/)
2. Fájlok másolása (images + labels)
3. data.yaml generálás
4. version_meta.json írás
5. Könyvtár átnevezése (tmp → végleges név)
6. metadata.json frissítés (versions lista bővítés)
```

**Megjegyzés:** A könyvtár átnevezés az atomikus lépés. Ha 5. lépés előtt omlik meg: félkész könyvtár marad → integrity checker eltávolítja.

---

## Párhuzamos hozzáférés

### Jelenlegi állapot

A DrónTerápia **nem adatbázis-alapú**, ezért nincs natív tranzakciókezelés. A Streamlit session state felhasználónként izolált.

| Forgatókönyv | Kezelés |
|--------------|---------|
| Egy felhasználó, egy böngésző tab | Teljes mértékben támogatott |
| Egy felhasználó, két tab egyszerre | Konfliktus lehetséges (ugyanazt a tile-t mentheti kétszer) |
| Két felhasználó, külön projektek | Biztonságos (projekt izolált) |
| Két felhasználó, ugyanaz a projekt | Ritkán fordul elő; utolsó írás nyer (last-write-wins) |

### Fájl zárolás

A `user_manager.py` fájl zárolást alkalmaz a `users.json` írásakor. Az annotációs fájlokra **nincs fájl zárolás** – kis fájlméretnél ez elfogadható kompromisszum.

---

## Adatintegritás

### Integrity Checker (integrity_checker_v2.py)

A projekt tartalmaz egy adatintegritás-ellenőrző scriptet:

```bash
python integrity_checker_v2.py
```

**Mit ellenőriz:**
- Metadata JSON fájlok érvényessége (valid JSON, kötelező mezők megléte)
- Tile fájlok és a metadata konzisztenciája (tile-ok száma egyezik-e)
- Label fájlok érvényessége (helyes YOLO formátum)
- Orphaned fájlok (nincs hozzá projekt)
- Hiányzó verziók (metadata hivatkozik rá, de könyvtár nem létezik)

**Javítás:**
```bash
python integrity_checker_v2.py --fix
```

### Cleanup folyamatok (CLEANUP_GUIDE.md)

```bash
python cleanup_old_tiles.py    # Régi tile-ok törlése
python cleanup_temp_files.py   # Ideiglenes fájlok törlése
```

---

## Session kezelés az annotáció kontextusában

### Streamlit session state lifecycle

```
Oldal betöltés
    │
    ├─ initialize_session()       # session_manager.py
    │   ├─ Cookie ellenőrzés
    │   └─ User betöltés
    │
    ├─ require_authentication()   # auth_helpers.py
    │   └─ Ha nincs user → redirect Home
    │
    ├─ Projekt lista betöltés
    │   └─ dataset_manager.get_projects(user_id)
    │
    └─ Session state inicializálás
        ├─ current_project_id
        ├─ current_tile_index = 0
        └─ annotation_mode = "existing"
```

### Session state elvesztése

Ha a felhasználó frissíti az oldalt (`F5`):
- Streamlit session state **törlődik**
- `current_tile_index` visszaáll 0-ra
- `current_project_id` elvész → projekt kiválasztás szükséges
- **Az annotációs fájlok megmaradnak** (fájlrendszeren)

**Következmény:** Navigáció újrakezdődik az elejéről, de az annotációk nem vesznek el.

---

## Adatmigrációs szempontok

### Régi projekt formátum (v1) → Új formátum (v3)

A `DATASET_MANAGER_V3_CHANGES.md` dokumentálja a formátum változásokat:

| Változás | Régi (v1) | Új (v3) |
|----------|-----------|---------|
| Tárolási hely | `data/annotation/` | `data/datasets/{project_id}/` |
| Metaadatok | Minimális | Kibővített (owner, tags, versions) |
| Verziók | Nincs | Explicit version management |
| Multi-user | Nem | Igen (owner_id) |

### Migráció lépései

```bash
# Régi struktúra mentés
cp -r data/annotation/ data/annotation_backup/

# Migráció futtatás
python migrate_datasets_v3.py

# Ellenőrzés
python integrity_checker_v2.py
```

---

## Backup és helyreállítás

### Manuális backup

```bash
# Teljes annotation adatok backup
xcopy data\datasets\ backup\datasets\ /E /I /Y

# Adott projekt backup
xcopy data\datasets\{project_id}\ backup\{project_id}\ /E /I /Y
```

### Helyreállítás elveszett annotáció esetén

1. **Ellenőrzőpont visszaállítás** — Ha van korábbi snapshot, a `restore_snapshot()` metódussal visszaállítható
2. Ellenőrizd a `data/datasets/{project_id}/labels/` mappát – a .txt fájlok általában megmaradnak
3. Ha tile is elveszett: a forrásképből (`source_images/`) újracsempézhető
4. Ha metadata elveszett: `integrity_checker_v2.py --rebuild` parancs

### Snapshot tranzakciós viselkedés

**`create_snapshot()` atomicitás:**
- Label fájlok egyenként másolódnak (`shutil.copy2`) — ha crash, részleges snapshot jöhet létre
- Manifest frissítés `FileLock`-kal védett — manifest konzisztens marad
- **Kockázat:** Alacsony — label fájlok < 1KB, másolás gyors

**`restore_snapshot()` atomicitás:**
1. Auto-checkpoint a jelenlegi állapotról (ha `auto_checkpoint=True`)
2. Labels mappa összes `.txt` törlése
3. Snapshot labels visszamásolása
4. Project statistics újraszámolás + metadata mentés
- **Kockázat:** Ha crash a 2. és 3. lépés között, üres labels mappa marad — de az auto-checkpoint-ból visszaállítható
- **Párhuzamos hozzáférés:** Ha két böngésző tabban párhuzamosan annotálnak és visszaállítanak, race condition lehetséges — az utolsó mentés nyer

---

## Ismert korlátok és kockázatok

| Korlát | Hatás | Javaslat |
|--------|-------|---------|
| Nincs adatbázis tranzakció | Ritka adatvesztés párhuzamos íráskor | Egy felhasználó, egy böngésző tab |
| ~~Nincs undo/redo~~ | ~~Mentett annotáció felülírható~~ | **Megoldva:** Ellenőrzőpont (snapshot) rendszer |
| Nagy TIFF > memória | Crash csempézéskor | Tile méret növelése |
| Streamlit session state elvesztése | Navigáció újra kezd | Nincs hatás az adatokra |
| Offline mentés nem lehetséges | Nincs helyi cache | Stabil szerver kapcsolat szükséges |
