#!/bin/bash
export JAVA_HOME="/mnt/TY/android/android-studio/jbr"
TARGET_PACKAGE="com.snda.wifilocating"

echo "[*] Building module..."
./gradlew :app:assembleDebug --no-daemon

if [ $? -eq 0 ]; then
    echo "[*] APK built successfully"

    echo "[*] Installing module..."
    adb install -r app/build/outputs/apk/debug/app-debug.apk

    echo "[*] Stopping target app: $TARGET_PACKAGE"
    adb shell am force-stop $TARGET_PACKAGE
    sleep 1

    echo "[*] Clearing logs..."
    adb logcat -c

    echo "[*] Starting target app: $TARGET_PACKAGE"
    adb shell am start -n $TARGET_PACKAGE/com.wifitutu.ui.launcher.LauncherActivity

    echo "[*] Waiting 10s and capturing LSPosed logs..."
    sleep 10

    echo "[*] Capturing LSPosed logs..."
    adb logcat -d | grep -i "WiFiKeyXposed" | head -50

    echo "[*] Capturing screenshot..."
    adb exec-out screencap -p > /tmp/wifikey_screenshot.png 2>/dev/null

    echo "[*] Done!"
else
    echo "[!] Build failed!"
    exit 1
fi