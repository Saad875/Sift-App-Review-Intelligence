from django.apps import AppConfig


class AnalysesConfig(AppConfig):
    """App holding analyses, reviews and every AI-generated artifact."""

    default_auto_field = "django.db.models.BigAutoField"
    name = "analyses"
