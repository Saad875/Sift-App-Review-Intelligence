# Sift

Sift turns app reviews into a ranked product backlog. It pulls App Store reviews (or a CSV export from Google Play Console), classifies every review with NVIDIA Nemotron, groups complaints into themes, ranks what to fix or build next, drafts developer replies and researches competitors on the web.

- `android/`: the Android app (Kotlin, XML layouts with ViewBinding, single activity, Clean Architecture)
- `backend/`: the Django backend that holds every API key and talks to Nebius Token Factory and Tavily
- `tools/`: static checks, the demo data generator and the translation generator

## Run the backend

```bash
cd backend
python -m venv .venv
.venv\Scripts\activate            # Windows; use source .venv/bin/activate elsewhere
pip install -r requirements.txt
copy .env.example .env            # then fill in NEBIUS_API_KEY and TAVILY_API_KEY
python manage.py migrate
python manage.py runserver 0.0.0.0:8000
```

`GET /api/v1/health/` reports whether Nebius and Tavily are configured. App Store search works without keys; analyses need `NEBIUS_API_KEY`, and competitor research needs `TAVILY_API_KEY`.

## Run the app on a phone

1. Open `android/` in Android Studio, or run `gradlew installDebug` with a device connected over USB debugging.
2. Forward the backend port to the phone: `adb reverse tcp:8000 tcp:8000`.
3. In the app, open Settings, set the backend URL to `http://localhost:8000/` and tap Test connection.

The emulator can use the default `http://10.0.2.2:8000/` instead.

## First launch

Onboarding runs once, on the first launch:

1. **Splash**: the system splash zooms out into an animated logo reveal.
2. **Language**: choose from 15 languages, each shown in its own script. The choice applies immediately.
3. **Welcome**: three swipeable pages (collect, understand, act) with parallax artwork and a morphing page indicator.
4. **Sift Pro preview**: a Free versus Pro comparison and two plan cards.

Settings can reopen the language picker and the Sift Pro screen at any time.

### Sift Pro is a preview

There is no billing. The plans and prices are display copy only. "Continue with Pro" just shows a "coming soon" message, no payment is taken and no payment details are requested.

## Languages

English (default), Simplified Chinese, Hindi, Spanish, French, Arabic, Bengali, Portuguese, Russian, Urdu, Indonesian, German, Japanese, Swahili and Turkish. Arabic and Urdu use a fully mirrored right-to-left layout.

The language is stored per app through AppCompat (`AppCompatDelegate.setApplicationLocales`). On Android 13 and later it also appears in the system's per-app language settings (`res/xml/locales_config.xml`).

Translations are generated from the tables in `tools/i18n`. After adding or changing English strings, update each table and regenerate:

```bash
cd tools/i18n
python -I gen.py de fr es pt ru tr id_ sw hi bn ar ur zh ja
```

The generator escapes text for Android resources, fails if any key is missing, and checks that every placeholder (`%1$s`, `%2$d` and so on) matches the English string. Brand names (Sift, NVIDIA Nemotron, Nebius Token Factory, Tavily) stay untranslated.

## Design

- **Type**: Manrope (SIL Open Font License, see `android/third_party/manrope/OFL.txt`). Scripts it does not cover fall back to the system Noto fonts.
- **Look**: a deep navy gradient backdrop, translucent glass cards and NVIDIA green (#76B900) accents.
- **Motion**: drifting ambient glows with scroll parallax, staggered entrances, count-up numbers, chart draw-ins, shared-axis and fade-through screen transitions, and springy press feedback on every button. Transitions and press feedback run for 200 to 350 ms.
- **Remove animations**: every animation respects the system setting.

## Tests

```bash
cd android && gradlew testDebugUnitTest
cd backend && python manage.py test analyses
```
