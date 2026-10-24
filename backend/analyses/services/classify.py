"""Step 2: classify reviews in batches with the fast Nemotron Nano model."""
from __future__ import annotations

import json
import logging
from dataclasses import dataclass
from typing import Iterable, Sequence

from . import schemas
from .llm import FAST, LLMClient, LLMError
from .validation import schema_errors

logger = logging.getLogger(__name__)

BATCH_SIZE = 20
MAX_REVIEW_CHARS = 1000

SYSTEM_PROMPT = f"""You classify mobile app store reviews for a product team.
Reviews can be in any language. Always write the summary in English.
For every review return an object with:
- id: the review id you were given (integer)
- sentiment: one of {schemas.SENTIMENTS}
- category: the single best fit from {schemas.CATEGORIES}
  (ui_ux = design, navigation or usability; account = login, sign up, subscription access or data loss;
  praise = positive feedback with no actionable request)
- severity: integer 1 to 5 for how urgently the team should act (1 = no action, 5 = app unusable or data loss)
- summary: one short English sentence describing the user's point
Return {{"results": [ ... ]}} with exactly one object per review."""


@dataclass(frozen=True)
class ReviewInput:
    """Minimal review data sent to the classifier."""

    id: int
    text: str
    title: str = ""
    rating: int | None = None


@dataclass(frozen=True)
class Classification:
    id: int
    sentiment: str
    category: str
    severity: int
    summary: str


def batched(items: Sequence[ReviewInput], size: int = BATCH_SIZE) -> Iterable[list[ReviewInput]]:
    for start in range(0, len(items), size):
        yield list(items[start:start + size])


def _build_prompt(batch: list[ReviewInput]) -> str:
    payload = [
        {
            "id": review.id,
            "rating": review.rating,
            "title": review.title[:200],
            "text": review.text[:MAX_REVIEW_CHARS],
        }
        for review in batch
    ]
    return "Classify these reviews:\n" + json.dumps(payload, ensure_ascii=False)


def valid_items(results: list[dict], expected_ids: set[int]) -> dict[int, Classification]:
    """Keep only well formed results whose id was requested."""
    accepted: dict[int, Classification] = {}
    for item in results:
        if schema_errors(item, schemas.CLASSIFICATION_ITEM):
            continue
        if item["id"] not in expected_ids or item["id"] in accepted:
            continue
        accepted[item["id"]] = Classification(
            id=item["id"],
            sentiment=item["sentiment"],
            category=item["category"],
            severity=item["severity"],
            summary=item["summary"].strip(),
        )
    return accepted


def classify_batch(llm: LLMClient, batch: list[ReviewInput]) -> tuple[dict[int, Classification], str]:
    """Classify one batch. Reviews still missing after one retry are left out.

    Returns the accepted classifications and an error message ("" if none).
    """
    pending = {review.id: review for review in batch}
    accepted: dict[int, Classification] = {}
    error = ""
    for _attempt in range(2):
        if not pending:
            break
        try:
            data = llm.chat_json(
                FAST,
                SYSTEM_PROMPT,
                _build_prompt(list(pending.values())),
                schemas.CLASSIFICATION_BATCH,
                temperature=0.0,
                max_tokens=3000,
            )
        except LLMError as exc:
            error = str(exc)
            logger.warning("Classification batch failed: %s", exc)
            continue
        found = valid_items(data["results"], set(pending))
        accepted.update(found)
        for review_id in found:
            pending.pop(review_id, None)
    if pending and not error:
        error = "The model skipped or malformed some reviews."
    return accepted, error
