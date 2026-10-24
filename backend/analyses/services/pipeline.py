"""The five step analysis pipeline, run in a background thread.

Status, current step and progress are saved after every step (and after every
classification batch) so the Android app can show live progress by polling.
A failure in one AI step is recorded as a warning and the run continues; only
problems that leave nothing to analyse fail the whole analysis.
"""
from __future__ import annotations

import logging
import threading
from collections import Counter
from datetime import timedelta

from django.db import close_old_connections, transaction
from django.utils import timezone

from ..models import Analysis, BacklogItem, CompetitorInsight, Review, Theme
from .classify import ReviewInput, batched, classify_batch
from .competitors import CompetitorResearcher
from .insights import SummarisedReview, ThemeResult, build_backlog, build_themes
from .llm import FAST, REASONING, LLMClient, LLMConfigurationError, LLMError
from .sources import itunes
from .sources.csv_upload import deduplicate
from .sources.itunes import RawReview, SourceError

logger = logging.getLogger(__name__)

STALE_AFTER = timedelta(minutes=20)

# Progress percentage reached at the end of each step.
PROGRESS = {
    Analysis.Step.FETCHING: 10,
    Analysis.Step.CLASSIFYING: 60,
    Analysis.Step.CLUSTERING: 75,
    Analysis.Step.PRIORITIZING: 88,
    Analysis.Step.RESEARCHING: 98,
}


class PipelineAbort(RuntimeError):
    """Stops the pipeline with a message shown to the user."""


class Pipeline:
    """Runs every step for one analysis."""

    def __init__(
        self,
        analysis_id,
        raw_reviews: list[RawReview] | None = None,
        llm: LLMClient | None = None,
        researcher: CompetitorResearcher | None = None,
    ) -> None:
        self.analysis = Analysis.objects.get(pk=analysis_id)
        self.raw_reviews = raw_reviews
        self.llm = llm or LLMClient()
        self.researcher = researcher or CompetitorResearcher(self.llm)

    # --- state helpers -------------------------------------------------

    def _update(self, **fields) -> None:
        for key, value in fields.items():
            setattr(self.analysis, key, value)
        self.analysis.save(update_fields=[*fields.keys(), "updated_at"])

    def _enter(self, step: str) -> None:
        self._update(status=Analysis.Status.RUNNING, current_step=step)

    def _finish_step(self, step: str) -> None:
        self._update(progress=PROGRESS[step])

    def _warn(self, message: str) -> None:
        self._update(warnings=[*self.analysis.warnings, message])

    # --- run ------------------------------------------------------------

    def run(self) -> None:
        try:
            self._check_configuration()
            self._fetch()
            if not self.analysis.reviews.exists():
                self._complete()
                return
            self._classify()
            themes = self._themes()
            self._backlog(themes)
            if self.analysis.competitors:
                self._competitors(themes)
            self._complete()
        except PipelineAbort as exc:
            self._fail(str(exc))
        except Exception:  # Unexpected bug: never leave an analysis stuck as running.
            logger.exception("Pipeline crashed for analysis %s", self.analysis.pk)
            self._fail("Something went wrong while analysing reviews. Please try again.")

    def _check_configuration(self) -> None:
        try:
            _ = FAST.model, REASONING.model
            _ = self.llm.client
        except LLMConfigurationError as exc:
            raise PipelineAbort(str(exc)) from exc

    def _complete(self) -> None:
        self._update(
            status=Analysis.Status.COMPLETED,
            current_step=Analysis.Step.DONE,
            progress=100,
            completed_at=timezone.now(),
        )

    def _fail(self, message: str) -> None:
        self._update(status=Analysis.Status.FAILED, error=message)

    # --- step 1 ---------------------------------------------------------

    def _fetch(self) -> None:
        self._enter(Analysis.Step.FETCHING)
        if self.raw_reviews is None:
            try:
                raw = itunes.fetch_reviews(self.analysis.app_id, self.analysis.country)
            except SourceError as exc:
                raise PipelineAbort(str(exc)) from exc
        else:
            raw = self.raw_reviews
        unique = deduplicate(raw)
        Review.objects.bulk_create(
            [
                Review(
                    analysis=self.analysis,
                    external_id=r.external_id[:128],
                    author=r.author[:255],
                    title=r.title[:512],
                    text=r.text,
                    rating=r.rating,
                    review_date=r.review_date,
                    app_version=r.app_version[:64],
                )
                for r in unique
            ]
        )
        if not unique:
            self._warn("No reviews were found for this app.")
        self._finish_step(Analysis.Step.FETCHING)

    # --- step 2 ---------------------------------------------------------

    def _classify(self) -> None:
        self._enter(Analysis.Step.CLASSIFYING)
        reviews = list(self.analysis.reviews.all())
        inputs = [ReviewInput(id=r.id, text=r.text, title=r.title, rating=r.rating) for r in reviews]
        by_id = {r.id: r for r in reviews}
        batches = list(batched(inputs))
        start, end = PROGRESS[Analysis.Step.FETCHING], PROGRESS[Analysis.Step.CLASSIFYING]
        failures = 0
        for index, batch in enumerate(batches, start=1):
            accepted, error = classify_batch(self.llm, batch)
            changed = []
            for item in batch:
                review = by_id[item.id]
                result = accepted.get(item.id)
                if result:
                    review.sentiment = result.sentiment
                    review.category = result.category
                    review.severity = result.severity
                    review.summary = result.summary[:512]
                    review.classification_error = ""
                else:
                    failures += 1
                    review.classification_error = (error or "Not classified.")[:255]
                changed.append(review)
            Review.objects.bulk_update(
                changed, ["sentiment", "category", "severity", "summary", "classification_error"]
            )
            self._update(progress=start + round((end - start) * index / len(batches)))
        if failures == len(inputs):
            raise PipelineAbort("The AI model could not classify any reviews. Please try again later.")
        if failures:
            self._warn(f"{failures} of {len(inputs)} reviews could not be classified and were skipped.")
        self._finish_step(Analysis.Step.CLASSIFYING)

    def _summaries(self) -> list[SummarisedReview]:
        return [
            SummarisedReview(
                id=r.id,
                summary=r.summary,
                sentiment=r.sentiment,
                category=r.category,
                severity=r.severity or 1,
                rating=r.rating,
                text=r.text,
            )
            for r in self.analysis.reviews.exclude(sentiment="").exclude(category="")
        ]

    # --- step 3 ---------------------------------------------------------

    def _themes(self) -> list[tuple[Theme, ThemeResult]]:
        self._enter(Analysis.Step.CLUSTERING)
        try:
            results = build_themes(self.llm, self._summaries())
        except (LLMError, ValueError) as exc:
            self._warn(f"Theme clustering failed: {exc}")
            self._finish_step(Analysis.Step.CLUSTERING)
            return []
        saved: list[tuple[Theme, ThemeResult]] = []
        with transaction.atomic():
            for position, result in enumerate(results):
                theme = Theme.objects.create(
                    analysis=self.analysis,
                    position=position,
                    title=result.title[:255],
                    description=result.description,
                    review_count=result.review_count,
                    negative_share=result.negative_share,
                    quotes=result.quotes,
                )
                Review.objects.filter(analysis=self.analysis, id__in=result.review_ids).update(theme=theme)
                saved.append((theme, result))
        self._finish_step(Analysis.Step.CLUSTERING)
        return saved

    # --- step 4 ---------------------------------------------------------

    def _backlog(self, themes: list[tuple[Theme, ThemeResult]]) -> None:
        self._enter(Analysis.Step.PRIORITIZING)
        summaries = self._summaries()
        categories = dict(Counter(s.category for s in summaries))
        try:
            items = build_backlog(self.llm, [result for _, result in themes], summaries, categories)
        except (LLMError, ValueError) as exc:
            self._warn(f"Backlog generation failed: {exc}")
            self._finish_step(Analysis.Step.PRIORITIZING)
            return
        BacklogItem.objects.bulk_create(
            [
                BacklogItem(
                    analysis=self.analysis,
                    rank=rank,
                    title=item.title[:255],
                    item_type=item.item_type,
                    priority_score=item.priority_score,
                    reasoning=item.reasoning,
                    user_impact=item.user_impact,
                    suggested_fix=item.suggested_fix,
                    effort=item.effort,
                    theme=themes[item.theme_index][0] if item.theme_index is not None else None,
                    evidence_quotes=item.evidence_quotes,
                )
                for rank, item in enumerate(items, start=1)
            ]
        )
        self._finish_step(Analysis.Step.PRIORITIZING)

    # --- step 5 ---------------------------------------------------------

    def _competitors(self, themes: list[tuple[Theme, ThemeResult]]) -> None:
        self._enter(Analysis.Step.RESEARCHING)
        titles = [theme.title for theme, _ in themes]
        failures = 0
        for name in self.analysis.competitors:
            result = self.researcher.research(name, self.analysis.app_name, titles)
            if result.error:
                failures += 1
            CompetitorInsight.objects.create(
                analysis=self.analysis,
                name=result.name[:255],
                summary=result.summary,
                strengths=result.strengths,
                weaknesses=result.weaknesses,
                opportunities=result.opportunities,
                sources=result.sources,
                error=result.error[:512],
            )
        if failures:
            self._warn(f"Research failed for {failures} competitor(s).")
        self._finish_step(Analysis.Step.RESEARCHING)


def run_pipeline(analysis_id, raw_reviews: list[RawReview] | None = None) -> None:
    """Thread target: run the pipeline and release the DB connection."""
    close_old_connections()
    try:
        Pipeline(analysis_id, raw_reviews).run()
    finally:
        close_old_connections()


def launch(analysis_id, raw_reviews: list[RawReview] | None = None) -> None:
    """Start the pipeline in a daemon thread and return immediately."""
    thread = threading.Thread(
        target=run_pipeline, args=(analysis_id, raw_reviews), name=f"pipeline-{analysis_id}", daemon=True
    )
    thread.start()


def mark_if_stale(analysis: Analysis) -> Analysis:
    """Fail analyses whose worker died (for example after a server restart)."""
    active = analysis.status in (Analysis.Status.QUEUED, Analysis.Status.RUNNING)
    if active and timezone.now() - analysis.updated_at > STALE_AFTER:
        analysis.status = Analysis.Status.FAILED
        analysis.error = "The analysis was interrupted. Please start it again."
        analysis.save(update_fields=["status", "error", "updated_at"])
    return analysis
