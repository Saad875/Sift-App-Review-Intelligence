"""Consistent JSON error responses: {"error": {"code": "...", "message": "..."}}."""
from __future__ import annotations

from typing import Any

from rest_framework import status
from rest_framework.exceptions import APIException, Throttled, ValidationError
from rest_framework.response import Response
from rest_framework.views import exception_handler


class ServiceUnavailable(APIException):
    status_code = status.HTTP_503_SERVICE_UNAVAILABLE
    default_code = "service_unavailable"
    default_detail = "A required upstream service is unavailable."


class UpstreamError(APIException):
    status_code = status.HTTP_502_BAD_GATEWAY
    default_code = "upstream_error"
    default_detail = "An upstream service returned an error."


def _first_message(detail: Any) -> str:
    if isinstance(detail, dict):
        for key, value in detail.items():
            message = _first_message(value)
            return message if key == "non_field_errors" else f"{key}: {message}"
    if isinstance(detail, list) and detail:
        return _first_message(detail[0])
    return str(detail)


def api_exception_handler(exc: Exception, context: dict) -> Response | None:
    response = exception_handler(exc, context)
    if response is None:
        return None
    if isinstance(exc, ValidationError):
        code, message = "invalid_request", _first_message(exc.detail)
    elif isinstance(exc, Throttled):
        code = "rate_limited"
        message = "Too many requests. Please wait a moment and try again."
    elif isinstance(exc, APIException):
        code = exc.get_codes() if isinstance(exc.get_codes(), str) else exc.default_code
        message = _first_message(exc.detail)
    elif response.status_code == status.HTTP_404_NOT_FOUND:
        code, message = "not_found", "The requested item was not found."
    else:
        code, message = "error", "Request failed."
    payload: dict[str, Any] = {"error": {"code": code, "message": message}}
    if isinstance(exc, Throttled) and exc.wait is not None:
        payload["error"]["retry_after_seconds"] = int(exc.wait)
    response.data = payload
    return response
