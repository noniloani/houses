# BC House HUD: camera and location targeting

Date: 2026-10-12
Status: Implemented for Android field testing; user requested immediate build and push

## Outcome

Improve the Android app's appearance and reduce incorrect property selection while the wearer looks at houses in British Columbia. Use live frames from the glasses together with geographic address candidates. Deliver an updated signed debug APK for Android 12+ and clear instructions for testing on actual glasses.

The user approved the camera/location direction and explicitly requested both signals. On-device processing is the default assumption because no cloud-processing preference was supplied. No external vision account or backend is required for this version.

## Current problem

HouseLocator uses phone GPS and the phone rotation sensor. HouseHudController chooses the first address inside a broad 35-degree heading cone, or the nearest address without a heading. It polls every three seconds and rescans after a 12-metre move or 25-degree phone turn. This cannot observe head turns while the phone stays in a pocket. It also presents an estimated address without describing the evidence behind it.

BC Assessment search currently permits a locality fallback and takes the first result. That can select the wrong record even after the correct civic address is chosen. Camera targeting and assessment-record matching must both preserve address identity.

## Approach and limits

Use geography to find plausible BC civic addresses and on-device text recognition to match visible house numbers and street signs. Retain the existing location estimate as an explicitly labelled fallback. A cloud scene model could interpret broader visual context but adds latency, operating costs, and a backend; facade matching would require a reference-image dataset. Neither is part of this build.

The glasses camera indicates head-facing view, not eye gaze. A readable number near the middle of the frame is evidence, not proof of the associated facade. The app must not invent hidden numbers or claim exact identification from GPS alone. When several candidates are plausible, offer manual selection.

The glasses having a map does not establish an accessible, precise GPS feed. Use Android phone location with its measured accuracy and timestamp. Verify the installed Meta toolkit's supported interfaces during implementation; only use an additional glasses signal if its public API and coordinate semantics are established. Glasses GPS and absolute glasses heading are not dependencies of this version.

## Components and data flow

1. **Glasses camera adapter:** owned by the existing device-session layer. Check/request Meta camera permission through its documented permission flow. Attach the camera only after the parent session starts. Stream decoded frames; copy owned image data before asynchronous analysis. Validate pixel format, dimensions, and rotation against the installed toolkit and official sample. Do not interpret compressed data as pixels.
2. **Frame analyzer:** bundled ML Kit Latin text recognition running on the phone. Produce timestamped text observations and normalized bounding boxes. Recognize complete civic-number tokens, including suffixes; exclude prices, dates, phone numbers and street ordinals from standalone house-number evidence. Street-name matches may disambiguate candidates but never create an address outside the geocoder results.
3. **Location candidate provider:** extend existing location fixes with age and accuracy. Refresh nearby addresses after movement and on a bounded retry interval. Use an accuracy-aware radius capped at 150 metres, retrieve up to 50 candidates, and preserve the most recent successful candidate list only while its location remains fresh. Poor location or a truncated candidate list must not become evidence of uniqueness.
4. **Target resolver:** a platform-independent component combining recent text observations and address candidates. Exact normalized civic-number matches have priority over proximity. Central observations receive preference; competing numbers or duplicate-number candidates remain uncertain unless street evidence distinguishes them. Phone heading is a weak hint and cannot establish a visual match.
5. **House HUD controller:** consume resolver state, obtain the chosen record, and publish consistent state to both phone and glasses. Maintain selection generations so late geocoder/assessment responses cannot overwrite a newer target. Share one current state rather than separate phone and glasses decisions.

Data flow: phone location → nearby civic addresses; glasses frames → text observations; both → target resolver → verified assessment-address match → property card.

## Temporal selection rules

- Analyze at most two frames per second with one analysis in flight; drop superseded frames instead of creating a queue.
- Show a provisional visual candidate immediately after a usable observation.
- Promote to **Visual match** after the same unique candidate appears in at least three analyzed frames within two seconds. This label describes repeated address evidence, not a calibrated probability.
- After two seconds without matching evidence, change the label to **Last visual match**. After five seconds, remove automatic visual selection and return to searching/estimated state. Expired frames cannot reacquire a target.
- A conflicting candidate removes the active visual-match label immediately; a new target must satisfy the same consistency rule.
- Manual selection remains available for 20 seconds, labelled **Manually selected**; automatic vision does not silently override it. Rescan clears the hold and accumulated evidence.
- Location fixes older than 15 seconds or worse than 50 metres accuracy show **Location weak** and cannot independently acquire an automatic visual match. Display the actual reported accuracy rather than an exact-position claim.
- A stopped, paused, disconnected, or failed camera immediately ceases to support an active visual-match label.

These starting thresholds are tunable constants. Actual response time must be measured on the user's glasses; there is no promised end-to-end instantaneous identification.

## Assessment accuracy

Match search results against the selected civic number, normalized street name/type/direction, and locality. Treat incompatible or ambiguous results as unavailable/ambiguous instead of taking the first result. Validate the returned record's address where available. Keep multi-unit buildings explicit; recognizing a building number does not identify an individual strata unit.

Cache records only for addresses actually selected or viewed, with a bounded cache. Do not bulk-fetch all nearby assessments. Separate network failures from genuine missing records so retry remains possible. Show assessed value as assessed value, never as a current market price or sale listing.

## Phone and glasses design

Replace the sample-oriented landing page with a focused **BC House HUD** scanning experience. Use a deep navy background, warm white text, restrained mint accents, readable type, generous spacing, and large controls. Keep connection/settings accessible without forcing the user through sample menus.

The phone screen contains:

- Header with app name and glasses connection state.
- Live camera preview when available, with a center guide and boxes around recognized address text. These are text tags, not fabricated house segmentation.
- Visible camera, location accuracy, and identification states.
- Selected address and assessed value, followed by land/building split and key property details.
- Start/stop scanning, rescan, and nearby-address selection controls.

The glasses show a compact black-background card suitable for the additive display: identification status, address, prominent assessed value, a short row of key details, and existing navigation controls. Preserve detailed fields/trend where they fit without crowding the main card. Keep image dimensions within the toolkit's existing constraints. A frame-center guide is a targeting aid; the SDK card is not a world-anchored AR overlay.

Use clear states: **Searching**, **Reading number**, **Visual match**, **Last visual match**, **Nearby estimate**, **Uncertain**, **Manually selected**, **Camera unavailable**, **Location weak**, and **Assessment unavailable**. Do not show a decorative numeric confidence percentage.

## Permissions, lifecycle, and privacy

Camera access starts only when the user starts scanning. On-device image processing keeps transient frames in memory; no frame recording, uploads, or image logging. Geographic address queries and assessment retrieval still require internet access.

Handle denied camera permission, revoked location permission, unsupported devices, camera pause, and session errors with actionable UI. Location-only mode remains usable and labelled estimated. Stop analysis, release recognition resources, and detach the camera on scan stop/session shutdown. Stop camera processing when the app is backgrounded in this first version; require deliberate restart on return. Avoid repeated permission dialogs on every app resume.

## Verification and deliverables

Automated tests cover exact number/suffix matching, duplicate civic numbers on different streets, central versus competing observations, stale/poor location, evidence expiry, camera interruption, manual hold, and assessment-address mismatch. Retain existing parser tests. Test selection-generation guards so older responses cannot restore a superseded address.

Build the APK and verify its signature and Android minimum version. Inspect the redesigned UI in an emulator if one can run locally. Preview mode must disclose when no real glasses camera is attached; mock or location-only results cannot count as live-glasses verification.

Provide a signed debug APK, concise install instructions, and a field-test checklist: adjacent houses, phone facing away, unreadable number, duplicate numbers, night/blur, multi-unit building, camera interruption, and weak GPS. Report hardware tests as unperformed unless a real device was used. Do not claim an accuracy percentage without a labelled field-test dataset.

## Sources

- Meta camera lifecycle and frame streaming: https://github.com/facebook/meta-wearables-dat-android/blob/main/.cursor/rules/camera-streaming.mdc
- Meta capability overview: https://developers.meta.com/wearables/
- On-device bundled text recognition and image inputs: https://developers.google.com/ml-kit/vision/text-recognition/v2/android

## Review

Self-review completed: scope is one camera/location subsystem plus the UI that explains it; no cloud backend, facade dataset, GPS-exactness promise, or invented probability. Camera pixel conversion and permission details will be verified against the actual installed SDK before implementation. User explicitly directed immediate implementation and delivery; see the implementation plan for verification results.
