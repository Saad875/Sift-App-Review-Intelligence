# Sift user guide

Sift turns an app's reviews into a ranked list of what to fix or build next. Here is how to set it up and use every screen.

## 1. Before you start (about 5 minutes)

You need the Sift backend running on a computer, and the app on an Android phone (Android 7.0 or later) or an emulator.

1. **Start the backend** (see the README quick start for full commands):
   - In VS Code, open the project folder, then **Terminal → Run Task… → Run Sift backend**.
   - Or from a terminal: `cd backend`, then `.venv\Scripts\python manage.py runserver 0.0.0.0:8000`.
2. **Connect the phone** with USB debugging turned on, then run:
   ```
   adb reverse tcp:8000 tcp:8000
   ```
   This lets the app reach the backend at `http://localhost:8000/`.
3. **Install the app:** `adb install dist/Sift-debug.apk`, or press Run in Android Studio.
4. **API keys:** for real AI analyses, put your keys in `backend/.env` (`NEBIUS_API_KEY` for the AI models, `TAVILY_API_KEY` for competitor research), then restart the backend. App Store search works without keys.

## 2. First launch

1. **Splash:** the Sift logo animates in. Nothing to do.
2. **Choose your language:** tap one of the 15 languages; the app switches straight away. Tap **Continue**.
3. **Welcome:** swipe or tap **Next** through three short pages: collect, understand, act. Tap **Get started** (or **Skip**).
4. **Sift Pro preview:** shows what Pro will offer. It is a preview: nothing is charged and no payment details are asked for. Tap **Maybe later** to continue.

This only happens once. You can change the language or see Pro again later from Settings.

## 3. Analyse an App Store app

1. On **Home**, type an app name in **Search any app** and tap the arrow.
2. Tap the app in the results.
3. On **Set up analysis**:
   - **Country:** tap the card to choose which App Store country to read reviews from.
   - **Competitors (optional):** type up to three competitor app names and tap **+** after each. Sift researches them on the web.
   - Tap **Start analysis**.
4. **Progress** shows each step live: collect reviews, classify, find themes, rank the backlog, research competitors. It usually takes one to three minutes. You can leave this screen; the analysis keeps running and appears under **Recent analyses** on Home.

## 4. Analyse your own reviews (CSV)

1. On **Home**, tap **Import CSV**.
2. Pick a CSV file (a Google Play Console export or any file with a review text column; under 5 MB). Rating and date columns are detected automatically.
3. Tap **Start analysis**.

A sample file with fictional reviews is in `test-data/sample_reviews.csv`.

## 5. Read the report

The report has four tabs:

- **Overview:** number of reviews, average rating, share of negative reviews, a sentiment chart, the rating distribution and the top themes with example quotes.
- **Backlog:** a ranked list of what to fix or build, each with a priority score (0 to 100), type (bug or feature) and effort.
  - Tap an item to see why it ranks there, the user impact, a suggested fix and evidence from reviews.
  - **Copy as ticket** copies the item as Markdown for Jira, Linear or GitHub; the button briefly shows **Copied**.
- **Reviews:** every review with its sentiment, category and severity.
  - Tap the chips to filter (for example **Negative** plus **Bugs**); **Clear filters** resets them.
  - Tap a review to open it, choose a tone (Friendly, Formal, Apologetic) and tap **Draft reply**. Edit the text and tap **Copy**.
- **Competitors:** strengths, weaknesses and opportunities for each competitor you added, with links to the sources.

Tap the **share** icon at the top of a report to send the whole report as a Markdown file.

## 6. Settings

Open Settings with the gear icon on Home.

- **Language:** switch between the 15 languages at any time.
- **Sift Pro:** see the Pro preview again.
- **Appearance:** Dark, Light or System theme.
- **Local data:** reports are saved on the phone so they open offline; **Clear local data** removes them after you confirm.
- **About:** how the AI is used, the privacy note, the version and the open-source licence.

## 7. If something goes wrong

| What you see | What to do |
|---|---|
| "Can't reach the Sift server" | Make sure the backend is running and run `adb reverse tcp:8000 tcp:8000` again (it resets when the phone reconnects), then tap **Try again**. |
| "You are offline" | Check the phone's internet connection. Reports you already opened still work. |
| Analysis failed with "NEBIUS_API_KEY is not configured" | Add your key to `backend/.env` and restart the backend. |
| "No reviews yet" | Apple's public feed has no reviews for that app in that country. Try another country or app, or import a CSV. |
| Competitor research skipped or failed | Add `TAVILY_API_KEY` to `backend/.env`. |
