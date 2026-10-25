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
    adb logcat -d -v time | grep -E -A 45 'FATAL EXCEPTION|AndroidRuntime|com.aistudio.auraeq.pkvmrx' | tail -n 200 || true
    exit 1
fi

echo "SUCCESS: AuraEQ remained alive after first launch."
