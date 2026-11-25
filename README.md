# Livespace
LSPosed module: ColorOS 16.1 lockscreen bottom capsule (Live Space) on ColorOS 16.0.x, anchored above the under-display fingerprint scanner. Status: early scaffolding, see docs/ANALYSIS.md.

Termux (rooted):
    pkg install git unzip
    git pull
    sh scripts/install_dex.sh /sdcard/Download/SystemUI_16.1.apk
The APK is built by GitHub Actions (Actions > latest run > Artifacts > Livespace-debug).
