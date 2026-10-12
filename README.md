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

## How it works

1. **Where you are and which way you face.** Phone GPS plus the phone's compass
   (`HouseLocator`). Keep the phone facing the same way you do (in hand, or chest pocket
   screen-out). With no compass reading it falls back to the nearest address.
2. **Which house.** The free [BC Address Geocoder](https://www2.gov.bc.ca/gov/content/data/geographic-data-services/location-services/geocoder)
   returns civic addresses within 70 m; the one inside a ~35° cone of your heading wins.
3. **The record.** `BcAssessmentClient` does what the public bcassessment.ca search page does
   (session cookie, address search, property information) and `BcAssessmentParser` reads it.
   Results are cached; only the house you are looking at is fetched.
4. **The HUD.** `HudRenderer` draws a 560x360 colour bitmap (black is see-through on the
   glasses) and the controller sends it with **Prev / Rescan / Next** buttons, which you drive
   with the touchpad or Neural Band. The HUD follows you: walk 12 m or turn 25° and it
   re-picks, unless you just used Prev/Next (then it holds for 20 s).

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

Open the app, tap **Connect my glasses**, allow Bluetooth and location, then **Samples ›
BC house HUD › Try it**. Use **Developer preview** to test on the phone without glasses.

## Notes

- Use it for personal lookups. BC Assessment's site has its own terms of use;
  this is not a bulk scraper.
- Strata buildings show a "multi-unit" card, since each unit has its own assessment.
- Glasses-side orientation (the experimental Motion capability) would beat the phone compass;
  it needs Motion enabled for the app in Wearables Developer Center.
