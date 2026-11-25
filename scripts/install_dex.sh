#!/data/data/com.termux/files/usr/bin/sh
# Run in Termux (rooted). Extracts the 16.1 capsule dex files and makes them readable by SystemUI.
APK="${1:-/sdcard/Download/SystemUI_16.1.apk}"
TMP="$HOME/ls_dex"; DST=/data/local/tmp/livespace
mkdir -p "$TMP" && unzip -o "$APK" classes3.dex classes8.dex -d "$TMP" || { echo "unzip failed (pkg install unzip)"; exit 1; }
su -c "mkdir -p $DST && cp $TMP/classes3.dex $TMP/classes8.dex $DST/ && chmod 755 $DST && chmod 444 $DST/*.dex && chcon -R u:object_r:system_file:s0 $DST && ls -lZ $DST"
echo "Now enable the Livespace module for System UI in LSPosed, then: su -c 'killall com.android.systemui'"
echo "Log: LSPosed manager > Logs > filter [Livespace]"
