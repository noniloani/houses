# Camera and location targeting implementation plan

Goal: Ship a redesigned Android scanner with real glasses frames, on-device address OCR, geographic matching, and a signed APK.
Architecture: Session-owned camera adapter feeds an independent temporal address resolver. HouseHudController publishes one state for the phone and glasses; assessment lookup validates the selected address.
Tech stack: Kotlin, Compose, Meta DAT 1.0.1 camera, bundled ML Kit text recognition.
Spec: ../specs/2026-10-12-vision-location-design.md
Execution: Native in this session; user explicitly requested immediate build and push with no further approval pauses.

## Constraints
Android 12+, no cloud image processing, at most two analyses per second, one in flight, three observations within two seconds, expire visual evidence after five seconds, stale GPS after fifteen seconds, manual hold twenty seconds.

## Review focus
Duplicate numbers, stale responses, camera disconnects, wrong assessment search results, and permission denial must not yield confident incorrect records.

## Tasks
- [x] Add resolver tests for exact/suffix matching, ambiguity, temporal lock/expiry, poor GPS and manual hold; implement TargetResolver.kt.
- [x] Add mwdat-camera and bundled OCR; implement GlassesVision.kt with correct decoded-frame conversion and permission/session lifecycle.
- [x] Extend HouseLocator fixes and controller state; integrate evidence, stale-result guards, manual selection, and strict assessment matching.
- [x] Create LiveScannerScreen.kt and unify navigation/theme/settings; show preview/text tags, status and property cards; simplify glasses HUD.
- [x] Run unit tests and assembleDebug; inspect emulator UI if available; verify APK signature, document device-test limits, commit and push.

## Verification ledger

- Resolver: tests observed failing before implementation; temporal, duplicate number, street direction, stale GPS, manual hold and candidate truncation cases now pass.
- Frame conversion: black/white and red fixtures observed failing before implementation; I420 decode and invalid-size tests pass.
- Assessment identity: correct-address acceptance observed failing before implementation; wrong number/street/town/direction rejection passes.
- Final independent review: four Important findings, no Critical or Minor findings. Fixed informational-error teardown, location-denial recovery, retained camera-permission coordination and street-token boundaries.
- Review regressions: informational error, recreated permission callback/canceled retry, DOMAIN vs MAIN and opposite street directions observed RED then GREEN.
- Full suite: 32 tests, zero failures/errors; assembleDebug succeeds.
- Emulator: APK installs and scanner launches without AndroidRuntime crash. Phone HUD preview runs. Denied location displays an explicit permission-required card; Android settings recovery is available.
- Device-only checks: live glasses frame rotation/stride, wireless recovery timing, OCR accuracy and end-to-end latency remain unverified without physical glasses. No hardware accuracy claim is made.
