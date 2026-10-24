"""Turn raw model output into validated Python objects.

Language models sometimes wrap JSON in Markdown fences, prepend reasoning
inside ``<think>`` tags, or add a sentence before the payload. These helpers
strip that noise, parse the JSON and validate it against a JSON Schema.
"""
from __future__ import annotations

import json
import re
from typing import Any

from jsonschema import Draft202012Validator

_THINK_BLOCK = re.compile(r"<think>.*?</think>", re.DOTALL | re.IGNORECASE)
_FENCE = re.compile(r"```(?:json|JSON)?\s*(.*?)```", re.DOTALL)


class ModelOutputError(ValueError):
    """Raised when model output is not valid JSON or does not match its schema."""


def strip_reasoning(text: str) -> str:
    """Remove ``<think>`` blocks, including an unterminated leading one."""
    cleaned = _THINK_BLOCK.sub("", text)
    if "</think>" in cleaned.lower():
        cleaned = cleaned[cleaned.lower().rfind("</think>") + len("</think>"):]
    return cleaned.strip()


def extract_json(text: str | None) -> Any:
    """Parse the first JSON object or array found in ``text``.

    Raises ModelOutputError when nothing parseable is found.
    """
    if text is None or not text.strip():
        raise ModelOutputError("The model returned an empty response.")
    cleaned = strip_reasoning(text)

    candidates: list[str] = [cleaned]
    candidates.extend(match.strip() for match in _FENCE.findall(cleaned))
    for opener, closer in (("{", "}"), ("[", "]")):
        start, end = cleaned.find(opener), cleaned.rfind(closer)
        if start != -1 and end > start:
            candidates.append(cleaned[start:end + 1])

    for candidate in candidates:
        try:
            return json.loads(candidate)
        except (json.JSONDecodeError, TypeError):
            continue
    raise ModelOutputError("The model response did not contain valid JSON.")


def schema_errors(data: Any, schema: dict) -> list[str]:
    """Return human readable schema violations, empty when ``data`` is valid."""
    validator = Draft202012Validator(schema)
    messages = []
    for error in sorted(validator.iter_errors(data), key=lambda e: list(e.path)):
        location = "/".join(str(part) for part in error.path) or "root"
        messages.append(f"{location}: {error.message}")
    return messages


def validate(data: Any, schema: dict) -> Any:
    """Return ``data`` unchanged when it matches ``schema``, else raise."""
    errors = schema_errors(data, schema)
    if errors:
        raise ModelOutputError("Schema validation failed: " + "; ".join(errors[:5]))
    return data


def parse_and_validate(text: str | None, schema: dict) -> Any:
    """Extract JSON from ``text`` and validate it in one step."""
    return validate(extract_json(text), schema)
