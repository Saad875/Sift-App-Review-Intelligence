"""Root URL configuration. Every public endpoint is versioned under /api/v1/."""
from django.urls import include, path

urlpatterns = [
    path("api/v1/", include("analyses.urls")),
]
