@echo off
rem Installs the autostart task: the server will start as ADMINISTRATOR at every Windows logon.
rem Asks for administrator permission (UAC) once, now.
cd /d "%~dp0"
fltmc >nul 2>&1
if errorlevel 1 (
    echo Requesting administrator permission...
    powershell -NoProfile -Command "Start-Process -FilePath '%~f0' -Verb RunAs"
    exit /b
)
where py >nul 2>nul && (set "PY=py -3") || (set "PY=python")
%PY% run.py --install-autostart
pause
