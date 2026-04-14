package com.dronefly.app;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * NOAA Space Weather Prediction Center — planetáris Kp-index lekérő.
 *
 * Endpoint: https://services.swpc.noaa.gov/products/noaa-planetary-k-index.json
 * Frissítés: 3 óránként (NOAA mérési ciklus), az app 10 percenként kérdezi le.
 *
 * Válasz formátum (aktuális): [ {"time_tag":"...","Kp":0.33,...}, ... ]
 * Régi formátum (visszafelé kompatibilis): [ ["time_tag","kp_index"], [...] ]
 *
 * Kp-index értelmezése (drónosok számára):
 *   0–2 : Nyugodt           → zöld  – nincs hatás
 *   3–4 : Enyhe zavar       → sárga – minimális hatás
 *   5   : Kis vihar (G1)    → narancs – compass warning lehetséges
 *   6+  : Közepes/súlyos    → piros  – GPS/compass problémák lehetségesek
 *
 * API kulcs nem szükséges. Nem koordinátafüggő (globális index).
 */
public class KpIndexProvider {

    private static final String TAG = "KpIndexProvider";
    private static final String ENDPOINT =
            "https://services.swpc.noaa.gov/products/noaa-planetary-k-index.json";
    private static final int TIMEOUT_MS = 10_000;

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler   = new Handler(Looper.getMainLooper());

    public interface KpCallback {
        /** @param kp Kp-index (0–9), vagy -1 ha nem elérhető / nincs internet */
        void onResult(int kp);
    }

    /** Aszinkron lekérés – az eredmény mindig a főszálon érkezik vissza. */
    public static void fetch(KpCallback callback) {
        executor.submit(() -> {
            int kp = fetchSync();
            mainHandler.post(() -> callback.onResult(kp));
        });
    }

    private static int fetchSync() {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(ENDPOINT).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setRequestProperty("Accept", "application/json");

            if (conn.getResponseCode() != 200) return -1;

            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(conn.getInputStream()))) {
                String line;
                while ((line = r.readLine()) != null) sb.append(line);
            }

            JSONArray arr = new JSONArray(sb.toString());
            if (arr.length() < 1) return -1;

            double kpFloat;
            Object last = arr.get(arr.length() - 1);
            if (last instanceof org.json.JSONObject) {
                // Új formátum: {"time_tag":"...","Kp":0.33,...}
                kpFloat = ((org.json.JSONObject) last).getDouble("Kp");
            } else {
                // Régi formátum: ["time_tag", "2.00"]
                kpFloat = Double.parseDouble(((JSONArray) last).getString(1).trim());
            }
            return (int) Math.round(kpFloat);

        } catch (Throwable t) {
            Log.e(TAG, "Kp lekérés sikertelen: " + t.getMessage());
            return -1;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}
