"""Generate the Android app's bundled sample analysis.

The app, its developer, competitors and reviews are fictional. Statistics are
computed from the reviews so every number in the sample report is consistent.
Run from the repository root:

    python tools/generate_demo.py
"""
from __future__ import annotations

import json
from collections import Counter
from pathlib import Path

OUTPUT = Path(__file__).resolve().parent.parent / "android/app/src/main/assets/demo/demo_analysis.json"

THEMES = [
    {
        "title": "Sync loses recent edits",
        "description": "Notes edited on one device revert to an older version after syncing to another device.",
    },
    {
        "title": "Slow start-up on large libraries",
        "description": "Users with thousands of notes wait several seconds for the app to open or search.",
    },
    {
        "title": "Subscription price increase",
        "description": "Long-time users are unhappy that features moved behind the new Pro plan.",
    },
    {
        "title": "Requests for handwriting support",
        "description": "Tablet users ask for pencil input and handwritten note search.",
    },
    {
        "title": "Dark mode contrast",
        "description": "Grey text on the dark theme is hard to read, especially in code blocks.",
    },
    {
        "title": "Clean, focused writing experience",
        "description": "Many users praise the minimal editor and fast capture widget.",
    },
]

# (theme index or None, rating, sentiment, category, severity, title, text, author, version, date)
REVIEWS = [
    (0, 1, "negative", "bug", 5, "Lost an hour of work", "I edited my meeting notes on the iPad, opened the phone and everything I wrote was gone. Sync rolled it back.", "maria_k", "4.2.1", "2026-03-11"),
    (0, 2, "negative", "bug", 5, "Sync overwrites changes", "Twice this week my latest edits were replaced by an older copy from my laptop. I no longer trust it.", "devon.r", "4.2.1", "2026-03-10"),
    (0, 1, "negative", "bug", 5, "Data loss", "Notes revert after syncing. Support told me to reinstall, which did not help.", "anika", "4.2.0", "2026-03-08"),
    (0, 2, "negative", "bug", 4, "Conflicts are silent", "When two devices edit the same note there is no conflict warning, one version just disappears.", "theo_writes", "4.2.1", "2026-03-07"),
    (0, 3, "neutral", "bug", 3, "Mostly fine but sync is shaky", "Love the editor. Sync between phone and Mac occasionally drops a paragraph though.", "jules", "4.2.0", "2026-03-05"),
    (0, 1, "negative", "bug", 5, "Please fix sync", "Lost my grocery list and a draft essay. Rolling back edits is the one thing a notes app cannot do.", "sam_okafor", "4.2.1", "2026-03-04"),
    (0, 2, "negative", "bug", 4, "Edits disappear", "Offline edits on the train vanish once I reconnect.", "lena.b", "4.1.9", "2026-02-27"),
    (0, 2, "negative", "bug", 4, "Sync bug since 4.2", "Ever since the 4.2 update edits from my tablet keep getting reverted.", "pkumar", "4.2.0", "2026-02-25"),
    (1, 2, "negative", "performance", 3, "Takes forever to open", "With about 6,000 notes the app takes 8 seconds to open. It used to be instant.", "harriet", "4.2.1", "2026-03-09"),
    (1, 3, "neutral", "performance", 3, "Search is slow", "Search across my archive freezes for a few seconds before results show up.", "marco.v", "4.2.0", "2026-03-06"),
    (1, 2, "negative", "performance", 3, "Laggy on older phone", "Scrolling the note list stutters badly on my three year old phone.", "yusuf", "4.1.9", "2026-03-02"),
    (1, 2, "negative", "performance", 4, "Start-up got slower", "Opening the app now shows a blank screen for several seconds before my notes load.", "claire_d", "4.2.1", "2026-02-28"),
    (1, 3, "neutral", "performance", 2, "Good app, slow with big library", "Great for small notebooks, but my research vault makes it crawl.", "ivan.p", "4.2.0", "2026-02-21"),
    (2, 1, "negative", "pricing", 4, "Price doubled", "Features I used for years are now Pro only and the price went up. Feels like a bait and switch.", "rob_h", "4.2.0", "2026-03-03"),
    (2, 2, "negative", "pricing", 3, "Too expensive now", "The new Pro plan costs more than competitors with fewer features.", "nadia", "4.2.1", "2026-03-01"),
    (2, 2, "negative", "pricing", 3, "Paywalled export", "Exporting to PDF now needs Pro. That used to be free.", "kwame", "4.2.0", "2026-02-26"),
    (2, 3, "neutral", "pricing", 2, "Fair but pricey", "I understand they need revenue, but a family plan or student discount would help.", "emma.s", "4.2.1", "2026-02-20"),
    (2, 1, "negative", "pricing", 4, "Cancelled my subscription", "Moving tags behind the paywall was the last straw.", "georgeb", "4.2.0", "2026-02-18"),
    (3, 4, "neutral", "feature_request", 2, "Add pencil support", "Would be perfect on iPad if I could write with the pencil and search my handwriting.", "aiko", "4.2.1", "2026-03-09"),
    (3, 3, "neutral", "feature_request", 2, "Handwriting please", "Everything is great except there is no way to sketch or handwrite.", "tomasz", "4.2.0", "2026-03-04"),
    (3, 4, "positive", "feature_request", 1, "Great app, one wish", "Love it. Handwritten notes would make this my only notes app.", "priya.n", "4.2.1", "2026-02-24"),
    (3, 3, "neutral", "feature_request", 2, "Missing drawing tools", "Need simple drawing for diagrams in lecture notes.", "ben_lee", "4.1.9", "2026-02-19"),
    (4, 3, "neutral", "ui_ux", 2, "Dark mode hard to read", "Grey text on dark grey is very low contrast. Code blocks are almost invisible.", "omar", "4.2.1", "2026-03-08"),
    (4, 2, "negative", "ui_ux", 3, "Contrast issues", "I have low vision and the dark theme is unreadable for me.", "rachel.m", "4.2.0", "2026-03-02"),
    (4, 3, "neutral", "ui_ux", 2, "Needs a high contrast option", "Please add a high contrast theme or let us pick text colors.", "felix", "4.2.1", "2026-02-23"),
    (5, 5, "positive", "praise", 1, "Best minimal notes app", "Clean, fast and gets out of the way. The quick capture widget is brilliant.", "sofia_r", "4.2.1", "2026-03-11"),
    (5, 5, "positive", "praise", 1, "Love the editor", "Markdown shortcuts feel natural and the typography is beautiful.", "liam.c", "4.2.1", "2026-03-10"),
    (5, 4, "positive", "praise", 1, "Calm and focused", "No clutter, no distractions. Exactly what I wanted for journaling.", "hana", "4.2.0", "2026-03-07"),
    (5, 5, "positive", "praise", 1, "Perfect for quick capture", "The widget lets me jot ideas in two seconds. Five stars.", "diego", "4.2.1", "2026-03-06"),
    (5, 5, "positive", "praise", 1, "Beautiful design", "The nicest looking notes app I have used. Worth every penny.", "chloe.w", "4.2.0", "2026-03-03"),
    (5, 4, "positive", "praise", 1, "Simple and reliable on one device", "I only use it on my phone and it has been perfect.", "arjun", "4.1.9", "2026-02-28"),
    (5, 5, "positive", "praise", 1, "My daily driver", "Been using it for three years. Still the best writing experience.", "zoe_t", "4.2.1", "2026-02-22"),
    (None, 4, "positive", "other", 1, "Good support", "Support replied within a day and helped me move my notes.", "malik", "4.2.0", "2026-03-05"),
    (None, 3, "neutral", "account", 2, "Login loop", "Signing in with my email sometimes loops back to the login screen.", "petra", "4.2.1", "2026-03-01"),
    (None, 2, "negative", "ads", 3, "Upsell banners", "The free tier now shows a Pro banner on every screen.", "noah.g", "4.2.0", "2026-02-27"),
    (None, 4, "positive", "praise", 1, "Great widgets", "Home screen widgets are genuinely useful.", "ines", "4.2.1", "2026-02-25"),
    (None, 3, "neutral", "other", 2, "Okay", "Does the job. Nothing special.", "kai", "4.1.9", "2026-02-17"),
    (None, 5, "positive", "praise", 1, "Fantastic", "Simple, elegant and fast.", "ana.l", "4.2.1", "2026-02-16"),
    (None, 2, "negative", "account", 3, "Cannot delete account", "There is no way to delete my account from inside the app.", "frank_o", "4.2.0", "2026-02-15"),
    (None, 4, "positive", "feature_request", 1, "Add tags to widget", "Would love to pick a tag in the quick capture widget.", "mei", "4.2.1", "2026-02-14"),
]

BACKLOG = [
    {
        "title": "Fix sync overwriting recent edits",
        "type": "bug",
        "priority_score": 94,
        "theme": 0,
        "effort": "L",
        "reasoning": "The most frequent complaint with the highest severity. Data loss drives one-star reviews and cancellations, and it began with release 4.2.",
        "user_impact": "Users lose work when switching devices and stop trusting the app with important notes.",
        "suggested_fix": "Add per-paragraph version vectors, keep both versions on conflict and show a merge prompt. Add an automated multi-device sync test to the release checklist.",
    },
    {
        "title": "Speed up start-up and search for large libraries",
        "type": "bug",
        "priority_score": 78,
        "theme": 1,
        "effort": "M",
        "reasoning": "Second largest negative theme. Power users with big libraries are the most likely to pay for Pro.",
        "user_impact": "Opening the app or searching takes several seconds once a library passes a few thousand notes.",
        "suggested_fix": "Load the note list lazily, move the search index to a background full-text index and show cached results first.",
    },
    {
        "title": "Revisit what the Pro plan includes",
        "type": "feature",
        "priority_score": 71,
        "theme": 2,
        "effort": "S",
        "reasoning": "Pricing reviews are almost entirely negative and mention cancelling. Moving export and tags behind the paywall caused most of the anger.",
        "user_impact": "Long-time users feel features were taken away and are leaving for cheaper alternatives.",
        "suggested_fix": "Return PDF export and tags to the free tier, and add a student discount and family plan.",
    },
    {
        "title": "Add a high contrast dark theme",
        "type": "bug",
        "priority_score": 58,
        "theme": 4,
        "effort": "S",
        "reasoning": "Accessibility issue affecting readability for low-vision users. Small effort for a clear improvement.",
        "user_impact": "Some users cannot read notes or code blocks in the dark theme.",
        "suggested_fix": "Raise body text contrast to at least 4.5:1, restyle code blocks and add a high contrast option in Settings.",
    },
    {
        "title": "Support pencil handwriting on tablets",
        "type": "feature",
        "priority_score": 52,
        "theme": 3,
        "effort": "L",
        "reasoning": "Frequent, mostly positive requests from tablet users. It is a growth opportunity rather than a source of churn.",
        "user_impact": "Tablet users keep a second app for handwritten notes and sketches.",
        "suggested_fix": "Add an ink canvas block with pencil support, then add handwriting recognition to search.",
    },
    {
        "title": "Allow account deletion in the app",
        "type": "feature",
        "priority_score": 44,
        "theme": None,
        "effort": "S",
        "reasoning": "Few reviews, but app store policies require in-app account deletion, so the risk is high.",
        "user_impact": "Users who want to leave cannot remove their data without contacting support.",
        "suggested_fix": "Add Delete account to Settings with a confirmation step and an export prompt.",
        "evidence": ["There is no way to delete my account from inside the app."],
    },
    {
        "title": "Reduce upsell banners on the free tier",
        "type": "feature",
        "priority_score": 33,
        "theme": None,
        "effort": "S",
        "reasoning": "A small but growing complaint that adds to the pricing frustration.",
        "user_impact": "Free users see a Pro banner on every screen, which makes the app feel cluttered.",
        "suggested_fix": "Show the upgrade prompt only when a Pro feature is tapped, and at most once a week otherwise.",
        "evidence": ["The free tier now shows a Pro banner on every screen."],
    },
]

COMPETITORS = [
    {
        "name": "Lanternbook",
        "summary": "A fictional cross-platform notes app in this sample, known for reliable sync and a generous free tier.",
        "strengths": [
            "Conflict-free sync with visible version history",
            "Free tier includes export and tags",
            "Fast search on large libraries",
        ],
        "weaknesses": [
            "Cluttered interface with many toolbars",
            "No quick capture widget",
        ],
        "opportunities": [
            "Match its sync reliability and keep the cleaner editor as the differentiator",
            "Market the quick capture widget, which it lacks",
        ],
        "sources": [
            {"title": "Sample source: Lanternbook feature overview", "url": "https://example.com/sample/lanternbook-features"},
            {"title": "Sample source: notes app comparison", "url": "https://example.com/sample/notes-app-comparison"},
        ],
        "error": "",
    },
    {
        "name": "Quillstack",
        "summary": "A fictional tablet-first notes app in this sample, popular for handwriting and sketching.",
        "strengths": [
            "Excellent pencil input with handwriting search",
            "Student pricing",
        ],
        "weaknesses": [
            "Weak Markdown support",
            "Slow on phones",
        ],
        "opportunities": [
            "Offer a lightweight ink block to win tablet users without losing the writing focus",
            "Introduce student pricing to answer price complaints",
        ],
        "sources": [
            {"title": "Sample source: Quillstack pricing page", "url": "https://example.com/sample/quillstack-pricing"},
        ],
        "error": "",
    },
]

FAST_MODEL = "nvidia/NVIDIA-Nemotron-Nano-9B-v2"
REASONING_MODEL = "nvidia/Llama-3_3-Nemotron-Super-49B-v1_5"


def quotes_for(theme_index: int, limit: int = 3) -> list[str]:
    return [r[6] for r in REVIEWS if r[0] == theme_index][:limit]


def build() -> dict:
    reviews = []
    for index, (theme, rating, sentiment, category, severity, title, text, author, version, date) in enumerate(REVIEWS, start=1):
        reviews.append(
            {
                "id": index,
                "author": author,
                "title": title,
                "text": text,
                "rating": rating,
                "review_date": f"{date}T09:00:00Z",
                "app_version": version,
                "sentiment": sentiment,
                "category": category,
                "severity": severity,
                "summary": text.split(". ")[0].rstrip(".") + ".",
                "theme_title": THEMES[theme]["title"] if theme is not None else None,
            }
        )

    themes = []
    for position, theme in enumerate(THEMES):
        members = [r for r in REVIEWS if r[0] == position]
        negative = sum(1 for r in members if r[2] == "negative")
        themes.append(
            {
                "id": position + 1,
                "position": position,
                "title": theme["title"],
                "description": theme["description"],
                "review_count": len(members),
                "negative_share": round(negative / len(members), 2),
                "quotes": quotes_for(position),
            }
        )

    backlog = []
    for rank, item in enumerate(BACKLOG, start=1):
        theme = item["theme"]
        evidence = quotes_for(theme, 2) if theme is not None else item["evidence"]
        assert all(any(r[6] == quote for r in REVIEWS) for quote in evidence), item["title"]
        backlog.append(
            {
                "id": rank,
                "rank": rank,
                "title": item["title"],
                "type": item["type"],
                "priority_score": item["priority_score"],
                "reasoning": item["reasoning"],
                "user_impact": item["user_impact"],
                "suggested_fix": item["suggested_fix"],
                "effort": item["effort"],
                "theme_title": THEMES[theme]["title"] if theme is not None else None,
                "evidence_quotes": evidence,
            }
        )

    competitors = [dict(c, id=i) for i, c in enumerate(COMPETITORS, start=1)]

    ratings = [r[1] for r in REVIEWS]
    sentiment = Counter(r[2] for r in REVIEWS)
    categories = Counter(r[3] for r in REVIEWS)
    distribution = Counter(ratings)
    analysis = {
        "id": "sample-demo-analysis",
        "app_name": "Tidepool Notes",
        "app_id": "sample",
        "developer": "Harbor Labs (sample)",
        "icon_url": "",
        "source": "demo",
        "country": "us",
        "competitors": [c["name"] for c in COMPETITORS],
        "status": "completed",
        "current_step": "done",
        "progress": 100,
        "error": "",
        "warnings": [],
        "created_at": "2026-03-12T10:00:00Z",
        "steps": [
            {"key": "fetching", "state": "done", "model_role": None, "model": None},
            {"key": "classifying", "state": "done", "model_role": "fast", "model": FAST_MODEL},
            {"key": "clustering", "state": "done", "model_role": "reasoning", "model": REASONING_MODEL},
            {"key": "prioritizing", "state": "done", "model_role": "reasoning", "model": REASONING_MODEL},
            {"key": "researching", "state": "done", "model_role": "reasoning", "model": REASONING_MODEL},
        ],
        "stats": {
            "total_reviews": len(REVIEWS),
            "classified_reviews": len(REVIEWS),
            "average_rating": round(sum(ratings) / len(ratings), 2),
            "sentiment": {k: sentiment.get(k, 0) for k in ("positive", "neutral", "negative")},
            "categories": {
                k: categories.get(k, 0)
                for k in ("bug", "performance", "ui_ux", "feature_request", "pricing", "ads", "account", "praise", "other")
            },
            "rating_distribution": {str(s): distribution.get(s, 0) for s in range(1, 6)},
            "theme_count": len(themes),
            "backlog_count": len(backlog),
            "competitor_count": len(competitors),
        },
    }
    return {"analysis": analysis, "themes": themes, "backlog": backlog, "competitors": competitors, "reviews": reviews}


if __name__ == "__main__":
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(json.dumps(build(), indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"Wrote {OUTPUT}")
