# Tiny Motors

A calm, cheerful drag-and-merge puzzle for children aged 5 to 10. Tap **Add car** and a little car
drives onto the 5 × 6 board. Drag a car onto an identical car and they merge into the next car on
an 11-car ladder, from a Kei car (2 points) up to the Hypercar (2048 points). Every new car goes
into the Garage sticker book.

There are no ads, accounts, purchases, links, timers or data collection. The app works fully
offline and asks for **no permissions**.

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
  installs the app and checks launch → daily gift → Play → Add car → drag-merge → tow → rapid taps
  until the board is full → process death and resume → Garage → Settings, then a random-tap stress
  test with animations on. It fails on any crash and uploads the screenshots
  (`smoke-test-screenshots`).

You can run the same smoke test against your own phone: `scripts/smoke-test.sh app-debug.apk out/`
(needs `adb`, `python3` and USB debugging).

## Project layout

```
app/src/main/java/com/tinymotors/game/
  engine/      Pure Kotlin rules: GameEngine, GameState, seedable Rng, codec, daily gift, combo
  data/        DataStore persistence (GameRepository) and Settings
  audio/       SoundPool effects + MediaPlayer music with audio focus; TextToSpeech names
  ui/          Compose screens (Home, Game, Garage, Settings), board, mascot, particles, overlays
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

## What has been tested

* **Unit tests (38, all passing):** merge, move, swap, Hypercar never merging, spawn odds (seeded,
  100 000 draws each), smart spawn, preview = actual car, stuck detection, tow, undo (limit,
  rewards kept, same car after undo), unlock rewards and the 5-tow cap, win once, helper truck,
  new game carry-over, daily-gift dates, combo timing, save-format round trip, damaged saves, and a
  real DataStore round trip that simulates process death.
* **Lint:** no issues in debug or release.
* **Emulator (Android 14, Pixel 6 profile, in GitHub Actions):** `scripts/smoke-test.sh` passed:
  launch, daily gift, Play, Add car, drag-merge, tutorial, tow, 30 rapid Add car taps until the
  board was full (plus taps on the full board), process death and resume with the same board,
  back button, Garage, Settings, and 1500 random taps with animations on, with no crash.
* **APK checks:** release APK is about 2.4 MB, declares no permissions, targets SDK 36.
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
* **No Store:** the Store screen in the mock-up (coins, "Get 750", buying cars and themes) is not
  built. Your brief rules out purchases and manipulative rewards, and Google Play's Families policy
  is strict about virtual currency for children. Every car is earned by merging.
* **Towy's moods** use the expression pictures: smiling when idle (with a quick blink), laughing
  on merges, surprised when stuck, and squinting with joy while dancing on a win.
* **Spawn odds after the SUV:** level 3 is 7 %, level 2 stays at 15 %, level 1 is 78 %.
* **Smart spawn** ignores Hypercars and only applies when there is at least one other car. Because
  of it, a full board almost always contains a matching pair, so "Garage is full!" is rare.
* **Preview = what you get:** the next random car is rolled in advance, so the Add car preview is
  always exactly the car that arrives, and undo cannot be used to re-roll it.
* **Tows:** a new game starts with at least 3 tow trucks, but extra ones from gifts and new cars
  are kept (never more than 5).
* **Undo** restores the board, score and next car, but never takes back a tow truck earned from a
  new car. Undo is not offered after the free helper tow truck clears space.
* **Combos** are a cheerful effect only (bigger sparkles, rising chimes, "Combo x3"); they give no
  extra points, so nothing feels like pressure.
* **Daily gift** appears on the home screen the first time the app opens each calendar day,
  including the very first day. Tapping the box gives +1 tow truck (max 5); if already full, it
  says so kindly.
* **Tutorial** is interactive: a finger shows "tap Add car", then drags between two real matching
  cars on the board, then a "Find all 11 cars!" card. Skip is always visible.
* **Start over** (behind the 3-second hold) erases progress, stickers, best score and the
  tutorial flag, but keeps the sound settings. A separate, ungated **New game** in Settings keeps
  stickers and best score.
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
   Console. The app itself contains no links.
5. Fill in the store listing, content rating, Data safety form and target audience using
   `STORE_LISTING.md`. Choose the Families programme.
6. Upload screenshots (take them on your phone, or use the smoke-test screenshots), the icon
   `store/play_icon_512.png` and `store/feature_graphic_1024x500.png`.
7. New personal developer accounts must run a closed test with testers for a period before
   production; check the current rule in Play Console.
