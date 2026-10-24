"""Unit tests for CSV review parsing."""
from __future__ import annotations

from datetime import datetime, timezone

from django.test import SimpleTestCase

from analyses.services.sources.csv_upload import (
    CSVParseError,
    RawReview,
    decode_bytes,
    deduplicate,
    parse_csv,
    parse_date,
)

PLAY_CONSOLE_HEADER = (
    "Package Name,App Version Code,App Version Name,Reviewer Language,Device,"
    "Review Submit Date and Time,Review Submit Millis Since Epoch,Review Last Update Date and Time,"
    "Review Last Update Millis Since Epoch,Star Rating,Review Title,Review Text,"
    "Developer Reply Date and Time,Developer Reply Millis Since Epoch,Developer Reply Text,Review Link"
)


class ParseCsvTests(SimpleTestCase):
    def test_google_play_console_export_utf16(self):
        rows = [
            PLAY_CONSOLE_HEADER,
            'com.example,42,2.1.0,en,pixel7,2024-05-02T10:15:00Z,1714644900000,2024-05-02T10:15:00Z,'
            '1714644900000,1,,"App crashes when I open the camera",,,,https://play.google.com/r/1',
            'com.example,42,2.1.0,de,pixel6,2024-05-03T08:00:00Z,1714723200000,2024-05-03T08:00:00Z,'
            '1714723200000,5,,"Tolle App, sehr schnell",,,,https://play.google.com/r/2',
            'com.example,42,2.1.0,en,pixel5,2024-05-04T08:00:00Z,1714809600000,2024-05-04T08:00:00Z,'
            '1714809600000,4,,,,,,https://play.google.com/r/3',
        ]
        data = "\n".join(rows).encode("utf-16")
        reviews = parse_csv(data)
        self.assertEqual(len(reviews), 2)  # The star-only row has no text and is skipped.
        newest = reviews[0]
        self.assertEqual(newest.text, "Tolle App, sehr schnell")
        self.assertEqual(newest.rating, 5)
        self.assertEqual(newest.app_version, "2.1.0")
        self.assertEqual(newest.external_id, "https://play.google.com/r/2")
        self.assertEqual(newest.review_date, datetime(2024, 5, 3, 8, 0, tzinfo=timezone.utc))
        self.assertEqual(reviews[1].rating, 1)

    def test_generic_headers_and_semicolon_delimiter(self):
        data = "Date;Stars;Content;Author\n2024-01-05;2;Too many ads;sam\n05/01/2024;4.0;Nice update;lee\n"
        reviews = parse_csv(data.encode("utf-8"))
        self.assertEqual(len(reviews), 2)
        by_author = {r.author: r for r in reviews}
        self.assertEqual(by_author["sam"].rating, 2)
        self.assertEqual(by_author["lee"].rating, 4)
        self.assertEqual(by_author["sam"].text, "Too many ads")

    def test_bom_and_case_insensitive_headers(self):
        data = "﻿REVIEW,Rating\nGreat,5\n".encode("utf-8")
        self.assertEqual(parse_csv(data)[0].text, "Great")

    def test_millis_used_when_date_missing(self):
        data = "review text,review submit millis since epoch\nHello,1714644900000\n".encode()
        self.assertEqual(parse_csv(data)[0].review_date.year, 2024)

    def test_invalid_rating_becomes_none(self):
        data = "text,rating\nOkay,ten\nMeh,9\n".encode()
        self.assertTrue(all(r.rating is None for r in parse_csv(data)))

    def test_missing_text_column(self):
        with self.assertRaisesMessage(CSVParseError, "review text column"):
            parse_csv(b"rating,date\n5,2024-01-01\n")

    def test_empty_and_header_only(self):
        with self.assertRaises(CSVParseError):
            parse_csv(b"")
        with self.assertRaises(CSVParseError):
            parse_csv(b"review,rating\n")

    def test_rows_without_text(self):
        with self.assertRaisesMessage(CSVParseError, "no rows with review text"):
            parse_csv(b"review,rating\n,5\n  ,4\n")

    def test_cap_keeps_newest(self):
        lines = ["text,date"] + [f"Review {day},2024-01-{day:02d}" for day in range(1, 21)]
        reviews = parse_csv("\n".join(lines).encode(), max_reviews=5)
        self.assertEqual(len(reviews), 5)
        self.assertEqual(reviews[0].text, "Review 20")

    def test_too_large(self):
        with self.assertRaisesMessage(CSVParseError, "5 MB"):
            parse_csv(b"text\n" + b"a" * (5 * 1024 * 1024 + 1))


class HelperTests(SimpleTestCase):
    def test_parse_date_formats(self):
        self.assertEqual(parse_date("2024-03-01").day, 1)
        self.assertEqual(parse_date("2024-03-01 12:30:00").hour, 12)
        self.assertEqual(parse_date("Mar 05, 2024").day, 5)
        self.assertEqual(parse_date("2024-03-01T10:00:00+02:00").hour, 8)
        self.assertIsNone(parse_date("yesterday"))
        self.assertIsNone(parse_date(""))

    def test_decode_cp1252(self):
        self.assertEqual(decode_bytes("café".encode("cp1252")), "café")

    def test_deduplicate(self):
        reviews = [
            RawReview(text="Great  app", rating=5, author="a"),
            RawReview(text="great app", rating=5, author="A"),
            RawReview(text="great app", rating=4, author="a"),
            RawReview(text="other", external_id="x"),
            RawReview(text="other text", external_id="x"),
        ]
        self.assertEqual([r.text for r in deduplicate(reviews)], ["Great  app", "great app", "other"])
