package org.droidplanner.android.aerokontiki;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import org.droidplanner.android.DroidPlannerApp;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by kellys on 10/2/15.
 */
public class LaunchProfile {
    static final String TAG = LaunchProfile.class.getSimpleName();
    private static final String PREF_PROFILES = "pref_launch_profiles";

    static LaunchProfile populate(LaunchProfile p, JSONObject jo) {
        p.name = jo.optString("name");
        p.takeoffAngle = jo.optInt("takeoffAngle");

        p.dropAltitude = jo.optDouble("dropAltitude");
        p.haulSpeed = jo.optDouble("haulSpeed");
        p.returnSpeed = jo.optDouble("returnSpeed");
        p.haulAltitude = jo.optDouble("haulAltitude");
        return p;
    }

    static JSONObject populate(JSONObject jo, LaunchProfile p) {
        try {
            jo.put("name", p.name);
            jo.put("takeoffAngle", p.takeoffAngle);

            jo.put("dropAltitude", p.dropAltitude);
            jo.put("haulSpeed", p.haulSpeed);
            jo.put("returnSpeed", p.returnSpeed);
            jo.put("haulAltitude", p.haulAltitude);
            return jo;
        }
        catch(Throwable ex) {
            Log.e(TAG, ex.getMessage(), ex);
            return null;
        }
    }

    static SharedPreferences getPrefs() {
        return PreferenceManager.getDefaultSharedPreferences(DroidPlannerApp.get());
    }

    public static List<LaunchProfile> all() {
        final ArrayList<LaunchProfile> list = new ArrayList<>();

        final SharedPreferences prefs = getPrefs();
        final String str = prefs.getString(PREF_PROFILES, "[]");

        try {
            final JSONArray aa = new JSONArray(str);

            final int size = aa.length();
            for(int i = 0; i < size; ++i) {
                JSONObject jo = aa.getJSONObject(i);
                LaunchProfile profile = populate(new LaunchProfile(), jo);
                if(profile != null) {
                    list.add(profile);
                }
            }
        }
        catch(Throwable ex) {
            Log.e(TAG, ex.getMessage(), ex);
        }

        return list;
    }

    public static boolean save(List<LaunchProfile> list) {
        try {
            final JSONArray aa = new JSONArray();

            for(LaunchProfile pp: list) {
                JSONObject jo = populate(new JSONObject(), pp);
                if(jo != null) {
                    aa.put(jo);
                }
            }

            final SharedPreferences prefs = getPrefs();
            prefs.edit().putString(PREF_PROFILES, aa.toString()).commit();
            return true;
        }
        catch(Throwable ex) {
            Log.e(TAG, ex.getMessage(), ex);
            return false;
        }
    }

    public String name;
    public int takeoffAngle;
    public double haulAltitude;
    public double dropAltitude;
    public double haulSpeed;
    public double returnSpeed;

    public LaunchProfile() {
        super();
    }

    @Override
    public boolean equals(Object o) {
        if(this == o) return true;
        if(o == null || getClass() != o.getClass()) return false;

        LaunchProfile that = (LaunchProfile) o;

        boolean eq = false;

        do {
            if(takeoffAngle != that.takeoffAngle) break;
            if(Double.compare(that.haulAltitude, haulAltitude) != 0) break;
            if(Double.compare(that.dropAltitude, dropAltitude) != 0) break;
            if(Double.compare(that.haulSpeed, haulSpeed) != 0) break;
            if(Double.compare(that.returnSpeed, returnSpeed) != 0) break;
            if (!name.equals(that.name)) break;

            eq = true;
        } while(false);

        return eq;
    }

    @Override
    public int hashCode() {
        int result;
        long temp;
        result = name.hashCode();
        result = 31 * result + takeoffAngle;
        temp = Double.doubleToLongBits(haulAltitude);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(dropAltitude);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(haulSpeed);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(returnSpeed);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        return result;
    }
}
