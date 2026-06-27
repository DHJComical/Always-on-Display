package com.dhj.always_on_display.hook;

import android.app.Activity;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

public class HookEntry extends XposedModule {
    private static final String TAG = "AlwaysOnDisplay";

    @Override
    public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam param) {
        super.onModuleLoaded(param);
        log(android.util.Log.INFO, TAG, "Module loaded in process: " + param.getProcessName());
    }

    @Override
    public void onPackageLoaded(XposedModuleInterface.PackageLoadedParam param) {
        super.onPackageLoaded(param);
        hookActivityLifecycle(param.getPackageName());
    }

    private void hookActivityLifecycle(String packageName) {
        try {
            Method onCreate = Activity.class.getDeclaredMethod("onCreate", Bundle.class);
            Method onResume = Activity.class.getDeclaredMethod("onResume");

            hook(onCreate)
                    .setId("always_on_display_activity_on_create")
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        keepScreenOn(chain.getThisObject(), packageName);
                        return result;
                    });

            hook(onResume)
                    .setId("always_on_display_activity_on_resume")
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        keepScreenOn(chain.getThisObject(), packageName);
                        return result;
                    });

            log(android.util.Log.INFO, TAG, "Keep-screen-on hooks installed for: " + packageName);
        } catch (NoSuchMethodException e) {
            log(android.util.Log.ERROR, TAG, "Unable to find Activity lifecycle methods", e);
        } catch (Throwable t) {
            log(android.util.Log.ERROR, TAG, "Unable to install keep-screen-on hooks for: " + packageName, t);
        }
    }

    private void keepScreenOn(Object thisObject, String packageName) {
        if (!(thisObject instanceof Activity)) {
            return;
        }

        Activity activity = (Activity) thisObject;
        Window window = activity.getWindow();
        if (window == null) {
            return;
        }

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        log(android.util.Log.DEBUG, TAG, "Keep screen on: " + packageName + "/" + activity.getClass().getName());
    }
}
