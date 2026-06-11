package com.ty.wifikeyxposed;

/*
 * 用户需求核心摘要 (CONTEXT RESTORE):
 * 1. 目标应用：WiFi万能钥匙 5.2.13 (com.snda.wifilocating)
 * 2. 核心功能：本地 SVIP 永久解锁、全模块去广告 (开屏/列表/视频)、MD3E 设置界面。
 * 3. 创新功能：底栏极致精简，且实现状态双向同步。
 * 4. 逻辑增强：实时监控应用底栏开启情况。若应用隐藏了某项，则模块设置自动标记为“精简开启”。
 * 5. 交互增强：实现稳定版免 Root 重启机制、高风险操作倒计时二次确认。
 * 6. 验证流程：每次更改后构建 APK，通过 ADB 安装，重启目标应用，查看 LSPosed 日志。
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
import android.view.ViewGroup;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public class MainHook extends XposedModule {
    private static final String TAG = "WiFiKeyXposed";
    private static final String TARGET_PACKAGE = "com.snda.wifilocating";
    private static final String ME_FRAGMENT_CLASS = "com.wifitutu.ui.me.MeFragment";
    private static final String TEENAGER_MANAGER_CLASS = "com.wifitutu.link.foundation.sdk.d1";
    private static final String AD_STRATEGY_CLASS = "com.wifi.business.potocol.sdk.base.strategy.AdStrategy";
    private static final String REMOTE_CONFIG_INTERFACE = "com.link.ida.sdk.protocol.api.interfaces.IWfRemoteConfig";
    private static final String URI_CHECKER_CLASS = "com.wifitutu.ui.dialog.c";
    private static final String ACTION_RESTART = "com.ty.wifikeyxposed.ACTION_RESTART";
    private static final String ACTION_CLEAR_CLOUD = "com.ty.wifikeyxposed.ACTION_CLEAR_CLOUD";

    private Handler mainHandler;
    private boolean isPerformingForcedHide = false;

    public MainHook() {
        super();
    }

    private boolean isFeatureEnabled(String key, boolean def) {
        try {
            SharedPreferences sp = getRemotePreferences("settings");
            return sp.getBoolean(key, def);
        } catch (Exception e) {
            return def;
        }
    }

    private void updateFeatureState(String key, boolean value) {
        try {
            SharedPreferences sp = getRemotePreferences("settings");
            if (sp.getBoolean(key, !value) != value) {
                sp.edit().putBoolean(key, value).apply();
                log(4, TAG, "Auto-synced tab state: " + key + " -> " + value);
            }
        } catch (Exception e) {
            log(6, TAG, "Failed to update remote prefs: " + e.getMessage());
        }
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
            log(4, TAG, "=== 开始初始化 hook ===");
            hookVipStatus(classLoader);
            log(4, TAG, "hookVipStatus 完成");
            hookBottomNavigation(classLoader);
            log(4, TAG, "hookBottomNavigation 完成");
            hookFloatingBall(classLoader);
            log(4, TAG, "hookFloatingBall 完成");
            hookHomeWidgets(classLoader);
            log(4, TAG, "hookHomeWidgets 完成");
            // hookHomeDialogComponents 暂时禁用 — S1 hook 导致 NPE 崩溃
            // TODO: 需要分析 S1 返回值类型后再启用
            // hookHomeDialogComponents(classLoader);
            // log(4, TAG, "hookHomeDialogComponents 完成");
            hookAds(classLoader);
            log(4, TAG, "hookAds 完成");
            hookCommonFlags(classLoader);
            log(4, TAG, "hookCommonFlags 完成");
            hookCloudControl(classLoader);
            log(4, TAG, "hookCloudControl 完成");
            hookTeenagerMode(classLoader);
            log(4, TAG, "hookTeenagerMode 完成");
            hookAntiTamper(classLoader);
            log(4, TAG, "hookAntiTamper 完成");
            hookWifiSilentDelete(classLoader);
            log(4, TAG, "hookWifiSilentDelete 完成");
            hookQuickSettingsBypass(classLoader);
            log(4, TAG, "hookQuickSettingsBypass 完成");
            log(4, TAG, "=== hook 初始化完成 ===");
        } catch (Throwable e) {
            log(6, TAG, "Initialization error: " + e.getMessage());
        }
    }

    /**
     * 实现底栏精简功能及状态实时监控
     */
    private void hookBottomNavigation(ClassLoader classLoader) {
        // 核心 Hook A: 模仿青少年模式拦截 Tab 初始化 (Source of Truth)
        try {
            Class<?> extensionsKtCls = classLoader.loadClass("com.wifitutu.widget.maintab.ExtentionsKt");
            Class<?> yCls = classLoader.loadClass("com.wifitutu.widget.maintab.y");
            final Object teenagerEnum = Enum.valueOf((Class<Enum>) yCls, "TEENAGER");

            for (Method m : extensionsKtCls.getDeclaredMethods()) {
                if (m.getName().equals("a") && m.getParameterCount() == 1) {
                    hook(m).intercept(chain -> {
                        Object result = chain.proceed();
                        if (result != null) return result; // 原生已拦截

                        Object iVar = chain.getArgs().get(0);
                        try {
                            Method getIdMethod = iVar.getClass().getMethod("getId");
                            String id = (String) getIdMethod.invoke(iVar);
                            String prefKey = mapTabIdToPrefKey(id);
                            if (prefKey != null && isFeatureEnabled(prefKey, false)) {
                                log(4, TAG, "Simplified bottom bar (Filter): " + id);
                                return teenagerEnum;
                            }
                        } catch (Exception ignored) {}
                        return null;
                    });
                }
            }
        } catch (Exception e) {
            log(6, TAG, "Failed to hook ExtentionsKt.a: " + e.getMessage());
        }

        // 核心 Hook B: 拦截视频/广场/发现等硬编码开关
        try {
            Class<?> movieExtCls = classLoader.loadClass("com.wifitutu.extents.c");
            hook(movieExtCls.getDeclaredMethod("d")).intercept(chain -> {
                if (isFeatureEnabled("hide_tab_video", false)) return false;
                return chain.proceed();
            });
            hook(movieExtCls.getDeclaredMethod("b")).intercept(chain -> {
                if (isFeatureEnabled("hide_tab_nearby", false)) return false;
                return chain.proceed();
            });
        } catch (Exception ignored) {}

        // 核心 Hook C: 拦截 WebTab 开关
        try {
            Class<?> webTabCls = classLoader.loadClass("com.wifitutu.ui.web.d");
            hook(webTabCls.getDeclaredMethod("f")).intercept(chain -> {
                if (isFeatureEnabled("hide_tab_web", false)) return false;
                return chain.proceed();
            });
        } catch (Exception ignored) {}

        // 核心 Hook D: 强制压制 View 可见性 (Catch-all)
        try {
            Class<?> viewCls = classLoader.loadClass("android.view.View");
            Method setVisibilityMethod = viewCls.getMethod("setVisibility", int.class);

            hook(setVisibilityMethod).intercept(chain -> {
                if (isPerformingForcedHide) return chain.proceed();

                View view = (View) chain.getThisObject();
                int id = view.getId();
                if (id == View.NO_ID) return chain.proceed();

                String resName = null;
                try { resName = view.getResources().getResourceEntryName(id); } catch (Exception ignored) {}
                if (resName == null || !resName.startsWith("navigation_")) return chain.proceed();

                int requestedVisibility = (int) chain.getArgs().get(0);
                String prefKey = mapResNameToPrefKey(resName);
                if (prefKey == null) return chain.proceed();

                // 逻辑 A: 如果模块设置了隐藏，且应用尝试显示 -> 拦截并强制设为 GONE
                if (requestedVisibility == View.VISIBLE && isFeatureEnabled(prefKey, false)) {
                    log(4, TAG, "Blocking Visibility on tab: " + resName);
                    chain.getArgs().set(0, View.GONE); // 修改参数为 GONE
                    return chain.proceed();
                }

                // 逻辑 B: 实时监控。如果应用自己设为 GONE -> 自动同步模块开关为 ON (Hidden)
                if (requestedVisibility == View.GONE) {
                    updateFeatureState(prefKey, true);
                }

                return chain.proceed();
            });

            // 生命周期刷新 (二次压制)
            Class<?> mainActivityCls = classLoader.loadClass("com.wifitutu.ui.main.MainActivity");
            Method onResumeMethod = mainActivityCls.getDeclaredMethod("onResume");
            hook(onResumeMethod).intercept(chain -> {
                Object result = chain.proceed();
                mainHandler.postDelayed(() -> refreshBottomTabs(chain.getThisObject()), 1500);
                return result;
            });

        } catch (Exception e) {
            log(6, TAG, "Failed to hook BottomNavigation catch-all: " + e.getMessage());
        }
    }

    private String mapTabIdToPrefKey(String id) {
        if (id == null) return null;
        switch (id) {
            case "connect": return "hide_tab_home";
            case "nearby": return "hide_tab_nearby";
            case "movie": return "hide_tab_video";
            case "welfare": return "hide_tab_welfare";
            case "im": return "hide_tab_im";
            case "guard": return "hide_tab_guard";
            case "mine": return "hide_tab_me";
            // v5.2.18 新增底栏 Tab
            case "deepseek": return "hide_tab_deepseek";
            case "shopmall": return "hide_tab_shopmall";
            case "bus": return "hide_tab_bus";
            case "film": return "hide_tab_film";
            case "ai": return "hide_tab_ai";
            case "kouxin": return "hide_tab_kouxin";
            default: return null;
        }
    }

    private String mapResNameToPrefKey(String resName) {
        switch (resName) {
            case "navigation_home": return "hide_tab_home";
            case "navigation_nearby": return "hide_tab_nearby";
            case "navigation_video": return "hide_tab_video";
            case "navigation_welfare": return "hide_tab_welfare";
            case "navigation_im": return "hide_tab_im";
            case "navigation_web": return "hide_tab_web";
            case "navigation_guard": return "hide_tab_guard";
            case "navigation_me": return "hide_tab_me";
            case "navigation_kouxin": return "hide_tab_kouxin";
            default: return null;
        }
    }

    private void refreshBottomTabs(Object mainActivity) {
        try {
            isPerformingForcedHide = true;
            android.app.Activity activity = (android.app.Activity) mainActivity;
            View root = activity.getWindow().getDecorView();
            String[] tabNames = {
                "navigation_home", "navigation_nearby", "navigation_video", 
                "navigation_welfare", "navigation_im", "navigation_web", 
                "navigation_guard", "navigation_me", "navigation_kouxin"
            };
            
            for (String name : tabNames) {
                int id = activity.getResources().getIdentifier(name, "id", TARGET_PACKAGE);
                if (id != 0) {
                    View v = root.findViewById(id);
                    if (v != null) {
                        String prefKey = mapResNameToPrefKey(name);
                        if (isFeatureEnabled(prefKey, false)) {
                            log(4, TAG, "Forcing GONE on tab: " + name);
                            v.setVisibility(View.GONE);
                        } else if (v.getVisibility() == View.GONE) {
                            updateFeatureState(prefKey, true);
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        } finally {
            isPerformingForcedHide = false;
        }
    }

    private void hookHomeWidgets(ClassLoader classLoader) {
        if (!isAnyToolHideEnabled() && !isFeatureEnabled("remove_ads", false) && !isFeatureEnabled("hide_speed_up", false)) return;
        try {
            Class<?> homeDialogCls = classLoader.loadClass("com.wifitutu.ui.home.HomeDialog");
            Method n0Method = null;
            for (Method m : homeDialogCls.getDeclaredMethods()) {
                if (m.getName().equals("n0") && m.getParameterCount() == 7) {
                    n0Method = m;
                    break;
                }
            }
            if (n0Method != null) {
                hook(n0Method).intercept(chain -> {
                    Object result = chain.proceed();
                    try {
                        Object dialog = chain.getThisObject();
                        Field bindingField = null;
                        for (Field f : dialog.getClass().getDeclaredFields()) {
                            if (f.getType().getName().contains("DialogHomeBinding")) {
                                bindingField = f;
                                break;
                            }
                        }
                        if (bindingField == null) bindingField = dialog.getClass().getDeclaredField("g");
                        bindingField.setAccessible(true);
                        Object binding = bindingField.get(dialog);
                        if (binding != null) {
                            Field rnField = binding.getClass().getField("rnLayout");
                            final android.view.View rnLayout = (android.view.View) rnField.get(binding);
                            // 用 getRoot 获取根 View，统一处理所有隐藏
                            try {
                                Method getRootMethod = binding.getClass().getMethod("getRoot");
                                final android.view.View rootView = (android.view.View) getRootMethod.invoke(binding);
                                if (rootView != null && rootView instanceof android.view.ViewGroup) {
                                    final android.view.ViewGroup rootVg = (android.view.ViewGroup) rootView;
                                    // 多次延迟隐藏
                                    rootVg.postDelayed(() -> hideSpecialViews(rootVg), 500);
                                    rootVg.postDelayed(() -> hideSpecialViews(rootVg), 2000);
                                    rootVg.postDelayed(() -> hideSpecialViews(rootVg), 5000);
                                    // OnGlobalLayoutListener 持续监控
                                    rootVg.getViewTreeObserver().addOnGlobalLayoutListener(
                                        new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
                                            @Override
                                            public void onGlobalLayout() {
                                                hideSpecialViews(rootVg);
                                            }
                                        }
                                    );
                                }
                            } catch (Exception ignored) {}
                        }
                    } catch (Exception e) {
                        log(6, TAG, "Error in tool hide: " + e.getMessage());
                    }
                    return result;
                });
                log(4, TAG, "Hooked HomeDialog.n0() for tool hide (v9)");
            }
        } catch (Exception e) {
            log(6, TAG, "Failed to hook HomeDialog.n0(): " + e.getMessage());
        }
    }

    private void hideToolsInView(android.view.View root) {
        if (!(root instanceof android.view.ViewGroup)) return;
        android.view.ViewGroup vg = (android.view.ViewGroup) root;
        for (int i = 0; i < vg.getChildCount(); i++) {
            android.view.View child = vg.getChildAt(i);
            // 检查 contentDescription
            if (child.getContentDescription() != null) {
                String desc = child.getContentDescription().toString().trim();
                String pk = mapToolDescToPrefKey(desc);
                if (pk != null && isFeatureEnabled(pk, false) && child.getVisibility() != android.view.View.GONE) {
                    child.setVisibility(android.view.View.GONE);
                    log(4, TAG, "Hid tool (desc): " + desc);
                }
            }
            // 检查自身是 TextView
            if (child instanceof android.widget.TextView) {
                String text = ((android.widget.TextView) child).getText().toString().trim();
                String pk = mapToolDescToPrefKey(text);
                if (pk != null && isFeatureEnabled(pk, false) && child.getVisibility() != android.view.View.GONE) {
                    child.setVisibility(android.view.View.GONE);
                    log(4, TAG, "Hid tool (text): " + text);
                }
            }
            // 检查子 View 的 TextView
            if (child instanceof android.view.ViewGroup) {
                android.view.ViewGroup cvg = (android.view.ViewGroup) child;
                boolean found = false;
                for (int j = 0; j < cvg.getChildCount(); j++) {
                    android.view.View sub = cvg.getChildAt(j);
                    if (sub instanceof android.widget.TextView) {
                        String text = ((android.widget.TextView) sub).getText().toString().trim();
                        String pk = mapToolDescToPrefKey(text);
                        if (pk != null && isFeatureEnabled(pk, false) && child.getVisibility() != android.view.View.GONE) {
                            child.setVisibility(android.view.View.GONE);
                            log(4, TAG, "Hid tool (child text): " + text);
                            found = true;
                            break;
                        }
                    }
                }
                // 递归遍历更深层
                if (!found) {
                    hideToolsInView(cvg);
                }
            }
        }
    }

    private void hideViewById(android.view.ViewGroup root, String resName) {
        for (int i = 0; i < root.getChildCount(); i++) {
            android.view.View child = root.getChildAt(i);
            try {
                String entryName = child.getResources().getResourceEntryName(child.getId());
                if (resName.equals(entryName) && child.getVisibility() != android.view.View.GONE) {
                    child.setVisibility(android.view.View.GONE);
                }
            } catch (Exception ignored) {}
            if (child instanceof android.view.ViewGroup) {
                hideViewById((android.view.ViewGroup) child, resName);
            }
        }
    }


    // 隐藏广告横幅、立即加速按钮等特殊 View
    private void hideSpecialViews(android.view.ViewGroup root) {
        // 广告横幅 — 受 remove_ads 控制
        if (isFeatureEnabled("remove_ads", false)) {
            hideViewById(root, "ad_banner");
        }
        // 立即加速 — 受 hide_speed_up 控制
        if (isFeatureEnabled("hide_speed_up", false)) {
            hideViewById(root, "speed_up_layout");
        }
        // 工具栏 — 受各工具开关控制
        if (isAnyToolHideEnabled()) {
            hideToolsInView(root);
            // 隐藏 rnLayout 容器以消除空白
            hideViewById(root, "rnLayout");
        }
    }

    private void hideRnLayoutArea(android.view.ViewGroup root) {
        // 遍历查找 rnLayout 并隐藏
        hideViewById(root, "rnLayout");
    }

    private boolean isAnyToolHideEnabled() {
        String[] keys = {"hide_tool_clean", "hide_tool_speedup", "hide_tool_cooling",
            "hide_tool_speedtest", "hide_tool_network", "hide_tool_security",
            "hide_tool_kuaikan", "hide_tool_novel", "hide_tool_game", "hide_tool_more",
            "hide_tool_pieces", "hide_tool_douyin_coupon", "hide_tool_friend_msg"};
        for (String k : keys) {
            if (isFeatureEnabled(k, false)) return true;
        }
        return false;
    }

    private String findToolName(android.view.View view) {
        if (view.getContentDescription() != null) {
            String desc = view.getContentDescription().toString().trim();
            if (!desc.isEmpty()) return desc;
        }
        if (view instanceof android.widget.TextView) {
            String text = ((android.widget.TextView) view).getText().toString().trim();
            if (!text.isEmpty() && mapToolDescToPrefKey(text) != null) return text;
        }
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup vg = (android.view.ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                android.view.View child = vg.getChildAt(i);
                if (child instanceof android.widget.TextView) {
                    String text = ((android.widget.TextView) child).getText().toString().trim();
                    if (!text.isEmpty() && mapToolDescToPrefKey(text) != null) return text;
                }
            }
        }
        return null;
    }

    private String mapToolDescToPrefKey(String name) {
        if (name == null || name.isEmpty()) return null;
        if (name.equals("垃圾清理")) return "hide_tool_clean";
        if (name.equals("手机加速")) return "hide_tool_speedup";
        if (name.equals("手机降温")) return "hide_tool_cooling";
        if (name.equals("网络测速")) return "hide_tool_speedtest";
        if (name.equals("网络加速")) return "hide_tool_network";
        if (name.equals("安全检测")) return "hide_tool_security";
        if (name.contains("快看")) return "hide_tool_kuaikan";
        if (name.equals("免费小说")) return "hide_tool_novel";
        if (name.equals("游戏中心")) return "hide_tool_game";
        if (name.equals("更多")) return "hide_tool_more";
        if (name.equals("文件碎片")) return "hide_tool_pieces";
        if (name.contains("抖音优惠")) return "hide_tool_douyin_coupon";
        if (name.equals("好友消息")) return "hide_tool_friend_msg";
        return null;
    }

    private void hookHomeDialogComponents(ClassLoader classLoader) {
        try {
            Class<?> homeDialogCls = classLoader.loadClass("com.wifitutu.ui.home.HomeDialog");

            // S1 — IM 消息提醒 (多个重载)
            for (Method m : homeDialogCls.getDeclaredMethods()) {
                if (m.getName().equals("S1") && m.getParameterCount() > 0) {
                    final Method s1m = m;
                    Class<?> retType = m.getReturnType();
                    hook(s1m).intercept(chain -> {
                        if (isFeatureEnabled("hide_tool_im", false)) {
                            return retType == int.class ? 0 : (retType == boolean.class ? Boolean.FALSE : null);
                        }
                        Object r = chain.proceed();
                        return r != null ? r : (retType == int.class ? 0 : (retType == boolean.class ? Boolean.FALSE : null));
                    });
                    log(4, TAG, "Hooked HomeDialog.S1()");
                    break;
                }
            }

            // a1 — VIP 顶部入口
            try {
                hook(homeDialogCls.getDeclaredMethod("a1")).intercept(chain -> {
                    if (isFeatureEnabled("hide_tool_vip", false)) return null;
                    return chain.proceed();
                });
                log(4, TAG, "Hooked HomeDialog.a1() (VIP)");
            } catch (Exception ignored) {}

            // x1 — 用户个人信息布局
            try {
                hook(homeDialogCls.getDeclaredMethod("x1")).intercept(chain -> {
                    if (isFeatureEnabled("hide_tool_user", false)) return null;
                    return chain.proceed();
                });
                log(4, TAG, "Hooked HomeDialog.x1() (User)");
            } catch (Exception ignored) {}

            // I1 — 赋能面板
            try {
                hook(homeDialogCls.getDeclaredMethod("I1", homeDialogCls)).intercept(chain -> {
                    if (isFeatureEnabled("hide_tool_empower", false)) return null;
                    return chain.proceed();
                });
                log(4, TAG, "Hooked HomeDialog.I1() (Empower)");
            } catch (Exception ignored) {}
        } catch (Exception e) {
            log(6, TAG, "Failed to hook HomeDialog components: " + e.getMessage());
        }
    }

    private String mapWidgetIdToPrefKey(String id) {
        if (id == null) return null;
        switch (id) {
            case "12": return "hide_tool_clean";     // 垃圾清理
            case "13": return "hide_tool_speedup";   // 手机加速
            case "14": return "hide_tool_cooling";   // 手机降温
            case "16": return "hide_tool_speedtest"; // 网络测速
            case "17": return "hide_tool_network";   // 网络加速
            case "18": return "hide_tool_security";  // 安全检测
            case "20": return "hide_tool_kuaikan";   // 快看
            case "56": return "hide_tool_novel";     // 免费小说
            case "60": return "hide_tool_game";      // 游戏中心
            case "100": return "hide_tool_more";     // 更多
            default: return null;
        }
    }

    private String mapOldWidgetIdToPrefKey(String id) {
        if (id == null) return null;
        switch (id) {
            case "501": return "hide_tool_clean";     // 垃圾清理
            case "502": return "hide_tool_speedup";   // 手机加速
            case "503": return "hide_tool_cooling";   // 手机降温
            case "505": return "hide_tool_speedtest"; // 网络测速
            case "504": return "hide_tool_pieces";    // 文件碎片
            default: return null;
        }
    }

    private void hookCloudControl(ClassLoader classLoader) {
        try {
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

            try {
                Class<?> adStrategyCls = classLoader.loadClass(AD_STRATEGY_CLASS);
                Method getTaiChiMethod = adStrategyCls.getDeclaredMethod("getTaiChiValue");
                hook(getTaiChiMethod).intercept(chain -> {
                    if (isFeatureEnabled("remove_cloud_control", false)) return "";
                    return chain.proceed();
                });
            } catch (Exception ignored) {}

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
            File filesDir = context.getFilesDir();
            String[] configPaths = {"probe", "config", "strategy", "mmkv"};
            for (String path : configPaths) {
                deleteDir(new File(filesDir, path));
            }
            
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

            // isRunning() → true，让 app 认为青少年模式已开启
            for (Method m : teenagerClass.getDeclaredMethods()) {
                if (m.getName().equals("isRunning") && m.getParameterCount() == 0 && m.getReturnType() == boolean.class) {
                    hook(m).intercept(chain -> {
                        if (isFeatureEnabled("lite_teenager", false)) return true;
                        return chain.proceed();
                    });
                    log(4, TAG, "Hooked teenager.isRunning()");
                    break;
                }
            }

            // isLimited() → false，避免限时锁定
            for (Method m : teenagerClass.getDeclaredMethods()) {
                if (m.getName().equals("isLimited") && m.getParameterCount() == 0 && m.getReturnType() == boolean.class) {
                    hook(m).intercept(chain -> {
                        if (isFeatureEnabled("lite_teenager", false)) return false;
                        return chain.proceed();
                    });
                    log(4, TAG, "Hooked teenager.isLimited()");
                    break;
                }
            }

            // open(pwd) → true，跳过密码验证
            for (Method m : teenagerClass.getDeclaredMethods()) {
                if (m.getName().equals("open") && m.getParameterCount() == 1 && m.getReturnType() == boolean.class) {
                    hook(m).intercept(chain -> {
                        if (isFeatureEnabled("lite_teenager", false)) return true;
                        return chain.proceed();
                    });
                    log(4, TAG, "Hooked teenager.open()");
                    break;
                }
            }

            // Ge(pwd) → true，跳过密码验证（关闭青少年模式）
            for (Method m : teenagerClass.getDeclaredMethods()) {
                if (m.getName().equals("Ge") && m.getParameterCount() == 1 && m.getReturnType() == boolean.class) {
                    hook(m).intercept(chain -> {
                        if (isFeatureEnabled("lite_teenager", false)) return true;
                        return chain.proceed();
                    });
                    log(4, TAG, "Hooked teenager.Ge()");
                    break;
                }
            }

        } catch (Exception e) {
            log(6, TAG, "Failed to hook TeenagerMode: " + e.getMessage());
        }
    }

    /**
     * 绕过 5.2.19 防篡改检测
     * 核心: AppManager.is_cheat_() (native) → sdk.i.Ye() → g0.Ye()
     * 拦截 Ye() 让它永远返回 false，app 就认为未被篡改
     */
    private void hookAntiTamper(ClassLoader classLoader) {
        // Hook sdk.i.Ye() — 这是 isCheat 的 Java 层入口
        try {
            Class<?> sdkICls = classLoader.loadClass("com.wifitutu.link.foundation.sdk.i");
            for (Method m : sdkICls.getDeclaredMethods()) {
                if (m.getName().equals("Ye") && m.getParameterCount() == 0 && m.getReturnType() == boolean.class) {
                    hook(m).intercept(chain -> {
                        log(4, TAG, "Bypassed isCheat (Ye) → false");
                        return false;
                    });
                    log(4, TAG, "Hooked sdk.i.Ye() (anti-tamper bypass)");
                    break;
                }
            }
        } catch (Exception e) {
            log(6, TAG, "Failed to hook sdk.i.Ye(): " + e.getMessage());
        }

        // Hook g0.Ye() — 接口层兜底
        try {
            Class<?> g0Cls = classLoader.loadClass("com.wifitutu.link.foundation.core.g0");
            for (Method m : g0Cls.getDeclaredMethods()) {
                if (m.getName().equals("Ye") && m.getParameterCount() == 0 && m.getReturnType() == boolean.class) {
                    hook(m).intercept(chain -> {
                        log(4, TAG, "Bypassed g0.Ye() → false");
                        return false;
                    });
                    log(4, TAG, "Hooked g0.Ye() (anti-tamper bypass)");
                    break;
                }
            }
        } catch (Exception e) {
            log(6, TAG, "Failed to hook g0.Ye(): " + e.getMessage());
        }

        // Hook AppManager.is_cheat_() — native 层绕过
        try {
            Class<?> appMgrCls = classLoader.loadClass("com.wifitutu.link.foundation.native_.AppManager");
            for (Method m : appMgrCls.getDeclaredMethods()) {
                if (m.getName().equals("is_cheat_") && m.getParameterCount() == 0) {
                    hook(m).intercept(chain -> {
                        log(4, TAG, "Bypassed is_cheat_() → false");
                        return false;
                    });
                    log(4, TAG, "Hooked AppManager.is_cheat_() (native anti-tamper)");
                    break;
                }
            }
        } catch (Exception e) {
            log(6, TAG, "Failed to hook is_cheat_: " + e.getMessage());
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
        if (!isFeatureEnabled("unlock_vip", false)) return;

        // v2 策略: 对所有 VIP 类的方法做泛化拦截 + l5.c/d 精确 hook
        // int→2, boolean→true/false, long→2035年, enum→SVIP/YEAR

        // v3 策略: 仅 hook m5.c/d 精确入口 (不再泛化拦截所有VIP方法，避免NPE崩溃)

        // ★ 关键: m5.c() / m5.d() — VIP/SVIP 最终判断入口 (5.2.19: 旧版 l5→m5)
        try {
            Class<?> m5Cls = classLoader.loadClass("com.wifitutu.link.foundation.core.m5");
            for (Method m : m5Cls.getDeclaredMethods()) {
                String name = m.getName();
                if (name.equals("d") && m.getParameterCount() == 1 && m.getReturnType() == boolean.class) {
                    hook(m).intercept(chain -> {
                        log(4, TAG, "Spoofed m5.d(isVIP) → true");
                        return true;
                    });
                    log(4, TAG, "Hooked m5.d() (isVIP check)");
                } else if (name.equals("c") && m.getParameterCount() == 1 && m.getReturnType() == boolean.class) {
                    hook(m).intercept(chain -> {
                        log(4, TAG, "Spoofed m5.c(isSVIP) → true");
                        return true;
                    });
                    log(4, TAG, "Hooked m5.c() (isSVIP check)");
                }
            }
        } catch (Throwable e) {
            log(6, TAG, "m5 hook failed: " + e.getMessage());
        }

        // BridgeUserVipInfo 构造后篡改字段
        try {
            Class<?> bviCls = classLoader.loadClass("com.wifitutu.link.foundation.native_.model.generate.vip.BridgeUserVipInfo");
            for (Constructor<?> c : bviCls.getDeclaredConstructors()) {
                hook(c).intercept(chain -> {
                    Object result = chain.proceed();
                    if (result != null) {
                        try {
                            for (Field f : result.getClass().getDeclaredFields()) {
                                f.setAccessible(true);
                                String fn = f.getName().toLowerCase();
                                if (fn.equals("svip")) f.set(result, true);
                                if (fn.equals("expired")) f.set(result, false);
                                if (fn.equals("category")) f.set(result, 2);
                                if (fn.equals("autorenew")) f.set(result, true);
                                if (fn.equals("endtime")) f.set(result, 2082729600000L);
                                if (fn.equals("starttime")) f.set(result, 1700000000000L);
                            }
                        } catch (Exception ignored) {}
                    }
                    return result;
                });
            }
            log(4, TAG, "Hooked BridgeUserVipInfo constructors");
        } catch (Throwable ignored) {}

        log(4, TAG, "VIP hook v3 (m5.c/d + BridgeUserVipInfo)");
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

    /**
     * 拦截 WiFi 万能钥匙静默删除网络配置的行为
     * 1. block_wifi_clear_config: 连接前静默清除 App 配置的 WiFi 网络 (Companion.a)
     * 2. block_wifi_delete_model: 连接时先删后加的 useDeleteModel 模式 (x1.f)
     * 3. block_wifi_post_clean: 连接失败后静默清理 WiFi 配置 (x1.n)
     */
    private void hookWifiSilentDelete(ClassLoader classLoader) {
        try {
            Class<?> x1Cls = classLoader.loadClass("com.wifitutu.link.feature.wifi.x1");
            Class<?> companionCls = classLoader.loadClass("com.wifitutu.link.feature.wifi.x1$a");

            // Hook 1: 拦截 Companion.a(clearAppConfiged, ignoreWifiId)
            // 该方法在连接 WiFi 前会静默删除所有 App 自建的网络配置
            try {
                Method clearMethod = null;
                for (Method m : companionCls.getDeclaredMethods()) {
                    if (m.getName().equals("a") && m.getParameterCount() == 2
                            && m.getParameterTypes()[0] == boolean.class) {
                        clearMethod = m;
                        break;
                    }
                }
                if (clearMethod != null) {
                    final Method m = clearMethod;
                    hook(m).intercept(chain -> {
                        if (isFeatureEnabled("block_wifi_clear_config", false)) {
                            log(4, TAG, "Blocked: Companion.a (clearAppConfiged) — 阻止连接前静默清除网络配置");
                            chain.getArgs().set(0, false);
                        }
                        return chain.proceed();
                    });
                    log(4, TAG, "Hooked: x1$a.a(clearAppConfiged)");
                }
            } catch (Exception e) {
                log(6, TAG, "Failed to hook x1$a.a: " + e.getMessage());
            }

            // Hook 2: 拦截 x1.f() 中的 useDeleteModel 行为
            // 该方法在连接 WiFi 时会先删除旧配置再添加新配置
            try {
                Class<?> y2Cls = classLoader.loadClass("jz.x2");
                Class<?> wifiKeyModeCls = classLoader.loadClass("com.wifitutu.link.foundation.kernel.WIFI_KEY_MODE");
                Method fMethod = x1Cls.getDeclaredMethod("f", y2Cls, wifiKeyModeCls);

                hook(fMethod).intercept(chain -> {
                    if (isFeatureEnabled("block_wifi_delete_model", false)) {
                        try {
                            Object x1Instance = chain.getThisObject();
                            Field useDeleteField = x1Cls.getDeclaredField("useDeleteModel");
                            useDeleteField.setAccessible(true);
                            useDeleteField.set(x1Instance, false);
                            log(4, TAG, "Blocked: x1.f() useDeleteModel — 禁止先删后加连接模式");
                        } catch (Exception e) {
                            log(6, TAG, "Failed to disable useDeleteModel: " + e.getMessage());
                        }
                    }
                    return chain.proceed();
                });
                log(4, TAG, "Hooked: x1.f() (useDeleteModel control)");
            } catch (Exception e) {
                log(6, TAG, "Failed to hook x1.f: " + e.getMessage());
            }

            // Hook 3: 拦截 x1.n() — 连接失败后静默清理 WiFi 配置
            try {
                Method nMethod = x1Cls.getDeclaredMethod("n");

                hook(nMethod).intercept(chain -> {
                    if (isFeatureEnabled("block_wifi_post_clean", false)) {
                        log(4, TAG, "Blocked: x1.n() — 阻止连接失败后静默清理网络配置");
                        return null;
                    }
                    return chain.proceed();
                });
                log(4, TAG, "Hooked: x1.n() (post-connect cleanup)");
            } catch (Exception e) {
                log(6, TAG, "Failed to hook x1.n: " + e.getMessage());
            }
        } catch (Exception e) {
            log(6, TAG, "Failed to hook WiFi silent delete: " + e.getMessage());
        }
    }

    /**
     * 绕过 QS 磁贴添加引导弹窗
     * 
     * 拦截流程分析：
     * 1. ConnectHandler.P() / HomeAction 调用 u3.b().Im(proc)
     * 2. f0.Im() 检查 ku() — 如果 QS 磁贴已添加则直接 proc.invoke() 继续连接
     * 3. 若 ku() 返回 false → 弹出 APP_QUICK_SETTINGS_GUIDE_BEFORE_CONNECT 引导弹窗
     * 4. 弹窗回调中检查 q7$b.Gb() — 已添加则继续，否则"连接取消"
     * 
     * Hook 策略：
     * - 主 Hook: com.wifitutu.widget.qs.feature.a.ku() → 始终返回 true (跳过弹窗)
     * - 安全网: com.wifitutu.widget.qs.mgr.a.Gb() → 始终返回 true (兜底弹窗回调)
     */
    private void hookQuickSettingsBypass(ClassLoader classLoader) {
        // 主 Hook: ku() 检查 QS 磁贴是否已添加 → 始终返回 true
        try {
            Class<?> qsFeatureCls = classLoader.loadClass("com.wifitutu.widget.qs.feature.a");
            Method kuMethod = qsFeatureCls.getDeclaredMethod("ku");
            hook(kuMethod).intercept(chain -> {
                if (isFeatureEnabled("bypass_qs_guide", false)) {
                    log(4, TAG, "Bypassed QS tile check (ku) → true");
                    return true;
                }
                return chain.proceed();
            });
            log(4, TAG, "Hooked: qs.feature.a.ku() (QS tile added check)");
        } catch (Exception e) {
            log(6, TAG, "Failed to hook ku(): " + e.getMessage());
        }

        // 安全网: qs.mgr.a.Gb() — 弹窗回调中的二次检查
        try {
            Class<?> qsMgrCls = classLoader.loadClass("com.wifitutu.widget.qs.mgr.a");
            Method gbMethod = qsMgrCls.getDeclaredMethod("Gb");
            hook(gbMethod).intercept(chain -> {
                if (isFeatureEnabled("bypass_qs_guide", false)) {
                    log(4, TAG, "Bypassed QS guide check (Gb) → true");
                    return true;
                }
                return chain.proceed();
            });
            log(4, TAG, "Hooked: qs.mgr.a.Gb() (QS guide dialog callback)");
        } catch (Exception e) {
            log(6, TAG, "Failed to hook Gb(): " + e.getMessage());
        }

        // 悬浮窗权限绕过: Hook permission.c.B0()，对 SYSTEM_ALERT_WINDOW 直接返回 true
        try {
            Class<?> permCheckerCls = classLoader.loadClass("com.wifitutu.link.foundation.kernel.permission.c");
            Method b0Method = permCheckerCls.getMethod("B0",
                    classLoader.loadClass("com.wifitutu.link.foundation.kernel.f6"));
            hook(b0Method).intercept(chain -> {
                if (isFeatureEnabled("bypass_overlay_guide", false)) {
                    try {
                        Object f6Obj = chain.getArgs().get(0);
                        // f6 的 target 字段存着权限 ID (混淆后字段名 "a")
                        Field targetField = f6Obj.getClass().getDeclaredField("a");
                        targetField.setAccessible(true);
                        String target = (String) targetField.get(f6Obj);
                        if ("android:system_alert_window".equals(target)) {
                            log(4, TAG, "Spoofed B0(SYSTEM_ALERT_WINDOW) → true");
                            return true;
                        }
                    } catch (Exception ex) {
                        log(6, TAG, "B0() field access error: " + ex.getMessage());
                    }
                }
                return chain.proceed();
            });
            log(4, TAG, "Hooked: permission.c.B0() (overlay permission bypass)");
        } catch (Exception e) {
            log(6, TAG, "Failed to hook permission.c.B0(): " + e.getMessage());
        }
    }

    /**
     * 屏蔽主界面"签到领现金"广告气泡 BubbleView
     * 
     * com.wifitutu.ui.bubble.BubbleView — 主界面广告浮球
     * showBubbleIfOrNot(int tab) 控制显示，void 方法
     * refreshBubbleView() 内部调 setVisibility(VISIBLE) 显示
     * 
     * 策略: 拦截 showBubbleIfOrNot → 直接 return，不执行刷新逻辑
     */
    private void hookFloatingBall(ClassLoader classLoader) {
        if (!isFeatureEnabled("block_coin_task_ball", false)) return;

        try {
            Class<?> bubbleViewCls = classLoader.loadClass("com.wifitutu.ui.bubble.BubbleView");

            // 拦截 showBubbleIfOrNot — 直接 return，不设置 currentTab 也不调 refreshBubbleView
            for (Method m : bubbleViewCls.getDeclaredMethods()) {
                if (m.getName().equals("showBubbleIfOrNot")) {
                    hook(m).intercept(chain -> {
                        log(4, TAG, "Blocked BubbleView.showBubbleIfOrNot() — 广告气泡已屏蔽");
                        return null; // void 方法，直接返回
                    });
                    log(4, TAG, "Hooked BubbleView.showBubbleIfOrNot()");
                    break;
                }
            }

            // 拦截 fetchData — 阻止广告数据加载，避免网络请求浪费
            for (Method m : bubbleViewCls.getDeclaredMethods()) {
                if (m.getName().equals("fetchData")) {
                    hook(m).intercept(chain -> {
                        log(4, TAG, "Blocked BubbleView.fetchData() — 跳过广告数据加载");
                        return null; // void 方法
                    });
                    log(4, TAG, "Hooked BubbleView.fetchData()");
                    break;
                }
            }

        } catch (Exception e) {
            log(6, TAG, "Failed to hook BubbleView: " + e.getMessage());
        }
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
