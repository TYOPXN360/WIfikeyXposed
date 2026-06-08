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
    private static final String TEENAGER_MANAGER_CLASS = "com.wifitutu.link.foundation.sdk.c1";
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
            hookRestartLogic(classLoader);
            hookMeFragment(classLoader);
            hookPushNotifications(classLoader);
            hookVipStatus(classLoader);
            hookStorage(classLoader);
            hookCommonFlags(classLoader);
            hookAds(classLoader);
            hookTeenagerMode(classLoader);
            hookCloudControl(classLoader);
            hookBottomNavigation(classLoader);
            hookHomeWidgets(classLoader);
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
                "navigation_guard", "navigation_me"
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
        // 核心 Hook 1: 拦截工具助手类的 g() 方法 (总开关)
        try {
            Class<?> toolsHelperCls = classLoader.loadClass("com.wifitutu.ui.rn.b");
            Method gMethod = toolsHelperCls.getDeclaredMethod("g");
            log(4, TAG, "Found toolsHelperCls.g() method");
            hook(gMethod).intercept(chain -> {
                boolean result = isFeatureEnabled("hide_tool_area", false);
                log(4, TAG, "Toolbar switch g() called, hide_tool_area=" + result);
                if (result) return false;
                return chain.proceed();
            });
            log(4, TAG, "Hooked toolbar switch method g()");
        } catch (Exception e) {
            log(6, TAG, "Failed to hook toolsHelperCls.g(): " + e.getMessage());
        }

        // 核心 Hook 2: 拦截工具配置类 b.a() 方法 - 在数据源层面过滤toolItems
        try {
            Class<?> toolsConfigCls = classLoader.loadClass("com.wifitutu.widget.svc.wkconfig.config.api.generate.tools.b");
            Class<?> q0Cls = classLoader.loadClass("com.wifitutu.link.foundation.core.q0");
            Method aMethod = toolsConfigCls.getDeclaredMethod("a", q0Cls);
            log(4, TAG, "Found toolsConfigCls.a() method");
            
            hook(aMethod).intercept(chain -> {
                Object result = chain.proceed();
                if (result != null) {
                    log(4, TAG, "toolsConfigCls.a() returned HomeHeadTools object");
                    try {
                        Method getToolItemsMethod = result.getClass().getMethod("getToolItems");
                        List<?> toolItems = (List<?>) getToolItemsMethod.invoke(result);
                        if (toolItems != null) {
                            log(4, TAG, "Original toolItems size: " + toolItems.size());
                            List<Object> filteredList = new ArrayList<>();
                            for (Object item : toolItems) {
                                Method getIdMethod = item.getClass().getMethod("getId");
                                String id = (String) getIdMethod.invoke(item);
                                Method getNameMethod = item.getClass().getMethod("getName");
                                String name = (String) getNameMethod.invoke(item);
                                
                                String prefKey = mapWidgetIdToPrefKey(id);
                                if (prefKey == null) {
                                    prefKey = mapOldWidgetIdToPrefKey(id);
                                }
                                log(4, TAG, "Tool item from config: id=" + id + ", name=" + name + ", prefKey=" + prefKey);
                                
                                if (prefKey != null && isFeatureEnabled(prefKey, false)) {
                                    log(4, TAG, "Filtering tool from config: " + name + " (" + id + ")");
                                    continue;
                                }
                                filteredList.add(item);
                            }
                            Method setToolItemsMethod = result.getClass().getMethod("setToolItems", List.class);
                            setToolItemsMethod.invoke(result, filteredList);
                            log(4, TAG, "Tool items filtered: " + toolItems.size() + " -> " + filteredList.size());
                        }
                    } catch (Exception e) {
                        log(6, TAG, "Error filtering tool items from config: " + e.getMessage());
                    }
                }
                return result;
            });
            log(4, TAG, "Hooked toolsConfigCls.a() method with filtering");
        } catch (Exception e) {
            log(6, TAG, "Failed to hook toolsConfigCls.a(): " + e.getMessage());
        }

        // 核心 Hook 3: 拦截 ConfigRnModule.Module.find() - JS读取原始配置JSON
        try {
            Class<?> configRnModuleCls = classLoader.loadClass("com.wifitutu.link.foundation.react_native.plugin.ConfigRnModule");
            Class<?>[] declaredClasses = configRnModuleCls.getDeclaredClasses();
            Class<?> moduleCls = null;
            for (Class<?> cls : declaredClasses) {
                if (cls.getSimpleName().equals("Module")) {
                    moduleCls = cls;
                    break;
                }
            }
            if (moduleCls != null) {
                Class<?> readableMapCls = classLoader.loadClass("com.facebook.react.bridge.ReadableMap");
                Method findMethod = moduleCls.getDeclaredMethod("find", readableMapCls);
                log(4, TAG, "Found ConfigRnModule.Module.find() method");
                
                hook(findMethod).intercept(chain -> {
                    Object result = chain.proceed();
                    if (result != null) {
                        try {
                            Object callArg = chain.getArgs().get(0);
                            Method getStringMethod = callArg.getClass().getMethod("getString", String.class);
                            String key = (String) getStringMethod.invoke(callArg, "key");
                            if ("connect_tools_config".equals(key)) {
                                log(4, TAG, "ConfigRnModule.Module.find() called with key=connect_tools_config");
                                // result is WritableMap {"data": raw_json_object}
                                // We already filtered in toolsConfigCls.a(), so no need to double-filter
                                // But if find() is called directly by JS, this catches it too
                            }
                        } catch (Exception ignored) {}
                    }
                    return result;
                });
                log(4, TAG, "Hooked ConfigRnModule.Module.find() method");
            }
        } catch (Exception e) {
            log(6, TAG, "Failed to hook ConfigRnModule.Module.find(): " + e.getMessage());
        }

        // 核心 Hook B: 拦截 HomeDialog 中的其他组件
        try {
            Class<?> homeDialogCls = classLoader.loadClass("com.wifitutu.ui.home.HomeDialog");

            // IM 消息提醒
            hook(homeDialogCls.getDeclaredMethod("S1")).intercept(chain -> {
                if (isFeatureEnabled("hide_tool_im", false)) return null;
                return chain.proceed();
            });

            // VIP 顶部入口
            hook(homeDialogCls.getDeclaredMethod("a1")).intercept(chain -> {
                if (isFeatureEnabled("hide_tool_vip", false)) return null;
                return chain.proceed();
            });

            // 用户个人信息布局
            hook(homeDialogCls.getDeclaredMethod("x1")).intercept(chain -> {
                if (isFeatureEnabled("hide_tool_user", false)) return null;
                return chain.proceed();
            });

            // 赋能面板 (Empower Panel)
            try {
                // I1 是负责计算并显示赋能面板高度的方法，拦截它可以阻止面板显示
                hook(homeDialogCls.getDeclaredMethod("I1", homeDialogCls)).intercept(chain -> {
                    if (isFeatureEnabled("hide_tool_empower", false)) return null;
                    return chain.proceed();
                });
            } catch (Exception ignored) {}
        } catch (Exception e) {
            log(6, TAG, "Failed to hook HomeDialog components: " + e.getMessage());
        }

        // 核心 Hook C: 拦截动态卡片 (Dynamic Card)
        try {
            Class<?> rnDynamicCardCls = classLoader.loadClass("com.wifitutu.ui.view.dynamiccard.RnWifiDynamicCardView");
            hook(rnDynamicCardCls.getDeclaredMethod("isSupportDynamicCard")).intercept(chain -> {
                if (isFeatureEnabled("hide_tool_dynamic_card", false)) return false;
                return chain.proceed();
            });
        } catch (Exception e) {
            log(6, TAG, "Failed to hook RnWifiDynamicCardView: " + e.getMessage());
        }

        // 核心 Hook D: 拦截 Target 30 提示
        try {
            Class<?> x5Cls = classLoader.loadClass("com.wifitutu.link.foundation.core.x5");
            Class<?> n1Cls = classLoader.loadClass("com.wifitutu.link.foundation.sdk.n1");
            // D 方法是负责渲染这些小组件的核心入口
            // 尝试不同的方法签名
            boolean hooked = false;
            for (Method m : x5Cls.getDeclaredMethods()) {
                if (m.getName().equals("D")) {
                    Class<?>[] params = m.getParameterTypes();
                    if (params.length >= 1 && params[0] == n1Cls) {
                        hook(m).intercept(chain -> {
                            Object n1Obj = chain.getArgs().get(0);
                            if (n1Obj != null) {
                                try {
                                    Method getIdMethod = n1Obj.getClass().getMethod("getId");
                                    String id = (String) getIdMethod.invoke(n1Obj);
                                    if (id != null && id.contains("target30") && isFeatureEnabled("hide_tool_target30", false)) {
                                        log(4, TAG, "Blocking Target30 widget: " + id);
                                        return null;
                                    }
                                } catch (Exception ignored) {}
                            }
                            return chain.proceed();
                        });
                        hooked = true;
                        log(4, TAG, "Successfully hooked Target30 method D");
                        break;
                    }
                }
            }
            if (!hooked) {
                log(6, TAG, "Failed to find Target30 method D with correct signature");
            }
        } catch (Exception e) {
            log(6, TAG, "Failed to hook Target30 widgets: " + e.getMessage());
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
