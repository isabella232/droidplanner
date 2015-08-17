package org.droidplanner.android.aerokontiki;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.location.LocationManager;
import android.os.Handler;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Toast;

import com.o3dr.android.client.Drone;
import com.o3dr.android.client.apis.drone.ExperimentalApi;
import com.o3dr.services.android.lib.coordinate.LatLong;
import com.o3dr.services.android.lib.coordinate.LatLongAlt;
import com.o3dr.services.android.lib.drone.attribute.AttributeType;
import com.o3dr.services.android.lib.drone.mission.Mission;
import com.o3dr.services.android.lib.drone.mission.item.MissionItem;
import com.o3dr.services.android.lib.drone.mission.item.command.ChangeSpeed;
import com.o3dr.services.android.lib.drone.mission.item.command.ReturnToLaunch;
import com.o3dr.services.android.lib.drone.mission.item.command.SetServo;
import com.o3dr.services.android.lib.drone.mission.item.command.Takeoff;
import com.o3dr.services.android.lib.drone.mission.item.spatial.Waypoint;
import com.o3dr.services.android.lib.drone.property.Gps;
import com.o3dr.services.android.lib.drone.property.Home;
import com.o3dr.services.android.lib.drone.property.State;
import com.o3dr.services.android.lib.drone.property.VehicleMode;
import com.o3dr.services.android.lib.util.MathUtils;

import org.droidplanner.android.DroidPlannerApp;
import org.droidplanner.android.R;
import org.droidplanner.android.proxy.mission.MissionProxy;
import org.droidplanner.android.proxy.mission.item.MissionItemProxy;
import org.droidplanner.android.utils.prefs.DroidPlannerPrefs;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by kellys on 8/9/15.
 */
public class AeroKontiki {
    static final String TAG = AeroKontiki.class.getSimpleName();

    public static final String EVENT_SPEAK = "com.aerokoniki.apps.android.event.SPEAK";
    public static final String EVENT_POINT_DROPPED = "com.aerokontiki.apps.android.event.POINT_DROPPED";
    public static final String EVENT_MISSION_SENT = "com.aerokontiki.apps.android.event.MISSION_SENT";
    public static final String EVENT_FLYING = "com.aerokontiki.apps.android.event.FLIGHT_START";
    public static final String EVENT_ARMED = "com.aerokoniki.apps.android.event.ARMED";
    public static final String EVENT_DISARMED = "com.aerokoniki.apps.android.event.DISARMED";
    public static final String EVENT_CONNECTED = "com.aerokoniki.apps.android.event.CONNECTED";
    public static final String EVENT_DISCONNECTED = "com.aerokoniki.apps.android.event.DISCONNECTED";
    public static final String EVENT_MARKER_MOVING = "com.aerokontiki.apps.android.event.MARKER_MOVING";

    public static final String EXTRA_DATA = "_data_";

    public static final int SERVO_HOOK_CHANNEL = 9;
    public static final int SERVO_HOOK_PWM_OPEN = 1100;
    public static final int SERVO_HOOK_PWM_CLOSE = 1900;

    private static double sWPNavSpeedParam = 0;

    public static IntentFilter populate(IntentFilter filter) {
        for(String action: new String[] {
            EVENT_POINT_DROPPED, EVENT_MISSION_SENT, EVENT_FLYING, EVENT_ARMED,
            EVENT_DISARMED, EVENT_DISCONNECTED, EVENT_CONNECTED, EVENT_MARKER_MOVING
        }) {
            filter.addAction(action);
        }

        return filter;
    }

    public static boolean hasWPSpeedParam() { return sWPNavSpeedParam > 0; }
    public static void setWPSpeedParam(double value) { sWPNavSpeedParam = value; }
    public static double getWPSpeedParam() { return sWPNavSpeedParam; }

    /**
     * Return "my" location. If the drone has a valid location, return that.
     * Otherwise, if the device has a valid location, return that.
     * Otherwise, return <code>null</code>.
     */
    public static LatLong getMyLocation() {
        final Drone drone = DroidPlannerApp.get().getDrone();
        final Gps gps = drone.getAttribute(AttributeType.GPS);
        LatLong ll = null;

        if(gps != null && gps.isValid()) {
            ll = new LatLong(gps.getPosition().getLatitude(), gps.getPosition().getLongitude());
        }
        else {
            LocationManager man = (LocationManager)DroidPlannerApp.get().getSystemService(Context.LOCATION_SERVICE);
            Location last = man.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if(last != null) {
                ll = new LatLong(last.getLatitude(), last.getLongitude());
            }
        }

        return ll;
    }

    public static boolean generateNewMission(Activity activity, MissionProxy missionProxy) {
        final Drone drone = DroidPlannerApp.get().getDrone();
        Gps home = drone.getAttribute(AttributeType.GPS);

        if(home != null && home.isValid()) {
            Mission mission = new Mission();

            final DroidPlannerPrefs prefs = DroidPlannerApp.get().getAppPreferences();
            int dragSpeed = prefs.getDefaultDragSpeed();
            int dragHeight = prefs.getDefaultDragAltitude();

            final LatLong here = home.getPosition();

            Log.v(TAG, "dragSpeed=" + dragSpeed + " dragHeight=" + dragHeight);

            int idx = 0;

            Waypoint dest = new Waypoint();
            dest.setCoordinate(new LatLongAlt(here.getLatitude(), here.getLongitude(), dragHeight));
            dest.setAcceptanceRadius(3f);
            dest.setDelay(2);

            mission.addMissionItem(idx++, dest);

            missionProxy.load(mission);

            Toast.makeText(activity, R.string.toast_drag_point_prompt, Toast.LENGTH_LONG).show();
            say(activity.getString(R.string.tts_set_drop_point_prompt));

            broadcast(new Intent(AeroKontiki.EVENT_POINT_DROPPED));
            return true;
        }
        else {
            Toast.makeText(activity, "Drone location not valid.", Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    public static double metersBetween(LatLong ll1, LatLong ll2) {
        Location l1 = toLocation(ll1);
        Location l2 = toLocation(ll2);
        return l1.distanceTo(l2);
    }

    public static Location toLocation(LatLong ll) {
        Location l = new Location("explicit");
        l.setLatitude(ll.getLatitude());
        l.setLongitude(ll.getLongitude());
        return l;
    }

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
//        EpmGripper open = new EpmGripper();
//        open.setRelease(true);
//        output.add(open);

        SetServo servoOpen = new SetServo();
        servoOpen.setChannel(SERVO_HOOK_CHANNEL);
        servoOpen.setPwm(SERVO_HOOK_PWM_OPEN);
        output.add(servoOpen);

        Waypoint waitForDrop = new Waypoint(dest);
        waitForDrop.setDelay(5);
        output.add(waitForDrop);

//        EpmGripper closeGripper = new EpmGripper();
//        closeGripper.setRelease(false);
//        output.add(closeGripper);

        SetServo servoClose = new SetServo();
        servoClose.setChannel(SERVO_HOOK_CHANNEL);
        servoClose.setPwm(SERVO_HOOK_PWM_CLOSE);
        output.add(servoClose);

        // Set the return speed.
        ChangeSpeed returnSpeed = new ChangeSpeed();
        returnSpeed.setSpeed(retSpeed);
        output.add(returnSpeed);

        // Pause at the destination for a couple of seconds after dropping
        Waypoint preReturn = new Waypoint(dest);
        preReturn.setDelay(2);
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

    public static void broadcast(Intent intent) {
        LocalBroadcastManager.getInstance(DroidPlannerApp.get()).sendBroadcast(intent);
    }

    public static void say(String str) {
        Intent intent = new Intent(EVENT_SPEAK)
            .putExtra(EXTRA_DATA, str);
        broadcast(intent);
    }

    public static void openHook(Drone drone) {
        ExperimentalApi.setServo(drone, SERVO_HOOK_CHANNEL, SERVO_HOOK_PWM_OPEN);
    }

    public static void closeHook(Drone drone) {
        ExperimentalApi.setServo(drone, SERVO_HOOK_CHANNEL, SERVO_HOOK_PWM_CLOSE);
    }

    public static void operateHook(final Drone drone, Handler handler, long keepOpenMs) {
        openHook(drone);

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                closeHook(drone);
            }
        }, keepOpenMs);
    }
}
