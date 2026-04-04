# L4 – Tranzakciós és Párhuzamos Kezelés – Export / Import

**Modul:** M03
**Szint:** L4 – Tranzakciós és Párhuzamos Kezelés
**Verzió:** v1.0.0
**Létrehozva:** 2026-04-02
**Státusz:** ✅ Implementálva (v1.0.0)

---

## 1. Fájlírás tranzakciómodell

```
exportCsv() / exportKmz() híváskor:

1. MissionExporter.toLitchiCsv() / toKmz() → adat memóriában (String / byte[])
2. new File(getExternalFilesDir(null), fileName) → fájl objektum
3. FileOutputStream → fájl írás
4. FileProvider.getUriForFile() → URI
5. Intent.ACTION_SEND → megosztás

Hiba a 3. lépésnél (IOException):
  → catch blokk → Toast hibaüzenet: "Export hiba: " + e.getMessage()
  → Részlegesen megírt fájl maradhat a tárolón
  → Következő exportnál felülíródik (ha azonos névvel próbálkozik)
    VAGY új fájlnevet kap (időbélyeges névnél)
```

---

## 2. Import stream kezelés

```
CsvMissionParser.parse() try-with-resources blokk:
  InputStream is = context.getContentResolver().openInputStream(uri);
  BufferedReader reader = new BufferedReader(new InputStreamReader(is));
  → automatikusan lezáródik (try-with-resources)
  → ContentResolver kezeli az URI-t (file://, content://, stb.)

Hibák:
  SecurityException   → uri-hoz nincs olvasási jog
  FileNotFoundException → fájl törölve lett a választó után
  IOException          → olvasási hiba közben
  → Minden esetben: üres lista visszatérés + e.printStackTrace()
```

---

## 3. Fájlnév ütközés kezelés

```
Fájlnév formátum: dronefly_mission_YYYYMMDD_HHmmss.csv
  → SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date())
  → Másodperc pontosságú időbélyeg
  → Azonos másodpercen belüli dupla export: felülírás
    (ritka eset, nem kritikus — a megosztott URI az új fájlra mutat)
```

---

## 4. Kompatibilitás Crystal Sky (API 22)

```
FileProvider:
  → Android 4.4+ (API 19+) óta elérhető
  → API 22-n teljesen kompatibilis
  → getExternalFilesDir() API 8+ óta elérhető

Intent.ACTION_GET_CONTENT (CSV import):
  → A Crystal Sky fájlkezelője (DJI Files app) kezeli
  → Ha nincs fájlkezelő → ActivityNotFoundException
  → Kezelés: try-catch, Toast: "Nincs fájlkezelő alkalmazás telepítve"
```

---

## 5. Nagy fájlok kezelése

```
Litchi CSV mérete:
  1 waypoint ≈ 150 byte
  3000 waypoint ≈ 450 KB
  → Memóriában: teljes String StringBuilder-be → elfogadható

KMZ mérete:
  1 waypoint ≈ 300 byte (KML Placemark)
  3000 waypoint ≈ 900 KB KML → ZIP tömörítés után ~200–400 KB
  → ByteArrayOutputStream → elfogadható

Crystal Sky (4 GB RAM):
  Nincs memóriagond az export méretével
```
