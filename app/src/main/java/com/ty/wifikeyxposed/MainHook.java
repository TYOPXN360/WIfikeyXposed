package com.ty.wifikeyxposed;

/*
 * 用户需求核心摘要 (CONTEXT RESTORE):
 * 1. 目标应用：WiFi万能钥匙 5.2.13 (com.snda.wifilocating)
 * 2. 核心功能：本地 SVIP 永久解锁、全模块去广告 (开屏/列表/视频)、MD3E 设置界面。
 * 3. 验证流程：每次更改后构建 APK，通过 ADB 安装，重启目标应用，查看 LSPosed 日志。
 * 4. 强制要求：所有更改必须进行 Git Commit。
 * 5. 日志查看方法：adb logcat -s LSPosed LSPosed-Bridge WiFiKeyXposed
 * 6. Java 路径: /media/tyopxn360/Android/MC/Java/Java21
 * 7. 行为规范：思考必须是中文，交流必须是中文。遇到报错先联网搜索方案，不得盲目乱改。
 * 8. 解锁策略：必须是“解锁会员”而非“删除会员体系”。保留 SVIP 标识，仅隐藏推广横幅。
 * 9. API 规范：100% 符合 libxposed API 101 规范，完全移除旧 API 支持。
 * 10. 修复记录：通过 getRemotePreferences("settings") 解决跨进程读取失败。
 * 11. 状态同步修复：确保构造函数为空（API 101 要求），并在 onPackageReady 中精准同步开关状态。
 */

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public class MainHook extends XposedModule {
    private static final String TAG \u003d "WiFiKeyXposed";
    private static final String TARGET_PACKAGE \u003d "com.snda.wifilocating";
    private static final String ME_FRAGMENT_CLASS \u003d "com.wifitutu.ui.me.MeFragment";

    private Handler mainHandler;

    public MainHook() {
        super();
    }

    @Override
    public void onPackageReady(@NonNull PackageReadyParam param) {
        super.onPackageReady(param);
        if (!param.getPackageName().equals(TARGET_PACKAGE)) {
            return;
        }

        if (mainHandler \u003d\u003d null) {
            mainHandler \u003d new Handler(Looper.getMainLooper());
        }

        // 核心：在注入时实时读取开关
        final boolean vipEnabled \u003d isUnlockVipEnabled();
        final boolean removeAds \u003d isRemoveAdsEnabled();
        final boolean deepClean \u003d isDeepCleanVipEnabled();
        final boolean blockNews \u003d isBlockNewsEnabled();

        log(4, TAG, String.format("Hooking into: %s (VIP: %b, Ads: %b, Clean: %b, News: %b)", 
            param.getPackageName(), vipEnabled, removeAds, deepClean, blockNews));

        ClassLoader classLoader \u003d param.getClassLoader();
        
        try {
            hookMeFragment(classLoader, vipEnabled, deepClean);
            hookPushNotifications(classLoader, blockNews);
            hookVipStatus(classLoader, vipEnabled);
            hookStorage(classLoader, vipEnabled);
            hookCommonFlags(classLoader, vipEnabled);
            hookAds(classLoader, removeAds);
        } catch (Throwable e) {
            log(6, TAG, "Initialization error: " + e.getMessage());
        }
    }

    private void hookAds(ClassLoader classLoader, boolean enabled) {
        if (!enabled) return;
        
        final XposedInterface.Hooker returnTrueHooker \u003d new XposedInterface.Hooker() {
            @Override public Object intercept(@NonNull XposedInterface.Chain chain) throws Throwable { return true; }
        };
        final XposedInterface.Hooker returnFalseHooker \u003d new XposedInterface.Hooker() {
            @Override public Object intercept(@NonNull XposedInterface.Chain chain) throws Throwable { return false; }
        };

        try {
            Class<?> abstractAdsClass \u003d classLoader.loadClass("com.wifi.business.potocol.sdk.base.ad.AbstractAds");
            for (Method m : abstractAdsClass.getDeclaredMethods()) {
                if (m.getName().equals("isBlocked") \u0026\u0026 m.getParameterCount() \u003d\u003d 0) {
                    hook(m).intercept(returnTrueHooker);
                }
            }
        } catch (Exception ignored) {}

        try {
            Class<?> adStrategyClass \u003d classLoader.loadClass("com.wifi.business.potocol.sdk.base.strategy.AdStrategy");
            for (Method m : adStrategyClass.getDeclaredMethods()) {
                if (m.getName().equals("getBlock") \u0026\u0026 m.getParameterCount() \u003d\u003d 0) {
                    hook(m).intercept(returnTrueHooker);
                }
            }
        } catch (Exception ignored) {}

        try {
            String[] managerClasses \u003d {
                "com.wifitutu.ad.imp.busi.manager.b",
                "com.wifitutu.ad.imp.busi.manager.f"
            };
            Class<?> configValueClass \u003d classLoader.loadClass("vt.c");
            for (String clsName : managerClasses) {
                try {
                    Class<?> cls \u003d classLoader.loadClass(clsName);
                    for (Method m : cls.getDeclaredMethods()) {
                        if (m.getName().equals("a") \u0026\u0026 m.getParameterCount() \u003d\u003d 1 \u0026\u0026 m.getParameterTypes()[0] \u003d\u003d configValueClass) {
                            hook(m).intercept(returnFalseHooker);
                        }
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        
        try {
            Class<?> adsFilterClass \u003d classLoader.loadClass("com.wifi.business.potocol.sdk.base.utils.AdsFilter");
            Field isBlockField \u003d adsFilterClass.getDeclaredField("isBlock");
            isBlockField.setAccessible(true);
            isBlockField.set(null, true);
        } catch (Exception ignored) {}
    }

    private void hookMeFragment(ClassLoader classLoader, final boolean vipEnabled, final boolean deepClean) {
        try {
            Class<?> meFragmentClass \u003d classLoader.loadClass(ME_FRAGMENT_CLASS);
            
            try {
                Method a2Method \u003d meFragmentClass.getDeclaredMethod("a2");
                a2Method.setAccessible(true);
                hook(a2Method).intercept(new XposedInterface.Hooker() {
                    @Override
                    public Object intercept(@NonNull XposedInterface.Chain chain) throws Throwable {
                        Object result \u003d chain.proceed();
                        try { injectCustomSettings(chain.getThisObject()); } catch (Exception ignored) {}
                        return result;
                    }
                });
            } catch (Exception ignored) {}

            try {
                Method d2Method \u003d meFragmentClass.getDeclaredMethod("d2");
                d2Method.setAccessible(true);
                hook(d2Method).intercept(new XposedInterface.Hooker() {
                    @Override
                    public Object intercept(@NonNull XposedInterface.Chain chain) throws Throwable {
                        if (vipEnabled) return true;
                        return chain.proceed();
                    }
                });
            } catch (Exception ignored) {}

            final XposedInterface.Hooker hideBannerHooker \u003d new XposedInterface.Hooker() {
                @Override
                public Object intercept(@NonNull XposedInterface.Chain chain) throws Throwable {
                    Object result \u003d chain.proceed();
                    if (vipEnabled) {
                        hideVipBanners(chain.getThisObject(), deepClean);
                    }
                    return result;
                }
            };

            for (Method m : meFragmentClass.getDeclaredMethods()) {
                if ((m.getName().equals("onResume") || m.getName().equals("y0")) 
                     \u0026\u0026 m.getParameterCount() \u003d\u003d 0) {
                    hook(m).intercept(hideBannerHooker);
                }
            }
        } catch (Exception ignored) {}
    }

    private void hideVipBanners(Object meFragment, boolean deepClean) {
        try {
            final Object binding \u003d findBindingField(meFragment);
            if (binding \u003d\u003d null) return;
            
            String[] promos \u003d {"regionVip", "regionMovieVip"};
            for (String name : promos) {
                final View v \u003d getFieldSafe(binding, name);
                if (v !\u003d null) {
                    mainHandler.post(new Runnable() {
                        @Override public void run() { v.setVisibility(View.GONE); }
                    });
                }
            }
            
            if (deepClean) {
                String[] flags \u003d {"vipFlag", "vipSepWifiFlag", "vipSepMovieFlag"};
                for (String name : flags) {
                    final View v \u003d getFieldSafe(binding, name);
                    if (v !\u003d null) {
                        mainHandler.post(new Runnable() {
                            @Override public void run() { v.setVisibility(View.GONE); }
                        });
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    private void injectCustomSettings(final Object meFragment) throws Exception {
        Object binding \u003d findBindingField(meFragment);
        if (binding \u003d\u003d null) return;

        final View anchor \u003d getFieldSafe(binding, "aboutUs");
        if (anchor !\u003d null) {
            mainHandler.post(new Runnable() {
                @Override public void run() {
                    try { performInjection(anchor); } catch (Exception ignored) {}
                }
            });
        } else {
            final View checkUpdate \u003d getFieldSafe(binding, "checkUpdate");
            if (checkUpdate !\u003d null) {
                mainHandler.post(new Runnable() {
                    @Override public void run() {
                        try { performInjection(checkUpdate); } catch (Exception ignored) {}
                    }
                });
            }
        }
    }

    private void performInjection(View anchor) {
        final Context context \u003d anchor.getContext();
        View parentView \u003d (View) anchor.getParent();
        
        if (!(parentView instanceof LinearLayout)) {
            if (parentView !\u003d null \u0026\u0026 parentView.getParent() instanceof LinearLayout) {
                anchor \u003d parentView;
                parentView \u003d (View) anchor.getParent();
            } else {
                return;
            }
        }

        LinearLayout parent \u003d (LinearLayout) parentView;
        if (parent.findViewWithTag("wifikey_xposed_entry") !\u003d null) return;

        TextView customSetting \u003d new TextView(context);
        customSetting.setTag("wifikey_xposed_entry");
        customSetting.setText("Wifi万能钥匙增强");
        customSetting.setTextSize(16);
        customSetting.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        
        if (anchor instanceof TextView) {
            customSetting.setTextColor(((TextView) anchor).getTextColors());
            customSetting.setPadding(anchor.getPaddingLeft(), anchor.getPaddingTop(), anchor.getPaddingRight(), anchor.getPaddingBottom());
        } else {
            int p \u003d (int) (16 * context.getResources().getDisplayMetrics().density);
            customSetting.setPadding(p, p, p, p);
        }
        
        customSetting.setLayoutParams(anchor.getLayoutParams());
        customSetting.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Intent intent \u003d new Intent();
                intent.setComponent(new ComponentName("com.ty.wifikeyxposed", "com.ty.wifikeyxposed.SettingsActivity"));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            }
        });

        int index \u003d parent.indexOfChild(anchor);
        parent.addView(customSetting, index + 1);
        
        View divider \u003d new View(context);
        divider.setBackgroundColor(Color.parseColor("#EEEEEE"));
        parent.addView(divider, index + 1, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2));
    }

    private void hookVipStatus(final ClassLoader classLoader, final boolean enabled) {
        if (!enabled) return;
        
        final XposedInterface.Hooker vipMethodHooker \u003d new XposedInterface.Hooker() {
            @Override
            public Object intercept(@NonNull XposedInterface.Chain chain) throws Throwable {
                Method m \u003d (Method) chain.getExecutable();
                Class<?> returnType \u003d m.getReturnType();
                String name \u003d m.getName();
                
                if (m.getParameterCount() \u003d\u003d 0) {
                    if (returnType \u003d\u003d int.class || returnType \u003d\u003d Integer.class) {
                        if (name.equals("getIndex")) return chain.proceed();
                        return 2; 
                    }
                    if (returnType \u003d\u003d boolean.class || returnType \u003d\u003d Boolean.class) {
                        if (name.equals("Ao")) return false; 
                        if (name.equals("I2")) return true;  
                        if (name.toLowerCase().contains("expired") || name.equals("n")) return false;
                        return true;
                    }
                    if (returnType \u003d\u003d long.class || returnType \u003d\u003d Long.class) {
                        if (name.toLowerCase().contains("date") || name.toLowerCase().contains("expire") || name.toLowerCase().contains("time")) {
                            return 2082729600000L;
                        }
                        return chain.proceed();
                    }
                    if (returnType.isEnum()) {
                        String typeName \u003d returnType.getName();
                        try {
                            if (typeName.endsWith(".n7") || typeName.equals("g50.g")) {
                                return Enum.valueOf((Class\u003cEnum\u003e) returnType, "SVIP");
                            } else if (typeName.endsWith(".VIP_CATEGORY") || typeName.endsWith(".MOVIE_VIP_CATEGORY")) {
                                return Enum.valueOf((Class\u003cEnum\u003e) returnType, "YEAR");
                            }
                        } catch (Exception ignored) {}
                    }
                }
                return chain.proceed();
            }
        };

        final XposedInterface.Hooker vipConstructorHooker \u003d new XposedInterface.Hooker() {
            @Override
            public Object intercept(@NonNull XposedInterface.Chain chain) throws Throwable {
                Object result \u003d chain.proceed();
                Object obj \u003d chain.getThisObject();
                if (obj \u003d\u003d null) return result;
                
                Class<?> curr \u003d obj.getClass();
                while (curr !\u003d null \u0026\u0026 !curr.getName().equals("java.lang.Object")) {
                    for (Field f : curr.getDeclaredFields()) {
                        try {
                            String name \u003d f.getName().toLowerCase();
                            f.setAccessible(true);
                            Class<?> type \u003d f.getType();
                            if (name.contains("vip") || name.contains("svip")) {
                                if (type \u003d\u003d int.class || type \u003d\u003d Integer.class) f.set(obj, 2);
                                else if (type \u003d\u003d boolean.class || type \u003d\u003d Boolean.class) f.set(obj, true);
                                else if (type \u003d\u003d long.class || type \u003d\u003d Long.class) f.set(obj, 2082729600000L);
                            } else if (name.contains("expired")) {
                                if (type \u003d\u003d boolean.class || type \u003d\u003d Boolean.class) f.set(obj, false);
                            }
                        } catch (Exception ignored) {}
                    }
                    curr \u003d curr.getSuperclass();
                }
                return result;
            }
        };

        String[] vipClasses \u003d {
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
                Class<?> clazz \u003d classLoader.loadClass(clsName);
                for (Method m : clazz.getDeclaredMethods()) {
                    if (!m.isSynthetic() \u0026\u0026 !m.getName().equals("toString")) {
                        hook(m).intercept(vipMethodHooker);
                    }
                }
                for (java.lang.reflect.Constructor<?> c : clazz.getDeclaredConstructors()) {
                    hook(c).intercept(vipConstructorHooker);
                }
            } catch (Throwable ignored) {}
        }

        try {
            Class<?> j5Class \u003d classLoader.loadClass("com.wifitutu.link.foundation.core.j5");
            for (Method m : j5Class.getDeclaredMethods()) {
                if (Modifier.isStatic(m.getModifiers()) \u0026\u0026 (m.getReturnType() \u003d\u003d boolean.class || m.getReturnType() \u003d\u003d Boolean.class)) {
                    hook(m).intercept(new XposedInterface.Hooker() {
                        @Override
                        public Object intercept(@NonNull XposedInterface.Chain chain) throws Throwable {
                            String name \u003d chain.getExecutable().getName();
                            if (name.equals("c") || name.equals("d")) return true; 
                            return chain.proceed();
                        }
                    });
                }
            }
        } catch (Throwable ignored) {}
    }

    private void hookStorage(ClassLoader classLoader, boolean enabled) {
        if (!enabled) return;
        
        XposedInterface.Hooker storageHooker \u003d new XposedInterface.Hooker() {
            @Override
            public Object intercept(@NonNull XposedInterface.Chain chain) throws Throwable {
                String key \u003d (String) chain.getArgs().get(0);
                if (key \u003d\u003d null) return chain.proceed();
                String lowerKey \u003d key.toLowerCase();
                String methodName \u003d chain.getExecutable().getName();
                if (lowerKey.contains("vip") || lowerKey.contains("svip")) {
                    if (methodName.equals("getBool")) return true;
                    if (methodName.equals("getInt")) return 2;
                    if (methodName.equals("getLong")) return 2082729600000L;
                    if (methodName.equals("getString")) return "2";
                }
                return chain.proceed();
            }
        };

        String[] storageClasses \u003d {
            "com.wifitutu.link.foundation.sdk.z0",
            "com.wifitutu.link.foundation.sdk.feature.l",
            "com.wifitutu.widget.feature.u"
        };

        for (String cls : storageClasses) {
            try {
                Class<?> clazz \u003d classLoader.loadClass(cls);
                for (Method m : clazz.getDeclaredMethods()) {
                    if (m.getName().startsWith("get") \u0026\u0026 m.getParameterCount() \u003e 0 \u0026\u0026 m.getParameterTypes()[0] \u003d\u003d String.class) {
                        hook(m).intercept(storageHooker);
                    }
                }
            } catch (Throwable ignored) {}
        }
    }

    private void hookCommonFlags(ClassLoader classLoader, boolean enabled) {
        if (!enabled) return;
        try {
            Class<?> aClass \u003d classLoader.loadClass("com.wifitutu.movie.core.utils.a");
            for (Method m : aClass.getDeclaredMethods()) {
                if (m.getReturnType() \u003d\u003d boolean.class \u0026\u0026 m.getParameterCount() \u003d\u003d 0) {
                    hook(m).intercept(new XposedInterface.Hooker() {
                        @Override
                        public Object intercept(@NonNull XposedInterface.Chain chain) throws Throwable {
                            String name \u003d chain.getExecutable().getName();
                            if (name.equals("j") || name.equals("i")) return true;
                            return chain.proceed();
                        }
                    });
                }
            }
        } catch (Throwable ignored) {}
    }

    private void hookPushNotifications(ClassLoader classLoader, boolean enabled) {
        if (!enabled) return;
        try {
            Class<?> pushHelperClass \u003d classLoader.loadClass("com.wifitutu.wakeup.imp.malawi.push.a");
            Class<?> mwTaskModelClass \u003d classLoader.loadClass("com.wifitutu.wakeup.imp.malawi.strategy.bean.MwTaskModel");
            Method vMethod \u003d pushHelperClass.getDeclaredMethod("v", mwTaskModelClass);

            hook(vMethod).intercept(new XposedInterface.Hooker() {
                @Override
                public Object intercept(@NonNull XposedInterface.Chain chain) throws Throwable {
                    return null; // Block news
                }
            });
        } catch (Exception ignored) {}
    }

    private boolean isUnlockVipEnabled() {
        try {
            SharedPreferences sp \u003d getRemotePreferences("settings");
            return sp.getBoolean("unlock_vip", false); 
        } catch (Exception e) {
            return false; 
        }
    }

    private boolean isDeepCleanVipEnabled() {
        try {
            SharedPreferences sp \u003d getRemotePreferences("settings");
            return sp.getBoolean("deep_clean_vip", false);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isRemoveAdsEnabled() {
        try {
            SharedPreferences sp \u003d getRemotePreferences("settings");
            return sp.getBoolean("remove_ads", false);
        } catch (Exception e) {
            return false; 
        }
    }

    private boolean isBlockNewsEnabled() {
        try {
            SharedPreferences sp \u003d getRemotePreferences("settings");
            return sp.getBoolean("block_news", false);
        } catch (Exception e) {
            return false;
        }
    }

    private View getFieldSafe(Object obj, String name) {
        try {
            Field f \u003d obj.getClass().getField(name);
            return (View) f.get(obj);
        } catch (Exception e) {
            return null;
        }
    }

    private Object findBindingField(Object obj) {
        Class<?> curr \u003d obj.getClass();
        while (curr !\u003d null \u0026\u0026 !curr.getName().equals("java.lang.Object")) {
            try {
                Field f \u003d curr.getDeclaredField("binding");
                f.setAccessible(true);
                return f.get(obj);
            } catch (Exception ignored) {}
            for (Field f : curr.getDeclaredFields()) {
                if (f.getType().getName().endsWith("Binding")) {
                    try {
                        f.setAccessible(true);
                        Object val \u003d f.get(obj);
                        if (val !\u003d null) return val;
                    } catch (Exception ignored) {}
                }
            }
            curr \u003d curr.getSuperclass();
        }
        return null;
    }
}
