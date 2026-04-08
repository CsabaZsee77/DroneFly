package com.dronefly.app.layers;

import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Polygon;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Légtér adatok (CTR, TMA, ATZ, D, R, P) megjelenítése OSMDroid MapView-on.
 * Forrás: OpenAIP API v2 – GeoJSON FeatureCollection.
 *
 * Szín kód (properties.type):
 *  - 0 CTR:        piros   (#FFFF2200 stroke 3dp, #33FF2200 fill)
 *  - 1 TMA:        lila    (#FFAA00FF stroke 2dp, #26AA00FF fill)
 *  - 2 ATZ:        narancs (#FFFF6600 stroke 2dp, #26FF6600 fill)
 *  - 3 Danger:     piros   (#FFFF0000 stroke 2.5dp, #40FF0000 fill)
 *  - 4 Restricted: narancspiros (#FFFF4400 stroke 2.5dp, #33FF4400 fill)
 *  - 5 Prohibited: sötétpiros (#FFCC0000 stroke 3dp, #4DCC0000 fill)
 *  - default:      narancs (#FFFF8800 stroke 2dp, #26FF8800 fill)
 *
 * GeoJSON koordináta rend: [longitude, latitude] → GeoPoint(lat, lon)-ra fordítva.
 */
public class AirspaceLayer {

    private static final String TAG = "AirspaceLayer";
    // OpenAIP v2 airspace endpoint: bbox=west,south,east,north
    private static final String API_BASE = "https://api.airspace.openaip.net/api/airspaces";
    private static final int CONNECT_TIMEOUT_MS = 20000;
    private static final int READ_TIMEOUT_MS = 30000;

    private final MapView mapView;
    private final String apiKey;
    private final List<Polygon> overlays = new ArrayList<>();
    private boolean visible = false;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public AirspaceLayer(MapView mapView, String apiKey) {
        this.mapView = mapView;
        this.apiKey = apiKey;
    }

    /**
     * Réteg be/ki kapcsolása. Ha látható: elrejti és törli. Ha nem látható: lekérdezi és megjeleníti.
     * Az onDone Runnable mindig a főszálon fut le (sikeres megjelenítés és hiba esetén is).
     */
    public void toggle(final BoundingBox bbox, final Runnable onDone) {
        if (apiKey == null || apiKey.isEmpty()) {
            Toast.makeText(mapView.getContext(),
                    "OpenAIP API kulcs szükséges",
                    Toast.LENGTH_LONG).show();
            if (onDone != null) {
                mainHandler.post(onDone);
            }
            return;
        }

        if (visible) {
            clear();
            visible = false;
            if (onDone != null) {
                mainHandler.post(onDone);
            }
            return;
        }

        // Nem látható → lekérdezés és megjelenítés
        fetchAndShow(bbox, onDone);
    }

    public boolean isVisible() {
        return visible;
    }

    /** Eltávolítja az összes overlay-t a térképről és a belső listából. */
    public void clear() {
        for (Polygon p : overlays) {
            mapView.getOverlays().remove(p);
        }
        overlays.clear();
        mapView.invalidate();
    }

    // -------------------------------------------------------------------------
    // Belső segédmetódusok
    // -------------------------------------------------------------------------

    private void fetchAndShow(final BoundingBox bbox, final Runnable onDone) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection conn = null;
                try {
                    // OpenAIP bbox sorrend: west,south,east,north
                    String bboxParam = bbox.getLonWest() + "," + bbox.getLatSouth()
                            + "," + bbox.getLonEast() + "," + bbox.getLatNorth();
                    String urlStr = API_BASE + "?bbox=" + bboxParam + "&apiKey=" + apiKey;

                    URL url = new URL(urlStr);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("Accept", "application/json");
                    conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
                    conn.setReadTimeout(READ_TIMEOUT_MS);

                    int responseCode = conn.getResponseCode();
                    if (responseCode != 200) {
                        final String msg = "OpenAIP API hiba: HTTP " + responseCode;
                        Log.e(TAG, msg);
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(mapView.getContext(), msg, Toast.LENGTH_LONG).show();
                                if (onDone != null) onDone.run();
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
                    Log.d(TAG, "OpenAIP válasz: " + json.length() + " karakter");

                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            addPolygonsFromGeoJson(json);
                            visible = true;
                            if (onDone != null) onDone.run();
                        }
                    });

                } catch (final Throwable t) {
                    Log.e(TAG, "OpenAIP lekérdezés hiba: " + t.getMessage(), t);
                    final String msg = "Légtér betöltési hiba: " + t.getMessage();
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(mapView.getContext(), msg, Toast.LENGTH_LONG).show();
                            if (onDone != null) onDone.run();
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

    /**
     * GeoJSON FeatureCollection parse + Polygon overlays létrehozása.
     * Hívása csak a főszálon történik.
     */
    private void addPolygonsFromGeoJson(String json) {
        try {
            JSONObject root = new JSONObject(json);
            JSONArray features = root.getJSONArray("features");

            for (int i = 0; i < features.length(); i++) {
                JSONObject feature = features.getJSONObject(i);

                JSONObject properties = feature.optJSONObject("properties");
                if (properties == null) continue;

                int type = properties.optInt("type", -1);

                JSONObject geometry = feature.optJSONObject("geometry");
                if (geometry == null) continue;

                String geomType = geometry.optString("type", "");
                if (!"Polygon".equals(geomType)) continue;

                JSONArray coordinateRings = geometry.optJSONArray("coordinates");
                if (coordinateRings == null || coordinateRings.length() == 0) continue;

                // Az első ring az exterior ring
                JSONArray ring = coordinateRings.getJSONArray(0);
                if (ring.length() < 3) continue;

                List<GeoPoint> points = new ArrayList<>();
                for (int j = 0; j < ring.length(); j++) {
                    JSONArray coord = ring.getJSONArray(j);
                    // GeoJSON: [longitude, latitude]
                    double lon = coord.getDouble(0);
                    double lat = coord.getDouble(1);
                    points.add(new GeoPoint(lat, lon));
                }

                if (points.size() < 3) continue;

                Polygon polygon = new Polygon();
                polygon.setPoints(points);
                polygon.getFillPaint().setColor(getFillColor(type));
                polygon.getOutlinePaint().setColor(getStrokeColor(type));
                polygon.getOutlinePaint().setStrokeWidth(dpToPx(getStrokeWidth(type)));

                mapView.getOverlays().add(polygon);
                overlays.add(polygon);
            }

            mapView.invalidate();
            Log.d(TAG, "Légtér megjelenítve: " + overlays.size() + " polygon");

        } catch (Throwable t) {
            Log.e(TAG, "GeoJSON parse hiba: " + t.getMessage(), t);
            Toast.makeText(mapView.getContext(),
                    "Légtér parse hiba: " + t.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private int getFillColor(int type) {
        switch (type) {
            case 0: return Color.parseColor("#33FF2200"); // CTR
            case 1: return Color.parseColor("#26AA00FF"); // TMA
            case 2: return Color.parseColor("#26FF6600"); // ATZ
            case 3: return Color.parseColor("#40FF0000"); // Danger
            case 4: return Color.parseColor("#33FF4400"); // Restricted
            case 5: return Color.parseColor("#4DCC0000"); // Prohibited
            default: return Color.parseColor("#26FF8800");
        }
    }

    private int getStrokeColor(int type) {
        switch (type) {
            case 0: return Color.parseColor("#FFFF2200"); // CTR
            case 1: return Color.parseColor("#FFAA00FF"); // TMA
            case 2: return Color.parseColor("#FFFF6600"); // ATZ
            case 3: return Color.parseColor("#FFFF0000"); // Danger
            case 4: return Color.parseColor("#FFFF4400"); // Restricted
            case 5: return Color.parseColor("#FFCC0000"); // Prohibited
            default: return Color.parseColor("#FFFF8800");
        }
    }

    private float getStrokeWidth(int type) {
        switch (type) {
            case 0: return 3.0f; // CTR
            case 3: return 2.5f; // Danger
            case 4: return 2.5f; // Restricted
            case 5: return 3.0f; // Prohibited
            default: return 2.0f;
        }
    }

    private float dpToPx(float dp) {
        float density = mapView.getContext().getResources().getDisplayMetrics().density;
        return dp * density;
    }
}
