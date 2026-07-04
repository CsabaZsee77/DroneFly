package com.dronefly.app.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

/**
 * Interaktív vonalzó nézet GSD-kalibrációhoz (M09_L1 §10, M09_L3 `GsdRulerView`).
 *
 * A képet fit-to-view jeleníti meg, két húzható végponttal. A mérés a bitmap
 * saját px-terében történik (felbontás-invariancia, M09_L4 §7): a
 * {@link #getLinePixelLength()} és a {@link #getBitmapWidth()} ugyanabban a
 * px-térben van.
 *
 * Középkereszt + él-figyelmeztetés (M09_L1 §10.3 4. szabály): a mérés a kép
 * közepére szimmetrikusan a legpontosabb (objektív-torzítás, gimbal-dőlés,
 * parallaxis a széleken).
 */
public class GsdRulerView extends View {

    public interface OnChangeListener {
        void onLineChanged();
    }

    private Bitmap bitmap;
    private final float[] p1 = new float[2];   // bitmap-px koordináták
    private final float[] p2 = new float[2];
    private boolean initialized = false;

    private float scale = 1f;
    private float offX = 0f, offY = 0f;
    private int dragging = -1;

    private final Paint imgPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint crossPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint loupeBorder = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint loupeCross = new Paint(Paint.ANTI_ALIAS_FLAG);

    // Lupe (nagyító) a mozgatott végpont pontos illesztéséhez — a sarokban
    // jelenik meg húzás közben (M09: az ujj alá nézve pixelre pontos igazítás)
    private static final float LOUPE_SIZE = 300f;
    private static final float LOUPE_MAG = 2.5f;
    private static final float LOUPE_MARGIN = 24f;

    private OnChangeListener listener;

    public GsdRulerView(Context ctx) {
        super(ctx);
        linePaint.setColor(0xFFFFEE00);
        linePaint.setStrokeWidth(4f);
        linePaint.setStyle(Paint.Style.STROKE);
        handlePaint.setColor(0xFFFF6600);
        crossPaint.setColor(0x8800FFFF);
        crossPaint.setStrokeWidth(2f);
        loupeBorder.setColor(0xFFFFAA00);
        loupeBorder.setStyle(Paint.Style.STROKE);
        loupeBorder.setStrokeWidth(4f);
        loupeCross.setColor(0xFFFF3333);
        loupeCross.setStrokeWidth(2f);
    }

    public void setBitmap(Bitmap bmp) {
        this.bitmap = bmp;
        // A végpontok bitmap-px koordinátákban vannak — nem függenek a nézet
        // méretétől, ezért azonnal inicializálhatók (a live-mérés így már az első
        // megjelenítéskor helyes hosszt ad, nem 0-t). ensureLayout() csak a
        // rajzoló skálát/eltolást számolja.
        if (bmp != null) {
            float cy = bmp.getHeight() / 2f;
            p1[0] = bmp.getWidth() * 0.25f; p1[1] = cy;
            p2[0] = bmp.getWidth() * 0.75f; p2[1] = cy;
            initialized = true;
        } else {
            initialized = false;
        }
        invalidate();
    }

    public void setOnChangeListener(OnChangeListener l) {
        this.listener = l;
    }

    private void ensureLayout() {
        if (bitmap == null || getWidth() == 0) return;
        scale = Math.min((float) getWidth() / bitmap.getWidth(),
                (float) getHeight() / bitmap.getHeight());
        offX = (getWidth() - bitmap.getWidth() * scale) / 2f;
        offY = (getHeight() - bitmap.getHeight() * scale) / 2f;
        // A végpontokat a setBitmap() már inicializálta (bitmap-px térben).
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (bitmap == null) return;
        ensureLayout();

        canvas.save();
        canvas.translate(offX, offY);
        canvas.scale(scale, scale);
        canvas.drawBitmap(bitmap, 0, 0, imgPaint);
        canvas.restore();

        // Középkereszt (bitmap közepe → view)
        float ccx = offX + bitmap.getWidth() / 2f * scale;
        float ccy = offY + bitmap.getHeight() / 2f * scale;
        float cs = Math.min(getWidth(), getHeight()) * 0.03f;
        canvas.drawLine(ccx - cs, ccy, ccx + cs, ccy, crossPaint);
        canvas.drawLine(ccx, ccy - cs, ccx, ccy + cs, crossPaint);

        // A vonal — piros, ha a széleknél mér (nem centrált), zöldes-sárga, ha centrált
        linePaint.setColor(isCentered() ? 0xFFFFEE00 : 0xFFFF3333);
        float v1x = offX + p1[0] * scale, v1y = offY + p1[1] * scale;
        float v2x = offX + p2[0] * scale, v2y = offY + p2[1] * scale;
        canvas.drawLine(v1x, v1y, v2x, v2y, linePaint);
        canvas.drawCircle(v1x, v1y, 18f, handlePaint);
        canvas.drawCircle(v2x, v2y, 18f, handlePaint);

        // Lupe húzás közben — a mozgatott végpont környéke felnagyítva a sarokban
        if (dragging >= 0) {
            float bx = (dragging == 0) ? p1[0] : p2[0];
            float by = (dragging == 0) ? p1[1] : p2[1];
            float evx = offX + bx * scale;
            float evy = offY + by * scale;
            drawLoupe(canvas, bx, by, evx, evy);
        }
    }

    /**
     * Nagyító ablak a sarokban: a (bx,by) bitmap-pont környékét LOUPE_MAG-szeresen
     * kinagyítva mutatja, célkereszttel a pontos pozíción. A lupe az ujjal
     * (evx,evy) átellenes sarokba kerül, hogy ne takarja az ujj.
     */
    private void drawLoupe(Canvas canvas, float bx, float by, float evx, float evy) {
        float lx = (evx < getWidth() / 2f) ? getWidth() - LOUPE_SIZE - LOUPE_MARGIN : LOUPE_MARGIN;
        float ly = (evy < getHeight() / 2f) ? getHeight() - LOUPE_SIZE - LOUPE_MARGIN : LOUPE_MARGIN;
        float cx = lx + LOUPE_SIZE / 2f;
        float cy = ly + LOUPE_SIZE / 2f;
        float loupeScale = scale * LOUPE_MAG;

        canvas.save();
        canvas.clipRect(lx, ly, lx + LOUPE_SIZE, ly + LOUPE_SIZE);
        canvas.drawColor(0xFF000000);
        // A (bx,by) bitmap-pont a lupe közepére kerül, loupeScale nagyítással
        canvas.translate(cx, cy);
        canvas.scale(loupeScale, loupeScale);
        canvas.translate(-bx, -by);
        canvas.drawBitmap(bitmap, 0, 0, imgPaint);
        canvas.restore();

        // Célkereszt a lupe közepén (a pontos végpont-pozíció) + keret
        float cs = LOUPE_SIZE * 0.12f;
        canvas.drawLine(cx - cs, cy, cx + cs, cy, loupeCross);
        canvas.drawLine(cx, cy - cs, cx, cy + cs, loupeCross);
        canvas.drawRect(lx, ly, lx + LOUPE_SIZE, ly + LOUPE_SIZE, loupeBorder);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (bitmap == null) return false;
        float x = event.getX(), y = event.getY();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragging = nearestHandle(x, y);
                return dragging >= 0;
            case MotionEvent.ACTION_MOVE:
                if (dragging >= 0) {
                    float bx = clamp((x - offX) / scale, 0, bitmap.getWidth());
                    float by = clamp((y - offY) / scale, 0, bitmap.getHeight());
                    if (dragging == 0) { p1[0] = bx; p1[1] = by; }
                    else { p2[0] = bx; p2[1] = by; }
                    invalidate();
                    if (listener != null) listener.onLineChanged();
                    return true;
                }
                return false;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dragging = -1;
                return true;
        }
        return false;
    }

    /** A két végpont közül a közelebbi indexe, ha a koppintás elég közel van; -1 különben. */
    private int nearestHandle(float x, float y) {
        float v1x = offX + p1[0] * scale, v1y = offY + p1[1] * scale;
        float v2x = offX + p2[0] * scale, v2y = offY + p2[1] * scale;
        float d1 = dist(x, y, v1x, v1y);
        float d2 = dist(x, y, v2x, v2y);
        float hit = 80f;
        if (d1 <= d2 && d1 < hit) return 0;
        if (d2 < hit) return 1;
        return -1;
    }

    /** A húzott vonal hossza a BITMAP px-terében (M09_L4 §7 felbontás-invariancia). */
    public double getLinePixelLength() {
        return dist(p1[0], p1[1], p2[0], p2[1]);
    }

    public int getBitmapWidth() {
        return bitmap != null ? bitmap.getWidth() : 0;
    }

    /** kép_magasság / kép_szélesség — a footprint másik oldalához. */
    public double getImageAspect() {
        return bitmap != null && bitmap.getWidth() > 0
                ? (double) bitmap.getHeight() / bitmap.getWidth() : 1.0;
    }

    /** Igaz, ha a vonal középpontja a képközéphez elég közel (±15% szélesség). */
    public boolean isCentered() {
        if (bitmap == null) return true;
        float midX = (p1[0] + p2[0]) / 2f;
        float midY = (p1[1] + p2[1]) / 2f;
        float dx = Math.abs(midX - bitmap.getWidth() / 2f) / bitmap.getWidth();
        float dy = Math.abs(midY - bitmap.getHeight() / 2f) / bitmap.getHeight();
        return dx < 0.15f && dy < 0.15f;
    }

    private static float dist(float ax, float ay, float bx, float by) {
        float dx = ax - bx, dy = ay - by;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
