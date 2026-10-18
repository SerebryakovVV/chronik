# Chronik

A single-screen Android app for quick notes. Requires Android 7.0 (API 24) or later.

- **Add note** at the bottom creates a blank note at the top and opens the keyboard.
- Edit each note directly in its card. Text and order save automatically on the device.
- **Copy** copies the complete text, including line breaks and spaces.
- The trash button deletes a note; **Undo** restores the most recent deletion while its message is visible.
- Drag a card's handle to reorder it. Tap the handle for **Move up** and **Move down**; the same actions are exposed to accessibility services.
- Tap outside an editor to clear its focus and dismiss the keyboard. Tapping another note switches directly to that editor.
- The app follows the device's light or dark theme.

## Build and verify

Open the project in Android Studio with Android SDK 36.1 and JDK 21 installed, then run the `app` configuration.

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug
.\gradlew.bat connectedDebugAndroidTest
```

The second command requires an emulator or connected device. Instrumentation tests cover storage round trips, malformed data, editing, recreation, copying, deleting/undoing, and reordering.

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Release APK

Run `.\gradlew.bat assembleRelease` to produce the signed release APK at `app/build/outputs/apk/release/app-release.apk` when the local signing configuration is present.

The release key and signing properties are stored in the ignored `.signing/` directory. Keep a private backup of this directory: future updates must use the same signing key. Without these local files, release builds are unsigned.

The app uses native Android views, RecyclerView and ItemTouchHelper, with app-private SharedPreferences storage. It requires no account, server, or network permission.
