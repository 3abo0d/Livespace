#!/data/data/com.termux/files/usr/bin/sh
# Rooted Termux. Builds capsule.dex from the 16.1 SystemUI + THIS device's SystemUI and installs it for the module.
# usage: sh scripts/build_capsule_dex.sh [/sdcard/Download/SystemUI_16.1.apk]
set -e
NEW="${1:-/sdcard/Download/SystemUI_16.1.apk}"
REPO="$(cd "$(dirname "$0")/.." && pwd)"
cd "$HOME"
pkg install -y openjdk-17 python unzip curl
[ -f "$HOME/apktool.jar" ] || curl -L -o "$HOME/apktool.jar" https://github.com/iBotPeaches/Apktool/releases/download/v2.12.1/apktool_2.12.1.jar
su -c "cp /system_ext/priv-app/SystemUI/SystemUI.apk $HOME/device_systemui.apk && chown $(id -u):$(id -g) $HOME/device_systemui.apk"
[ -r "$NEW" ] || { echo "cannot read $NEW (run termux-setup-storage or copy it into ~)"; exit 1; }
python3 "$REPO/tools/make_capsule_dex.py" --new "$NEW" --old "$HOME/device_systemui.apk" --apktool "$HOME/apktool.jar" --work "$HOME/ls_work" --out "$HOME/capsule.dex"
su -c "mkdir -p /data/local/tmp/livespace && rm -f /data/local/tmp/livespace/*.dex && cp $HOME/capsule.dex /data/local/tmp/livespace/capsule.dex && chmod 755 /data/local/tmp/livespace && chmod 444 /data/local/tmp/livespace/capsule.dex && chcon -R u:object_r:system_file:s0 /data/local/tmp/livespace && ls -lZ /data/local/tmp/livespace"
echo "Done. Restart SystemUI:  su -c 'killall com.android.systemui'   then check LSPosed log for [Livespace]"
