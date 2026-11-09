@echo off
rem Installs the required packages into the Python installed on Windows (no virtual environment).
cd /d "%~dp0"
where py >nul 2>nul && (set "PY=py -3") || (set "PY=python")
%PY% -m pip install --upgrade -r requirements.txt
pause
