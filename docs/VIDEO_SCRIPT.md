# Sift demo video script

**File:** `docs/demo.mp4`: 3 min 29 s, 540×1250, H.264, about 21 MB.

**How it was recorded:**
- Recorded live on an OPPO CPH2333 (Android 13) against the local backend on 29 October 2026.
- This phone blocks `adb shell screenrecord`, so the clip was made from continuous screenshots (about 2–3 per second), stitched into an MP4 with Windows' built-in encoder. It plays like a fast slideshow, not 60 fps.
- The phone's status bar is cropped out. In the system file picker, other file names and the Google account are blurred.
- Each scene has an on-screen caption.
- The server had a Tavily key but **no Nebius key**. The video therefore shows reviews being collected and then the honest "AI key is not configured" message. No results are faked.
- Re-record the report part once `NEBIUS_API_KEY` is set; the last row below marks what to add.
- For a smooth recording on a phone that allows it, use Android Studio's **Logcat → Screen Record** or `scrcpy --record demo.mp4`.

## Segments

| # | Time | Screen | What happens |
|---|---|---|---|
| 1 | 0:00–0:03 | Title card | Sift, App Review Intelligence |
| 2 | 0:03–0:13 | Splash → language picker | The logo animates in; 15 languages in their own scripts; **Continue** |
| 3 | 0:13–0:22 | Welcome pages | Collect → understand → act; **Next**, **Get started** |
| 4 | 0:22–0:30 | Sift Pro preview | Plan comparison; **Maybe later** (preview only, no billing) |
| 5 | 0:30–0:42 | Home → search | Type "whatsapp"; live App Store results |
| 6 | 0:42–1:01 | Setup | WhatsApp Messenger, United States; add competitor "Telegram"; **Start analysis** |
| 7 | 1:01–1:19 | Progress | "300 unique reviews collected: Done". Then "Classify every review: Failed", with "The server's AI key is not configured… Add NEBIUS_API_KEY to backend/.env and restart the server." |
| 8 | 1:19–2:01 | CSV import | **Import CSV** → `sample_reviews.csv` → setup shows "CSV file" → **Start analysis** → 12 reviews collected, then the same clear message |
| 9 | 2:01–2:57 | Settings | Language → العربية: the whole app mirrors right to left |
| 10 | 2:57–3:12 | Settings | Back to English |
| 11 | 3:12–3:24 | About | AI-powered insights on Nebius Token Factory, Tavily web search, privacy note, licence |
| 12 | 3:24–3:29 | End card | What works without the key, and what the key unlocks |
| *(with key)* | *add* | Report | Overview charts, Backlog → Copy as ticket, Reviews → Draft reply, Competitors with sources, Share |

## 90-second narration (about 220 words)

> **[Splash]** App reviews are the most honest product feedback a team gets, and almost nobody has time to read them. This is Sift.
>
> **[Language picker]** Sift speaks fifteen languages, from English and Chinese to Arabic and Urdu with a full right-to-left layout.
>
> **[Welcome pages]** The idea is simple: collect the reviews, understand what users feel, and act on what matters most.
>
> **[Pro preview]** There's a preview of Sift Pro for teams. It's a preview only, nothing is charged, so I'll tap Maybe later.
>
> **[Search]** Let's search for WhatsApp. Results come live from the App Store, in any country.
>
> **[Setup]** I'll add Telegram as a competitor and start the analysis.
>
> **[Progress]** Sift's backend collects up to three hundred recent reviews. Then models on Nebius Token Factory classify every review, group complaints into themes and rank a backlog with evidence. A tool-calling agent uses Tavily to research competitors on the web. My server has no Nebius key yet, so Sift stops here and says exactly what to fix: no fake results.
>
> **[CSV]** Google Play reviews come in through a Play Console CSV export. No scraping.
>
> **[Settings, Arabic]** Switch to Arabic and the whole app mirrors instantly. Back to English.
>
> **[About]** No API keys live on the phone; every AI call goes through the backend.
>
> **[Close]** Sift: from thousands of reviews to the next thing to build, in minutes.

**When the key is set,** replace the Progress line from "My server has no Nebius key yet…" with:

> A minute later, the report: sentiment and ratings, the top themes with real quotes, a ranked backlog I can copy straight into Jira, reply drafts in three tones, and a competitor summary with sources.
