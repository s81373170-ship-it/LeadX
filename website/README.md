# LeadX Pro Website

Classic minimal black-and-white B2B Lead Intelligence & Wallet web app matching the Android LeadX Pro architecture.

## Run Locally
```bash
npx serve website
```
Open `http://localhost:3000`.

## Optional Cloud Mode (Firebase Google Sign-In + Firestore)
1. Copy `firebase-config.example.js` to `firebase-config.js` and add your Firebase Web SDK keys plus optional `SUREPASS_API_TOKEN` / `PROXYCURL_API_KEY` in `window.LEADX_API_CONFIG`.
2. Add or override your Surepass Vahan RC API token or Proxycurl API key inside the in-app **Settings / API Keys** view when running live lookups.

## Modules
- `api-services.js`: Surepass Vahan RC verification (`SUREPASS_API_TOKEN`, deducts 15 pts while enforcing 5,000-pt minimum reserve) and Proxycurl live LinkedIn profile enrichment.
- `linkedin-scraper.js`: LinkedIn Sales Navigator visible DOM extractor, Chrome Extension background service worker generator, and Firestore `leads` persistence (`10` pts/lead).
- `app.js`: Monochromatic Owner/Employee wallet portal, ID point transfer, CSV exporter, and UI state controller.

