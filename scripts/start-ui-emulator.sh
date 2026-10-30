#!/usr/bin/env bash
set -euo pipefail
: "${ANDROID_HOME:?Android SDK is required}"
: "${ANDROID_USER_HOME:?Android user directory is required}"
test -e /dev/kvm
sudo chmod a+rw /dev/kvm
export ANDROID_AVD_HOME="$ANDROID_USER_HOME/avd"
mkdir -p "$ANDROID_AVD_HOME/daylog-ui.avd"
cat > "$ANDROID_AVD_HOME/daylog-ui.ini" <<INI
avd.ini.encoding=UTF-8
path=$ANDROID_AVD_HOME/daylog-ui.avd
target=android-36
INI
cat > "$ANDROID_AVD_HOME/daylog-ui.avd/config.ini" <<INI
avd.ini.encoding=UTF-8
avd.name=daylog-ui
abi.type=x86_64
tag.id=google_apis
image.sysdir.1=$ANDROID_HOME/system-images/android-36/google_apis/x86_64/
hw.cpu.arch=x86_64
hw.cpu.ncore=2
hw.ramSize=3072
hw.lcd.width=1080
hw.lcd.height=2340
hw.lcd.density=420
hw.gpu.enabled=yes
hw.gpu.mode=swiftshader
hw.camera.back=virtualscene
hw.camera.front=emulated
hw.audioInput=yes
hw.audioOutput=yes
hw.keyboard=yes
hw.mainKeys=no
disk.dataPartition.size=4G
showDeviceFrame=no
INI
"$ANDROID_HOME/platform-tools/adb" start-server
nohup "$ANDROID_HOME/emulator/emulator" -avd daylog-ui -no-window -no-audio -no-snapshot -no-boot-anim -accel on -gpu swiftshader -feature -Vulkan -timezone Asia/Tokyo -memory 3072 -cores 2 > "$RUNNER_TEMP/emulator.log" 2>&1 &
deadline=$((SECONDS+300))
until [ "$("$ANDROID_HOME/platform-tools/adb" -s emulator-5554 shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ]; do
  if [ "$SECONDS" -ge "$deadline" ]; then tail -80 "$RUNNER_TEMP/emulator.log"; exit 1; fi
  sleep 2
done
"$ANDROID_HOME/platform-tools/adb" -s emulator-5554 shell input keyevent 82
"$ANDROID_HOME/platform-tools/adb" -s emulator-5554 shell settings put global window_animation_scale 0
"$ANDROID_HOME/platform-tools/adb" -s emulator-5554 shell settings put global transition_animation_scale 0
"$ANDROID_HOME/platform-tools/adb" -s emulator-5554 shell settings put global animator_duration_scale 0
"$ANDROID_HOME/platform-tools/adb" -s emulator-5554 shell settings put secure show_ime_with_hard_keyboard 1
