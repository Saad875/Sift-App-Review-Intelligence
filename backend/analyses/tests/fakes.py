"""Test doubles that mimic the openai client's response objects."""
from __future__ import annotations

import json
from types import SimpleNamespace
from typing import Any, Callable


def message(content: str | None = None, tool_calls: list | None = None) -> SimpleNamespace:
    return SimpleNamespace(content=content, tool_calls=tool_calls)


def tool_call(call_id: str, name: str, arguments: dict) -> SimpleNamespace:
    return SimpleNamespace(id=call_id, function=SimpleNamespace(name=name, arguments=json.dumps(arguments)))


class FakeOpenAI:
    """Returns scripted messages; ``responder`` may also compute them from kwargs."""

    def __init__(self, responses: list[Any] | None = None, responder: Callable[[dict], Any] | None = None):
        self.responses = list(responses or [])
        self.responder = responder
        self.calls: list[dict] = []
        self.chat = SimpleNamespace(completions=SimpleNamespace(create=self._create))

    def _create(self, **kwargs):
        self.calls.append(kwargs)
        if self.responder is not None:
            result = self.responder(kwargs)
        else:
            result = self.responses.pop(0)
        if isinstance(result, Exception):
            raise result
        msg = result if isinstance(result, SimpleNamespace) else message(result)
        return SimpleNamespace(choices=[SimpleNamespace(message=msg)])
