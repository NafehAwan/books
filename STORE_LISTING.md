# Google Play store listing: Tiny Motors

## App details

| Field | Value |
| --- | --- |
| App name (max 30) | **Tiny Motors: Car Merge** |
| Short description (max 80) | **Merge cute cars, build a Hypercar and fill your Garage sticker book!** (72 characters) |
| Category | Games → Puzzle (or Educational) |
| Tags | Merge, Cars, Puzzle, Kids, Casual |
| Contains ads | **Yes** (child-safe AdMob: small banners on menus and above the game board, optional reward videos) |
| In-app purchases | **Yes** (coin packs, Remove ads; all behind a grown-up gate) |
| Graphics | Icon `store/play_icon_512.png`, feature graphic `store/feature_graphic_1024x500.png`, screenshots in `store/screenshots/` |

## Full description

> Vroom! Tiny Motors is a calm, cheerful car puzzle made for kids aged 5 to 10.
>
> Drag a car onto a car that looks the same and *pop!* they turn into a bigger, cooler car. After
> every move a new little car drives onto the board. Can you go all the way from the tiny Kei car
> to the amazing Hypercar?
>
> 🚗 11 colourful toy cars to discover: Kei car, City hatch, Retro bug, Sedan, Estate, SUV, Pickup,
> Coupe, Sports car, Supercar and Hypercar
> 📒 A Garage sticker book that fills up with every new car. Tap a sticker to hear it honk and say
> its name
> 🛻 Towy the friendly tow truck peeks over the board, cheers you on and gives gentle hints
> 🎨 22 fun car skins to collect: Police Patrol, Taxi, Dino Rider, Bunny Hop, Unicorn Dream, Galaxy
> and more
> 🪙 Earn coins by merging and decorate your board with themes: Beach Day, Snow Day, Candy Land and
> Night Drive
> 🎁 A little present every day, with no streaks and nothing lost if you miss a day
> 🎶 Happy sounds and a relaxing marimba tune
>
> Made for kids and parents:
> • Unlimited play: no lives, no timers, no countdowns, no "game over"
> • Child-safe ads only (no personalised ads): a small banner that never covers the game, no pop-up
>   ads, and a one-time Remove ads option
> • Every purchase sits behind a grown-up gate; coins can always be earned for free
> • Parent settings for sound, music, vibration, voice and hints
> • Respects the system "Remove animations" setting

## Target audience and content (Families)

* **Target age groups:** **Ages 6–8** and **Ages 9–12** (add Ages 5 & under if you like). This
  puts the app in Google Play's Families programme.
* **Appeals to children:** Yes.
* **Ads:** Yes. AdMob is on Google Play's list of Families self-certified ads SDKs. The app tags
  every request child-directed and under age of consent, allows only G-rated ads, does not use the
  advertising ID and strips the Privacy Sandbox ad permissions. In the AdMob console also mark the
  app as **child-directed** ("Tag for child-directed treatment") and set the maximum ad content
  rating to **G**.
* **Teacher Approved programme:** possible after launch, but reviewers prefer apps without banners.

## Content rating questionnaire (IARC) suggested answers

* Category: **Game**.
* Violence, fear, sexuality, language, controlled substances: **No**.
* Gambling or simulated gambling: **No**. The next car is random but free, and coins are never
  wagered.
* Users can interact or exchange content: **No**. Shares user location: **No**.
* **Digital purchases: Yes** (in-app purchases).
* Expected rating: **Everyone / PEGI 3 / USK 0**, with "In-app purchases" and "Contains ads" labels.

## Data safety form answers

The developer collects nothing. The data below is collected by the **Google Mobile Ads SDK**.
Check it against Google's current AdMob guidance before you submit
(<https://developers.google.com/admob/android/privacy/play-data-disclosure>).

* Does your app collect or share any of the required user data types? **Yes** (through AdMob).
* **Location → Approximate location:** collected, shared with Google; purpose: advertising, fraud
  prevention. (Estimated from the IP address.)
* **App activity → App interactions:** collected, shared; purpose: advertising, analytics, fraud
  prevention.
* **App info and performance → Crash logs, Diagnostics:** collected, shared; purpose: analytics,
  fraud prevention.
* **Device or other IDs:** collected, shared; purpose: advertising, fraud prevention. (The
  advertising ID is not used, but the SDK may use the app set ID.)
* Is data processed ephemerally? No. Is collection required? Yes (users cannot opt out in the app,
  but parents can buy Remove ads to stop banners).
* Is all data encrypted in transit? **Yes.**
* Can users request deletion? Game progress: yes, in the app (Settings → Parent Reset) or by
  uninstalling. Ad data is handled by Google under its policies.
* Game progress is included in **Android's own backup** (to the user's Google account; the
  developer has no access). Google's Data safety guidance treats data the developer cannot access
  as not collected, so it does not need to be declared; check the current Play Console help page
  ("Data safety section") when you fill in the form.
* Purchases: handled by Google Play; the app does not collect payment info or purchase history.
* Play reminders are local notifications made on the device; nothing is collected for them.
* Privacy policy URL: the public URL where you host `PRIVACY_POLICY.md`.

## In-app products to create in Play Console (Monetize → Products → In-app products)

| Product ID | Type | Suggested price | Gives |
| --- | --- | --- | --- |
| `coins_500` | Consumable (one-time product, multiple purchases) | $0.99 | 500 coins |
| `coins_1500` | Consumable | $1.99 | 1500 coins |
| `coins_4000` | Consumable | $3.99 | 4000 coins |
| `remove_ads` | Non-consumable (one-time) | $2.99 | No more banners |

## App access, ads and government declarations

* App access: **All functionality is available without special access** (no login).
* Ads declaration: **Yes, my app contains ads.**
* News app: No. COVID-19 app: No. Government app: No. Financial features: None. Health: No.

## Screenshots (phone, 1080 × 1920, ready to upload)

Google Play needs 2–8 phone screenshots, and the long side may be at most twice the short side
(so 1080 × 2400 is rejected; these are 9:16). Taken on an emulator in airplane mode, so no test
ads show. Re-take them with `scripts/store-screenshots.sh` (or the "Store screenshots" workflow).

1. `store/screenshots/1_home.png`: Home screen with the logo and Towy
2. `2_game.png`: a busy board mid-game
3. `3_new_car.png`: the "New car unlocked!" ribbon
4. `4_garage.png`: the Garage sticker book
5. `5_store_themes.png`: board themes in the Store
6. `6_daily_gift.png`: the daily present
7. `7_parent_settings.png`: parent settings
