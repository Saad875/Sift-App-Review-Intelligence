FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    SQLITE_PATH=/app/data/db.sqlite3

WORKDIR /app

COPY requirements.txt .
RUN pip install --upgrade pip && pip install -r requirements.txt

COPY . .
RUN useradd --create-home --uid 1000 sift \
    && mkdir -p /app/data \
    && chown -R sift:sift /app
USER sift

EXPOSE 8000
VOLUME ["/app/data"]

# One worker process keeps the in-process rate limiter and background
# pipeline threads consistent; threads handle concurrent requests.
CMD ["sh", "-c", "python manage.py migrate --noinput && gunicorn config.wsgi:application --bind 0.0.0.0:8000 --workers 1 --threads 8 --timeout 120"]
