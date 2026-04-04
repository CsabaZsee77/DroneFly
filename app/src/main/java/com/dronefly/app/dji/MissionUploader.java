package com.dronefly.app.dji;

import com.dronefly.app.model.MissionConfig;
import com.dronefly.app.model.WaypointData;

import java.util.List;

import dji.common.error.DJIError;
import dji.common.mission.waypoint.Waypoint;
import dji.common.mission.waypoint.WaypointAction;
import dji.common.mission.waypoint.WaypointActionType;
import dji.common.mission.waypoint.WaypointMission;
import dji.common.mission.waypoint.WaypointMissionFinishedAction;
import dji.common.mission.waypoint.WaypointMissionFlightPathMode;
import dji.common.mission.waypoint.WaypointMissionGotoWaypointMode;
import dji.common.mission.waypoint.WaypointMissionHeadingMode;
import dji.sdk.mission.MissionControl;
import dji.sdk.mission.waypoint.WaypointMissionOperator;

public class MissionUploader {

    public interface UploadCallback {
        void onSuccess();
        void onError(String message);
    }

    private WaypointMissionOperator getOperator() {
        try {
            MissionControl mc = MissionControl.getInstance();
            if (mc == null) return null;
            return mc.getWaypointMissionOperator();
        } catch (Throwable t) {
            return null;
        }
    }

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

        WaypointMission.Builder builder = new WaypointMission.Builder();
        builder.waypointCount(waypoints.size())
               .maxFlightSpeed(Math.min(15f, Math.max(1f, config.speedMs)))
               .autoFlightSpeed(Math.min(15f, Math.max(1f, config.speedMs)))
               .finishedAction(config.returnHome
                       ? WaypointMissionFinishedAction.GO_HOME
                       : WaypointMissionFinishedAction.NO_ACTION)
               .headingMode(WaypointMissionHeadingMode.AUTO)
               .flightPathMode(WaypointMissionFlightPathMode.NORMAL)
               .gotoFirstWaypointMode(WaypointMissionGotoWaypointMode.SAFELY);

        for (WaypointData wp : waypoints) {
            Waypoint waypoint = new Waypoint(
                    (float) wp.latitude,
                    (float) wp.longitude,
                    wp.altitudeM);
            if (wp.shootPhoto) {
                waypoint.addAction(new WaypointAction(
                        WaypointActionType.START_TAKE_PHOTO, 0));
            }
            waypoint.addAction(new WaypointAction(
                    WaypointActionType.GIMBAL_PITCH, (int) wp.gimbalPitch));
            builder.addWaypoint(waypoint);
        }

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

    /**
     * Misszió szüneteltetése – a drón lebeg az aktuális pozícióban.
     * Folytatás: resumeMission()-nel.
     */
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

    /**
     * Misszió folytatása szünet után.
     */
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
     * RTH-hoz használd ezután a kontrolleren a Return to Home gombot.
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
