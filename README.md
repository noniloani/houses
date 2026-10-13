# BC House HUD (Meta Ray-Ban Display)

An Android app for Meta Ray-Ban Display glasses. Look at a house in British Columbia and the
glasses show its BC Assessment record as a full-colour HUD: assessed value, change vs last
year, land vs building split, lot size, year built, bed/bath, floor area, a 10-year value
trend, and sale status.

Built on Meta's [DisplayAccess sample](https://github.com/facebook/meta-wearables-dat-android/tree/main/samples/DisplayAccess)
(Wearables Device Access Toolkit `mwdat` 1.0.1) following the
[display overview](https://wearables.developer.meta.com/docs/develop/dat/display-overview):
session first, attach Display after `STARTED`, send content only after `DisplayState.STARTED`,
one root view per `sendContent`, images kept under 600x600.

## Camera + location targeting

The redesigned **House lens** scanner combines the phone's GPS with live frames from the glasses. Start scanning, grant Meta camera access, and aim the visible house number inside the guide. Bundled on-device text recognition tags address text; frames are not recorded or uploaded.

Nearby BC civic addresses constrain the number matches. Three consistent observations within two seconds produce a **Visual match**. Conflicting numbers, duplicate addresses, stale GPS and expired camera evidence lower certainty. **Nearby estimate** means geographic ranking only; **Manually selected** holds your choice for 20 seconds. The camera follows head direction, not eye gaze, and unreadable house numbers cannot be identified exactly. No glasses GPS feed is assumed.

The phone shows the camera preview, recognition boxes, GPS accuracy, nearby addresses and property details. The glasses card carries the same identification status. Scanning continues when the phone is locked or pocketed through a foreground service. Stop with the app button or the scanning notification. Display updates are serialized and the most recent card is replayed after automatic reconnection. Phone preview displays the HUD without a real glasses camera.

BC Assessment search and returned records are checked against civic number, street, direction and locality. Strata buildings open a paged unit picker in the glasses. Select a unit with the glasses controls to load its assessment; Units returns to the list and Buildings selects another nearby building. A selected unit holds the building until you leave that view. Rental buildings assessed as one property have no invented per-unit values. Ambiguous or mismatched records are withheld. Lookups are cached only for selected addresses; nearby assessments are not bulk fetched.

## For-sale status

BC Assessment doesn't publish listings, and MLS data (REALTOR.ca, CREA DDF) needs a licensed
feed, so the HUD shows "Sale status: no feed" for now. It does flag "Sold recently" when BC
Assessment's sales history lists a sale. Plug a licensed feed into `ListingProvider` to light
up "FOR SALE $asking".

## Run it

Needs Android Studio (or JDK 17+ and Android SDK 36), an Android phone with the Meta AI app,
and the glasses with Developer Mode on and the toolkit installed.

```bash
git clone https://github.com/noniloani/houses && cd houses
./gradlew installDebug        # phone connected over adb
./gradlew testDebugUnitTest   # parser/ranking tests (uses a real BC Assessment fixture)
```

In Developer Mode no credentials are needed. For a release build, put
`mwdat_application_id` and `mwdat_client_token` from Wearables Developer Center in
`local.properties`.

Open **House lens**, tap **Connect glasses** and register through Meta AI, then return to **Scan** and tap **Start scanning**. Allow precise location, nearby devices, and camera access. Use **Preview on phone** to test the glasses HUD without glasses (no real vision input).

### Field test checklist

- Look between adjacent numbered houses while the phone faces another direction.
- Confirm a readable number becomes a visual match and its assessment address agrees.
- Check duplicate numbers on different streets stay uncertain without street evidence.
- Cover the number or pause/disconnect the camera; the match must lose its active label.
- Select a nearby address manually; it remains held for 20 seconds.
- Test blur/night, weak GPS, multi-unit buildings, denied permissions and network loss.

Build and JVM tests do not verify camera behavior on physical glasses. Measure hardware latency and accuracy during these field tests; no accuracy percentage is claimed.

## Notes

- Use it for personal lookups. BC Assessment's site has its own terms of use;
  this is not a bulk scraper.
- Strata buildings show a "multi-unit" card, since each unit has its own assessment.
- Glasses-side orientation (the experimental Motion capability) would beat the phone compass;
  it needs Motion enabled for the app in Wearables Developer Center.
