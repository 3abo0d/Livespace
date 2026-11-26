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

## Milestone 0.2: dex patcher (tools/make_capsule_dex.py)
First on-device log: the 16.1 dex loads and the container constructor runs, then fails with NoSuchMethodError AnimatableProperty.from(...) (16.0.x stripped that static helper; its inner classes are identical).
A scan of the whole capsule closure (935 classes) found 187 member references to shared classes that do not exist on 16.0.5. The patcher:
* redirects `AnimatableProperty.from` to `com.livespace.shim.Adapters.animatableFrom` (builds `$5`/`$6` through their constructors),
* rewrites the new `COUISpringAnimation(Object, FloatPropertyCompat, F)` constructor to the old `(FloatPropertyCompat, Object)` one,
* copies the 8 R8 `ExternalSyntheticOutline` helper classes that differ between builds to `ls/o/...`,
* redirects every other missing method/static field to default no-op stubs (`ls/Stubs`), so a missing API can never throw NoSuchMethodError inside SystemUI,
* leaves 3 references that cannot be redirected: `LockscreenNotificationCenterAnimBreakerExImpl.<init>` (lockscreen shade transition, not on the capsule path), and two `invoke-super` calls to a default method of `TransitionAnimator$Controller` (only hit when launching an app from a capsule).
Stubs return 0/false/null. Behaviour that depended on those APIs (immersive music wallpaper, AOD transitions, drag springs) is inert for now.

## Milestone 0.3: resources
Log after 0.2: capsule.dex loads, then `Resources$NotFoundException 0x7f0702b5` (16.1 resource ids are hard-coded in the dex and do not exist in the 16.0.x resource table).
Fix: `tools/make_capsule_res.py` copies the 16.1 `resources.arsc` + `res/**` into `capsule_res.apk` with the package id relocated 0x7f -> 0x7e (arsc entries, bag parents/keys and every binary XML attribute/reference/resource-map are rewritten), and `make_capsule_dex.py` rewrites the matching resource-id constants in the dex. The module adds `capsule_res.apk` to SystemUI's AssetManager before building the container, so the capsule sees its real 16.1 dimens/layouts/drawables while SystemUI keeps its own resources.

## Milestone 0.4: enums + access
Log after 0.3: resources load (`resources added`), then `NoSuchFieldError ViewBlurManager$CardType.CAPSULE`.
Cause: the scanner trusted the framework `Enum` parent to supply any field, so missing enum constants were never flagged. Fixed. Missing enum constants now map to an existing constant of the same enum (CardType.CAPSULE -> NOTIFICATION, ClockSize.IMMERSED -> LARGE) instead of null.
New check: capsule.dex is a different classloader than SystemUI, so private/package-private SystemUI members are unreachable (IllegalAccessError). 7 such accesses in 4 members (OplusCustomRow.getContentLayout, TransformState.setTransformationStart{ScaleX,ScaleY}, NotificationStatisticUtil.getNotificationType) are now stubbed. `invoke-super` calls to missing interface defaults are stubbed too.
Only reference left: `LockscreenNotificationCenterAnimBreakerExImpl.<init>` (shade transition controller, not on the capsule path).

## Milestone 0.5: the hierarchy-walk bug
Log after 0.4: `NoSuchMethodError ViewBlurManager.getCurrentMaterialColor()`. The member check treated an already-visited ancestor (e.g. `Object` reached again through an interface) as "found", so every missing member on a class with interfaces was invisible. Fixed; the real list is 54 missing members / 141 call sites (earlier "stubbed 189" undercounted).
Largest group: the Oplus expandable-view extension interfaces `OplusExpandableViewEx`/`StateEx` (+Impl, Companion, lambdas): capsule progress, clip width/height, corner radius, blur radio. They are now relocated (16.1 copies under `ls/o/`) like the helper classes, instead of stubbed. Everything else missing (stack animator requests, immersive clock/wallpaper, ViewTransformationHelper custom transformation) is stubbed; `ViewBlurManager.getCurrentMaterialColor` returns a fixed translucent dark colour (0xb8282828) because 16.0.x has no material-colour concept.
Unknown parents/interfaces that live in a SystemUI library (not in the APK) are only trusted for `on*`/`dump` callbacks.
Left (cannot be redirected): `LockscreenNotificationCenterAnimBreakerExImpl.<init>`, `ExpandableViewState.mExpandableViewStateEx` (type changed), `KeyguardStyleClockControllerImpl.isEngineImmersive` (instance field). All are off the capsule construction path.

## Milestone 0.6: load-time link errors
Log after 0.5: `LinkageError: OplusAnimationFilter.combineFilter overrides final method` (16.1 made `AnimationFilter.combineFilter` non-final).
New pre-checks: final-method overrides (renamed with a `$ls` suffix so they stop overriding: combineFilter, cancelAnimations, copy, onTransitionAnimationEnd, setBounds), classes extending a class that is final on the device (the 16.1 copy of `BlurDrawable` is relocated and used as the super), static-vs-instance and interface-vs-class call mismatches (stubbed).
Module: if the real container cannot be built, a visible placeholder pill is attached above the fingerprint area so placement can be checked; the log reports whether a fingerprint view was found.

## Milestone 0.7: crash safety
Field report after 0.6: the container attached cleanly, but opening the notification shade crashed SystemUI repeatedly (the module was switched off by the user).
Added: `SafeHost` wraps the container and guards measure/layout/draw/touch/configuration, `CapsuleGuard` swallows capsule-originated exceptions that surface in `Handler.dispatchMessage` and switches the capsule off with the full stack trace in the LSPosed log, the crash-loop guard now trips after 3 SystemUI starts within 15 minutes (the old 60 s reset let a crash a few minutes after attach slip through), and the container is inactive whenever the keyguard is not showing.
The module sources are now compile-checked with javac against android-33 before every push.

## Milestone 0.8: touch handler NPE
Field report: opening the shade threw `NullPointerException ... NotificationCapsuleContainerTouchHandler.onInterceptTouchEvent` from `NotificationCapsuleContainer.onInterceptTouchEvent`.
Cause (verified in the smali): `NotificationCapsuleContainer.scroller` is only assigned by `NotificationCapsuleContainerControllerImpl` (Dagger-built in 16.1; it also sets capsuleViewModel, containerControllerImpl, lightOSController, stackY listener). Nothing creates that controller here, so the field stays null.
Interim fix (tools/make_capsule_dex.py `guard_scroller`): the three container methods that use the handler (`onInterceptTouchEvent`, `onTouchEvent`, `checkIfNeedCancelHScroll`) return early when it is null. Two more callers (`CapsuleDataPinHandler.autoScrollToNext`, `CapsuleViewExpandKtKt`) only run once capsules exist.
Real fix (next): construct `NotificationCapsuleContainerControllerImpl` ourselves (needs CapsuleViewModel, the Lazy providers, OplusNotificationCapsuleInteractorImpl), which also gives touch handling, data binding and stack coupling.

## Milestone 0.9: demo capsule
Log after 0.8: clean start, `capsule container attached (guarded)`, fingerprint view found (`com.android.keyguard.OplusLockIconView`).
`DemoCapsule` builds a real `CapsuleItemView` via `CapsuleNotificationData(key, CapsuleNotificationContainerView, color, animAdd)` + `attachCapsuleEntry` + `addContainerView`, with a "Live Space" label, so the capsule is visible before live data exists. Disable with `/data/local/tmp/livespace/nodemo`.
