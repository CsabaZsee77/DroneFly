package com.dronefly.app.ui;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.Rect;

import com.dronefly.app.model.Block;
import com.dronefly.app.model.BlockStatus;

import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.Projection;
import org.osmdroid.views.overlay.Overlay;

import java.util.ArrayList;
import java.util.List;

/**
 * OSMDroid térképi overlay az M07 blokk-felosztás vizualizációjához.
 *
 * Funkciók:
 *  - Cellák rajzolása státusz-szín szerint (szürke/narancs/zöld)
 *  - Kiválasztott blokk vastagabb sárga kerettel
 *  - Felirat a cella közepén (id)
 *  - hitTest(GeoPoint) → Block vagy null pont-a-poligonban teszttel
 *
 * Lásd: docs/M07_BLOKK_FELOSZTAS/M07_L3_ALLAPOTGEP_ES_ENGINE.md §BlockOverlay
 */
public class BlockOverlay extends Overlay {

    // Az L3 §Szín konstansok táblázat szerint
    private static final int COLOR_NOT_STARTED = 0x80808080;
    private static final int COLOR_IN_PROGRESS = 0x80FFA500;
    private static final int COLOR_DONE        = 0x8000C850;
    private static final int COLOR_STROKE_NORMAL   = 0xFF404040;
    private static final int COLOR_STROKE_SELECTED = 0xFFFFFF00;
    private static final int COLOR_TEXT            = 0xFFFFFFFF;
    private static final int COLOR_TEXT_SHADOW     = 0xFF000000;

    private final MapView mapView;
    private List<Block>  blocks = new ArrayList<>();
    private Block        selectedBlock;

    private final Paint fillNotStarted;
    private final Paint fillInProgress;
    private final Paint fillDone;
    private final Paint strokeNormal;
    private final Paint strokeSelected;
    private final Paint textPaint;

    public BlockOverlay(MapView mapView) {
        this.mapView = mapView;

        fillNotStarted = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillNotStarted.setColor(COLOR_NOT_STARTED);
        fillNotStarted.setStyle(Paint.Style.FILL);

        fillInProgress = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillInProgress.setColor(COLOR_IN_PROGRESS);
        fillInProgress.setStyle(Paint.Style.FILL);

        fillDone = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillDone.setColor(COLOR_DONE);
        fillDone.setStyle(Paint.Style.FILL);

        float density = mapView.getResources().getDisplayMetrics().density;

        strokeNormal = new Paint(Paint.ANTI_ALIAS_FLAG);
        strokeNormal.setColor(COLOR_STROKE_NORMAL);
        strokeNormal.setStyle(Paint.Style.STROKE);
        strokeNormal.setStrokeWidth(2f * density);

        strokeSelected = new Paint(Paint.ANTI_ALIAS_FLAG);
        strokeSelected.setColor(COLOR_STROKE_SELECTED);
        strokeSelected.setStyle(Paint.Style.STROKE);
        strokeSelected.setStrokeWidth(4f * density);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(COLOR_TEXT);
        textPaint.setTextSize(14f * density);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setShadowLayer(2f * density, 0, 0, COLOR_TEXT_SHADOW);
    }

    public void setBlocks(List<Block> blocks) {
        this.blocks = (blocks != null) ? blocks : new ArrayList<Block>();
        // Ha a régebbi kiválasztás már nincs az új listában → kioltjuk
        if (selectedBlock != null) {
            boolean stillThere = false;
            for (Block b : this.blocks) {
                if (b.row == selectedBlock.row && b.col == selectedBlock.col) {
                    selectedBlock = b;
                    stillThere = true;
                    break;
                }
            }
            if (!stillThere) selectedBlock = null;
        }
        mapView.invalidate();
    }

    public List<Block> getBlocks() { return blocks; }

    public Block getSelected() { return selectedBlock; }

    public void setSelected(Block b) {
        this.selectedBlock = b;
        mapView.invalidate();
    }

    public void clear() {
        this.blocks = new ArrayList<>();
        this.selectedBlock = null;
        mapView.invalidate();
    }

    /**
     * Pont-a-poligonban teszt minden blokk cellPolygon-ja ellen.
     * Visszaadja a találatot, vagy null-t ha nincs.
     */
    public Block hitTest(GeoPoint tap) {
        if (blocks == null || tap == null) return null;
        for (Block b : blocks) {
            if (pointInPolygon(tap, b.cellPolygon)) return b;
        }
        return null;
    }

    /** Ray-casting algoritmus GPS koordinátákon (lokális approximáció pontos). */
    private static boolean pointInPolygon(GeoPoint p, List<GeoPoint> poly) {
        if (poly == null || poly.size() < 3) return false;
        double x = p.getLongitude();
        double y = p.getLatitude();
        boolean inside = false;
        int n = poly.size();
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double xi = poly.get(i).getLongitude(), yi = poly.get(i).getLatitude();
            double xj = poly.get(j).getLongitude(), yj = poly.get(j).getLatitude();
            boolean intersect = ((yi > y) != (yj > y))
                    && (x < (xj - xi) * (y - yi) / (yj - yi + 1e-12) + xi);
            if (intersect) inside = !inside;
        }
        return inside;
    }

    @Override
    public void draw(Canvas canvas, MapView m, boolean shadow) {
        if (shadow || blocks == null || blocks.isEmpty()) return;
        Projection proj = m.getProjection();

        Point pt = new Point();
        Path path = new Path();
        Rect textBounds = new Rect();

        for (Block b : blocks) {
            if (b.cellPolygon == null || b.cellPolygon.size() < 3) continue;

            path.reset();
            for (int i = 0; i < b.cellPolygon.size(); i++) {
                proj.toPixels(b.cellPolygon.get(i), pt);
                if (i == 0) path.moveTo(pt.x, pt.y);
                else        path.lineTo(pt.x, pt.y);
            }
            path.close();

            canvas.drawPath(path, paintForStatus(b.status));
            boolean isSelected = (selectedBlock != null
                    && selectedBlock.row == b.row
                    && selectedBlock.col == b.col);
            canvas.drawPath(path, isSelected ? strokeSelected : strokeNormal);

            // Felirat
            if (b.cellCenter != null) {
                proj.toPixels(b.cellCenter, pt);
                textPaint.getTextBounds(b.id, 0, b.id.length(), textBounds);
                canvas.drawText(b.id, pt.x, pt.y + textBounds.height() / 2f, textPaint);
            }
        }
    }

    private Paint paintForStatus(BlockStatus s) {
        if (s == null) return fillNotStarted;
        switch (s) {
            case IN_PROGRESS: return fillInProgress;
            case DONE:        return fillDone;
            case NOT_STARTED:
            default:          return fillNotStarted;
        }
    }
}
