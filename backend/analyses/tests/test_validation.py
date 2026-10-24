"""Unit tests for JSON extraction, schema validation and the retry policy."""
from __future__ import annotations

import json

from django.test import SimpleTestCase, override_settings

from analyses.services import schemas
from analyses.services.classify import ReviewInput, classify_batch, valid_items
from analyses.services.llm import FAST, LLMClient, LLMError
from analyses.services.validation import (
    ModelOutputError,
    extract_json,
    parse_and_validate,
    schema_errors,
    strip_reasoning,
)

from .fakes import FakeOpenAI

MODELS = {"MODEL_FAST": "test-nano", "MODEL_REASONING": "test-super", "NEBIUS_API_KEY": "k"}


class ExtractJsonTests(SimpleTestCase):
    def test_plain_object(self):
        self.assertEqual(extract_json('{"a": 1}'), {"a": 1})

    def test_markdown_fence(self):
        self.assertEqual(extract_json('Here you go:\n```json\n{"a": [1, 2]}\n```'), {"a": [1, 2]})

    def test_think_block_is_removed(self):
        text = '<think>The user wants {"wrong": true}</think>\n{"right": true}'
        self.assertEqual(extract_json(text), {"right": True})

    def test_unterminated_think_prefix(self):
        self.assertEqual(strip_reasoning('reasoning... </think> {"x": 1}'), '{"x": 1}')

    def test_surrounding_prose(self):
        self.assertEqual(extract_json('Sure! {"ok": "yes"} Hope that helps.'), {"ok": "yes"})

    def test_top_level_array(self):
        self.assertEqual(extract_json("[1, 2, 3]"), [1, 2, 3])

    def test_empty_raises(self):
        with self.assertRaises(ModelOutputError):
            extract_json("   ")
        with self.assertRaises(ModelOutputError):
            extract_json(None)

    def test_garbage_raises(self):
        with self.assertRaises(ModelOutputError):
            extract_json("not json at all {broken")


class SchemaTests(SimpleTestCase):
    def _item(self, **overrides):
        item = {"id": 1, "sentiment": "negative", "category": "bug", "severity": 4, "summary": "Crashes."}
        item.update(overrides)
        return item

    def test_valid_classification(self):
        self.assertEqual(schema_errors(self._item(), schemas.CLASSIFICATION_ITEM), [])

    def test_rejects_unknown_category(self):
        self.assertTrue(schema_errors(self._item(category="crash"), schemas.CLASSIFICATION_ITEM))

    def test_rejects_out_of_range_severity(self):
        self.assertTrue(schema_errors(self._item(severity=9), schemas.CLASSIFICATION_ITEM))
        self.assertTrue(schema_errors(self._item(severity=0), schemas.CLASSIFICATION_ITEM))

    def test_rejects_missing_field(self):
        item = self._item()
        del item["summary"]
        self.assertTrue(schema_errors(item, schemas.CLASSIFICATION_ITEM))

    def test_backlog_priority_bounds(self):
        item = {
            "title": "Fix login", "type": "bug", "priority_score": 101, "reasoning": "r", "user_impact": "u",
            "suggested_fix": "f", "effort": "S", "theme_id": None, "evidence_review_ids": [],
        }
        self.assertTrue(schema_errors({"items": [item]}, schemas.BACKLOG))
        item["priority_score"] = 80
        self.assertEqual(schema_errors({"items": [item]}, schemas.BACKLOG), [])

    def test_parse_and_validate_raises_with_message(self):
        with self.assertRaisesMessage(ModelOutputError, "reply"):
            parse_and_validate('{"text": "hi"}', schemas.REPLY)

    def test_valid_items_filters_bad_and_unexpected(self):
        results = [
            self._item(id=1),
            self._item(id=2, sentiment="angry"),
            self._item(id=99),
            self._item(id=1, summary="duplicate"),
        ]
        accepted = valid_items(results, {1, 2})
        self.assertEqual(list(accepted), [1])
        self.assertEqual(accepted[1].summary, "Crashes.")


@override_settings(**MODELS)
class RetryPolicyTests(SimpleTestCase):
    def test_retries_once_then_succeeds(self):
        fake = FakeOpenAI(["not json", '{"reply": "Thanks!"}'])
        result = LLMClient(fake).chat_json(FAST, "sys", "user", schemas.REPLY)
        self.assertEqual(result, {"reply": "Thanks!"})
        self.assertEqual(len(fake.calls), 2)
        self.assertIn("invalid", fake.calls[1]["messages"][-1]["content"])
        self.assertEqual(fake.calls[0]["response_format"], {"type": "json_object"})
        self.assertEqual(fake.calls[0]["model"], "test-nano")

    def test_fails_after_second_invalid_output(self):
        fake = FakeOpenAI(['{"wrong": 1}', '{"still": "wrong"}'])
        with self.assertRaises(LLMError):
            LLMClient(fake).chat_json(FAST, "sys", "user", schemas.REPLY)
        self.assertEqual(len(fake.calls), 2)

    def test_falls_back_when_json_mode_unsupported(self):
        fake = FakeOpenAI([RuntimeError("response_format is not supported"), '{"reply": "ok"}'])
        result = LLMClient(fake).chat_json(FAST, "sys", "user", schemas.REPLY)
        self.assertEqual(result["reply"], "ok")
        self.assertNotIn("response_format", fake.calls[1])

    def test_provider_errors_become_llm_errors(self):
        class APITimeoutError(Exception):
            pass

        fake = FakeOpenAI([APITimeoutError("slow")])
        with self.assertRaisesMessage(LLMError, "timed out"):
            LLMClient(fake).chat_json(FAST, "sys", "user", schemas.REPLY)

    @override_settings(MODEL_FAST="")
    def test_missing_model_is_a_configuration_error(self):
        with self.assertRaisesMessage(LLMError, "MODEL_FAST"):
            LLMClient(FakeOpenAI([])).chat_json(FAST, "sys", "user", schemas.REPLY)


@override_settings(**MODELS)
class ClassifyBatchTests(SimpleTestCase):
    def _result(self, review_id, sentiment="positive"):
        return {"id": review_id, "sentiment": sentiment, "category": "praise", "severity": 1, "summary": "Likes it."}

    def test_missing_items_are_retried_once(self):
        batch = [ReviewInput(id=i, text=f"Review {i}") for i in (1, 2, 3)]
        first = json.dumps({"results": [self._result(1), self._result(2)]})
        second = json.dumps({"results": [self._result(3)]})
        fake = FakeOpenAI([first, second])
        accepted, error = classify_batch(LLMClient(fake), batch)
        self.assertEqual(sorted(accepted), [1, 2, 3])
        self.assertEqual(error, "")
        self.assertIn('"id": 3', fake.calls[1]["messages"][1]["content"])

    def test_item_fails_gracefully_without_breaking_batch(self):
        batch = [ReviewInput(id=i, text=f"Review {i}") for i in (1, 2)]
        good_one = json.dumps({"results": [self._result(1), {"id": 2, "sentiment": "??"}]})
        still_bad = json.dumps({"results": [{"id": 2, "sentiment": "??"}]})
        fake = FakeOpenAI([good_one, still_bad])
        accepted, error = classify_batch(LLMClient(fake), batch)
        self.assertEqual(list(accepted), [1])
        self.assertTrue(error)
