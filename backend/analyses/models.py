"""Database models for analyses and the artifacts the AI pipeline produces."""
from __future__ import annotations

import uuid

from django.db import models


class Analysis(models.Model):
    """One run of the review intelligence pipeline for a single app."""

    class Source(models.TextChoices):
        APPSTORE = "appstore", "App Store"
        CSV = "csv", "CSV upload"

    class Status(models.TextChoices):
        QUEUED = "queued", "Queued"
        RUNNING = "running", "Running"
        COMPLETED = "completed", "Completed"
        FAILED = "failed", "Failed"

    class Step(models.TextChoices):
        QUEUED = "queued", "Queued"
        FETCHING = "fetching", "Fetching reviews"
        CLASSIFYING = "classifying", "Classifying reviews"
        CLUSTERING = "clustering", "Finding themes"
        PRIORITIZING = "prioritizing", "Building the backlog"
        RESEARCHING = "researching", "Researching competitors"
        DONE = "done", "Done"

    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    app_name = models.CharField(max_length=255)
    app_id = models.CharField(max_length=64, blank=True)
    developer = models.CharField(max_length=255, blank=True)
    icon_url = models.URLField(max_length=1024, blank=True)
    source = models.CharField(max_length=16, choices=Source.choices)
    country = models.CharField(max_length=2, default="us")
    competitors = models.JSONField(default=list, blank=True)
    status = models.CharField(max_length=16, choices=Status.choices, default=Status.QUEUED)
    current_step = models.CharField(max_length=16, choices=Step.choices, default=Step.QUEUED)
    progress = models.PositiveSmallIntegerField(default=0)
    error = models.TextField(blank=True)
    warnings = models.JSONField(default=list, blank=True)
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)
    completed_at = models.DateTimeField(null=True, blank=True)

    class Meta:
        ordering = ["-created_at"]

    def __str__(self) -> str:
        return f"{self.app_name} ({self.status})"


class Review(models.Model):
    """A single user review plus its AI classification."""

    class Sentiment(models.TextChoices):
        POSITIVE = "positive", "Positive"
        NEUTRAL = "neutral", "Neutral"
        NEGATIVE = "negative", "Negative"

    class Category(models.TextChoices):
        BUG = "bug", "Bug"
        PERFORMANCE = "performance", "Performance"
        UI_UX = "ui_ux", "UI and UX"
        FEATURE_REQUEST = "feature_request", "Feature request"
        PRICING = "pricing", "Pricing"
        ADS = "ads", "Ads"
        ACCOUNT = "account", "Account"
        PRAISE = "praise", "Praise"
        OTHER = "other", "Other"

    analysis = models.ForeignKey(Analysis, on_delete=models.CASCADE, related_name="reviews")
    external_id = models.CharField(max_length=128, blank=True)
    author = models.CharField(max_length=255, blank=True)
    title = models.CharField(max_length=512, blank=True)
    text = models.TextField()
    rating = models.PositiveSmallIntegerField(null=True, blank=True)
    review_date = models.DateTimeField(null=True, blank=True)
    app_version = models.CharField(max_length=64, blank=True)
    sentiment = models.CharField(max_length=16, choices=Sentiment.choices, blank=True)
    category = models.CharField(max_length=32, choices=Category.choices, blank=True)
    severity = models.PositiveSmallIntegerField(null=True, blank=True)
    summary = models.CharField(max_length=512, blank=True)
    classification_error = models.CharField(max_length=255, blank=True)
    theme = models.ForeignKey(
        "Theme", on_delete=models.SET_NULL, null=True, blank=True, related_name="reviews"
    )

    class Meta:
        ordering = ["-review_date", "id"]
        indexes = [models.Index(fields=["analysis", "sentiment", "category"])]

    @property
    def is_classified(self) -> bool:
        return bool(self.sentiment and self.category)


class Theme(models.Model):
    """A cluster of related feedback found by the reasoning model."""

    analysis = models.ForeignKey(Analysis, on_delete=models.CASCADE, related_name="themes")
    position = models.PositiveSmallIntegerField(default=0)
    title = models.CharField(max_length=255)
    description = models.TextField()
    review_count = models.PositiveIntegerField(default=0)
    negative_share = models.FloatField(default=0.0)
    quotes = models.JSONField(default=list)

    class Meta:
        ordering = ["position"]


class BacklogItem(models.Model):
    """A ranked bug fix or feature suggestion derived from the themes."""

    class ItemType(models.TextChoices):
        BUG = "bug", "Bug"
        FEATURE = "feature", "Feature"

    class Effort(models.TextChoices):
        S = "S", "Small"
        M = "M", "Medium"
        L = "L", "Large"

    analysis = models.ForeignKey(Analysis, on_delete=models.CASCADE, related_name="backlog")
    rank = models.PositiveSmallIntegerField()
    title = models.CharField(max_length=255)
    item_type = models.CharField(max_length=16, choices=ItemType.choices)
    priority_score = models.PositiveSmallIntegerField()
    reasoning = models.TextField()
    user_impact = models.TextField()
    suggested_fix = models.TextField()
    effort = models.CharField(max_length=1, choices=Effort.choices)
    theme = models.ForeignKey(
        Theme, on_delete=models.SET_NULL, null=True, blank=True, related_name="backlog_items"
    )
    evidence_quotes = models.JSONField(default=list)

    class Meta:
        ordering = ["rank"]


class CompetitorInsight(models.Model):
    """Web-researched comparison against one named competitor."""

    analysis = models.ForeignKey(Analysis, on_delete=models.CASCADE, related_name="competitor_insights")
    name = models.CharField(max_length=255)
    summary = models.TextField(blank=True)
    strengths = models.JSONField(default=list)
    weaknesses = models.JSONField(default=list)
    opportunities = models.JSONField(default=list)
    sources = models.JSONField(default=list)
    error = models.CharField(max_length=512, blank=True)

    class Meta:
        ordering = ["id"]


class ReplyDraft(models.Model):
    """A developer reply drafted by the reasoning model for one review."""

    class Tone(models.TextChoices):
        FRIENDLY = "friendly", "Friendly"
        FORMAL = "formal", "Formal"
        APOLOGETIC = "apologetic", "Apologetic"

    review = models.ForeignKey(Review, on_delete=models.CASCADE, related_name="reply_drafts")
    tone = models.CharField(max_length=16, choices=Tone.choices)
    text = models.TextField()
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ["-created_at"]
