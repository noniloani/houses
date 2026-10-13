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
- [ ] Add resolver tests for exact/suffix matching, ambiguity, temporal lock/expiry, poor GPS and manual hold; implement TargetResolver.kt.
- [ ] Add mwdat-camera and bundled OCR; implement GlassesVision.kt with correct decoded-frame conversion and permission/session lifecycle.
- [ ] Extend HouseLocator fixes and controller state; integrate evidence, stale-result guards, manual selection, and strict assessment matching.
- [ ] Create LiveScannerScreen.kt and unify navigation/theme/settings; show preview/text tags, status and property cards; simplify glasses HUD.
- [ ] Run unit tests and assembleDebug; inspect emulator UI if available; verify APK signature, document device-test limits, commit and push.
