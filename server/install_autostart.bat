@echo off
rem Starts the server automatically when you log in to Windows (Task Scheduler) and restarts it after a crash.
cd /d "%~dp0"
where py >nul 2>nul && (set "PY=py -3") || (set "PY=python")
%PY% run.py --install-autostart
pause
