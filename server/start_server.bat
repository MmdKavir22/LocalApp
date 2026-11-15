@echo off
rem Runs the server with the Python installed on Windows (no virtual environment), always as administrator.
cd /d "%~dp0"
fltmc >nul 2>&1
if errorlevel 1 (
    if "%~1"=="" (
        powershell -NoProfile -Command "Start-Process -FilePath '%~f0' -Verb RunAs"
    ) else (
        powershell -NoProfile -Command "Start-Process -FilePath '%~f0' -ArgumentList '%*' -Verb RunAs"
    )
    exit /b
)
where py >nul 2>nul && (set "PY=py -3") || (set "PY=python")
%PY% run.py %*
if errorlevel 1 if not errorlevel 3 pause
