package org.droidplanner.android.proxy.mission.item.fragments;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.Button;
import android.widget.TextView;

import org.beyene.sius.unit.composition.speed.SpeedUnit;
import org.beyene.sius.unit.impl.FactorySpeed;
import org.beyene.sius.unit.length.LengthUnit;
import org.droidplanner.android.DroidPlannerApp;
import org.droidplanner.android.R;
import org.droidplanner.android.aerokontiki.AeroKontiki;
import org.droidplanner.android.utils.prefs.DroidPlannerPrefs;
import org.droidplanner.android.utils.unit.UnitManager;
import org.droidplanner.android.utils.unit.providers.area.AreaUnitProvider;
import org.droidplanner.android.utils.unit.providers.length.LengthUnitProvider;
import org.droidplanner.android.utils.unit.providers.speed.SpeedUnitProvider;
import org.droidplanner.android.utils.unit.systems.ImperialUnitSystem;
import org.droidplanner.android.utils.unit.systems.UnitSystem;
import org.droidplanner.android.widgets.spinnerWheel.CardWheelHorizontalView;
import org.droidplanner.android.widgets.spinnerWheel.adapters.LengthWheelAdapter;
import org.droidplanner.android.widgets.spinnerWheel.adapters.SpeedWheelAdapter;

/**
 * Created by kellys on 8/5/15.
 */
public class SpeedAndAltitudeFragment extends Fragment {
    static final String TAG = SpeedAndAltitudeFragment.class.getSimpleName();

    public interface Listener {
        void onMissionCanceled();
        void onDragSpeedSet(double speed);
        void onReturnSpeedSet(double speed);
        void onTakeoffAltitudeSet(double alt);
        void onDropAltitudeSet(double alt);
    }

    static final int MIN_ALTITUDE = 10;
    static final int MAX_ALTITUDE = 100;
    static final int MIN_SPEED = 2;
    static final int MAX_HAUL_SPEED = 10;
    static final int MAX_RETURN_SPEED = 15;

    private final CardWheelHorizontalView.OnCardWheelScrollListener<SpeedUnit> mSpeedScrollListener = new CardWheelHorizontalView.OnCardWheelScrollListener<SpeedUnit>() {
        public void onScrollingStarted(CardWheelHorizontalView cardWheel, SpeedUnit startValue) { }
        public void onScrollingUpdate(CardWheelHorizontalView cardWheel, SpeedUnit oldValue, SpeedUnit newValue) { }

        @Override
        public void onScrollingEnded(CardWheelHorizontalView cardWheel, SpeedUnit startValue, SpeedUnit endValue) {
            switch (cardWheel.getId()) {
                case R.id.pick_drag_speed: {
                    double baseValue = endValue.toBase().getValue();
                    if(mListener != null) {
                        mListener.onDragSpeedSet(baseValue);
                    }

                    setSpeedText(cardWheel, R.string.lbl_drag_speed_wheel, baseValue);
                    break;
                }

                case R.id.pick_return_speed: {
                    double baseValue = endValue.toBase().getValue();
                    if(mListener != null) {
                        mListener.onReturnSpeedSet(baseValue);
                    }

                    setSpeedText(cardWheel, R.string.lbl_return_speed_wheel, baseValue);
                    break;
                }
            }
        }
    };

    private final CardWheelHorizontalView.OnCardWheelScrollListener<LengthUnit> mAltitudeScrollListener = new CardWheelHorizontalView.OnCardWheelScrollListener<LengthUnit>() {
        public void onScrollingStarted(CardWheelHorizontalView cardWheel, LengthUnit startValue) { }
        public void onScrollingUpdate(CardWheelHorizontalView cardWheel, LengthUnit oldValue, LengthUnit newValue) { }

        @Override
        public void onScrollingEnded(CardWheelHorizontalView cardWheel, LengthUnit startValue, LengthUnit endValue) {
            switch (cardWheel.getId()) {
                case R.id.pick_takeoff_altitude: {
                    double baseValue = endValue.toBase().getValue();
                    if(mListener != null) {
                        mListener.onTakeoffAltitudeSet(baseValue);
                    }
                    break;
                }

                case R.id.pick_drop_altitude: {
                    double baseValue = endValue.toBase().getValue();
                    if(mListener != null) {
                        mListener.onDropAltitudeSet(baseValue);
                    }
                    break;
                }
            }
        }
    };

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            Log.v(TAG, "intent.action=" + action);

            switch(action) {
                case AeroKontiki.EVENT_CONNECTED:
                case AeroKontiki.EVENT_DISARMED: {
                    showView(mWheelLayout, true);
                    showView(mCancelFlightButton, false);
                    break;
                }

                case AeroKontiki.EVENT_ARMED:
                case AeroKontiki.EVENT_FLYING: {
                    showView(mWheelLayout, false);
                    showView(mDistanceLayout, false);
                    showView(mCancelFlightButton, true);
                    break;
                }

                case AeroKontiki.EVENT_MISSION_SENT: {
                    showView(mDistanceLayout, false);
                    showView(mWheelLayout, false);
                    showView(mCancelFlightButton, false);
                    break;
                }

                case AeroKontiki.EVENT_DISCONNECTED:
                case AeroKontiki.EVENT_POINT_DROPPED: {
                    boolean dropped = AeroKontiki.EVENT_POINT_DROPPED.equals(action);
                    showView(mWheelLayout, dropped);
                    showView(mDistanceLayout, dropped);
                    showView(mCancelFlightButton, false);
                    break;
                }
            }

            showParentViewIfNeeded();
        }
    };

    private final View.OnClickListener mClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            switch(v.getId()) {
                case R.id.btn_cancel_flight: {
                    onCancelFlightClick(v);
                    break;
                }
            }
        }
    };


    private LengthUnitProvider lengthUnitProvider;
    private AreaUnitProvider areaUnitProvider;
    private SpeedUnitProvider speedUnitProvider;

    private View mWheelLayout;
    private TextView mDistanceText;
    private View mDistanceLayout;
    private CardWheelHorizontalView<SpeedUnit> mDragSpeedPicker;
    private CardWheelHorizontalView<SpeedUnit> mReturnSpeedPicker;
    private CardWheelHorizontalView<LengthUnit> mTakeoffAltitudePicker;
    private CardWheelHorizontalView<LengthUnit> mDropAltitudePicker;
    private Button mCancelFlightButton;

    private Listener mListener;

    public void setListener(Listener listener) {
        mListener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_speed_altitude, null, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final Context context = DroidPlannerApp.get();

        initProviders(context);

        mDistanceLayout = view.findViewById(R.id.layout_drag_distance);
        mDistanceText = (TextView)view.findViewById(R.id.txt_drag_distance);

        mWheelLayout = view.findViewById(R.id.layout_wheels);
        mDragSpeedPicker = (CardWheelHorizontalView<SpeedUnit>)view.findViewById(R.id.pick_drag_speed);
        mReturnSpeedPicker = (CardWheelHorizontalView<SpeedUnit>)view.findViewById(R.id.pick_return_speed);
        mTakeoffAltitudePicker = (CardWheelHorizontalView<LengthUnit>)view.findViewById(R.id.pick_takeoff_altitude);
        mDropAltitudePicker = (CardWheelHorizontalView<LengthUnit>)view.findViewById(R.id.pick_drop_altitude);

        final SpeedWheelAdapter haulSpeedAdapter = new SpeedWheelAdapter(context, R.layout.wheel_text_centered,
            speedUnitProvider.boxBaseValueToTarget(MIN_SPEED), speedUnitProvider.boxBaseValueToTarget(MAX_HAUL_SPEED));

        final SpeedWheelAdapter returnSpeedAdapter = new SpeedWheelAdapter(context, R.layout.wheel_text_centered,
            speedUnitProvider.boxBaseValueToTarget(MIN_SPEED), speedUnitProvider.boxBaseValueToTarget(MAX_RETURN_SPEED));

        final LengthWheelAdapter altitudeAdapter = new LengthWheelAdapter(context, R.layout.wheel_text_centered,
            lengthUnitProvider.boxBaseValueToTarget(MIN_ALTITUDE), lengthUnitProvider.boxBaseValueToTarget(MAX_ALTITUDE));

        mDragSpeedPicker.setViewAdapter(haulSpeedAdapter);
        mDragSpeedPicker.addScrollListener(mSpeedScrollListener);

        mReturnSpeedPicker.setViewAdapter(returnSpeedAdapter);
        mReturnSpeedPicker.addScrollListener(mSpeedScrollListener);

        mTakeoffAltitudePicker.setViewAdapter(altitudeAdapter);
        mTakeoffAltitudePicker.addScrollListener(mAltitudeScrollListener);

        mDropAltitudePicker.setViewAdapter(altitudeAdapter);
        mDropAltitudePicker.addScrollListener(mAltitudeScrollListener);

        mCancelFlightButton = (Button)view.findViewById(R.id.btn_cancel_flight);
        mCancelFlightButton.setOnClickListener(mClickListener);

        final DroidPlannerPrefs prefs = DroidPlannerApp.get().getAppPreferences();

        mTakeoffAltitudePicker.setCurrentValue(lengthUnitProvider.boxBaseValueToTarget(prefs.getDefaultDropAltitude()));
        mDragSpeedPicker.setCurrentValue(speedUnitProvider.boxBaseValueToTarget(prefs.getDefaultDragSpeed()));
        mReturnSpeedPicker.setCurrentValue(speedUnitProvider.boxBaseValueToTarget(prefs.getDefaultReturnSpeed()));

        setSpeedText(mDragSpeedPicker, R.string.lbl_drag_speed_wheel, speedUnitProvider.boxBaseValueToTarget(prefs.getDefaultDragSpeed()).getValue());
        setSpeedText(mReturnSpeedPicker, R.string.lbl_return_speed_wheel, speedUnitProvider.boxBaseValueToTarget(prefs.getDefaultReturnSpeed()).getValue());

        showView(mWheelLayout, false);
        showView(mCancelFlightButton, false);
        showView(mDistanceLayout, false);
    }

    @Override
    public void onResume() {
        super.onResume();

        LocalBroadcastManager.getInstance(DroidPlannerApp.get()).registerReceiver(mReceiver, AeroKontiki.populate(new IntentFilter()));
    }

    @Override
    public void onPause() {
        super.onPause();

        try {
            LocalBroadcastManager.getInstance(DroidPlannerApp.get()).unregisterReceiver(mReceiver);
        } catch(Throwable ex) { /* ok */ }
    }

    void initProviders(Context context) {
        final UnitSystem unitSystem = UnitManager.getUnitSystem(context);
        lengthUnitProvider = unitSystem.getLengthUnitProvider();
        areaUnitProvider = unitSystem.getAreaUnitProvider();
        speedUnitProvider = unitSystem.getSpeedUnitProvider();
    }

    public void showDragDistance(double distance) {
        final int maxDrag = DroidPlannerApp.get().getAppPreferences().getMaxDragDistance();
        String unit = (distance > 1000)? "km": "m";
        double measured = (distance > 1000)? (distance / 1000): distance;

        mDistanceText.setText(String.format("%.2f %s", measured, unit));
        int colorId = (distance > maxDrag)? R.color.layout_drag_distance_error: R.color.layout_drag_distance_normal;
        mDistanceLayout.setBackgroundColor(getActivity().getResources().getColor(colorId));
    }

    void onCancelFlightClick(View v) {
        new AlertDialog.Builder(getActivity())
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setTitle(R.string.dlg_cancel_flight_title)
            .setMessage(R.string.dlg_cancel_flight_msg)
            .setNegativeButton(R.string.btn_no, null)
            .setPositiveButton(R.string.btn_yes, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    onCancelFlightConfirm();
                }
            })
            .create()
            .show();
    }

    void onCancelFlightConfirm() {
        if(mListener != null) {
            mListener.onMissionCanceled();
        }
    }

    void showView(View v, boolean show) {
        if(v != null) {
            v.setVisibility((show) ? View.VISIBLE : View.GONE);
        }
    }

    void showParentViewIfNeeded() {
        View view = getView();
        ViewParent parent = view.getParent(); // This should be the layout that contains this fragment.
        View parentView = (parent != null && (parent instanceof View))? (View)parent: null;

        if(parentView != null) {
            boolean show = false;

            for(View vv: new View[] {mCancelFlightButton, mWheelLayout, mDistanceLayout}) {
                if(mCancelFlightButton.getVisibility() == View.VISIBLE) {
                    show = true;
                    break;
                }
            }
        }
        else {
            Log.w(TAG, "parentView is null");
        }
    }

    void setSpeedText(CardWheelHorizontalView<?> wheel, int stringId, double value) {
        final Context context = wheel.getContext();
        UnitSystem sys = UnitManager.getUnitSystem(context);

        String text = null;

        if(sys instanceof ImperialUnitSystem) {
            text = context.getString(R.string.cvt_mph, FactorySpeed.mph(value).getValue());
        }
        else {
            text = context.getString(R.string.cvt_kph, AeroKontiki.metersSecondToKph(value));
        }

        wheel.setText(context.getString(stringId, text));
    }
}
