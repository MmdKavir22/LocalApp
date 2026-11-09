@echo off
rem Runs the server with the Python installed on Windows (no virtual environment).
cd /d "%~dp0"
where py >nul 2>nul && (set "PY=py -3") || (set "PY=python")
%PY% run.py %*
if errorlevel 1 if not errorlevel 3 pause
