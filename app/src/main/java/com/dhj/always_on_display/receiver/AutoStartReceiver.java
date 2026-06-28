package com.dhj.always_on_display.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.dhj.always_on_display.data.AppSelectorStore;
import com.dhj.always_on_display.logging.DebugLog;
import com.dhj.always_on_display.service.KeepAwakeRestartScheduler;
import com.dhj.always_on_display.service.KeepAwakeServiceController;

public class AutoStartReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? "<null>" : intent.getAction();
        DebugLog.i(context, "Auto-start receiver invoked: action=" + action);
        if (!AppSelectorStore.isAppEnabled(context)) {
            DebugLog.i(context, "Auto-start ignored because application is disabled by user");
            if (KeepAwakeRestartScheduler.ACTION_RESTART_MONITOR.equals(action)) {
                KeepAwakeRestartScheduler.cancelRestart(context, "restart_skipped_disabled");
            }
            return;
        }
        if (KeepAwakeRestartScheduler.ACTION_RESTART_MONITOR.equals(action)) {
            KeepAwakeRestartScheduler.cancelRestart(context, "restart_broadcast_received");
        }
        KeepAwakeServiceController.syncService(context, "broadcast:" + action);
    }
}
