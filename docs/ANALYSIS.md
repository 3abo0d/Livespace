# 16.0.5 vs 16.1 SystemUI diff (capsule / Live Space)

* 65,797 classes (16.0.5) vs 67,693 (16.1): +2,778 / -882.
* `com.oplus.systemui.notification.lockscreen.*` (713 classes, in 16.1 classes3.dex + classes8.dex) does **not exist** in 16.0.5. It contains the stacked capsule: `NotificationCapsuleContainer`, `CapsuleItemView`, animators, metaball/ear blending, stroke effects, data handlers, `CapsuleMediaPlayerDataHelper`, `LockScreenSeedingInteractor`, `CapsuleSeedingData`.
* 16.0.5 has an older, simpler capsule: `com.oplus.systemui.keyguard.notificationcapsule.KeyguardNotificationCapsuleView` + `...ViewControllerImpl`/`Interactor`/`ViewModel`, used by `OplusKeyguardBottomAreaViewBinder` (dimens `keyguard_notification_capsule_view_*_margin_to_fp_top`).
* `KeyguardNotificationCapsuleViewControllerEx` / `OplusKeyguardDependencyEx.createKeyguardNotificationCapsuleViewController` are no-op stubs in both builds, so they are not the integration point.
* 16.1 gets the container via Dagger `providerKeyguardNotificationCapsuleView(NotificationPanelView)` = `findViewById` in the panel layout, so the container is a view declared in the NotificationPanelView layout.
* Only ~40 classes referenced by the capsule package are missing from 16.0.5 outside the package: `qs.base.olivelink.{Spring,Scalar,SpringType,CubicBezier}`, `com.oplus.posteffect.{CornerParams,CornerType,ClipShader,drawable.MetaBallBlurDrawable}`, `OplusExpandableViewProxy`, `OplusPhysicsProperty*`, `ViewBlurManager$MaterialColorChangeCallback`, etc. ~227 other dependencies exist in both.
* Live alerts new in 16.1: `statusbar.seeding.livealert.{LitePluginLoadManager,LiteUmsLoader,SeedlingServiceObserver}`, `seedling.livealert.LiveAlertPendingIntentService`, `media.seedling.livealert.{MediaLiveAlertController,MediaLiveAlertProvider}`.
* Media: part of the capsule (`CapsuleMediaPlayerDataHelper`), plus new `OplusMediaPlayerManager` and QS media panel classes.
* 16.1 capsule resources: ~74 names vs ~36 in 16.0.5. Resource ids are hard-coded in the 16.1 dex, so they will not match 16.0.5's resources.arsc: a resource APK (addAssetPath) or id remapping is required.

## Milestone 0.1 (this commit)
Loader + attach + UDFPS anchor + diagnostics. The constructor of the 16.1 container will probably fail or show wrong resources until the resource problem is solved; the log shows how far it gets.

## Next
1. Read the module log, see which step fails.
2. Ship a resource overlay for the 16.1 capsule resources and attach via `Resources.addAssetPath`.
3. Build `CapsuleNotificationDataController`/`LockScreenSeedingInteractor` by hand (replacing Dagger), feed from metis/seedling.
4. Hide the legacy 16.0.5 capsule view.
5. Live alerts + media.
