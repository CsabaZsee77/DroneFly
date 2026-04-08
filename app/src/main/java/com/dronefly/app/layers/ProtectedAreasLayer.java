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

import java.util.ArrayList;
import java.util.List;

/**
 * Natura 2000 és nemzeti park területek megjelenítése OSMDroid MapView-on.
 * Forrás: OSM Overpass API, boundary=protected_area, protect_class 2/4/5/6.
 *
 * Szín kód:
 *  - protect_class=2 (nemzeti park):   zöld   (#FF00AA44 stroke, #4D00AA44 fill)
 *  - protect_class=4 (Natura 2000 SAC): narancs (#FFFF8800 stroke, #40FF8800 fill)
 *  - protect_class=5/6 (egyéb):        barna  (#FFAA6600 stroke, #33AA6600 fill)
 *  - default:                           sárga  (#FFEECC00 stroke, #40EECC00 fill)
 */
public class ProtectedAreasLayer {

    private static final String TAG = "ProtectedAreasLayer";

    private final MapView mapView;
    private final List<Polygon> overlays = new ArrayList<>();
    private boolean visible = false;
    private final OverpassClient client = OverpassClient.getInstance();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public ProtectedAreasLayer(MapView mapView) {
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
                        "Védett területek betöltési hiba: " + message,
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
                + "  relation[\"boundary\"=\"protected_area\"][\"protect_class\"~\"^(2|4|5|6)$\"](" + bboxStr + ");\n"
                + "  way[\"boundary\"=\"protected_area\"][\"protect_class\"~\"^(2|4|5|6)$\"](" + bboxStr + ");\n"
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

                // protect_class tag
                String protectClass = "";
                JSONObject tags = element.optJSONObject("tags");
                if (tags != null) {
                    protectClass = tags.optString("protect_class", "");
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
                polygon.getFillPaint().setColor(getFillColor(protectClass));
                polygon.getOutlinePaint().setColor(getStrokeColor(protectClass));
                polygon.getOutlinePaint().setStrokeWidth(dpToPx(getStrokeWidth(protectClass)));

                mapView.getOverlays().add(polygon);
                overlays.add(polygon);
            }

            mapView.invalidate();
            Log.d(TAG, "Védett területek megjelenítve: " + overlays.size() + " polygon");

        } catch (Throwable t) {
            Log.e(TAG, "JSON parse hiba: " + t.getMessage(), t);
            Toast.makeText(mapView.getContext(),
                    "Védett területek parse hiba: " + t.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private int getFillColor(String protectClass) {
        if ("2".equals(protectClass)) return Color.parseColor("#4D00AA44");
        if ("4".equals(protectClass)) return Color.parseColor("#40FF8800");
        if ("5".equals(protectClass) || "6".equals(protectClass)) return Color.parseColor("#33AA6600");
        return Color.parseColor("#40EECC00");
    }

    private int getStrokeColor(String protectClass) {
        if ("2".equals(protectClass)) return Color.parseColor("#FF00AA44");
        if ("4".equals(protectClass)) return Color.parseColor("#FFFF8800");
        if ("5".equals(protectClass) || "6".equals(protectClass)) return Color.parseColor("#FFAA6600");
        return Color.parseColor("#FFEECC00");
    }

    private float getStrokeWidth(String protectClass) {
        if ("2".equals(protectClass)) return 2.5f;
        return 2.0f;
    }

    private float dpToPx(float dp) {
        float density = mapView.getContext().getResources().getDisplayMetrics().density;
        return dp * density;
    }
}
