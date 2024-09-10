
package com.example.xposedlockmodule;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import java.util.HashSet;
import java.util.Set;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class LockModule implements IXposedHookLoadPackage {

    private static final String PREFS_NAME = "XposedLockModulePrefs";
    private static boolean isLocked = false;
    private static Handler handler = new Handler();
    private static int clickCount = 0;
    private static Set<String> allowedApps = new HashSet<>();
    private static int requiredClicks;
    private static float unlockRegionX, unlockRegionY, unlockRegionWidth, unlockRegionHeight;

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (lpparam.packageName.equals("com.android.systemui") || lpparam.packageName.equals("com.android.launcher3")) {
            XposedHelpers.findAndHookMethod(Activity.class, "onResume", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (isLocked && !allowedApps.contains(lpparam.packageName)) {
                        applyOverlay((Activity) param.thisObject);
                    }
                }
            });
        }

        XposedHelpers.findAndHookMethod(Application.class, "onCreate", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Application app = (Application) param.thisObject;
                SharedPreferences prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                loadSettings(prefs);
                if (!isLocked) {
                    isLocked = true;
                    applyOverlay(null);
                }
            }
        });
    }

    private void applyOverlay(Activity activity) {
        WindowManager windowManager = (WindowManager) activity.getSystemService(Context.WINDOW_SERVICE);
        WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                android.graphics.PixelFormat.TRANSLUCENT);

        View overlayView = new View(activity);
        overlayView.setBackgroundColor(0x00000000); // 完全透明

        overlayView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                float x = event.getX();
                float y = event.getY();
                if (x >= unlockRegionX && x <= unlockRegionX + unlockRegionWidth &&
                    y >= unlockRegionY && y <= unlockRegionY + unlockRegionHeight) {
                    clickCount++;
                    if (clickCount >= requiredClicks) {
                        removeOverlay(windowManager, overlayView);
                    }
                }
            }
            return true; // 拦截所有触摸事件
        });

        windowManager.addView(overlayView, layoutParams);
    }

    private void removeOverlay(WindowManager windowManager, View overlayView) {
        try {
            windowManager.removeView(overlayView);
            clickCount = 0;
            isLocked = false;
            Toast.makeText(overlayView.getContext(), "解锁成功", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            XposedBridge.log("移除遮罩失败: " + e.getMessage());
        }
    }

    private void loadSettings(SharedPreferences prefs) {
        requiredClicks = prefs.getInt("requiredClicks", 5);
        unlockRegionX = prefs.getFloat("unlockRegionX", 0);
        unlockRegionY = prefs.getFloat("unlockRegionY", 0);
        unlockRegionWidth = prefs.getFloat("unlockRegionWidth", 100);
        unlockRegionHeight = prefs.getFloat("unlockRegionHeight", 100);
        allowedApps = prefs.getStringSet("allowedApps", new HashSet<>());
    }
}
