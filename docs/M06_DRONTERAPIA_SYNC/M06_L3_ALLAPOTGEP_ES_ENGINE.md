# L3 – Állapotgép és Engine – Dronterapia Szinkron

**Modul:** M06
**Szint:** L3 – Állapotgép és Engine
**Verzió:** v2.1.0
**Létrehozva:** 2026-04-21
**Utolsó módosítás:** 2026-04-21
**Státusz:** ✅ Implementálva (v2.1.0)

---

## Forrásfájlok

| Fájl | Szerepkör | Státusz |
|------|-----------|---------|
| `sync/NetworkMonitor.java` | Hálózati állapot figyelő (OFFLINE/ONLINE, API 22 workaround) | ✅ Implementálva |
| `sync/AuthManager.java` | Dronterapia bejelentkezés (username+jelszó → Bearer token), SharedPreferences tárolás, SSL trust-all | ✅ Implementálva |
| `sync/SyncManager.java` | Kétirányú .flightprogram.json szinkronizáció (uploadDirtyFiles, downloadNewFiles) | ✅ Implementálva |
| `MissionPlannerActivity.java` | Sync UI elemek (btnLogin, btnSync, tvSyncStatus), networkListener, initSyncControls() | ✅ Implementálva |
| `mission/ProjectManager.java` | sync_pending: true dirty flag beállítása mentéskor | ✅ Implementálva |

**Függőségek:**
- `android.net.ConnectivityManager` — hálózati állapot
- `android.content.SharedPreferences` — token tárolás
- `java.net.HttpURLConnection` — HTTP kliens
- `org.json.JSONObject`, `JSONArray` — JSON parse/build
- `java.util.concurrent.ExecutorService` — háttérszál kezelés

---

## NetworkMonitor.java

### Osztálystruktúra

```java
public class NetworkMonitor {

    public enum State { OFFLINE, ONLINE }

    public interface Listener {
        void onNetworkStateChanged(State state);
    }

    private final Context context;
    private final ConnectivityManager cm;
    private Listener listener;
    private State currentState = State.OFFLINE;

    // API 23+ callback regisztráció
    private ConnectivityManager.NetworkCallback networkCallback;
}
```

### Konstruktor és inicializálás

```java
public NetworkMonitor(Context context) {
    this.context = context;
    this.cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
}

public void start(Listener listener) {
    this.listener = listener;

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                setState(State.ONLINE);
            }
            @Override
            public void onLost(Network network) {
                setState(State.OFFLINE);
            }
        };
        cm.registerDefaultNetworkCallback(networkCallback);
        // Kezdeti állapot lekérdezése
        currentState = hasActiveNetwork() ? State.ONLINE : State.OFFLINE;
    }
}

public void stop() {
    if (networkCallback != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        cm.unregisterNetworkCallback(networkCallback);
    }
}
```

### hasActiveNetwork() — API 22 workaround

```java
public boolean hasActiveNetwork() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        Network network = cm.getActiveNetwork();
        if (network == null) return false;
        NetworkCapabilities nc = cm.getNetworkCapabilities(network);
        return nc != null && nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    } else {
        // API 22 (Crystal Sky Android 5.1) fallback
        // getActiveNetworkInfo() deprecated API 28-ban, de API 22-n ez az egyetlen lehetőség
        @SuppressWarnings("deprecation")
        NetworkInfo ni = cm.getActiveNetworkInfo();
        return ni != null && ni.isConnected();
    }
}
```

### setState() — állapotváltás és listener értesítés

```java
private void setState(State newState) {
    if (currentState != newState) {
        currentState = newState;
        if (listener != null) {
            new Handler(Looper.getMainLooper()).post(() -> listener.onNetworkStateChanged(newState));
        }
    }
}

public State getState() { return currentState; }
```

---

## AuthManager.java

### Osztálystruktúra

```java
public class AuthManager {

    private static final String PREF_NAME   = "dronterapia_auth";
    private static final String KEY_TOKEN   = "access_token";
    private static final String KEY_USER    = "username";
    private static final String BASE_URL    = "https://app.dronterapia.hu";

    public interface AuthCallback {
        void onSuccess(String username);
        void onError(String message);
    }

    private final Context context;
    private final SharedPreferences prefs;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
}
```

### login() — username/password bejelentkezés

```java
public void login(String username, String password, AuthCallback callback) {
    executor.execute(() -> {
        try {
            URL url = new URL(BASE_URL + "/api/auth/login");
            HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();

            // SSL trust-all workaround (Let's Encrypt nem ismert Crystal Sky-on)
            conn.setSSLSocketFactory(getTrustAllSSLSocketFactory());
            conn.setHostnameVerifier((hostname, session) -> true);

            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(30_000);

            JSONObject body = new JSONObject();
            body.put("username", username);
            body.put("password", password);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.toString().getBytes("UTF-8"));
            }

            int code = conn.getResponseCode();
            if (code == 200) {
                String response = readStream(conn.getInputStream());
                JSONObject json = new JSONObject(response);
                String token = json.getString("access_token");
                String user  = json.optString("username", username);

                // Token tárolása SharedPreferences-be (async write)
                prefs.edit()
                    .putString(KEY_TOKEN, token)
                    .putString(KEY_USER, user)
                    .apply();  // apply() = aszinkron írás, nem blocking

                new Handler(Looper.getMainLooper()).post(() -> callback.onSuccess(user));
            } else {
                new Handler(Looper.getMainLooper()).post(() ->
                    callback.onError("HTTP " + code));
            }
        } catch (Exception e) {
            new Handler(Looper.getMainLooper()).post(() ->
                callback.onError(e.getMessage()));
        }
    });
}
```

### logout(), getToken(), isAuthenticated()

```java
public void logout() {
    prefs.edit()
        .remove(KEY_TOKEN)
        .remove(KEY_USER)
        .apply();
}

public String getToken() {
    return prefs.getString(KEY_TOKEN, null);
}

public String getUsername() {
    return prefs.getString(KEY_USER, null);
}

public boolean isAuthenticated() {
    return getToken() != null;
}
```

### SSL trust-all workaround

```java
private SSLSocketFactory getTrustAllSSLSocketFactory() throws Exception {
    TrustManager[] trustAllCerts = new TrustManager[] {
        new X509TrustManager() {
            public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            public void checkClientTrusted(X509Certificate[] c, String a) {}
            public void checkServerTrusted(X509Certificate[] c, String a) {}
        }
    };
    SSLContext sc = SSLContext.getInstance("TLS");
    sc.init(null, trustAllCerts, new java.security.SecureRandom());
    return sc.getSocketFactory();
}
```

**Miért szükséges a trust-all workaround:**
A Crystal Sky Android 5.1 nem tartalmazza a Let's Encrypt ISRG Root X1 gyökértanúsítványt
a rendszer-tanúsítványtárban. Az `app.dronterapia.hu` Let's Encrypt tanúsítványt használ,
amit a Crystal Sky `SSLHandshakeException`-nel visszautasít. A custom `TrustManager`
megkerüli az ellenőrzést. Gyártási környezetben ennél szigorúbb megoldás (certificate pinning
vagy megbízható CA bundle) szükséges lenne, de Crystal Sky-on ez az egyetlen működő megközelítés.

---

## SyncManager.java

### Osztálystruktúra

```java
public class SyncManager {

    private static final String BASE_URL = "https://app.dronterapia.hu";
    private static final String API_PATH = "/api/flight-programs";

    public interface SyncCallback {
        void onComplete(String statusMessage);
        void onError(String errorMessage);
    }

    private final Context context;
    private final AuthManager authManager;
    private final File missionsDir;  // getExternalFilesDir("missions")
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
}
```

### syncAll() — teljes szinkronizálási ciklus

```java
public void syncAll(SyncCallback callback) {
    executor.execute(() -> {
        try {
            String token = authManager.getToken();
            if (token == null) {
                postError(callback, "Nincs bejelentkezve");
                return;
            }

            // 1. Szerveres lista lekérése
            JSONArray serverList = fetchServerList(token);
            if (serverList == null) {
                postError(callback, "Szerverlista lekérés sikertelen");
                return;
            }

            // Server ID-k halmazba gyűjtve a POST/PUT döntéshez
            Set<String> serverIds = new HashSet<>();
            Map<String, String> serverUpdatedAt = new HashMap<>();
            for (int i = 0; i < serverList.length(); i++) {
                JSONObject rec = serverList.getJSONObject(i);
                String id = rec.optString("id", null);
                if (id != null) {
                    serverIds.add(id);
                    serverUpdatedAt.put(id, rec.optString("updated_at", ""));
                }
            }

            // 2. Upload fázis
            uploadDirtyFiles(token, serverIds);

            // 3. Download fázis
            downloadNewFiles(token, serverList, serverUpdatedAt);

            String ts = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
            postComplete(callback, "Utolsó sync: " + ts);

        } catch (Exception e) {
            postError(callback, "Szinkronizálási hiba: " + e.getMessage());
        }
    });
}
```

### fetchServerList() — GET /api/flight-programs

```java
private JSONArray fetchServerList(String token) {
    try {
        URL url = new URL(BASE_URL + API_PATH);
        HttpsURLConnection conn = openConnection(url, token);
        conn.setRequestMethod("GET");

        int code = conn.getResponseCode();
        if (code == 200) {
            String body = readStream(conn.getInputStream());
            return new JSONArray(body);
        }
        Log.e("SYNC", "fetchServerList HTTP " + code);
        return null;
    } catch (Exception e) {
        Log.e("SYNC", "fetchServerList error: " + e.getMessage());
        return null;
    }
}
```

### uploadDirtyFiles() — dirty fájlok feltöltése

```java
private void uploadDirtyFiles(String token, Set<String> serverIds) {
    File[] files = missionsDir.listFiles(
        f -> f.getName().endsWith(".flightprogram.json"));
    if (files == null) return;

    for (File f : files) {
        try {
            String content = readFile(f);
            JSONObject json = new JSONObject(content);
            JSONObject metadata = json.getJSONObject("metadata");

            boolean syncPending = metadata.optBoolean("sync_pending", false);
            String sourceSystem = metadata.optString("source_system", "");

            if (!syncPending && !"dronefly".equals(sourceSystem)) continue;

            String localId = metadata.optString("id", null);
            boolean isOnServer = localId != null && serverIds.contains(localId);

            boolean success = pushToServer(token, localId, json, isOnServer);

            if (success) {
                metadata.put("sync_pending", false);
                metadata.put("last_synced_at",
                    new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                        .format(new Date()));
                json.put("metadata", metadata);
                writeFile(f, json.toString());
            }
        } catch (Exception e) {
            Log.e("SYNC", "uploadDirtyFiles error for " + f.getName() + ": " + e.getMessage());
        }
    }
}
```

### pushToServer() — POST vagy PUT

```java
private boolean pushToServer(String token, String id, JSONObject program, boolean isOnServer) {
    try {
        URL url;
        String method;
        if (isOnServer && id != null) {
            url = new URL(BASE_URL + API_PATH + "/" + id);
            method = "PUT";
        } else {
            url = new URL(BASE_URL + API_PATH);
            method = "POST";
        }

        HttpsURLConnection conn = openConnection(url, token);
        conn.setRequestMethod(method);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(program.toString().getBytes("UTF-8"));
        }

        int code = conn.getResponseCode();
        if (code == 200 || code == 201 || code == 204) {
            Log.d("SYNC", method + " success: " + (id != null ? id : "new"));
            return true;
        }
        Log.e("SYNC", method + " HTTP " + code + " for id=" + id);
        return false;
    } catch (Exception e) {
        Log.e("SYNC", "pushToServer error: " + e.getMessage());
        return false;
    }
}
```

### downloadNewFiles() — újabb szerveres fájlok letöltése

```java
private void downloadNewFiles(String token, JSONArray serverList,
                               Map<String, String> serverUpdatedAt) throws Exception {
    for (int i = 0; i < serverList.length(); i++) {
        JSONObject rec = serverList.getJSONObject(i);
        String id = rec.optString("id", null);
        String serverTs = rec.optString("updated_at", "");
        String name = rec.optString("name", id);

        if (id == null) continue;

        // Helyi fájl keresése az id alapján
        File localFile = findLocalFileById(id);
        if (localFile != null) {
            // Létezik helyi fájl — updated_at összehasonlítás
            try {
                JSONObject localJson = new JSONObject(readFile(localFile));
                String localTs = localJson.getJSONObject("metadata")
                    .optString("updated_at", "");
                if (serverTs.compareTo(localTs) <= 0) {
                    continue; // helyi verzió frissebb vagy azonos
                }
            } catch (Exception e) {
                // Parse hiba → letöltjük újra
            }
        }

        // Letöltés
        JSONObject full = fetchProgram(token, id);
        if (full != null) {
            String filename = sanitizeFilename(name) + ".flightprogram.json";
            File target = localFile != null ? localFile : new File(missionsDir, filename);
            writeFile(target, full.toString());
            Log.d("SYNC", "Downloaded: " + id + " → " + target.getName());
        }
    }
}
```

---

## MissionPlannerActivity — Sync UI integráció

### Sync UI mezők

```java
// Sync UI elemek (M06)
Button   btnLogin;        // "Bejelentkezés" gomb
Button   btnSync;         // "Szinkronizálás" gomb
TextView tvSyncStatus;    // állapot szöveg ("Online – bejelentkezve: user@...")

NetworkMonitor networkMonitor;
AuthManager    authManager;
SyncManager    syncManager;
```

### initSyncControls() — inicializálás

```java
void initSyncControls() {
    btnLogin    = findViewById(R.id.btnLogin);
    btnSync     = findViewById(R.id.btnSync);
    tvSyncStatus = findViewById(R.id.tvSyncStatus);

    authManager   = new AuthManager(this);
    syncManager   = new SyncManager(this, authManager);
    networkMonitor = new NetworkMonitor(this);

    btnLogin.setOnClickListener(v -> showUsernamePasswordDialog());
    btnSync.setOnClickListener(v -> triggerSync());

    networkMonitor.start(state -> {
        // callback főszálon érkezik (NetworkMonitor.setState() Handler-rel továbbít)
        updateSyncStatus();
    });
}
```

### showUsernamePasswordDialog() — bejelentkezési párbeszédablak

```java
void showUsernamePasswordDialog() {
    LinearLayout layout = new LinearLayout(this);
    layout.setOrientation(LinearLayout.VERTICAL);
    layout.setPadding(40, 20, 40, 20);

    EditText etUser = new EditText(this);
    etUser.setHint("Felhasználónév");
    EditText etPass = new EditText(this);
    etPass.setHint("Jelszó");
    etPass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

    layout.addView(etUser);
    layout.addView(etPass);

    new AlertDialog.Builder(this)
        .setTitle("Bejelentkezés – Dronterapia")
        .setView(layout)
        .setPositiveButton("Bejelentkezés", (d, w) -> {
            String user = etUser.getText().toString().trim();
            String pass = etPass.getText().toString().trim();
            if (user.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, "Add meg a felhasználónevet és jelszót", Toast.LENGTH_SHORT).show();
                return;
            }
            authManager.login(user, pass, new AuthManager.AuthCallback() {
                @Override public void onSuccess(String username) {
                    updateSyncStatus();
                    Toast.makeText(MissionPlannerActivity.this,
                        "Bejelentkezve: " + username, Toast.LENGTH_SHORT).show();
                }
                @Override public void onError(String msg) {
                    Toast.makeText(MissionPlannerActivity.this,
                        "Bejelentkezési hiba: " + msg, Toast.LENGTH_LONG).show();
                }
            });
        })
        .setNegativeButton("Mégse", null)
        .show();
}
```

### updateSyncStatus() — állapot szöveg frissítése

```java
void updateSyncStatus() {
    if (networkMonitor.getState() == NetworkMonitor.State.OFFLINE) {
        tvSyncStatus.setText("Offline");
        btnSync.setEnabled(false);
        btnLogin.setEnabled(false);
    } else {
        // ONLINE
        if (authManager.isAuthenticated()) {
            tvSyncStatus.setText("Online – bejelentkezve: " + authManager.getUsername());
            btnSync.setEnabled(true);
            btnLogin.setEnabled(false);
        } else {
            tvSyncStatus.setText("Online – nincs bejelentkezve");
            btnSync.setEnabled(false);
            btnLogin.setEnabled(true);
        }
    }
}
```

### triggerSync() — szinkronizálás indítása gombra

```java
void triggerSync() {
    if (missionRunning) {
        Toast.makeText(this, "Repülés folyamatban — sync nem indítható", Toast.LENGTH_SHORT).show();
        return;
    }
    tvSyncStatus.setText("Szinkronizálás folyamatban...");
    btnSync.setEnabled(false);

    syncManager.syncAll(new SyncManager.SyncCallback() {
        @Override public void onComplete(String msg) {
            tvSyncStatus.setText(msg);
            btnSync.setEnabled(true);
        }
        @Override public void onError(String msg) {
            tvSyncStatus.setText("Hiba: " + msg);
            btnSync.setEnabled(true);
        }
    });
}
```

### Lifecycle hook-ok

```java
@Override
protected void onResume() {
    super.onResume();
    // ... meglévő kód (DJIHelper, statusHandler) ...
    networkMonitor.start(state -> updateSyncStatus());
    updateSyncStatus();  // kezdeti állapot
}

@Override
protected void onPause() {
    super.onPause();
    // ... meglévő kód ...
    networkMonitor.stop();
}

@Override
protected void onDestroy() {
    super.onDestroy();
    // ... meglévő kód ...
    networkMonitor.stop();
}
```

---

## API végpontok

| Metódus | Végpont | Leírás |
|---------|---------|--------|
| `POST` | `/api/auth/login` | Bejelentkezés — `{"username","password"}` → `{"access_token","username"}` |
| `GET` | `/api/flight-programs` | Szerveres lista — `[{id, name, updated_at}, ...]` |
| `POST` | `/api/flight-programs` | Új repülési program feltöltése (teljes JSON body) |
| `PUT` | `/api/flight-programs/{id}` | Meglévő repülési program frissítése (teljes JSON body) |
| `GET` | `/api/flight-programs/{id}` | Egyedi repülési program letöltése (teljes JSON) |

### openConnection() helper — közös kapcsolat-beállítások

```java
private HttpsURLConnection openConnection(URL url, String token) throws Exception {
    HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
    conn.setSSLSocketFactory(getTrustAllSSLSocketFactory());
    conn.setHostnameVerifier((h, s) -> true);
    conn.setRequestProperty("Authorization", "Bearer " + token);
    conn.setRequestProperty("Accept", "application/json");
    conn.setConnectTimeout(10_000);
    conn.setReadTimeout(30_000);
    return conn;
}
```

---

## ProjectManager — dirty flag integráció

```java
// ProjectManager.saveProject() — minden mentéskor sync_pending: true
public void saveProject(String name, List<GeoPoint> polygon,
                         MissionConfig config, List<ObstacleData> obstacles) {
    // ... meglévő JSON összeállítás ...
    JSONObject metadata = new JSONObject();
    metadata.put("id", getOrGenerateId(name));
    metadata.put("name", name);
    metadata.put("source_system", "dronefly");
    metadata.put("sync_pending", true);  // dirty flag
    metadata.put("updated_at",
        new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(new Date()));
    // ...
}

// saveProjectToFile() — közvetlen fájlírás esetén is dirty flag
public void saveProjectToFile(File file, JSONObject json) {
    try {
        json.getJSONObject("metadata").put("sync_pending", true);
    } catch (JSONException e) { /* ignore */ }
    writeFile(file, json.toString());
}
```
