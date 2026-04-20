package com.dronefly.app.mission;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.dronefly.app.model.WaypointData;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.security.cert.X509Certificate;
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
 * Domborzati magassági adatok lekérdezése az Open-Elevation API-ból (SRTM ~30m felbontás).
 * POST /api/v1/lookup  →  [{"latitude":..., "longitude":..., "elevation":...}, ...]
 *
 * Batch méret: max 200 pont/kérés (API korlát), szükség esetén daraboljuk.
 *
 * Android 5.1 (Crystal Sky) nem ismeri a modern Let's Encrypt / DigiCert gyökér-tanúsítványokat,
 * ezért permissive TrustManager-t alkalmazunk — az app nem nyilvános Play Store alkalmazás.
 */
public class ElevationProvider {

    private static final String TAG = "ElevationProvider";
    private static final String API_URL = "https://api.open-elevation.com/api/v1/lookup";
    private static final int BATCH_SIZE  = 100;   // kisebb batch = megbízhatóbb API válasz
    private static final int TIMEOUT_MS  = 60000; // 60 mp / kérés (nagy terület esetén lassú API)
    private static final int MAX_RETRIES = 3;     // újrapróbálkozás timeout/hiba esetén
    private static final int RETRY_DELAY_MS = 2000;

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Android 5.1 SSL fix: fogad minden tanúsítványt
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

    public interface ElevationCallback {
        void onSuccess(double[] elevations);
        void onError(String message);
    }

    /**
     * Waypoint lista domborzati magasságainak lekérdezése háttérszálon.
     * Az eredmény egy double[] tömb, ahol elevations[i] = waypoints.get(i) tengerszint feletti magassága méterben.
     */
    public static void fetchElevations(List<WaypointData> waypoints, ElevationCallback callback) {
        executor.execute(() -> {
            try {
                double[] elevations = new double[waypoints.size()];
                int totalBatches = (int) Math.ceil((double) waypoints.size() / BATCH_SIZE);

                for (int batchStart = 0; batchStart < waypoints.size(); batchStart += BATCH_SIZE) {
                    int batchEnd = Math.min(batchStart + BATCH_SIZE, waypoints.size());
                    int batchIdx = batchStart / BATCH_SIZE + 1;

                    JSONArray locations = new JSONArray();
                    for (int i = batchStart; i < batchEnd; i++) {
                        JSONObject loc = new JSONObject();
                        loc.put("latitude", waypoints.get(i).latitude);
                        loc.put("longitude", waypoints.get(i).longitude);
                        locations.put(loc);
                    }
                    JSONObject body = new JSONObject();
                    body.put("locations", locations);
                    byte[] bodyBytes = body.toString().getBytes("UTF-8");

                    // Retry logika: MAX_RETRIES próbálkozás batch-enként
                    boolean batchOk = false;
                    String lastError = "ismeretlen hiba";
                    for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
                        try {
                            HttpsURLConnection conn = openConnection(API_URL);
                            conn.setRequestMethod("POST");
                            conn.setRequestProperty("Content-Type", "application/json");
                            conn.setRequestProperty("Accept", "application/json");
                            conn.setDoOutput(true);
                            conn.setConnectTimeout(TIMEOUT_MS);
                            conn.setReadTimeout(TIMEOUT_MS);

                            try (OutputStream os = conn.getOutputStream()) {
                                os.write(bodyBytes);
                            }

                            int responseCode = conn.getResponseCode();
                            if (responseCode != 200) {
                                lastError = "HTTP " + responseCode;
                                conn.disconnect();
                                if (attempt < MAX_RETRIES) Thread.sleep(RETRY_DELAY_MS);
                                continue;
                            }

                            StringBuilder sb = new StringBuilder();
                            try (BufferedReader br = new BufferedReader(
                                    new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
                                String line;
                                while ((line = br.readLine()) != null) sb.append(line);
                            }

                            JSONObject response = new JSONObject(sb.toString());
                            JSONArray results = response.getJSONArray("results");
                            for (int i = 0; i < results.length(); i++) {
                                elevations[batchStart + i] =
                                    results.getJSONObject(i).getDouble("elevation");
                            }

                            conn.disconnect();
                            Log.d(TAG, "Batch " + batchIdx + "/" + totalBatches
                                + " kész (" + (batchEnd - batchStart) + " pont)");
                            batchOk = true;
                            break;

                        } catch (Throwable t) {
                            lastError = t.getMessage();
                            Log.w(TAG, "Batch " + batchIdx + " próba " + attempt
                                + "/" + MAX_RETRIES + " hiba: " + lastError);
                            if (attempt < MAX_RETRIES) Thread.sleep(RETRY_DELAY_MS);
                        }
                    }

                    if (!batchOk) {
                        final String msg = "SRTM letöltési hiba (batch " + batchIdx
                            + "/" + totalBatches + "): " + lastError;
                        mainHandler.post(() -> callback.onError(msg));
                        return;
                    }
                }

                mainHandler.post(() -> callback.onSuccess(elevations));

            } catch (Throwable t) {
                Log.e(TAG, "Elevation lekérdezés hiba: " + t.getMessage(), t);
                final String msg = "Domborzati adat hiba: " + t.getMessage();
                mainHandler.post(() -> callback.onError(msg));
            }
        });
    }

    /**
     * Egyetlen pont tengerszint feletti magasságának lekérdezése (szinkron, háttérszálon hívd!).
     * @return magasság méterben, vagy Double.NaN ha nem elérhető
     */
    public static double fetchSingleElevation(double lat, double lon) {
        try {
            String urlStr = API_URL + "?locations=" + lat + "," + lon;
            HttpsURLConnection conn = openConnection(urlStr);
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);

            if (conn.getResponseCode() != 200) return Double.NaN;

            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
            }

            JSONObject response = new JSONObject(sb.toString());
            JSONArray results = response.getJSONArray("results");
            double elev = results.getJSONObject(0).getDouble("elevation");
            conn.disconnect();
            return elev;
        } catch (Throwable t) {
            Log.e(TAG, "Single elevation hiba: " + t.getMessage());
            return Double.NaN;
        }
    }

    private static HttpsURLConnection openConnection(String urlStr) throws Exception {
        HttpsURLConnection conn = (HttpsURLConnection) new URL(urlStr).openConnection();
        if (trustAllSslContext != null) {
            conn.setSSLSocketFactory(trustAllSslContext.getSocketFactory());
            conn.setHostnameVerifier(trustAllHostnames);
        }
        return conn;
    }

    /**
     * Domborzatkövetés alkalmazása waypoint listára.
     *
     * Képlet: waypoint_alt = baseAGL + (terrain_elev[i] - terrain_elev[takeoff])
     */
    public static void applyTerrainCorrection(List<WaypointData> waypoints,
                                               double[] elevations,
                                               double takeoffElevation,
                                               double baseAGL) {
        for (int i = 0; i < waypoints.size(); i++) {
            double terrainDelta = elevations[i] - takeoffElevation;
            float correctedAlt = (float) (baseAGL + terrainDelta);
            correctedAlt = Math.max(10f, correctedAlt);

            waypoints.get(i).altitudeM = correctedAlt;
            waypoints.get(i).terrainElevation = elevations[i];
            waypoints.get(i).hasTerrainCorrection = true;
        }
        Log.i(TAG, "Domborzatkövetés alkalmazva: " + waypoints.size()
                + " waypoint, takeoff elev=" + takeoffElevation
                + "m, base AGL=" + baseAGL + "m");
    }
}
