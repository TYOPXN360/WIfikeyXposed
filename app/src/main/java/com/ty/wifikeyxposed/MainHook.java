package com.ty.wifikeyxposed;

/*
 * 用户需求核心摘要 (CONTEXT RESTORE):
 * 1. 目标应用：WiFi万能钥匙 5.2.13 (com.snda.wifilocating)
 * 2. 核心功能：本地 SVIP 永久解锁、全模块去广告 (开屏/列表/视频)、MD3E 设置界面。
 * 3. 创新功能：精简版青少年模式、去除云控系统、一键清除云控缓存。
 * 4. 交互增强：实现稳定版免 Root 重启机制。
 * 5. 验证流程：每次更改后构建 APK，通过 ADB 安装，重启目标应用，查看 LSPosed 日志。
 * 6. 强制要求：所有更改必须进行 Git Commit。
 * 7. 行为规范：思考必须是中文，交流必须是中文。
 * 8. API 规范：100% 符合 libxposed API 101 规范。
 */

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public class MainHook extends XposedModule {
    private static final String TAG = "WiFiKeyXposed";
    private static final String TARGET_PACKAGE = "com.snda.wifilocating";
    private static final String ME_FRAGMENT_CLASS = "com.wifitutu.ui.me.MeFragment";
    private static final String TEENAGER_MANAGER_CLASS = "com.wifitutu.link.foundation.sdk.c1";
    private static final String AD_STRATEGY_CLASS = "com.wifi.business.potocol.sdk.base.strategy.AdStrategy";
    private static final String REMOTE_CONFIG_INTERFACE = "com.link.ida.sdk.protocol.api.interfaces.IWfRemoteConfig";
    private static final String URI_CHECKER_CLASS = "com.wifitutu.ui.dialog.c";
    private static final String ACTION_RESTART = "com.ty.wifikeyxposed.ACTION_RESTART";
    private static final String ACTION_CLEAR_CLOUD = "com.ty.wifikeyxposed.ACTION_CLEAR_CLOUD";

    private Handler mainHandler;

    public MainHook() {
        super();
    }

    private boolean isFeatureEnabled(String key, boolean def) {
        try {
            SharedPreferences sp = getRemotePreferences("settings");
            if (sp.contains(key)) {
                return sp.getBoolean(key, def);
            }
        } catch (Exception e) {
            log(6, TAG, "Failed to read remote prefs: " + e.getMessage());
        }
        return def;
    }

    @Override
    public void onPackageReady(@NonNull PackageReadyParam param) {
        super.onPackageReady(param);
        if (!param.getPackageName().equals(TARGET_PACKAGE)) {
            return;
        }

        if (mainHandler == null) {
            mainHandler = new Handler(Looper.getMainLooper());
        }

        log(4, TAG, "Hooking into: " + param.getPackageName());

        ClassLoader classLoader = param.getClassLoader();
        
        try {
            hookRestartLogic(classLoader);
            hookMeFragment(classLoader);
            hookPushNotifications(classLoader);
            hookVipStatus(classLoader);
            hookStorage(classLoader);
            hookCommonFlags(classLoader);
            hookAds(classLoader);
            hookTeenagerMode(classLoader);
            hookCloudControl(classLoader);
        } catch (Throwable e) {
            log(6, TAG, "Initialization error: " + e.getMessage());
        }
    }

    private void hookCloudControl(ClassLoader classLoader) {
        try {
            // 1. 拦截远程配置接口
            try {
                Class<?> remoteConfigCls = classLoader.loadClass(REMOTE_CONFIG_INTERFACE);
                for (Method m : remoteConfigCls.getDeclaredMethods()) {
                    if (m.getName().equals("getConfig")) {
                        hook(m).intercept(chain -> {
                            if (isFeatureEnabled("remove_cloud_control", false)) return null;
                            return chain.proceed();
                        });
                    }
                }
            } catch (Exception ignored) {}

            // 2. 中和广告策略中的太极值
            try {
                Class<?> adStrategyCls = classLoader.loadClass(AD_STRATEGY_CLASS);
                Method getTaiChiMethod = adStrategyCls.getDeclaredMethod("getTaiChiValue");
                hook(getTaiChiMethod).intercept(chain -> {
                    if (isFeatureEnabled("remove_cloud_control", false)) return "";
                    return chain.proceed();
                });
            } catch (Exception ignored) {}

            // 3. 绕过 URI 云控拦截
            try {
                Class<?> uriCheckerCls = classLoader.loadClass(URI_CHECKER_CLASS);
                String[] checkMethods = {"h", "i", "k", "l", "m"};
                for (String name : checkMethods) {
                    try {
                        for (Method m : uriCheckerCls.getDeclaredMethods()) {
                            if (m.getName().equals(name) && m.getReturnType() == boolean.class) {
                                hook(m).intercept(chain -> {
                                    if (isFeatureEnabled("remove_cloud_control", false)) return true;
                                    return chain.proceed();
                                });
                            }
                        }
                    } catch (Exception ignored) {}
                }
            } catch (Exception ignored) {}

        } catch (Exception e) {
            log(6, TAG, "Failed to hook CloudControl: " + e.getMessage());
        }
    }

    private void hookRestartLogic(ClassLoader classLoader) {
        try {
            Class<?> appClass = classLoader.loadClass("android.app.Application");
            Method onCreateMethod = appClass.getDeclaredMethod("onCreate");
            
            hook(onCreateMethod).intercept(chain -> {
                Object result = chain.proceed();
                final Context context = (Context) chain.getThisObject();
                
                BroadcastReceiver receiver = new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        String action = intent.getAction();
                        if (ACTION_RESTART.equals(action)) {
                            doSuicide();
                        } else if (ACTION_CLEAR_CLOUD.equals(action)) {
                            log(4, TAG, "Clearing cloud config files (no auto-restart)...");
                            clearCloudFiles(context);
                        }
                    }
                };
                
                IntentFilter filter = new IntentFilter();
                filter.addAction(ACTION_RESTART);
                filter.addAction(ACTION_CLEAR_CLOUD);
                if (Build.VERSION.SDK_INT >= 34) {
                    context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
                } else {
                    // 低版本系统使用显式 exported 标志
                    context.registerReceiver(receiver, filter);
                }
                return result;
            });
        } catch (Exception e) {
            log(6, TAG, "Failed to hook restart logic: " + e.getMessage());
        }
    }

    private void doSuicide() {
        log(4, TAG, "Suicide initiated. Killing " + Process.myPid());
        Process.killProcess(Process.myPid());
        System.exit(0);
    }

    private void clearCloudFiles(Context context) {
        try {
            // 1. 清理 files 目录下的配置
            File filesDir = context.getFilesDir();
            String[] configPaths = {"probe", "config", "strategy", "mmkv"};
            for (String path : configPaths) {
                deleteDir(new File(filesDir, path));
            }
            
            // 2. 清理相关 SharedPreferences
            File spDir = new File(context.getApplicationInfo().dataDir, "shared_prefs");
            if (spDir.exists()) {
                File[] files = spDir.listFiles();
                if (files != null) {
                    for (File f : files) {
                        String name = f.getName();
                        if (name.contains("config") || name.contains("strategy") || name.contains("cloud")) {
                            boolean deleted = f.delete();
                            if (!deleted) log(4, TAG, "Could not delete: " + name);
                        }
                    }
                }
            }
            log(4, TAG, "Cloud config cleanup finished.");
        } catch (Exception e) {
            log(6, TAG, "Cleanup error: " + e.getMessage());
        }
    }

    private void deleteDir(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File f : files) deleteDir(f);
            }
        }
        boolean deleted = file.delete();
        if (!deleted) log(4, TAG, "Could not delete file/dir: " + file.getName());
    }

    private void hookTeenagerMode(ClassLoader classLoader) {
        try {
            Class<?> teenagerClass = classLoader.loadClass(TEENAGER_MANAGER_CLASS);
            
            Method isRunningMethod = teenagerClass.getDeclaredMethod("isRunning");
            hook(isRunningMethod).intercept(chain -> {
                if (isFeatureEnabled("lite_teenager", false)) return true;
                return chain.proceed();
            });

            Method isLimitedMethod = teenagerClass.getDeclaredMethod("isLimited");
            hook(isLimitedMethod).intercept(chain -> {
                if (isFeatureEnabled("lite_teenager", false)) return false;
                return chain.proceed();
            });

            try {
                Method owMethod = teenagerClass.getDeclaredMethod("ow");
                hook(owMethod).intercept(chain -> {
                    if (isFeatureEnabled("lite_teenager", false)) return null;
                    return chain.proceed();
                });
            } catch (Exception ignored) {}

        } catch (Exception e) {
            log(6, TAG, "Failed to hook TeenagerMode: " + e.getMessage());
        }
    }

    private void hookAds(ClassLoader classLoader) {
        try {
            Class<?> abstractAdsClass = classLoader.loadClass("com.wifi.business.potocol.sdk.base.ad.AbstractAds");
            for (Method m : abstractAdsClass.getDeclaredMethods()) {
                if (m.getName().equals("isBlocked") && m.getParameterCount() == 0) {
                    hook(m).intercept(chain -> {
                        if (isFeatureEnabled("remove_ads", false)) return true;
                        return chain.proceed();
                    });
                }
            }
        } catch (Exception ignored) {}

        try {
            Class<?> adStrategyClass = classLoader.loadClass("com.wifi.business.potocol.sdk.base.strategy.AdStrategy");
            for (Method m : adStrategyClass.getDeclaredMethods()) {
                if (m.getName().equals("getBlock") && m.getParameterCount() == 0) {
                    hook(m).intercept(chain -> {
                        if (isFeatureEnabled("remove_ads", false)) return true;
                        return chain.proceed();
                    });
                }
            }
        } catch (Exception ignored) {}
    }

    private void hookMeFragment(ClassLoader classLoader) {
        try {
            Class<?> meFragmentClass = classLoader.loadClass(ME_FRAGMENT_CLASS);
            
            try {
                Method a2Method = meFragmentClass.getDeclaredMethod("a2");
                a2Method.setAccessible(true);
                hook(a2Method).intercept(new XposedInterface.Hooker() {
                    @Override
                    public Object intercept(@NonNull XposedInterface.Chain chain) throws Throwable {
                        Object result = chain.proceed();
                        try { injectCustomSettings(chain.getThisObject()); } catch (Exception ignored) {}
                        return result;
                    }
                });
            } catch (Exception ignored) {}

            try {
                Method d2Method = meFragmentClass.getDeclaredMethod("d2");
                d2Method.setAccessible(true);
                hook(d2Method).intercept(new XposedInterface.Hooker() {
                    @Override
                    public Object intercept(@NonNull XposedInterface.Chain chain) throws Throwable {
                        if (isFeatureEnabled("unlock_vip", false)) return true;
                        return chain.proceed();
                    }
                });
            } catch (Exception ignored) {}

            final XposedInterface.Hooker hideBannerHooker = new XposedInterface.Hooker() {
                @Override
                public Object intercept(@NonNull XposedInterface.Chain chain) throws Throwable {
                    Object result = chain.proceed();
                    boolean vip = isFeatureEnabled("unlock_vip", false);
                    if (vip) {
                        boolean clean = isFeatureEnabled("deep_clean_vip", false);
                        hideVipBanners(chain.getThisObject(), clean);
                    }
                    return result;
                }
            };

            for (Method m : meFragmentClass.getDeclaredMethods()) {
                if ((m.getName().equals("onResume") || m.getName().equals("y0")) && m.getParameterCount() == 0) {
                    hook(m).intercept(hideBannerHooker);
                }
            }
        } catch (Exception ignored) {}
    }

    private void hideVipBanners(Object meFragment, boolean deepClean) {
        try {
            final Object binding = findBindingField(meFragment);
            if (binding == null) return;
            
            String[] promos = {"regionVip", "regionMovieVip"};
            for (String name : promos) {
                final View v = getFieldSafe(binding, name);
                if (v != null) {
                    mainHandler.post(() -> v.setVisibility(View.GONE));
                }
            }
            
            if (deepClean) {
                String[] flags = {"vipFlag", "vipSepWifiFlag", "vipSepMovieFlag"};
                for (String name : flags) {
                    final View v = getFieldSafe(binding, name);
                    if (v != null) {
                        mainHandler.post(() -> v.setVisibility(View.GONE));
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    private void injectCustomSettings(final Object meFragment) throws Exception {
        Object binding = findBindingField(meFragment);
        if (binding == null) return;

        final View anchor = getFieldSafe(binding, "aboutUs");
        if (anchor != null) {
            mainHandler.post(() -> {
                try { performInjection(anchor); } catch (Exception ignored) {}
            });
        } else {
            final View checkUpdate = getFieldSafe(binding, "checkUpdate");
            if (checkUpdate != null) {
                mainHandler.post(() -> {
                    try { performInjection(checkUpdate); } catch (Exception ignored) {}
                });
            }
        }
    }

    private void performInjection(View anchor) {
        final Context context = anchor.getContext();
        View p = (View) anchor.getParent();
        
        if (!(p instanceof LinearLayout)) {
            if (p != null && p.getParent() instanceof LinearLayout) {
                anchor = p;
                p = (View) anchor.getParent();
            } else {
                return;
            }
        }

        LinearLayout parent = (LinearLayout) p;
        if (parent.findViewWithTag("wifikey_xposed_entry") != null) return;

        TextView customSetting = new TextView(context);
        customSetting.setTag("wifikey_xposed_entry");
        customSetting.setText("Wifi万能钥匙增强");
        customSetting.setTextSize(16);
        customSetting.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        
        if (anchor instanceof TextView) {
            customSetting.setTextColor(((TextView) anchor).getTextColors());
            customSetting.setPadding(anchor.getPaddingLeft(), anchor.getPaddingTop(), anchor.getPaddingRight(), anchor.getPaddingBottom());
        } else {
            int pad = (int) (16 * context.getResources().getDisplayMetrics().density);
            customSetting.setPadding(pad, pad, pad, pad);
        }
        
        customSetting.setLayoutParams(anchor.getLayoutParams());
        customSetting.setOnClickListener(v -> {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName("com.ty.wifikeyxposed", "com.ty.wifikeyxposed.SettingsActivity"));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        });

        int index = parent.indexOfChild(anchor);
        parent.addView(customSetting, index + 1);
        
        View divider = new View(context);
        divider.setBackgroundColor(Color.parseColor("#EEEEEE"));
        parent.addView(divider, index + 1, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2));
    }

    private void hookVipStatus(ClassLoader classLoader) {
        final XposedInterface.Hooker vipMethodHooker = new XposedInterface.Hooker() {
            @Override
            public Object intercept(@NonNull XposedInterface.Chain chain) throws Throwable {
                if (!isFeatureEnabled("unlock_vip", false)) return chain.proceed();

                Method m = (Method) chain.getExecutable();
                Class<?> returnType = m.getReturnType();
                String name = m.getName();
                
                if (m.getParameterCount() == 0) {
                    if (returnType == int.class || returnType == Integer.class) {
                        if (name.equals("getIndex")) return chain.proceed();
                        return 2; 
                    }
                    if (returnType == boolean.class || returnType == Boolean.class) {
                        if (name.equals("Ao")) return false; 
                        if (name.equals("I2")) return true;  
                        if (name.toLowerCase().contains("expired") || name.equals("n")) return false;
                        return true;
                    }
                    if (returnType == long.class || returnType == Long.class) {
                        if (name.toLowerCase().contains("date") || name.toLowerCase().contains("expire") || name.toLowerCase().contains("time")) {
                            return 2082729600000L;
                        }
                        return chain.proceed();
                    }
                    if (returnType.isEnum()) {
                        String typeName = returnType.getName();
                        try {
                            if (typeName.endsWith(".n7") || typeName.equals("g50.g")) {
                                return Enum.valueOf((Class<Enum>) returnType, "SVIP");
                            } else if (typeName.endsWith(".VIP_CATEGORY") || typeName.endsWith(".MOVIE_VIP_CATEGORY")) {
                                return Enum.valueOf((Class<Enum>) returnType, "YEAR");
                            }
                        } catch (Exception ignored) {}
                    }
                }
                return chain.proceed();
            }
        };

        final XposedInterface.Hooker vipConstructorHooker = new XposedInterface.Hooker() {
            @Override
            public Object intercept(@NonNull XposedInterface.Chain chain) throws Throwable {
                Object result = chain.proceed();
                if (!isFeatureEnabled("unlock_vip", false)) return result;

                Object obj = chain.getThisObject();
                if (obj == null) return result;
                
                Class<?> curr = obj.getClass();
                while (curr != null && !curr.getName().equals("java.lang.Object")) {
                    for (Field f : curr.getDeclaredFields()) {
                        try {
                            String name = f.getName().toLowerCase();
                            f.setAccessible(true);
                            Class<?> type = f.getType();
                            if (name.contains("vip") || name.contains("svip")) {
                                if (type == int.class || type == Integer.class) f.set(obj, 2);
                                else if (type == boolean.class || type == Boolean.class) f.set(obj, true);
                                else if (type == long.class || type == Long.class) f.set(obj, 2082729600000L);
                            } else if (name.contains("expired")) {
                                if (type == boolean.class || type == Boolean.class) f.set(obj, false);
                            }
                        } catch (Exception ignored) {}
                    }
                    curr = curr.getSuperclass();
                }
                return result;
            }
        };

        String[] vipClasses = {
            "py.a", "py.b", "w40.f", "w40.g", "j50.a", "j50.b", "ly.b", "i50.g", "l5",
            "com.wifitutu.link.foundation.native_.model.generate.vip.BridgeUserVipInfo",
            "mz.f", "i50.d", "i50.e", "i50.b", "i50.l", "m50.g", "ry.b", "fz.m2", "g50.w", "m50.i",
            "com.wifitutu.movie.core.utils.a", "com.wifitutu.link.foundation.sdk.k1",
            "com.wifitutu.link.foundation.sdk.i1", "com.wifitutu.user.imp.j", "com.wifitutu.widget.core.j",
            "com.wifitutu.widget.core.k9", "com.wifitutu.link.foundation.core.k0",
            "com.wifitutu.link.foundation.react_native.core.VipItemInfo",
            "com.wifitutu.link.foundation.webengine.plugin.VipItemInfo",
            "com.wifitutu.link.foundation.native_.model.generate.user.BridgeUserInfo"
        };
        
        for (String clsName : vipClasses) {
            try {
                Class<?> clazz = classLoader.loadClass(clsName);
                for (Method m : clazz.getDeclaredMethods()) {
                    if (!m.isSynthetic() && !m.getName().equals("toString")) {
                        hook(m).intercept(vipMethodHooker);
                    }
                }
                for (Constructor<?> c : clazz.getDeclaredConstructors()) {
                    hook(c).intercept(vipConstructorHooker);
                }
            } catch (Throwable ignored) {}
        }

        try {
            Class<?> j5Class = classLoader.loadClass("com.wifitutu.link.foundation.core.j5");
            for (Method m : j5Class.getDeclaredMethods()) {
                if (Modifier.isStatic(m.getModifiers()) && (m.getReturnType() == boolean.class || m.getReturnType() == Boolean.class)) {
                    hook(m).intercept(new XposedInterface.Hooker() {
                        @Override
                        public Object intercept(@NonNull XposedInterface.Chain chain) throws Throwable {
                            if (!isFeatureEnabled("unlock_vip", false)) return chain.proceed();
                            String name = chain.getExecutable().getName();
                            if (name.equals("c") || name.equals("d")) return true; 
                            return chain.proceed();
                        }
                    });
                }
            }
        } catch (Throwable ignored) {}
    }

    private void hookStorage(ClassLoader classLoader) {
        XposedInterface.Hooker storageHooker = new XposedInterface.Hooker() {
            @Override
            public Object intercept(@NonNull XposedInterface.Chain chain) throws Throwable {
                if (!isFeatureEnabled("unlock_vip", false)) return chain.proceed();
                String key = (String) chain.getArgs().get(0);
                if (key == null) return chain.proceed();
                String lowerKey = key.toLowerCase();
                String methodName = chain.getExecutable().getName();
                if (lowerKey.contains("vip") || lowerKey.contains("svip")) {
                    if (methodName.equals("getBool")) return true;
                    if (methodName.equals("getInt")) return 2;
                    if (methodName.equals("getLong")) return 2082729600000L;
                    if (methodName.equals("getString")) return "2";
                }
                return chain.proceed();
            }
        };

        String[] storageClasses = {
            "com.wifitutu.link.foundation.sdk.z0",
            "com.wifitutu.link.foundation.sdk.feature.l",
            "com.wifitutu.widget.feature.u"
        };

        for (String cls : storageClasses) {
            try {
                Class<?> clazz = classLoader.loadClass(cls);
                for (Method m : clazz.getDeclaredMethods()) {
                    if (m.getName().startsWith("get") && m.getParameterCount() > 0 && m.getParameterTypes()[0] == String.class) {
                        hook(m).intercept(storageHooker);
                    }
                }
            } catch (Throwable ignored) {}
        }
    }

    private void hookCommonFlags(ClassLoader classLoader) {
        try {
            Class<?> aClass = classLoader.loadClass("com.wifitutu.movie.core.utils.a");
            for (Method m : aClass.getDeclaredMethods()) {
                if (m.getReturnType() == boolean.class && m.getParameterCount() == 0) {
                    hook(m).intercept(new XposedInterface.Hooker() {
                        @Override
                        public Object intercept(@NonNull XposedInterface.Chain chain) throws Throwable {
                            if (!isFeatureEnabled("unlock_vip", false)) return chain.proceed();
                            String name = chain.getExecutable().getName();
                            if (name.equals("j") || name.equals("i")) return true;
                            return chain.proceed();
                        }
                    });
                }
            }
        } catch (Throwable ignored) {}
    }

    private void hookPushNotifications(ClassLoader classLoader) {
        try {
            Class<?> pushHelperClass = classLoader.loadClass("com.wifitutu.wakeup.imp.malawi.push.a");
            Class<?> mwTaskModelClass = classLoader.loadClass("com.wifitutu.wakeup.imp.malawi.strategy.bean.MwTaskModel");
            Method vMethod = pushHelperClass.getDeclaredMethod("v", mwTaskModelClass);

            hook(vMethod).intercept(new XposedInterface.Hooker() {
                @Override
                public Object intercept(@NonNull XposedInterface.Chain chain) throws Throwable {
                    if (isFeatureEnabled("block_news", false)) return null;
                    return chain.proceed();
                }
            });
        } catch (Exception ignored) {}
    }

    private View getFieldSafe(Object obj, String name) {
        try {
            Field f = obj.getClass().getField(name);
            return (View) f.get(obj);
        } catch (Exception e) {
            return null;
        }
    }

    private Object findBindingField(Object obj) {
        Class<?> curr = obj.getClass();
        while (curr != null && !curr.getName().equals("java.lang.Object")) {
            try {
                Field f = curr.getDeclaredField("binding");
                f.setAccessible(true);
                return f.get(obj);
            } catch (Exception ignored) {}
            for (Field f : curr.getDeclaredFields()) {
                if (f.getType().getName().endsWith("Binding")) {
                    try {
                        f.setAccessible(true);
                        Object val = f.get(obj);
                        if (val != null) return val;
                    } catch (Exception ignored) {}
                }
            }
            curr = curr.getSuperclass();
        }
        return null;
    }
}
