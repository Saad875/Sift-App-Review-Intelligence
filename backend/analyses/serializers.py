"""Serializers for request validation and API responses."""
from __future__ import annotations

import re

from django.conf import settings
from rest_framework import serializers

from .models import Analysis, BacklogItem, CompetitorInsight, ReplyDraft, Review, Theme
from .services.stats import summary_stats

MAX_COMPETITORS = 3
COUNTRY_PATTERN = re.compile(r"^[a-zA-Z]{2}$")

PIPELINE_STEPS = [
    (Analysis.Step.FETCHING, None),
    (Analysis.Step.CLASSIFYING, "fast"),
    (Analysis.Step.CLUSTERING, "reasoning"),
    (Analysis.Step.PRIORITIZING, "reasoning"),
    (Analysis.Step.RESEARCHING, "reasoning"),
]


def normalise_competitors(value) -> list[str]:
    """Accept a list, a JSON-ish comma separated string or repeated form fields."""
    if value in (None, ""):
        return []
    items = value if isinstance(value, (list, tuple)) else str(value).strip("[]").split(",")
    cleaned: list[str] = []
    for item in items:
        name = str(item).strip().strip('"').strip("'").strip()
        if name and name.lower() not in {c.lower() for c in cleaned}:
            cleaned.append(name[:80])
    if len(cleaned) > MAX_COMPETITORS:
        raise serializers.ValidationError(f"At most {MAX_COMPETITORS} competitors are allowed.")
    return cleaned


class AnalysisCreateSerializer(serializers.Serializer):
    """Validates POST /analyses/ for both App Store and CSV sources."""

    source = serializers.ChoiceField(choices=Analysis.Source.choices)
    app_id = serializers.RegexField(r"^\d{1,20}$", required=False, allow_blank=True)
    country = serializers.CharField(required=False, default="us", max_length=2)
    app_name = serializers.CharField(required=False, allow_blank=True, max_length=255)
    developer = serializers.CharField(required=False, allow_blank=True, max_length=255)
    icon_url = serializers.URLField(required=False, allow_blank=True, max_length=1024)
    file = serializers.FileField(required=False)

    def validate_country(self, value: str) -> str:
        if not COUNTRY_PATTERN.match(value or ""):
            raise serializers.ValidationError("Use a two letter country code such as 'us'.")
        return value.lower()

    def validate(self, attrs: dict) -> dict:
        if attrs["source"] == Analysis.Source.APPSTORE and not attrs.get("app_id"):
            raise serializers.ValidationError({"app_id": "An App Store app id is required."})
        if attrs["source"] == Analysis.Source.CSV and not attrs.get("file"):
            raise serializers.ValidationError({"file": "Attach a CSV file of reviews."})
        return attrs


class StepSerializerMixin:
    @staticmethod
    def build_steps(analysis: Analysis) -> list[dict]:
        keys = [key for key, _ in PIPELINE_STEPS]
        current = keys.index(analysis.current_step) if analysis.current_step in keys else -1
        models = {"fast": settings.MODEL_FAST, "reasoning": settings.MODEL_REASONING}
        steps = []
        for index, (key, role) in enumerate(PIPELINE_STEPS):
            if key == Analysis.Step.RESEARCHING and not analysis.competitors:
                state = "skipped"
            elif analysis.status == Analysis.Status.COMPLETED:
                state = "done"
            elif analysis.status == Analysis.Status.QUEUED or index > current:
                state = "waiting"
            elif index < current:
                state = "done"
            elif analysis.status == Analysis.Status.FAILED:
                state = "failed"
            else:
                state = "running"
            steps.append(
                {
                    "key": key,
                    "label": Analysis.Step(key).label,
                    "state": state,
                    "model_role": role,
                    "model": models[role] if role else None,
                }
            )
        return steps


class AnalysisSerializer(serializers.ModelSerializer, StepSerializerMixin):
    """Full analysis detail including live progress and summary stats."""

    steps = serializers.SerializerMethodField()
    stats = serializers.SerializerMethodField()

    class Meta:
        model = Analysis
        fields = [
            "id", "app_name", "app_id", "developer", "icon_url", "source", "country", "competitors",
            "status", "current_step", "progress", "error", "warnings", "created_at", "updated_at",
            "completed_at", "steps", "stats",
        ]

    def get_steps(self, obj: Analysis) -> list[dict]:
        return self.build_steps(obj)

    def get_stats(self, obj: Analysis) -> dict:
        return summary_stats(obj)


class ReviewSerializer(serializers.ModelSerializer):
    theme_id = serializers.IntegerField(source="theme.id", read_only=True, default=None)
    theme_title = serializers.CharField(source="theme.title", read_only=True, default=None)

    class Meta:
        model = Review
        fields = [
            "id", "author", "title", "text", "rating", "review_date", "app_version", "sentiment",
            "category", "severity", "summary", "theme_id", "theme_title",
        ]


class ThemeSerializer(serializers.ModelSerializer):
    class Meta:
        model = Theme
        fields = ["id", "position", "title", "description", "review_count", "negative_share", "quotes"]


class BacklogItemSerializer(serializers.ModelSerializer):
    type = serializers.CharField(source="item_type")
    theme_id = serializers.IntegerField(source="theme.id", read_only=True, default=None)
    theme_title = serializers.CharField(source="theme.title", read_only=True, default=None)

    class Meta:
        model = BacklogItem
        fields = [
            "id", "rank", "title", "type", "priority_score", "reasoning", "user_impact", "suggested_fix",
            "effort", "theme_id", "theme_title", "evidence_quotes",
        ]


class CompetitorInsightSerializer(serializers.ModelSerializer):
    class Meta:
        model = CompetitorInsight
        fields = ["id", "name", "summary", "strengths", "weaknesses", "opportunities", "sources", "error"]


class ReplyRequestSerializer(serializers.Serializer):
    tone = serializers.ChoiceField(choices=ReplyDraft.Tone.choices, default=ReplyDraft.Tone.FRIENDLY)


class ReplyDraftSerializer(serializers.ModelSerializer):
    review_id = serializers.IntegerField(source="review.id", read_only=True)

    class Meta:
        model = ReplyDraft
        fields = ["id", "review_id", "tone", "text", "created_at"]
