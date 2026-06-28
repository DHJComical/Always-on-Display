package com.dhj.always_on_display.monitor;

import android.content.Context;
import android.os.Build;
import android.provider.Settings;

public final class OverlayPermissionMonitor {
    private OverlayPermissionMonitor() {
    }

    public static boolean canDrawOverlays(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }
        return Settings.canDrawOverlays(context);
    }
}
