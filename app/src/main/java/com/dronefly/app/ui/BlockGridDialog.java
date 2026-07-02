package com.dronefly.app.ui;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.dronefly.app.model.BlockGridConfig;

import java.util.Locale;

/**
 * Felosztás-paraméter dialog (M07).
 *
 * Programatikusan épített AlertDialog, hogy konzisztens legyen a meglévő
 * MissionPlannerActivity dialog-stílussal (saveProjectDialog stb.).
 *
 * Tartalom:
 *  - Cella szélesség / magasság SeekBar (30..500 m, 10-es lépés)
 *  - Rotation SeekBar (0..179°, 1-es lépés)
 *  - Overlap buffer SeekBar (0..min(W,H)/2 m, 5-ös lépés)
 *  - Min coverage SeekBar (0..50 %, 5-ös lépés)
 *  - Origin mode RadioGroup (centroid / first_vertex)
 *  - Info-szöveg: becsült blokkszám + idő (ha számítható)
 *
 * Lásd: docs/M07_BLOKK_FELOSZTAS/M07_L3_ALLAPOTGEP_ES_ENGINE.md §UI flow
 */
public class BlockGridDialog {

    public interface Listener {
        /** A felhasználó az [Alkalmaz] gombot nyomta. A config copy() klón, módosítható. */
        void onApply(BlockGridConfig config);

        /** A SeekBar mozgás közben — élő előnézethez. */
        void onPreview(BlockGridConfig config);

        /** A felhasználó a [Mégse] gombot nyomta vagy bezárta a dialog-ot. */
        void onCancel();
    }

    private final Context  context;
    private final Listener listener;
    private final BlockGridConfig working; // klón, hogy a Cancel ne hagyjon nyomot

    public BlockGridDialog(Context ctx, BlockGridConfig current, Listener listener) {
        this.context  = ctx;
        this.listener = listener;
        this.working  = (current != null) ? current.copy() : new BlockGridConfig();
    }

    public void show() {
        int dp = (int) context.getResources().getDisplayMetrics().density;

        // Root scroll + linear
        ScrollView scroll = new ScrollView(context);
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(16 * dp, 8 * dp, 16 * dp, 8 * dp);
        scroll.addView(root);

        // ── Cella szélesség ──────────────────────────────────────────────
        final TextView lblCellW = new TextView(context);
        root.addView(lblCellW);
        final SeekBar sbCellW = new SeekBar(context);
        sbCellW.setMax((500 - 30) / 10); // 30..500, 10-es lépés
        sbCellW.setProgress((int) Math.round((working.cellWidthM - 30) / 10));
        root.addView(sbCellW);
        updateCellWLabel(lblCellW, working.cellWidthM);

        // ── Cella magasság ──────────────────────────────────────────────
        final TextView lblCellH = new TextView(context);
        root.addView(lblCellH);
        final SeekBar sbCellH = new SeekBar(context);
        sbCellH.setMax((500 - 30) / 10);
        sbCellH.setProgress((int) Math.round((working.cellHeightM - 30) / 10));
        root.addView(sbCellH);
        updateCellHLabel(lblCellH, working.cellHeightM);

        // ── Dőlésszög ────────────────────────────────────────────────────
        final TextView lblRot = new TextView(context);
        root.addView(lblRot);
        final SeekBar sbRot = new SeekBar(context);
        sbRot.setMax(179);
        sbRot.setProgress((int) Math.round(working.rotationDeg));
        root.addView(sbRot);
        updateRotLabel(lblRot, working.rotationDeg);

        // ── Átfedési puffer ──────────────────────────────────────────────
        final TextView lblBuf = new TextView(context);
        root.addView(lblBuf);
        final SeekBar sbBuf = new SeekBar(context);
        // max: min(cellW, cellH)/2, 5-ös lépés
        sbBuf.setMax((int) Math.max(1, (Math.min(working.cellWidthM, working.cellHeightM) / 2) / 5));
        sbBuf.setProgress((int) Math.round(working.overlapBufferM / 5));
        root.addView(sbBuf);
        updateBufLabel(lblBuf, working.overlapBufferM);

        // ── Min. lefedettség ────────────────────────────────────────────
        final TextView lblCov = new TextView(context);
        root.addView(lblCov);
        final SeekBar sbCov = new SeekBar(context);
        sbCov.setMax(10); // 0..50%, 5-ös lépés
        sbCov.setProgress((int) Math.round(working.minCoveragePercent / 5));
        root.addView(sbCov);
        updateCovLabel(lblCov, working.minCoveragePercent);

        // ── Origin mode ──────────────────────────────────────────────────
        TextView lblOrigin = new TextView(context);
        lblOrigin.setText("Rács origója:");
        lblOrigin.setPadding(0, 8 * dp, 0, 4 * dp);
        root.addView(lblOrigin);

        final RadioGroup rg = new RadioGroup(context);
        rg.setOrientation(LinearLayout.VERTICAL);
        final RadioButton rbCentroid = new RadioButton(context);
        rbCentroid.setText("Középpont (ajánlott)");
        rbCentroid.setId(View.generateViewId());
        rg.addView(rbCentroid);
        final RadioButton rbFirstVertex = new RadioButton(context);
        rbFirstVertex.setText("Első csúcs");
        rbFirstVertex.setId(View.generateViewId());
        rg.addView(rbFirstVertex);
        if (BlockGridConfig.ORIGIN_FIRST_VERTEX.equals(working.originMode)) {
            rbFirstVertex.setChecked(true);
        } else {
            rbCentroid.setChecked(true);
        }
        root.addView(rg);

        // ── SeekBar listenerek (élő előnézet) ────────────────────────────
        SeekBar.OnSeekBarChangeListener changeListener = new SeekBar.OnSeekBarChangeListener() {
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { }
            @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if (!fromUser) return;
                working.cellWidthM         = 30 + sbCellW.getProgress() * 10;
                working.cellHeightM        = 30 + sbCellH.getProgress() * 10;
                working.rotationDeg        = sbRot.getProgress();
                // A puffer-max függ a cella mérettől → újraszámolt felső határ
                int bufMax = (int) Math.max(1,
                        (Math.min(working.cellWidthM, working.cellHeightM) / 2) / 5);
                if (sbBuf.getMax() != bufMax) sbBuf.setMax(bufMax);
                working.overlapBufferM     = sbBuf.getProgress() * 5;
                working.minCoveragePercent = sbCov.getProgress() * 5;

                updateCellWLabel(lblCellW, working.cellWidthM);
                updateCellHLabel(lblCellH, working.cellHeightM);
                updateRotLabel(lblRot, working.rotationDeg);
                updateBufLabel(lblBuf, working.overlapBufferM);
                updateCovLabel(lblCov, working.minCoveragePercent);

                if (listener != null) listener.onPreview(working);
            }
        };
        sbCellW.setOnSeekBarChangeListener(changeListener);
        sbCellH.setOnSeekBarChangeListener(changeListener);
        sbRot.setOnSeekBarChangeListener(changeListener);
        sbBuf.setOnSeekBarChangeListener(changeListener);
        sbCov.setOnSeekBarChangeListener(changeListener);

        rg.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (checkedId == rbFirstVertex.getId()) {
                    working.originMode = BlockGridConfig.ORIGIN_FIRST_VERTEX;
                } else {
                    working.originMode = BlockGridConfig.ORIGIN_CENTROID;
                }
                // Origin mode váltáskor töröljük a cached origót, hogy újraszámolódjon
                working.originLat = Double.NaN;
                working.originLon = Double.NaN;
                if (listener != null) listener.onPreview(working);
            }
        });

        // ── Dialog építése ──────────────────────────────────────────────
        new AlertDialog.Builder(context)
                .setTitle("Blokk-felosztás")
                .setView(scroll)
                .setPositiveButton("Alkalmaz", new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int which) {
                        if (listener != null) listener.onApply(working);
                    }
                })
                .setNegativeButton("Mégse", new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int which) {
                        if (listener != null) listener.onCancel();
                    }
                })
                .setOnCancelListener(new android.content.DialogInterface.OnCancelListener() {
                    @Override public void onCancel(android.content.DialogInterface d) {
                        if (listener != null) listener.onCancel();
                    }
                })
                .show();
    }

    // ── Cimke-frissítők ──────────────────────────────────────────────────

    private static void updateCellWLabel(TextView t, double v) {
        t.setText(String.format(Locale.US, "Cella szélesség: %d m", (int) v));
    }
    private static void updateCellHLabel(TextView t, double v) {
        t.setText(String.format(Locale.US, "Cella magasság: %d m", (int) v));
    }
    private static void updateRotLabel(TextView t, double v) {
        t.setText(String.format(Locale.US, "Dőlésszög: %d°", (int) v));
    }
    private static void updateBufLabel(TextView t, double v) {
        t.setText(String.format(Locale.US, "Átfedési puffer: %d m", (int) v));
    }
    private static void updateCovLabel(TextView t, double v) {
        t.setText(String.format(Locale.US, "Min. lefedettség: %d %%", (int) v));
    }
}
