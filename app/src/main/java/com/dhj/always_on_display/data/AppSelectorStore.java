package com.dhj.always_on_display.data;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class AppSelectorStore {
    public static final String PREFS_NAME = "overlay_compat";
    public static final String KEY_SELECTED_PACKAGES = "selected_packages";
    public static final String KEY_OVERLAY_ACTIVE = "overlay_active";

    private AppSelectorStore() {
    }

    public static Set<String> readSelectedPackages(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> packages = preferences.getStringSet(KEY_SELECTED_PACKAGES, Collections.emptySet());
        return new HashSet<>(packages);
    }

    public static void writeSelectedPackages(Context context, Set<String> packages) {
        SharedPreferences preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        preferences.edit().putStringSet(KEY_SELECTED_PACKAGES, new HashSet<>(packages)).apply();
    }

    public static boolean isOverlayActive(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return preferences.getBoolean(KEY_OVERLAY_ACTIVE, false);
    }

    public static void setOverlayActive(Context context, boolean active) {
        SharedPreferences preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        preferences.edit().putBoolean(KEY_OVERLAY_ACTIVE, active).apply();
    }
}
