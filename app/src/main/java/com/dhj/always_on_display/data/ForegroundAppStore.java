package com.dhj.always_on_display.data;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

public final class ForegroundAppStore {
    private static final String PREFS_NAME = "foreground_app_state";
    private static final String KEY_LAST_FOREGROUND_PACKAGE = "last_foreground_package";
    private static final String KEY_LAST_EVENT_UPTIME_MILLIS = "last_event_uptime_millis";

    private ForegroundAppStore() {
    }

    public static void writeForegroundPackage(Context context, @Nullable String packageName, long eventUptimeMillis) {
        SharedPreferences preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        preferences.edit()
                .putString(KEY_LAST_FOREGROUND_PACKAGE, packageName)
                .putLong(KEY_LAST_EVENT_UPTIME_MILLIS, eventUptimeMillis)
                .apply();
    }

    @Nullable
    public static String readForegroundPackage(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return preferences.getString(KEY_LAST_FOREGROUND_PACKAGE, null);
    }

    public static long readLastEventUptimeMillis(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return preferences.getLong(KEY_LAST_EVENT_UPTIME_MILLIS, 0L);
    }
}
