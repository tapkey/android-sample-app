package net.tpky.demoapp;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

class AuthStateManager {

    private static final String KEY_USERNAME = "KEY_USERNAME";
    private static final String KEY_PASSWORD = "KEY_PASSWORD";

    private static SharedPreferences getPreferences(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }

    static void setLoggedIn(Context context, String username, String password) {
        SharedPreferences.Editor editor = getPreferences(context).edit();
        editor.putString(KEY_USERNAME, username);
        editor.putString(KEY_PASSWORD, password);
        editor.apply();
    }

    static void setLoggedOut(Context context) {
        SharedPreferences.Editor editor = getPreferences(context).edit();
        editor.remove(KEY_USERNAME);
        editor.remove(KEY_PASSWORD);
        editor.apply();
    }

    static boolean isLoggedIn(Context context) {
        SharedPreferences preferences = getPreferences(context);
        return preferences.contains(KEY_USERNAME) && preferences.contains(KEY_PASSWORD);
    }

    static String getUsername(Context context) {
        return getPreferences(context).getString(KEY_USERNAME, null);
    }

    static String getPassword(Context context) {
        return getPreferences(context).getString(KEY_PASSWORD, null);
    }
}
