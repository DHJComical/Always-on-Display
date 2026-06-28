package com.dhj.always_on_display.service;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;

import androidx.annotation.NonNull;

import com.dhj.always_on_display.data.AppSelectorStore;
import com.dhj.always_on_display.logging.DebugLog;
import com.dhj.always_on_display.system.BackgroundLaunchHelper;

public final class KeepAwakeRestartScheduler {
    public static final String ACTION_RESTART_MONITOR =
            "com.dhj.always_on_display.action.RESTART_KEEP_AWAKE_MONITOR";

    private static final long RESTART_DELAY_MS = 3_000L;
    private static final int REQUEST_CODE_RESTART = 2001;

    private KeepAwakeRestartScheduler() {
    }

    public static void scheduleRestart(@NonNull Context context, @NonNull String reason) {
        if (!AppSelectorStore.isAppEnabled(context)) {
            DebugLog.i(context, "Restart scheduling skipped because application is disabled by user: reason=" + reason);
            return;
        }

        AlarmManager alarmManager = context.getSystemService(AlarmManager.class);
        if (alarmManager == null) {
            DebugLog.w(context, "AlarmManager unavailable, cannot schedule restart: reason=" + reason);
            return;
        }

        long triggerAtMillis = SystemClock.elapsedRealtime() + RESTART_DELAY_MS;
        PendingIntent restartIntent = createRestartPendingIntent(context, PendingIntent.FLAG_UPDATE_CURRENT);
        if (restartIntent == null) {
            DebugLog.w(context, "Restart PendingIntent unavailable, cannot schedule restart: reason=" + reason);
            return;
        }
        boolean exactAlarmGranted = BackgroundLaunchHelper.canScheduleExactAlarms(context);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (exactAlarmGranted) {
                alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        triggerAtMillis,
                        restartIntent
                );
            } else {
                alarmManager.setAndAllowWhileIdle(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        triggerAtMillis,
                        restartIntent
                );
            }
        } else {
            alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtMillis, restartIntent);
        }
        DebugLog.i(context, "Scheduled keep-awake monitor restart in "
                + RESTART_DELAY_MS
                + "ms: reason="
                + reason
                + ", exactAlarmGranted="
                + exactAlarmGranted);
    }

    public static void cancelRestart(@NonNull Context context, @NonNull String reason) {
        AlarmManager alarmManager = context.getSystemService(AlarmManager.class);
        if (alarmManager == null) {
            return;
        }

        PendingIntent restartIntent = createRestartPendingIntent(context, PendingIntent.FLAG_NO_CREATE);
        if (restartIntent == null) {
            return;
        }

        alarmManager.cancel(restartIntent);
        restartIntent.cancel();
        DebugLog.i(context, "Cancelled pending keep-awake monitor restart: reason=" + reason);
    }

    private static PendingIntent createRestartPendingIntent(@NonNull Context context, int extraFlags) {
        Intent intent = new Intent(context, KeepAwakeOverlayService.class)
                .setAction(KeepAwakeOverlayService.ACTION_START)
                .setPackage(context.getPackageName());
        int flags = PendingIntent.FLAG_IMMUTABLE | extraFlags;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return PendingIntent.getForegroundService(context, REQUEST_CODE_RESTART, intent, flags);
        }
        return PendingIntent.getService(context, REQUEST_CODE_RESTART, intent, flags);
    }
}
