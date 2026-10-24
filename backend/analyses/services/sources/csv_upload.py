"""Parse review CSV files, including Google Play Console review exports.

Play Console exports are UTF-16 encoded and use headers such as
``Star Rating``, ``Review Text`` and ``Review Submit Date and Time``. Generic
exports from other tools use names like ``rating``, ``content`` or ``date``.
The parser detects the encoding, delimiter and columns automatically.
"""
from __future__ import annotations

import csv
import io
import re
from datetime import datetime, timezone

from .itunes import RawReview

MAX_FILE_BYTES = 5 * 1024 * 1024
MAX_CSV_REVIEWS = 500

TEXT_HEADERS = [
    "review text", "review_text", "reviewtext", "review", "text", "content",
    "body", "comment", "comments", "review body", "review content", "feedback", "message",
]
TITLE_HEADERS = ["review title", "review_title", "title", "subject", "headline"]
RATING_HEADERS = [
    "star rating", "star_rating", "rating", "stars", "score", "star", "review rating", "rating value",
]
DATE_HEADERS = [
    "review submit date and time", "review last update date and time", "date", "review date",
    "review_date", "created_at", "created", "submitted", "submitted_at", "timestamp", "time",
    "updated", "updated_at", "date submitted",
]
MILLIS_HEADERS = ["review submit millis since epoch", "review last update millis since epoch"]
AUTHOR_HEADERS = ["author", "author name", "user", "username", "user name", "reviewer", "name", "nickname"]
VERSION_HEADERS = ["app version name", "app version", "version", "app_version"]
ID_HEADERS = ["review link", "review id", "review_id", "id"]

DATE_FORMATS = [
    "%Y-%m-%dT%H:%M:%S%z",
    "%Y-%m-%dT%H:%M:%SZ",
    "%Y-%m-%dT%H:%M:%S",
    "%Y-%m-%d %H:%M:%S",
    "%Y-%m-%d %H:%M",
    "%Y-%m-%d",
    "%Y/%m/%d",
    "%m/%d/%Y %H:%M",
    "%m/%d/%Y",
    "%d.%m.%Y",
    "%b %d, %Y",
    "%d %b %Y",
]


class CSVParseError(ValueError):
    """The CSV file cannot be used, with a message safe to show to users."""


def _normalise(header: str) -> str:
    return re.sub(r"\s+", " ", header.replace("﻿", "").strip().strip('"').lower())


def decode_bytes(data: bytes) -> str:
    """Decode CSV bytes, trying the encodings review exports commonly use."""
    if data.startswith((b"\xff\xfe", b"\xfe\xff")):
        return data.decode("utf-16")
    # UTF-16 without a byte order mark: ASCII characters leave null bytes behind.
    encodings = ("utf-16-le", "utf-16-be") if b"\x00" in data[:1024] else ()
    for encoding in (*encodings, "utf-8-sig", "cp1252", "latin-1"):
        try:
            text = data.decode(encoding)
        except UnicodeDecodeError:
            continue
        if "\x00" not in text:
            return text
    raise CSVParseError("The file encoding is not supported. Save it as UTF-8 and try again.")


def _find_column(headers: list[str], aliases: list[str]) -> int | None:
    for alias in aliases:
        if alias in headers:
            return headers.index(alias)
    return None


def _parse_rating(value: str) -> int | None:
    match = re.search(r"\d+(?:[.,]\d+)?", value or "")
    if not match:
        return None
    number = round(float(match.group(0).replace(",", ".")))
    return number if 1 <= number <= 5 else None


def parse_date(value: str) -> datetime | None:
    """Parse the date formats seen in review exports; returns aware UTC datetimes."""
    value = (value or "").strip()
    if not value:
        return None
    try:
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError:
        parsed = None
        for fmt in DATE_FORMATS:
            try:
                parsed = datetime.strptime(value, fmt)
                break
            except ValueError:
                continue
    if parsed is None:
        return None
    if parsed.tzinfo is None:
        parsed = parsed.replace(tzinfo=timezone.utc)
    return parsed.astimezone(timezone.utc)


def _parse_millis(value: str) -> datetime | None:
    try:
        return datetime.fromtimestamp(int(value) / 1000, tz=timezone.utc)
    except (TypeError, ValueError, OverflowError, OSError):
        return None


def _sniff_dialect(sample: str) -> type[csv.Dialect] | csv.Dialect:
    try:
        return csv.Sniffer().sniff(sample, delimiters=",;\t|")
    except csv.Error:
        return csv.excel


def parse_csv(data: bytes, max_reviews: int = MAX_CSV_REVIEWS) -> list[RawReview]:
    """Parse uploaded CSV bytes into reviews.

    Rows without review text (for example star-only Play Console ratings)
    are skipped. Raises CSVParseError with a user friendly message.
    """
    if not data:
        raise CSVParseError("The uploaded file is empty.")
    if len(data) > MAX_FILE_BYTES:
        raise CSVParseError("The file is larger than 5 MB.")

    text = decode_bytes(data)
    dialect = _sniff_dialect(text[:8192])
    rows = list(csv.reader(io.StringIO(text), dialect))
    rows = [row for row in rows if any(cell.strip() for cell in row)]
    if len(rows) < 2:
        raise CSVParseError("The file needs a header row and at least one review.")

    headers = [_normalise(cell) for cell in rows[0]]
    text_col = _find_column(headers, TEXT_HEADERS)
    if text_col is None:
        raise CSVParseError(
            "Could not find a review text column. Use a header such as 'Review Text', 'review' or 'content'."
        )
    title_col = _find_column(headers, TITLE_HEADERS)
    rating_col = _find_column(headers, RATING_HEADERS)
    date_col = _find_column(headers, DATE_HEADERS)
    millis_col = _find_column(headers, MILLIS_HEADERS)
    author_col = _find_column(headers, AUTHOR_HEADERS)
    version_col = _find_column(headers, VERSION_HEADERS)
    id_col = _find_column(headers, ID_HEADERS)

    def cell(row: list[str], index: int | None) -> str:
        if index is None or index >= len(row):
            return ""
        return row[index].strip()

    reviews: list[RawReview] = []
    for row in rows[1:]:
        body = cell(row, text_col)
        if not body:
            continue
        review_date = parse_date(cell(row, date_col)) or _parse_millis(cell(row, millis_col))
        reviews.append(
            RawReview(
                text=body,
                rating=_parse_rating(cell(row, rating_col)),
                review_date=review_date,
                title=cell(row, title_col) if title_col != text_col else "",
                author=cell(row, author_col),
                external_id=cell(row, id_col),
                app_version=cell(row, version_col),
            )
        )

    if not reviews:
        raise CSVParseError("The file has no rows with review text.")

    epoch = datetime.min.replace(tzinfo=timezone.utc)
    reviews.sort(key=lambda r: r.review_date or epoch, reverse=True)
    return reviews[:max_reviews]


def deduplicate(reviews: list[RawReview]) -> list[RawReview]:
    """Drop repeated reviews (same external id, or same author, rating and text)."""
    seen: set[str] = set()
    unique: list[RawReview] = []
    for review in reviews:
        normalised_text = re.sub(r"\s+", " ", review.text.strip().lower())
        keys = [f"text:{review.author.lower()}|{review.rating}|{normalised_text}"]
        if review.external_id:
            keys.append(f"id:{review.external_id}")
        if any(key in seen for key in keys):
            continue
        seen.update(keys)
        unique.append(review)
    return unique
