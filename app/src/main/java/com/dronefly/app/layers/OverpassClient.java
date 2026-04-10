package com.dronefly.app.layers;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Overpass API kliens – OSM térbeli lekérdezések háttérszálon.
 * Endpoint: https://overpass-api.de/api/interpreter?data=[encoded query]
 * Singleton, ExecutorService + Handler(mainLooper) pattern (lásd: ElevationProvider).
 */
public class OverpassClient {

    private static final String TAG = "OverpassClient";
    // HTTP POST – elkerüli az HTTPS/TLS problémákat Android 5.1-en,
    // a query a request bodybe kerül (nincs URL-hossz limit sem).
    private static final String POST_URL = "http://overpass-api.de/api/interpreter";
    private static final int CONNECT_TIMEOUT_MS = 20000;
    private static final int READ_TIMEOUT_MS = 90000;

    private static OverpassClient instance;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface OverpassCallback {
        void onSuccess(String json);
        void onError(String message);
    }

    private OverpassClient() {}

    public static synchronized OverpassClient getInstance() {
        if (instance == null) {
            instance = new OverpassClient();
        }
        return instance;
    }

    /**
     * Overpass lekérdezés háttérszálon. Az eredményt (raw JSON string) a főszálon adja vissza.
     *
     * @param overpassQuery az Overpass QL lekérdezés szövege
     * @param callback      sikeres/hibás visszahívás a főszálon
     */
    public void fetch(final String overpassQuery, final OverpassCallback callback) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection conn = null;
                try {
                    URL url = new URL(POST_URL);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setDoOutput(true);
                    conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
                    conn.setRequestProperty("Accept", "application/json");
                    conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
                    conn.setReadTimeout(READ_TIMEOUT_MS);

                    // Query a POST bodybe: data=<url-encoded query>
                    String body = "data=" + URLEncoder.encode(overpassQuery, "UTF-8");
                    byte[] bodyBytes = body.getBytes("UTF-8");
                    conn.setFixedLengthStreamingMode(bodyBytes.length);
                    conn.getOutputStream().write(bodyBytes);
                    conn.getOutputStream().flush();

                    int responseCode = conn.getResponseCode();
                    if (responseCode != 200) {
                        final String msg = "Overpass API hiba: HTTP " + responseCode;
                        Log.e(TAG, msg);
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                callback.onError(msg);
                            }
                        });
                        return;
                    }

                    StringBuilder sb = new StringBuilder();
                    BufferedReader br = new BufferedReader(
                            new InputStreamReader(conn.getInputStream(), "UTF-8"));
                    String line;
                    while ((line = br.readLine()) != null) {
                        sb.append(line);
                    }
                    br.close();

                    final String json = sb.toString();
                    Log.d(TAG, "Overpass válasz: " + json.length() + " karakter");

                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onSuccess(json);
                        }
                    });

                } catch (final Throwable t) {
                    Log.e(TAG, "Overpass lekérdezés hiba: " + t.getMessage(), t);
                    final String msg = "Overpass hiba: " + t.getMessage();
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onError(msg);
                        }
                    });
                } finally {
                    if (conn != null) {
                        conn.disconnect();
                    }
                }
            }
        });
    }
}
