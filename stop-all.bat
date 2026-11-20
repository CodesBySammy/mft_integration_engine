@echo off
title MFT Enterprise Engine - Stop All
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\stop-all.ps1"
echo Services stopped.
pause
