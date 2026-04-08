package com.dronefly.app.layers;

import android.graphics.Color;
import android.graphics.DashPathEffect;
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

import java.util.ArrayList;
import java.util.List;

/**
 * Közigazgatási határok (admin_level=8, település) megjelenítése OSMDroid MapView-on.
 * Forrás: OSM Overpass API, boundary=administrative, admin_level=8.
 *
 * Vizuális stílus:
 *  - stroke: #FFDDDDDD (világosszürke/fehér), vastagság: 1.5dp
 *  - fill: átlátszó (#00000000)
 *  - szaggatott vonal: DashPathEffect {12f, 8f}
 */
public class AdminBoundaryLayer {

    private static final String TAG = "AdminBoundaryLayer";

    private final MapView mapView;
    private final List<Polygon> overlays = new ArrayList<>();
    private boolean visible = false;
    private final OverpassClient client = OverpassClient.getInstance();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public AdminBoundaryLayer(MapView mapView) {
        this.mapView = mapView;
    }

    /**
     * Réteg be/ki kapcsolása. Ha látható: elrejti és törli. Ha nem látható: lekérdezi és megjeleníti.
     * Az onDone Runnable mindig a főszálon fut le (sikeres megjelenítés és hiba esetén is).
     */
    public void toggle(final BoundingBox bbox, final Runnable onDone) {
        if (visible) {
            clear();
            visible = false;
            if (onDone != null) {
                mainHandler.post(onDone);
            }
            return;
        }

        String query = buildQuery(bbox);
        client.fetch(query, new OverpassClient.OverpassCallback() {
            @Override
            public void onSuccess(String json) {
                addPolygonsFromJson(json);
                visible = true;
                if (onDone != null) {
                    onDone.run(); // már a főszálon vagyunk (OverpassClient garantálja)
                }
            }

            @Override
            public void onError(String message) {
                Log.e(TAG, "Fetch hiba: " + message);
                Toast.makeText(mapView.getContext(),
                        "Közigazgatási határok betöltési hiba: " + message,
                        Toast.LENGTH_LONG).show();
                if (onDone != null) {
                    onDone.run();
                }
            }
        });
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

    private String buildQuery(BoundingBox bbox) {
        // south,west,north,east
        String bboxStr = bbox.getLatSouth() + "," + bbox.getLonWest()
                + "," + bbox.getLatNorth() + "," + bbox.getLonEast();
        return "[out:json][timeout:25];\n"
                + "(\n"
                + "  relation[\"boundary\"=\"administrative\"][\"admin_level\"=\"8\"](" + bboxStr + ");\n"
                + "  way[\"boundary\"=\"administrative\"][\"admin_level\"=\"8\"](" + bboxStr + ");\n"
                + ");\n"
                + "out geom;";
    }

    /**
     * JSON parse + Polygon overlays létrehozása és hozzáadása a térképhez.
     * Hívása csak a főszálon történik (OverpassClient.onSuccess).
     */
    private void addPolygonsFromJson(String json) {
        try {
            JSONObject root = new JSONObject(json);
            JSONArray elements = root.getJSONArray("elements");

            for (int i = 0; i < elements.length(); i++) {
                JSONObject element = elements.getJSONObject(i);

                // Geometry tömb kiolvasása (way és relation esetén ugyanúgy)
                JSONArray geometry = element.optJSONArray("geometry");
                if (geometry == null || geometry.length() < 3) {
                    continue; // nem rajzolható polygon
                }

                List<GeoPoint> points = new ArrayList<>();
                for (int j = 0; j < geometry.length(); j++) {
                    JSONObject pt = geometry.getJSONObject(j);
                    double lat = pt.getDouble("lat");
                    double lon = pt.getDouble("lon");
                    points.add(new GeoPoint(lat, lon));
                }

                if (points.size() < 3) {
                    continue;
                }

                Polygon polygon = new Polygon();
                polygon.setPoints(points);

                // Átlátszó kitöltés
                polygon.getFillPaint().setColor(Color.TRANSPARENT);

                // Szaggatott szürke körvonal
                float strokeWidthPx = dpToPx(1.5f);
                polygon.getOutlinePaint().setColor(Color.parseColor("#FFDDDDDD"));
                polygon.getOutlinePaint().setStrokeWidth(strokeWidthPx);
                polygon.getOutlinePaint().setPathEffect(
                        new DashPathEffect(new float[]{12f, 8f}, 0));

                mapView.getOverlays().add(polygon);
                overlays.add(polygon);
            }

            mapView.invalidate();
            Log.d(TAG, "Közigazgatási határok megjelenítve: " + overlays.size() + " polygon");

        } catch (Throwable t) {
            Log.e(TAG, "JSON parse hiba: " + t.getMessage(), t);
            Toast.makeText(mapView.getContext(),
                    "Közigazgatási határok parse hiba: " + t.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private float dpToPx(float dp) {
        float density = mapView.getContext().getResources().getDisplayMetrics().density;
        return dp * density;
    }
}
