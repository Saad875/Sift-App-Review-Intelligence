"""REST endpoints under /api/v1/."""
from __future__ import annotations

from django.conf import settings
from django.http import HttpResponse
from django.shortcuts import get_object_or_404
from rest_framework import generics, serializers, status
from rest_framework.pagination import PageNumberPagination
from rest_framework.response import Response
from rest_framework.throttling import ScopedRateThrottle
from rest_framework.views import APIView

from .exceptions import ServiceUnavailable, UpstreamError
from .models import Analysis, ReplyDraft, Review
from .serializers import (
    AnalysisCreateSerializer,
    AnalysisSerializer,
    BacklogItemSerializer,
    CompetitorInsightSerializer,
    ReplyDraftSerializer,
    ReplyRequestSerializer,
    ReviewSerializer,
    ThemeSerializer,
    normalise_competitors,
)
from .services import pipeline
from .services.llm import LLMClient, LLMConfigurationError, LLMError
from .services.replies import draft_reply
from .services.report import build_markdown
from .services.schemas import CATEGORIES, SENTIMENTS
from .services.sources import itunes
from .services.sources.csv_upload import CSVParseError, parse_csv


class HealthView(APIView):
    """Liveness check that also reports which integrations are configured."""

    def get(self, request):
        return Response(
            {
                "status": "ok",
                "version": "1.0.0",
                "models": {"fast": settings.MODEL_FAST, "reasoning": settings.MODEL_REASONING},
                "configured": {
                    "nebius": bool(settings.NEBIUS_API_KEY and settings.MODEL_FAST and settings.MODEL_REASONING),
                    "tavily": bool(settings.TAVILY_API_KEY),
                },
            }
        )


class AppSearchView(APIView):
    """Search the App Store by name."""

    def get(self, request):
        term = request.query_params.get("q", "").strip()
        country = request.query_params.get("country", "us").strip().lower()
        if len(term) < 2:
            raise serializers.ValidationError({"q": "Enter at least two characters."})
        if len(country) != 2 or not country.isalpha():
            raise serializers.ValidationError({"country": "Use a two letter country code."})
        try:
            apps = itunes.search_apps(term[:100], country)
        except itunes.SourceError as exc:
            raise UpstreamError(str(exc)) from exc
        return Response(
            {
                "results": [
                    {
                        "app_id": app.app_id,
                        "name": app.name,
                        "developer": app.developer,
                        "icon_url": app.icon_url,
                        "rating": app.rating,
                        "rating_count": app.rating_count,
                        "genre": app.genre,
                    }
                    for app in apps
                ]
            }
        )


def _competitors_from(request) -> list[str]:
    data = request.data
    if hasattr(data, "getlist"):
        values = data.getlist("competitors")
        raw = values if len(values) > 1 else (values[0] if values else None)
    else:
        raw = data.get("competitors")
    try:
        return normalise_competitors(raw)
    except serializers.ValidationError as exc:
        raise serializers.ValidationError({"competitors": exc.detail}) from exc


class AnalysisCreateView(APIView):
    """Create an analysis and start the pipeline in the background."""

    throttle_classes = [ScopedRateThrottle]
    throttle_scope = "analysis_create"

    def post(self, request):
        serializer = AnalysisCreateSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        data = serializer.validated_data
        competitors = _competitors_from(request)
        raw_reviews = None

        if data["source"] == Analysis.Source.CSV:
            try:
                raw_reviews = parse_csv(data["file"].read())
            except CSVParseError as exc:
                raise serializers.ValidationError({"file": str(exc)}) from exc
            name = data.get("app_name") or data["file"].name.rsplit(".", 1)[0][:255] or "Uploaded reviews"
            analysis = Analysis.objects.create(
                app_name=name,
                source=Analysis.Source.CSV,
                country=data["country"],
                competitors=competitors,
            )
        else:
            info = None
            if not data.get("app_name") or not data.get("icon_url"):
                try:
                    info = itunes.lookup_app(data["app_id"], data["country"])
                except itunes.SourceError as exc:
                    raise UpstreamError(str(exc)) from exc
                if info is None and not data.get("app_name"):
                    raise serializers.ValidationError(
                        {"app_id": "No App Store app was found with this id in the selected country."}
                    )
            analysis = Analysis.objects.create(
                app_name=data.get("app_name") or info.name,
                app_id=data["app_id"],
                developer=data.get("developer") or (info.developer if info else ""),
                icon_url=data.get("icon_url") or (info.icon_url if info else ""),
                source=Analysis.Source.APPSTORE,
                country=data["country"],
                competitors=competitors,
            )

        pipeline.launch(analysis.pk, raw_reviews)
        return Response(AnalysisSerializer(analysis).data, status=status.HTTP_201_CREATED)


class AnalysisDetailView(APIView):
    """Status, current step, progress and summary statistics."""

    def get(self, request, pk):
        analysis = pipeline.mark_if_stale(get_object_or_404(Analysis, pk=pk))
        return Response(AnalysisSerializer(analysis).data)


class ReviewPagination(PageNumberPagination):
    page_size = 50
    page_size_query_param = "page_size"
    max_page_size = 100


class AnalysisReviewsView(generics.ListAPIView):
    """Paginated reviews, filterable by sentiment and category."""

    serializer_class = ReviewSerializer
    pagination_class = ReviewPagination

    def get_queryset(self):
        analysis = get_object_or_404(Analysis, pk=self.kwargs["pk"])
        queryset = analysis.reviews.select_related("theme")
        sentiment = self.request.query_params.get("sentiment")
        category = self.request.query_params.get("category")
        if sentiment:
            if sentiment not in SENTIMENTS:
                raise serializers.ValidationError({"sentiment": f"Use one of {', '.join(SENTIMENTS)}."})
            queryset = queryset.filter(sentiment=sentiment)
        if category:
            if category not in CATEGORIES:
                raise serializers.ValidationError({"category": f"Use one of {', '.join(CATEGORIES)}."})
            queryset = queryset.filter(category=category)
        return queryset


class _AnalysisChildList(APIView):
    serializer_class: type[serializers.Serializer]
    relation: str

    def get(self, request, pk):
        analysis = get_object_or_404(Analysis, pk=pk)
        items = getattr(analysis, self.relation).all()
        return Response({"results": self.serializer_class(items, many=True).data})


class AnalysisThemesView(_AnalysisChildList):
    serializer_class = ThemeSerializer
    relation = "themes"


class AnalysisBacklogView(_AnalysisChildList):
    serializer_class = BacklogItemSerializer
    relation = "backlog"


class AnalysisCompetitorsView(_AnalysisChildList):
    serializer_class = CompetitorInsightSerializer
    relation = "competitor_insights"


class AnalysisExportView(APIView):
    """Download the whole report as Markdown."""

    def get(self, request, pk):
        analysis = get_object_or_404(Analysis, pk=pk)
        if analysis.status != Analysis.Status.COMPLETED:
            raise serializers.ValidationError("The analysis has not finished yet.")
        response = HttpResponse(build_markdown(analysis), content_type="text/markdown; charset=utf-8")
        safe_name = "".join(ch if ch.isalnum() else "-" for ch in analysis.app_name).strip("-") or "report"
        response["Content-Disposition"] = f'attachment; filename="sift-{safe_name.lower()}.md"'
        return response


class ReplyDraftView(APIView):
    """Draft a developer reply for one review in the requested tone."""

    throttle_classes = [ScopedRateThrottle]
    throttle_scope = "reply_create"

    def post(self, request, pk):
        review = get_object_or_404(Review.objects.select_related("analysis"), pk=pk)
        serializer = ReplyRequestSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        tone = serializer.validated_data["tone"]
        try:
            text = draft_reply(
                LLMClient(),
                app_name=review.analysis.app_name,
                review_text=review.text,
                rating=review.rating,
                tone=tone,
            )
        except LLMConfigurationError as exc:
            raise ServiceUnavailable(str(exc)) from exc
        except LLMError as exc:
            raise UpstreamError(str(exc)) from exc
        draft = ReplyDraft.objects.create(review=review, tone=tone, text=text)
        return Response(ReplyDraftSerializer(draft).data, status=status.HTTP_201_CREATED)
