from django.urls import path

from . import views

urlpatterns = [
    path("health/", views.HealthView.as_view(), name="health"),
    path("apps/search/", views.AppSearchView.as_view(), name="app-search"),
    path("analyses/", views.AnalysisCreateView.as_view(), name="analysis-create"),
    path("analyses/<uuid:pk>/", views.AnalysisDetailView.as_view(), name="analysis-detail"),
    path("analyses/<uuid:pk>/reviews/", views.AnalysisReviewsView.as_view(), name="analysis-reviews"),
    path("analyses/<uuid:pk>/themes/", views.AnalysisThemesView.as_view(), name="analysis-themes"),
    path("analyses/<uuid:pk>/backlog/", views.AnalysisBacklogView.as_view(), name="analysis-backlog"),
    path("analyses/<uuid:pk>/competitors/", views.AnalysisCompetitorsView.as_view(), name="analysis-competitors"),
    path("analyses/<uuid:pk>/export/", views.AnalysisExportView.as_view(), name="analysis-export"),
    path("reviews/<int:pk>/reply/", views.ReplyDraftView.as_view(), name="review-reply"),
]
