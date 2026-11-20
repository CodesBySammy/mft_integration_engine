@echo off
title MFT Enterprise Engine - 1-Click Setup
echo ==============================================================================
echo Launching MFT Enterprise Engine Automated Setup & Bootstrap...
echo ==============================================================================
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\setup.ps1"
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [ERROR] Setup encountered an issue. See messages above.
    pause
    exit /b %ERRORLEVEL%
)
echo.
echo Setup finished successfully. All dashboards have been opened.
echo Press any key to close this window.
pause > nul
