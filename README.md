# Kharchnama for Android

Personal expense book with Faygul, your AI financial adviser. This is the Android build of the Kharchnama app.

## What the Android app adds
- **SMS auto-import**: reads bank and wallet transaction SMS (inbox scan and new messages live). OTPs and promotions are ignored, duplicates are skipped, and you can review before anything is added.
- **Bill reminders** as real notifications at 9:00 on the due date.
- **Fingerprint / face unlock** on top of the 4-digit PIN; the app relocks after a minute in the background.
- **Reports and backups** save or share through Android's share sheet.
- **Works offline**: fonts and libraries are bundled.
- **Faygul** uses your own Anthropic API key (More → Faygul AI). The key is stored only on the phone.

## Get the APK (no computer needed)
Every push to `main` builds the app on GitHub Actions. When the build finishes (about 5 minutes), open the repository's **Releases** page on your phone, tap the latest `Kharchnama-N.apk`, and allow installing from your browser when Android asks. Later builds install over the previous one and keep your data.

## Build it yourself
1. Install Node 20, JDK 17 and Android Studio.
2. `npm ci && node build-web.js && npx cap sync android`
3. Open the `android` folder in Android Studio and press Run, or run `cd android && ./gradlew assembleDebug`.

## Project layout
- `web/app.html`: the app (same source as the Claude artifact version).
- `web/native.js`: bridge to Android features and the Anthropic API.
- `build-web.js`: bundles the app, fonts and libraries into `www/`.
- `android/app/src/main/java/com/kharcha/app/KharchaNativePlugin.java`: SMS reading and biometric unlock.

## Before publishing on the Play Store
- Create a private release signing key; the included `kharcha-debug.keystore` is for personal testing only.
- Google Play only allows SMS permissions for default SMS apps. For the store version, switch SMS auto-import to reading bank-app notifications or remove it.
- Route Faygul through your own server instead of per-user API keys if you want users to chat without bringing a key.
