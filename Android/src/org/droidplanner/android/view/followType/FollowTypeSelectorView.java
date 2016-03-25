package org.droidplanner.android.view.followType;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import com.o3dr.android.client.Drone;
import com.o3dr.android.client.apis.solo.SoloMessageApi;
import com.o3dr.services.android.lib.gcs.follow.FollowType;

import org.droidplanner.android.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by kellys on 3/27/16.
 */
public class FollowTypeSelectorView extends LinearLayout {
    static final String TAG = FollowTypeSelectorView.class.getSimpleName();

    private final Drone mDrone;

    private Spinner mTypesSpinner;
    private FollowType mFollowType = FollowType.LEASH;

    public FollowTypeSelectorView(Context context, Drone drone) {
        super(context);
        mDrone = drone;
        init(context);
    }

    public FollowType getFollowType() {
        return mFollowType;
    }

    public FollowTypeSelectorView setFollowType(FollowType type) {
        mFollowType = type;

        int idx = 0;

        for(FollowType item: FollowType.values()) {
            if(type == item) {
                mTypesSpinner.setSelection(idx);
            }

            ++idx;
        }

        return this;
    }

    private void init(Context context) {
        LayoutInflater.from(context).inflate(R.layout.view_select_follow_type, this);

        mTypesSpinner = (Spinner)findViewById(R.id.spin_follow_types);
        mTypesSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                mFollowType = (FollowType) parent.getAdapter().getItem(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });

        fillTypesSpinner(context);
    }

    void fillTypesSpinner(Context context) {
        final List<FollowType> list = new ArrayList<FollowType>();
        list.addAll(FollowType.getFollowTypes(true));

        // If connected to a Solo-type drone, add Solo Follow Shot
        SoloMessageApi api = SoloMessageApi.getApi(mDrone);
        if(api != null) {
            list.add(FollowType.SOLO_SHOT);
        }

        final ArrayAdapter<FollowType> adapter = new ArrayAdapter<FollowType>(
                context, android.R.layout.simple_spinner_item, android.R.id.text1, list) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View v = super.getView(position, convertView, parent);
                ((TextView)v).setText(getItem(position).getTypeLabel());
                return v;
            }

            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                View v = super.getDropDownView(position, convertView, parent);
                ((TextView)v).setText(getItem(position).getTypeLabel());
                return v;
            }
        };

        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        mTypesSpinner.setAdapter(adapter);
    }
}
