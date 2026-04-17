package com.dronefly.app.dji;

import com.dronefly.app.model.MissionConfig;
import com.dronefly.app.model.WaypointData;

import java.util.ArrayList;
import java.util.List;

import dji.common.error.DJIError;
import dji.common.mission.waypoint.Waypoint;
import dji.common.mission.waypoint.WaypointAction;
import dji.common.mission.waypoint.WaypointActionType;
import dji.common.mission.waypoint.WaypointMission;
import dji.common.mission.waypoint.WaypointMissionDownloadEvent;
import dji.common.mission.waypoint.WaypointMissionExecutionEvent;
import dji.common.mission.waypoint.WaypointMissionFinishedAction;
import dji.common.mission.waypoint.WaypointMissionFlightPathMode;
import dji.common.mission.waypoint.WaypointMissionGotoWaypointMode;
import dji.common.mission.waypoint.WaypointMissionHeadingMode;
import dji.common.mission.waypoint.WaypointMissionUploadEvent;
import dji.sdk.mission.MissionControl;
import dji.sdk.mission.waypoint.WaypointMissionOperator;
import dji.sdk.mission.waypoint.WaypointMissionOperatorListener;

public class MissionUploader {

    // ── Általános feltöltési callback ──────────────────────────────────────────

    public interface UploadCallback {
        void onSuccess();
        void onError(String message);
    }

    // ── Misszió végrehajtás figyelő ────────────────────────────────────────────

    /**
     * Repülés közbeni haladás és befejezés értesítések.
     * A waypointIndex 0-alapú, a feltöltött szegmensen belüli index.
     * Az actualIndex = progressOffset + waypointIndex (teljes szegmensben).
     */
    public interface MissionProgressListener {
        void onWaypointReached(int waypointIndex, int totalWaypoints);
        void onMissionFinished(boolean completedSuccessfully, int lastReachedIndex);
    }

    // ── Belső állapot ──────────────────────────────────────────────────────────

    private WaypointMissionOperatorListener executionListener;
    private int trackedWaypointIndex = -1;

    // ── SDK operator elérés ────────────────────────────────────────────────────

    private WaypointMissionOperator getOperator() {
        try {
            MissionControl mc = MissionControl.getInstance();
            if (mc == null) return null;
            return mc.getWaypointMissionOperator();
        } catch (Throwable t) {
            return null;
        }
    }

    // ── Misszió feltöltés ──────────────────────────────────────────────────────

    public void uploadMission(List<WaypointData> waypoints, MissionConfig config,
                              UploadCallback callback) {
        WaypointMissionOperator operator = getOperator();
        if (operator == null) {
            if (callback != null) callback.onError("DJI SDK nincs inicializálva");
            return;
        }
        if (waypoints == null || waypoints.isEmpty()) {
            if (callback != null) callback.onError("Nincsenek waypontok");
            return;
        }

        // Waypoint lista összeállítása — csak sávvégpontok, fotót kamera intervallum triggereli
        List<Waypoint> wpList = new ArrayList<>();
        for (WaypointData wp : waypoints) {
            Waypoint waypoint = new Waypoint(
                    (float) wp.latitude,
                    (float) wp.longitude,
                    wp.altitudeM);
            // CURVED módban a waypoint akciók figyelmen kívül maradnak —
            // ne adjunk hozzá TAKE_PHOTO vagy GIMBAL_PITCH akciót
            waypoint.cornerRadiusInMeters = 0.2f; // szoros kanyar sávváltásnál
            wpList.add(waypoint);
        }

        WaypointMission.Builder builder = new WaypointMission.Builder();
        builder.waypointList(wpList)
               .waypointCount(wpList.size())
               .maxFlightSpeed(Math.min(15f, Math.max(1f, config.speedMs)))
               .autoFlightSpeed(Math.min(15f, Math.max(1f, config.speedMs)))
               .finishedAction(config.returnHome
                       ? WaypointMissionFinishedAction.GO_HOME
                       : WaypointMissionFinishedAction.NO_ACTION)
               .headingMode(WaypointMissionHeadingMode.AUTO)
               .flightPathMode(WaypointMissionFlightPathMode.CURVED)
               .gotoFirstWaypointMode(WaypointMissionGotoWaypointMode.SAFELY);

        DJIError loadError = operator.loadMission(builder.build());
        if (loadError != null) {
            if (callback != null)
                callback.onError("Misszió betöltési hiba: " + loadError.getDescription());
            return;
        }

        operator.uploadMission(djiError -> {
            if (djiError == null) {
                if (callback != null) callback.onSuccess();
            } else {
                if (callback != null)
                    callback.onError("Feltöltési hiba: " + djiError.getDescription());
            }
        });
    }

    // ── Listener indítás / leállítás ───────────────────────────────────────────

    /**
     * Elindítja a waypoint végrehajtás figyelőt.
     * @param totalWaypoints A feltöltött waypontok száma (a haladás kiszámításához)
     * @param listener       Visszahívó felület
     */
    public void startListening(int totalWaypoints, MissionProgressListener listener) {
        WaypointMissionOperator operator = getOperator();
        if (operator == null) return;

        stopListening(); // előző listener eltávolítása ha volt
        trackedWaypointIndex = -1;

        executionListener = new WaypointMissionOperatorListener() {

            @Override
            public void onDownloadUpdate(WaypointMissionDownloadEvent event) {}

            @Override
            public void onUploadUpdate(WaypointMissionUploadEvent event) {}

            @Override
            public void onExecutionUpdate(WaypointMissionExecutionEvent event) {
                if (event == null || event.getProgress() == null) return;
                dji.common.mission.waypoint.WaypointExecutionProgress p = event.getProgress();
                if (p.isWaypointReached) {
                    trackedWaypointIndex = p.targetWaypointIndex;
                    if (listener != null) {
                        listener.onWaypointReached(trackedWaypointIndex, totalWaypoints);
                    }
                }
            }

            @Override
            public void onExecutionStart() {}

            @Override
            public void onExecutionFinish(DJIError error) {
                if (listener != null) {
                    listener.onMissionFinished(error == null, trackedWaypointIndex);
                }
            }
        };

        operator.addListener(executionListener);
    }

    /** Leállítja a végrehajtás figyelőt. */
    public void stopListening() {
        try {
            WaypointMissionOperator operator = getOperator();
            if (operator != null && executionListener != null) {
                operator.removeListener(executionListener);
            }
        } catch (Throwable ignored) {}
        executionListener = null;
    }

    // ── Misszió vezérlők ───────────────────────────────────────────────────────

    public void startMission(UploadCallback callback) {
        WaypointMissionOperator operator = getOperator();
        if (operator == null) {
            if (callback != null) callback.onError("DJI SDK nincs inicializálva");
            return;
        }
        operator.startMission(djiError -> {
            if (djiError == null) {
                if (callback != null) callback.onSuccess();
            } else {
                if (callback != null)
                    callback.onError("Start hiba: " + djiError.getDescription());
            }
        });
    }

    public void pauseMission(UploadCallback callback) {
        WaypointMissionOperator operator = getOperator();
        if (operator == null) {
            if (callback != null) callback.onError("DJI SDK nincs inicializálva");
            return;
        }
        operator.pauseMission(djiError -> {
            if (djiError == null) {
                if (callback != null) callback.onSuccess();
            } else {
                if (callback != null)
                    callback.onError("Szünet hiba: " + djiError.getDescription());
            }
        });
    }

    public void resumeMission(UploadCallback callback) {
        WaypointMissionOperator operator = getOperator();
        if (operator == null) {
            if (callback != null) callback.onError("DJI SDK nincs inicializálva");
            return;
        }
        operator.resumeMission(djiError -> {
            if (djiError == null) {
                if (callback != null) callback.onSuccess();
            } else {
                if (callback != null)
                    callback.onError("Folytatás hiba: " + djiError.getDescription());
            }
        });
    }

    /**
     * Misszió leállítása – a drón lebeg, kézi vezérlés átvehető.
     * RTH-hoz a kontrolleren a Return to Home gombot kell nyomni.
     */
    public void stopMission(UploadCallback callback) {
        WaypointMissionOperator operator = getOperator();
        if (operator == null) {
            if (callback != null) callback.onError("DJI SDK nincs inicializálva");
            return;
        }
        operator.stopMission(djiError -> {
            if (djiError == null) {
                if (callback != null) callback.onSuccess();
            } else {
                if (callback != null)
                    callback.onError("Stop hiba: " + djiError.getDescription());
            }
        });
    }
}
