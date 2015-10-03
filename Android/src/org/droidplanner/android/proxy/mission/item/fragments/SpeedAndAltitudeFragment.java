package org.droidplanner.android.proxy.mission.item.fragments;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import org.beyene.sius.unit.composition.speed.SpeedUnit;
import org.beyene.sius.unit.impl.FactorySpeed;
import org.beyene.sius.unit.length.LengthUnit;
import org.droidplanner.android.DroidPlannerApp;
import org.droidplanner.android.R;
import org.droidplanner.android.aerokontiki.AeroKontiki;
import org.droidplanner.android.aerokontiki.LaunchProfile;
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

import java.util.ArrayList;
import java.util.List;

/**
 * Created by kellys on 8/5/15.
 */
public class SpeedAndAltitudeFragment extends Fragment {
    static final String TAG = SpeedAndAltitudeFragment.class.getSimpleName();

    private static final int MSG_UPDATE_TAKEOFF_ANGLE = 101;

    public interface Listener {
        void onMissionCanceled();
        void onHaulSpeedSet(double speed);
        void onReturnSpeedSet(double speed);
        void onHaulAltitudeSet(double alt);
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
            updateControlsFrom(null);

            switch (cardWheel.getId()) {
                case R.id.pick_drag_speed: {
                    double baseValue = endValue.toBase().getValue();
                    if(mListener != null) {
                        mListener.onHaulSpeedSet(baseValue);
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
            updateControlsFrom(null);

            switch (cardWheel.getId()) {
                case R.id.pick_takeoff_altitude: {
                    double baseValue = endValue.toBase().getValue();
                    if(mListener != null) {
                        mListener.onHaulAltitudeSet(baseValue);
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
                    showView(mCancelFlightButton, false);
                    break;
                }

                case AeroKontiki.EVENT_ARMED:
                case AeroKontiki.EVENT_FLYING: {
                    onCloseTabs();
                    showView(mCancelFlightButton, true);
                    break;
                }

                case AeroKontiki.EVENT_MISSION_SENT: {
                    showView(mDistanceLayout, false);
                    onCloseTabs();
                    showView(mCancelFlightButton, false);
                    break;
                }

                case AeroKontiki.EVENT_DISCONNECTED:
                case AeroKontiki.EVENT_POINT_DROPPED: {
                    boolean dropped = AeroKontiki.EVENT_POINT_DROPPED.equals(action);
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

                case R.id.btn_save_profile: {
                    onSaveProfileClick(v);
                    break;
                }

                case R.id.button_controls: {
                    onShowControls();
                    break;
                }

                case R.id.button_profiles: {
                    onShowProfiles();
                    break;
                }

                case R.id.btn_close: {
                    onCloseTabs();
                    break;
                }
            }
        }
    };

    private final SeekBar.OnSeekBarChangeListener mSeekChangeListener = new SeekBar.OnSeekBarChangeListener() {
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            if(fromUser) {
                mHandler.removeMessages(MSG_UPDATE_TAKEOFF_ANGLE);
                Message m = mHandler.obtainMessage(MSG_UPDATE_TAKEOFF_ANGLE, progress);
                m.arg1 = progress;
                mHandler.sendMessageDelayed(m, 500);

                if(fromUser) {
                    updateControlsFrom(null);
                }
            }

            mTakeoffAngleText.setText(String.format("%d", AeroKontiki.computeTakeoffAngleFromProgress(progress)));
        }

        public void onStartTrackingTouch(SeekBar seekBar) { }
        public void onStopTrackingTouch(SeekBar seekBar) { }
    };

    private final AdapterView.OnItemClickListener mItemClickListener = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            LaunchProfile item = (LaunchProfile)parent.getAdapter().getItem(position);
            updateControlsFrom(item);
        }
    };

    private final AdapterView.OnItemLongClickListener mLongClickListener = new AdapterView.OnItemLongClickListener() {
        @Override
        public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
            LaunchProfile item = (LaunchProfile)parent.getAdapter().getItem(position);
            onDeleteItem(item);
            return false;
        }
    };

    private final Handler mHandler = new android.os.Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            switch(msg.what) {
                case MSG_UPDATE_TAKEOFF_ANGLE: {
                    int progress = msg.arg1;
                    DroidPlannerApp.get().getAppPreferences().setTakeoffAngle(progress);
                    return true;
                }

                default: {
                    return false;
                }
            }
        }
    });

    private LengthUnitProvider lengthUnitProvider;
    private AreaUnitProvider areaUnitProvider;
    private SpeedUnitProvider speedUnitProvider;

    private View mWheelLayout;
    private TextView mDistanceText;
    private View mDistanceLayout;
    private CardWheelHorizontalView<SpeedUnit> mHaulSpeedPicker;
    private CardWheelHorizontalView<SpeedUnit> mReturnSpeedPicker;
    private CardWheelHorizontalView<LengthUnit> mHaulAltitudePicker;
    private CardWheelHorizontalView<LengthUnit> mDropAltitudePicker;
    private Button mCancelFlightButton;
    private SeekBar mSeekTakeoffAngle;
    private TextView mTakeoffAngleText;
    private View mProfilesLayout;
    private ListView mListView;
    private View mSaveButton;
    private View mCloseTabsButton;
    private LaunchProfile mSelectedLaunchProfile;

    private Listener mListener;

    private final ArrayList<LaunchProfile> mProfiles = new ArrayList<>();

    public void setListener(Listener listener) {
        mListener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_speed_altitude, null, false);
    }

    void updateControlsFrom(LaunchProfile item) {
        mSelectedLaunchProfile = item;

        if(item != null) {
            mSeekTakeoffAngle.setProgress(item.takeoffAngle);
            mHaulAltitudePicker.setCurrentValue(lengthUnitProvider.boxBaseValueToTarget(item.haulAltitude));
            mDropAltitudePicker.setCurrentValue(lengthUnitProvider.boxBaseValueToTarget(item.dropAltitude));
            mHaulSpeedPicker.setCurrentValue(speedUnitProvider.boxBaseValueToTarget(item.haulSpeed));
            mReturnSpeedPicker.setCurrentValue(speedUnitProvider.boxBaseValueToTarget(item.returnSpeed));

            mListener.onHaulSpeedSet(item.haulSpeed);
            mListener.onHaulAltitudeSet(item.haulAltitude);
            mListener.onDropAltitudeSet(item.dropAltitude);
            mListener.onReturnSpeedSet(item.returnSpeed);
        }

        ((ArrayAdapter<?>)mListView.getAdapter()).notifyDataSetChanged();
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final Context context = DroidPlannerApp.get();

        mProfilesLayout = view.findViewById(R.id.layout_profiles);

        initProviders(context);

        mSeekTakeoffAngle = (SeekBar)view.findViewById(R.id.seek_takeoff_angle);
        mSeekTakeoffAngle.setOnSeekBarChangeListener(mSeekChangeListener);

        mTakeoffAngleText = (TextView)view.findViewById(R.id.txt_takeoff_angle);

        mDistanceLayout = view.findViewById(R.id.layout_drag_distance);
        mDistanceText = (TextView)view.findViewById(R.id.txt_drag_distance);

        mWheelLayout = view.findViewById(R.id.layout_wheels);
        mHaulSpeedPicker = (CardWheelHorizontalView<SpeedUnit>)view.findViewById(R.id.pick_drag_speed);
        mReturnSpeedPicker = (CardWheelHorizontalView<SpeedUnit>)view.findViewById(R.id.pick_return_speed);
        mHaulAltitudePicker = (CardWheelHorizontalView<LengthUnit>)view.findViewById(R.id.pick_takeoff_altitude);
        mDropAltitudePicker = (CardWheelHorizontalView<LengthUnit>)view.findViewById(R.id.pick_drop_altitude);

        final SpeedWheelAdapter haulSpeedAdapter = new SpeedWheelAdapter(context, R.layout.wheel_text_centered,
            speedUnitProvider.boxBaseValueToTarget(MIN_SPEED), speedUnitProvider.boxBaseValueToTarget(MAX_HAUL_SPEED));

        final SpeedWheelAdapter returnSpeedAdapter = new SpeedWheelAdapter(context, R.layout.wheel_text_centered,
            speedUnitProvider.boxBaseValueToTarget(MIN_SPEED), speedUnitProvider.boxBaseValueToTarget(MAX_RETURN_SPEED));

        final LengthWheelAdapter altitudeAdapter = new LengthWheelAdapter(context, R.layout.wheel_text_centered,
            lengthUnitProvider.boxBaseValueToTarget(MIN_ALTITUDE), lengthUnitProvider.boxBaseValueToTarget(MAX_ALTITUDE));

        mHaulSpeedPicker.setViewAdapter(haulSpeedAdapter);
        mHaulSpeedPicker.addScrollListener(mSpeedScrollListener);

        mReturnSpeedPicker.setViewAdapter(returnSpeedAdapter);
        mReturnSpeedPicker.addScrollListener(mSpeedScrollListener);

        mHaulAltitudePicker.setViewAdapter(altitudeAdapter);
        mHaulAltitudePicker.addScrollListener(mAltitudeScrollListener);

        mDropAltitudePicker.setViewAdapter(altitudeAdapter);
        mDropAltitudePicker.addScrollListener(mAltitudeScrollListener);

        mCancelFlightButton = (Button)view.findViewById(R.id.btn_cancel_flight);
        mCancelFlightButton.setOnClickListener(mClickListener);

        final DroidPlannerPrefs prefs = DroidPlannerApp.get().getAppPreferences();

        mHaulAltitudePicker.setCurrentValue(lengthUnitProvider.boxBaseValueToTarget(prefs.getDefaultDropAltitude()));
        mHaulSpeedPicker.setCurrentValue(speedUnitProvider.boxBaseValueToTarget(prefs.getDefaultDragSpeed()));
        mReturnSpeedPicker.setCurrentValue(speedUnitProvider.boxBaseValueToTarget(prefs.getDefaultReturnSpeed()));

        setSpeedText(mHaulSpeedPicker, R.string.lbl_drag_speed_wheel, speedUnitProvider.boxBaseValueToTarget(prefs.getDefaultDragSpeed()).getValue());
        setSpeedText(mReturnSpeedPicker, R.string.lbl_return_speed_wheel, speedUnitProvider.boxBaseValueToTarget(prefs.getDefaultReturnSpeed()).getValue());

        final int progress = prefs.getTakeoffAngle();
        mSeekTakeoffAngle.setProgress(progress);
        mTakeoffAngleText.setText(String.format("%d", AeroKontiki.computeTakeoffAngleFromProgress(progress)));

        mListView = (ListView)view.findViewById(R.id.list);
        mListView.setOnItemClickListener(mItemClickListener);
        mListView.setOnItemLongClickListener(mLongClickListener);
        mListView.setEmptyView(view.findViewById(android.R.id.empty));

        mSaveButton = view.findViewById(R.id.btn_save_profile);
        mSaveButton.setOnClickListener(mClickListener);

        for(int id: new int[] {
            R.id.button_controls,
            R.id.button_profiles,
            R.id.btn_close
        }) {
            view.findViewById(id).setOnClickListener(mClickListener);
        }

        mCloseTabsButton = view.findViewById(R.id.btn_close);

        final boolean show = true;

        showView(mDistanceLayout, show);
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

    @Override
    public void onStart() {
        super.onStart();

        mProfiles.clear();
        mProfiles.addAll(LaunchProfile.all());
        loadProfiles(mProfiles);
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

    void onSaveProfileClick(View v) {
        final View view = LayoutInflater.from(getActivity()).inflate(R.layout.edit_input, null);
        final EditText edit = (EditText)view.findViewById(R.id.edit_input);
        final TextView prompt = (TextView)view.findViewById(R.id.txt_prompt);

        new AlertDialog.Builder(getActivity())
            .setTitle(R.string.dlg_save_profile_title)
            .setView(view)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    final String name = edit.getText().toString();

                    if(!TextUtils.isEmpty(name)) {
                        LaunchProfile profile = new LaunchProfile();

                        profile.name = name;
                        profile.takeoffAngle = mSeekTakeoffAngle.getProgress();
                        profile.haulSpeed = mHaulSpeedPicker.getCurrentValue().toBase().getValue();
                        profile.haulAltitude = mHaulAltitudePicker.getCurrentValue().toBase().getValue();
                        profile.dropAltitude = mHaulAltitudePicker.getCurrentValue().toBase().getValue();
                        profile.returnSpeed = mReturnSpeedPicker.getCurrentValue().toBase().getValue();

                        mProfiles.add(profile);

                        if(LaunchProfile.save(mProfiles)) {
                            loadProfiles(LaunchProfile.all());
                        }
                        else {
                            Toast.makeText(getActivity(), R.string.toast_err_saving_profile, Toast.LENGTH_SHORT).show();
                        }
                    }
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

    void loadProfiles(List<LaunchProfile> list) {
        final ArrayAdapter<LaunchProfile> adapter = new ArrayAdapter<LaunchProfile>(DroidPlannerApp.get(), android.R.layout.simple_list_item_1, android.R.id.text1, list) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View v = super.getView(position, convertView, parent);
                LaunchProfile item = getItem(position);
                boolean selected = (item.equals(mSelectedLaunchProfile));

                String name = item.name;

                TextView tv = (TextView)v;
                tv.setText(name);

                tv.setTypeface(null, (selected)? Typeface.BOLD: Typeface.NORMAL);
                tv.setTextColor((selected)? Color.WHITE: Color.DKGRAY);
                tv.setBackgroundColor((selected)? Color.DKGRAY: Color.TRANSPARENT);

                return v;
            }
        };

        mListView.setAdapter(adapter);
    }

    void onDeleteItem(final LaunchProfile item) {
        new AlertDialog.Builder(getActivity())
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setTitle(item.name)
            .setMessage(R.string.dlg_delete_profile_msg)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    mProfiles.remove(item);
                    LaunchProfile.save(mProfiles);
                    loadProfiles(mProfiles);
                }
            })
            .create()
            .show();
    }

    void onShowControls() {
        showView(mWheelLayout, true);
        showView(mProfilesLayout, false);
        showView(mCloseTabsButton, true);
    }

    void onShowProfiles() {
        showView(mWheelLayout, false);
        showView(mProfilesLayout, true);
        showView(mCloseTabsButton, true);
    }

    void onCloseTabs() {
        showView(mWheelLayout, false);
        showView(mProfilesLayout, false);
        showView(mCloseTabsButton, false);
    }
}
