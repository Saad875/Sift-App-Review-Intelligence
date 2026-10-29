# Sift demo video script

**File:** `docs/demo.mp4`, recorded on an OPPO CPH2333 (Android 13) against the local backend.

**How it was recorded:**
- This phone blocks `adb shell screenrecord`, so the clip was made from continuous screenshots (about 2–3 per second) stitched into an H.264 MP4. It plays like a fast slideshow, not 60 fps.
- For a smooth recording, use Android Studio's **Logcat → Screen Record** on a phone that allows it, or scrcpy (`scrcpy --record demo.mp4`).
- The server had a Tavily key but **no Nebius key**, so the video stops honestly at the "AI key is not configured" message. No results are faked.
- Re-record the report part once `NEBIUS_API_KEY` is set. The segment list below marks what to add.

## Segments

| # | Time | Screen | What happens |
|---|---|---|---|
| 1 | 0:00–0:04 | Launcher → splash | Tap Sift; the logo animates in |
| 2 | 0:04–0:12 | Language picker | 15 languages in their own scripts; scroll, keep English, **Continue** |
| 3 | 0:12–0:20 | Welcome pages | Collect → understand → act; **Next**, **Get started** |
| 4 | 0:20–0:28 | Sift Pro preview | Plan comparison, Yearly selected; **Maybe later** (preview only, no billing) |
| 5 | 0:28–0:34 | Home | Animated logo, search, Import CSV, empty Recent analyses |
| 6 | 0:34–0:42 | Search | Type "whatsapp"; live App Store results |
| 7 | 0:42–0:52 | Setup | Add competitor "Telegram"; **Start analysis** |
| 8 | 0:52–1:08 | Progress | Reviews are collected, then the clear message: "The server's AI key is not configured…" |
| 9 | 1:08–1:20 | CSV import | Import CSV → `sample_reviews.csv` → setup shows "CSV file" → Start analysis → same clear message |
| 10 | 1:20–1:40 | Settings | Language → العربية: the whole app mirrors right to left. Back to English. |
| 11 | 1:40–1:50 | About | AI-powered insights, privacy note, licence |
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
