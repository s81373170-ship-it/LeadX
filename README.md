# LeadX Pro (Android)

B2B lead intelligence app built with Kotlin, Jetpack Compose, Room, WorkManager, and Firebase Firestore.

## Easy Shareable `.apk` Download (Chrome & Teammates)

### Important: Make Repo Public First
If your GitHub repository is **Private**, anyone you share a link with in Chrome will get a **404 / Sign-In error** and will not be able to download the APK.
1. Open your GitHub repo **Settings → Danger Zone → Change repository visibility → Make public**.
2. Push to `main` (or run **Actions → Build & Download LeadX Pro APK → Run workflow**).

### Permanent Download Links (Safe to Share Anywhere)
Every push runs Android unit tests, Web tests, verifies APK signatures (`apksigner verify`), and publishes **`LeadX-Pro.apk`**:
1. **Direct Chrome APK Download (GitHub Releases)**:
   `https://github.com/s81373170-ship-it/Linkedin-data-apk/releases/latest/download/LeadX-Pro.apk`
2. **GitHub Releases Page**:
   `https://github.com/s81373170-ship-it/Linkedin-data-apk/releases/latest`
3. **Web & APK Portal (GitHub Pages)**:
   `https://s81373170-ship-it.github.io/Linkedin-data-apk/`

> **Why Chrome "Share" failed before**:
> - **Do not share Actions Artifact URLs** (`.../actions/runs/.../artifacts/...`): GitHub always blocks unauthenticated users from downloading Actions artifacts, and they download as a `.zip` instead of `.apk`.
> - **Do not share Chrome's temporary Download URL** (`release-assets.githubusercontent.com/...` copied from Chrome Downloads): those signed URLs expire within minutes and fail for the recipient.
> - **Always share the permanent `releases/latest/download/LeadX-Pro.apk` link above**, or use the in-app **Share Installable .apk File** / **Save .apk to Downloads** buttons to send the actual `.apk` file.

### Android Install Note (Realme / ColorOS / Oppo / Xiaomi / WhatsApp)
If tapping the `.apk` directly inside WhatsApp shows *"There was a problem parsing the package"*, open **Files / Chrome Downloads** and install **`LeadX-Pro.apk`** from **Downloads** directly.

## Local Build & Tests
```bash
# Restore debug keystore (first time)
tr -d '\r\n' < debug.keystore.base64 | base64 -d > debug.keystore

# Run unit tests and assemble APK
gradle :app:testDebugUnitTest :app:assembleDebug
```

## API Keys Security Note
Configure `PROXYCURL_API_KEY` and `SUREPASS_API_TOKEN` via the **Secrets panel in AI Studio** (never in `local.properties`) or via the in-app **API Keys** dialog. Do not embed private build-time API secrets into APKs you distribute publicly.
