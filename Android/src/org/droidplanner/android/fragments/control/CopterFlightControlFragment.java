package org.droidplanner.android.fragments.control;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import com.google.android.gms.analytics.HitBuilders;
import com.o3dr.android.client.Drone;
import com.o3dr.android.client.apis.drone.ExperimentalApi;
import com.o3dr.services.android.lib.coordinate.LatLong;
import com.o3dr.services.android.lib.coordinate.LatLongAlt;
import com.o3dr.services.android.lib.drone.attribute.AttributeEvent;
import com.o3dr.services.android.lib.drone.attribute.AttributeEventExtra;
import com.o3dr.services.android.lib.drone.attribute.AttributeType;
import com.o3dr.services.android.lib.drone.mission.Mission;
import com.o3dr.services.android.lib.drone.mission.item.spatial.Waypoint;
import com.o3dr.services.android.lib.drone.property.Gps;
import com.o3dr.services.android.lib.drone.property.GuidedState;
import com.o3dr.services.android.lib.drone.property.State;
import com.o3dr.services.android.lib.drone.property.VehicleMode;
import com.o3dr.services.android.lib.gcs.follow.FollowState;

import org.droidplanner.android.DroidPlannerApp;
import org.droidplanner.android.R;
import org.droidplanner.android.activities.FlightActivity;
import org.droidplanner.android.activities.helpers.SuperUI;
import org.droidplanner.android.aerokontiki.AeroKontiki;
import org.droidplanner.android.dialogs.SlideToUnlockDialog;
import org.droidplanner.android.dialogs.SupportYesNoDialog;
import org.droidplanner.android.dialogs.SupportYesNoWithPrefsDialog;
import org.droidplanner.android.proxy.mission.MissionProxy;
import org.droidplanner.android.utils.analytics.GAUtils;
import org.droidplanner.android.utils.prefs.DroidPlannerPrefs;

/**
 * Provide functionality for flight action button specific to copters.
 */
public class CopterFlightControlFragment extends BaseFlightControlFragment {

    private static final String TAG = CopterFlightControlFragment.class.getSimpleName();

    private static final String ACTION_FLIGHT_ACTION_BUTTON = "Copter flight action button";

    private static final IntentFilter eventFilter = new IntentFilter();

    static {
        eventFilter.addAction(AttributeEvent.STATE_ARMING);
        eventFilter.addAction(AttributeEvent.STATE_CONNECTED);
        eventFilter.addAction(AttributeEvent.STATE_DISCONNECTED);
        eventFilter.addAction(AttributeEvent.STATE_UPDATED);
        eventFilter.addAction(AttributeEvent.STATE_VEHICLE_MODE);
        eventFilter.addAction(AttributeEvent.FOLLOW_START);
        eventFilter.addAction(AttributeEvent.FOLLOW_STOP);
        eventFilter.addAction(AttributeEvent.FOLLOW_UPDATE);
        eventFilter.addAction(AttributeEvent.MISSION_DRONIE_CREATED);
    }

    private final BroadcastReceiver eventReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            switch (action) {
                case AttributeEvent.STATE_ARMING:
                case AttributeEvent.STATE_CONNECTED:
                case AttributeEvent.STATE_DISCONNECTED:
                case AttributeEvent.STATE_UPDATED:
                    setupButtonsByFlightState();
                    break;

                case AttributeEvent.STATE_VEHICLE_MODE:
                    updateFlightModeButtons();
                    break;

                case AttributeEvent.FOLLOW_START:
                case AttributeEvent.FOLLOW_STOP:
                    final FollowState followState = getDrone().getAttribute(AttributeType.FOLLOW_STATE);
                    if (followState != null) {
                        String eventLabel = null;
                        switch (followState.getState()) {
                            case FollowState.STATE_START:
                                eventLabel = "FollowMe enabled";
                                break;

                            case FollowState.STATE_RUNNING:
                                eventLabel = "FollowMe running";
                                break;

                            case FollowState.STATE_END:
                                eventLabel = "FollowMe disabled";
                                break;

                            case FollowState.STATE_INVALID:
                                eventLabel = "FollowMe error: invalid state";
                                break;

                            case FollowState.STATE_DRONE_DISCONNECTED:
                                eventLabel = "FollowMe error: drone not connected";
                                break;

                            case FollowState.STATE_DRONE_NOT_ARMED:
                                eventLabel = "FollowMe error: drone not armed";
                                break;
                        }

                        if (eventLabel != null) {
                            HitBuilders.EventBuilder eventBuilder = new HitBuilders.EventBuilder()
                                    .setCategory(GAUtils.Category.FLIGHT)
                                    .setAction(ACTION_FLIGHT_ACTION_BUTTON)
                                    .setLabel(eventLabel);
                            GAUtils.sendEvent(eventBuilder);

                            Toast.makeText(getActivity(), eventLabel, Toast.LENGTH_SHORT).show();
                        }
                    }

                    /* FALL - THROUGH */
                case AttributeEvent.FOLLOW_UPDATE:
                    updateFlightModeButtons();
                    updateFollowButton();
                    break;

                case AttributeEvent.MISSION_DRONIE_CREATED:
                    //Get the bearing of the dronie mission.
                    float bearing = intent.getFloatExtra(AttributeEventExtra.EXTRA_MISSION_DRONIE_BEARING, -1);
                    if (bearing >= 0) {
                        final FlightActivity flightActivity = (FlightActivity) getActivity();
                        if (flightActivity != null) {
                            flightActivity.updateMapBearing(bearing);
                        }
                    }
                    break;
            }
        }
    };

    private final Handler mHandler = new Handler();

    private MissionProxy missionProxy;

    private View mDisconnectedButtons;
    private View mDisarmedButtons;
    private View mArmedButtons;
    private View mInFlightButtons;

    private Button followBtn;
    private Button homeBtn;
    private Button landBtn;
    private Button pauseBtn;
    private Button autoBtn;
    private Button uploadButton;

    private int orangeColor;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_copter_mission_control, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        orangeColor = getResources().getColor(R.color.orange);

        mDisconnectedButtons = view.findViewById(R.id.mc_disconnected_buttons);
        mDisarmedButtons = view.findViewById(R.id.mc_disarmed_buttons);
        mArmedButtons = view.findViewById(R.id.mc_armed_buttons);
        mInFlightButtons = view.findViewById(R.id.mc_in_flight_buttons);

        final Button connectBtn = (Button) view.findViewById(R.id.mc_connectBtn);
        connectBtn.setOnClickListener(this);

        homeBtn = (Button) view.findViewById(R.id.mc_homeBtn);
        homeBtn.setOnClickListener(this);

        final Button armBtn = (Button) view.findViewById(R.id.mc_armBtn);
        armBtn.setOnClickListener(this);

        final Button disarmBtn = (Button) view.findViewById(R.id.mc_disarmBtn);
        disarmBtn.setOnClickListener(this);

        landBtn = (Button) view.findViewById(R.id.mc_land);
        landBtn.setOnClickListener(this);

        final Button takeoffBtn = (Button) view.findViewById(R.id.mc_takeoff);
        takeoffBtn.setOnClickListener(this);

        pauseBtn = (Button) view.findViewById(R.id.mc_pause);
        pauseBtn.setOnClickListener(this);

        autoBtn = (Button) view.findViewById(R.id.mc_autoBtn);
        autoBtn.setOnClickListener(this);

        final Button takeoffInAuto = (Button) view.findViewById(R.id.mc_TakeoffInAutoBtn);
        takeoffInAuto.setOnClickListener(this);

        followBtn = (Button) view.findViewById(R.id.mc_follow);
        followBtn.setOnClickListener(this);
        followBtn.setVisibility(View.GONE); // hide for this app

        uploadButton = (Button)view.findViewById(R.id.mc_sendMissionBtn);
        for(int id: new int[] {
            R.id.mc_dropPointBtn, R.id.mc_sendMissionBtn, R.id.mc_hookBtn
        }) {
            view.findViewById(id).setOnClickListener(this);
        }
    }

    @Override
    public void onApiConnected() {
        super.onApiConnected();
        missionProxy = getMissionProxy();

        setupButtonsByFlightState();
        updateFlightModeButtons();
        updateFollowButton();

        getBroadcastManager().registerReceiver(eventReceiver, eventFilter);
    }

    @Override
    public void onApiDisconnected() {
        super.onApiDisconnected();
        getBroadcastManager().unregisterReceiver(eventReceiver);
    }

    @Override
    public void onClick(View v) {
        HitBuilders.EventBuilder eventBuilder = new HitBuilders.EventBuilder()
                .setCategory(GAUtils.Category.FLIGHT);

        final Drone drone = getDrone();
        switch (v.getId()) {
            case R.id.mc_connectBtn:
                ((SuperUI) getActivity()).toggleDroneConnection();
                break;

            case R.id.mc_armBtn:
                getArmingConfirmation();
                eventBuilder.setAction(ACTION_FLIGHT_ACTION_BUTTON).setLabel("Arm");
                break;

            case R.id.mc_disarmBtn:
                getDrone().arm(false);
                eventBuilder.setAction(ACTION_FLIGHT_ACTION_BUTTON).setLabel("Disarm");
                break;

            case R.id.mc_land:
                getDrone().changeVehicleMode(VehicleMode.COPTER_LAND);
                eventBuilder.setAction(ACTION_FLIGHT_ACTION_BUTTON).setLabel(VehicleMode
                        .COPTER_LAND.getLabel());
                break;

            case R.id.mc_takeoff:
                getTakeOffConfirmation();
                eventBuilder.setAction(ACTION_FLIGHT_ACTION_BUTTON).setLabel("Takeoff");
                break;

            case R.id.mc_homeBtn:
                getDrone().changeVehicleMode(VehicleMode.COPTER_RTL);
                eventBuilder.setAction(ACTION_FLIGHT_ACTION_BUTTON).setLabel(VehicleMode.COPTER_RTL
                        .getLabel());
                break;

            case R.id.mc_pause: {
                final FollowState followState = drone.getAttribute(AttributeType.FOLLOW_STATE);
                if (followState.isEnabled()) {
                    drone.disableFollowMe();
                }

                drone.pauseAtCurrentLocation();
                eventBuilder.setAction(ACTION_FLIGHT_ACTION_BUTTON).setLabel("Pause");
                break;
            }

            case R.id.mc_autoBtn:
                getDrone().changeVehicleMode(VehicleMode.COPTER_AUTO);
                eventBuilder.setAction(ACTION_FLIGHT_ACTION_BUTTON).setLabel(VehicleMode.COPTER_AUTO.getLabel());
                break;

            case R.id.mc_TakeoffInAutoBtn:
                getTakeOffInAutoConfirmation();
                eventBuilder.setAction(ACTION_FLIGHT_ACTION_BUTTON).setLabel(VehicleMode.COPTER_AUTO.getLabel());
                break;

            case R.id.mc_follow:
                toggleFollowMe();
                break;

//            case R.id.mc_dronieBtn:
//                getDronieConfirmation();
//                eventBuilder.setAction(ACTION_FLIGHT_ACTION_BUTTON).setLabel("Dronie uploaded");
//                break;

            case R.id.mc_hookBtn: {
                handleHook();
                break;
            }

            case R.id.mc_dropPointBtn: {
                handleDropPoint();
                break;
            }

            case R.id.mc_sendMissionBtn: {
                handleSendMission();
                break;
            }

            default:
                eventBuilder = null;
                break;
        }

        if (eventBuilder != null) {
            GAUtils.sendEvent(eventBuilder);
        }

    }

    private void getDronieConfirmation() {
        SupportYesNoWithPrefsDialog ynd = SupportYesNoWithPrefsDialog.newInstance(getActivity()
                .getApplicationContext(), getString(R.string.pref_dronie_creation_title),
            getString(R.string.pref_dronie_creation_message), new SupportYesNoDialog.Listener() {
                @Override
                public void onYes() {
                    missionProxy.makeAndUploadDronie(getDrone());
                }

                @Override
                public void onNo() {
                }
            }, DroidPlannerPrefs.PREF_WARN_ON_DRONIE_CREATION);

        if (ynd != null) {
            ynd.show(getChildFragmentManager(), "Confirm dronie creation");
        }
    }

    private void getTakeOffConfirmation(){
        final SlideToUnlockDialog unlockDialog = SlideToUnlockDialog.newInstance("take off", new Runnable() {
            @Override
            public void run() {
                final int takeOffAltitude = getAppPrefs().getDefaultAltitude();
                getDrone().doGuidedTakeoff(takeOffAltitude);
            }
        });

        unlockDialog.show(getChildFragmentManager(), "Slide to take off");
    }

    private void getTakeOffInAutoConfirmation() {
        final SlideToUnlockDialog unlockDialog = SlideToUnlockDialog.newInstance("take off in auto", new Runnable() {
            @Override
            public void run() {
                final int takeOffAltitude = getAppPrefs().getDefaultAltitude();

                final Drone drone = getDrone();

                // Need to bump the throttle to actually initiate the mission. Stupid.
                drone.doGuidedTakeoff(takeOffAltitude);

                broadcast(new Intent(AeroKontiki.EVENT_FLYING));

                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        drone.changeVehicleMode(VehicleMode.COPTER_AUTO);
                    }
                }, 10000);
            }
        });

        unlockDialog.show(getChildFragmentManager(), "Slide to take off in auto");
    }

    private void getArmingConfirmation() {
        SlideToUnlockDialog unlockDialog = SlideToUnlockDialog.newInstance("arm", new Runnable() {
            @Override
            public void run() {
                getDrone().arm(true);
            }
        }) ;
        unlockDialog.show(getChildFragmentManager(), "Slide To Arm");
    }

    private void updateFlightModeButtons() {
        resetFlightModeButtons();

        State droneState = getDrone().getAttribute(AttributeType.STATE);
        if (droneState == null)
            return;

        final VehicleMode flightMode = droneState.getVehicleMode();
        if (flightMode == null)
            return;

        switch (flightMode) {
            case COPTER_AUTO:
                autoBtn.setActivated(true);
                break;

            case COPTER_GUIDED:
                final Drone drone = getDrone();
                final GuidedState guidedState = drone.getAttribute(AttributeType.GUIDED_STATE);
                final FollowState followState = drone.getAttribute(AttributeType.FOLLOW_STATE);
                if (guidedState.isInitialized() && !followState.isEnabled()) {
                    pauseBtn.setActivated(true);
                }
                break;

            case COPTER_RTL:
                homeBtn.setActivated(true);
                break;

            case COPTER_LAND:
                landBtn.setActivated(true);
                break;
            default:
                break;
        }
    }

    private void resetFlightModeButtons() {
        homeBtn.setActivated(false);
        landBtn.setActivated(false);
        pauseBtn.setActivated(false);
        autoBtn.setActivated(false);
    }

    private void updateFollowButton() {
        FollowState followState = getDrone().getAttribute(AttributeType.FOLLOW_STATE);
        if (followState == null)
            return;

        switch (followState.getState()) {
            case FollowState.STATE_START:
                followBtn.setBackgroundColor(orangeColor);
                break;

            case FollowState.STATE_RUNNING:
                followBtn.setActivated(true);
                followBtn.setBackgroundResource(R.drawable.flight_action_row_bg_selector);
                break;

            default:
                followBtn.setActivated(false);
                followBtn.setBackgroundResource(R.drawable.flight_action_row_bg_selector);
                break;
        }
    }

    private void resetButtonsContainerVisibility() {
        mDisconnectedButtons.setVisibility(View.GONE);
        mDisarmedButtons.setVisibility(View.GONE);
        mArmedButtons.setVisibility(View.GONE);
        mInFlightButtons.setVisibility(View.GONE);
    }

    private void setupButtonsByFlightState() {
        final State droneState = getDrone().getAttribute(AttributeType.STATE);
        if (droneState != null && droneState.isConnected()) {

            // Only set this once, so it doesn't get set again after it's been altered by a mission.
            if(!AeroKontiki.hasWPSpeedParam()) {
                AeroKontiki.setWPSpeedParam(getDrone().getSpeedParameter());
            }

            if (droneState.isArmed()) {
                if (droneState.isFlying()) {
                    setupButtonsForFlying();
                } else {
                    setupButtonsForArmed();
                }
            } else {
                setupButtonsForDisarmed();
            }
        }
        else {
            setupButtonsForDisconnected();
        }
    }

    private void setupButtonsForDisconnected() {
        resetButtonsContainerVisibility();
        mDisconnectedButtons.setVisibility(View.VISIBLE);
        broadcast(new Intent(AeroKontiki.EVENT_DISCONNECTED));

    }

    private void setupButtonsForDisarmed() {
        resetButtonsContainerVisibility();
        mDisarmedButtons.setVisibility(View.VISIBLE);

        broadcast(new Intent(AeroKontiki.EVENT_DISARMED));
    }

    private void setupButtonsForArmed() {
        resetButtonsContainerVisibility();
        mArmedButtons.setVisibility(View.VISIBLE);

        broadcast(new Intent(AeroKontiki.EVENT_ARMED));
    }

    private void setupButtonsForFlying() {
        resetButtonsContainerVisibility();
        mInFlightButtons.setVisibility(View.VISIBLE);

        broadcast(new Intent(AeroKontiki.EVENT_FLYING));
    }

    @Override
    public boolean isSlidingUpPanelEnabled(Drone drone) {
        if (!drone.isConnected())
            return false;

        final State droneState = drone.getAttribute(AttributeType.STATE);
        return droneState.isArmed() && droneState.isFlying();
    }

    void handleDropPoint() {
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

            Toast.makeText(getActivity(), R.string.toast_drag_point_prompt, Toast.LENGTH_LONG).show();
            say(getActivity().getString(R.string.tts_set_drop_point_prompt));

            uploadButton.setVisibility(View.VISIBLE);

            broadcast(new Intent(AeroKontiki.EVENT_POINT_DROPPED));
        }
        else {
            Toast.makeText(getActivity(), "Drone location not valid.", Toast.LENGTH_SHORT).show();
        }
    }

    void handleSendMission() {
        Log.v(TAG, "handleSendMission()");
        final Drone drone = DroidPlannerApp.get().getDrone();

        AeroKontiki.massageMission(missionProxy);

        missionProxy.sendMissionToAPM(drone);
        broadcast(new Intent(AeroKontiki.EVENT_MISSION_SENT));

        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                say(getActivity().getString(R.string.tts_arm_checklist_prompt));
            }
        }, 5000);
    }

    void handleHook() {
        say("Operating hook");
        ExperimentalApi.triggerCamera(DroidPlannerApp.get().getDrone());
    }

    private void say(String str) {
        Intent intent = new Intent(AeroKontiki.EVENT_SPEAK)
            .putExtra(AeroKontiki.EXTRA_DATA, str);
        broadcast(intent);
    }

    private void broadcast(Intent intent) {
        LocalBroadcastManager.getInstance(DroidPlannerApp.get()).sendBroadcast(intent);
    }
}
