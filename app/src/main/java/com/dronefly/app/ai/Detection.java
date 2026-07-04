package com.dronefly.app.ai;

/** Egyetlen YOLO detekció — bbox normalizált (0-1) koordinátákban. */
public class Detection {
    public int classIndex;
    public float confidence;
    public float cx, cy, w, h;

    public Detection(int classIndex, float confidence, float cx, float cy, float w, float h) {
        this.classIndex = classIndex;
        this.confidence = confidence;
        this.cx = cx;
        this.cy = cy;
        this.w = w;
        this.h = h;
    }

    /** IoU (Intersection over Union) két detekció bbox-a között — NMS-hez. */
    public static float iou(Detection a, Detection b) {
        float ax1 = a.cx - a.w / 2f, ay1 = a.cy - a.h / 2f;
        float ax2 = a.cx + a.w / 2f, ay2 = a.cy + a.h / 2f;
        float bx1 = b.cx - b.w / 2f, by1 = b.cy - b.h / 2f;
        float bx2 = b.cx + b.w / 2f, by2 = b.cy + b.h / 2f;

        float interX1 = Math.max(ax1, bx1);
        float interY1 = Math.max(ay1, by1);
        float interX2 = Math.min(ax2, bx2);
        float interY2 = Math.min(ay2, by2);

        float interW = Math.max(0f, interX2 - interX1);
        float interH = Math.max(0f, interY2 - interY1);
        float interArea = interW * interH;

        float areaA = a.w * a.h;
        float areaB = b.w * b.h;
        float unionArea = areaA + areaB - interArea;

        return unionArea <= 0f ? 0f : interArea / unionArea;
    }
}
