"""Apple App Store data: iTunes Search API and the public customer reviews RSS feed."""
from __future__ import annotations

import logging
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from datetime import datetime
from typing import Any

import requests
from django.conf import settings

logger = logging.getLogger(__name__)

SEARCH_URL = "https://itunes.apple.com/search"
LOOKUP_URL = "https://itunes.apple.com/lookup"
# The XML (Atom) flavour of the feed: since 2026 the JSON flavour returns no entries.
REVIEWS_URL = (
    "https://itunes.apple.com/{country}/rss/customerreviews/"
    "page={page}/id={app_id}/sortby=mostrecent/xml"
)
ATOM = "{http://www.w3.org/2005/Atom}"
ITUNES = "{http://itunes.apple.com/rss}"
MAX_PAGES = 10
MAX_REVIEWS = 300
USER_AGENT = "Mozilla/5.0 (compatible; Sift/1.0)"  # Apple returns an empty feed for non-browser agents.


class SourceError(RuntimeError):
    """The upstream data source failed or returned unusable data."""


@dataclass
class AppInfo:
    app_id: str
    name: str
    developer: str
    icon_url: str
    rating: float | None
    rating_count: int | None
    genre: str


@dataclass
class RawReview:
    """A review before it is stored, shared by every data source."""

    text: str
    rating: int | None = None
    review_date: datetime | None = None
    title: str = ""
    author: str = ""
    external_id: str = ""
    app_version: str = ""


def _get(url: str, params: dict[str, Any] | None = None) -> requests.Response:
    try:
        return requests.get(
            url,
            params=params,
            timeout=settings.HTTP_TIMEOUT_SECONDS,
            headers={"User-Agent": USER_AGENT, "Accept": "application/json"},
        )
    except requests.Timeout as exc:
        raise SourceError("The App Store took too long to respond.") from exc
    except requests.RequestException as exc:
        raise SourceError("Could not reach the App Store.") from exc


def _to_app_info(item: dict[str, Any]) -> AppInfo:
    return AppInfo(
        app_id=str(item.get("trackId", "")),
        name=item.get("trackName", ""),
        developer=item.get("artistName") or item.get("sellerName", ""),
        icon_url=item.get("artworkUrl512") or item.get("artworkUrl100") or "",
        rating=item.get("averageUserRating"),
        rating_count=item.get("userRatingCount"),
        genre=item.get("primaryGenreName", ""),
    )


def search_apps(term: str, country: str, limit: int = 20) -> list[AppInfo]:
    """Search the App Store through the iTunes Search API."""
    response = _get(
        SEARCH_URL,
        {"term": term, "entity": "software", "country": country, "limit": limit},
    )
    if response.status_code != 200:
        raise SourceError(f"App search failed with HTTP {response.status_code}.")
    try:
        results = response.json().get("results", [])
    except ValueError as exc:
        raise SourceError("App search returned an unreadable response.") from exc
    return [_to_app_info(item) for item in results if item.get("trackId")]


def lookup_app(app_id: str, country: str) -> AppInfo | None:
    """Fetch app metadata by id; returns None when the app is unknown."""
    response = _get(LOOKUP_URL, {"id": app_id, "country": country})
    if response.status_code != 200:
        return None
    try:
        results = response.json().get("results", [])
    except ValueError:
        return None
    return _to_app_info(results[0]) if results else None


def _label(entry: dict[str, Any], key: str) -> str:
    value = entry.get(key)
    if isinstance(value, dict):
        label = value.get("label")
        if isinstance(label, str):
            return label
        name = value.get("name")
        if isinstance(name, dict):
            return str(name.get("label", ""))
    return ""


def _parse_date(value: str) -> datetime | None:
    if not value:
        return None
    try:
        return datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError:
        return None


def parse_feed_page(payload: dict[str, Any]) -> list[RawReview]:
    """Convert one RSS JSON page into RawReview objects."""
    entries = payload.get("feed", {}).get("entry", [])
    if isinstance(entries, dict):
        entries = [entries]
    reviews: list[RawReview] = []
    for entry in entries:
        text = _label(entry, "content").strip()
        rating_text = _label(entry, "im:rating")
        if not text or not rating_text:
            continue  # The first entry of older feeds describes the app itself.
        try:
            rating: int | None = int(rating_text)
        except ValueError:
            rating = None
        reviews.append(
            RawReview(
                text=text,
                rating=rating,
                review_date=_parse_date(_label(entry, "updated")),
                title=_label(entry, "title").strip(),
                author=_label(entry, "author").strip(),
                external_id=_label(entry, "id"),
                app_version=_label(entry, "im:version"),
            )
        )
    return reviews


def parse_feed_xml(document: bytes) -> list[RawReview]:
    """Convert one Atom XML page of the reviews feed into RawReview objects."""
    root = ET.fromstring(document)
    reviews: list[RawReview] = []
    for entry in root.findall(f"{ATOM}entry"):
        text = next(
            ((node.text or "").strip() for node in entry.findall(f"{ATOM}content") if node.get("type") == "text"),
            "",
        )
        rating_text = (entry.findtext(f"{ITUNES}rating") or "").strip()
        if not text or not rating_text:
            continue  # Skips any entry that describes the app itself rather than a review.
        try:
            rating: int | None = int(rating_text)
        except ValueError:
            rating = None
        reviews.append(
            RawReview(
                text=text,
                rating=rating,
                review_date=_parse_date((entry.findtext(f"{ATOM}updated") or "").strip()),
                title=(entry.findtext(f"{ATOM}title") or "").strip(),
                author=(entry.findtext(f"{ATOM}author/{ATOM}name") or "").strip(),
                external_id=(entry.findtext(f"{ATOM}id") or "").strip(),
                app_version=(entry.findtext(f"{ITUNES}version") or "").strip(),
            )
        )
    return reviews


def fetch_reviews(app_id: str, country: str, max_reviews: int = MAX_REVIEWS) -> list[RawReview]:
    """Fetch up to ``max_reviews`` of the most recent reviews (pages 1 to 10)."""
    collected: list[RawReview] = []
    for page in range(1, MAX_PAGES + 1):
        response = _get(REVIEWS_URL.format(country=country, page=page, app_id=app_id))
        if response.status_code in (400, 404):
            break  # Apple returns these past the last available page.
        if response.status_code != 200:
            if collected:
                logger.warning("Review page %s failed with %s; keeping earlier pages.", page, response.status_code)
                break
            raise SourceError(f"The App Store reviews feed failed with HTTP {response.status_code}.")
        try:
            page_reviews = parse_feed_xml(response.content)
        except ET.ParseError:
            break
        if not page_reviews:
            break
        collected.extend(page_reviews)
        if len(collected) >= max_reviews:
            break
    return collected[:max_reviews]
