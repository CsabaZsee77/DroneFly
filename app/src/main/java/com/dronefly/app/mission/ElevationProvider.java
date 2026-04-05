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
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Domborzati magassági adatok lekérdezése az Open-Elevation API-ból (SRTM ~30m felbontás).
 * POST /api/v1/lookup  →  [{"latitude":..., "longitude":..., "elevation":...}, ...]
 *
 * Batch méret: max 200 pont/kérés (API korlát), szükség esetén daraboljuk.
 */
public class ElevationProvider {

    private static final String TAG = "ElevationProvider";
    private static final String API_URL = "https://api.open-elevation.com/api/v1/lookup";
    private static final int BATCH_SIZE = 200;
    private static final int TIMEOUT_MS = 15000;

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

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

                // Batch-ekre bontás
                for (int batchStart = 0; batchStart < waypoints.size(); batchStart += BATCH_SIZE) {
                    int batchEnd = Math.min(batchStart + BATCH_SIZE, waypoints.size());

                    // JSON body összeállítása
                    JSONArray locations = new JSONArray();
                    for (int i = batchStart; i < batchEnd; i++) {
                        JSONObject loc = new JSONObject();
                        loc.put("latitude", waypoints.get(i).latitude);
                        loc.put("longitude", waypoints.get(i).longitude);
                        locations.put(loc);
                    }
                    JSONObject body = new JSONObject();
                    body.put("locations", locations);

                    // HTTP POST
                    HttpURLConnection conn = (HttpURLConnection) new URL(API_URL).openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.setRequestProperty("Accept", "application/json");
                    conn.setDoOutput(true);
                    conn.setConnectTimeout(TIMEOUT_MS);
                    conn.setReadTimeout(TIMEOUT_MS);

                    try (OutputStream os = conn.getOutputStream()) {
                        os.write(body.toString().getBytes("UTF-8"));
                    }

                    int responseCode = conn.getResponseCode();
                    if (responseCode != 200) {
                        final String msg = "API hiba: HTTP " + responseCode;
                        mainHandler.post(() -> callback.onError(msg));
                        return;
                    }

                    // Válasz olvasása
                    StringBuilder sb = new StringBuilder();
                    try (BufferedReader br = new BufferedReader(
                            new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
                        String line;
                        while ((line = br.readLine()) != null) sb.append(line);
                    }

                    // JSON parse
                    JSONObject response = new JSONObject(sb.toString());
                    JSONArray results = response.getJSONArray("results");
                    for (int i = 0; i < results.length(); i++) {
                        elevations[batchStart + i] = results.getJSONObject(i).getDouble("elevation");
                    }

                    conn.disconnect();
                    Log.d(TAG, "Batch lekérdezve: " + batchStart + "-" + batchEnd
                            + " (" + results.length() + " pont)");
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
            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
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

    /**
     * Domborzatkövetés alkalmazása waypoint listára.
     *
     * Képlet: waypoint_alt = baseAGL + (terrain_elev[i] - terrain_elev[takeoff])
     *
     * @param waypoints         A waypointok listája (módosítja az altitudeM mezőt!)
     * @param elevations        Tengerszint feletti magasságok (Open-Elevation API-ból)
     * @param takeoffElevation  A felszállási pont tengerszint feletti magassága
     * @param baseAGL           Alap repülési magasság a felszállási pont felett (m)
     */
    public static void applyTerrainCorrection(List<WaypointData> waypoints,
                                               double[] elevations,
                                               double takeoffElevation,
                                               double baseAGL) {
        for (int i = 0; i < waypoints.size(); i++) {
            double terrainDelta = elevations[i] - takeoffElevation;
            float correctedAlt = (float) (baseAGL + terrainDelta);
            // Minimum 10m biztonság
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
