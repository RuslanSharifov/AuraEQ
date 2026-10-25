#!/usr/bin/env bash
set -euo pipefail

PACKAGE="com.aistudio.auraeq.pkvmrx"
ACTIVITY="com.example.MainActivity"

echo "Installing built AuraEQ debug APK..."
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb logcat -c
adb shell am force-stop "$PACKAGE" || true
echo "Launching AuraEQ MainActivity..."
adb shell am start -W -n "$PACKAGE/$ACTIVITY"
sleep 8

if ! adb shell pidof "$PACKAGE" >/dev/null; then
    echo "::error::AuraEQ process closed immediately after launch."
    echo "======== CRASH BUFFER ========"
    adb logcat -b crash -d -v threadtime | tail -n 250 || true
    echo "======== FATAL AND PROCESS EVENTS ========"
    adb logcat -d -v threadtime | grep -E -A 35 -B 5 'FATAL EXCEPTION|Process: com.aistudio.auraeq.pkvmrx|Force finishing activity|am_crash|Fatal signal' | head -n 350 || true
    echo "======== ACTIVITY STATE ========"
    adb shell dumpsys activity activities | grep -E -A 5 'com.aistudio.auraeq.pkvmrx|mResumedActivity' | head -n 80 || true
    exit 1
fi

echo "SUCCESS: AuraEQ remained alive after first launch."
