package com.dronefly.app.mission;

import java.util.ArrayList;
import java.util.List;

/**
 * Sutherland-Hodgman poligon-kivágó algoritmus.
 *
 * A subject poligon (vágandó) lehet konkáv vagy konvex, de a clip poligonnak
 * KONVEXNEK kell lennie (M07-ben: rotált téglalap cella + puffer → konvex).
 *
 * A clip poligon csúcsainak CCW (counter-clockwise) sorrendben kell lenniük
 * a "bent = bal-félsík" konvenció miatt. A BlockGridGenerator a 4 sarokpontot
 * konzisztens CCW sorrendben adja át (lokális XY rendszerben: bal-alsó →
 * jobb-alsó → jobb-felső → bal-felső).
 *
 * Pont reprezentáció: double[]{x, y} 2D lokális koordinátákban (m).
 *
 * Lásd: docs/M07_BLOKK_FELOSZTAS/M07_L3_ALLAPOTGEP_ES_ENGINE.md §PolygonClipper
 */
public class PolygonClipper {

    /** Két szakasz párhuzamos vagy nincs metszéspont — kis tolerancia. */
    private static final double EPS = 1e-9;

    /**
     * Subject ∩ Clip metszet poligon kiszámítása.
     *
     * @param subject vágandó poligon csúcsai (konkáv megengedett),
     *                bármilyen körüljárási irányban
     * @param clip    vágó konvex poligon CCW sorrendben
     * @return        a metszet poligon csúcsai sorrendben, vagy üres lista ha
     *                nincs metszet (a subject teljesen a clip-en kívül esik)
     */
    public static List<double[]> clip(List<double[]> subject, List<double[]> clip) {
        if (subject == null || subject.size() < 3) return new ArrayList<>();
        if (clip    == null || clip.size()    < 3) return new ArrayList<>();

        // Output induló érték: a subject teljes csúcslistája
        List<double[]> output = new ArrayList<>(subject.size());
        for (double[] p : subject) output.add(new double[]{p[0], p[1]});

        int clipN = clip.size();
        for (int i = 0; i < clipN; i++) {
            if (output.isEmpty()) return output; // teljesen levágódott

            List<double[]> input = output;
            output = new ArrayList<>(input.size());

            double[] edgeStart = clip.get(i);
            double[] edgeEnd   = clip.get((i + 1) % clipN);

            int inputN = input.size();
            for (int j = 0; j < inputN; j++) {
                double[] curr = input.get(j);
                double[] prev = input.get((j + inputN - 1) % inputN);

                boolean currIn = isInside(curr, edgeStart, edgeEnd);
                boolean prevIn = isInside(prev, edgeStart, edgeEnd);

                if (currIn) {
                    if (!prevIn) {
                        double[] xPt = intersect(prev, curr, edgeStart, edgeEnd);
                        if (xPt != null) output.add(xPt);
                    }
                    output.add(curr);
                } else if (prevIn) {
                    double[] xPt = intersect(prev, curr, edgeStart, edgeEnd);
                    if (xPt != null) output.add(xPt);
                }
            }
        }
        return output;
    }

    /**
     * Egy pont a clip él bal-félsíkjában van? CCW orientáció esetén ez = "bent".
     * Cross product: (edgeEnd - edgeStart) × (p - edgeStart) ≥ 0
     */
    private static boolean isInside(double[] p, double[] edgeStart, double[] edgeEnd) {
        double cross = (edgeEnd[0] - edgeStart[0]) * (p[1]         - edgeStart[1])
                     - (edgeEnd[1] - edgeStart[1]) * (p[0]         - edgeStart[0]);
        return cross >= -EPS;
    }

    /**
     * Két szakasz (S→E és edgeStart→edgeEnd) metszéspontja paraméteres formában.
     * Visszatér null-lal, ha párhuzamosak (a SH algoritmusban ez az ág nem futhat
     * normál esetben, mert prevIn != currIn fennáll, de védő ellenőrzés).
     */
    private static double[] intersect(double[] s, double[] e,
                                       double[] edgeStart, double[] edgeEnd) {
        double x1 = s[0],         y1 = s[1];
        double x2 = e[0],         y2 = e[1];
        double x3 = edgeStart[0], y3 = edgeStart[1];
        double x4 = edgeEnd[0],   y4 = edgeEnd[1];

        double denom = (x1 - x2) * (y3 - y4) - (y1 - y2) * (x3 - x4);
        if (Math.abs(denom) < EPS) return null;

        double t = ((x1 - x3) * (y3 - y4) - (y1 - y3) * (x3 - x4)) / denom;
        double ix = x1 + t * (x2 - x1);
        double iy = y1 + t * (y2 - y1);
        return new double[]{ix, iy};
    }
}
