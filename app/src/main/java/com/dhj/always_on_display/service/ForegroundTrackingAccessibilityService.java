package com.dhj.always_on_display.service;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.accessibility.AccessibilityEvent;

import com.dhj.always_on_display.data.ForegroundAppStore;
import com.dhj.always_on_display.logging.DebugLog;

public class ForegroundTrackingAccessibilityService extends AccessibilityService {
    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        DebugLog.i(this, "Accessibility tracking service connected");
        KeepAwakeServiceController.syncService(this, "accessibility_service_connected");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) {
            return;
        }

        String packageName = event.getPackageName().toString();
        if (getPackageName().equals(packageName)) {
            return;
        }

        long eventTime = event.getEventTime();
        ForegroundAppStore.writeForegroundPackage(this, packageName, eventTime);
        DebugLog.d(this, "Accessibility foreground event: package="
                + packageName
                + ", eventType="
                + event.getEventType()
                + ", eventTime="
                + eventTime);
        KeepAwakeServiceController.refreshServiceState(this, "accessibility_event");
    }

    @Override
    public void onInterrupt() {
        DebugLog.w(this, "Accessibility tracking service interrupted");
    }

    @Override
    public boolean onUnbind(Intent intent) {
        DebugLog.w(this, "Accessibility tracking service unbound");
        ForegroundAppStore.writeForegroundPackage(this, null, 0L);
        KeepAwakeServiceController.refreshServiceState(this, "accessibility_service_unbound");
        return super.onUnbind(intent);
    }
}
