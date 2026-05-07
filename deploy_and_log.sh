#!/bin/bash
export JAVA_HOME="/mnt/TY/android/android-studio/jbr"
TARGET_PACKAGE="com.snda.wifilocating"
LOG_FILE="lsp_log_$(date +%Y%m%d_%H%M%S).txt"

echo "[*] Setting log buffer size..."
adb logcat -G 50M

echo "[*] Building module..."
./gradlew :app:assembleDebug --no-daemon

if [ $? -eq 0 ]; then
    echo "[*] APK built successfully"

    echo "[*] Installing module..."
    adb install -r app/build/outputs/apk/debug/app-debug.apk

    echo "[*] Stopping target app: $TARGET_PACKAGE"
    adb shell am force-stop $TARGET_PACKAGE
    sleep 1

    echo "[*] Clearing all log buffers..."
    adb logcat -b main -c
    adb logcat -b system -c
    adb logcat -b crash -c
    adb logcat -b events -c

    echo "[*] Starting target app in background..."
    adb shell am start -n $TARGET_PACKAGE/com.wifitutu.ui.launcher.LauncherActivity &

    echo "[*] Waiting 30s for app to initialize and hooks to load..."
            sleep 30

    echo "[*] Capturing LSPosed logs..."
    echo "=== LSPosed Framework Logs ===" | tee "$LOG_FILE"
    adb logcat -d -v threadtime -b main -b system -b crash | grep -i "LSPosedFramework\|WiFiKeyXposed\|MainHook" | tee -a "$LOG_FILE"

    echo "" | tee -a "$LOG_FILE"
    echo "=== Tool Items Filter Logs ===" | tee -a "$LOG_FILE"
    adb logcat -d -v threadtime -b main -b system | grep -E "toolItems|Tool item|Filtering|b\$c|HomeHeadTools|===|Found|Hooked" | tee -a "$LOG_FILE"

    echo "" | tee -a "$LOG_FILE"
    echo "=== Lazy getValue Logs (sample) ===" | tee -a "$LOG_FILE"
    adb logcat -d -v threadtime -b main -b system | grep "Lazy getValue" | head -20 | tee -a "$LOG_FILE"

    echo "" | tee -a "$LOG_FILE"
    echo "[*] Log saved to: $LOG_FILE"
    echo "[*] Done!"
else
    echo "[!] Build failed!"
    exit 1
fi