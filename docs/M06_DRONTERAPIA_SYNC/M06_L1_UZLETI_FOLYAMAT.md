# L1 – Üzleti Folyamat – Dronterapia Szinkron

**Modul:** M06
**Szint:** L1 – Üzleti Folyamat
**Verzió:** v2.1.0
**Létrehozva:** 2026-04-21
**Utolsó módosítás:** 2026-04-21
**Státusz:** ✅ Implementálva (v2.1.0)

---

## 1. Modul célja

Az M06 modul a DroneFly Android alkalmazás és a Dronterapia webes platform közötti
adatszinkronizációt valósítja meg. A kezelő a Crystal Sky tableten terepi körülmények
között dolgozik — sok esetben internet-kapcsolat nélkül. Az M06 biztosítja, hogy:

- A tableten létrehozott repülési programok feltölthetők a Dronterapia platformra
- A platformon létrehozott vagy módosított repülési programok letölthetők a tabletre
- Az összes helyi funkció (tervezés, DJI feltöltés, repülés) internet nélkül is teljes
  értékűen működik

A szinkronizáció egysége a `.flightprogram.json` fájl — parcellák szinkronizálása
(F-S06) egyelőre nem része az implementációnak.

---

## 2. Szereplők

| Szereplő | Szerepkör |
|----------|-----------|
| Kezelő (terepi) | A Crystal Sky tableten dolgozó mezőgazdasági drónpilóta |
| DroneFly Android | A tableten futó app (M06 komponensek: NetworkMonitor, AuthManager, SyncManager) |
| Dronterapia platform | A webes backend (`app.dronterapia.hu`) — repülési program tárolás, REST API |
| Crystal Sky tablet | DJI Android 5.1 (API 22) eszköz, korlátozott hálózati lehetőségekkel |

---

## 3. Főfolyamat — terepi munkamenet

```
[Reggel, irodában vagy tábla szélén — wifi elérhető]

1. Kezelő megnyitja a DroneFly appot
2. A NetworkMonitor érzékeli az internet-kapcsolatot → ONLINE állapot
3. Ha még nincs bejelentkezve:
   a. A "Bejelentkezés" gomb aktív
   b. Kezelő megnyomja → AlertDialog jelenik meg (felhasználónév + jelszó)
   c. DroneFly POST /api/auth/login → Bearer token tárolva SharedPreferences-be
   d. Az app megjegyzi a tokent — nem kell újra bejelentkezni

4. Kezelő a "Szinkronizálás" gombra nyom
   → SyncManager lefuttatja a teljes sync ciklust:
     a. Szerveres repülési program lista lekérve (GET /api/flight-programs)
     b. Helyi dirty fájlok feltöltve (POST vagy PUT)
     c. Szerveren újabb fájlok letöltve

[Terepi munkavégzés — mobil internet nincs]

5. NetworkMonitor érzékeli a kapcsolat elvesztését → OFFLINE állapot
6. Sync UI disabled, toast: "Offline"
7. Kezelő folytatja a munkát: polygon rajzolás, misszió generálás, DJI feltöltés

8. Kezelő ment egy repülési programot:
   → ProjectManager.saveProject() → sync_pending: true kerül a fájlba

[Kapcsolat visszatér]

9. NetworkMonitor érzékeli az ONLINE állapotot
10. A tvSyncStatus mező frissül ("Online – bejelentkezve")
11. Kezelő manuálisan indítja a szinkronizálást ("Szinkronizálás" gomb)
    → A dirty flag-es fájlok feltöltődnek
    → Szerveren esetleg módosult fájlok letöltődnek
```

---

## 4. Bejelentkezési folyamat részletesen

```
Kezelő: "Bejelentkezés" gomb
  │
  ▼
AlertDialog megjelenik:
  - Felhasználónév szövegmező (EditText)
  - Jelszó szövegmező (EditText, inputType=textPassword)
  - "Bejelentkezés" és "Mégse" gombok
  │
  ▼
Kezelő kitölti az adatokat → "Bejelentkezés"
  │
  ▼
AuthManager.login(username, password, callback)
  → HTTP POST https://app.dronterapia.hu/api/auth/login
  → Body: {"username": "...", "password": "..."}
  │
  ├─ Siker: {"access_token": "...", "username": "..."}
  │    → Token tárolva SharedPreferences-be
  │    → updateSyncStatus() → "Online – bejelentkezve: <felhasználónév>"
  │
  └─ Hiba: HTTP 401, hálózati hiba
       → Toast hibaüzenet
       → Bejelentkezési állapot nem változik
```

---

## 5. Szinkronizálási folyamat részletesen

```
Kezelő: "Szinkronizálás" gomb  VAGY  programból hívott syncAll()
  │
  ▼
Precondition ellenőrzés:
  - NetworkMonitor.getState() == ONLINE?
  - AuthManager.isAuthenticated()?
  - !MissionPlannerActivity.missionRunning?
  │
  ├─ Bármely feltétel NEM teljesül → sync nem indul, tvSyncStatus frissül
  │
  └─ Minden feltétel teljesül → sync indul
       │
       ▼
  1. GET /api/flight-programs
     → szerveres lista: [{id, name, updated_at}, ...]
     │
     ▼
  2. Upload fázis:
     → helyi .flightprogram.json fájlok vizsgálata
     → sync_pending: true VAGY source_system: "dronefly"?
       → fájl szerveres listában van (ID alapján)?
         → IGEN → PUT /api/flight-programs/{id}
         → NEM  → POST /api/flight-programs
       → sikeres upload után: sync_pending: false, last_synced_at: most
     │
     ▼
  3. Download fázis:
     → szerveres lista vs. helyi fájlok összehasonlítása
     → szerveres updated_at > helyi updated_at?
       → IGEN → GET /api/flight-programs/{id} → helyi fájl felülírva
     │
     ▼
  tvSyncStatus: "Utolsó sync: <időbélyeg>"
```

---

## 6. Offline-first alapelv

A DroneFly terepi eszköz. Az internet-kapcsolat nem előfeltétele semmilyen
alapfunkciónak. Az M06 modul ehhez az elvhez alkalmazkodik:

```
Internet nélkül:    Teljes tervezés, DJI feltöltés, repülés — zavartalanul
Bejelentkezve+online:  Szinkronizálás elérhető
Bejelentkezve+offline: Helyi munka megy, sync szünetel, token megmarad
Kijelentkezve:      Csak helyi funkciók, sync UI rejtett
```

Kapcsolat nélküli mentés esetén a fájl `sync_pending: true` jelöléssel kerül
lemezre — a szinkronizálás a következő online alkalommal automatikusan elvégezhető.

---

## 7. Végállapotok és hibaesetek

| Esemény | Viselkedés |
|---------|-----------|
| Bejelentkezés sikeres | Token tárolva, tvSyncStatus frissítve |
| Bejelentkezés sikertelen (401) | Toast, állapot nem változik |
| Szinkronizálás hálózati hiba | Log, dirty flag megmarad, tvSyncStatus: hiba |
| HTTP 40x/50x szerverhiba | Log + skip, dirty flag megmarad |
| Repülés közben sync kísérlet | Sync nem indul (precondition: !missionRunning) |
| Kapcsolat elvesztése sync közben | Részleges sync, dirty flag megmarad a nem feltöltött fájlokon |
| Token lejárat (F-S13, nyitott) | Egyelőre nem kezelt — HTTP 401 → silent fail |

---

## 8. Kapcsolódó modulok

| Modul | Kapcsolat típusa |
|-------|-----------------|
| M01 Misszió Tervező | **Felhasználó** — Sync UI elemek (btnLogin, btnSync, tvSyncStatus) itt jelennek meg |
| M03 Export/Import | **Formátum** — `.flightprogram.json` fájl, amit ProjectManager ment/tölt |
| M04 DJI Integráció | **Precondition** — missionRunning állapot blokkolja a syncet |
