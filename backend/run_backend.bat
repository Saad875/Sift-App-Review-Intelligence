@echo off
title Sift backend
cd /d "%~dp0"

echo Applying database migrations...
".venv\Scripts\python.exe" manage.py migrate --noinput

set ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe
if exist "%ADB%" (
    echo Forwarding phone port 8000 to this PC...
    "%ADB%" reverse tcp:8000 tcp:8000
)

echo.
echo Sift backend running at http://localhost:8000/  ^(health: http://localhost:8000/api/v1/health/^)
echo Keep this window open. Press Ctrl+C to stop.
echo.
".venv\Scripts\python.exe" manage.py runserver 0.0.0.0:8000
pause
