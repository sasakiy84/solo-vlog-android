#!/usr/bin/env bash
# Hosted runners do not necessarily include sdkmanager or an Android SDK.
set -euo pipefail
: "${RUNNER_TEMP:?Run this script on a GitHub Actions runner}"
: "${GITHUB_ENV:?GitHub environment file is required}"
: "${GITHUB_PATH:?GitHub path file is required}"
export ANDROID_HOME="$RUNNER_TEMP/solo-android-sdk"
export ANDROID_USER_HOME="$RUNNER_TEMP/solo-android-user"
task_bin="$RUNNER_TEMP/solo-android-bin"
mkdir -p "$task_bin"
curl --fail --silent --show-error --location --proto '=https' --proto-redir '=https' \
  https://dl.google.com/android/cli/latest/linux_x86_64/android -o "$task_bin/android"
chmod +x "$task_bin/android"
"$task_bin/android" --no-metrics sdk install platforms/android-36 build-tools/36.0.0
test -s "$ANDROID_HOME/platforms/android-36/android.jar"
"$ANDROID_HOME/build-tools/36.0.0/aapt2" version
printf 'ANDROID_HOME=%s\nANDROID_USER_HOME=%s\n' "$ANDROID_HOME" "$ANDROID_USER_HOME" >> "$GITHUB_ENV"
printf '%s\n' "$task_bin" "$ANDROID_HOME/build-tools/36.0.0" >> "$GITHUB_PATH"
