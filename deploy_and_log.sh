#!/bin/bash
# 自动化部署与日志监控脚本

TARGET_PACKAGE="com.snda.wifilocating"
MODULE_PACKAGE="com.ty.wifikeyxposed"

echo "[*] Building module..."
./gradlew :app:assembleDebug

if [ $? -eq 0 ]; then
    echo "[*] Installing module..."
    adb install -r app/build/outputs/apk/debug/app-debug.apk

    echo "[*] Restarting target app: $TARGET_PACKAGE"
    adb shell am force-stop $TARGET_PACKAGE

    # 延迟一下确保进程完全杀死
    sleep 1

    adb shell am start -n $TARGET_PACKAGE/com.wifitutu.ui.main.MainActivity

    echo "[*] Following logs (Ctrl+C to stop)..."
    # 清除旧日志并开始监控
    adb logcat -c
    adb logcat | grep -i "WiFiKeyXposed"
else
    echo "[!] Build failed!"
    exit 1
fi
