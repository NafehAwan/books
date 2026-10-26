# Tiny Motors

A calm, cheerful drag-and-merge puzzle for children aged 5 to 10. Drag a car onto an identical car
on the 5 × 6 board and they merge into the next car on an 11-car ladder, from a Kei car (2 points)
up to the Hypercar (2048 points). After every move a new little car drives onto the board by
itself. Every new car goes into the Garage sticker book.

Play is unlimited: no lives, timers or "game over". The game earns money in a child-safe way:
coins (earned by playing, or bought) buy board themes in the Store, small AdMob banners show on the
Home and Garage screens (never during play), children can choose to watch a reward video for
coins, and parents can buy **Remove ads**. Every real-money button sits behind a grown-up gate.
See **Money: ads and purchases** below.

## Install the APK on your phone

1. Get `app-debug.apk`: build it yourself (below), or download the **tiny-motors-debug-apk**
   artifact from the latest run on the repository's **Actions** tab (the file comes zipped; unzip
   it first).
2. Copy `app-debug.apk` to your phone (USB cable, Google Drive, email to yourself, and so on).
3. On the phone, open the file with the Files app. Android asks you to allow installs from this
   source: tap **Settings**, turn on **Allow from this source**, go back and tap **Install**.
4. When you are done, you can turn that permission off again in
   Settings → Apps → Special app access → Install unknown apps.

With a USB cable and developer options on, `adb install app/build/outputs/apk/debug/app-debug.apk`
also works.

## Build

Requirements: JDK 17 or newer and the Android SDK (platform 36, build-tools 36). Android Studio
includes both; open the project folder and press Run.

From the command line:

```bash
# Tell Gradle where the SDK is (Android Studio does this for you):
echo "sdk.dir=$HOME/Android/Sdk" > local.properties

./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest      # rules, persistence and daily-gift tests
./gradlew lintDebug lintRelease  # Android lint (no issues)
```

## Signed release (for Google Play)

1. Create your private upload key once:

   ```bash
   scripts/make-keystore.sh
   ```

   This writes `release.jks` and `keystore.properties` in the project root. Both are in
   `.gitignore`. **Never commit them**, and back them up (for example in a password manager).

2. Build:

   ```bash
   ./gradlew bundleRelease assembleRelease
   ```

   * `app/build/outputs/bundle/release/app-release.aab` is the file you upload to Google Play.
   * `app/build/outputs/apk/release/app-release.apk` is a signed APK for sideloading.

   Without `keystore.properties`, the same command still works but produces unsigned files.
   Release builds are minified and resource-shrunk with R8.

3. Optional: sign in GitHub Actions too. Add four repository secrets:
   `KEYSTORE_BASE64` (from `base64 -w0 release.jks`), `KEYSTORE_PASSWORD`, `KEY_ALIAS`
   (default `tinymotors`) and `KEY_PASSWORD` (the same as the keystore password if you used the
   script). The **tiny-motors-release** artifact is then signed.

New Play apps use Play App Signing: Google keeps the final signing key, and yours is the
"upload key". Keep it safe anyway; resetting a lost upload key takes a support request.

## Continuous integration

`.github/workflows/android.yml` runs on every push:

* **Test, lint and build**: unit tests, lint, debug APK, release AAB and APK, uploaded as
  artifacts (`tiny-motors-debug-apk`, `tiny-motors-release`, `reports`).
* **Emulator smoke test**: starts an Android 14 emulator and runs `scripts/smoke-test.sh`, which
  installs the app and checks launch → daily gift → Play → drag-merge (a new car arrives) → move →
  tow → filling the board → process death and resume → Store (Themes, Coins) → Garage → Settings,
  then a random-tap stress test with animations on. It fails on any crash and uploads the screenshots
  (`smoke-test-screenshots`).

You can run the same smoke test against your own phone: `scripts/smoke-test.sh app-debug.apk out/`
(needs `adb`, `python3` and USB debugging).

## Project layout

```
app/src/main/java/com/tinymotors/game/
  engine/      Pure Kotlin rules: GameEngine, GameState, seedable Rng, codec, daily gift, combo, coins, Shop
  data/        DataStore persistence (GameRepository) and Settings
  audio/       SoundPool effects + MediaPlayer music with audio focus; TextToSpeech names
  ui/          Compose screens (Home, Game, Garage, Store, Settings), board, mascot, particles, overlays
  monetize/    AdsManager (child-directed AdMob) and BillingManager (Google Play Billing)
  GameViewModel.kt, MainActivity.kt
app/src/test/  Unit tests (JUnit, no device needed)
app/src/main/res/drawable-nodpi/car_01_kei.webp … car_11_hypercar.webp   the 11 cut-out toy cars
app/src/main/res/drawable-nodpi/ui_*.webp   icons, Towy the mascot, logo and win badge cut from the designs
app/src/main/res/raw/        generated sound effects and music (OGG)
art-source/ui/               the design mock-ups (icon sheet, game design, screens)
art-source/car_sheet.webp    the first car sheet (kept for reference)
tools/process_art.py         toy cars → 11 transparent cars, tile colours, launcher icon, splash
tools/process_ui_art.py      icons, Towy, logo and badge cut from the mock-ups
tools/make_audio.py          synthesises every sound and the music loop (NumPy + wave)
tools/make_font.py           static Fredoka font files from the OFL variable font
tools/make_store_art.py      Play feature graphic
tools/gpt_image.py           optional extra art with OpenAI's image model (needs your API key)
store/                       512 px Play icon, 1024 × 500 feature graphic, emulator screenshots
scripts/                     make-keystore.sh, smoke-test.sh
```

### Re-generating the assets

```bash
pip install pillow numpy opencv-python-headless fonttools "rembg[cpu]"
python3 tools/process_art.py     # cars, car_colors.xml, launcher icon, splash, previews in tools/out/
python3 tools/process_ui_art.py  # icons, Towy, logo, win badge
python3 tools/make_audio.py      # needs ffmpeg for OGG (falls back to WAV without it)
python3 tools/make_font.py
python3 tools/make_store_art.py
```

`tools/process_art.py` cuts the 11 toy cars from `art-source/ui/icons_sheet.png` with the rembg
segmentation model, removes the soft blue ground shadow baked into the mock-up, fills any window
the model mistook for background, de-fringes the edges, centres each car on a 448 × 448 canvas at
88 % width and saves WebP. It samples each car's paint colour (door area only) for the tile tint
and edge; the Hypercar tile is dark navy. Run it with `CAR_SOURCE=sheet` to use the first car sheet
instead. `tools/process_ui_art.py` keys icons off their flat button faces and uses rembg for Towy,
the logo and the win badge. Preview sheets on white, dark and sky backgrounds go to `tools/out/`.
The ground shadow under every car is drawn by the app at runtime.

## Money: ads and purchases

How it earns, and how it stays kind:

| | What | Rules in the app |
| --- | --- | --- |
| Coins | Earned free: a merge pays the new car's level (1–11), +20 for each new car type, +100 for the Hypercar, +25 with the daily gift, +50 per reward video. Also sold in packs. | Coins only buy board themes. They never buy cars, lives or progress. |
| Store | Board themes: Sunny Park (free), Beach Day 200, Snow Day 400, Candy Land 600, Night Drive 900 coins. | Cosmetic only. |
| Reward videos | "Watch an ad: +50 coins", labelled **Ad**. | Always the child's choice, at most 5 a day. |
| Banners | Small adaptive banner on Home and Garage. | Never on the game board, in dialogs or in Settings. |
| Coin packs | `coins_500`, `coins_1500`, `coins_4000` (consumable). | Behind the grown-up gate (hold 3 s), then Google Play's own confirmation. |
| Remove ads | `remove_ads` (one-time). | Removes banners for good and is restored automatically. Reward videos stay optional. |

Child-safety settings in code (`monetize/AdsManager.kt`): every request is tagged
child-directed (COPPA) and under the age of consent (GDPR), the maximum ad rating is **G**, the
advertising-ID permission and the Privacy Sandbox ad permissions are removed in the manifest, and no
analytics SDK is included.

### Set up AdMob (your ad money)

1. Create an AdMob account (<https://admob.google.com>), add the app (Android), and in
   **App settings** set it as directed to children and the max ad content rating to **G**.
2. Create two ad units: an **Adaptive banner** and a **Rewarded** ad.
3. Put the IDs in `gradle.properties` (or `~/.gradle/gradle.properties`):

   ```properties
   admobAppId=ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY
   admobBannerId=ca-app-pub-XXXXXXXXXXXXXXXX/ZZZZZZZZZZ
   admobRewardedId=ca-app-pub-XXXXXXXXXXXXXXXX/WWWWWWWWWW
   ```

   Until you do, every build uses Google's official **test** IDs (they show "Test Ad" and earn
   nothing). Debug builds always use the test IDs, so you can never click your own real ads.
4. Link AdMob to your payments profile to get paid.

### Ad content filters (keep ads halal-friendly)

No ad network can promise that every single ad fits your values, so use every filter AdMob offers.
The app already does this in code:

* every request is child-directed and under the age of consent, with maximum rating **G**, which
  already excludes alcohol, gambling, dating, adult content and personalised ads;
* ad sound is always muted, so video ads never play music or voices.

Then in the AdMob console, open **Blocking controls** for Tiny Motors:

1. **Sensitive categories:** block every one you do not want. Good candidates: astrology and
   esoteric, social casino games, get-rich-quick, dating, references to sex, cosmetic procedures,
   weight loss, ringtones and downloadables (music), sensationalism, politics, religion.
2. **General categories:** block banking, loans and credit, insurance and investing (interest),
   and any food and drink, entertainment, music or fashion groups you prefer to avoid.
3. **Advertiser URLs:** add any advertiser you see and dislike.
4. **Ad review center:** turn it on and regularly look at the ads that served in your app; block
   single ads or whole advertisers with one tap.

Category names change from time to time, so choose from the list AdMob shows you. For a 100 %
guarantee the only option is no third-party ads at all (earn from coin packs only); that is a
small code change if you ever want it.

### Set up in-app purchases (your purchase money)

1. In Play Console, set up a payments profile (Monetize → Monetization setup).
2. Upload a signed AAB to a testing track first (purchases only work for apps installed from Play).
3. Create the four products listed in `STORE_LISTING.md` (`coins_500`, `coins_1500`, `coins_4000`,
   `remove_ads`) and activate them.
4. Add your Google account as a **license tester** (Settings → License testing) to test purchases
   without being charged.

Prices are read from Google Play at runtime, so they show in each country's currency. Purchases are
checked on the device only; there is no server-side receipt check.

## What has been tested

* **Unit tests (48, all passing):** merge, move, swap, Hypercar never merging, spawn odds (seeded,
  100 000 draws each), smart spawn, preview = actual car, automatic car after every move (and undo
  taking it back), tow refill, stuck detection, undo limits, unlock rewards and the 5-tow cap, win
  once, helper truck, coins (merge, firsts, Hypercar, gift), buying and switching themes, new game
  carry-over, daily-gift dates, combo timing, save-format round trip (including old saves without
  coins), damaged saves, and a real DataStore round trip that simulates process death.
* **Lint:** no issues in debug or release.
* **Emulator (Android 14, Pixel 6 profile, in GitHub Actions):** `scripts/smoke-test.sh` passed:
  launch, daily gift (+25 coins), Play, drag-merge (7 cars stay 7 because a new car arrives; City
  hatches 2 → 3), tutorial, move to an empty space (7 → 8), tow (8 → 7), moving cars until the
  board was full (30), process death and resume with the same board, back button, Store (Themes
  and Coins tabs; the test reward video loaded), Garage with a test banner, Settings, and 1500
  random taps with animations on, with no app crash.
* **APK checks:** release APK is about 4.7 MB (AAB 8.4 MB), targets SDK 36. Permissions:
  INTERNET, ACCESS_NETWORK_STATE, WAKE_LOCK and FOREGROUND_SERVICE (needed by AdMob) and
  BILLING (Google Play); the advertising ID and Privacy Sandbox ad permissions are removed.
* **Ads and purchases:** test ads load on the emulator. Real purchases cannot be tested until the
  app is on a Play testing track with the products created; until then the Store shows
  "Not available" on those buttons.
* **Not tested:** a real phone (sound, music, spoken names and haptics can only be heard or felt
  on a device), Android 7 (minSdk 24) and tablets, a TalkBack pass, and a long play-through to the
  Hypercar on a device (the win flow is covered by unit tests).

## Choices I made where the brief left room

* **Car pictures:** the game uses the toy-style cars from your design icon sheet, in this order:
  red Kei car, blue City hatch, yellow Retro bug, green Sedan, purple Estate, blue SUV, orange
  Pickup, pink Coupe, teal Sports car, blue Supercar, purple Hypercar (the order of their shapes
  on the sheet). All 11 cut out cleanly. The first car sheet is kept in `art-source/`. The app
  still has a drawn placeholder car in case a picture ever fails to decode.
* **Design mock-ups:** Towy, the logo, icons, header pills, tabbed settings, the collection book,
  the purple "New car" ribbon and the "2048!" badge follow your designs. Hills, trees and flowers
  are drawn in code so they stay sharp on every screen size. The pictures come from 1536 px
  mock-ups, so they are a little soft at large sizes; separate high-resolution files would look
  sharper.
* **Store:** coins buy board themes only. You chose no lives, child-safe reward videos plus
  banners, and coin packs; I added a one-time Remove ads purchase because parents value it. Cars are
  never sold, so every car is still earned by merging.
* **Towy's moods** use the expression pictures: smiling when idle (with a quick blink), laughing
  on merges, surprised when stuck, and squinting with joy while dancing on a win.
* **Spawn odds after the SUV:** level 3 is 7 %, level 2 stays at 15 %, level 1 is 78 %.
* **Smart spawn** ignores Hypercars and only applies when there is at least one other car. Because
  of it, a full board almost always contains a matching pair, so "Garage is full!" is rare.
* **Cars arrive by themselves:** the Add car button is gone. After every move, swap or merge a new
  car drives in (if there is space), and after a tow the board is topped back up to at least 2
  cars. The "Next" panel shows exactly the car that will arrive, and undo takes back the move and
  the car together, so it cannot be used to re-roll.
* **Tows:** a new game starts with at least 3 tow trucks, but extra ones from gifts and new cars
  are kept (never more than 5).
* **Undo** restores the board, score and next car, but never takes back a tow truck earned from a
  new car. Undo is not offered after the free helper tow truck clears space.
* **Combos** are a cheerful effect only (bigger sparkles, rising chimes, "Combo x3"); they give no
  extra points, so nothing feels like pressure.
* **Daily gift** appears on the home screen the first time the app opens each calendar day,
  including the very first day. Tapping the box gives +1 tow truck (max 5) and 25 coins.
* **Tutorial** is interactive: a finger drags between two real matching cars on the board, then a
  card says "Every move brings a new car!" and "Find all 11 cars!". Skip is always visible.
* **Parent Reset** (behind the 3-second hold) erases progress, stickers, best score, coins and
  themes, but keeps settings and purchases (Remove ads). **Reset Game** (also a 3-second hold)
  only starts a new board.
* **No backup:** progress is never uploaded or copied to another device (`allowBackup=false` plus
  exclusion rules), so uninstalling the app deletes progress.
* **Haptics** use the system's light keyboard-tap feedback, which needs no VIBRATE permission and
  respects the phone's own vibration settings.
* **Voice:** car names are spoken with the phone's text-to-speech engine at a slightly raised
  pitch. If no engine or voice is installed, the app silently skips speech.
* **Reduced motion:** when "Remove animations" is on, particles, confetti, bobbing, wiggles and
  drive-ins are turned off and cars simply appear.
* **Portrait:** the activity requests portrait. Android 16 lets large tablets ignore that, so the
  layout also adapts (the board is capped at 480 dp and sized to fit the height).
* **Compose version:** the newest Compose (1.12, BOM 2026.08+) requires compileSdk 37. To keep
  compile and target SDK at 36 (Google Play's current requirement), the project uses Compose BOM
  2026.06.01 (Compose 1.11.4), with AGP 9.4.1, Kotlin 2.4.21 and Gradle 9.8.2.
* **Font:** Fredoka (SIL Open Font License) is bundled as two small static weights. The licence is
  in `app/src/main/assets/licenses/fredoka_ofl.txt` and `tools/font-source/OFL.txt`.
* **GPT Image 2:** I could not use it. It is a hosted OpenAI API, not something that can be
  installed, and this build environment had no OpenAI API key and no network access to
  api.openai.com. All game art comes from your car sheet, and the icon, splash and store graphics
  are made from those cut-outs. `tools/gpt_image.py` is ready if you want to generate extra art
  with your own key.

## Before publishing on Google Play (things only you can do)

1. Create a Google Play Console developer account (one-time fee) and verify your identity.
2. Change `applicationId` in `app/build.gradle.kts` if you want a different package name; it
   cannot be changed after the first upload.
3. Run `scripts/make-keystore.sh`, build `app-release.aab`, and create the app in Play Console.
4. Host `PRIVACY_POLICY.md` at a public URL (GitHub Pages works) and enter that URL in Play
   Console.
4b. Set up AdMob and the in-app products (see **Money: ads and purchases**).
5. Fill in the store listing, content rating, Data safety form and target audience using
   `STORE_LISTING.md`. Choose the Families programme.
6. Upload screenshots (take them on your phone, or use the smoke-test screenshots), the icon
   `store/play_icon_512.png` and `store/feature_graphic_1024x500.png`.
7. New personal developer accounts must run a closed test with testers for a period before
   production; check the current rule in Play Console.
