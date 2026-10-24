"""On-demand developer reply drafts with the reasoning model."""
from __future__ import annotations

import json

from . import schemas
from .llm import REASONING, LLMClient

TONE_GUIDES = {
    "friendly": "warm, upbeat and personal, like a helpful teammate",
    "formal": "polite, concise and professional",
    "apologetic": "sincerely apologetic, taking ownership without making excuses",
}

SYSTEM_PROMPT = """You write public developer replies to app store reviews.
Rules:
- Reply in the same language as the review.
- 2 to 4 sentences, under 350 characters when possible (store replies have length limits).
- Acknowledge the user's specific point. Do not promise dates or features that are not confirmed.
- If there is a problem, invite them to contact support. Never ask for passwords or personal data.
- No placeholders such as [Name] and no signatures.
Return {"reply": "..."}."""


def draft_reply(llm: LLMClient, *, app_name: str, review_text: str, rating: int | None, tone: str) -> str:
    """Return a reply for one review in the requested tone."""
    if tone not in TONE_GUIDES:
        raise ValueError(f"Unsupported tone '{tone}'.")
    prompt = json.dumps(
        {
            "app": app_name,
            "tone": f"{tone}: {TONE_GUIDES[tone]}",
            "rating": rating,
            "review": review_text[:2000],
        },
        ensure_ascii=False,
    )
    data = llm.chat_json(REASONING, SYSTEM_PROMPT, prompt, schemas.REPLY, temperature=0.6, max_tokens=1200)
    return data["reply"].strip()
