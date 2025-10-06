#!/bin/bash

SDK_URL="https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip"
ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"

mkdir -p "$ANDROID_HOME/cmdline-tools"

curl -sL "$SDK_URL" -o /tmp/cmdline-tools.zip
unzip -q /tmp/cmdline-tools.zip -d /tmp/
mv /tmp/cmdline-tools "$ANDROID_HOME/cmdline-tools/latest"
rm /tmp/cmdline-tools.zip

yes | "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --licenses > /dev/null 2>&1
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" "platform-tools" "platforms;android-36" "build-tools;36.1.0" > /dev/null 2>&1

export ANDROID_HOME="$ANDROID_HOME"
