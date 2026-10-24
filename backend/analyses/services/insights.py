"""Steps 3 and 4: theme clustering and backlog ranking with the reasoning model.

The model refers to reviews by id. Counts, negative shares and quotes are
then computed from the stored reviews, so every number and quote shown in the
app is grounded in real data rather than generated text.
"""
from __future__ import annotations

import json
from dataclasses import dataclass, field

from . import schemas
from .llm import REASONING, LLMClient
from .validation import ModelOutputError

MAX_QUOTE_CHARS = 280


@dataclass(frozen=True)
class SummarisedReview:
    id: int
    summary: str
    sentiment: str
    category: str
    severity: int
    rating: int | None
    text: str


@dataclass
class ThemeResult:
    title: str
    description: str
    review_ids: list[int]
    review_count: int
    negative_share: float
    quotes: list[str]


@dataclass
class BacklogResult:
    title: str
    item_type: str
    priority_score: int
    reasoning: str
    user_impact: str
    suggested_fix: str
    effort: str
    theme_index: int | None
    evidence_quotes: list[str] = field(default_factory=list)


def quote(text: str) -> str:
    """Shorten review text to a readable quote."""
    clean = " ".join(text.split())
    if len(clean) <= MAX_QUOTE_CHARS:
        return clean
    return clean[:MAX_QUOTE_CHARS].rsplit(" ", 1)[0] + "…"


THEMES_SYSTEM = """You are a senior product analyst. You receive one-line summaries of classified app reviews.
Group them into 5 to 10 distinct themes that a product team can act on (fewer only if there are very few reviews).
Each theme needs:
- title: short and specific, for example "Crashes when uploading photos"
- description: two sentences explaining what users experience and why it matters
- review_ids: ids of every review that belongs to the theme (a review belongs to at most one theme)
- representative_review_ids: up to 3 ids whose reviews best illustrate the theme
Return {"themes": [ ... ]} ordered from most to least important."""


def build_themes(llm: LLMClient, reviews: list[SummarisedReview]) -> list[ThemeResult]:
    """Cluster review summaries into grounded themes."""
    by_id = {review.id: review for review in reviews}
    lines = [
        {"id": r.id, "s": r.sentiment, "c": r.category, "sev": r.severity, "summary": r.summary}
        for r in reviews
    ]

    def check(data: dict) -> dict:
        if not any(set(t["review_ids"]) & by_id.keys() for t in data["themes"]):
            raise ModelOutputError("No theme references a known review id.")
        return data

    data = llm.chat_json(
        REASONING,
        THEMES_SYSTEM,
        f"There are {len(reviews)} reviews.\n" + json.dumps(lines, ensure_ascii=False),
        schemas.THEMES,
        temperature=0.3,
        max_tokens=6000,
        item_validator=check,
    )

    assigned: set[int] = set()
    themes: list[ThemeResult] = []
    for raw in data["themes"]:
        ids = [i for i in dict.fromkeys(raw["review_ids"]) if i in by_id and i not in assigned]
        if not ids:
            continue
        assigned.update(ids)
        negatives = sum(1 for i in ids if by_id[i].sentiment == "negative")
        quote_ids = [i for i in raw["representative_review_ids"] if i in by_id][:3]
        for candidate in sorted(ids, key=lambda i: -by_id[i].severity):
            if len(quote_ids) >= 3:
                break
            if candidate not in quote_ids:
                quote_ids.append(candidate)
        themes.append(
            ThemeResult(
                title=raw["title"].strip(),
                description=raw["description"].strip(),
                review_ids=ids,
                review_count=len(ids),
                negative_share=round(negatives / len(ids), 3),
                quotes=[quote(by_id[i].text) for i in quote_ids],
            )
        )
    if not themes:
        raise ModelOutputError("The model produced no usable themes.")
    return themes


BACKLOG_SYSTEM = """You are a head of product turning app review themes into a prioritised backlog.
Create 5 to 12 backlog items (fewer if the feedback is thin). Each item needs:
- title: an actionable ticket title
- type: "bug" or "feature"
- priority_score: integer 0 to 100. Weigh how many users are affected, severity, negative share,
  impact on ratings and retention, and how cheap the fix is
- reasoning: why it ranks where it does, citing the numbers you were given
- user_impact: who is affected and how
- suggested_fix: a concrete implementation suggestion
- effort: "S", "M" or "L"
- theme_id: the id of the theme it addresses, or null
- evidence_review_ids: 1 to 3 review ids that support the item
Return {"items": [ ... ]}."""


def build_backlog(
    llm: LLMClient,
    themes: list[ThemeResult],
    reviews: list[SummarisedReview],
    category_counts: dict[str, int],
) -> list[BacklogResult]:
    """Rank what to fix or build next, grounded in themes and real quotes."""
    by_id = {review.id: review for review in reviews}
    theme_payload = [
        {
            "theme_id": index,
            "title": theme.title,
            "description": theme.description,
            "review_count": theme.review_count,
            "negative_share": theme.negative_share,
            "sample_review_ids": theme.review_ids[:8],
        }
        for index, theme in enumerate(themes)
    ]
    severe = sorted(reviews, key=lambda r: (-r.severity, r.id))[:40]
    context = {
        "total_reviews": len(reviews),
        "category_counts": category_counts,
        "themes": theme_payload,
        "most_severe_reviews": [
            {"id": r.id, "category": r.category, "severity": r.severity, "summary": r.summary}
            for r in severe
        ],
    }
    data = llm.chat_json(
        REASONING,
        BACKLOG_SYSTEM,
        json.dumps(context, ensure_ascii=False),
        schemas.BACKLOG,
        temperature=0.3,
        max_tokens=6000,
    )

    items: list[BacklogResult] = []
    for raw in data["items"]:
        theme_index = raw["theme_id"]
        if theme_index is not None and not 0 <= theme_index < len(themes):
            theme_index = None
        evidence_ids = [i for i in dict.fromkeys(raw["evidence_review_ids"]) if i in by_id][:3]
        quotes = [quote(by_id[i].text) for i in evidence_ids]
        if not quotes and theme_index is not None:
            quotes = themes[theme_index].quotes[:2]
        items.append(
            BacklogResult(
                title=raw["title"].strip(),
                item_type=raw["type"],
                priority_score=raw["priority_score"],
                reasoning=raw["reasoning"].strip(),
                user_impact=raw["user_impact"].strip(),
                suggested_fix=raw["suggested_fix"].strip(),
                effort=raw["effort"],
                theme_index=theme_index,
                evidence_quotes=quotes,
            )
        )
    items.sort(key=lambda item: -item.priority_score)
    return items
