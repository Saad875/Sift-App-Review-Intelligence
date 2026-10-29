# Sift in 5 minutes: a guide for judges

Sift reads an app's reviews and turns them into a ranked product backlog, with evidence, reply drafts and competitor research. This page shows how to try it quickly and what to expect with and without the AI key.

## What you need

- **The app:** an Android phone (Android 7.0 or later) with USB debugging, or an emulator. Install with `adb install -r dist/Sift-debug.apk`.
- **The backend:** Python 3.11+ on a computer:
  ```bat
  cd backend
  python -m venv .venv
  .venv\Scripts\pip install -r requirements.txt
  copy .env.example .env
  .venv\Scripts\python manage.py migrate
  .venv\Scripts\python manage.py runserver 0.0.0.0:8000
  ```
- **The connection:** `adb reverse tcp:8000 tcp:8000`, so the app reaches the backend at `http://localhost:8000/`. The phone also needs Wi-Fi or mobile data.
- **Keys, for the AI part.** Put them in `backend/.env`, then restart the server:
  - `NEBIUS_API_KEY`: create one at <https://tokenfactory.nebius.com>;
  - `MODEL_FAST` and `MODEL_REASONING`: Token Factory model ids (defaults are provided);
  - `TAVILY_API_KEY`: for competitor research.

To check which keys the server has, open <http://localhost:8000/api/v1/health/>. `configured.nebius` and `configured.tavily` show `true` or `false`; the keys themselves are never shown.

## The 5-minute tour

| Minute | Do this | Look for |
|---|---|---|
| 0:00 | Open Sift | An animated logo splash. Pick a language from 15, each shown in its own script; the app switches instantly. Swipe through the three welcome pages. |
| 0:45 | Sift Pro preview → **Maybe later** | A plan comparison. It is only a preview: nothing is charged and no payment details are asked for. |
| 1:00 | Home → search **whatsapp** → tap the result | Live App Store results with icons and ratings |
| 1:30 | Setup: add the competitor **Telegram** → **Start analysis** | Country card, competitor chips, then a live progress timeline |
| 2:30 | The report (needs the AI key) | **Overview** (charts draw in), **Backlog** (tap an item, then **Copy as ticket**), **Reviews** (filter by Negative, open one, **Draft reply**), **Competitors** (with sources), and the share icon |
| 3:30 | Home → **Import CSV** → `test-data/sample_reviews.csv` (copy it to the phone first) | Shows that Google Play reviews work through a Play Console export, with no scraping |
| 4:15 | Settings → Language → **العربية** → back → switch back to English | The full right-to-left layout |
| 4:45 | Settings → About | How the AI is used, the privacy note and the open-source licence |

## What works without the AI key, and what needs it

| Feature | Without `NEBIUS_API_KEY` | With it |
|---|---|---|
| Onboarding, 15 languages, RTL, themes, Settings | ✅ Works | ✅ |
| App Store search | ✅ Live from Apple | ✅ |
| Collecting reviews (App Store feed or CSV) | ✅ Reviews are fetched and stored | ✅ |
| Classification, themes, ranked backlog | ❌ The analysis stops after collecting, with: *"The server's AI key is not configured, so this analysis cannot run. Add NEBIUS_API_KEY to backend/.env and restart the server."* | ✅ |
| Reply drafts | ❌ Same clear message | ✅ |
| Competitor research | ❌ Needs both the AI model and `TAVILY_API_KEY` | ✅ with Tavily |
| Errors (server stopped, offline) | ✅ Friendly screens with **Try again** | ✅ |

There is deliberately **no demo mode and no fake data**: the report only ever shows real reviews and real model output.

## Things worth knowing

- **Apple's public review feed has gaps.** WhatsApp in the US returns reviews. Some apps (for example Notion in the US) currently return none, and Sift then says "No reviews yet". If that happens, try another app or country, or use the CSV import.
- **The app holds no keys.** Every AI and search call goes through the backend.
- **The AI runs on Nebius Token Factory** (OpenAI-compatible API), with a fast model for classification and a reasoning model for themes, the backlog, replies and Tavily-powered competitor research. See the [README](../README.md#the-ai-pipeline-step-by-step).
- **More detail:** the [QA report](QA_REPORT.md) lists every check run on a real phone, and the [user guide](USER_GUIDE.md) explains every screen.

## If something goes wrong

| You see | Fix |
|---|---|
| "Can't reach the Sift server" | Start the backend and run `adb reverse tcp:8000 tcp:8000` again |
| "You are offline" | Turn on the phone's Wi-Fi or mobile data |
| "The server's AI key is not configured" | Add `NEBIUS_API_KEY` to `backend/.env` and restart the backend |
| "No reviews yet" | Apple's feed is empty for that app and country; try another one, or import a CSV |
