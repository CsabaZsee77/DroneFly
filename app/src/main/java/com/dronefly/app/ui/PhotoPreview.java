package com.dronefly.app.ui;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.Gravity;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.dronefly.app.ai.Detection;
import com.dronefly.app.ai.YoloInferenceEngine;

import java.io.File;
import java.util.List;
import java.util.Locale;

/**
 * Teljes képernyős fotó-előnézet (M09) — koppintásra bezárul.
 *
 * Ha detekció-lista is érkezik, a bounding boxokat rárajzolja a képre
 * (a Dronterapia webes Counting felület annotált képének megfelelője).
 * A normalizált (0-1) bbox koordináták közvetlenül átskálázhatók az
 * előnézeti bitmapre, mert a preprocess az egész képet (letterbox nélkül)
 * skálázta a modell négyzetes bemenetére.
 *
 * A dekódolás inSampleSize-zal történik (20 MP-es P4P fotónál a teljes
 * felbontású dekódolás OOM-kockázat lenne a Crystal Sky-n).
 */
public final class PhotoPreview {

    private static final int PREVIEW_TARGET_PX = 1280;

    private PhotoPreview() {}

    public static void show(Activity activity, File photo) {
        show(activity, photo, null, null);
    }

    public static void show(Activity activity, File photo, List<Detection> detections) {
        show(activity, photo, detections, null);
    }

    /**
     * @param onCalibrate ha nem null, egy „📏 Skála" gomb jelenik meg; koppintásra
     *                    az előnézet bezárul és lefut a callback (a hívó nyitja a
     *                    kalibrációs dialógust — M09_L1 §10.7)
     */
    public static void show(Activity activity, File photo, List<Detection> detections,
                            Runnable onCalibrate) {
        Bitmap bmp = YoloInferenceEngine.decodeDownsampled(photo, PREVIEW_TARGET_PX);
        if (bmp == null) {
            Toast.makeText(activity, "A kép nem olvasható: " + photo.getName(),
                    Toast.LENGTH_SHORT).show();
            return;
        }

        if (detections != null && !detections.isEmpty()) {
            Bitmap annotated = bmp.copy(Bitmap.Config.ARGB_8888, true);
            bmp.recycle();
            bmp = annotated;
            drawDetections(new Canvas(bmp), detections, bmp.getWidth(), bmp.getHeight());
        }

        final Bitmap shown = bmp;
        Dialog dialog = new Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen);

        FrameLayout root = new FrameLayout(activity);
        root.setBackgroundColor(0xFF000000);

        ImageView iv = new ImageView(activity);
        iv.setImageBitmap(shown);
        root.addView(iv, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        if (detections != null) {
            TextView counter = new TextView(activity);
            counter.setText(String.format(Locale.getDefault(), "%d detektálás", detections.size()));
            counter.setTextColor(Color.WHITE);
            counter.setTextSize(16);
            counter.setBackgroundColor(0xAA000000);
            counter.setPadding(24, 12, 24, 12);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP | Gravity.START);
            lp.setMargins(16, 16, 0, 0);
            root.addView(counter, lp);
        }

        if (onCalibrate != null) {
            Button calib = new Button(activity);
            calib.setText("📏 Skála");
            calib.setTextColor(Color.WHITE);
            calib.setBackgroundColor(0xCC4444AA);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP | Gravity.END);
            lp.setMargins(0, 16, 16, 0);
            calib.setOnClickListener(v -> {
                dialog.dismiss();
                onCalibrate.run();
            });
            root.addView(calib, lp);
            // A kalibráló gomb ne zárja be a képet a háttér-kattintással
            iv.setOnClickListener(v -> dialog.dismiss());
        } else {
            root.setOnClickListener(v -> dialog.dismiss());
        }
        dialog.setContentView(root);
        dialog.setOnDismissListener(d -> shown.recycle());
        dialog.show();
    }

    private static void drawDetections(Canvas canvas, List<Detection> detections, int w, int h) {
        Paint paint = new Paint();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(2f, w / 500f));
        paint.setColor(0xFF00FF44);
        for (Detection d : detections) {
            float left = (d.cx - d.w / 2f) * w;
            float top = (d.cy - d.h / 2f) * h;
            float right = (d.cx + d.w / 2f) * w;
            float bottom = (d.cy + d.h / 2f) * h;
            canvas.drawRect(left, top, right, bottom, paint);
        }
    }
}
