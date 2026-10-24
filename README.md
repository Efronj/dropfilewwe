# QuickDrop

Fast two-way file sharing for Android with an iOS-style glass UI. Built with Kotlin + Jetpack Compose on
Google's **Nearby Connections** API (the same family of tech as Quick Share): devices find each other over
Bluetooth, then the link upgrades to Wi-Fi Direct / hotspot for full-speed transfers. No internet needed.

## Get the APK (no Android Studio needed)

1. Create a free GitHub account and a **new empty repository**.
2. Upload everything in this folder (keep the `.github` folder) and commit to the `main` branch.
3. Open the **Actions** tab → **Build APK** → wait ~5 minutes for the green tick
   (or press **Run workflow**).
4. Open the finished run → download the **QuickDrop-apk** artifact → unzip → `app-release.apk`.
5. Copy the APK to your phone and install it (allow "Install unknown apps" when asked).
   Install it on **both** phones you want to transfer between.

### Or build with Android Studio
Open this folder in Android Studio (Ladybug or newer) → **Build → Build APK(s)**.
Command line: `./gradlew assembleRelease` (needs the Android SDK and JDK 17).

## Using it
- Open QuickDrop on both phones and allow the permissions.
- Tap the other phone's bubble on the radar. The receiver confirms the matching 4-digit code.
- Tap the big **Send** orb → photos & videos, files, clipboard or text. Both sides can send at any time.
- From any app (Gallery, Files…) use **Share → QuickDrop** and pick the device.
- Received files are sorted into `Pictures/QuickDrop`, `Movies/QuickDrop`, `Download/QuickDrop`.

## Features
1. Two-way transfer of photos, videos and any file, streamed at Wi-Fi Direct speed
2. Live speed and time-left on every transfer, with cancel
3. Appears in every app's **Share** sheet
4. Send text, links or your clipboard instantly
5. Transfer history — tap to open files or copy received text
6. Trusted devices — auto-accept connections from phones you choose
7. Received files auto-sorted into Pictures / Movies / Downloads
8. Storage meter
9. Large file finder — find the biggest photos/videos, then send or open them
10. QR code maker, flashlight toggle and battery check

## Notes
- Android 10 or newer. Wi-Fi and Bluetooth must be on (Android 11 and older also need Location on).
- Trusted devices are matched by device name, so only trust names you recognise.
- The release APK is signed with the standard debug key so it installs directly; for the Play Store
  you would add your own signing key.
