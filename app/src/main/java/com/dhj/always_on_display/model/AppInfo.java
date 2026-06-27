package com.dhj.always_on_display.model;

import android.graphics.drawable.Drawable;

public final class AppInfo {
    private final String appName;
    private final String packageName;
    private final Drawable icon;

    public AppInfo(String appName, String packageName, Drawable icon) {
        this.appName = appName;
        this.packageName = packageName;
        this.icon = icon;
    }

    public String getAppName() {
        return appName;
    }

    public String getPackageName() {
        return packageName;
    }

    public Drawable getIcon() {
        return icon;
    }
}
