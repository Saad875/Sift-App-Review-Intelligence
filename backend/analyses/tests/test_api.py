"""API tests with external services patched out."""
from __future__ import annotations

from unittest import mock

from django.core.files.uploadedfile import SimpleUploadedFile
from django.test import TestCase, override_settings
from rest_framework.test import APIClient

from analyses.models import Analysis, BacklogItem, Review, Theme
from analyses.services.sources.itunes import AppInfo


@override_settings(MODEL_FAST="test-nano", MODEL_REASONING="test-super")
class ApiTests(TestCase):
    def setUp(self):
        self.client = APIClient()

    def test_health(self):
        response = self.client.get("/api/v1/health/")
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json()["status"], "ok")

    def test_search_requires_query(self):
        response = self.client.get("/api/v1/apps/search/?q=a")
        self.assertEqual(response.status_code, 400)
        self.assertEqual(response.json()["error"]["code"], "invalid_request")

    @mock.patch("analyses.views.itunes.search_apps")
    def test_search(self, search):
        search.return_value = [AppInfo("1", "Notes", "Dev", "https://i/1.png", 4.5, 10, "Productivity")]
        response = self.client.get("/api/v1/apps/search/?q=notes&country=gb")
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json()["results"][0]["name"], "Notes")
        search.assert_called_once_with("notes", "gb")

    @mock.patch("analyses.views.pipeline.launch")
    def test_create_csv_analysis(self, launch):
        upload = SimpleUploadedFile("my-app.csv", b"review,rating\nGreat,5\nBad,1\n", content_type="text/csv")
        response = self.client.post(
            "/api/v1/analyses/",
            {"source": "csv", "file": upload, "competitors": "Rival, Other"},
            format="multipart",
        )
        self.assertEqual(response.status_code, 201, response.content)
        body = response.json()
        self.assertEqual(body["app_name"], "my-app")
        self.assertEqual(body["competitors"], ["Rival", "Other"])
        self.assertEqual([s["state"] for s in body["steps"]], ["waiting"] * 5)
        args = launch.call_args[0]
        self.assertEqual(len(args[1]), 2)

    def test_create_csv_with_bad_file(self):
        upload = SimpleUploadedFile("x.csv", b"rating\n5\n", content_type="text/csv")
        response = self.client.post("/api/v1/analyses/", {"source": "csv", "file": upload}, format="multipart")
        self.assertEqual(response.status_code, 400)
        self.assertIn("review text column", response.json()["error"]["message"])

    @mock.patch("analyses.views.pipeline.launch")
    @mock.patch("analyses.views.itunes.lookup_app")
    def test_create_appstore_analysis(self, lookup, launch):
        lookup.return_value = AppInfo("123", "Notes", "Dev", "https://i/1.png", 4.5, 10, "Productivity")
        response = self.client.post(
            "/api/v1/analyses/",
            {"source": "appstore", "app_id": "123", "country": "US", "competitors": ["A", "B"]},
            format="json",
        )
        self.assertEqual(response.status_code, 201, response.content)
        self.assertEqual(response.json()["app_name"], "Notes")
        self.assertEqual(response.json()["country"], "us")
        launch.assert_called_once()

    def test_too_many_competitors(self):
        response = self.client.post(
            "/api/v1/analyses/",
            {"source": "appstore", "app_id": "1", "app_name": "X", "icon_url": "https://x.io/i.png",
             "competitors": ["A", "B", "C", "D"]},
            format="json",
        )
        self.assertEqual(response.status_code, 400)
        self.assertIn("competitors", response.json()["error"]["message"])

    def _completed(self):
        analysis = Analysis.objects.create(
            app_name="Notes", source="appstore", app_id="1", status="completed", current_step="done", progress=100
        )
        theme = Theme.objects.create(analysis=analysis, title="Crashes", description="d", review_count=1,
                                     negative_share=1.0, quotes=["It crashes"])
        Review.objects.create(analysis=analysis, text="It crashes", rating=1, sentiment="negative",
                              category="bug", severity=5, summary="Crashes", theme=theme)
        Review.objects.create(analysis=analysis, text="Love it", rating=5, sentiment="positive",
                              category="praise", severity=1, summary="Likes")
        BacklogItem.objects.create(analysis=analysis, rank=1, title="Fix crash", item_type="bug",
                                   priority_score=90, reasoning="r", user_impact="u", suggested_fix="f",
                                   effort="M", theme=theme, evidence_quotes=["It crashes"])
        return analysis

    def test_detail_and_children(self):
        analysis = self._completed()
        base = f"/api/v1/analyses/{analysis.pk}/"
        detail = self.client.get(base).json()
        self.assertEqual(detail["stats"]["total_reviews"], 2)
        self.assertEqual(detail["stats"]["sentiment"]["negative"], 1)
        self.assertEqual(detail["stats"]["rating_distribution"]["5"], 1)
        self.assertEqual(detail["steps"][1]["model"], "test-nano")
        self.assertEqual(detail["steps"][4]["state"], "skipped")

        reviews = self.client.get(base + "reviews/?sentiment=negative").json()
        self.assertEqual(reviews["count"], 1)
        self.assertEqual(reviews["results"][0]["theme_title"], "Crashes")
        self.assertEqual(self.client.get(base + "reviews/?category=nope").status_code, 400)

        self.assertEqual(self.client.get(base + "themes/").json()["results"][0]["title"], "Crashes")
        backlog = self.client.get(base + "backlog/").json()["results"][0]
        self.assertEqual(backlog["type"], "bug")
        self.assertEqual(self.client.get(base + "competitors/").json()["results"], [])

        export = self.client.get(base + "export/")
        self.assertEqual(export.status_code, 200)
        self.assertIn("# Sift report: Notes", export.content.decode())
        self.assertIn("Fix crash", export.content.decode())

    def test_unknown_analysis(self):
        response = self.client.get("/api/v1/analyses/00000000-0000-0000-0000-000000000000/")
        self.assertEqual(response.status_code, 404)
        self.assertEqual(response.json()["error"]["code"], "not_found")

    @mock.patch("analyses.views.draft_reply", return_value="Thanks for the feedback!")
    def test_reply_draft(self, draft):
        analysis = self._completed()
        review = analysis.reviews.first()
        response = self.client.post(f"/api/v1/reviews/{review.pk}/reply/", {"tone": "formal"}, format="json")
        self.assertEqual(response.status_code, 201)
        self.assertEqual(response.json()["text"], "Thanks for the feedback!")
        self.assertEqual(review.reply_drafts.count(), 1)

    def test_reply_rejects_bad_tone(self):
        review = self._completed().reviews.first()
        response = self.client.post(f"/api/v1/reviews/{review.pk}/reply/", {"tone": "rude"}, format="json")
        self.assertEqual(response.status_code, 400)
