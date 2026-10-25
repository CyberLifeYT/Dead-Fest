@echo off
rem Dead-Fest Xbox Death Detector launcher. First run sets up a private Python environment.
setlocal
cd /d "%~dp0"
if not exist ".venv\Scripts\python.exe" (
    echo Setting up the detector for the first time...
    py -3 -m venv .venv || python -m venv .venv || goto :nopython
    ".venv\Scripts\python.exe" -m pip install --upgrade pip >nul
    ".venv\Scripts\python.exe" -m pip install -r requirements.txt || goto :failed
)
if "%~1"=="" (
    ".venv\Scripts\python.exe" -m deadfest_detector run
) else (
    ".venv\Scripts\python.exe" -m deadfest_detector %*
)
exit /b %errorlevel%

:nopython
echo Python 3.11 or newer is needed: https://www.python.org/downloads/windows/
exit /b 1

:failed
echo Installing the detector's packages failed. Check your internet connection and try again.
exit /b 1
