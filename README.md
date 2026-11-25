# Livespace
LSPosed module: ColorOS 16.1 lockscreen bottom capsule (Live Space) on ColorOS 16.0.x, anchored above the under-display fingerprint scanner. Status: early scaffolding, see docs/ANALYSIS.md.

Termux (rooted), after installing the module APK from GitHub Actions:
    pkg install -y git && git clone https://github.com/3abo0d/Livespace && cd Livespace   # or git pull
    termux-setup-storage        # once, so /sdcard/Download is readable
    sh scripts/build_capsule_dex.sh /sdcard/Download/SystemUI_16.1.apk
    su -c 'killall com.android.systemui'
The script builds `capsule.dex` on the phone from the 16.1 SystemUI and your own device SystemUI (nothing proprietary is stored in this repo).

Safety: if SystemUI restarts 3 times in a row after the capsule attaches, the module turns itself off.
Re-enable: `su -c 'rm /data/user/0/com.android.systemui/files/livespace_attempts'`. Manual kill switch: `su -c 'touch /data/local/tmp/livespace/disable'`.
