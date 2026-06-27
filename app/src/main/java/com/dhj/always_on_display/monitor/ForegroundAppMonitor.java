package com.dhj.always_on_display.monitor;

import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.os.Build;
import android.os.Process;

import com.dhj.always_on_display.logging.DebugLog;

import java.util.List;

public final class ForegroundAppMonitor {
    private static final long EVENT_LOOKBACK_MS = 15_000L;
    private static final long USAGE_STATS_LOOKBACK_MS = 6 * 60 * 60 * 1000L;

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
        boolean granted = mode == AppOpsManager.MODE_ALLOWED;
        DebugLog.d(context, "Usage access check: mode=" + mode + ", granted=" + granted);
        return granted;
    }

    public static String getForegroundPackageName(Context context) {
        UsageStatsManager usageStatsManager = context.getSystemService(UsageStatsManager.class);
        if (usageStatsManager == null) {
            DebugLog.w(context, "UsageStatsManager unavailable");
            return null;
        }

        long endTime = System.currentTimeMillis();
        String packageName = resolveFromEvents(context, usageStatsManager, endTime);
        if (packageName != null) {
            return packageName;
        }

        packageName = resolveFromUsageStats(context, usageStatsManager, endTime);
        if (packageName != null) {
            DebugLog.d(context, "Foreground app fallback from usage stats: " + packageName);
        } else {
            DebugLog.d(context, "Foreground app unavailable from usage events and usage stats");
        }
        return packageName;
    }

    private static String resolveFromEvents(Context context, UsageStatsManager usageStatsManager, long endTime) {
        long startTime = endTime - EVENT_LOOKBACK_MS;
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
        if (packageName != null) {
            DebugLog.d(context, "Foreground app from usage events: " + packageName);
        }
        return packageName;
    }

    private static String resolveFromUsageStats(Context context, UsageStatsManager usageStatsManager, long endTime) {
        List<UsageStats> usageStats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                endTime - USAGE_STATS_LOOKBACK_MS,
                endTime
        );
        if (usageStats == null || usageStats.isEmpty()) {
            return null;
        }

        UsageStats latestUsageStats = null;
        for (UsageStats stats : usageStats) {
            if (stats == null || stats.getPackageName() == null) {
                continue;
            }
            if (latestUsageStats == null || stats.getLastTimeUsed() > latestUsageStats.getLastTimeUsed()) {
                latestUsageStats = stats;
            }
        }
        return latestUsageStats == null ? null : latestUsageStats.getPackageName();
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
