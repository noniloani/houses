# Glasses reliability and strata units

User request: fix unstable connection and missing glasses HUD, and provide building/unit selection in the glasses. Execution: implement and deliver directly, preserving the user's prior build/push instructions.

## Evidence
- MainActivity previously stopped the SDK session on every onStop, including phone lock/background.
- Display STOPPED and send failures did not restore the attached capability or resend the card.
- Concurrent HUD sends could race; the controller deduplicated failed content and never replayed it.
- Nearby geocoder requests included strata units, crowding neighboring buildings out of the result limit.
- BC Assessment search returns a building gid; its public search script uses GetSubUnits/{gid}?multiunitId={gid}. The old app discarded gid.

## Implementation
- [x] Foreground connected-device/location service and notification Stop action keep scanning active while pocketed.
- [x] Latest-value serialized transport retains and replays the latest HUD after reattachment; reconnect uses bounded backoff and a startup watchdog.
- [x] Reject stale SDK callbacks and stop recovery after explicit stop. Restore camera once per attached session, with a 2 FPS feed to reduce transport load.
- [x] Explicitly leave mock preview before real scanning; show readiness only for a connected display-capable device.
- [x] Request building-level nearby addresses with excludeUnits=true and onlyCivic=true.
- [x] Preserve group ID; fetch and validate units; paged glasses-only unit/building menus, selected-unit lookup, and strict unit identity checks.
- [x] Freeze automatic targeting while browsing units; label the building held and selected unit manual.
- [x] Unit tests observed RED before implementation for transport replay, serialization, failed-send retry, unit metadata/identity/pagination, and building-only query.

## Verification so far
39 JVM tests; assembleDebug succeeds. Emulator APK installs. A live 1000 Beach Avenue lookup yielded 115 units; selecting Unit 111 showed its $970,000 assessment in the SDK glasses preview. ScanService remained foreground after Home/background, and the same unit card was present on return. Physical glasses transport, camera accuracy, and display rendering still require device testing.

## Sources
- Meta foreground service sample: https://github.com/facebook/meta-wearables-dat-android/blob/main/samples/CameraAccess/app/src/main/java/com/meta/wearable/dat/externalsampleapps/cameraaccess/stream/StreamingService.kt
- BC Assessment unit selector script: https://www.bcassessment.ca/Scripts/App/searchjs
- BC Geocoder API guide: https://github.com/bcgov/api-specs/blob/master/geocoder/geocoder-developer-guide.md
