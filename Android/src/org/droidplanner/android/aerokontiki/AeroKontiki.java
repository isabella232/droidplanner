package org.droidplanner.android.aerokontiki;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.support.v4.content.LocalBroadcastManager;
import android.widget.Toast;

import com.o3dr.android.client.Drone;
import com.o3dr.services.android.lib.coordinate.LatLong;
import com.o3dr.services.android.lib.drone.attribute.AttributeType;
import com.o3dr.services.android.lib.drone.mission.Mission;
import com.o3dr.services.android.lib.drone.mission.item.MissionItem;
import com.o3dr.services.android.lib.drone.mission.item.command.CameraTrigger;
import com.o3dr.services.android.lib.drone.mission.item.command.ChangeSpeed;
import com.o3dr.services.android.lib.drone.mission.item.command.ReturnToLaunch;
import com.o3dr.services.android.lib.drone.mission.item.command.Takeoff;
import com.o3dr.services.android.lib.drone.mission.item.spatial.Waypoint;
import com.o3dr.services.android.lib.drone.property.Gps;
import com.o3dr.services.android.lib.drone.property.Home;
import com.o3dr.services.android.lib.drone.property.State;
import com.o3dr.services.android.lib.drone.property.VehicleMode;
import com.o3dr.services.android.lib.util.MathUtils;

import org.droidplanner.android.DroidPlannerApp;
import org.droidplanner.android.proxy.mission.MissionProxy;
import org.droidplanner.android.proxy.mission.item.MissionItemProxy;
import org.droidplanner.android.utils.prefs.DroidPlannerPrefs;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by kellys on 8/9/15.
 */
public class AeroKontiki {
    public static final String EVENT_SPEAK = "com.aerokoniki.apps.android.event.SPEAK";
    public static final String EVENT_POINT_DROPPED = "com.aerokontiki.apps.android.event.POINT_DROPPED";
    public static final String EVENT_MISSION_SENT = "com.aerokontiki.apps.android.event.MISSION_SENT";
    public static final String EVENT_FLYING = "com.aerokontiki.apps.android.event.FLIGHT_START";
    public static final String EVENT_ARMED = "com.aerokoniki.apps.android.event.ARMED";
    public static final String EVENT_DISARMED = "com.aerokoniki.apps.android.event.DISARMED";
    public static final String EVENT_CONNECTED = "com.aerokoniki.apps.android.event.CONNECTED";
    public static final String EVENT_DISCONNECTED = "com.aerokoniki.apps.android.event.DISCONNECTED";

    public static final String EXTRA_DATA = "_data_";

    private static double sWPNavSpeedParam = 0;

    public static IntentFilter populate(IntentFilter filter) {
        for(String action: new String[] {
            EVENT_POINT_DROPPED, EVENT_MISSION_SENT, EVENT_FLYING, EVENT_ARMED, EVENT_DISARMED, EVENT_DISCONNECTED, EVENT_CONNECTED
        }) {
            filter.addAction(action);
        }

        return filter;
    }

    public static boolean hasWPSpeedParam() { return sWPNavSpeedParam > 0; }
    public static void setWPSpeedParam(double value) { sWPNavSpeedParam = value; }
    public static double getWPSpeedParam() { return sWPNavSpeedParam; }

    /** Update the mission so it contains more useful waypoints. */
    public static void massageMission(MissionProxy missionProxy) {
        final Drone drone = DroidPlannerApp.get().getDrone();
//        final Gps home = drone.getAttribute(AttributeType.GPS);

        ArrayList<MissionItem> output = new ArrayList<MissionItem>();

        final DroidPlannerPrefs prefs = DroidPlannerApp.get().getAppPreferences();

        final int takeoffAlt = prefs.getDefaultTakeoffAltitude();
        final int dragAlt = prefs.getDefaultDragAltitude();
        final int dragSpeed = prefs.getDefaultDragSpeed();
        final int retSpeed = prefs.getDefaultReturnSpeed();

        Waypoint dest = null;

        // Find the waypoint in the initial mission.
        List<MissionItemProxy> proxies = missionProxy.getItems();
        for(MissionItemProxy proxy: proxies) {
            MissionItem item = proxy.getMissionItem();
            if(item != null) {
                if(item instanceof Waypoint) {
                    dest = (Waypoint)item;
                }
            }
        }

        // Takeoff to the drag altitude
        Takeoff to = new Takeoff();
        to.setTakeoffAltitude(takeoffAlt);
        output.add(to);

        // Disabling this for now, until it can be set to be several meters closer to dest.
//        if(home != null && home.isValid()) {
//            Waypoint pause = new Waypoint();
//            final LatLong here = home.getPosition();
//            pause.setCoordinate(new LatLongAlt(here.getLatitude(), here.getLongitude(), (double)dragAlt));
//            pause.setDelay(5);
//            output.add(pause);
//        }

        // Set the drag speed
        ChangeSpeed speed = new ChangeSpeed();
        speed.setSpeed(dragSpeed);
        output.add(speed);

        // Fly to the destination
        dest.setDelay(2);
        output.add(dest);

        // Drop the line
        CameraTrigger trigger = new CameraTrigger();
        trigger.setTriggerDistance(10); // this doesn't seem right.
        output.add(trigger);

        // Set the return speed.
        ChangeSpeed returnSpeed = new ChangeSpeed();
        returnSpeed.setSpeed(retSpeed);
        output.add(returnSpeed);

        // Pause at the destination for a couple of seconds after dropping
        Waypoint preReturn = new Waypoint(dest);
        preReturn.setDelay(5);
        output.add(preReturn);

        // Return to launch.
        ReturnToLaunch rtl = new ReturnToLaunch();
        output.add(rtl);

        Mission mission = new Mission();
        for(MissionItem item: output) {
            mission.addMissionItem(item);
        }

        missionProxy.load(mission);
    }

    public static void onMissionCanceled(Context context, Handler handler) {
        final Drone drone = DroidPlannerApp.get().getDrone();
        final State droneState = drone.getAttribute(AttributeType.STATE);
        final Gps location = drone.getAttribute(AttributeType.GPS);
        final Home home = drone.getAttribute(AttributeType.HOME);

        if(droneState.isFlying()) {
            drone.changeVehicleMode(VehicleMode.COPTER_BRAKE);
            Toast.makeText(context, "Release the line within 5 seconds", Toast.LENGTH_SHORT).show();

            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    LocalBroadcastManager.getInstance(
                        DroidPlannerApp.get()).sendBroadcast(
                        new Intent(AeroKontiki.EVENT_SPEAK)
                            .putExtra(AeroKontiki.EXTRA_DATA, "Ensure that the line is released. You have 5 seconds."));
                }
            }, 2000);

            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    VehicleMode mode = VehicleMode.COPTER_RTL;
                    if(location != null && home != null && location.isValid() && home.isValid()) {
                        double distance = MathUtils.getDistance2D(location.getPosition(), new LatLong(home.getCoordinate().getLatitude(), home.getCoordinate().getLongitude()));
                        if(distance <= 5) {
                            mode = VehicleMode.COPTER_LAND;
                        }
                    }

                    drone.changeVehicleMode(mode);
                }
            }, 7000);
        }
        else if(droneState.isArmed()) {
            drone.arm(false);
        }
    }
}
