"""JSON Schemas every model response is validated against."""
from __future__ import annotations

SENTIMENTS = ["positive", "neutral", "negative"]
CATEGORIES = [
    "bug",
    "performance",
    "ui_ux",
    "feature_request",
    "pricing",
    "ads",
    "account",
    "praise",
    "other",
]
EFFORTS = ["S", "M", "L"]
ITEM_TYPES = ["bug", "feature"]
TONES = ["friendly", "formal", "apologetic"]

_NON_EMPTY = {"type": "string", "minLength": 1}

CLASSIFICATION_ITEM = {
    "type": "object",
    "required": ["id", "sentiment", "category", "severity", "summary"],
    "properties": {
        "id": {"type": "integer"},
        "sentiment": {"enum": SENTIMENTS},
        "category": {"enum": CATEGORIES},
        "severity": {"type": "integer", "minimum": 1, "maximum": 5},
        "summary": {"type": "string", "minLength": 1, "maxLength": 300},
    },
}

# The batch envelope is checked loosely; each item is then validated on its
# own so one bad item does not discard the other nineteen.
CLASSIFICATION_BATCH = {
    "type": "object",
    "required": ["results"],
    "properties": {"results": {"type": "array", "items": {"type": "object"}}},
}

THEMES = {
    "type": "object",
    "required": ["themes"],
    "properties": {
        "themes": {
            "type": "array",
            "minItems": 1,
            "maxItems": 10,
            "items": {
                "type": "object",
                "required": ["title", "description", "review_ids", "representative_review_ids"],
                "properties": {
                    "title": {"type": "string", "minLength": 1, "maxLength": 120},
                    "description": _NON_EMPTY,
                    "review_ids": {"type": "array", "items": {"type": "integer"}},
                    "representative_review_ids": {
                        "type": "array",
                        "items": {"type": "integer"},
                        "maxItems": 5,
                    },
                },
            },
        }
    },
}

BACKLOG = {
    "type": "object",
    "required": ["items"],
    "properties": {
        "items": {
            "type": "array",
            "minItems": 1,
            "maxItems": 15,
            "items": {
                "type": "object",
                "required": [
                    "title",
                    "type",
                    "priority_score",
                    "reasoning",
                    "user_impact",
                    "suggested_fix",
                    "effort",
                    "theme_id",
                    "evidence_review_ids",
                ],
                "properties": {
                    "title": {"type": "string", "minLength": 1, "maxLength": 160},
                    "type": {"enum": ITEM_TYPES},
                    "priority_score": {"type": "integer", "minimum": 0, "maximum": 100},
                    "reasoning": _NON_EMPTY,
                    "user_impact": _NON_EMPTY,
                    "suggested_fix": _NON_EMPTY,
                    "effort": {"enum": EFFORTS},
                    "theme_id": {"type": ["integer", "null"]},
                    "evidence_review_ids": {"type": "array", "items": {"type": "integer"}},
                },
            },
        }
    },
}

COMPETITOR = {
    "type": "object",
    "required": ["summary", "strengths", "weaknesses", "opportunities", "sources"],
    "properties": {
        "summary": {"type": "string"},
        "strengths": {"type": "array", "items": _NON_EMPTY, "maxItems": 8},
        "weaknesses": {"type": "array", "items": _NON_EMPTY, "maxItems": 8},
        "opportunities": {"type": "array", "items": _NON_EMPTY, "maxItems": 8},
        "sources": {
            "type": "array",
            "items": {
                "type": "object",
                "required": ["url"],
                "properties": {"title": {"type": "string"}, "url": {"type": "string"}},
            },
        },
    },
}

REPLY = {
    "type": "object",
    "required": ["reply"],
    "properties": {"reply": {"type": "string", "minLength": 1, "maxLength": 2000}},
}
