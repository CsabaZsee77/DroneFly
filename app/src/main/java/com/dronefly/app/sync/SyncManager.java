package com.dronefly.app.sync;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.dronefly.app.mission.ProjectManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * Repülési programok szinkronizációja a Dronterapia szerverrel.
 *
 * Feltételek a szinkronizáláshoz (mind teljesüljön):
 *   - NetworkMonitor.isOnline() == true
 *   - AuthManager.isAuthenticated() == true
 *   - activeFlightInProgress == false  (DJI misszió nem fut)
 *
 * Irányok:
 *   - UPLOAD: tablet → szerver  (sync_pending: true fájlok)
 *   - DOWNLOAD: szerver → tablet (szerveren újabb updated_at)
 *   - FULL: upload + download (tipikus sync gomb)
 */
public class SyncManager {

    private static final String TAG     = "SyncManager";
    private static final String API_BASE = "https://app.dronterapia.hu/api";
    private static final int TIMEOUT_MS  = 30000;

    public enum SyncResult {
        SUCCESS,
        SKIPPED_OFFLINE,
        SKIPPED_NO_AUTH,
        SKIPPED_FLIGHT_ACTIVE,
        ERROR
    }

    public interface SyncCallback {
        void onProgress(String message);
        void onComplete(SyncResult result, int uploaded, int downloaded, String errorMessage);
    }

    public interface DeleteCallback {
        void onResult(boolean success);
    }

    private static volatile SyncManager instance;

    private final Context appContext;
    private final AuthManager authManager;
    private final NetworkMonitor networkMonitor;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private volatile boolean activeFlightInProgress = false;

    // Android 5.1 SSL fix
    private static SSLContext trustAllSslContext;
    private static final HostnameVerifier trustAllHostnames = new HostnameVerifier() {
        @Override public boolean verify(String hostname, SSLSession session) { return true; }
    };

    static {
        try {
            TrustManager[] trustAll = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                    public void checkClientTrusted(X509Certificate[] c, String t) {}
                    public void checkServerTrusted(X509Certificate[] c, String t) {}
                }
            };
            trustAllSslContext = SSLContext.getInstance("TLS");
            trustAllSslContext.init(null, trustAll, new java.security.SecureRandom());
        } catch (Exception e) {
            Log.e(TAG, "SSL context init hiba: " + e.getMessage());
        }
    }

    // ---- Singleton ----

    private SyncManager(Context context) {
        appContext      = context.getApplicationContext();
        authManager     = AuthManager.getInstance(appContext);
        networkMonitor  = NetworkMonitor.getInstance(appContext);
    }

    public static SyncManager getInstance(Context context) {
        if (instance == null) {
            synchronized (SyncManager.class) {
                if (instance == null) {
                    instance = new SyncManager(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    // ---- DJI misszió állapot jelzés ----

    /** Hívd, amikor DJI misszió indul / ér véget. */
    public void setFlightActive(boolean active) {
        activeFlightInProgress = active;
    }

    // ---- Publikus API ----

    /**
     * Teljes szinkronizáció: feltölt minden dirty fájlt, majd letölti a szerveres újakat.
     * Az eredmény a callback-en érkezik vissza a főszálon.
     */
    public void syncAll(final SyncCallback callback) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                SyncResult guard = checkPreconditions();
                if (guard != SyncResult.SUCCESS) {
                    deliverComplete(callback, guard, 0, 0, null);
                    return;
                }
                try {
                    // Szerveres ID lista előre lekérve — feltöltésnél is kell
                    deliverProgress(callback, "Szerveres lista lekérdezése...");
                    JSONArray serverList = fetchServerList();
                    java.util.Set<String> serverIds = new java.util.HashSet<>();
                    if (serverList != null) {
                        for (int i = 0; i < serverList.length(); i++) {
                            JSONObject m = serverList.getJSONObject(i).optJSONObject("metadata");
                            if (m != null) {
                                String sid = m.optString("id", "");
                                if (!sid.isEmpty()) serverIds.add(sid);
                            }
                        }
                    }
                    int uploaded   = uploadDirtyFiles(callback, serverIds);
                    if (uploaded == -1) {
                        deliverComplete(callback, SyncResult.SKIPPED_NO_AUTH, 0, 0, null);
                        return;
                    }
                    int downloaded = downloadNewFiles(callback, serverList);
                    deliverComplete(callback, SyncResult.SUCCESS, uploaded, downloaded, null);
                } catch (Exception e) {
                    Log.e(TAG, "syncAll hiba", e);
                    deliverComplete(callback, SyncResult.ERROR, 0, 0, e.getMessage());
                }
            }
        });
    }

    /**
     * Csak feltöltés (dirty fájlok szerver felé).
     */
    public void uploadPending(final SyncCallback callback) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                SyncResult guard = checkPreconditions();
                if (guard != SyncResult.SUCCESS) {
                    deliverComplete(callback, guard, 0, 0, null);
                    return;
                }
                try {
                    deliverProgress(callback, "Szerveres lista lekérdezése...");
                    JSONArray serverList = fetchServerList();
                    java.util.Set<String> serverIds = new java.util.HashSet<>();
                    if (serverList != null) {
                        for (int i = 0; i < serverList.length(); i++) {
                            JSONObject m = serverList.getJSONObject(i).optJSONObject("metadata");
                            if (m != null) {
                                String sid = m.optString("id", "");
                                if (!sid.isEmpty()) serverIds.add(sid);
                            }
                        }
                    }
                    int uploaded = uploadDirtyFiles(callback, serverIds);
                    if (uploaded == -1) {
                        deliverComplete(callback, SyncResult.SKIPPED_NO_AUTH, 0, 0, null);
                        return;
                    }
                    deliverComplete(callback, SyncResult.SUCCESS, uploaded, 0, null);
                } catch (Exception e) {
                    Log.e(TAG, "uploadPending hiba", e);
                    deliverComplete(callback, SyncResult.ERROR, 0, 0, e.getMessage());
                }
            }
        });
    }

    public void deleteFromServer(final String id, final DeleteCallback callback) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                boolean ok = false;
                try {
                    String urlStr = API_BASE + "/flight-programs/" + id;
                    HttpsURLConnection conn = openHttps(urlStr);
                    conn.setRequestMethod("DELETE");
                    conn.setRequestProperty("Authorization", "Bearer " + authManager.getToken());
                    int status = conn.getResponseCode();
                    conn.disconnect();
                    ok = (status == 200 || status == 204);
                    if (!ok) Log.w(TAG, "deleteFromServer SIKERTELEN: HTTP " + status + " id=" + id);
                    else     Log.d(TAG, "deleteFromServer OK: id=" + id);
                } catch (Exception e) {
                    Log.e(TAG, "deleteFromServer hiba (id=" + id + ")", e);
                }
                final boolean result = ok;
                mainHandler.post(new Runnable() {
                    @Override public void run() {
                        if (callback != null) callback.onResult(result);
                    }
                });
            }
        });
    }

    // ---- Belső logika ----

    private SyncResult checkPreconditions() {
        if (!networkMonitor.isOnline())       return SyncResult.SKIPPED_OFFLINE;
        if (!authManager.isAuthenticated())   return SyncResult.SKIPPED_NO_AUTH;
        if (activeFlightInProgress)           return SyncResult.SKIPPED_FLIGHT_ACTIVE;
        return SyncResult.SUCCESS;
    }

    /**
     * Feltölti azokat a fájlokat, amelyek:
     *   - sync_pending: true (módosítva a legutóbbi szinkron óta), VAGY
     *   - source_system: "dronefly" (tableten létrehozott) ÉS ID-jük nincs a szerveren
     *
     * @param serverIds szerveren már meglévő program ID-k halmaza
     * @return feltöltött fájlok száma
     */
    private int uploadDirtyFiles(SyncCallback callback, java.util.Set<String> serverIds) throws Exception {
        File dir = ProjectManager.getProjectsDir(appContext);
        File[] files = dir.listFiles();
        if (files == null) return 0;

        int count = 0;
        for (File f : files) {
            if (!f.getName().endsWith(ProjectManager.FILE_EXT)) continue;

            JSONObject root = readJson(f);
            JSONObject meta = root.optJSONObject("metadata");
            if (meta == null) continue;

            boolean pending      = meta.optBoolean("sync_pending", false);
            String id            = meta.optString("id", "");
            String sourceSystem  = meta.optString("source_system", "");
            boolean tabletCreated = "dronefly".equals(sourceSystem);
            boolean notOnServer  = !serverIds.contains(id);

            // Feltöltjük ha dirty, VAGY tableten készült és még nincs a szerveren
            if (!pending && !(tabletCreated && notOnServer)) continue;

            String name = meta.optString("name", f.getName());
            deliverProgress(callback, "Feltöltés: " + name);

            boolean isOnServer = serverIds.contains(id);
            int result = pushToServer(id, root, isOnServer);
            if (result == 0) {
                meta.put("sync_pending", false);
                writeJson(f, root);
                count++;
                Log.d(TAG, "Feltöltve: " + name);
            } else if (result == 2) {
                Log.w(TAG, "Feltöltés megszakítva: lejárt token");
                return -1; // 401 jelzése a hívónak
            } else {
                Log.w(TAG, "Feltöltés sikertelen: " + name);
            }
        }
        return count;
    }

    /**
     * Letölti azokat a szerveres programokat, amelyek újabbak a helyi verziónál (vagy hiányoznak).
     * @param serverList már lekért szerveres lista (null esetén megpróbálja lekérni)
     * @return letöltött fájlok száma
     */
    private int downloadNewFiles(SyncCallback callback, JSONArray serverList) throws Exception {
        if (serverList == null) {
            deliverProgress(callback, "Szerveres lista lekérdezése...");
            serverList = fetchServerList();
        }
        if (serverList == null) return 0;

        File dir = ProjectManager.getProjectsDir(appContext);
        int count = 0;

        for (int i = 0; i < serverList.length(); i++) {
            JSONObject entry = serverList.getJSONObject(i);
            // A szerver a teljes struktúrát adja vissza — id/updated_at/name a metadata-ban van
            JSONObject entryMeta   = entry.optJSONObject("metadata");
            if (entryMeta == null) continue;
            String serverId        = entryMeta.optString("id", "");
            String serverUpdatedAt = entryMeta.optString("updated_at", "");
            String serverName      = entryMeta.optString("name", serverId);

            if (serverId.isEmpty()) continue;

            // Megvan-e helyben, és frissebb-e?
            File localFile = findLocalById(dir, serverId);
            if (localFile != null) {
                JSONObject localMeta = readJson(localFile).optJSONObject("metadata");
                String localUpdated  = localMeta != null ? localMeta.optString("updated_at", "") : "";
                if (serverUpdatedAt.compareTo(localUpdated) <= 0) continue; // helyi frissebb/azonos
            }

            deliverProgress(callback, "Letöltés: " + serverName);

            JSONObject serverProgram = fetchProgram(serverId);
            if (serverProgram == null) continue;

            // sync_pending: false — szerveres verzió az igazság
            JSONObject meta = serverProgram.optJSONObject("metadata");
            if (meta != null) meta.put("sync_pending", false);

            String safeName = ProjectManager.sanitizeFilename(serverName);
            File target = (localFile != null) ? localFile
                    : new File(dir, safeName + ProjectManager.FILE_EXT);

            writeJson(target, serverProgram);
            count++;
            Log.d(TAG, "Letöltve: " + serverName);
        }
        return count;
    }

    // ---- HTTP ----

    /**
     * @param isOnServer true → PUT (frissítés), false → POST (létrehozás tablet ID-vel)
     */
    /**
     * @return 0 = siker, 1 = egyéb hiba, 2 = 401 (lejárt/érvénytelen token)
     */
    private int pushToServer(String id, JSONObject program, boolean isOnServer) {
        try {
            String body   = program.toString();
            String urlStr = isOnServer ? API_BASE + "/flight-programs/" + id
                                       : API_BASE + "/flight-programs";
            String method = isOnServer ? "PUT" : "POST";

            Log.d(TAG, "pushToServer → " + method + " " + urlStr + " (body: " + body.length() + " b)");

            HttpsURLConnection conn = openHttps(urlStr);
            conn.setRequestMethod(method);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + authManager.getToken());
            conn.setDoOutput(true);
            OutputStream out = conn.getOutputStream();
            out.write(body.getBytes("UTF-8"));
            out.flush();

            int status = conn.getResponseCode();
            boolean ok = status == 200 || status == 201;

            if (!ok) {
                String responseBody = "";
                try {
                    java.io.InputStream errStream = conn.getErrorStream();
                    if (errStream != null) {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(errStream, "UTF-8"));
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) sb.append(line);
                        reader.close();
                        responseBody = sb.toString();
                    }
                } catch (Exception ignored) {}
                Log.w(TAG, "pushToServer SIKERTELEN: HTTP " + status + " | body: " + responseBody);
                conn.disconnect();
                if (status == 401) {
                    authManager.logout(null);
                    return 2;
                }
                return 1;
            }

            Log.d(TAG, "pushToServer OK: HTTP " + status);
            conn.disconnect();
            return 0;

        } catch (Exception e) {
            Log.e(TAG, "pushToServer hiba (id=" + id + ")", e);
            return 1;
        }
    }

    private JSONArray fetchServerList() {
        try {
            HttpsURLConnection conn = openHttps(API_BASE + "/flight-programs");
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + authManager.getToken());
            int status = conn.getResponseCode();
            if (status != 200) { conn.disconnect(); return null; }
            String body = readBody(conn);
            conn.disconnect();
            return new JSONArray(body);
        } catch (Exception e) {
            Log.e(TAG, "fetchServerList hiba", e);
            return null;
        }
    }

    private JSONObject fetchProgram(String id) {
        try {
            HttpsURLConnection conn = openHttps(API_BASE + "/flight-programs/" + id);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + authManager.getToken());
            int status = conn.getResponseCode();
            if (status != 200) { conn.disconnect(); return null; }
            String body = readBody(conn);
            conn.disconnect();
            return new JSONObject(body);
        } catch (Exception e) {
            Log.e(TAG, "fetchProgram hiba (id=" + id + ")", e);
            return null;
        }
    }

    // ---- Fájl segédek ----

    private File findLocalById(File dir, String id) {
        File[] files = dir.listFiles();
        if (files == null) return null;
        for (File f : files) {
            if (!f.getName().endsWith(ProjectManager.FILE_EXT)) continue;
            try {
                JSONObject meta = readJson(f).optJSONObject("metadata");
                if (meta != null && id.equals(meta.optString("id", ""))) return f;
            } catch (Exception ignored) {}
        }
        return null;
    }

    private JSONObject readJson(File f) throws Exception {
        StringBuilder sb = new StringBuilder();
        BufferedReader reader = new BufferedReader(new FileReader(f));
        String line;
        while ((line = reader.readLine()) != null) sb.append(line);
        reader.close();
        return new JSONObject(sb.toString());
    }

    private void writeJson(File f, JSONObject json) throws Exception {
        FileWriter writer = new FileWriter(f, false);
        writer.write(json.toString(2));
        writer.close();
    }

    // ---- HTTP segédek ----

    private HttpsURLConnection openHttps(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
        if (trustAllSslContext != null) {
            conn.setSSLSocketFactory(trustAllSslContext.getSocketFactory());
            conn.setHostnameVerifier(trustAllHostnames);
        }
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        return conn;
    }

    private String readBody(HttpsURLConnection conn) throws Exception {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line);
        reader.close();
        return sb.toString();
    }

    // ---- Callback dispatch ----

    private void deliverProgress(final SyncCallback callback, final String message) {
        if (callback == null) return;
        mainHandler.post(new Runnable() {
            @Override public void run() { callback.onProgress(message); }
        });
    }

    private void deliverComplete(final SyncCallback callback, final SyncResult result,
                                  final int up, final int down, final String err) {
        if (callback == null) return;
        mainHandler.post(new Runnable() {
            @Override public void run() { callback.onComplete(result, up, down, err); }
        });
    }
}
