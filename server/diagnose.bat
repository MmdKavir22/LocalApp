@echo off
rem Prints why the phone may not be able to connect (firewall, addresses, network profile).
cd /d "%~dp0"
where py >nul 2>nul && (set "PY=py -3") || (set "PY=python")
%PY% run.py --diagnose
pause
