# LeadX Pro (Android)

B2B lead intelligence app built with Kotlin, Jetpack Compose, Room, WorkManager, and Firebase Firestore.

## Easy Shareable `.apk` Download (Chrome & Teammates)

### Important: Make Repo Public First
If your GitHub repository is **Private**, anyone you share a link with in Chrome will get a **404 / Sign-In error** and will not be able to download the APK.
1. Open your GitHub repo **Settings → Danger Zone → Change repository visibility → Make public**.
2. Push to `main` (or run **Actions → Build & Download LeadX Pro APK → Run workflow**).

### Permanent Download Links (Safe to Share Anywhere)
Every push runs Android unit tests, Web tests, verifies APK signatures (`apksigner verify`), and publishes a single universal **`LeadX-Pro.apk`** (no split APKs or `.aab` bundle):
1. **Direct Chrome APK Download (GitHub Releases)**:
   `https://github.com/s81373170-ship-it/LeadX/releases/latest/download/LeadX-Pro.apk`
2. **GitHub Releases Page**:
   `https://github.com/s81373170-ship-it/LeadX/releases/latest`
3. **Web & APK Portal (GitHub Pages)**:
   `https://s81373170-ship-it.github.io/LeadX/`

> **Fixing Red `❌` CI / Empty Releases on GitHub**:
> 1. Make sure `.github/workflows/build-apk.yml` is pushed to `main` in `s81373170-ship-it/LeadX`.
> 2. Open GitHub repo **Settings → Actions → General → Workflow permissions** and select **Read and write permissions** so the workflow can create and publish `LeadX-Pro.apk` under **Releases**.

> **Why Chrome "Share" failed before**:
> - **Do not share Actions Artifact URLs** (`.../actions/runs/.../artifacts/...`): GitHub always blocks unauthenticated users from downloading Actions artifacts, and they download as a `.zip` instead of `.apk`.
> - **Do not share Chrome's temporary Download URL** (`release-assets.githubusercontent.com/...` copied from Chrome Downloads): those signed URLs expire within minutes and fail for the recipient.
> - **Always share the permanent `releases/latest/download/LeadX-Pro.apk` link above**, or use the in-app **Share Installable .apk File** / **Save .apk to Downloads** buttons to send the actual `.apk` file.

### How to Install on Android ('Install from unknown sources')
1. Open **`LeadX-Pro.apk`** from **Chrome Downloads** or your phone's **Files** app (on Realme / ColorOS / Oppo / Xiaomi phones, avoid tapping `.apk` files directly inside WhatsApp if you see a package parse error).
2. When Android shows *"For your security, your phone isn't allowed to install unknown apps from this source"*, tap **Settings** and enable **Allow from this source** (`Settings → Apps → Special app access → Install unknown apps → Chrome / Files → Allow`).
3. Tap **Back** and choose **Install** (if Google Play Protect prompts, tap **More details → Install anyway**).

## Local Build & Packaging Verification
```bash
# Restore debug keystore (first time)
tr -d '\r\n' < debug.keystore.base64 | base64 -d > debug.keystore

# Run unit tests and assemble a single universal .apk (splits disabled in app/build.gradle.kts)
gradle :app:testDebugUnitTest :app:assembleDebug
```

## API Keys Security Note
Configure `PROXYCURL_API_KEY` and `SUREPASS_API_TOKEN` via the **Secrets panel in AI Studio** (never in `local.properties`) or via the in-app **API Keys** dialog. Do not embed private build-time API secrets into APKs you distribute publicly.
