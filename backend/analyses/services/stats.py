"""Summary statistics computed from stored reviews."""
from __future__ import annotations

from collections import Counter
from typing import Any

from ..models import Analysis
from .schemas import CATEGORIES, SENTIMENTS


def summary_stats(analysis: Analysis) -> dict[str, Any]:
    """Counts and averages for the Overview screen and the Markdown report."""
    rows = list(analysis.reviews.values_list("rating", "sentiment", "category", "severity"))
    ratings = [r[0] for r in rows if r[0]]
    sentiment = Counter(r[1] for r in rows if r[1])
    category = Counter(r[2] for r in rows if r[2])
    distribution = Counter(ratings)
    classified = sum(1 for r in rows if r[1] and r[2])
    severities = [r[3] for r in rows if r[3]]
    return {
        "total_reviews": len(rows),
        "classified_reviews": classified,
        "average_rating": round(sum(ratings) / len(ratings), 2) if ratings else None,
        "average_severity": round(sum(severities) / len(severities), 2) if severities else None,
        "sentiment": {key: sentiment.get(key, 0) for key in SENTIMENTS},
        "categories": {key: category.get(key, 0) for key in CATEGORIES},
        "rating_distribution": {str(star): distribution.get(star, 0) for star in range(1, 6)},
        "theme_count": analysis.themes.count(),
        "backlog_count": analysis.backlog.count(),
        "competitor_count": analysis.competitor_insights.count(),
    }
