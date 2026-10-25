@echo off
rem Dead-Fest Xbox Death Detector launcher.
rem Double-click: a menu that stays open. From a terminal: run-detector.bat <command> [options].
setlocal EnableDelayedExpansion
title Dead-Fest Xbox Death Detector
cd /d "%~dp0"
set "PY=.venv\Scripts\python.exe"

if not exist "%PY%" (
    echo Setting up the detector for the first time. This takes a minute...
    py -3 -m venv .venv 2>nul || python -m venv .venv 2>nul
    if not exist "%PY%" goto :nopython
    "%PY%" -m pip install --upgrade pip >nul
    "%PY%" -m pip install -r requirements.txt
    if errorlevel 1 goto :failed
    echo.
    echo Setup finished.
    echo.
)

rem Called with arguments (from a terminal): run that command and return.
if not "%~1"=="" (
    "%PY%" -m deadfest_detector %*
    exit /b !errorlevel!
)

:menu
cls
echo ==============================================
echo    DEAD-FEST // XBOX DEATH DETECTOR
echo ==============================================
echo.
echo    1  Start detecting deaths
echo    2  Check setup (storage, OCR, video, login)
echo    3  Choose the game
echo    4  Choose the video source
echo    5  Sign in and choose whether to share
echo    6  Upload waiting evidence / get admin decisions
echo    7  Open the evidence gallery
echo    8  List capture devices
echo    0  Exit
echo.
choice /C 123456780 /N /M "Press a number: "
set "pick=%errorlevel%"
rem No keyboard (input closed): stop instead of redrawing the menu forever.
if %pick% GEQ 255 exit /b 1
if %pick%==9 exit /b 0
if %pick%==1 goto :run
if %pick%==2 call :cmd doctor & goto :menu
if %pick%==3 goto :game
if %pick%==4 goto :source
if %pick%==5 call :cmd login & goto :menu
if %pick%==6 call :cmd sync & goto :menu
if %pick%==7 call :cmd gallery & goto :menu
if %pick%==8 call :cmd devices & goto :menu
goto :menu

:run
cls
echo Starting. Press Ctrl+C to stop (then answer N to keep this window open).
echo.
call :cmd run
goto :menu

:game
cls
echo Which game are you playing?
echo.
echo    1  Dying Light
echo    2  Dying Light 2
echo    3  Dying Light: The Beast
echo    4  World War Z
echo    5  Dead Island
echo    6  Dead Island Riptide
echo    7  Dead Island 2
echo    8  State of Decay 2
echo    0  Back
echo.
choice /C 123456780 /N /M "Press a number: "
set "g=%errorlevel%"
set "gid="
if %g%==1 set "gid=dying_light"
if %g%==2 set "gid=dying_light_2"
if %g%==3 set "gid=dying_light_the_beast"
if %g%==4 set "gid=world_war_z"
if %g%==5 set "gid=dead_island"
if %g%==6 set "gid=dead_island_riptide"
if %g%==7 set "gid=dead_island_2"
if %g%==8 set "gid=state_of_decay_2"
if defined gid call :cmd config --game !gid!
goto :menu

:source
cls
echo Where does the Xbox picture come from?
echo.
echo    1  HDMI capture card
echo    2  Xbox app Remote Play window on this PC
echo    0  Back
echo.
choice /C 120 /N /M "Press a number: "
set "s=%errorlevel%"
if %s%==1 goto :source_card
if %s%==2 call :cmd config --source window --window-title Xbox
goto :menu

:source_card
"%PY%" -m deadfest_detector devices
echo.
set "d=0"
set /p "d=Device number to use (Enter for 0): "
call :cmd config --source capture_card --device !d!
goto :menu

rem Runs one detector command, then waits so the result can be read.
:cmd
"%PY%" -m deadfest_detector %*
echo.
pause
exit /b 0

:nopython
echo.
echo Python 3.11 or newer is needed. Get it from https://www.python.org/downloads/windows/
echo and tick "Add python.exe to PATH" during setup, then run this again.
echo.
pause
exit /b 1

:failed
echo.
echo Installing the detector's packages failed. Check your internet connection and try again.
echo.
pause
exit /b 1
