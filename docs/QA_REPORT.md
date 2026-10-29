# Sift QA report

**Date:** 25 October 2026
**Device:** OPPO CPH2333 (Android 13, 1080×2412, 480 dpi), connected over USB debugging
**Build:** debug APK from `main` (also in `dist/Sift-debug.apk`)
**Backend:** Django dev server on the PC, reached from the phone with `adb reverse tcp:8000 tcp:8000`

## Summary

| Area | Result |
|---|---|
| Android build (`assembleDebug`) | Pass |
| Minified release build (`assembleRelease`, R8) | Pass |
| Android lint (`lintDebug`) | 0 errors; remaining warnings are newer-dependency and API-level notices |
| Android unit tests (`testDebugUnitTest`) | 34 tests, all pass |
| Backend tests (`python manage.py test analyses`) | 54 tests, all pass |
| End-to-end flow on the phone | Pass (see below) |
| Crashes found during QA | 1, fixed |
| NVIDIA or Nemotron text in the app | None (case-insensitive search of `android/app/src` finds 0 files) |

## How the AI steps were tested

The backend `.env` still has placeholder values for `NEBIUS_API_KEY` and `TAVILY_API_KEY`, so real model calls cannot run. To exercise every screen end to end anyway, QA ran the real backend with the scripted model and search stubs that the backend's own test suite uses (`analyses/tests/fakes.py`). Everything else in those runs was real:

- App Store search and reviews came live from Apple.
- The CSV upload was parsed by the backend.
- Statistics, Room caching, polling, export and sharing all ran for real.

Only the AI-written text (theme names, backlog reasoning, the reply draft, the competitor summary) came from the stub. That stub text does not appear in any screenshot in this repo. A run with real keys was not possible; see "Not tested".

## End-to-end checks on the phone

| # | Scenario | Result |
|---|---|---|
| 1 | Fresh install, first launch: system splash zooms out, then the animated logo splash | Pass |
| 2 | Language picker: 15 languages in their own scripts; choice applies instantly | Pass |
| 3 | Welcome pages: swipe, parallax, morphing indicator, Next/Get started, Skip | Pass |
| 4 | Sift Pro preview: plan selection, "Continue with Pro" shows "coming soon" only, "Maybe later" goes Home | Pass |
| 5 | Home: animated logo, search field, Import CSV row, empty Recent analyses state | Pass |
| 6 | Search "notion" and "whatsapp": live App Store results with icons and ratings | Pass |
| 7 | Setup: country card, add a competitor (Telegram), counter "1 of 3", Start analysis | Pass |
| 8 | Progress: live timeline, percentage, each step turns done, opens the report | Pass |
| 9 | Report, Overview tab: metrics count up, donut and rating bars draw in (50 real WhatsApp reviews) | Pass |
| 10 | Report, Backlog tab and item detail; "Copy as ticket" morphs to "✓ Copied" and fills the clipboard | Pass |
| 11 | Report, Reviews tab: sentiment filter (50 → 26 negative), review detail | Pass |
| 12 | Review detail: draft a reply (Friendly), Copy button | Pass |
| 13 | Report, Competitors tab: competitor card, summary, sources | Pass |
| 14 | Share report: system share sheet with the Markdown file, no permission errors in logcat | Pass |
| 15 | CSV import: file picker, setup shows "CSV file", analysis runs, report with 12 reviews | Pass |
| 16 | Settings: Language row, Sift Pro row, Dark/Light/System, Clear local data, About ("AI-powered insights"), licence | Pass |
| 17 | Error: backend stopped, search shows "Can't reach the Sift server" with Try again | Pass |
| 18 | Process death (`am kill` while in a report, then reopen) | **Crashed, fixed**, then pass (reopens on the report) |
| 19 | Arabic (RTL): language switch, mirrored Home and Settings, translated About card | Pass |
| 20 | Back navigation from every screen | Pass |

| 21 | German (long words): Settings About card and report Backlog tab, hyphenated, nothing cut off | Pass |
| 22 | Japanese (CJK): Home, recent analyses and report Backlog tab | Pass |
| 23 | Accessibility spot-check: no tappable control without a label on Settings and the report tabs (one Settings row fixed) | Pass |

Every string in all 15 languages is generated through `tools/i18n`, which checks every key and placeholder against English.

## Update, 29 October 2026: running without a Nebius key

The backend now has a real `TAVILY_API_KEY` but no `NEBIUS_API_KEY` (health reports `"nebius": false, "tavily": true`). Results, live:

| Check | Result |
|---|---|
| Health endpoint | Pass: reports which keys are set, never the keys themselves |
| App Store search ("whatsapp") | Pass, live |
| Review collection (WhatsApp, US) | Pass: 300 real reviews fetched and stored (rating distribution computed without AI) |
| Analysis without the AI key | Pass: reviews are collected first, then the analysis stops with "The server's AI key is not configured, so this analysis cannot run. Add NEBIUS_API_KEY to backend/.env and restart the server." (previously it failed before collecting, with a terse message) |
| Reply draft without the AI key | Pass: `503` with the same clear message |
| Classification, themes, backlog, replies, competitor research | **Not run**: needs `NEBIUS_API_KEY`. No other AI provider and no demo mode are used. |

Two fixes came out of this run:
- The Progress screen now marks **Collect reviews** as done (for example "300 unique reviews collected") and **Classify every review** as the step that stopped. Before, it showed collection as failed.
- On the failure card, the **Start again** and **Back to home** buttons now wrap onto two lines when they do not fit side by side. Before, "Back to home" was cut off.

A new backend test covers this path (`test_missing_key_keeps_reviews_and_fails_with_a_clear_message`).

**Demo video.** This phone blocks `adb shell screenrecord`, so `docs/demo.mp4` (3 min 29 s) is made from continuous screenshots stitched into an MP4, with captions. See `docs/VIDEO_SCRIPT.md`.

## Bugs found and fixed in this round

1. **Crash after process death.** On restore, the Android 12+ system splash has no icon view, so `SplashScreenViewProvider.iconView` threw a NullPointerException in the exit animation. The animation now falls back to a plain fade.
2. **Phones could not reach the backend.** `gradle.properties` still forced `http://10.0.2.2:8000/` (the emulator address). The default is now `http://localhost:8000/`, which works on a phone or an emulator after `adb reverse tcp:8000 tcp:8000`.
3. **No App Store reviews were collected.** Apple's JSON review feed now returns no entries, and Apple returns an empty feed to non-browser user agents. The backend now reads the Atom XML feed with a browser-compatible user agent; a new backend test covers the parser.
4. **The button shine stretched its container to full screen**, which pushed the language screen's Continue button to the top. The shine view no longer drives its parent's size.
5. **Progress card text clipped.** The store and country line now stays on one line.
6. **Settings: the Sift Pro row had no spoken label of its own.** It now has one, like the Language row.

## Not tested, and why

- **Real AI output** (actual model-written themes, backlog reasoning, replies and competitor research). This needs real `NEBIUS_API_KEY` and `TAVILY_API_KEY` values in `backend/.env`.
- **Airplane mode, forced rotation and "Remove animations".** This phone blocks changing those system settings over adb (WRITE_SETTINGS / WRITE_SECURE_SETTINGS are denied). The code paths exist: every animation checks the system animator scale, and the screens use ViewModels and saved state for rotation. They need a manual check.
- **TalkBack itself and very large font sizes.** Labels were checked through the accessibility tree, but TalkBack was not run end to end, and this phone blocks changing the font scale over adb. Screens use content descriptions, headings and sp-based text.

## Known limitations

- Apple's public review feed is inconsistent. Some apps (for example Notion in the US) currently return no reviews at all, and others return one page of about 50. Sift then shows "No reviews yet" or analyses what was returned.
- Sift Pro is a preview: there is no billing and no payment details are collected.
- The release APK needs your own signing key; the committed APK is a debug build.
