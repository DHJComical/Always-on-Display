package com.dhj.always_on_display.service;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;

import java.util.List;

public final class KeepAwakeServiceStatus {
    private KeepAwakeServiceStatus() {
    }

    @SuppressWarnings("deprecation")
    public static boolean isRunning(Context context) {
        ActivityManager activityManager = context.getSystemService(ActivityManager.class);
        if (activityManager == null) {
            return false;
        }

        List<ActivityManager.RunningServiceInfo> services =
                activityManager.getRunningServices(Integer.MAX_VALUE);
        if (services == null || services.isEmpty()) {
            return false;
        }

        String expectedClassName = KeepAwakeOverlayService.class.getName();
        String expectedPackageName = context.getPackageName();
        for (ActivityManager.RunningServiceInfo serviceInfo : services) {
            ComponentName componentName = serviceInfo.service;
            if (componentName == null) {
                continue;
            }
            if (expectedPackageName.equals(componentName.getPackageName())
                    && expectedClassName.equals(componentName.getClassName())) {
                return true;
            }
        }
        return false;
    }
}
