"""Step 5: competitor research with tool calling and Tavily web search.

The reasoning model is given a ``tavily_search`` tool. It decides what to
search for, reads the results and then writes strengths, weaknesses and
opportunities. Only URLs that Tavily actually returned are kept as sources.
"""
from __future__ import annotations

import json
import logging
from dataclasses import dataclass, field
from typing import Any, Callable

from django.conf import settings

from . import schemas
from .llm import REASONING, LLMClient, LLMError
from .validation import ModelOutputError, parse_and_validate

logger = logging.getLogger(__name__)

MAX_TOOL_ROUNDS = 4
MAX_RESULTS_PER_SEARCH = 5

TAVILY_TOOL = {
    "type": "function",
    "function": {
        "name": "tavily_search",
        "description": (
            "Search the web for up-to-date information about an app: reviews, feature lists, "
            "pricing, comparisons and recent news. Returns titles, URLs and snippets."
        ),
        "parameters": {
            "type": "object",
            "properties": {
                "query": {"type": "string", "description": "The web search query."},
                "max_results": {
                    "type": "integer",
                    "description": "Number of results to return, 1 to 5.",
                    "minimum": 1,
                    "maximum": MAX_RESULTS_PER_SEARCH,
                },
            },
            "required": ["query"],
        },
    },
}

SYSTEM_PROMPT = """You are a competitive intelligence analyst for a mobile app team.
Research the competitor with the tavily_search tool (2 or 3 focused searches: features and pricing,
what users praise, what users complain about). Then compare it with OUR app using the themes provided.
When you are done researching, reply with JSON only:
{"summary": "...", "strengths": ["..."], "weaknesses": ["..."], "opportunities": ["..."],
 "sources": [{"title": "...", "url": "..."}]}
- strengths and weaknesses describe the competitor, 3 to 5 each
- opportunities are 3 to 5 concrete moves OUR app can make to win users
- sources must be URLs returned by your searches"""


@dataclass
class CompetitorResult:
    name: str
    summary: str = ""
    strengths: list[str] = field(default_factory=list)
    weaknesses: list[str] = field(default_factory=list)
    opportunities: list[str] = field(default_factory=list)
    sources: list[dict[str, str]] = field(default_factory=list)
    error: str = ""


SearchFn = Callable[[str, int], list[dict[str, str]]]


def tavily_search(query: str, max_results: int = MAX_RESULTS_PER_SEARCH) -> list[dict[str, str]]:
    """Run a Tavily search and return compact results."""
    if not settings.TAVILY_API_KEY:
        raise LLMError("TAVILY_API_KEY is not configured on the server.")
    from tavily import TavilyClient

    client = TavilyClient(api_key=settings.TAVILY_API_KEY)
    response = client.search(
        query=query,
        max_results=max(1, min(max_results, MAX_RESULTS_PER_SEARCH)),
        search_depth="basic",
        timeout=settings.HTTP_TIMEOUT_SECONDS * 2,
    )
    return [
        {
            "title": item.get("title", ""),
            "url": item.get("url", ""),
            "content": (item.get("content") or "")[:600],
        }
        for item in response.get("results", [])
        if item.get("url")
    ]


class CompetitorResearcher:
    """Runs the tool calling loop for one competitor at a time."""

    def __init__(self, llm: LLMClient, search: SearchFn = tavily_search) -> None:
        self.llm = llm
        self.search = search

    def research(self, competitor: str, app_name: str, theme_titles: list[str]) -> CompetitorResult:
        seen_sources: dict[str, str] = {}
        try:
            data = self._run_tool_loop(competitor, app_name, theme_titles, seen_sources)
        except LLMError as exc:
            if not seen_sources:
                # Some models do not support tools; fall back to searching ourselves.
                try:
                    data = self._run_direct(competitor, app_name, theme_titles, seen_sources)
                except LLMError as fallback_exc:
                    return CompetitorResult(name=competitor, error=str(fallback_exc))
            else:
                return CompetitorResult(name=competitor, error=str(exc))
        return self._to_result(competitor, data, seen_sources)

    def _user_prompt(self, competitor: str, app_name: str, theme_titles: list[str]) -> str:
        return json.dumps(
            {"our_app": app_name, "our_top_review_themes": theme_titles[:8], "competitor": competitor},
            ensure_ascii=False,
        )

    def _execute(self, arguments: str, seen_sources: dict[str, str]) -> str:
        try:
            args = json.loads(arguments or "{}")
        except json.JSONDecodeError:
            return json.dumps({"error": "Arguments were not valid JSON."})
        query = str(args.get("query", "")).strip()
        if not query:
            return json.dumps({"error": "A query is required."})
        try:
            max_results = int(args.get("max_results", MAX_RESULTS_PER_SEARCH))
        except (TypeError, ValueError):
            max_results = MAX_RESULTS_PER_SEARCH
        try:
            results = self.search(query, max_results)
        except LLMError:
            raise
        except Exception as exc:
            logger.warning("Tavily search failed: %s", exc)
            return json.dumps({"error": "Search failed, try a different query."})
        for item in results:
            seen_sources.setdefault(item["url"], item.get("title", ""))
        return json.dumps({"results": results}, ensure_ascii=False)

    def _run_tool_loop(
        self, competitor: str, app_name: str, theme_titles: list[str], seen_sources: dict[str, str]
    ) -> dict[str, Any]:
        messages: list[dict[str, Any]] = [
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user", "content": self._user_prompt(competitor, app_name, theme_titles)},
        ]
        for _round in range(MAX_TOOL_ROUNDS):
            message = self.llm.complete(REASONING, messages, tools=[TAVILY_TOOL], max_tokens=4000)
            tool_calls = getattr(message, "tool_calls", None) or []
            if not tool_calls:
                return self._finalise(message.content or "", messages)
            messages.append(
                {
                    "role": "assistant",
                    "content": message.content or "",
                    "tool_calls": [
                        {
                            "id": call.id,
                            "type": "function",
                            "function": {"name": call.function.name, "arguments": call.function.arguments},
                        }
                        for call in tool_calls
                    ],
                }
            )
            for call in tool_calls:
                if call.function.name == "tavily_search":
                    content = self._execute(call.function.arguments, seen_sources)
                else:
                    content = json.dumps({"error": f"Unknown tool {call.function.name}."})
                messages.append({"role": "tool", "tool_call_id": call.id, "content": content})
        messages.append({"role": "user", "content": "Research is complete. Write the final JSON now."})
        message = self.llm.complete(REASONING, messages, json_mode=True, max_tokens=3000)
        return self._finalise(message.content or "", messages)

    def _finalise(self, content: str, messages: list[dict[str, Any]]) -> dict[str, Any]:
        try:
            return parse_and_validate(content, schemas.COMPETITOR)
        except ModelOutputError as exc:
            retry = messages + [
                {"role": "assistant", "content": content[:4000]},
                {"role": "user", "content": f"That was invalid ({exc}). Reply with the final JSON only."},
            ]
            message = self.llm.complete(REASONING, retry, json_mode=True, max_tokens=3000)
            try:
                return parse_and_validate(message.content or "", schemas.COMPETITOR)
            except ModelOutputError as second:
                raise LLMError(f"Competitor analysis was invalid twice: {second}") from second

    def _run_direct(
        self, competitor: str, app_name: str, theme_titles: list[str], seen_sources: dict[str, str]
    ) -> dict[str, Any]:
        findings = []
        for query in (f"{competitor} app features pricing", f"{competitor} app user reviews complaints"):
            findings.append({"query": query, **json.loads(self._execute(json.dumps({"query": query}), seen_sources))})
        prompt = self._user_prompt(competitor, app_name, theme_titles) + "\nSearch results:\n" + json.dumps(
            findings, ensure_ascii=False
        )
        return self.llm.chat_json(REASONING, SYSTEM_PROMPT, prompt, schemas.COMPETITOR, max_tokens=3000)

    @staticmethod
    def _to_result(competitor: str, data: dict[str, Any], seen_sources: dict[str, str]) -> CompetitorResult:
        sources = [
            {"title": s.get("title") or seen_sources[s["url"]], "url": s["url"]}
            for s in data["sources"]
            if s.get("url") in seen_sources
        ]
        if not sources:
            sources = [{"title": title, "url": url} for url, title in list(seen_sources.items())[:5]]
        unique = list({s["url"]: s for s in sources}.values())[:8]
        return CompetitorResult(
            name=competitor,
            summary=data["summary"].strip(),
            strengths=[s.strip() for s in data["strengths"]],
            weaknesses=[s.strip() for s in data["weaknesses"]],
            opportunities=[s.strip() for s in data["opportunities"]],
            sources=unique,
        )
