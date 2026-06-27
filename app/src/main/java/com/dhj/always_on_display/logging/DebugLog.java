package com.dhj.always_on_display.logging;

import android.content.Context;
import android.util.Log;

import androidx.annotation.Nullable;

import com.dhj.always_on_display.data.AppSelectorStore;

public final class DebugLog {
    public static final String TAG = "AlwaysOnDebug";

    private DebugLog() {
    }

    public static void d(@Nullable Context context, String message) {
        if (isEnabled(context)) {
            Log.d(TAG, message);
        }
    }

    public static void i(@Nullable Context context, String message) {
        if (isEnabled(context)) {
            Log.i(TAG, message);
        }
    }

    public static void w(@Nullable Context context, String message) {
        if (isEnabled(context)) {
            Log.w(TAG, message);
        }
    }

    public static void e(@Nullable Context context, String message, Throwable throwable) {
        if (isEnabled(context)) {
            Log.e(TAG, message, throwable);
        }
    }

    public static boolean isEnabled(@Nullable Context context) {
        return context != null && AppSelectorStore.isDebugLoggingEnabled(context);
    }
}
