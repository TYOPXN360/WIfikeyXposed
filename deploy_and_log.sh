#!/bin/bash

# 配置变量
TARGET_APP_PACKAGE="com.snda.wifilocating"
MODULE_APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
JAVA_HOME_PATH="/media/tyopxn360/Android/MC/Java/Java21"

# 设置环境
export JAVA_HOME=$JAVA_HOME_PATH
export PATH=$JAVA_HOME/bin:$PATH

echo "--- 1. 开始构建模块 ---"
./gradlew assembleDebug
if [ $? -ne 0 ]; then
    echo "构建失败，请检查代码！"
    exit 1
fi

echo "--- 2. 安装模块到手机 ---"
adb install -r $MODULE_APK_PATH
if [ $? -ne 0 ]; then
    echo "安装失败，请检查 ADB 连接！"
    exit 1
fi

echo "--- 3. 强制重启目标应用 ---"
adb shell am force-stop $TARGET_APP_PACKAGE
sleep 1
# 启动目标应用
adb shell monkey -p $TARGET_APP_PACKAGE -c android.intent.category.LAUNCHER 1

echo "--- 4. 等待应用初始化并尝试跳转到“我的”页面 ---"
sleep 5
# 模拟点击右下角的“我的”标签 (根据屏幕尺寸估算，通常在右下角)
# 在 1008x2244 屏幕上，“我的”按钮中心大约在 [880, 2150]
adb shell input tap 880 2150
sleep 2

echo "--- 5. 获取 LSPosed 日志 ---"
adb shell "su -c '/data/adb/lspd/bin/cli log'" || adb logcat -d | grep -i "WiFiKeyXposed"

echo "--- 6. 自动截图验证 (存放在 project_files/verify.png) ---"
adb shell screencap -p /sdcard/verify.png
adb pull /sdcard/verify.png ./verify.png

echo "--- 自动化流程完成 ---"
