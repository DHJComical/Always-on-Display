package com.dhj.always_on_display.monitor;

import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.os.Build;
import android.os.Process;

public final class ForegroundAppMonitor {
    private ForegroundAppMonitor() {
    }

    @SuppressWarnings("deprecation")
    public static boolean hasUsageAccess(Context context) {
        AppOpsManager appOpsManager = context.getSystemService(AppOpsManager.class);
        if (appOpsManager == null) {
            return false;
        }

        int mode;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            mode = appOpsManager.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.getPackageName()
            );
        } else {
            mode = appOpsManager.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.getPackageName()
            );
        }
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    public static String getForegroundPackageName(Context context) {
        UsageStatsManager usageStatsManager = context.getSystemService(UsageStatsManager.class);
        if (usageStatsManager == null) {
            return null;
        }

        long endTime = System.currentTimeMillis();
        long startTime = endTime - 10_000L;
        UsageEvents usageEvents = usageStatsManager.queryEvents(startTime, endTime);
        UsageEvents.Event event = new UsageEvents.Event();

        long latestTimestamp = 0L;
        String packageName = null;
        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event);
            if (isForegroundEvent(event) && event.getTimeStamp() >= latestTimestamp) {
                latestTimestamp = event.getTimeStamp();
                packageName = event.getPackageName();
            }
        }
        return packageName;
    }

    private static boolean isForegroundEvent(UsageEvents.Event event) {
        int eventType = event.getEventType();
        if (eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
            return true;
        }
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                && eventType == UsageEvents.Event.ACTIVITY_RESUMED;
    }
}
