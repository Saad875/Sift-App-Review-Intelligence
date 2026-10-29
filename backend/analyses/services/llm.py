"""Thin client for NVIDIA Nemotron models served by Nebius Token Factory.

Token Factory exposes an OpenAI compatible API, so the official ``openai``
package is used with ``base_url`` pointed at Nebius. Model names always come
from settings (``MODEL_FAST`` and ``MODEL_REASONING``).
"""
from __future__ import annotations

import logging
from dataclasses import dataclass
from typing import Any, Callable

from django.conf import settings

from .validation import ModelOutputError, parse_and_validate

logger = logging.getLogger(__name__)


class LLMError(RuntimeError):
    """A model call failed after its retry, or the provider is unreachable."""


class LLMConfigurationError(LLMError):
    """Required configuration (API key or model id) is missing."""


@dataclass(frozen=True)
class ModelRole:
    """Which configured model to use for a call."""

    setting_name: str

    @property
    def model(self) -> str:
        value = getattr(settings, self.setting_name, "")
        if not value:
            raise LLMConfigurationError(
                f"The server's AI model is not configured ({self.setting_name} is empty), so this analysis cannot run."
            )
        return value


FAST = ModelRole("MODEL_FAST")
REASONING = ModelRole("MODEL_REASONING")

JSON_ONLY_INSTRUCTION = (
    "Respond with a single valid JSON object only. Do not use Markdown, code fences "
    "or any text outside the JSON."
)


class LLMClient:
    """Calls a chat model, validates its JSON and retries once on bad output."""

    def __init__(self, client: Any | None = None) -> None:
        self._client = client
        self._json_mode_supported = True

    @property
    def client(self) -> Any:
        if self._client is None:
            if not settings.NEBIUS_API_KEY:
                raise LLMConfigurationError(
                    "The server's AI key is not configured, so this analysis cannot run. "
                    "Add NEBIUS_API_KEY to backend/.env and restart the server."
                )
            from openai import OpenAI

            self._client = OpenAI(
                api_key=settings.NEBIUS_API_KEY,
                base_url=settings.NEBIUS_BASE_URL,
                timeout=settings.LLM_TIMEOUT_SECONDS,
                max_retries=1,
            )
        return self._client

    def complete(
        self,
        role: ModelRole,
        messages: list[dict[str, Any]],
        *,
        json_mode: bool = True,
        tools: list[dict[str, Any]] | None = None,
        temperature: float = 0.2,
        max_tokens: int = 4096,
    ) -> Any:
        """Run one chat completion and return the first choice's message."""
        kwargs: dict[str, Any] = {
            "model": role.model,
            "messages": messages,
            "temperature": temperature,
            "max_tokens": max_tokens,
        }
        if tools:
            kwargs["tools"] = tools
            kwargs["tool_choice"] = "auto"
        elif json_mode and self._json_mode_supported:
            kwargs["response_format"] = {"type": "json_object"}

        try:
            response = self.client.chat.completions.create(**kwargs)
        except LLMError:
            raise
        except Exception as exc:  # openai raises several error types; normalise them.
            if "response_format" in kwargs and _looks_like_unsupported_json_mode(exc):
                logger.info("Model rejected response_format, retrying without JSON mode.")
                self._json_mode_supported = False
                kwargs.pop("response_format")
                try:
                    response = self.client.chat.completions.create(**kwargs)
                except Exception as retry_exc:
                    raise LLMError(_describe(retry_exc)) from retry_exc
            else:
                raise LLMError(_describe(exc)) from exc

        if not response.choices:
            raise LLMError("The model returned no choices.")
        return response.choices[0].message

    def chat_json(
        self,
        role: ModelRole,
        system: str,
        user: str,
        schema: dict,
        *,
        temperature: float = 0.2,
        max_tokens: int = 4096,
        item_validator: Callable[[Any], Any] | None = None,
    ) -> Any:
        """Request JSON, validate it against ``schema``, retry once, else raise.

        On the retry the validation error is sent back to the model so it can
        correct itself. ``item_validator`` may post-process or reject the data.
        """
        messages: list[dict[str, Any]] = [
            {"role": "system", "content": f"{system}\n\n{JSON_ONLY_INSTRUCTION}"},
            {"role": "user", "content": user},
        ]
        last_error: Exception | None = None
        for attempt in range(2):
            message = self.complete(
                role, messages, temperature=temperature, max_tokens=max_tokens
            )
            content = message.content or ""
            try:
                data = parse_and_validate(content, schema)
                return item_validator(data) if item_validator else data
            except ModelOutputError as exc:
                last_error = exc
                logger.warning("Invalid model output (attempt %s): %s", attempt + 1, exc)
                messages = messages[:2] + [
                    {"role": "assistant", "content": content[:4000]},
                    {
                        "role": "user",
                        "content": (
                            f"Your previous answer was invalid: {exc}. "
                            "Return corrected JSON that follows the requested format exactly."
                        ),
                    },
                ]
        raise LLMError(f"The model returned invalid output twice: {last_error}")


def _looks_like_unsupported_json_mode(exc: Exception) -> bool:
    text = str(exc).lower()
    return "response_format" in text or "json_object" in text


def _describe(exc: Exception) -> str:
    name = type(exc).__name__
    if "Timeout" in name:
        return "The AI model timed out. Please try again."
    if "Authentication" in name or "PermissionDenied" in name:
        return "The server's Nebius API key was rejected."
    if "RateLimit" in name:
        return "The AI provider is rate limiting requests. Please try again shortly."
    if "Connection" in name:
        return "Could not reach Nebius Token Factory."
    if "NotFound" in name:
        return "The configured model id was not found on Nebius Token Factory."
    return f"AI provider error ({name}): {str(exc)[:200]}"
