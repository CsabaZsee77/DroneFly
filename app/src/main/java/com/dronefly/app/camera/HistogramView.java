package com.dronefly.app.camera;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

/**
 * Élő hisztogram megjelenítő — 256 bint 64 oszlopba aggregál.
 * Narancssárga oszlopok, piros = clipping (225–255), sötétkék = crushing (0–31).
 */
public class HistogramView extends View {

    private final int[] bins = new int[64];
    private int maxBin = 1;

    private final Paint paintNormal   = new Paint();
    private final Paint paintClipping = new Paint();
    private final Paint paintCrushing = new Paint();
    private final Paint paintBg       = new Paint();
    private final Paint paintGrid     = new Paint();

    public HistogramView(Context context) {
        super(context);
        init();
    }

    public HistogramView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        paintNormal.setColor(0xFFFF8800);
        paintClipping.setColor(0xFFFF3333);
        paintCrushing.setColor(0xFF2244AA);
        paintBg.setColor(0xFF0D0D1A);
        paintGrid.setColor(0x33FFFFFF);
        paintGrid.setStrokeWidth(1f);
    }

    /** Frissíti a hisztogramot. Hívható bármely szálból — post()-on keresztül rajzol. */
    public void update(final int[] raw256) {
        post(() -> {
            aggregate(raw256);
            invalidate();
        });
    }

    /** Üres állapot (drón nincs csatlakoztatva). */
    public void clear() {
        post(() -> {
            for (int i = 0; i < 64; i++) bins[i] = 0;
            maxBin = 1;
            invalidate();
        });
    }

    private void aggregate(int[] raw256) {
        if (raw256 == null || raw256.length < 256) return;
        maxBin = 1;
        for (int i = 0; i < 64; i++) {
            int sum = 0;
            for (int j = 0; j < 4; j++) sum += raw256[i * 4 + j];
            bins[i] = sum;
            if (sum > maxBin) maxBin = sum;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();

        canvas.drawRect(0, 0, w, h, paintBg);

        // Rácsvonalak: negyed, fél, háromnegyed
        for (int q = 1; q <= 3; q++) {
            float x = w * q / 4f;
            canvas.drawLine(x, 0, x, h, paintGrid);
        }
        float midY = h / 2f;
        canvas.drawLine(0, midY, w, midY, paintGrid);

        float barW = (float) w / 64;

        for (int i = 0; i < 64; i++) {
            float barH = (float) bins[i] / maxBin * h;
            float left  = i * barW;
            float right = left + barW - 1;
            float top   = h - barH;

            Paint p;
            if (i >= 56)      p = paintClipping;  // túlexponált csúcs: piros
            else if (i <= 7)  p = paintCrushing;  // alulexponált csúcs: kék
            else               p = paintNormal;

            if (barH > 0) canvas.drawRect(left, top, right, h, p);
        }
    }
}
