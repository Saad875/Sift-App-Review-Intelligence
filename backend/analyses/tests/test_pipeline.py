"""End to end pipeline test with a scripted model and search tool."""
from __future__ import annotations

import json

from django.test import TestCase, override_settings

from analyses.models import Analysis
from analyses.services.competitors import CompetitorResearcher
from analyses.services.llm import LLMClient
from analyses.services.pipeline import Pipeline
from analyses.services.sources.itunes import RawReview, parse_feed_page, parse_feed_xml

from .fakes import FakeOpenAI, message, tool_call

MODELS = {"MODEL_FAST": "test-nano", "MODEL_REASONING": "test-super", "NEBIUS_API_KEY": "k"}


def scripted_responder(kwargs: dict):
    model = kwargs["model"]
    last = kwargs["messages"][-1]
    if model == "test-nano":
        reviews = json.loads(last["content"].split("\n", 1)[1])
        return json.dumps(
            {
                "results": [
                    {
                        "id": r["id"],
                        "sentiment": "negative" if r["rating"] <= 2 else "positive",
                        "category": "bug" if r["rating"] <= 2 else "praise",
                        "severity": 5 if r["rating"] <= 2 else 1,
                        "summary": r["text"][:40],
                    }
                    for r in reviews
                ]
            }
        )
    system = kwargs["messages"][0]["content"]
    if "Group them into" in system:
        ids = [line["id"] for line in json.loads(last["content"].split("\n", 1)[1])]
        return json.dumps(
            {
                "themes": [
                    {"title": "Crashes", "description": "App crashes.", "review_ids": ids[:3],
                     "representative_review_ids": ids[:1]},
                    {"title": "Love it", "description": "Users like it.", "review_ids": ids[3:],
                     "representative_review_ids": [999]},
                ]
            }
        )
    if "prioritised backlog" in system:
        first_id = json.loads(last["content"])["most_severe_reviews"][0]["id"]
        return json.dumps(
            {
                "items": [
                    {"title": "Polish", "type": "feature", "priority_score": 20, "reasoning": "r",
                     "user_impact": "u", "suggested_fix": "f", "effort": "S", "theme_id": 1,
                     "evidence_review_ids": []},
                    {"title": "Fix crash", "type": "bug", "priority_score": 95, "reasoning": "r",
                     "user_impact": "u", "suggested_fix": "f", "effort": "M", "theme_id": 0,
                     "evidence_review_ids": [first_id]},
                ]
            }
        )
    # Competitor research: first call asks for a search, the next one answers.
    if kwargs.get("tools") and last["role"] == "user":
        return message(None, [tool_call("c1", "tavily_search", {"query": "Rival features"})])
    return json.dumps(
        {
            "summary": "Rival is popular.",
            "strengths": ["Fast"],
            "weaknesses": ["Pricey"],
            "opportunities": ["Lower price tier"],
            "sources": [{"title": "Made up", "url": "https://invented.example"},
                        {"title": "Real", "url": "https://real.example/rival"}],
        }
    )


@override_settings(**MODELS)
class PipelineTests(TestCase):
    def _reviews(self):
        texts = [("Crash on start", 1), ("Crashes always", 2), ("Crash again", 1), ("Love it", 5), ("Great", 5)]
        reviews = [RawReview(text=t, rating=r, author=f"u{i}") for i, (t, r) in enumerate(texts)]
        return reviews + [RawReview(text="Love it", rating=5, author="u3")]  # Duplicate.

    def test_full_run(self):
        analysis = Analysis.objects.create(app_name="Demo", source="csv", competitors=["Rival"])
        searches = []

        def search(query, max_results):
            searches.append(query)
            return [{"title": "Real", "url": "https://real.example/rival", "content": "snippet"}]

        llm = LLMClient(FakeOpenAI(responder=scripted_responder))
        pipeline = Pipeline(analysis.pk, self._reviews(), llm=llm, researcher=CompetitorResearcher(llm, search))
        pipeline.run()

        analysis.refresh_from_db()
        self.assertEqual(analysis.status, Analysis.Status.COMPLETED, analysis.error)
        self.assertEqual(analysis.progress, 100)
        self.assertEqual(analysis.reviews.count(), 5)
        self.assertEqual(analysis.reviews.filter(sentiment="negative").count(), 3)
        themes = list(analysis.themes.all())
        self.assertEqual(themes[0].review_count, 3)
        self.assertEqual(themes[0].negative_share, 1.0)
        self.assertEqual(len(themes[0].quotes), 3)
        backlog = list(analysis.backlog.all())
        self.assertEqual([b.title for b in backlog], ["Fix crash", "Polish"])
        self.assertEqual(backlog[0].theme, themes[0])
        self.assertEqual(backlog[1].evidence_quotes, themes[1].quotes[:2])
        insight = analysis.competitor_insights.get()
        self.assertEqual(searches, ["Rival features"])
        self.assertEqual(insight.sources, [{"title": "Real", "url": "https://real.example/rival"}])
        self.assertEqual(insight.error, "")

    def test_zero_reviews_completes_with_warning(self):
        analysis = Analysis.objects.create(app_name="Empty", source="csv")
        Pipeline(analysis.pk, [], llm=LLMClient(FakeOpenAI([]))).run()
        analysis.refresh_from_db()
        self.assertEqual(analysis.status, Analysis.Status.COMPLETED)
        self.assertTrue(analysis.warnings)

    def test_theme_failure_does_not_break_analysis(self):
        def responder(kwargs):
            if kwargs["model"] == "test-nano":
                return scripted_responder(kwargs)
            return "nonsense"

        analysis = Analysis.objects.create(app_name="Demo", source="csv")
        Pipeline(analysis.pk, self._reviews(), llm=LLMClient(FakeOpenAI(responder=responder))).run()
        analysis.refresh_from_db()
        self.assertEqual(analysis.status, Analysis.Status.COMPLETED)
        self.assertEqual(analysis.themes.count(), 0)
        self.assertEqual(len(analysis.warnings), 2)

    def test_classification_total_failure_fails_analysis(self):
        analysis = Analysis.objects.create(app_name="Demo", source="csv")
        Pipeline(analysis.pk, self._reviews(), llm=LLMClient(FakeOpenAI(responder=lambda k: "bad"))).run()
        analysis.refresh_from_db()
        self.assertEqual(analysis.status, Analysis.Status.FAILED)
        self.assertIn("could not classify", analysis.error)

    @override_settings(NEBIUS_API_KEY="")
    def test_missing_key_keeps_reviews_and_fails_with_a_clear_message(self):
        analysis = Analysis.objects.create(app_name="Demo", source="csv")
        Pipeline(analysis.pk, self._reviews()).run()
        analysis.refresh_from_db()
        self.assertEqual(analysis.status, Analysis.Status.FAILED)
        self.assertIn("AI key is not configured", analysis.error)
        self.assertIn("NEBIUS_API_KEY", analysis.error)
        self.assertTrue(analysis.reviews.exists())
        self.assertEqual(analysis.current_step, Analysis.Step.CLASSIFYING)


class FeedParsingTests(TestCase):
    def test_parse_feed_xml_reads_text_reviews(self):
        document = b"""<?xml version="1.0" encoding="utf-8"?>
<feed xmlns:im="http://itunes.apple.com/rss" xmlns="http://www.w3.org/2005/Atom">
  <entry>
    <id>101</id><title>Lost my notes</title>
    <content type="text">Sync deleted my work.</content>
    <im:rating>1</im:rating><updated>2026-10-23T16:50:28-07:00</updated><im:version>4.2</im:version>
    <author><name>maria</name></author>
    <content type="html">&lt;b&gt;ignored&lt;/b&gt;</content>
  </entry>
  <entry><id>102</id><title>No rating</title><content type="text">Skipped</content></entry>
</feed>"""
        reviews = parse_feed_xml(document)
        self.assertEqual(len(reviews), 1)
        review = reviews[0]
        self.assertEqual(review.text, "Sync deleted my work.")
        self.assertEqual(review.rating, 1)
        self.assertEqual(review.author, "maria")
        self.assertEqual(review.app_version, "4.2")
        self.assertEqual(review.external_id, "101")
        self.assertIsNotNone(review.review_date)

    def test_parse_feed_page_skips_app_entry(self):
        payload = {
            "feed": {
                "entry": [
                    {"im:name": {"label": "App"}},
                    {
                        "author": {"name": {"label": "Kim"}},
                        "im:rating": {"label": "2"},
                        "title": {"label": "Slow"},
                        "content": {"label": "Takes ages to load"},
                        "id": {"label": "123"},
                        "im:version": {"label": "3.0"},
                        "updated": {"label": "2024-04-01T10:00:00-07:00"},
                    },
                ]
            }
        }
        reviews = parse_feed_page(payload)
        self.assertEqual(len(reviews), 1)
        self.assertEqual(reviews[0].author, "Kim")
        self.assertEqual(reviews[0].rating, 2)
        self.assertEqual(reviews[0].review_date.hour, 10)

    def test_single_entry_dict(self):
        payload = {"feed": {"entry": {"im:rating": {"label": "5"}, "content": {"label": "Nice"}}}}
        self.assertEqual(len(parse_feed_page(payload)), 1)
