# Buddy Blade — Android project

A ready-to-open Android Studio project. The game is one self-contained
HTML file in `app/src/main/assets/index.html`, shown in a fullscreen
landscape WebView. No Cordova, no npm, no command line.

## Build the APK

1. Open **Android Studio** → **File ▸ Open…** → pick this `android` folder
   (the one with `settings.gradle`), then **OK**.
2. Wait for "Gradle sync" to finish in the status bar. The first sync
   downloads Gradle and the Android plugin, so give it a few minutes.
3. **Build ▸ Build App Bundle(s) / APK(s) ▸ Build APK(s)**.
4. When the popup says "APK(s) generated successfully", click **locate**.
   The file is `app/build/outputs/apk/debug/app-debug.apk`.

Copy that to your phone and tap it (allow installs from unknown sources),
or with the phone plugged in and USB debugging on, just press **Run ▶** in
Android Studio to install and launch it directly.

## Updating the game

The APK embeds a copy of the game. After changing anything in `src/`:

```sh
python3 tools/bundle.py
cp buddy-blade.html android/app/src/main/assets/index.html
```

then rebuild in Android Studio.

## What the wrapper does

`MainActivity` keeps the screen on, locks to landscape, hides the status
and navigation bars, and enables DOM storage so the game's saves persist.
Everything else is the same build that runs in a desktop browser.
